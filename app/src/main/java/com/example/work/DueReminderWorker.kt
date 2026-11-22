package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.utils.AppPreferencesManager
import com.example.utils.DueReminderHelper
import com.example.utils.DueReminderTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DueReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "daily_due_date_reminder_work"
        private const val TAG = "DueReminderWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            AppPreferencesManager.init(applicationContext)
            val masterEnabled = AppPreferencesManager.isDueReminderEnabled(applicationContext)
            if (!masterEnabled) {
                Log.d(TAG, "Master due reminder switch is OFF. Skipping notification check.")
                return@withContext Result.success()
            }

            val db = AppDatabase.getDatabase(applicationContext)
            val customers = db.customerDao().getAllCustomersDirect()
            val transactions = db.transactionDao().getAllTransactionsDirect()
            val txByCustomer = transactions.groupBy { it.customerId }
            val now = System.currentTimeMillis()

            val targets = customers.mapNotNull { customer ->
                val due = customer.collectDueDate
                val custTxs = txByCustomer[customer.id].orEmpty()
                val paid = custTxs.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
                val received = custTxs.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
                val netBalance = paid - received

                if (DueReminderHelper.isEligibleForDueReminder(
                        masterEnabled = true,
                        customerReminderEnabled = customer.reminderEnabled,
                        collectDueDate = due,
                        netBalance = netBalance,
                        nowMillis = now
                    ) && due != null
                ) {
                    DueReminderTarget(
                        customerId = customer.id,
                        customerName = customer.name,
                        mobileNumber = customer.mobileNumber,
                        dueDateMillis = due,
                        netBalance = netBalance
                    )
                } else {
                    null
                }
            }

            if (targets.isNotEmpty()) {
                val posted = DueReminderHelper.postDueSummaryNotification(applicationContext, targets)
                Log.d(TAG, "Due reminder check found ${targets.size} due customer(s); posted=$posted")
            } else {
                Log.d(TAG, "No eligible overdue/due customers found today.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "DueReminderWorker error: ${e.message}", e)
            Result.retry()
        }
    }
}
