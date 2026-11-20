package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class DbStartupState {
    data object Checking : DbStartupState()
    data class Ready(
        val dbVersion: Int,
        val hasPreservedSafetyCopy: Boolean = false
    ) : DbStartupState()

    data class MigrationFailed(
        val errorMessage: String,
        val technicalDetail: String,
        val hasSafetyCopy: Boolean,
        val safetyCopyTimestamp: Long
    ) : DbStartupState()
}

/**
 * Protects existing Room database data across app updates:
 * 1. On the first launch of a new app version (before any Room migration runs),
 *    checkpoints WAL frames and copies `daily_expense_db` (+ `-wal` and `-shm`)
 *    into private storage (`filesDir/db_migration_safety/`).
 * 2. Keeps that safety copy intact until the NEXT successful launch on the new version.
 * 3. If opening/migrating the database fails, never deletes or clears any data —
 *    publishes [DbStartupState.MigrationFailed] so the UI can show a clear error
 *    and offer restoring from the pre-migration safety copy.
 */
object DatabaseSafetyManager {
    private const val TAG = "DatabaseSafetyManager"
    private const val SAFETY_PREFS = "db_safety_prefs"
    private const val SAFETY_ROOT_DIR = "db_migration_safety"

    private const val KEY_LAST_VERIFIED_VERSION_CODE = "last_verified_version_code"
    private const val KEY_KEEP_UNTIL_NEXT_LAUNCH = "keep_safety_copy_until_next_launch"
    private const val KEY_SAFETY_COPY_DIR = "safety_copy_dir_path"
    private const val KEY_SAFETY_COPY_TIMESTAMP = "safety_copy_timestamp"

    private val lock = Any()

    @Volatile
    private var preparedThisProcess = false

    @Volatile
    private var createdSafetyCopyThisLaunch = false

    private val _startupState = MutableStateFlow<DbStartupState>(DbStartupState.Checking)
    val startupState: StateFlow<DbStartupState> = _startupState.asStateFlow()

