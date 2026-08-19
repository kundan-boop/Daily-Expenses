package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class DailyBackupInfo(
    val fileName: String,
    val dateDisplay: String,
    val dateIso: String,
    val timestamp: Long,
    val fileSizeStr: String,
    val isFirebaseSynced: Boolean,
    val file: File
)

data class DriveBackupInfo(
    val fileName: String,
    val dateDisplay: String,
    val dateIso: String,
    val timestamp: Long,
    val fileSizeStr: String,
    val uri: Uri
)

object BackupManager {
    private const val TAG = "BackupManager"
    private const val BACKUP_DIR_NAME = "daily_backups"
    private const val PREFS_NAME = "backup_prefs"
    private const val KEY_LAST_BACKUP_DATE = "last_daily_backup_date"
    private const val KEY_DRIVE_FOLDER_URI = "drive_backup_folder_uri"
    private const val KEY_DRIVE_FOLDER_NAME = "drive_backup_folder_name"
    private const val KEY_DATA_DIRTY = "backup_data_is_dirty"
    private const val KEY_LAST_CHANGE_TIME = "backup_last_change_time"
    private const val KEY_LAST_BACKUP_TIME = "backup_last_backup_time"
    private const val MAX_LOCAL_BACKUP_DAYS = 7

    private fun getBackupDir(context: Context): File {
        val dir = File(context.filesDir, BACKUP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Flags that customer, transaction, or expense data was added, edited, or removed since the last backup.
     */
    fun markDataChanged(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_DATA_DIRTY, true)
            .putLong(KEY_LAST_CHANGE_TIME, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Data mutation recorded: hasUnbackedChanges = true")
    }

    /**
     * Checks if any customer or transaction was added, edited, or removed since the last backup.
     */
    fun hasUnbackedChanges(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DATA_DIRTY, false)
    }

    /**
     * Marks backup as completed, clearing dirty status.
     */
    fun markBackupCompleted(context: Context) {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_DATA_DIRTY, false)
            .putString(KEY_LAST_BACKUP_DATE, todayIso)
            .putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Backup recorded: hasUnbackedChanges = false (timestamp=${System.currentTimeMillis()})")
    }

    fun saveDriveFolderUri(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission: ${e.message}")
        }

