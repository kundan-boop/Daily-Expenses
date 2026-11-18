package com.example.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

data class FirebaseBackupStatus(
    val state: BackupState = BackupState.NOT_CONFIGURED,
    val lastBackupTimestamp: Long = 0L,
    val lastBackupFormatted: String? = null,
    val errorMessage: String? = null,
    val userEmail: String? = null,
    val snapshotCount: Int = 0
)

/**
 * Isolated SharedPreferences manager ("app_settings_prefs") for new settings & toggles
 * so existing "user_prefs" and "backup_prefs" files and keys remain 100% untouched on upgrade.
 */
object AppPreferencesManager {
    private const val PREFS_NAME = "app_settings_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_DRIVE_AUTO_BACKUP = "drive_auto_backup_enabled"
    private const val KEY_FIREBASE_AUTO_BACKUP = "firebase_auto_backup_enabled"
    private const val KEY_LAST_FIREBASE_BACKUP_TIME = "last_firebase_backup_time"
    private const val KEY_LAST_FIREBASE_BACKUP_STATE = "last_firebase_backup_state"
    private const val KEY_LAST_FIREBASE_BACKUP_ERROR = "last_firebase_backup_error"
    private const val KEY_FIREBASE_SNAPSHOT_COUNT = "last_firebase_snapshot_count"
    private const val KEY_PENDING_FIREBASE_BACKUP = "pending_firebase_backup"

    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow("English")
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _notificationsEnabledFlow = MutableStateFlow(true)
    val notificationsEnabledFlow: StateFlow<Boolean> = _notificationsEnabledFlow.asStateFlow()

    private val _driveAutoBackupEnabledFlow = MutableStateFlow(true)
    val driveAutoBackupEnabledFlow: StateFlow<Boolean> = _driveAutoBackupEnabledFlow.asStateFlow()

    private val _firebaseAutoBackupEnabledFlow = MutableStateFlow(true)
    val firebaseAutoBackupEnabledFlow: StateFlow<Boolean> = _firebaseAutoBackupEnabledFlow.asStateFlow()

    private val _firebaseBackupStatusFlow = MutableStateFlow(FirebaseBackupStatus())
    val firebaseBackupStatusFlow: StateFlow<FirebaseBackupStatus> = _firebaseBackupStatusFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        _themeModeFlow.value = runCatching { ThemeMode.valueOf(savedTheme ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        _languageFlow.value = prefs.getString(KEY_LANGUAGE, "English") ?: "English"
        _notificationsEnabledFlow.value = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        _driveAutoBackupEnabledFlow.value = prefs.getBoolean(KEY_DRIVE_AUTO_BACKUP, true)
        _firebaseAutoBackupEnabledFlow.value = prefs.getBoolean(KEY_FIREBASE_AUTO_BACKUP, true)
        val authUser = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()
        refreshFirebaseStatus(context, isSignedIn = authUser != null, userEmail = authUser?.email)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
        _themeModeFlow.value = mode
    }

    fun setLanguage(context: Context, language: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
        _languageFlow.value = language
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .apply()
        _notificationsEnabledFlow.value = enabled
    }

    fun isDriveAutoBackupEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DRIVE_AUTO_BACKUP, true)
    }

    fun setDriveAutoBackupEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DRIVE_AUTO_BACKUP, enabled)
            .apply()
        _driveAutoBackupEnabledFlow.value = enabled
    }

    fun isFirebaseAutoBackupEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIREBASE_AUTO_BACKUP, true)
    }

    fun setFirebaseAutoBackupEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FIREBASE_AUTO_BACKUP, enabled)
            .apply()
        _firebaseAutoBackupEnabledFlow.value = enabled
    }

    fun hasPendingFirebaseBackup(context: Context): Boolean {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING_FIREBASE_BACKUP, false)
    }

    fun setPendingFirebaseBackup(context: Context, pending: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_FIREBASE_BACKUP, pending)
            .apply()
    }

    fun getLastFirebaseBackupTime(context: Context): Long {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_FIREBASE_BACKUP_TIME, 0L)
    }

    fun updateFirebaseStatus(
        context: Context,
        state: BackupState,
        timestamp: Long = getLastFirebaseBackupTime(context),
        errorMessage: String? = null,
        userEmail: String? = _firebaseBackupStatusFlow.value.userEmail,
        snapshotCount: Int = _firebaseBackupStatusFlow.value.snapshotCount
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_LAST_FIREBASE_BACKUP_STATE, state.name)
            .putLong(KEY_LAST_FIREBASE_BACKUP_TIME, timestamp)
            .putInt(KEY_FIREBASE_SNAPSHOT_COUNT, snapshotCount)
        if (errorMessage != null) {
            editor.putString(KEY_LAST_FIREBASE_BACKUP_ERROR, errorMessage)
        } else {
            editor.remove(KEY_LAST_FIREBASE_BACKUP_ERROR)
        }
        editor.apply()

        _firebaseBackupStatusFlow.value = FirebaseBackupStatus(
            state = state,
            lastBackupTimestamp = timestamp,
            lastBackupFormatted = if (timestamp > 0L) LedgerDateFormatter.formatRelativeTimestamp(timestamp) else null,
            errorMessage = errorMessage,
            userEmail = userEmail,
            snapshotCount = snapshotCount
        )
    }

    fun refreshFirebaseStatus(
        context: Context,
        isSignedIn: Boolean = false,
        userEmail: String? = null,
        snapshotCount: Int? = null
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_LAST_FIREBASE_BACKUP_TIME, 0L)
        val storedStateName = prefs.getString(KEY_LAST_FIREBASE_BACKUP_STATE, null)
        val storedError = prefs.getString(KEY_LAST_FIREBASE_BACKUP_ERROR, null)
        val isPending = prefs.getBoolean(KEY_PENDING_FIREBASE_BACKUP, false)
        val count = snapshotCount ?: prefs.getInt(KEY_FIREBASE_SNAPSHOT_COUNT, 0)

        val state = when {
            !isSignedIn -> BackupState.NOT_CONFIGURED
            storedStateName == BackupState.IN_PROGRESS.name -> BackupState.IN_PROGRESS
            isPending && !BackupManager.isNetworkAvailable(context) -> BackupState.PENDING_OFFLINE
            storedStateName == BackupState.FAILED.name -> BackupState.FAILED
            isPending -> BackupState.PENDING_OFFLINE
            else -> BackupState.SUCCESS
        }

        _firebaseBackupStatusFlow.value = FirebaseBackupStatus(
            state = state,
            lastBackupTimestamp = lastTime,
            lastBackupFormatted = if (lastTime > 0L) LedgerDateFormatter.formatRelativeTimestamp(lastTime) else null,
            errorMessage = if (state == BackupState.FAILED) storedError else null,
            userEmail = userEmail,
            snapshotCount = count
        )
    }
}
