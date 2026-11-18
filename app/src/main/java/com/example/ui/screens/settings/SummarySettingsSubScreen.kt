package com.example.ui.screens.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.data.SummaryChartBucketTuple
import com.example.data.SummaryTransactionTuple
import com.example.ui.DailyExpenseViewModel
import com.example.ui.SummaryChartUiState
import com.example.ui.SummaryEntryListItem
import com.example.ui.SummaryTopOverviewState
import com.example.ui.theme.*
import com.example.utils.LedgerDateFormatter
import com.example.utils.LocalAppStrings
import com.example.utils.SummaryAnchor
import com.example.utils.SummaryPeriodHelper
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

private data class SubFinancialPeriodSummary(
    val periodName: String,
    val badgeName: String,
    val totalGave: Double,
    val totalGot: Double,
    val count: Int,
    val badgeColor: Color,
    val badgeBgColor: Color
) {
    val netBalance: Double get() = totalGot - totalGave
}

@Composable
fun SummarySettingsSubScreen(
    dailyExpenseViewModel: DailyExpenseViewModel?,
    onBack: () -> Unit,
    onCustomerClick: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val topState by (dailyExpenseViewModel?.summaryTopOverviewState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(SummaryTopOverviewState()) })
    val chartState by (dailyExpenseViewModel?.summaryChartState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(SummaryChartUiState(isLoading = false)) })
    val groupSubtotals by (dailyExpenseViewModel?.summaryGroupSubtotalsMap?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyMap()) })
    val pagedFlow = remember(dailyExpenseViewModel) {
        dailyExpenseViewModel?.summaryEntriesPagedFlow ?: flowOf(PagingData.empty())
    }
    val pagedEntries = pagedFlow.collectAsLazyPagingItems()

    val selectedSummaryTab = topState.selectedTab
    val anchor = topState.anchor
    val today = SummaryAnchor.today()

    val isToday = anchor.year == today.year && anchor.monthZeroBased == today.monthZeroBased && anchor.dayOfMonth == today.dayOfMonth
    val isThisMonth = anchor.year == today.year && anchor.monthZeroBased == today.monthZeroBased
    val isThisYear = anchor.year == today.year

    val monthNames = remember { arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec") }
    val monthFullNames = remember {
        arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }

    val dailyBadge = if (selectedSummaryTab == 0 || isToday) "TODAY" else String.format(
        Locale.US, "%02d %s %d", anchor.dayOfMonth, monthNames[anchor.monthZeroBased], anchor.year
    )
    val monthlyBadge = if (selectedSummaryTab == 0 || isThisMonth) "THIS MONTH" else "${monthNames[anchor.monthZeroBased].uppercase()} ${anchor.year}"
    val yearlyBadge = if (selectedSummaryTab == 0 || isThisYear) "THIS YEAR" else "${anchor.year}"

    val dailySummary = SubFinancialPeriodSummary(
        periodName = "Daily",
        badgeName = dailyBadge,
        totalGave = topState.dailyTotals.totalPaid,
        totalGot = topState.dailyTotals.totalReceived,
        count = topState.dailyTotals.entryCount,
        badgeColor = c.blueAccent,
        badgeBgColor = c.blueTint
    )
    val monthlySummary = SubFinancialPeriodSummary(
        periodName = "Monthly",
        badgeName = monthlyBadge,
        totalGave = topState.monthlyTotals.totalPaid,
        totalGot = topState.monthlyTotals.totalReceived,
        count = topState.monthlyTotals.entryCount,
        badgeColor = c.indigoAccent,
        badgeBgColor = c.indigoTint
    )
    val yearlySummary = SubFinancialPeriodSummary(
        periodName = "Yearly",
        badgeName = yearlyBadge,
        totalGave = topState.yearlyTotals.totalPaid,
        totalGot = topState.yearlyTotals.totalReceived,
        count = topState.yearlyTotals.entryCount,
        badgeColor = c.greenPrimary,
        badgeBgColor = c.greenTint
    )

    val dailyLabel = String.format(Locale.US, "%02d %s %d", anchor.dayOfMonth, monthFullNames[anchor.monthZeroBased], anchor.year)
    val monthlyLabel = "${monthFullNames[anchor.monthZeroBased]} ${anchor.year}"
    val yearlyLabel = "${anchor.year}"
    val isCurrentTop = when (selectedSummaryTab) {
        1 -> isToday
        2 -> isThisMonth
        3 -> isThisYear
        else -> true
    }

    SettingsSubScreenScaffold(
        title = s.summaryTitle,
        subtitle = s.summarySubtitle,
        onBack = onBack,
        useInternalScroll = false
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("summary_lazy_column")
        ) {
            // 1. Existing Summary Breakdown Table Card (powered by identical Room half-open SQLite queries)
            item(key = "summary_table_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = BorderStroke(1.dp, c.border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = c.blueTint,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Assessment, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.summaryTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                Text(s.summarySubtitle, fontSize = 11.sp, color = c.textMuted)
                            }
                        }

                        // Period Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.cardElevated, RoundedCornerShape(12.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val tabs = listOf("Overall", s.summaryDaily, s.summaryMonthly, s.summaryYearly)
                            tabs.forEachIndexed { index, tabTitle ->
                                val isSelected = selectedSummaryTab == index
                                val activeColor = when (index) {
                                    1 -> c.blueAccent
                                    2 -> c.indigoAccent
                                    3 -> c.greenPrimary
                                    else -> c.greenPrimary
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isSelected) activeColor else Color.Transparent)
                                        .clickable { dailyExpenseViewModel?.setSummaryTopTab(index) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tabTitle,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else c.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (selectedSummaryTab != 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(c.cardElevated, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { dailyExpenseViewModel?.stepSummaryPeriod(-1) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Period", tint = c.textSecondary)
                                }

                                val displayPeriodText = when (selectedSummaryTab) {
                                    1 -> dailyLabel
                                    2 -> monthlyLabel
                                    else -> yearlyLabel
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    dailyExpenseViewModel?.setSummaryAnchor(SummaryAnchor(y, m, d))
                                                },
                                                anchor.year,
                                                anchor.monthZeroBased,
                                                anchor.dayOfMonth
                                            ).apply {
                                                datePicker.maxDate = System.currentTimeMillis()
                                            }.show()
                                        }
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = displayPeriodText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!isCurrentTop) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = c.blueTint,
                                            modifier = Modifier.clickable { dailyExpenseViewModel?.resetSummaryToCurrent() }
                                        ) {
                                            Text(
                                                text = "Current",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = c.blueAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                val canStep = SummaryPeriodHelper.canStepForward(chartState.granularity, anchor, today)
                                IconButton(
                                    onClick = { dailyExpenseViewModel?.stepSummaryPeriod(1) },
                                    enabled = canStep,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Next Period",
                                        tint = if (canStep) c.textSecondary else c.textMuted.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }

                        if (selectedSummaryTab == 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(c.cardElevated)
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("METRIC", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.weight(1.1f), maxLines = 1)
                                    Surface(shape = RoundedCornerShape(4.dp), color = dailySummary.badgeBgColor, modifier = Modifier.weight(1f)) {
                                        Text("DAILY", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = dailySummary.badgeColor, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(vertical = 3.dp))
                                    }
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Surface(shape = RoundedCornerShape(4.dp), color = monthlySummary.badgeBgColor, modifier = Modifier.weight(1.1f)) {
                                        Text("MONTHLY", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = monthlySummary.badgeColor, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(vertical = 3.dp))
                                    }
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Surface(shape = RoundedCornerShape(4.dp), color = yearlySummary.badgeBgColor, modifier = Modifier.weight(1f)) {
                                        Text("YEARLY", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = yearlySummary.badgeColor, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(vertical = 3.dp))
                                    }
                                }

                                HorizontalDivider(color = c.border)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(s.totalGotLabel, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, modifier = Modifier.weight(1.1f), maxLines = 1)
                                    Text(String.format(Locale.US, "₹%,.0f", dailySummary.totalGot), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(String.format(Locale.US, "₹%,.0f", monthlySummary.totalGot), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.1f))
                                    Text(String.format(Locale.US, "₹%,.0f", yearlySummary.totalGot), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }

                                HorizontalDivider(color = c.divider)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(s.totalGaveLabel, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, modifier = Modifier.weight(1.1f), maxLines = 1)
                                    Text(String.format(Locale.US, "₹%,.0f", dailySummary.totalGave), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = c.redDebit, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(String.format(Locale.US, "₹%,.0f", monthlySummary.totalGave), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = c.redDebit, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.1f))
                                    Text(String.format(Locale.US, "₹%,.0f", yearlySummary.totalGave), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = c.redDebit, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }

                                HorizontalDivider(color = c.divider)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(c.surfaceSubtle)
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Net", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1.1f), maxLines = 1)
                                    val dNet = dailySummary.netBalance
                                    val mNet = monthlySummary.netBalance
                                    val yNet = yearlySummary.netBalance
                                    Text(String.format(Locale.US, "%s₹%,.0f", if (dNet >= 0) "+" else "", dNet), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = if (dNet >= 0) c.greenPrimary else c.redDebit, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(String.format(Locale.US, "%s₹%,.0f", if (mNet >= 0) "+" else "", mNet), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = if (mNet >= 0) c.greenPrimary else c.redDebit, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.1f))
                                    Text(String.format(Locale.US, "%s₹%,.0f", if (yNet >= 0) "+" else "", yNet), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = if (yNet >= 0) c.greenPrimary else c.redDebit, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }

                                HorizontalDivider(color = c.divider)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Txns", fontSize = 9.5.sp, color = c.textMuted, modifier = Modifier.weight(1.1f))
                                    Text("${dailySummary.count}", fontSize = 9.5.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                    Text("${monthlySummary.count}", fontSize = 9.5.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1.1f))
                                    Text("${yearlySummary.count}", fontSize = 9.5.sp, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                }
                            }
                        } else {
                            val period = when (selectedSummaryTab) {
                                1 -> dailySummary
                                2 -> monthlySummary
                                else -> yearlySummary
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(period.badgeBgColor.copy(alpha = 0.55f))
                                    .border(1.dp, period.badgeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = period.badgeColor) {
                                        Text(period.badgeName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                    Text("${period.count} Total Transactions", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(c.card, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Net Ledger Balance (${period.periodName})", fontSize = 11.sp, color = c.textMuted)
                                        val net = period.netBalance
                                        Text(
                                            text = String.format(Locale.US, "%s₹%,.2f", if (net >= 0) "+" else "", net),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (net >= 0) c.greenPrimary else c.redDebit
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = if (period.netBalance >= 0) c.greenTint else c.redTint,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (period.netBalance >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                                contentDescription = null,
                                                tint = if (period.netBalance >= 0) c.greenPrimary else c.redDebit,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = c.greenTint),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(s.totalGotLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(String.format(Locale.US, "₹%,.0f", period.totalGot), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }

                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = c.redTint),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(s.totalGaveLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.redDebit)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(String.format(Locale.US, "₹%,.0f", period.totalGave), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Interactive Graph Section (Daily | Monthly | Yearly, default Monthly)
            item(key = "summary_graph_section") {
                SummaryChartsSection(
                    state = chartState,
                    onSelectGranularity = { dailyExpenseViewModel?.setSummaryGranularity(it) },
                    onStepPeriod = { dailyExpenseViewModel?.stepSummaryPeriod(it) },
                    onPickAnchor = { dailyExpenseViewModel?.setSummaryAnchor(it) },
                    onResetToCurrent = { dailyExpenseViewModel?.resetSummaryToCurrent() },
                    onSelectBucket = { dailyExpenseViewModel?.selectSummaryChartBucket(it) },
                    onViewBucketEntries = { bucketKey ->
                        dailyExpenseViewModel?.openChartBucketEntries(bucketKey)
                        scope.launch {
                            listState.animateScrollToItem(2)
                        }
                    }
                )
            }

            // 3. Entries Section Header (Section 14)
            item(key = "summary_entries_header") {
                SummaryEntriesSectionHeader(
                    periodLabel = chartState.drillDownLabel ?: chartState.periodLabel,
                    isDrilledDown = chartState.drillDownBucketKey != null,
                    totalEntries = chartState.overview.entryCount,
                    onClearDrillDown = { dailyExpenseViewModel?.clearChartDrillDown() }
                )
            }

            // 4. Paging 3 Entry List (All Customers, Grouped by Day in Monthly & Month in Yearly)
            if (pagedEntries.loadState.refresh is LoadState.Loading && pagedEntries.itemCount == 0) {
                items(3, key = { "summary_loading_$it" }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.cardElevated)
                    )
                }
            } else if (pagedEntries.itemCount == 0) {
                item(key = "summary_entries_empty") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = c.card,
                        border = BorderStroke(1.dp, c.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("summary_entries_empty")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = c.textMuted,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = s.summaryNoDataPeriod,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textSecondary
                            )
                            Text(
                                text = "Every entry across all customers for the selected period appears here.",
                                fontSize = 11.5.sp,
                                color = c.textMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    count = pagedEntries.itemCount,
                    key = pagedEntries.itemKey { item ->
                        when (item) {
                            is SummaryEntryListItem.GroupHeader -> "hdr_${item.groupKey}"
                            is SummaryEntryListItem.EntryRow -> "tx_${item.tuple.id}"
                        }
                    }
                ) { index ->
                    when (val item = pagedEntries[index]) {
                        is SummaryEntryListItem.GroupHeader -> {
                            SummaryGroupHeaderCard(
                                title = item.title,
                                subtotal = groupSubtotals[item.groupKey]
                            )
                        }
                        is SummaryEntryListItem.EntryRow -> {
                            SummaryEntryRowCard(
                                tuple = item.tuple,
                                onCustomerClick = onCustomerClick
                            )
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryEntriesSectionHeader(
    periodLabel: String,
    isDrilledDown: Boolean,
    totalEntries: Int,
    onClearDrillDown: () -> Unit
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("summary_entries_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${s.summaryEntriesTitle} • $periodLabel",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isDrilledDown) {
                Text(
                    text = "$totalEntries entries across all customers",
                    fontSize = 11.sp,
                    color = c.textMuted
                )
            }
        }

        if (isDrilledDown) {
            AssistChip(
                onClick = onClearDrillDown,
                label = { Text("Show all", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(Icons.Default.Close, contentDescription = "Show all", modifier = Modifier.size(14.dp))
                },
                modifier = Modifier.testTag("clear_chart_drilldown_chip")
            )
        }
    }
}

@Composable
private fun SummaryGroupHeaderCard(
    title: String,
    subtotal: SummaryChartBucketTuple?
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = c.cardElevated,
        border = BorderStroke(1.dp, c.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtotal != null) {
                    Text(
                        text = "${subtotal.entryCount} entries",
                        fontSize = 10.sp,
                        color = c.textMuted
                    )
                }
            }

            if (subtotal != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (subtotal.totalReceived > 0.0) {
                        Text(
                            text = "${s.received}: ₹${String.format(Locale.US, "%,.0f", subtotal.totalReceived)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.greenPrimary
                        )
                    }
                    if (subtotal.totalPaid > 0.0) {
                        Text(
                            text = "${s.paid}: ₹${String.format(Locale.US, "%,.0f", subtotal.totalPaid)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.redDebit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryEntryRowCard(
    tuple: SummaryTransactionTuple,
    onCustomerClick: ((Long) -> Unit)?
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val isPaid = tuple.transactionType == "I_PAID"
    val formattedAmount = remember(tuple.amount) {
        if (tuple.amount % 1.0 == 0.0) {
            String.format(Locale.US, "₹%,.0f", tuple.amount)
        } else {
            String.format(Locale.US, "₹%,.2f", tuple.amount)
        }
    }
    val dateStr = remember(tuple.dateTimestamp) {
        LedgerDateFormatter.formatDateTimeWithYear(tuple.dateTimestamp)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onCustomerClick != null) {
                    Modifier.clickable { onCustomerClick(tuple.customerId) }
                } else Modifier
            )
            .testTag("summary_entry_${tuple.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isPaid) c.redTint else c.greenTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPaid) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (isPaid) s.paid else s.received,
                    tint = if (isPaid) c.redDebit else c.greenPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tuple.customerName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("summary_entry_customer_${tuple.id}")
                )

                val metaLine = buildString {
                    if (tuple.comment.isNotBlank()) {
                        append(tuple.comment)
                        append(" • ")
                    }
                    append(dateStr)
                }
                Text(
                    text = metaLine,
                    fontSize = 11.5.sp,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subTag = listOf(tuple.category, tuple.paymentMode)
                    .filter { it.isNotBlank() && it != "None" }
                    .joinToString(" • ")
                if (subTag.isNotBlank()) {
                    Text(
                        text = subTag,
                        fontSize = 10.sp,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedAmount,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPaid) c.redDebit else c.greenPrimary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isPaid) c.redTint else c.greenTint
                ) {
                    Text(
                        text = if (isPaid) s.gaveCredit.uppercase() else s.gotPayment.uppercase(),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaid) c.redDebit else c.greenPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