        val docFile = DocumentFile.fromTreeUri(context, uri)
        val folderName = docFile?.name ?: "Google Drive Folder"

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DRIVE_FOLDER_URI, uri.toString())
            .putString(KEY_DRIVE_FOLDER_NAME, folderName)
            .apply()
    }

    fun getDriveFolderUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DRIVE_FOLDER_URI, null)
    }

    fun getDriveFolderName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DRIVE_FOLDER_NAME, null)
    }

    fun clearDriveFolderUri(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_DRIVE_FOLDER_URI)
            .remove(KEY_DRIVE_FOLDER_NAME)
            .apply()
    }

    fun getDailyBackups(context: Context): List<DailyBackupInfo> {
        val dir = getBackupDir(context)
        val files = dir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".json") } ?: emptyArray()

        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return files.map { file ->
            val sizeKb = (file.length() / 1024.0)
            val sizeStr = if (sizeKb < 1.0) "${file.length()} B" else String.format(Locale.US, "%.1f KB", sizeKb)
            val lastMod = file.lastModified()
            
            val dateIso = try {
                file.name.removePrefix("backup_").removeSuffix(".json")
            } catch (e: Exception) {
                isoFormat.format(Date(lastMod))
            }

            DailyBackupInfo(
                fileName = file.name,
                dateDisplay = dateFormat.format(Date(lastMod)),
                dateIso = dateIso,
                timestamp = lastMod,
                fileSizeStr = sizeStr,
                isFirebaseSynced = true,
                file = file
            )
        }.sortedByDescending { it.timestamp }
    }

    /**
     * Lists backup files stored in the user-selected Google Drive folder.
     */
    fun getDriveBackups(context: Context): List<DriveBackupInfo> {
        val uriStr = getDriveFolderUri(context) ?: return emptyList()
        return try {
            val folderUri = Uri.parse(uriStr)
            val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
            if (!folder.exists()) return emptyList()

            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val backupFiles = folder.listFiles().filter {
                it.isFile && (it.name?.startsWith("backup_") == true || it.name?.endsWith(".json") == true)
            }

            backupFiles.map { doc ->
                val sizeKb = (doc.length() / 1024.0)
                val sizeStr = if (sizeKb < 1.0) "${doc.length()} B" else String.format(Locale.US, "%.1f KB", sizeKb)
                val lastMod = doc.lastModified()
                val fileName = doc.name ?: "backup.json"
                val dateIso = try {
                    fileName.removePrefix("backup_").removeSuffix(".json")
                } catch (e: Exception) {
                    isoFormat.format(Date(lastMod))
                }

                DriveBackupInfo(
                    fileName = fileName,
                    dateDisplay = dateFormat.format(Date(lastMod)),
                    dateIso = dateIso,
                    timestamp = lastMod,
                    fileSizeStr = sizeStr,
                    uri = doc.uri
                )
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list Drive backups: ${e.message}")
            emptyList()
        }
    }

    /**
     * Exports full database directly to JSON for background / on-close backup jobs.
     */
    suspend fun exportDatabaseToJson(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val customers = db.customerDao().getAllCustomersDirect()
            val transactions = db.transactionDao().getAllTransactionsDirect()
            val expenses = db.monthlyExpenseDao().getAllExpensesDirect()

            val rootObj = JSONObject()
            val custArray = JSONArray()
            val txArray = JSONArray()
            val expArray = JSONArray()

            customers.forEach { c ->
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("mobileNumber", c.mobileNumber)
                    put("address", c.address)
                    put("email", c.email)
                    put("notes", c.notes)
                    put("profilePhotoUri", c.profilePhotoUri ?: "")
                    put("collectToday", c.collectToday)
                    put("collectDueDate", c.collectDueDate ?: 0L)
                    put("lastUpdatedTimestamp", c.lastUpdatedTimestamp)
                }
                custArray.put(obj)
            }

            transactions.forEach { t ->
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("customerId", t.customerId)
                    put("transactionType", t.transactionType)
                    put("amount", t.amount)
                    put("dateTimestamp", t.dateTimestamp)
                    put("comment", t.comment)
                    put("category", t.category)
                    put("paymentMode", t.paymentMode)
                    put("attachmentPath", t.attachmentPath ?: "")
                }
                txArray.put(obj)
            }

            expenses.forEach { exp ->
                val obj = JSONObject().apply {
                    put("id", exp.id)
                    put("monthYear", exp.monthYear)
                    put("title", exp.title)
                    put("category", exp.category)
                    put("amount", exp.amount)
                    put("dateTimestamp", exp.dateTimestamp)
                    put("notes", exp.notes)
                    put("attachmentPath", exp.attachmentPath ?: "")
                }
                expArray.put(obj)
            }

            rootObj.put("version", 2)
            rootObj.put("customers", custArray)
            rootObj.put("transactions", txArray)
            rootObj.put("monthlyExpenses", expArray)

            rootObj.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting database to JSON: ${e.message}", e)
            ""
        }
    }

    /**
     * Executes automated backup when the app is closed / backgrounded, ONLY IF changes occurred.
     */
    fun performAutoBackupOnAppClose(context: Context) {
        if (!hasUnbackedChanges(context)) {
            Log.d(TAG, "Auto-backup skipped on close: No data changes detected since last backup.")
            return
        }

        Log.d(TAG, "Data changes detected! Triggering auto-backup on app close...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonString = exportDatabaseToJson(context)
                if (jsonString.isNotBlank()) {
                    val localInfo = performDailyBackup(context, jsonString)
                    val driveSuccess = performDriveAutoBackup(context, jsonString)
                    if (localInfo != null || driveSuccess) {
                        markBackupCompleted(context)
                        Log.d(TAG, "Auto-backup on app close completed successfully. Change flag reset.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed auto-backup on app close: ${e.message}", e)
            }
        }
    }

    /**
     * Creates or updates daily backup locally and into Google Drive (if folder selected).
     * Enforces local 7-day rolling retention and Google Drive single-file replacement.
     */
    fun performDailyBackup(context: Context, jsonString: String): DailyBackupInfo? {
        if (jsonString.isBlank()) return null
        try {
            val dir = getBackupDir(context)
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val backupFile = File(dir, "backup_$todayIso.json")

            backupFile.writeText(jsonString, Charsets.UTF_8)
            Log.d(TAG, "Saved daily backup file locally: ${backupFile.name}")

            cleanOldBackups(dir)

            // Also execute Google Drive Auto Backup (Single latest file replacement)
            performDriveAutoBackup(context, jsonString)

            markBackupCompleted(context)

            val backups = getDailyBackups(context)
            return backups.find { it.fileName == backupFile.name } ?: backups.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform daily backup: ${e.message}", e)
            return null
        }
    }

    /**
     * Performs Google Drive auto-backup into the selected Drive folder.
     * STRICT REQUIREMENT: Only ONE backup file should ever exist in the folder.
     * Deletes existing backup file(s) in the Drive folder BEFORE uploading the new one.
     */
    fun performDriveAutoBackup(context: Context, jsonString: String): Boolean {
        if (jsonString.isBlank()) return false
        val uriStr = getDriveFolderUri(context) ?: return false

        return try {
            val folderUri = Uri.parse(uriStr)
            val folder = DocumentFile.fromTreeUri(context, folderUri)
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                Log.e(TAG, "Drive folder inaccessible or cannot write")
                return false
            }

            // Step 1: Check selected Drive folder for existing backup file(s) and delete them BEFORE uploading
            val existingBackupFiles = folder.listFiles().filter {
                it.isFile && (it.name?.startsWith("backup_") == true || it.name?.endsWith(".json") == true)
            }

            for (oldFile in existingBackupFiles) {
                try {
                    val deleted = oldFile.delete()
                    Log.d(TAG, "Drive Backup: Deleted existing/old backup file ${oldFile.name}, success=$deleted")
                } catch (delEx: Exception) {
                    Log.w(TAG, "Could not delete old backup file: ${oldFile.name}, ${delEx.message}")
                }
            }

            // Step 2: Create the new single latest backup file
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val targetFileName = "backup_$todayIso.json"

            val targetFile = folder.createFile("application/json", targetFileName)
            if (targetFile == null) {
                Log.e(TAG, "Failed to create target backup file in Drive folder")
                return false
            }

            // Step 3: Write backup payload into the new file
            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { os ->
                os.write(jsonString.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            Log.d(TAG, "Successfully backed up to Google Drive folder: $targetFileName (Single latest backup)")

            // Step 4: Double check retention to ensure strictly only 1 backup file exists
            enforceSingleDriveBackup(folder, targetFileName)

            markBackupCompleted(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Drive Auto Backup failed: ${e.message}", e)
            false
        }
    }

    /**
     * Guarantees that only the newest backup file exists in the selected Drive folder.
     */
    private fun enforceSingleDriveBackup(folder: DocumentFile, currentFileName: String) {
        try {
            val allBackupFiles = folder.listFiles().filter {
                it.isFile && (it.name?.startsWith("backup_") == true || it.name?.endsWith(".json") == true)
            }

            if (allBackupFiles.size > 1) {
                val extras = allBackupFiles.filter { it.name != currentFileName }
                extras.forEach { extraFile ->
                    val deleted = extraFile.delete()
                    Log.d(TAG, "Drive Single-File Policy: Removed extra backup file ${extraFile.name}, success=$deleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing single Drive backup file: ${e.message}")
        }
    }

    private fun cleanOldBackups(dir: File) {
        val files = dir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".json") } ?: return
        if (files.size > MAX_LOCAL_BACKUP_DAYS) {
            val sortedOldestFirst = files.sortedBy { it.lastModified() }
            val filesToDeleteCount = sortedOldestFirst.size - MAX_LOCAL_BACKUP_DAYS

            for (i in 0 until filesToDeleteCount) {
                val oldestFile = sortedOldestFirst[i]
                val deleted = oldestFile.delete()
                Log.d(TAG, "7-Day Local Retention: Deleted old backup file ${oldestFile.name}, success=$deleted")
            }
        }
    }

    fun isBackupNeededToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_BACKUP_DATE, "") ?: ""
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return lastDate != todayIso || hasUnbackedChanges(context)
    }
}

