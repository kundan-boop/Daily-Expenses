package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseManager {
    private const val TAG = "DatabaseManager"

    fun optimizeDatabase(db: AppDatabase) {
        try {
            val sdb = db.openHelper.writableDatabase
            sdb.execSQL("PRAGMA auto_vacuum = FULL;")
            sdb.execSQL("PRAGMA optimize;")
            Log.d(TAG, "Database optimization executed: auto_vacuum = FULL & optimize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute database optimization: ${e.message}", e)
        }
    }

    fun schedulePeriodicOptimization(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                optimizeDatabase(db)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule database optimization: ${e.message}", e)
            }
        }
    }
}
