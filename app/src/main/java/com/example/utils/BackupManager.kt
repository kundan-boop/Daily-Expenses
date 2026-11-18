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
    const val MAX_DRIVE_BACKUP_DAYS = 7
    const val DEDICATED_FOLDER_NAME = "DailyExpenseBackups"

    data class BackupExportPackage(
        val jsonString: String,
        val customerCount: Int,
        val transactionCount: Int,
        val hasUserProfile: Boolean
    )

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
     * sent to Google Drive / Firebase as soon as network connectivity is restored.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        AppPreferencesManager.init(appContext)
        refreshBackupStatus(appContext)
        registerNetworkMonitor(appContext)

        val hasDrivePending = hasPendingDriveBackup(appContext) &&
            getDriveFolderUri(appContext) != null &&
            AppPreferencesManager.isDriveAutoBackupEnabled(appContext)
        val hasFirebasePending = AppPreferencesManager.hasPendingFirebaseBackup(appContext) &&
            isFirebaseAutoBackupReady(appContext)

        if (hasDrivePending || hasFirebasePending) {
            Log.d(TAG, "Init: Detected pending offline backup. Enqueueing WorkManager task...")
            enqueueDriveBackupWork(appContext)
            if (isNetworkAvailable(appContext)) {
                backupScope.launch {
                    if (hasDrivePending) performPendingDriveBackup(appContext)
                    if (hasFirebasePending) performPendingFirebaseBackup(appContext)
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
                        Log.d(TAG, "Network connection restored. Checking for queued cloud backups...")
                        val driveReady = hasPendingDriveBackup(context) &&
                            getDriveFolderUri(context) != null &&
                            AppPreferencesManager.isDriveAutoBackupEnabled(context)
                        val firebaseReady = AppPreferencesManager.hasPendingFirebaseBackup(context) &&
                            isFirebaseAutoBackupReady(context)

                        if (driveReady || firebaseReady) {
                            enqueueDriveBackupWork(context)
                            backupScope.launch {
                                if (driveReady) performPendingDriveBackup(context)
                                if (firebaseReady) performPendingFirebaseBackup(context)
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
                        if (AppPreferencesManager.hasPendingFirebaseBackup(context) && isFirebaseSignedIn(context)) {
                            AppPreferencesManager.updateFirebaseStatus(
                                context = context,
                                state = BackupState.PENDING_OFFLINE,
                                errorMessage = "Offline: queued for automatic cloud sync"
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

        val online = isNetworkAvailable(appContext)
        val driveConfigured = getDriveFolderUri(appContext) != null &&
            AppPreferencesManager.isDriveAutoBackupEnabled(appContext)
        val firebaseConfigured = isFirebaseAutoBackupReady(appContext)

        if (driveConfigured && !online) {
            setPendingDriveBackup(appContext, true)
            updateBackupStatus(
                appContext,
                BackupState.PENDING_OFFLINE,
                getLastDriveBackupTime(appContext),
                "Offline: queued for automatic upload"
            )
        }
        if (firebaseConfigured && !online) {
            AppPreferencesManager.setPendingFirebaseBackup(appContext, true)
            AppPreferencesManager.updateFirebaseStatus(
                context = appContext,
                state = BackupState.PENDING_OFFLINE,
                errorMessage = "Offline: queued for automatic cloud sync"
            )
        }
        if (driveConfigured || firebaseConfigured) {
            enqueueDriveBackupWork(appContext)
        }

        // Debounce immediate local + Drive + Firebase auto-backup pipeline
        debounceJob?.cancel()
        debounceJob = backupScope.launch {
            delay(350)
            executeAutoBackupPipeline(appContext)
        }
    }

    /**
     * Enqueues a WorkManager task that automatically runs Drive & Firebase auto-backups
     * when network is connected, retrying automatically on failure or reconnection.
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
     * 1. Exports database + userProfile + Base64 attachments to unified JSON.
     * 2. Writes local daily backup (2-day rolling window).
     * 3. Runs Google Drive auto-backup (if connected & enabled, 7-day retention).
     * 4. Runs Firebase Cloud Firestore auto-backup independently (if signed in & enabled, 7-day retention).
     */
    private suspend fun executeAutoBackupPipeline(context: Context) = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            try {
                Log.d(TAG, "Starting automatic backup pipeline following data change...")
                val pkg = exportFullBackupPackage(context)
                val jsonString = pkg.jsonString
                if (jsonString.isBlank()) {
                    Log.w(TAG, "Exported JSON is empty, skipping auto-backup.")
                    return@withLock
                }

                // 1. Local Daily Backup (always 2-day rolling)
                performDailyBackup(context, jsonString)

                // 2. Google Drive Auto-Backup (isolated error handling)
                val driveFolderUri = getDriveFolderUri(context)
                if (driveFolderUri != null && AppPreferencesManager.isDriveAutoBackupEnabled(context)) {
                    val online = isNetworkAvailable(context)
                    if (online) {
                        updateBackupStatus(context, BackupState.IN_PROGRESS, getLastDriveBackupTime(context), null)
                        val success = performDriveAutoBackup(context, jsonString)
                        if (success) {
                            val now = System.currentTimeMillis()
                            setPendingDriveBackup(context, false)
                            updateBackupStatus(context, BackupState.SUCCESS, now, null)
                        } else {
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
                        setPendingDriveBackup(context, true)
                        updateBackupStatus(
                            context,
                            BackupState.PENDING_OFFLINE,
                            getLastDriveBackupTime(context),
                            "Offline: queued for automatic upload"
                        )
                        enqueueDriveBackupWork(context)
                    }
                } else if (driveFolderUri == null) {
                    updateBackupStatus(context, BackupState.NOT_CONFIGURED, 0L, null)
                }

                // 3. Firebase Cloud Auto-Backup (completely independent of Drive status)
                syncFirebaseBackupLocked(context, pkg)

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
        return LedgerDateFormatter.formatRelativeTimestamp(timestamp)
    }

    fun isFirebaseSignedIn(context: Context): Boolean {
        return try {
            com.google.firebase.FirebaseApp.getApps(context).isNotEmpty() &&
                com.example.auth.GoogleAuthHelper.getCurrentFirebaseUser() != null
        } catch (_: Exception) {
            false
        }
    }

    fun isFirebaseAutoBackupReady(context: Context): Boolean {
        return isFirebaseSignedIn(context) && AppPreferencesManager.isFirebaseAutoBackupEnabled(context)
    }

    suspend fun performPendingFirebaseBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isFirebaseAutoBackupReady(context)) return@withContext false
        backupMutex.withLock {
            val pkg = exportFullBackupPackage(context)
            if (pkg.jsonString.isBlank()) return@withLock false
            syncFirebaseBackupLocked(context, pkg)
        }
    }

    internal suspend fun syncFirebaseBackupLocked(context: Context, pkg: BackupExportPackage): Boolean {
        if (!isFirebaseSignedIn(context)) {
            AppPreferencesManager.refreshFirebaseStatus(context, isSignedIn = false, userEmail = null)
            return false
        }
        if (!AppPreferencesManager.isFirebaseAutoBackupEnabled(context)) {
            return false
        }
        val userEmail = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()?.email
        if (!isNetworkAvailable(context)) {
            AppPreferencesManager.setPendingFirebaseBackup(context, true)
            AppPreferencesManager.updateFirebaseStatus(
                context = context,
                state = BackupState.PENDING_OFFLINE,
                errorMessage = "Offline: waiting for network connection",
                userEmail = userEmail
            )
            return false
        }
        return try {
            AppPreferencesManager.updateFirebaseStatus(
                context = context,
                state = BackupState.IN_PROGRESS,
                errorMessage = null,
                userEmail = userEmail
            )
            val repo = com.example.data.remote.FirebaseBackupRepository(context)
            val uploadResult = repo.uploadDailyBackup(
                jsonString = pkg.jsonString,
                customerCount = pkg.customerCount,
                transactionCount = pkg.transactionCount,
                hasUserProfile = pkg.hasUserProfile
            )
            if (uploadResult.isSuccess) {
                val now = System.currentTimeMillis()
                val snapshots = repo.listBackups().getOrNull()
                AppPreferencesManager.setPendingFirebaseBackup(context, false)
                AppPreferencesManager.updateFirebaseStatus(
                    context = context,
                    state = BackupState.SUCCESS,
                    timestamp = now,
                    errorMessage = null,
                    userEmail = userEmail,
                    snapshotCount = snapshots?.size ?: 1
                )
                true
            } else {
                val msg = uploadResult.exceptionOrNull()?.localizedMessage ?: "Firebase cloud sync failed"
                AppPreferencesManager.setPendingFirebaseBackup(context, true)
                AppPreferencesManager.updateFirebaseStatus(
                    context = context,
                    state = BackupState.FAILED,
                    errorMessage = msg,
                    userEmail = userEmail
                )
                false
            }
        } catch (e: Exception) {
            AppPreferencesManager.setPendingFirebaseBackup(context, true)
            AppPreferencesManager.updateFirebaseStatus(
                context = context,
                state = BackupState.FAILED,
                errorMessage = e.message ?: "Firebase cloud sync failed",
                userEmail = userEmail
            )
            false
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
                val pkg = exportFullBackupPackage(context)
                val jsonString = pkg.jsonString
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

    private fun multiPathToBase64Array(context: Context, multiPathString: String?): JSONArray {
        val jsonArray = JSONArray()
        if (multiPathString.isNullOrBlank()) return jsonArray
        val paths = multiPathString.split(Regex("[|,]")).map { it.trim() }.filter { it.isNotBlank() }
        paths.forEach { path ->
            val base64 = ImageUtils.encodeImageToBase64(context, path, maxDimension = 1600, quality = 88)
            if (!base64.isNullOrBlank()) {
                jsonArray.put(base64)
            }
        }
        return jsonArray
    }

    /**
     * Unified JSON export used by Local, Google Drive, and Firebase backups:
     * includes customers, transactions, monthlyExpenses, compressed Base64 attachments,
     * and the current userProfile + Base64 avatar.
     */
    suspend fun exportFullBackupPackage(context: Context): BackupExportPackage = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val customers = db.customerDao().getAllCustomersDirect()
            val transactions = db.transactionDao().getAllTransactionsDirect()
            val expenses = db.monthlyExpenseDao().getAllExpensesDirect()

            val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val profileName = userPrefs.getString("user_name", "Kundan Sinh") ?: "Kundan Sinh"
            val profilePhone = userPrefs.getString("user_phone", "1234567890") ?: "1234567890"
            val profileEmail = userPrefs.getString("user_email", "Store@gmail.com") ?: "Store@gmail.com"
            val profileBusiness = userPrefs.getString("business_name", "General Store").let {
                if (it == "Only General store" || it == "Rajesh General Store") "General Store" else (it ?: "General Store")
            }
            val savedProfileUri = userPrefs.getString("profile_image_uri", null)
            val fallbackAvatarFile = File(context.filesDir, "user_profile_avatar.jpg")
            val avatarSource = when {
                !savedProfileUri.isNullOrBlank() -> savedProfileUri
                fallbackAvatarFile.exists() && fallbackAvatarFile.length() > 0L -> fallbackAvatarFile.absolutePath
                else -> null
            }
            val profileImageBase64 = ImageUtils.encodeImageToBase64(context, avatarSource, maxDimension = 1024, quality = 85)

            val userProfileObj = JSONObject().apply {
                put("name", profileName)
                put("businessName", profileBusiness)
                put("phone", profilePhone)
                put("email", profileEmail)
                if (!profileImageBase64.isNullOrBlank()) {
                    put("profileImageBase64", profileImageBase64)
                }
            }

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
                    val photoArray = multiPathToBase64Array(context, c.profilePhotoUri)
                    if (photoArray.length() > 0) {
                        put("profilePhotoBase64List", photoArray)
                        put("profilePhotoBase64", photoArray.optString(0, ""))
                    }
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
                    val attachArray = multiPathToBase64Array(context, t.attachmentPath)
                    if (attachArray.length() > 0) {
                        put("attachmentBase64List", attachArray)
                        put("attachmentBase64", attachArray.optString(0, ""))
                    }
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
                    val attachArray = multiPathToBase64Array(context, exp.attachmentPath)
                    if (attachArray.length() > 0) {
                        put("attachmentBase64List", attachArray)
                        put("attachmentBase64", attachArray.optString(0, ""))
                    }
                }
                expArray.put(obj)
            }

            rootObj.put("version", 2)
            rootObj.put("exportedAt", System.currentTimeMillis())
            rootObj.put("userProfile", userProfileObj)
            rootObj.put("customers", custArray)
            rootObj.put("transactions", txArray)
            rootObj.put("monthlyExpenses", expArray)

            BackupExportPackage(
                jsonString = rootObj.toString(2),
                customerCount = customers.size,
                transactionCount = transactions.size,
                hasUserProfile = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting full backup package to JSON: ${e.message}", e)
            BackupExportPackage(jsonString = "", customerCount = 0, transactionCount = 0, hasUserProfile = false)
        }
    }

    /**
     * Exports full database directly to JSON (delegates to unified exportFullBackupPackage).
     */
    suspend fun exportDatabaseToJson(context: Context): String = exportFullBackupPackage(context).jsonString

    /**
     * Executes automated backup when the app is closed / backgrounded, ONLY IF changes occurred.
     */
    fun performAutoBackupOnAppClose(context: Context) {
        if (!hasUnbackedChanges(context) && !hasPendingDriveBackup(context) && !AppPreferencesManager.hasPendingFirebaseBackup(context)) {
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
     * 3. Verified Upload Before 7-Day Rotation: Only purges backups older than the newest 7 snapshots after confirming
     *    the new/overwritten snapshot exists and is non-empty.
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

            // Same-Day Overwrite: Check for existing backup for today to overwrite, rather than creating duplicates
            val existingTodayFile = folder.listFiles().firstOrNull {
                it.isFile && it.name == targetFileName
            }

            var targetDocFile: DocumentFile? = if (existingTodayFile != null) {
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

            val payloadBytes = jsonString.toByteArray(Charsets.UTF_8)
            var writeStreamOpened = false
            context.contentResolver.openOutputStream(targetDocFile.uri, "wt")?.use { os ->
                os.write(payloadBytes)
                os.flush()
                writeStreamOpened = true
            }
            if (!writeStreamOpened) {
                Log.w(TAG, "Stream write failed, attempting recreate fallback...")
                targetDocFile.delete()
                val fallbackFile = folder.createFile("application/json", targetFileName) ?: return false
                targetDocFile = fallbackFile
                context.contentResolver.openOutputStream(fallbackFile.uri, "w")?.use { os ->
                    os.write(payloadBytes)
                    os.flush()
                    writeStreamOpened = true
                } ?: return false
            }

            // Verify target exists and is non-empty BEFORE pruning older backups
            val verifiedNonEmpty = targetDocFile.exists() && (
                targetDocFile.length() > 0L ||
                    (context.contentResolver.openInputStream(targetDocFile.uri)?.use { it.read() != -1 } == true)
            )
            if (!verifiedNonEmpty) {
                Log.e(TAG, "Drive backup write could not be verified non-empty; keeping existing snapshots intact.")
                return false
            }

            Log.d(TAG, "Successfully verified upload in DailyExpenseBackups folder: $targetFileName")

            // Enforce 7-day retention ONLY AFTER upload & verification succeed
            enforceDriveSevenDayRotation(folder, targetFileName)

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
     * 7-Day Rotation in Google Drive Folder:
     * Retains the 7 most recent daily backup files in "DailyExpenseBackups" and removes older backups.
     */
    fun enforceDriveSevenDayRotation(folder: DocumentFile, currentFileName: String? = null) {
        try {
            val allBackupFiles = folder.listFiles().filter {
                it.isFile && (it.name?.startsWith("backup_") == true || it.name?.endsWith(".json") == true)
            }

            // Sort newest-first by date extracted from backup_YYYY-MM-DD.json (fallback lastModified)
            val sortedFilesNewestFirst = allBackupFiles.sortedWith(
                compareByDescending<DocumentFile> { doc ->
                    val name = doc.name.orEmpty()
                    if (name.startsWith("backup_") && name.endsWith(".json")) {
                        name.removePrefix("backup_").removeSuffix(".json")
                    } else {
                        String.format(Locale.US, "%020d", doc.lastModified())
                    }
                }.thenByDescending { it.lastModified() }
            )

            if (sortedFilesNewestFirst.size > MAX_DRIVE_BACKUP_DAYS) {
                val filesToDelete = sortedFilesNewestFirst.drop(MAX_DRIVE_BACKUP_DAYS)
                filesToDelete.forEach { oldFile ->
                    if (oldFile.name != currentFileName) {
                        val deleted = oldFile.delete()
                        Log.d(TAG, "7-Day Drive Retention: Deleted old backup file ${oldFile.name}, success=$deleted")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enforcing 7-Day Drive backup rotation: ${e.message}")
        }
    }

    @Deprecated("Use enforceDriveSevenDayRotation", ReplaceWith("enforceDriveSevenDayRotation(folder, currentFileName)"))
    fun enforceDrive3DayRotation(folder: DocumentFile, currentFileName: String? = null) {
        enforceDriveSevenDayRotation(folder, currentFileName)
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
