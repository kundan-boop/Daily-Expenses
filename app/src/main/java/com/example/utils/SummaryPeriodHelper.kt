package com.example.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SummaryGranularity {
    DAILY,
    MONTHLY,
    YEARLY
}

data class PeriodRange(
    val startMillis: Long,
    val endMillis: Long
)

data class SummaryAnchor(
    val year: Int,
    val monthZeroBased: Int, // 0..11
    val dayOfMonth: Int
) {
    companion object {
        fun today(timeZone: TimeZone = TimeZone.getDefault()): SummaryAnchor {
            val cal = Calendar.getInstance(timeZone)
            return SummaryAnchor(
                year = cal.get(Calendar.YEAR),
                monthZeroBased = cal.get(Calendar.MONTH),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun fromMillis(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): SummaryAnchor {
            val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
            return SummaryAnchor(
                year = cal.get(Calendar.YEAR),
                monthZeroBased = cal.get(Calendar.MONTH),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}

/**
 * Computes half-open `[startMillis, endMillis)` boundaries using device timezone midnight
 * so boundary timestamps (23:59:59.999 vs 00:00:00.000) never duplicate or drop entries.
 */
object SummaryPeriodHelper {

    fun dayRange(year: Int, monthZeroBased: Int, dayOfMonth: Int, timeZone: TimeZone = TimeZone.getDefault()): PeriodRange {
        val startCal = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, monthZeroBased, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        return PeriodRange(startCal.timeInMillis, endCal.timeInMillis)
    }

    fun monthRange(year: Int, monthZeroBased: Int, timeZone: TimeZone = TimeZone.getDefault()): PeriodRange {
        val startCal = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, monthZeroBased, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
        }
        return PeriodRange(startCal.timeInMillis, endCal.timeInMillis)
    }

    fun yearRange(year: Int, timeZone: TimeZone = TimeZone.getDefault()): PeriodRange {
        val startCal = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.YEAR, 1)
        }
        return PeriodRange(startCal.timeInMillis, endCal.timeInMillis)
    }

    fun periodRange(
        granularity: SummaryGranularity,
        anchor: SummaryAnchor,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PeriodRange = when (granularity) {
        SummaryGranularity.DAILY -> dayRange(anchor.year, anchor.monthZeroBased, anchor.dayOfMonth, timeZone)
        SummaryGranularity.MONTHLY -> monthRange(anchor.year, anchor.monthZeroBased, timeZone)
        SummaryGranularity.YEARLY -> yearRange(anchor.year, timeZone)
    }

    /**
     * For chart rendering:
     * - DAILY shows all days of the selected day's month (bars = days 1..N, selected day highlighted).
     * - MONTHLY shows all 12 months of the selected year (bars = Jan..Dec, selected month highlighted).
     * - YEARLY shows all distinct years present in the ledger (wide bounds).
     */
    fun chartRange(
        granularity: SummaryGranularity,
        anchor: SummaryAnchor,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PeriodRange = when (granularity) {
        SummaryGranularity.DAILY -> monthRange(anchor.year, anchor.monthZeroBased, timeZone)
        SummaryGranularity.MONTHLY -> yearRange(anchor.year, timeZone)
        SummaryGranularity.YEARLY -> PeriodRange(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2)
    }

    fun canStepForward(
        granularity: SummaryGranularity,
        anchor: SummaryAnchor,
        today: SummaryAnchor = SummaryAnchor.today()
    ): Boolean = when (granularity) {
        SummaryGranularity.DAILY -> {
            val anchorKey = anchor.year * 10000 + anchor.monthZeroBased * 100 + anchor.dayOfMonth
            val todayKey = today.year * 10000 + today.monthZeroBased * 100 + today.dayOfMonth
            anchorKey < todayKey
        }
        SummaryGranularity.MONTHLY -> {
            val anchorKey = anchor.year * 100 + anchor.monthZeroBased
            val todayKey = today.year * 100 + today.monthZeroBased
            anchorKey < todayKey
        }
        SummaryGranularity.YEARLY -> anchor.year < today.year
    }

    fun stepAnchor(
        granularity: SummaryGranularity,
        anchor: SummaryAnchor,
        delta: Int,
        today: SummaryAnchor = SummaryAnchor.today(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): SummaryAnchor {
        val cal = Calendar.getInstance(timeZone).apply {
            clear()
            set(anchor.year, anchor.monthZeroBased, anchor.dayOfMonth, 12, 0, 0)
            when (granularity) {
                SummaryGranularity.DAILY -> add(Calendar.DAY_OF_MONTH, delta)
                SummaryGranularity.MONTHLY -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, delta)
                }
                SummaryGranularity.YEARLY -> {
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.YEAR, delta)
                }
            }
        }
        val candidate = SummaryAnchor(
            year = cal.get(Calendar.YEAR),
            monthZeroBased = cal.get(Calendar.MONTH),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        )
        if (delta > 0 && isAfterPeriod(granularity, candidate, today)) {
            return today
        }
        return candidate
    }

    private fun isAfterPeriod(
        granularity: SummaryGranularity,
        candidate: SummaryAnchor,
        reference: SummaryAnchor
    ): Boolean = when (granularity) {
        SummaryGranularity.DAILY ->
            (candidate.year * 10000 + candidate.monthZeroBased * 100 + candidate.dayOfMonth) >
                (reference.year * 10000 + reference.monthZeroBased * 100 + reference.dayOfMonth)
        SummaryGranularity.MONTHLY ->
            (candidate.year * 100 + candidate.monthZeroBased) >
                (reference.year * 100 + reference.monthZeroBased)
        SummaryGranularity.YEARLY -> candidate.year > reference.year
    }

    fun clampToToday(anchor: SummaryAnchor, today: SummaryAnchor = SummaryAnchor.today()): SummaryAnchor {
        val aKey = anchor.year * 10000 + anchor.monthZeroBased * 100 + anchor.dayOfMonth
        val tKey = today.year * 10000 + today.monthZeroBased * 100 + today.dayOfMonth
        return if (aKey > tKey) today else anchor
    }

    fun formatPeriodLabel(
        granularity: SummaryGranularity,
        anchor: SummaryAnchor,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val cal = Calendar.getInstance(timeZone).apply {
            clear()
            set(anchor.year, anchor.monthZeroBased, anchor.dayOfMonth, 12, 0, 0)
        }
        return when (granularity) {
            SummaryGranularity.DAILY -> SimpleDateFormat("d MMM yyyy", locale).format(Date(cal.timeInMillis))
            SummaryGranularity.MONTHLY -> SimpleDateFormat("MMMM yyyy", locale).format(Date(cal.timeInMillis))
            SummaryGranularity.YEARLY -> anchor.year.toString()
        }
    }

    fun dayBucketKey(year: Int, monthZeroBased: Int, dayOfMonth: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, monthZeroBased + 1, dayOfMonth)

    fun monthBucketKey(year: Int, monthZeroBased: Int): String =
        String.format(Locale.US, "%04d-%02d", year, monthZeroBased + 1)

    fun localDayKeyFromMillis(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return dayBucketKey(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun localMonthKeyFromMillis(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return monthBucketKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun formatDayHeaderLabel(dayKey: String, locale: Locale = Locale.getDefault()): String {
        return try {
            val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val sdfOut = SimpleDateFormat("EEE, d MMM yyyy", locale)
            val date = sdfIn.parse(dayKey)
            if (date != null) sdfOut.format(date) else dayKey
        } catch (_: Exception) {
            dayKey
        }
    }

    fun formatMonthHeaderLabel(monthKey: String, locale: Locale = Locale.getDefault()): String {
        return try {
            val sdfIn = SimpleDateFormat("yyyy-MM", Locale.US)
            val sdfOut = SimpleDateFormat("MMMM yyyy", locale)
            val date = sdfIn.parse(monthKey)
            if (date != null) sdfOut.format(date) else monthKey
        } catch (_: Exception) {
            monthKey
        }
    }

    fun parseBucketKeyToRange(bucketKey: String, timeZone: TimeZone = TimeZone.getDefault()): PeriodRange? {
        val parts = bucketKey.split("-")
        return when (parts.size) {
            3 -> {
                val y = parts[0].toIntOrNull() ?: return null
                val m = (parts[1].toIntOrNull() ?: return null) - 1
                val d = parts[2].toIntOrNull() ?: return null
                dayRange(y, m, d, timeZone)
            }
            2 -> {
                val y = parts[0].toIntOrNull() ?: return null
                val m = (parts[1].toIntOrNull() ?: return null) - 1
                monthRange(y, m, timeZone)
            }
            1 -> {
                val y = parts[0].toIntOrNull() ?: return null
                yearRange(y, timeZone)
            }
            else -> null
        }
    }

    fun daysInMonth(year: Int, monthZeroBased: Int, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val cal = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, monthZeroBased, 1, 12, 0, 0)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
