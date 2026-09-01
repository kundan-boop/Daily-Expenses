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
        if (driveFolderUri == null) {
            Log.d(TAG, "Google Drive folder not configured. Worker exiting.")
            BackupManager.setPendingDriveBackup(applicationContext, false)
            BackupManager.updateBackupStatus(
                applicationContext,
                BackupState.NOT_CONFIGURED,
                0L,
                null
            )
            return@withContext Result.success()
        }

        val isOnline = BackupManager.isNetworkAvailable(applicationContext)
        if (!isOnline) {
            Log.d(TAG, "Device is offline. Queuing backup to retry automatically when network is available.")
            BackupManager.setPendingDriveBackup(applicationContext, true)
            BackupManager.updateBackupStatus(
                applicationContext,
                BackupState.PENDING_OFFLINE,
                BackupManager.getLastDriveBackupTime(applicationContext),
                "Offline: waiting for network connection"
            )
            return@withContext Result.retry()
        }

        try {
            BackupManager.updateBackupStatus(
                applicationContext,
                BackupState.IN_PROGRESS,
                BackupManager.getLastDriveBackupTime(applicationContext),
                null
            )

            val jsonString = BackupManager.exportDatabaseToJson(applicationContext)
            if (jsonString.isBlank()) {
                Log.w(TAG, "Database export produced empty JSON. Skipping upload.")
                return@withContext Result.success()
            }

            // Write local backup first
            BackupManager.performDailyBackup(applicationContext, jsonString)

            // Perform Drive backup with same-day overwrite & 3-day rotation inside DailyExpenseBackups
            val success = BackupManager.performDriveAutoBackup(applicationContext, jsonString)

            if (success) {
                Log.d(TAG, "WorkManager Google Drive auto-backup succeeded!")
                val now = System.currentTimeMillis()
                BackupManager.setPendingDriveBackup(applicationContext, false)
                BackupManager.markBackupCompleted(applicationContext)
                BackupManager.updateBackupStatus(
                    applicationContext,
                    BackupState.SUCCESS,
                    now,
                    null
                )
                Result.success()
            } else {
                Log.e(TAG, "Google Drive auto-backup upload failed.")
                BackupManager.setPendingDriveBackup(applicationContext, true)
                BackupManager.updateBackupStatus(
                    applicationContext,
                    BackupState.FAILED,
                    BackupManager.getLastDriveBackupTime(applicationContext),
                    "Upload failed. Will retry automatically."
                )
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during DriveBackupWorker: ${e.message}", e)
            BackupManager.setPendingDriveBackup(applicationContext, true)
            BackupManager.updateBackupStatus(
                applicationContext,
                BackupState.FAILED,
                BackupManager.getLastDriveBackupTime(applicationContext),
                e.message ?: "Backup failed"
            )
            Result.retry()
        }
    }
}
