package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.utils.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "periodic_local_backup_work"
        private const val TAG = "LocalBackupWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "LocalBackupWorker triggered by WorkManager (attempt $runAttemptCount)...")
        try {
            val pkg = BackupManager.exportFullBackupPackage(applicationContext)
            if (pkg.jsonString.isBlank()) {
                Log.w(TAG, "Export produced empty backup JSON; skipping write.")
                return@withContext Result.success()
            }
            val written = BackupManager.performDailyBackup(applicationContext, pkg.jsonString)
            if (written != null) {
                Log.d(TAG, "7-day local backup snapshot updated cleanly (retaining up to 4 copies).")
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "LocalBackupWorker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
