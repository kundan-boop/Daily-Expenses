package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object MediaStoreSaver {

    private const val GALLERY_ALBUM_NAME = "DailyExpense"

    fun saveImageToGallery(context: Context, imageSource: String): Result<Uri> {
        return try {
            val bytes = ImageUtils.readImageBytes(context, imageSource)
                ?: return Result.failure(IllegalStateException("Could not read image data"))
            if (bytes.isEmpty()) {
                return Result.failure(IllegalStateException("Image file is empty"))
            }

            val fileName = "DailyExpense_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$GALLERY_ALBUM_NAME"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IllegalStateException("Failed to create MediaStore entry"))

                try {
                    resolver.openOutputStream(itemUri)?.use { output ->
                        output.write(bytes)
                        output.flush()
                    } ?: return Result.failure(IllegalStateException("Unable to open gallery output stream"))

                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                    Result.success(itemUri)
                } catch (e: Exception) {
                    runCatching { resolver.delete(itemUri, null, null) }
                    Result.failure(e)
                }
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val albumDir = File(picturesDir, GALLERY_ALBUM_NAME).apply {
                    if (!exists()) mkdirs()
                }
                val destFile = File(albumDir, fileName)
                FileOutputStream(destFile).use { output ->
                    output.write(bytes)
                    output.flush()
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
                @Suppress("DEPRECATION")
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, destFile.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                }
                val inserted = runCatching {
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }.getOrNull()
                Result.success(inserted ?: Uri.fromFile(destFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createShareableImageUri(context: Context, imageSource: String): Uri? {
        return try {
            val clean = imageSource.trim()
            if (clean.isBlank()) return null

            val directFile = File(clean)
            val appFilesCanonical = context.filesDir.canonicalPath
            val appCacheCanonical = context.cacheDir.canonicalPath

            if (directFile.exists() && directFile.length() > 0L) {
                val canonical = directFile.canonicalPath
                if (canonical.startsWith(appFilesCanonical) || canonical.startsWith(appCacheCanonical)) {
                    return FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        directFile
                    )
                }
            }

            val bytes = ImageUtils.readImageBytes(context, clean) ?: return null
            if (bytes.isEmpty()) return null
            val tempFile = File(context.cacheDir, "share_receipt_${System.currentTimeMillis()}.jpg")
            tempFile.writeBytes(bytes)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun resolveImageModel(context: Context, rawPath: String): Any {
        val clean = rawPath.trim()
        val direct = File(clean)
        if (direct.exists() && direct.length() > 0L) return direct

        val name = direct.name
        val candidates = listOf(
            File(context.filesDir, clean),
            File(context.filesDir, name),
            File(File(context.filesDir, "attachments"), name),
            File(File(context.filesDir, "profile_photos"), name)
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0L } ?: clean
    }
}
