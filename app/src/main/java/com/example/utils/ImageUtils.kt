package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    /**
     * Reads image from content URI, file path, or base64, resizes down to maxDimension (default 1200px),
     * compresses as JPEG (default 80% quality), and saves into context.filesDir/subDirName.
     * Returns the absolute path of the saved compressed file, or null on failure.
     */
    fun compressAndSaveImage(
        context: Context,
        source: String?,
        subDirName: String = "attachments",
        maxDimension: Int = 1600,
        quality: Int = 90
    ): String? {
        if (source.isNullOrBlank()) return null
        val clean = source.trim()

        return try {
            val bytes = readImageBytes(context, clean) ?: return null
            if (bytes.isEmpty()) return null

            // Calculate rotation from Exif if available
            val rotationDegrees = getExifOrientationDegrees(bytes)

            // Decode dimensions
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var sampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
                w /= 2
                h /= 2
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null

            // Apply Exif rotation if needed
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            // Further scale if still larger than maxDimension
            val curW = bitmap.width
            val curH = bitmap.height
            if (curW > maxDimension || curH > maxDimension) {
                val scale = maxDimension.toFloat() / Math.max(curW, curH).toFloat()
                val targetW = (curW * scale).toInt().coerceAtLeast(1)
                val targetH = (curH * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            // Compress bitmap to JPEG stream
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val compressedBytes = outputStream.toByteArray()
            bitmap.recycle()

            // Save compressed file
            val targetDir = File(context.filesDir, subDirName).apply { if (!exists()) mkdirs() }
            val fileName = "${subDirName.singular()}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(targetDir, fileName)
            destFile.writeBytes(compressedBytes)

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun String.singular(): String {
        return when (this) {
            "attachments" -> "attach"
            "profile_photos" -> "photo"
            else -> "img"
        }
    }

    fun readImageBytes(context: Context, source: String): ByteArray? {
        val clean = source.trim()
        if (clean.isBlank()) return null

        return try {
            if (clean.startsWith("content://") || clean.startsWith("file://")) {
                val uri = Uri.parse(clean)
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else if (clean.contains(";base64,")) {
                val base64Clean = clean.substringAfter(";base64,").trim()
                Base64.decode(base64Clean, Base64.DEFAULT)
            } else if (!clean.startsWith("http") && !clean.startsWith("/") && clean.length > 100) {
                try {
                    Base64.decode(clean, Base64.DEFAULT)
                } catch (e: Exception) {
                    null
                }
            } else if (clean.startsWith("/") && !clean.startsWith("/data/") && !clean.startsWith("/storage/") && !clean.startsWith("/sdcard/") && !clean.startsWith("/emulated/") && clean.length > 100) {
                val file = File(clean)
                if (file.exists() && file.length() > 0) {
                    file.readBytes()
                } else {
                    try {
                        Base64.decode(clean, Base64.DEFAULT)
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                val file = File(clean)
                if (file.exists() && file.length() > 0) {
                    file.readBytes()
                } else {
                    val fileName = file.name
                    val candidates = listOf(
                        File(context.filesDir, clean),
                        File(context.filesDir, fileName),
                        File(File(context.filesDir, "attachments"), fileName),
                        File(File(context.filesDir, "profile_photos"), fileName),
                        File(context.cacheDir, fileName)
                    )
                    val found = candidates.firstOrNull { it.exists() && it.length() > 0 }
                    found?.readBytes() ?: run {
                        try {
                            val uri = Uri.parse(clean)
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } catch (e: Exception) { null }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getExifOrientationDegrees(bytes: ByteArray): Int {
        return try {
            val inputStream: InputStream = bytes.inputStream()
            val exif = ExifInterface(inputStream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun cleanupTempCacheFiles(context: Context) {
        try {
            val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L
            cacheFiles.forEach { file ->
                if (file.isFile && (now - file.lastModified() > oneDayMillis)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun compressAndSaveBitmap(
        context: Context,
        inputBitmap: Bitmap?,
        subDirName: String = "attachments",
        maxDimension: Int = 1600,
        quality: Int = 90
    ): String? {
        if (inputBitmap == null) return null
        return try {
            var bitmap = inputBitmap
            val curW = bitmap.width
            val curH = bitmap.height
            if (curW > maxDimension || curH > maxDimension) {
                val scale = maxDimension.toFloat() / Math.max(curW, curH).toFloat()
                val targetW = (curW * scale).toInt().coerceAtLeast(1)
                val targetH = (curH * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled != bitmap) {
                    bitmap = scaled
                }
            }
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val compressedBytes = outputStream.toByteArray()

            val targetDir = File(context.filesDir, subDirName).apply { if (!exists()) mkdirs() }
            val fileName = "${subDirName.singular()}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val destFile = File(targetDir, fileName)
            destFile.writeBytes(compressedBytes)

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun cleanOrphanedImages(context: Context, activePaths: Set<String>) {
        try {
            val attachmentsDir = File(context.filesDir, "attachments")
            val photosDir = File(context.filesDir, "profile_photos")
            val minAgeMs = 10 * 60 * 1000L
            val now = System.currentTimeMillis()

            listOf(attachmentsDir, photosDir).forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        val isOldEnough = (now - file.lastModified()) > minAgeMs
                        val fileName = file.name
                        val absPath = file.absolutePath
                        val isReferenced = activePaths.any { path ->
                            path.contains(fileName) || path.contains(absPath)
                        }
                        if (isOldEnough && !isReferenced) {
                            file.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun extractImagesFromPdf(context: Context, pdfUri: Uri): List<String> {
        val extractedPaths = mutableListOf<String>()
        var inputStream: InputStream? = null
        var document: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
        try {
            inputStream = context.contentResolver.openInputStream(pdfUri) ?: return emptyList()
            document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
            for (page in document.pages) {
                val resources = page.resources ?: continue
                for (xObjectName in resources.xObjectNames) {
                    val xObject = resources.getXObject(xObjectName)
                    if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                        val bmp = xObject.image
                        if (bmp != null) {
                            val savedPath = compressAndSaveBitmap(context, bmp, "attachments")
                            if (savedPath != null) {
                                extractedPaths.add(savedPath)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { document?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
        }
        return extractedPaths
    }
}
