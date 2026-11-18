package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.utils.BackupManager
import com.example.utils.BackupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "google_drive_auto_backup_work"
        private const val TAG = "DriveBackupWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "DriveBackupWorker triggered by WorkManager (attempt $runAttemptCount)...")

        val driveFolderUri = BackupManager.getDriveFolderUri(applicationContext)
        val driveEnabled = driveFolderUri != null &&
            com.example.utils.AppPreferencesManager.isDriveAutoBackupEnabled(applicationContext)
        val firebaseEnabled = BackupManager.isFirebaseAutoBackupReady(applicationContext)

        if (!driveEnabled && !firebaseEnabled) {
            Log.d(TAG, "Neither Drive nor Firebase auto-backup is active. Worker exiting.")
            if (driveFolderUri == null) {
                BackupManager.setPendingDriveBackup(applicationContext, false)
                BackupManager.updateBackupStatus(
                    applicationContext,
                    BackupState.NOT_CONFIGURED,
                    0L,
                    null
                )
            }
            return@withContext Result.success()
        }

        val isOnline = BackupManager.isNetworkAvailable(applicationContext)
        if (!isOnline) {
            Log.d(TAG, "Device is offline. Queuing cloud backup retry.")
            if (driveEnabled) {
                BackupManager.setPendingDriveBackup(applicationContext, true)
                BackupManager.updateBackupStatus(
                    applicationContext,
                    BackupState.PENDING_OFFLINE,
                    BackupManager.getLastDriveBackupTime(applicationContext),
                    "Offline: waiting for network connection"
                )
            }
            if (firebaseEnabled) {
                com.example.utils.AppPreferencesManager.setPendingFirebaseBackup(applicationContext, true)
                com.example.utils.AppPreferencesManager.updateFirebaseStatus(
                    context = applicationContext,
                    state = BackupState.PENDING_OFFLINE,
                    errorMessage = "Offline: waiting for network connection"
                )
            }
            return@withContext Result.retry()
        }

        try {
            val pkg = BackupManager.exportFullBackupPackage(applicationContext)
            if (pkg.jsonString.isBlank()) {
                Log.w(TAG, "Database export produced empty JSON. Skipping upload.")
                return@withContext Result.success()
            }

            // Always update 2-day local daily backup
            BackupManager.performDailyBackup(applicationContext, pkg.jsonString)

            var needsRetry = false

            if (driveEnabled) {
                BackupManager.updateBackupStatus(
                    applicationContext,
                    BackupState.IN_PROGRESS,
                    BackupManager.getLastDriveBackupTime(applicationContext),
                    null
                )
                val driveOk = BackupManager.performDriveAutoBackup(applicationContext, pkg.jsonString)
                if (driveOk) {
                    val now = System.currentTimeMillis()
                    BackupManager.setPendingDriveBackup(applicationContext, false)
                    BackupManager.updateBackupStatus(
                        applicationContext,
                        BackupState.SUCCESS,
                        now,
                        null
                    )
                } else {
                    needsRetry = true
                    BackupManager.setPendingDriveBackup(applicationContext, true)
                    BackupManager.updateBackupStatus(
                        applicationContext,
                        BackupState.FAILED,
                        BackupManager.getLastDriveBackupTime(applicationContext),
                        "Upload failed. Will retry automatically."
                    )
                }
            }

            if (firebaseEnabled) {
                val firebaseOk = BackupManager.performPendingFirebaseBackup(applicationContext)
                if (!firebaseOk) {
                    needsRetry = true
                }
            }

            return@withContext if (needsRetry) {
                Result.retry()
            } else {
                BackupManager.markBackupCompleted(applicationContext)
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during DriveBackupWorker: ${e.message}", e)
            if (driveEnabled) {
                BackupManager.setPendingDriveBackup(applicationContext, true)
                BackupManager.updateBackupStatus(
                    applicationContext,
                    BackupState.FAILED,
                    BackupManager.getLastDriveBackupTime(applicationContext),
                    e.message ?: "Backup failed"
                )
            }
            Result.retry()
        }
    }
}
