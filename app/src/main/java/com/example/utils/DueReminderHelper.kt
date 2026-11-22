package com.example.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import com.example.work.DueReminderWorker
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DueReminderTarget(
    val customerId: Long,
    val customerName: String,
    val mobileNumber: String,
    val dueDateMillis: Long,
    val netBalance: Double
)

object DueReminderHelper {
    private const val TAG = "DueReminderHelper"
    const val DUE_REMINDERS_CHANNEL_ID = "due_reminders_channel"
    const val DUE_REMINDER_NOTIFICATION_ID = 4102

    fun endOfTodayUtcMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val local = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                23,
                59,
                59
            )
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun isDueOrOverdue(dueDateMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (dueDateMillis == null || dueDateMillis <= 0L) return false
        return dueDateMillis <= endOfTodayUtcMillis(nowMillis)
    }

    fun isEligibleForDueReminder(
        masterEnabled: Boolean,
        customerReminderEnabled: Boolean,
        collectDueDate: Long?,
        netBalance: Double,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!masterEnabled || !customerReminderEnabled) return false
        if (collectDueDate == null || collectDueDate <= 0L) return false
        if (netBalance <= 0.01) return false
        return collectDueDate <= endOfTodayUtcMillis(nowMillis)
    }

    fun computeDelayMillisUntilNextRun(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= nowMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - nowMillis).coerceAtLeast(1000L)
    }

    fun scheduleDailyDueReminder(
        context: Context,
        hour: Int,
        minute: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE
    ) {
        try {
            val initialDelay = computeDelayMillisUntilNextRun(hour, minute)
            val request = PeriodicWorkRequestBuilder<DueReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("due_date_daily_reminder")
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                DueReminderWorker.WORK_NAME,
                policy,
                request
            )
            Log.d(TAG, "Scheduled daily due reminder at %02d:%02d (delay=${initialDelay}ms)".format(hour, minute))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule due reminder work: ${e.message}", e)
        }
    }

    fun cancelDueReminder(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(DueReminderWorker.WORK_NAME)
            Log.d(TAG, "Cancelled daily due reminder work.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel due reminder work: ${e.message}", e)
        }
    }

    fun ensureScheduledIfEnabled(context: Context) {
        if (AppPreferencesManager.isDueReminderEnabled(context)) {
            scheduleDailyDueReminder(
                context = context,
                hour = AppPreferencesManager.getDueReminderHour(context),
                minute = AppPreferencesManager.getDueReminderMinute(context),
                policy = ExistingPeriodicWorkPolicy.KEEP
            )
        } else {
            cancelDueReminder(context)
        }
    }

    fun formatDueDateUtc(dueDateMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(dueDateMillis))
    }

    fun formatReminderTime(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
        }
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(DUE_REMINDERS_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    DUE_REMINDERS_CHANNEL_ID,
                    "Payment Due Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily reminders for customers whose payment due date has arrived"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun postDueSummaryNotification(context: Context, targets: List<DueReminderTarget>): Boolean {
        if (targets.isEmpty() || !canPostNotifications(context)) return false
        ensureNotificationChannel(context)

        val totalDue = targets.sumOf { it.netBalance }
        val amountText = String.format(Locale.US, "₹%,.0f", totalDue)
        val title = if (targets.size == 1) {
            "Payment due: ${targets.first().customerName}"
        } else {
            "${targets.size} customers due ($amountText)"
        }
        val body = if (targets.size == 1) {
            "Pending receivable: $amountText (due ${formatDueDateUtc(targets.first().dueDateMillis)})"
        } else {
            val names = targets.take(3).joinToString(", ") { it.customerName }
            val extra = if (targets.size > 3) " +${targets.size - 3} more" else ""
            "Due from $names$extra"
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            DUE_REMINDER_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DUE_REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(DUE_REMINDER_NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission rejected: ${e.message}")
            false
        }
    }

    fun buildReminderMessage(customerName: String, netBalance: Double, dueDateMillis: Long?): String {
        val amountFormatted = String.format(Locale.US, "₹%,.2f", kotlin.math.abs(netBalance))
        val duePart = dueDateMillis?.takeIf { it > 0L }?.let { " (due date: ${formatDueDateUtc(it)})" } ?: ""
        return "Hello $customerName,\n\nFriendly payment reminder for your pending balance of $amountFormatted$duePart.\n\nPlease settle at your convenience. Thank you!"
    }

    fun buildWhatsAppIntent(mobileNumber: String, message: String): Intent {
        val digits = mobileNumber.filter { it.isDigit() }
        val normalizedPhone = when {
            digits.length == 10 -> "91$digits"
            else -> digits
        }
        val encodedMsg = URLEncoder.encode(message, "UTF-8")
        val url = if (normalizedPhone.isNotBlank()) {
            "https://wa.me/$normalizedPhone?text=$encodedMsg"
        } else {
            "https://wa.me/?text=$encodedMsg"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildSmsIntent(mobileNumber: String, message: String): Intent {
        val cleanNumber = mobileNumber.trim()
        val uri = if (cleanNumber.isNotBlank()) {
            Uri.parse("smsto:${Uri.encode(cleanNumber)}")
        } else {
            Uri.parse("smsto:")
        }
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