    fun getAppVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (e: Exception) {
            1L
        }
    }

    private fun readRawSqliteUserVersion(dbFile: File): Int {
        if (!dbFile.exists() || dbFile.length() == 0L) return 0
        var sqlite: SQLiteDatabase? = null
        return try {
            sqlite = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            sqlite.version
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read raw SQLite user_version: ${e.message}")
            -1
        } finally {
            try {
                sqlite?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun checkpointWalBeforeCopy(dbFile: File) {
        val walFile = File("${dbFile.absolutePath}-wal")
        if (!dbFile.exists() || !walFile.exists() || walFile.length() == 0L) return
        if (AppDatabase.isInstanceOpen()) return

        var sqlite: SQLiteDatabase? = null
        try {
            sqlite = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
            sqlite.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)?.use { cursor ->
                cursor.moveToFirst()
            }
            Log.d(TAG, "WAL checkpoint completed before pre-migration safety copy.")
        } catch (e: Exception) {
            Log.w(TAG, "Pre-copy WAL checkpoint skipped/warning: ${e.message}")
        } finally {
            try {
                sqlite?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Must execute BEFORE [AppDatabase.getDatabase] runs any Room migration in this process.
     */
    fun prepareBeforeDatabaseOpen(
        context: Context,
        currentVersionCodeOverride: Long? = null
    ) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (preparedThisProcess) return

            val prefs = appContext.getSharedPreferences(SAFETY_PREFS, Context.MODE_PRIVATE)
            val currentVersionCode = currentVersionCodeOverride ?: getAppVersionCode(appContext)
            val lastVerifiedVersion = prefs.getLong(KEY_LAST_VERIFIED_VERSION_CODE, -1L)
            val dbFile = appContext.getDatabasePath(AppDatabase.DATABASE_NAME)

            if (dbFile.exists() && dbFile.length() > 0L) {
                val rawDbVersion = readRawSqliteUserVersion(dbFile)
                val isFirstLaunchOfNewVersion = lastVerifiedVersion != currentVersionCode
                val hasPendingSchemaUpgrade = rawDbVersion in 1 until AppDatabase.DATABASE_VERSION

                if (isFirstLaunchOfNewVersion || hasPendingSchemaUpgrade) {
                    val createdDir = createSafetyCopyLocked(appContext, dbFile, currentVersionCode)
                    if (createdDir != null) {
                        val existingDirPath = prefs.getString(KEY_SAFETY_COPY_DIR, null)
                        if (!existingDirPath.isNullOrBlank() && existingDirPath != createdDir.absolutePath) {
                            deleteDirSafely(File(existingDirPath))
                        }
                        prefs.edit()
                            .putString(KEY_SAFETY_COPY_DIR, createdDir.absolutePath)
                            .putLong(KEY_SAFETY_COPY_TIMESTAMP, System.currentTimeMillis())
                            .putBoolean(KEY_KEEP_UNTIL_NEXT_LAUNCH, true)
                            .apply()
                        createdSafetyCopyThisLaunch = true
                    }
                }
            }

            preparedThisProcess = true
        }
    }

    private fun createSafetyCopyLocked(
        context: Context,
        dbFile: File,
        targetVersionCode: Long
    ): File? {
        return try {
            checkpointWalBeforeCopy(dbFile)

            val safetyRoot = File(context.filesDir, SAFETY_ROOT_DIR).apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val stagingDir = File(safetyRoot, "staging_${targetVersionCode}_$timestamp").apply { mkdirs() }
            val finalDir = File(safetyRoot, "snapshot_v${targetVersionCode}_$timestamp")

            val destDb = File(stagingDir, AppDatabase.DATABASE_NAME)
            dbFile.copyTo(destDb, overwrite = true)
            if (!destDb.exists() || destDb.length() != dbFile.length() || destDb.length() == 0L) {
                deleteDirSafely(stagingDir)
                return null
            }

            val walSrc = File("${dbFile.absolutePath}-wal")
            if (walSrc.exists()) {
                walSrc.copyTo(File(stagingDir, "${AppDatabase.DATABASE_NAME}-wal"), overwrite = true)
            }
            val shmSrc = File("${dbFile.absolutePath}-shm")
            if (shmSrc.exists()) {
                shmSrc.copyTo(File(stagingDir, "${AppDatabase.DATABASE_NAME}-shm"), overwrite = true)
            }

            if (!stagingDir.renameTo(finalDir)) {
                finalDir.mkdirs()
                stagingDir.listFiles()?.forEach { child ->
                    child.copyTo(File(finalDir, child.name), overwrite = true)
                }
                deleteDirSafely(stagingDir)
            }

            Log.d(TAG, "Created pre-migration safety copy at ${finalDir.absolutePath}")
            finalDir
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pre-migration safety copy: ${e.message}", e)
            null
        }
    }

    /**
     * Runs preparation + opens/migrates Room safely, catching any migration failure without wiping data.
     */
    fun prepareAndVerifySync(
        context: Context,
        currentVersionCodeOverride: Long? = null,
        dbVersionOpener: (Context) -> Int = { ctx ->
            val db = AppDatabase.getDatabase(ctx)
            db.openHelper.writableDatabase.version
        }
    ): DbStartupState {
        val appContext = context.applicationContext
        synchronized(lock) {
            prepareBeforeDatabaseOpen(appContext, currentVersionCodeOverride)

            val prefs = appContext.getSharedPreferences(SAFETY_PREFS, Context.MODE_PRIVATE)
            val currentVersionCode = currentVersionCodeOverride ?: getAppVersionCode(appContext)
            val lastVerifiedVersion = prefs.getLong(KEY_LAST_VERIFIED_VERSION_CODE, -1L)
            val keepUntilNextLaunch = prefs.getBoolean(KEY_KEEP_UNTIL_NEXT_LAUNCH, false)
            val safetyDirPath = prefs.getString(KEY_SAFETY_COPY_DIR, null)
            val safetyTimestamp = prefs.getLong(KEY_SAFETY_COPY_TIMESTAMP, 0L)

            return try {
                val openedVersion = dbVersionOpener(appContext)

                if (createdSafetyCopyThisLaunch) {
                    // Keep the safety copy intact throughout this first launch; delete on NEXT successful launch.
                    prefs.edit()
                        .putLong(KEY_LAST_VERIFIED_VERSION_CODE, currentVersionCode)
                        .putBoolean(KEY_KEEP_UNTIL_NEXT_LAUNCH, true)
                        .apply()
                } else if (keepUntilNextLaunch && lastVerifiedVersion == currentVersionCode) {
                    // This is the next successful launch after the upgrade — safe to retire old safety copy.
                    if (!safetyDirPath.isNullOrBlank()) {
                        deleteDirSafely(File(safetyDirPath))
                    }
                    prefs.edit()
                        .remove(KEY_SAFETY_COPY_DIR)
                        .remove(KEY_SAFETY_COPY_TIMESTAMP)
                        .putBoolean(KEY_KEEP_UNTIL_NEXT_LAUNCH, false)
                        .apply()
                } else {
                    prefs.edit()
                        .putLong(KEY_LAST_VERIFIED_VERSION_CODE, currentVersionCode)
                        .apply()
                }

                val state = DbStartupState.Ready(
                    dbVersion = openedVersion,
                    hasPreservedSafetyCopy = hasValidSafetyCopy(appContext)
                )
                _startupState.value = state
                state
            } catch (t: Throwable) {
                Log.e(TAG, "Database open/migration failed; preserving data & safety copy", t)
                AppDatabase.resetInstanceForRecovery()
                val state = DbStartupState.MigrationFailed(
                    errorMessage = t.localizedMessage ?: "Database upgrade failed without data loss.",
                    technicalDetail = t.stackTraceToString().take(1500),
                    hasSafetyCopy = hasValidSafetyCopy(appContext),
                    safetyCopyTimestamp = safetyTimestamp
                )
                _startupState.value = state
                state
            }
        }
    }

    suspend fun ensurePreparedAndVerified(context: Context): DbStartupState {
        val current = _startupState.value
        if (current is DbStartupState.Ready) return current
        return withContext(Dispatchers.IO) {
            prepareAndVerifySync(context)
        }
    }

    fun hasValidSafetyCopy(context: Context): Boolean {
        val dir = getSafetyCopyDir(context) ?: return false
        val dbCopy = File(dir, AppDatabase.DATABASE_NAME)
        return dbCopy.exists() && dbCopy.length() > 0L
    }

    fun getSafetyCopyDir(context: Context): File? {
        val prefs = context.applicationContext.getSharedPreferences(SAFETY_PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_SAFETY_COPY_DIR, null) ?: return null
        val dir = File(path)
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    fun getSafetyCopyDbFile(context: Context): File? {
        val dir = getSafetyCopyDir(context) ?: return null
        val dbCopy = File(dir, AppDatabase.DATABASE_NAME)
        return if (dbCopy.exists() && dbCopy.length() > 0L) dbCopy else null
    }

    /**
     * Restores `daily_expense_db` (+ `-wal` and `-shm`) from the pre-migration safety copy,
     * preserving the failed DB snapshot separately before restoring, then re-attempts opening.
     */
    fun restoreFromSafetyCopy(
        context: Context,
        currentVersionCodeOverride: Long? = null,
        dbVersionOpener: (Context) -> Int = { ctx ->
            val db = AppDatabase.getDatabase(ctx)
            db.openHelper.writableDatabase.version
        }
    ): Result<Int> {
        val appContext = context.applicationContext
        synchronized(lock) {
            val safetyDir = getSafetyCopyDir(appContext)
                ?: return Result.failure(IllegalStateException("No safety copy directory found."))
            val safetyDb = File(safetyDir, AppDatabase.DATABASE_NAME)
            if (!safetyDb.exists() || safetyDb.length() == 0L) {
                return Result.failure(IllegalStateException("Safety copy database file is missing or empty."))
            }

            return try {
                AppDatabase.resetInstanceForRecovery()

                val targetDb = appContext.getDatabasePath(AppDatabase.DATABASE_NAME)
                targetDb.parentFile?.mkdirs()

                // Preserve failed DB state separately in case user wants forensic inspection
                if (targetDb.exists() && targetDb.length() > 0L) {
                    val failedArchive = File(appContext.filesDir, "$SAFETY_ROOT_DIR/last_failed_attempt").apply { mkdirs() }
                    targetDb.copyTo(File(failedArchive, AppDatabase.DATABASE_NAME), overwrite = true)
                    val curWal = File("${targetDb.absolutePath}-wal")
                    if (curWal.exists()) curWal.copyTo(File(failedArchive, "${AppDatabase.DATABASE_NAME}-wal"), overwrite = true)
                    val curShm = File("${targetDb.absolutePath}-shm")
                    if (curShm.exists()) curShm.copyTo(File(failedArchive, "${AppDatabase.DATABASE_NAME}-shm"), overwrite = true)
                }

                // Clear current WAL/SHM before copying safety snapshot back
                val targetWal = File("${targetDb.absolutePath}-wal")
                val targetShm = File("${targetDb.absolutePath}-shm")
                if (targetWal.exists()) targetWal.delete()
                if (targetShm.exists()) targetShm.delete()

                safetyDb.copyTo(targetDb, overwrite = true)
                val safetyWal = File(safetyDir, "${AppDatabase.DATABASE_NAME}-wal")
                if (safetyWal.exists()) {
                    safetyWal.copyTo(targetWal, overwrite = true)
                }
                val safetyShm = File(safetyDir, "${AppDatabase.DATABASE_NAME}-shm")
                if (safetyShm.exists()) {
                    safetyShm.copyTo(targetShm, overwrite = true)
                }

                preparedThisProcess = true
                createdSafetyCopyThisLaunch = false
                val state = prepareAndVerifySync(
                    context = appContext,
                    currentVersionCodeOverride = currentVersionCodeOverride,
                    dbVersionOpener = dbVersionOpener
                )
                when (state) {
                    is DbStartupState.Ready -> Result.success(state.dbVersion)
                    is DbStartupState.MigrationFailed -> Result.failure(IllegalStateException(state.errorMessage))
                    DbStartupState.Checking -> Result.failure(IllegalStateException("Database verification pending."))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Restore from safety copy failed: ${t.message}", t)
                Result.failure(t)
            }
        }
    }

    fun exportSafetyCopyToUri(context: Context, destUri: Uri): Boolean {
        val sourceDb = getSafetyCopyDbFile(context) ?: return false
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                sourceDb.inputStream().use { input ->
                    input.copyTo(out)
                }
                out.flush()
            } ?: return false
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export safety copy: ${e.message}", e)
            false
        }
    }

    internal fun resetForTesting() {
        synchronized(lock) {
            preparedThisProcess = false
            createdSafetyCopyThisLaunch = false
            _startupState.value = DbStartupState.Checking
        }
    }

    private fun deleteDirSafely(dir: File) {
        try {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }
}
