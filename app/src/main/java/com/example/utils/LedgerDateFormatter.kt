package com.example.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shared thread-safe date/time formatters so customer last-updated dates and
 * entry timestamps always include the year consistently across every screen.
 */
object LedgerDateFormatter {

    fun formatDateWithYear(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return ""
        val sdf = SimpleDateFormat("dd MMM yyyy", locale)
        return sdf.format(Date(timestamp))
    }

    fun formatDateTimeWithYear(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return ""
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", locale)
        return sdf.format(Date(timestamp))
    }

    /**
     * Customer list & activity summary format: e.g. "16 Nov 2026, 2:10 PM"
     */
    fun formatLastEntryDateTime(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return ""
        val sdf = SimpleDateFormat("d MMM yyyy, h:mm a", locale)
        return sdf.format(Date(timestamp))
    }

    fun formatCompactRupee(amount: Double, includeSign: Boolean = false): String {
        val sign = if (includeSign) {
            when {
                amount > 0.005 -> "+"
                amount < -0.005 -> "-"
                else -> ""
            }
        } else if (amount < -0.005) "-" else ""
        val abs = kotlin.math.abs(amount)
        val body = when {
            abs >= 10_000_000.0 -> String.format(Locale.US, "%.1fCr", abs / 10_000_000.0).replace(".0Cr", "Cr")
            abs >= 100_000.0 -> String.format(Locale.US, "%.1fL", abs / 100_000.0).replace(".0L", "L")
            abs >= 1_000.0 -> String.format(Locale.US, "%.1fK", abs / 1_000.0).replace(".0K", "K")
            abs % 1.0 == 0.0 -> String.format(Locale.US, "%.0f", abs)
            else -> String.format(Locale.US, "%.1f", abs)
        }
        return "${sign}₹$body"
    }

    fun formatRelativeTimestamp(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return ""
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return if (now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        ) {
            "Today at ${SimpleDateFormat("hh:mm a", locale).format(Date(timestamp))}"
        } else {
            formatDateTimeWithYear(timestamp, locale)
        }
    }
}
