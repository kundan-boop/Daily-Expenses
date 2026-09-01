package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.example.data.AppDatabase
import com.example.work.DriveBackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class BackupState {
    SUCCESS,
    PENDING_OFFLINE,
    FAILED,
    IN_PROGRESS,
    NOT_CONFIGURED
}

data class DriveBackupStatus(
    val state: BackupState = BackupState.NOT_CONFIGURED,
    val lastBackupTimestamp: Long = 0L,
    val lastBackupFormatted: String? = null,
    val errorMessage: String? = null,
    val folderName: String? = null
)

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
    private const val KEY_DRIVE_ROOT_TREE_URI = "drive_backup_root_tree_uri"
    private const val KEY_DRIVE_FOLDER_NAME = "drive_backup_folder_name"
    private const val KEY_DATA_DIRTY = "backup_data_is_dirty"
    private const val KEY_LAST_CHANGE_TIME = "backup_last_change_time"
    private const val KEY_LAST_BACKUP_TIME = "backup_last_backup_time"
    private const val KEY_PENDING_DRIVE_BACKUP = "pending_drive_backup"
    private const val KEY_LAST_DRIVE_BACKUP_TIME = "last_drive_backup_time"
    private const val KEY_LAST_BACKUP_STATUS = "backup_last_status"
    private const val KEY_LAST_BACKUP_ERROR = "backup_last_error"
    
    // Retention policies
    private const val MAX_LOCAL_BACKUP_DAYS = 2
    const val MAX_DRIVE_BACKUP_DAYS = 3
    const val DEDICATED_FOLDER_NAME = "DailyExpenseBackups"

    private val backupScope = CoroutineScope(Dispatchers.IO + Job())
    private val backupMutex = Mutex()
    private var debounceJob: Job? = null
    private val isNetworkCallbackRegistered = AtomicBoolean(false)

    private val _backupStatusFlow = MutableStateFlow(
        DriveBackupStatus(
            state = BackupState.NOT_CONFIGURED,
            lastBackupTimestamp = 0L,
            lastBackupFormatted = null,
            errorMessage = null,
            folderName = null
        )
    )
    val backupStatusFlow: StateFlow<DriveBackupStatus> = _backupStatusFlow.asStateFlow()

    /**
     * Initializes network monitoring so queued offline backups are automatically
     * sent to Google Drive as soon as network connectivity is restored.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        refreshBackupStatus(appContext)
        registerNetworkMonitor(appContext)

        // If app launched and there are pending offline backups and we're online, run them immediately
        if (hasPendingDriveBackup(appContext) && getDriveFolderUri(appContext) != null) {
            Log.d(TAG, "Init: Detected pending offline backup. Enqueueing WorkManager task...")
            enqueueDriveBackupWork(appContext)
            if (isNetworkAvailable(appContext)) {
                backupScope.launch {
                    performPendingDriveBackup(appContext)
                }
            }
        }
    }

    private fun registerNetworkMonitor(context: Context) {
        if (isNetworkCallbackRegistered.getAndSet(true)) return

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val networkRequest = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                cm.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "Network connection restored. Checking for queued Drive backups...")
                        if (hasPendingDriveBackup(context) && getDriveFolderUri(context) != null) {
                            Log.d(TAG, "Network restored & pending Drive backup detected! Auto-uploading queued backup...")
                            enqueueDriveBackupWork(context)
                            backupScope.launch {
                                performPendingDriveBackup(context)
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        Log.d(TAG, "Network connection lost.")
                        if (hasPendingDriveBackup(context)) {
                            updateBackupStatus(
                                context,
                                BackupState.PENDING_OFFLINE,
                                getLastDriveBackupTime(context),
                                "Offline: queued for automatic upload"
                            )
                        }
                    }
                })
                Log.d(TAG, "Network connectivity monitor successfully registered.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register NetworkCallback: ${e.message}")
        }
    }

    /**
     * Checks whether an active internet connection is available.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val netInfo = cm.activeNetworkInfo
                netInfo != null && netInfo.isConnected
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking network availability: ${e.message}")
            false
        }
    }

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
     * Called automatically after ANY change (customer created/updated/deleted, transaction added/edited/deleted,
     * expense added/edited/deleted, category modified).
     *
     * 1. Updates Room dirty flag.
     * 2. Enqueues WorkManager background backup task with network constraints (auto-retries when connected).
     * 3. Triggers immediate local and Google Drive auto-backup with debouncing.
     */
    fun notifyDataChanged(context: Context) {
        val appContext = context.applicationContext
        markDataChanged(appContext)

        if (getDriveFolderUri(appContext) != null) {
            if (!isNetworkAvailable(appContext)) {
                setPendingDriveBackup(appContext, true)
                updateBackupStatus(
                    appContext,
                    BackupState.PENDING_OFFLINE,
                    getLastDriveBackupTime(appContext),
                    "Offline: queued for automatic upload"
                )
            }
            // Enqueue WorkManager background task to guarantee automatic retry with network constraints
            enqueueDriveBackupWork(appContext)
        }

        // Debounce immediate local + Drive auto-backup pipeline
        debounceJob?.cancel()
        debounceJob = backupScope.launch {
            delay(350)
            executeAutoBackupPipeline(appContext)
        }
    }

    /**
     * Enqueues a WorkManager task that automatically runs the Google Drive auto-backup
     * when network is connected, and retries automatically without requiring user intervention.
     */
    fun enqueueDriveBackupWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("google_drive_auto_backup")
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                DriveBackupWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "WorkManager DriveBackupWorker enqueued with CONNECTED network constraints.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue WorkManager task: ${e.message}", e)
        }
    }

    /**
     * Executes the automatic backup pipeline:
     * 1. Exports database to JSON.
     * 2. Writes local daily backup (2-day rolling window).
     * 3. If Google Drive is connected:
     *    - If online: performs Drive backup into dedicated "DailyExpenseBackups" folder.
     *    - If offline: marks pending Drive backup and updates status to PENDING_OFFLINE.
     */
    private suspend fun executeAutoBackupPipeline(context: Context) = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            try {
                Log.d(TAG, "Starting automatic backup pipeline following data change...")
                val jsonString = exportDatabaseToJson(context)
                if (jsonString.isBlank()) {
                    Log.w(TAG, "Exported JSON is empty, skipping auto-backup.")
                    return@withLock
                }

                // 1. Local Daily Backup
                performDailyBackup(context, jsonString)

                // 2. Google Drive Auto-Backup
                val driveFolderUri = getDriveFolderUri(context)
                if (driveFolderUri != null) {
                    val online = isNetworkAvailable(context)
                    if (online) {
                        Log.d(TAG, "Device is ONLINE. Triggering immediate Google Drive auto-backup...")
                        updateBackupStatus(context, BackupState.IN_PROGRESS, getLastDriveBackupTime(context), null)
                        val success = performDriveAutoBackup(context, jsonString)
                        if (success) {
                            val now = System.currentTimeMillis()
                            setPendingDriveBackup(context, false)
                            markBackupCompleted(context)
                            updateBackupStatus(context, BackupState.SUCCESS, now, null)
                            Log.d(TAG, "Google Drive auto-backup completed successfully following change.")
                        } else {
                            Log.w(TAG, "Drive upload failed. Queueing pending backup for retry...")
                            setPendingDriveBackup(context, true)
                            updateBackupStatus(
                                context,
                                BackupState.FAILED,
                                getLastDriveBackupTime(context),
                                "Upload failed. Will retry automatically."
                            )
                            enqueueDriveBackupWork(context)
                        }
                    } else {
                        Log.d(TAG, "Device is OFFLINE. Queued pending backup for automatic Google Drive sync upon reconnection.")
                        setPendingDriveBackup(context, true)
                        updateBackupStatus(
                            context,
                            BackupState.PENDING_OFFLINE,
                            getLastDriveBackupTime(context),
                            "Offline: queued for automatic upload"
                        )
                        enqueueDriveBackupWork(context)
                    }
                } else {
                    Log.d(TAG, "No Google Drive folder connected. Local backup updated.")
                    updateBackupStatus(context, BackupState.NOT_CONFIGURED, 0L, null)
                }

                markBackupCompleted(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error in auto-backup pipeline: ${e.message}", e)
                updateBackupStatus(context, BackupState.FAILED, getLastDriveBackupTime(context), e.message)
            }
        }
    }

    /**
     * Checks if any customer or transaction was added, edited, or removed since the last backup.
     */
    fun hasUnbackedChanges(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DATA_DIRTY, false)
    }

    /**
     * Checks if there is a pending Drive backup waiting to be uploaded when network becomes available.
     */
    fun hasPendingDriveBackup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PENDING_DRIVE_BACKUP, false)
    }

    /**
     * Sets whether a Drive backup is queued pending internet connection.
     */
    fun setPendingDriveBackup(context: Context, pending: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_PENDING_DRIVE_BACKUP, pending)
            .apply()
        Log.d(TAG, "Pending Drive backup status set to: $pending")
    }

    fun getLastDriveBackupTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_DRIVE_BACKUP_TIME, 0L)
    }

    fun updateBackupStatus(
        context: Context,
        state: BackupState,
        timestamp: Long,
        errorMessage: String? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_LAST_BACKUP_STATUS, state.name)
            .putLong(KEY_LAST_DRIVE_BACKUP_TIME, timestamp)
        if (errorMessage != null) {
            editor.putString(KEY_LAST_BACKUP_ERROR, errorMessage)
        } else {
            editor.remove(KEY_LAST_BACKUP_ERROR)
        }
        editor.apply()

        val folderName = getDriveFolderName(context)
        val formatted = if (timestamp > 0) formatTimestamp(timestamp) else null
        _backupStatusFlow.value = DriveBackupStatus(
            state = state,
            lastBackupTimestamp = timestamp,
            lastBackupFormatted = formatted,
            errorMessage = errorMessage,
            folderName = folderName
        )
    }

    fun refreshBackupStatus(context: Context): DriveBackupStatus {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val folderUri = getDriveFolderUri(context)
        val folderName = getDriveFolderName(context)
        val lastTime = prefs.getLong(KEY_LAST_DRIVE_BACKUP_TIME, 0L)
        val error = prefs.getString(KEY_LAST_BACKUP_ERROR, null)
        val isPending = prefs.getBoolean(KEY_PENDING_DRIVE_BACKUP, false)
        val storedStatus = prefs.getString(KEY_LAST_BACKUP_STATUS, null)

        val state = when {
            folderUri == null -> BackupState.NOT_CONFIGURED
            isPending && !isNetworkAvailable(context) -> BackupState.PENDING_OFFLINE
            storedStatus == BackupState.FAILED.name -> BackupState.FAILED
            storedStatus == BackupState.IN_PROGRESS.name -> BackupState.IN_PROGRESS
            isPending -> BackupState.PENDING_OFFLINE
            lastTime > 0 -> BackupState.SUCCESS
            else -> BackupState.SUCCESS
        }

        val formatted = if (lastTime > 0) formatTimestamp(lastTime) else null
        val status = DriveBackupStatus(
            state = state,
            lastBackupTimestamp = lastTime,
            lastBackupFormatted = formatted,
            errorMessage = error,
            folderName = folderName
        )
        _backupStatusFlow.value = status
        return status
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = Calendar.getInstance()
        val backupCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        return if (now.get(Calendar.YEAR) == backupCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == backupCal.get(Calendar.DAY_OF_YEAR)) {
            "Today at ${timeFormat.format(Date(timestamp))}"
        } else {
            dateFormat.format(Date(timestamp))
        }
    }

    /**
     * Runs queued pending backup to Google Drive.
     */
    suspend fun performPendingDriveBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (getDriveFolderUri(context) == null) {
            setPendingDriveBackup(context, false)
            updateBackupStatus(context, BackupState.NOT_CONFIGURED, 0L, null)
            return@withContext false
        }

        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "Cannot run pending Drive backup: Still offline.")
            updateBackupStatus(
                context,
                BackupState.PENDING_OFFLINE,
                getLastDriveBackupTime(context),
                "Offline: waiting for network connection"
            )
            return@withContext false
        }

        backupMutex.withLock {
            try {
                Log.d(TAG, "Executing pending Google Drive backup...")
                updateBackupStatus(context, BackupState.IN_PROGRESS, getLastDriveBackupTime(context), null)
                val jsonString = exportDatabaseToJson(context)
                if (jsonString.isNotBlank()) {
                    val success = performDriveAutoBackup(context, jsonString)
                    if (success) {
                        val now = System.currentTimeMillis()
                        setPendingDriveBackup(context, false)
                        markBackupCompleted(context)
                        updateBackupStatus(context, BackupState.SUCCESS, now, null)
                        Log.d(TAG, "Queued Drive backup successfully synced to Google Drive!")
                        return@withLock true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed executing pending Drive backup: ${e.message}", e)
                updateBackupStatus(context, BackupState.FAILED, getLastDriveBackupTime(context), e.message)
            }
            return@withLock false
        }
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

    /**
     * Connects Google Drive with dedicated folder creation & support.
     * Checks if the selected folder is named "DailyExpenseBackups", or creates/selects
     * a dedicated "DailyExpenseBackups" folder inside the chosen tree.
     */
    fun saveDriveFolderUri(context: Context, pickedUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(pickedUri, flags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission: ${e.message}")
        }

        val pickedDoc = DocumentFile.fromTreeUri(context, pickedUri)
        var targetFolderDoc: DocumentFile? = null

        if (pickedDoc != null) {
            if (pickedDoc.name == DEDICATED_FOLDER_NAME) {
                targetFolderDoc = pickedDoc
            } else {
                val existingSub = pickedDoc.findFile(DEDICATED_FOLDER_NAME)
                if (existingSub != null && existingSub.isDirectory) {
                    targetFolderDoc = existingSub
                } else {
                    targetFolderDoc = pickedDoc.createDirectory(DEDICATED_FOLDER_NAME) ?: pickedDoc
                }
            }
        }

        val finalFolderUri = targetFolderDoc?.uri ?: pickedUri
        val folderName = DEDICATED_FOLDER_NAME

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DRIVE_FOLDER_URI, finalFolderUri.toString())
            .putString(KEY_DRIVE_ROOT_TREE_URI, pickedUri.toString())
            .putString(KEY_DRIVE_FOLDER_NAME, folderName)
            .apply()

        updateBackupStatus(context, BackupState.IN_PROGRESS, 0L, null)

        // Trigger immediate auto-backup to newly connected folder
        backupScope.launch {
            try {
                val jsonStr = exportDatabaseToJson(context)
                if (jsonStr.isNotBlank()) {
                    val success = performDriveAutoBackup(context, jsonStr)
                    if (success) {
                        val now = System.currentTimeMillis()
                        setPendingDriveBackup(context, false)
                        markBackupCompleted(context)
                        updateBackupStatus(context, BackupState.SUCCESS, now, null)
                    } else {
                        updateBackupStatus(context, BackupState.FAILED, 0L, "Initial backup failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initial backup to new Drive folder failed: ${e.message}")
                updateBackupStatus(context, BackupState.FAILED, 0L, e.message)
            }
        }
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
            .remove(KEY_DRIVE_ROOT_TREE_URI)
            .remove(KEY_DRIVE_FOLDER_NAME)
            .remove(KEY_PENDING_DRIVE_BACKUP)
            .remove(KEY_LAST_BACKUP_STATUS)
            .remove(KEY_LAST_BACKUP_ERROR)
            .apply()
        updateBackupStatus(context, BackupState.NOT_CONFIGURED, 0L, null)
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
     * Lists backup files stored in the dedicated "DailyExpenseBackups" Google Drive folder.
     */
    fun getDriveBackups(context: Context): List<DriveBackupInfo> {
        val uriStr = getDriveFolderUri(context) ?: return emptyList()
        return try {
            val folderUri = Uri.parse(uriStr)
            var folder = DocumentFile.fromTreeUri(context, folderUri) ?: DocumentFile.fromSingleUri(context, folderUri)
            if (folder == null || !folder.exists()) return emptyList()

            if (folder.name != DEDICATED_FOLDER_NAME && folder.isDirectory) {
                val sub = folder.findFile(DEDICATED_FOLDER_NAME)
                if (sub != null && sub.isDirectory) {
                    folder = sub
                }
            }

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
     * Exports full database directly to JSON.
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
        if (!hasUnbackedChanges(context) && !hasPendingDriveBackup(context)) {
            Log.d(TAG, "Auto-backup skipped on close: No pending data changes or offline queue.")
            return
        }

        Log.d(TAG, "Data changes detected! Triggering auto-backup on app close...")
        backupScope.launch {
            try {
                executeAutoBackupPipeline(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed auto-backup on app close: ${e.message}", e)
            }
        }
    }

    /**
     * Creates or updates daily backup locally and into Google Drive (if folder selected).
     * Enforces local 2-day rolling retention.
     */
    fun performDailyBackup(context: Context, jsonString: String): DailyBackupInfo? {
        if (jsonString.isBlank()) return null
        try {
            val dir = getBackupDir(context)
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val backupFile = File(dir, "backup_$todayIso.json")

            // Same-day overwrite: Overwrites existing today file if present
            backupFile.writeText(jsonString, Charsets.UTF_8)
            Log.d(TAG, "Saved daily backup file locally: ${backupFile.name}")

            cleanOldLocalBackups(dir)

            val backups = getDailyBackups(context)
            return backups.find { it.fileName == backupFile.name } ?: backups.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform daily backup: ${e.message}", e)
            return null
        }
    }

    /**
     * Performs Google Drive auto-backup strictly into the dedicated "DailyExpenseBackups" folder.
     *
     * Core Requirements Handled:
     * 1. Dedicated Folder: Stored inside "DailyExpenseBackups".
     * 2. Same-Day Overwrite: Overwrites today's existing backup file in the Drive folder instead of creating duplicate files.
     * 3. 3-Day Rotation: Enforces a strict 3-day retention window, purging files older than the 3 newest daily backups.
     */
    fun performDriveAutoBackup(context: Context, jsonString: String): Boolean {
        if (jsonString.isBlank()) return false
        val uriStr = getDriveFolderUri(context) ?: return false

        return try {
            val folderUri = Uri.parse(uriStr)
            var folder = DocumentFile.fromTreeUri(context, folderUri) ?: DocumentFile.fromSingleUri(context, folderUri)
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                Log.e(TAG, "Drive folder inaccessible or cannot write")
                return false
            }

            // Ensure we are inside "DailyExpenseBackups"
            if (folder.name != DEDICATED_FOLDER_NAME && folder.isDirectory) {
                val subfolder = folder.findFile(DEDICATED_FOLDER_NAME) ?: folder.createDirectory(DEDICATED_FOLDER_NAME)
                if (subfolder != null && subfolder.isDirectory) {
                    folder = subfolder
                }
            }

            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val targetFileName = "backup_$todayIso.json"

            // 3-Day Rotation: Clean up older backups outside 3-day window before and after upload
            enforceDrive3DayRotation(folder, targetFileName)

            // Same-Day Overwrite: Check for existing backup for today to overwrite, rather than creating duplicates
            val existingTodayFile = folder.listFiles().firstOrNull {
                it.isFile && it.name == targetFileName
            }

            val targetDocFile = if (existingTodayFile != null) {
                Log.d(TAG, "Found existing backup for today in DailyExpenseBackups: $targetFileName. Overwriting in-place...")
                existingTodayFile
            } else {
                Log.d(TAG, "Creating new backup file in DailyExpenseBackups folder: $targetFileName")
                folder.createFile("application/json", targetFileName)
            }

            if (targetDocFile == null) {
                Log.e(TAG, "Failed to create or target backup file in DailyExpenseBackups folder")
                return false
            }

            // Write JSON payload into the target file (in truncate/overwrite mode)
            context.contentResolver.openOutputStream(targetDocFile.uri, "wt")?.use { os ->
                os.write(jsonString.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: run {
                // Fallback for providers that don't support "wt"
                Log.w(TAG, "Stream write failed, attempting recreate fallback...")
                targetDocFile.delete()
                val fallbackFile = folder.createFile("application/json", targetFileName) ?: return false
                context.contentResolver.openOutputStream(fallbackFile.uri, "w")?.use { os ->
                    os.write(jsonString.toByteArray(Charsets.UTF_8))
                    os.flush()
                } ?: return false
            }

            Log.d(TAG, "Successfully backed up to DailyExpenseBackups folder: $targetFileName")

            // Re-enforce 3-day rotation to guarantee folder maintains strictly at most 3 daily backup files
            enforceDrive3DayRotation(folder, targetFileName)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_LAST_DRIVE_BACKUP_TIME, System.currentTimeMillis())
                .apply()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Drive Auto Backup failed: ${e.message}", e)
            false
        }
    }

    /**
     * 3-Day Rotation in Google Drive Folder:
     * Retains only the 3 most recent daily backup files in "DailyExpenseBackups" and removes older backups.
     */
    fun enforceDrive3DayRotation(folder: DocumentFile, currentFileName: String? = null) {
        try {
            val allBackupFiles = folder.listFiles().filter {
                it.isFile && (it.name?.startsWith("backup_") == true || it.name?.endsWith(".json") == true)
            }

            // Group by unique filename sorted descending by last modified
            val sortedFilesNewestFirst = allBackupFiles.sortedByDescending { it.lastModified() }

            if (sortedFilesNewestFirst.size > MAX_DRIVE_BACKUP_DAYS) {
                val filesToDelete = sortedFilesNewestFirst.drop(MAX_DRIVE_BACKUP_DAYS)
                filesToDelete.forEach { oldFile ->
                    // Do not delete today's active target if currently processing
                    if (oldFile.name != currentFileName) {
                        val deleted = oldFile.delete()
                        Log.d(TAG, "3-Day Drive Retention: Deleted old backup file ${oldFile.name}, success=$deleted")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing 3-Day Drive backup rotation: ${e.message}")
        }
    }

    private fun cleanOldLocalBackups(dir: File) {
        val files = dir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".json") } ?: return
        if (files.size > MAX_LOCAL_BACKUP_DAYS) {
            val sortedOldestFirst = files.sortedBy { it.lastModified() }
            val filesToDeleteCount = sortedOldestFirst.size - MAX_LOCAL_BACKUP_DAYS

            for (i in 0 until filesToDeleteCount) {
                val oldestFile = sortedOldestFirst[i]
                val deleted = oldestFile.delete()
                Log.d(TAG, "2-Day Local Retention: Deleted old backup file ${oldestFile.name}, success=$deleted")
            }
        }
    }

    fun isBackupNeededToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_BACKUP_DATE, "") ?: ""
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return lastDate != todayIso || hasUnbackedChanges(context) || hasPendingDriveBackup(context)
    }
}
