package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CustomerEntity
import com.example.data.TransactionEntity
import com.example.ui.DailyExpenseViewModel
import com.example.utils.PdfDateFilterMode
import com.example.utils.PdfReportExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomerLedgerFeaturesTest {

    private fun ts(year: Int, monthZeroBased: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun sampleTransactions(): List<TransactionEntity> = listOf(
        TransactionEntity(
            id = 1L, customerId = 10L, transactionType = "I_PAID",
            amount = 100.0, dateTimestamp = ts(2024, Calendar.FEBRUARY, 29, 9, 0), comment = "Leap Day Milk"
        ),
        TransactionEntity(
            id = 2L, customerId = 10L, transactionType = "I_RECEIVED",
            amount = 40.0, dateTimestamp = ts(2025, Calendar.MARCH, 15, 14, 30), comment = "March 2025 Cash"
        ),
        TransactionEntity(
            id = 3L, customerId = 10L, transactionType = "I_PAID",
            amount = 250.0, dateTimestamp = ts(2026, Calendar.JANUARY, 5, 10, 0), comment = "Jan 2026 Supplies"
        ),
        TransactionEntity(
            id = 4L, customerId = 10L, transactionType = "I_PAID",
            amount = 50.0, dateTimestamp = ts(2026, Calendar.JANUARY, 5, 18, 0), comment = "Jan 2026 Evening"
        ),
        TransactionEntity(
            id = 5L, customerId = 10L, transactionType = "I_RECEIVED",
            amount = 150.0, dateTimestamp = ts(2026, Calendar.FEBRUARY, 20, 11, 0), comment = "Feb 2026 Part Pay"
        )
    )

    @Test
    fun testPdfDateFilter_specificMonth() {
        val allTxs = sampleTransactions()
        val res = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.SPECIFIC_MONTH,
            selectedMonth = Calendar.JANUARY,
            selectedMonthYear = 2026,
            locale = Locale.US
        )

        assertEquals("January 2026", res.periodLabel)
        assertEquals(listOf(3L, 4L), res.transactions.map { it.id })
    }

    @Test
    fun testPdfDateFilter_specificMonth_leapYearBoundary() {
        val allTxs = sampleTransactions()
        val res = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.SPECIFIC_MONTH,
            selectedMonth = Calendar.FEBRUARY,
            selectedMonthYear = 2024,
            locale = Locale.US
        )

        assertEquals("February 2024", res.periodLabel)
        assertEquals(listOf(1L), res.transactions.map { it.id })
    }

    @Test
    fun testPdfDateFilter_specificDayAndDateRange() {
        val allTxs = sampleTransactions()

        // Single day: 05 Jan 2026 (should include morning + evening on Jan 5, exclude Jan 6 or Feb 2026)
        val dayMillis = ts(2026, Calendar.JANUARY, 5, 15, 0)
        val singleDay = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.DATE_RANGE,
            startDateMillis = dayMillis,
            endDateMillis = dayMillis,
            locale = Locale.US
        )
        assertEquals(listOf(3L, 4L), singleDay.transactions.map { it.id })

        // Multi-day range: 01 Jan 2026 .. 20 Feb 2026
        val rangeResult = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.DATE_RANGE,
            startDateMillis = ts(2026, Calendar.JANUARY, 1),
            endDateMillis = ts(2026, Calendar.FEBRUARY, 20),
            locale = Locale.US
        )
        assertEquals(listOf(3L, 4L, 5L), rangeResult.transactions.map { it.id })
    }

    @Test
    fun testPdfDateFilter_specificYear() {
        val allTxs = sampleTransactions()
        val res2025 = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.SPECIFIC_YEAR,
            selectedYearOnly = 2025,
            locale = Locale.US
        )
        assertEquals("Year 2025", res2025.periodLabel)
        assertEquals(listOf(2L), res2025.transactions.map { it.id })

        val res2026 = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.SPECIFIC_YEAR,
            selectedYearOnly = 2026,
            locale = Locale.US
        )
        assertEquals(listOf(3L, 4L, 5L), res2026.transactions.map { it.id })
    }

    @Test
    fun testPdfDateFilter_all() {
        val allTxs = sampleTransactions()
        val resAll = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.ALL,
            locale = Locale.US
        )
        assertEquals("All Available Dates", resAll.periodLabel)
        assertEquals(allTxs.size, resAll.transactions.size)
    }

    @Test
    fun testPdfDateFilter_noDataForSelectedPeriod_andFilteredSummaryTotals() {
        val allTxs = sampleTransactions()
        val emptyRes = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.SPECIFIC_MONTH,
            selectedMonth = Calendar.DECEMBER,
            selectedMonthYear = 2027,
            locale = Locale.US
        )
        assertTrue(emptyRes.transactions.isEmpty())
        assertEquals("December 2027", emptyRes.periodLabel)

        // Verify filtered summary totals reflect ONLY transactions inside the selected period (Jan 2026 = 250 + 50 Paid, 0 Received)
        val filteredJan = PdfReportExporter.filterTransactionsForPeriod(
            transactions = allTxs,
            filterMode = PdfDateFilterMode.SPECIFIC_MONTH,
            selectedMonth = Calendar.JANUARY,
            selectedMonthYear = 2026,
            locale = Locale.US
        )
        assertEquals("January 2026", filteredJan.periodLabel)
        val janPaid = filteredJan.transactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
        val janReceived = filteredJan.transactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
        assertEquals(300.0, janPaid, 0.001)
        assertEquals(0.0, janReceived, 0.001)
        assertEquals(300.0, janPaid - janReceived, 0.001)
    }

    @Test
    fun testRunningTotals_chronologicalWithTiebreakerAndUpdates() {
        // Unsorted input list (as returned DESC from Room), including two entries with identical timestamp
        val baseTime = 1700000000000L
        val entries = listOf(
            TransactionEntity(id = 3L, customerId = 1L, transactionType = "I_PAID", amount = 30.0, dateTimestamp = baseTime + 2000L),
            TransactionEntity(id = 2L, customerId = 1L, transactionType = "I_PAID", amount = 20.0, dateTimestamp = baseTime),
            TransactionEntity(id = 1L, customerId = 1L, transactionType = "I_PAID", amount = 10.0, dateTimestamp = baseTime)
        )

        val totals = PdfReportExporter.computeRunningTotals(entries)
        assertEquals(10.0, totals[1L]!!, 0.001)
        assertEquals(30.0, totals[2L]!!, 0.001)
        assertEquals(60.0, totals[3L]!!, 0.001)

        // Add a I_RECEIVED entry at the end -> latest total must match header net balance
        val withReceived = entries + TransactionEntity(
            id = 4L, customerId = 1L, transactionType = "I_RECEIVED", amount = 25.0, dateTimestamp = baseTime + 5000L
        )
        val updatedTotals = PdfReportExporter.computeRunningTotals(withReceived)
        val headerBalance = withReceived.filter { it.transactionType == "I_PAID" }.sumOf { it.amount } -
            withReceived.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
        assertEquals(headerBalance, updatedTotals[4L]!!, 0.001)
        assertEquals(35.0, updatedTotals[4L]!!, 0.001)

        // Edit entry 2 from 20 -> 50 and verify subsequent entries recompute
        val afterEdit = withReceived.map { if (it.id == 2L) it.copy(amount = 50.0) else it }
        val editedTotals = PdfReportExporter.computeRunningTotals(afterEdit)
        assertEquals(10.0, editedTotals[1L]!!, 0.001)
        assertEquals(60.0, editedTotals[2L]!!, 0.001)
        assertEquals(90.0, editedTotals[3L]!!, 0.001)
        assertEquals(65.0, editedTotals[4L]!!, 0.001)

        // Delete entry 1 and verify all remaining totals shift
        val afterDelete = afterEdit.filterNot { it.id == 1L }
        val deletedTotals = PdfReportExporter.computeRunningTotals(afterDelete)
        assertNull(deletedTotals[1L])
        assertEquals(50.0, deletedTotals[2L]!!, 0.001)
        assertEquals(80.0, deletedTotals[3L]!!, 0.001)
        assertEquals(55.0, deletedTotals[4L]!!, 0.001)
    }

    private suspend fun awaitUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            delay(20)
        }
    }

    @Test
    fun testSeparateSearchIsolation_andRoomDatabaseInPlaceSafety() = runBlocking {
        val app: Application = ApplicationProvider.getApplicationContext()
        val db = AppDatabase.getDatabase(app)

        // Verify exact database name and schema version are preserved for in-place upgrade
        assertEquals("daily_expense_db", db.openHelper.databaseName)
        val dbVersion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
            db.openHelper.readableDatabase.version
        }
        assertEquals(7, dbVersion)

        val custAlphaId = db.customerDao().insertCustomer(
            CustomerEntity(name = "Alpha Store", mobileNumber = "9000011111")
        )
        val custBetaId = db.customerDao().insertCustomer(
            CustomerEntity(name = "Beta Hardware", mobileNumber = "9000022222")
        )

        db.transactionDao().insertTransaction(
            TransactionEntity(
                customerId = custAlphaId,
                transactionType = "I_PAID",
                amount = 777.0,
                comment = "SpecialCementBag",
                category = "Materials",
                paymentMode = "UPI",
                dateTimestamp = ts(2026, Calendar.JANUARY, 12)
            )
        )
        db.transactionDao().insertTransaction(
            TransactionEntity(
                customerId = custBetaId,
                transactionType = "I_RECEIVED",
                amount = 888.0,
                comment = "SteelRodOnlyBeta",
                category = "Hardware",
                paymentMode = "Cash",
                dateTimestamp = ts(2026, Calendar.JANUARY, 15)
            )
        )

        val vm = DailyExpenseViewModel(app)

        // Keep WhileSubscribed StateFlows active while testing debounced search
        val custCollector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).let { scope ->
            val job1 = scope.launch { vm.filteredCustomerSummaries.collect {} }
            val job2 = scope.launch { vm.filteredCustomerTransactions.collect {} }
            job1 to job2
        }
        try {
            // Wait for Room emissions to populate initial customer summaries
            awaitUntil { vm.filteredCustomerSummaries.value.size == 2 }
            assertEquals(2, vm.filteredCustomerSummaries.value.size)

            // Customer search matches customer fields ONLY — must NOT match transaction comment "SpecialCementBag" or amount "777"
            vm.setCustomerSearchQuery("SpecialCementBag")
            awaitUntil { vm.filteredCustomerSummaries.value.isEmpty() }
            assertTrue(
                "Customer search must not match transaction comments",
                vm.filteredCustomerSummaries.value.isEmpty()
            )

            // Reset to known matching query, then test amount query does NOT match
            vm.setCustomerSearchQuery("Alpha")
            awaitUntil { vm.filteredCustomerSummaries.value.size == 1 }
            val matchedCusts = vm.filteredCustomerSummaries.value
            assertEquals(1, matchedCusts.size)
            assertEquals(custAlphaId, matchedCusts.first().customer.id)

            vm.setCustomerSearchQuery("777")
            awaitUntil { vm.filteredCustomerSummaries.value.isEmpty() }
            assertTrue(
                "Customer search must not match transaction amounts",
                vm.filteredCustomerSummaries.value.isEmpty()
            )

            // Restore customer search to "Alpha" to verify independence when opening Customer Detail
            vm.setCustomerSearchQuery("Alpha")
            awaitUntil { vm.filteredCustomerSummaries.value.size == 1 }

            // Entering Customer Detail clears transaction search and isolates to active customer's transactions
            vm.setActiveCustomerDetailId(custAlphaId)
            assertEquals("", vm.transactionSearchQuery.value)
            awaitUntil { vm.filteredCustomerTransactions.value.size == 1 }
            assertEquals(1, vm.filteredCustomerTransactions.value.size)

            // Searching Beta's transaction comment while viewing Alpha must yield empty
            vm.setTransactionSearchQuery("SteelRodOnlyBeta")
            awaitUntil { vm.filteredCustomerTransactions.value.isEmpty() }
            assertTrue(
                "Transaction search must never return other customers' transactions",
                vm.filteredCustomerTransactions.value.isEmpty()
            )

            // Searching Alpha's transaction amount / comment / payment mode matches Alpha's tx
            vm.setTransactionSearchQuery("777")
            awaitUntil { vm.filteredCustomerTransactions.value.size == 1 }
            val matchedTxs = vm.filteredCustomerTransactions.value
            assertEquals(1, matchedTxs.size)
            assertEquals(custAlphaId, matchedTxs.first().customerId)

            // Searching Alpha's customer name while in Alpha's transaction search must NOT match unless in tx fields
            vm.setTransactionSearchQuery("Alpha Store")
            awaitUntil { vm.filteredCustomerTransactions.value.isEmpty() }
            assertTrue(
                "Transaction search must search transaction fields only, not customer name",
                vm.filteredCustomerTransactions.value.isEmpty()
            )

            // Switching/leaving detail clears transaction search while keeping customer search independent
            vm.clearTransactionSearchQuery()
            vm.setActiveCustomerDetailId(null)
            awaitUntil { vm.transactionSearchQuery.value.isEmpty() }
            assertEquals("", vm.transactionSearchQuery.value)
            assertEquals("Alpha", vm.customerSearchQuery.value)
        } finally {
            custCollector.first.cancel()
            custCollector.second.cancel()
        }

        // Verify data persists intact in the database
        val persistedCustomers = db.customerDao().getAllCustomersDirect()
        val persistedTxs = db.transactionDao().getAllTransactionsDirect()
        assertEquals(2, persistedCustomers.size)
        assertEquals(2, persistedTxs.size)
    }
}
