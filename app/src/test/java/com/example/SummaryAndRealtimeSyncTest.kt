package com.example

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CustomerEntity
import com.example.data.TransactionEntity
import com.example.ui.AuthViewModel
import com.example.ui.CustomerBalanceSummary
import com.example.ui.DailyExpenseViewModel
import com.example.ui.screens.CustomerCardItem
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.settings.SummarySettingsSubScreen
import com.example.ui.theme.DailyExpenseTheme
import com.example.utils.LedgerDateFormatter
import com.example.utils.SummaryAnchor
import com.example.utils.SummaryGranularity
import com.example.utils.SummaryPeriodHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h1280dp-xhdpi", sdk = [36])
class SummaryAndRealtimeSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        runBlocking {
            app = ApplicationProvider.getApplicationContext()
            db = AppDatabase.getDatabase(app)
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
    }

    @After
    fun teardown() {
        runBlocking {
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
    }

    private fun localTimestamp(
        year: Int,
        monthZeroBased: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        millis: Int = 0
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, monthZeroBased, day, hour, minute, second)
        set(Calendar.MILLISECOND, millis)
    }.timeInMillis

    private suspend fun awaitUntil(timeoutMs: Long = 4000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(100, TimeUnit.MILLISECONDS)
            delay(25)
        }
    }

    // ---- Section 8: Year + Time on Last Entry Date & Compact 320px/768px Card Layout ----

    @Test
    fun ledgerDateFormatter_formatLastEntryDateTime_includesYearAndTime_withoutMutatingTimestamp() {
        val originalTs = localTimestamp(2026, Calendar.NOVEMBER, 16, hour = 14, minute = 10)
        val snapshot = originalTs

        val formatted = LedgerDateFormatter.formatLastEntryDateTime(originalTs, Locale.US)

        assertEquals("16 Nov 2026, 2:10 PM", formatted)
        assertEquals("", LedgerDateFormatter.formatLastEntryDateTime(0L, Locale.US))
        assertEquals(snapshot, originalTs)
    }

    @Test
    fun customerCardItem_at320dpAnd768dp_displaysYearAndSingleLineWithoutPushingBalance() {
        val ts = localTimestamp(2026, Calendar.NOVEMBER, 16, hour = 14, minute = 10)
        val summary = CustomerBalanceSummary(
            customer = CustomerEntity(
                id = 77L,
                name = "Shri Ganesh Wholesale General Store & Traders",
                mobileNumber = "9876543210",
                collectToday = true,
                lastUpdatedTimestamp = ts
            ),
            totalPaid = 125000.0,
            totalReceived = 0.0,
            netBalance = 125000.0,
            lastTransactionDate = ts
        )

        val expectedSuffix = LedgerDateFormatter.formatLastEntryDateTime(ts)
        assertTrue(expectedSuffix.contains("2026"))

        var widthDp by mutableStateOf(320.dp)
        composeTestRule.setContent {
            DailyExpenseTheme(darkTheme = false) {
                Box(modifier = Modifier.width(widthDp)) {
                    CustomerCardItem(summary = summary, onClick = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        val dateNode320 = composeTestRule.onNodeWithTag("customer_last_entry_77", useUnmergedTree = true).fetchSemanticsNode()
        val balNode320 = composeTestRule.onNodeWithText("₹ 125,000", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(balNode320.boundsInRoot.right <= composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.right + 1f)
        composeTestRule.onNodeWithTag("customer_last_entry_77", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Last entry $expectedSuffix", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("₹ 125,000", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("Expected single line height at 320dp, got ${dateNode320.boundsInRoot.height}", dateNode320.boundsInRoot.height < 80f)
        assertTrue("Expected balance on right at 320dp", balNode320.boundsInRoot.left > dateNode320.boundsInRoot.right)

        // Wide 768dp layout
        widthDp = 768.dp
        composeTestRule.waitForIdle()
        val dateNode768 = composeTestRule.onNodeWithTag("customer_last_entry_77", useUnmergedTree = true).fetchSemanticsNode()
        val balNode768 = composeTestRule.onNodeWithText("₹ 125,000", useUnmergedTree = true).fetchSemanticsNode()
        composeTestRule.onNodeWithTag("customer_last_entry_77", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Last entry $expectedSuffix", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("₹ 125,000", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("Expected single line height at 768dp, got ${dateNode768.boundsInRoot.height}", dateNode768.boundsInRoot.height < 80f)
        assertTrue("Expected balance on right at 768dp, got ${balNode768.boundsInRoot.left}", balNode768.boundsInRoot.left > dateNode768.boundsInRoot.right)
    }

    // ---- Section 12: Real-Time Updates Without Back/Refresh/Restart ----

    @Test
    fun realtimeSync_customerDetailAndSummaryFlows_updateImmediatelyAfterSave() {
        runBlocking {
            val dueTs = localTimestamp(2026, Calendar.DECEMBER, 20, hour = 12, minute = 0)
        val custId = withContext(Dispatchers.IO) {
            db.customerDao().insertCustomer(
                CustomerEntity(
                    name = "Original Customer",
                    mobileNumber = "9000012345",
                    lastUpdatedTimestamp = localTimestamp(2026, Calendar.NOVEMBER, 10, 9, 0)
                )
            )
        }
        val vm = DailyExpenseViewModel(app)

        val summariesJob = CoroutineScope(Dispatchers.Default).launch {
            vm.filteredCustomerSummaries.collect {}
        }

        try {
            composeTestRule.setContent {
                DailyExpenseTheme {
                    CustomerDetailScreen(
                        customerId = custId,
                        viewModel = vm,
                        onBackClick = {},
                        onAddEntryClick = {},
                        onEditEntryClick = {},
                        onEditCustomerClick = {}
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Initial customer name visible
            awaitUntil {
                composeTestRule.onAllNodesWithText("Original Customer").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("customer_detail_name").assertIsDisplayed()
            composeTestRule.onNodeWithText("Original Customer").assertIsDisplayed()

            // Edit customer name + due date while staying on CustomerDetailScreen
            val currentCustomer = db.customerDao().getCustomerById(custId).first()!!
            var customerSaved = false
            vm.updateCustomer(
                currentCustomer.copy(
                    name = "Updated Realtime Traders",
                    collectToday = true,
                    collectDueDate = dueTs
                ),
                onSuccess = { customerSaved = true }
            )

            awaitUntil { customerSaved }
            awaitUntil {
                composeTestRule.onAllNodesWithText("Updated Realtime Traders").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Updated Realtime Traders").assertIsDisplayed()
            composeTestRule.onNodeWithText("DUE: 20 DEC 2026").assertIsDisplayed()

            // Customer list Flow also reflects the updated name immediately
            awaitUntil {
                vm.filteredCustomerSummaries.value.any { it.customer.id == custId && it.customer.name == "Updated Realtime Traders" }
            }

            // Add transaction while staying on CustomerDetailScreen -> balance and entry update live
            var txAdded = false
            vm.addTransaction(
                customerId = custId,
                transactionType = "I_PAID",
                amount = 1500.0,
                comment = "Live Cement Supply",
                category = "Materials",
                paymentMode = "UPI",
                dateTimestamp = localTimestamp(2026, Calendar.NOVEMBER, 16, 11, 0),
                onSuccess = { txAdded = true }
            )
            awaitUntil { txAdded }
            awaitUntil {
                composeTestRule.onAllNodesWithText("Live Cement Supply").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Live Cement Supply").assertIsDisplayed()

            // Edit transaction amount from 1500 -> 2400 -> recalculates balance in real time
            val insertedTx = db.transactionDao().getTransactionsForCustomer(custId).first().first()
            var txUpdated = false
            vm.updateTransaction(
                insertedTx.copy(amount = 2400.0, comment = "Updated Supply"),
                onSuccess = { txUpdated = true }
            )
            awaitUntil { txUpdated }
            awaitUntil {
                vm.filteredCustomerSummaries.value.firstOrNull { it.customer.id == custId }?.totalPaid == 2400.0
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Updated Supply").assertIsDisplayed()
        } finally {
            summariesJob.cancel()
        }
        }
    }

    @Test
    fun realtimeSync_authProfileEdit_updatesObservableProfileImmediately() {
        val authViewModel = AuthViewModel(app)
        authViewModel.updateProfile(
            name = "Kundan Sinh",
            businessName = "Daily Mart Store",
            phone = "9898989898",
            email = "store@example.com"
        )

        val profile = authViewModel.userProfile.value
        assertEquals("Kundan Sinh", profile.name)
        assertEquals("Daily Mart Store", profile.businessName)
        assertEquals("9898989898", profile.phone)
        assertEquals("store@example.com", profile.email)
    }

    // ---- Sections 13 & 14: Boundary Precision, SQLite Queries, Chart Consistency & Paging 3 ----

    @Test
    fun summaryQueries_halfOpenMonthBoundaries_strictlySeparateLastMillisecondFromNextMonthMidnight() {
        runBlocking {
            val custId = withContext(Dispatchers.IO) {
            db.customerDao().insertCustomer(CustomerEntity(name = "Boundary Customer", mobileNumber = "9000000001"))
        }

        val novRange = SummaryPeriodHelper.monthRange(2026, Calendar.NOVEMBER)
        val decRange = SummaryPeriodHelper.monthRange(2026, Calendar.DECEMBER)
        assertEquals(novRange.endMillis, decRange.startMillis)

        // 30 Nov 2026 23:59:59.999
        val novEndMinus1 = novRange.endMillis - 1L
        // 01 Dec 2026 00:00:00.000 (midnight)
        val decStartMidnight = decRange.startMillis

        withContext(Dispatchers.IO) {
            db.transactionDao().insertTransaction(
                TransactionEntity(
                    customerId = custId,
                    transactionType = "I_PAID",
                    amount = 350.0,
                    dateTimestamp = novEndMinus1,
                    comment = "Last Millisecond Nov"
                )
            )
            db.transactionDao().insertTransaction(
                TransactionEntity(
                    customerId = custId,
                    transactionType = "I_RECEIVED",
                    amount = 725.0,
                    dateTimestamp = decStartMidnight,
                    comment = "Midnight Start Dec"
                )
            )
        }

        val novTotals = db.transactionDao().observeSummaryPeriodTotals(novRange.startMillis, novRange.endMillis).first()
        val decTotals = db.transactionDao().observeSummaryPeriodTotals(decRange.startMillis, decRange.endMillis).first()

        assertEquals(350.0, novTotals.totalPaid, 0.001)
        assertEquals(0.0, novTotals.totalReceived, 0.001)
        assertEquals(1, novTotals.entryCount)

        assertEquals(0.0, decTotals.totalPaid, 0.001)
        assertEquals(725.0, decTotals.totalReceived, 0.001)
        assertEquals(1, decTotals.entryCount)

        val novPaged = db.transactionDao().getSummaryTransactionsPaged(novRange.startMillis, novRange.endMillis)
            .load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page
        val decPaged = db.transactionDao().getSummaryTransactionsPaged(decRange.startMillis, decRange.endMillis)
            .load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page

        assertEquals(listOf("Last Millisecond Nov"), novPaged.data.map { it.comment })
        assertEquals(listOf("Midnight Start Dec"), decPaged.data.map { it.comment })
        }
    }

    @Test
    fun summaryConsistency_addEditMoveMonthAndMultiCustomerEntries_matchTotalsBucketsAndPagedRows() {
        runBlocking {
            val custA = withContext(Dispatchers.IO) {
            db.customerDao().insertCustomer(CustomerEntity(name = "Ramesh Builders", mobileNumber = "9000000002"))
        }
        val custB = withContext(Dispatchers.IO) {
            db.customerDao().insertCustomer(CustomerEntity(name = "November Deduction", mobileNumber = "9000000003"))
        }

        val vm = DailyExpenseViewModel(app)
        val targetAnchor = SummaryAnchor(2026, Calendar.NOVEMBER, 16)
        vm.setSummaryAnchor(targetAnchor)
        vm.setSummaryGranularity(SummaryGranularity.MONTHLY)

        val collectorJob = CoroutineScope(Dispatchers.Default).launch {
            launch { vm.summaryChartState.collect {} }
            launch { vm.summaryGroupSubtotalsMap.collect {} }
            launch { vm.summaryTopOverviewState.collect {} }
        }

        try {
            // Start empty -> 0 entries in Nov 2026
            awaitUntil { !vm.summaryChartState.value.isLoading && vm.summaryChartState.value.anchor == targetAnchor }
            assertEquals(0, vm.summaryChartState.value.overview.entryCount)

            // 1. Add date-only 00:00:00 entry on 16 Nov 2026
            val nov16Midnight = localTimestamp(2026, Calendar.NOVEMBER, 16, hour = 0, minute = 0, second = 0, millis = 0)
            var saved1 = false
            vm.addTransaction(
                customerId = custA,
                transactionType = "I_PAID",
                amount = 1000.0,
                comment = "PDF Midnight Entry",
                category = "General",
                paymentMode = "CASH",
                dateTimestamp = nov16Midnight,
                onSuccess = { saved1 = true }
            )
            awaitUntil { saved1 }
            awaitUntil { vm.summaryChartState.value.overview.entryCount == 1 }

            // 2. Add 2nd entry on same date for Customer B
            val nov16Evening = localTimestamp(2026, Calendar.NOVEMBER, 16, hour = 18, minute = 30)
            var saved2 = false
            vm.addTransaction(
                customerId = custB,
                transactionType = "I_RECEIVED",
                amount = 400.0,
                comment = "Second Entry Same Day",
                category = "Expense",
                paymentMode = "UPI",
                dateTimestamp = nov16Evening,
                onSuccess = { saved2 = true }
            )
            awaitUntil { saved2 }
            awaitUntil { vm.summaryChartState.value.overview.entryCount == 2 }

            val stateAfterAdds = vm.summaryChartState.value
            val novRange = SummaryPeriodHelper.monthRange(2026, Calendar.NOVEMBER)
            val novPaged = db.transactionDao().getSummaryTransactionsPaged(novRange.startMillis, novRange.endMillis)
                .load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page
            val daySubtotals = vm.summaryGroupSubtotalsMap.value

            // Invariants: Overview == Sum(Day subtotals) == Chart November Bucket == Sum(Paging 3 rows)
            assertEquals(1000.0, stateAfterAdds.overview.totalPaid, 0.001)
            assertEquals(400.0, stateAfterAdds.overview.totalReceived, 0.001)
            assertEquals(-600.0, stateAfterAdds.overview.netBalance, 0.001)
            assertEquals(2, stateAfterAdds.overview.customerCount)

            val novBucket = stateAfterAdds.points.first { it.bucketKey == "2026-11" }
            assertEquals(stateAfterAdds.overview.totalPaid, novBucket.totalPaid, 0.001)
            assertEquals(stateAfterAdds.overview.totalReceived, novBucket.totalReceived, 0.001)

            val sumDayPaid = daySubtotals.values.sumOf { it.totalPaid }
            val sumDayRec = daySubtotals.values.sumOf { it.totalReceived }
            assertEquals(stateAfterAdds.overview.totalPaid, sumDayPaid, 0.001)
            assertEquals(stateAfterAdds.overview.totalReceived, sumDayRec, 0.001)

            assertEquals(2, novPaged.data.size)
            assertEquals(
                1000.0,
                novPaged.data.filter { it.transactionType == "I_PAID" }.sumOf { it.amount },
                0.001
            )
            assertEquals(
                400.0,
                novPaged.data.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount },
                0.001
            )

            // 3. Edit 1st entry amount 1000 -> 1500
            val tx1 = db.transactionDao().getTransactionsForCustomer(custA).first().first()
            var edited1 = false
            vm.updateTransaction(tx1.copy(amount = 1500.0), onSuccess = { edited1 = true })
            awaitUntil { edited1 }
            awaitUntil { vm.summaryChartState.value.overview.totalPaid == 1500.0 }
            assertEquals(-1100.0, vm.summaryChartState.value.overview.netBalance, 0.001)

            // 4. Move 2nd entry to 10 Oct 2026 -> disappears from Nov, appears in Oct bucket
            val tx2 = db.transactionDao().getTransactionsForCustomer(custB).first().first()
            val oct10 = localTimestamp(2026, Calendar.OCTOBER, 10, hour = 10, minute = 0)
            var moved = false
            vm.updateTransaction(tx2.copy(dateTimestamp = oct10), onSuccess = { moved = true })
            awaitUntil { moved }
            awaitUntil { vm.summaryChartState.value.overview.entryCount == 1 }

            val afterMove = vm.summaryChartState.value
            assertEquals(1500.0, afterMove.overview.totalPaid, 0.001)
            assertEquals(0.0, afterMove.overview.totalReceived, 0.001)
            val octBucket = afterMove.points.first { it.bucketKey == "2026-10" }
            assertEquals(400.0, octBucket.totalReceived, 0.001)
            assertEquals(1, octBucket.entryCount)
        } finally {
            collectorJob.cancel()
        }
        }
    }

    @Test
    fun summarySettingsSubScreen_emptyStateDefaultMonthlyFutureNextDisabledAndDrillDown() {
        runBlocking {
            val vm = DailyExpenseViewModel(app)

        composeTestRule.setContent {
            DailyExpenseTheme {
                SummarySettingsSubScreen(
                    dailyExpenseViewModel = vm,
                    onBack = {},
                    onCustomerClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Default is Monthly, anchored to today -> Next arrow is disabled, empty state shown
        awaitUntil {
            composeTestRule.onAllNodesWithTag("chart_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("chart_toggle_monthly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chart_next_period").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("chart_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("summary_entries_empty").assertIsDisplayed()

        // Step to previous period -> Next arrow becomes enabled
        composeTestRule.onNodeWithTag("chart_prev_period").performClick()
        composeTestRule.waitForIdle()
        awaitUntil {
            try {
                composeTestRule.onNodeWithTag("chart_next_period").assertIsEnabled()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule.onNodeWithTag("chart_next_period").assertIsEnabled()
        composeTestRule.onNodeWithTag("chart_next_period").performClick()
        composeTestRule.waitForIdle()

        // Seed an entry on today -> charts + tooltip "View entries" + Paging 3 list render cleanly
        val custId = withContext(Dispatchers.IO) {
            db.customerDao().insertCustomer(CustomerEntity(name = "Today Cash Customer", mobileNumber = "9000000004"))
        }
        var added = false
        vm.addTransaction(
            customerId = custId,
            transactionType = "I_RECEIVED",
            amount = 950.0,
            comment = "Today Counter Sale",
            category = "Sales",
            paymentMode = "UPI",
            dateTimestamp = System.currentTimeMillis(),
            onSuccess = { added = true }
        )
        awaitUntil { added }
        awaitUntil {
            composeTestRule.onAllNodesWithTag("chart_view_entries_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chart_tooltip_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chart_view_entries_button").performClick()
        composeTestRule.waitForIdle()

        awaitUntil {
            composeTestRule.onAllNodesWithTag("clear_chart_drilldown_chip").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("clear_chart_drilldown_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_chart_drilldown_chip").performClick()
        composeTestRule.waitForIdle()

        // Switch Daily & Yearly tabs cleanly
        composeTestRule.onNodeWithTag("chart_toggle_daily").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_toggle_yearly").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("summary_charts_section").assertIsDisplayed()
        }
    }
}
