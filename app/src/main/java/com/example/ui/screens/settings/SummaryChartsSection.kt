package com.example.ui.screens.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.SummaryChartPoint
import com.example.ui.SummaryChartUiState
import com.example.ui.theme.LedgerTheme
import com.example.utils.LedgerDateFormatter
import com.example.utils.LocalAppStrings
import com.example.utils.SummaryAnchor
import com.example.utils.SummaryGranularity
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryChartsSection(
    state: SummaryChartUiState,
    onSelectGranularity: (SummaryGranularity) -> Unit,
    onStepPeriod: (Int) -> Unit,
    onPickAnchor: (SummaryAnchor) -> Unit,
    onResetToCurrent: () -> Unit,
    onSelectBucket: (String?) -> Unit,
    onViewBucketEntries: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val context = LocalContext.current

    var showMonthYearPicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }

    val openPeriodPicker: () -> Unit = {
        when (state.granularity) {
            SummaryGranularity.DAILY -> {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        onPickAnchor(SummaryAnchor(year = y, monthZeroBased = m, dayOfMonth = d))
                    },
                    state.anchor.year,
                    state.anchor.monthZeroBased,
                    state.anchor.dayOfMonth
                ).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
            SummaryGranularity.MONTHLY -> showMonthYearPicker = true
            SummaryGranularity.YEARLY -> showYearPicker = true
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("summary_charts_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = c.indigoTint,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = c.indigoAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive Cashflow Charts",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                    Text(
                        text = "Tap any bar or point to inspect totals & entries",
                        fontSize = 11.sp,
                        color = c.textMuted
                    )
                }
            }

            // Segmented Toggle: Daily | Monthly | Yearly (Default: Monthly)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.cardElevated, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val options = listOf(
                    SummaryGranularity.DAILY to s.summaryDaily,
                    SummaryGranularity.MONTHLY to s.summaryMonthly,
                    SummaryGranularity.YEARLY to s.summaryYearly
                )
                options.forEach { (granularity, label) ->
                    val selected = state.granularity == granularity
                    val activeBg = when (granularity) {
                        SummaryGranularity.DAILY -> c.blueAccent
                        SummaryGranularity.MONTHLY -> c.indigoAccent
                        SummaryGranularity.YEARLY -> c.greenPrimary
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) activeBg else Color.Transparent)
                            .clickable { onSelectGranularity(granularity) }
                            .testTag("chart_toggle_${granularity.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Period Navigator: < [November 2026] >
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.cardElevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { onStepPeriod(-1) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chart_prev_period")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Period",
                        tint = c.textPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { openPeriodPicker() }
                        .padding(horizontal = 6.dp)
                        .testTag("chart_period_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select Period",
                        tint = c.indigoAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.periodLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.canStepForward) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = c.blueTint,
                            modifier = Modifier.clickable { onResetToCurrent() }
                        ) {
                            Text(
                                text = "Now",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.blueAccent,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onStepPeriod(1) },
                    enabled = state.canStepForward,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chart_next_period")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Period",
                        tint = if (state.canStepForward) c.textPrimary else c.textMuted.copy(alpha = 0.35f)
                    )
                }
            }

            if (state.isLoading) {
                SummaryChartSkeleton()
            } else {
                // Highlight Metrics Above Charts
                val ov = state.overview
                val netColor = if (ov.netBalance >= 0) c.greenPrimary else c.redDebit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HighlightStatTile(
                        title = s.summaryTotalPaid,
                        value = String.format(Locale.US, "₹%,.0f", ov.totalPaid),
                        bg = c.redTint,
                        fg = c.redDebit,
                        modifier = Modifier.weight(1f)
                    )
                    HighlightStatTile(
                        title = s.summaryTotalReceived,
                        value = String.format(Locale.US, "₹%,.0f", ov.totalReceived),
                        bg = c.greenTint,
                        fg = c.greenPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    HighlightStatTile(
                        title = s.summaryNetLabel,
                        value = String.format(
                            Locale.US,
                            "%s₹%,.0f",
                            if (ov.netBalance >= 0) "+" else "-",
                            kotlin.math.abs(ov.netBalance)
                        ),
                        bg = if (ov.netBalance >= 0) c.greenTint.copy(alpha = 0.7f) else c.redTint.copy(alpha = 0.7f),
                        fg = netColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniMetadataChip(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        text = "${ov.entryCount} entries",
                        bg = c.surfaceSubtle,
                        fg = c.textSecondary
                    )
                    MiniMetadataChip(
                        icon = Icons.Default.People,
                        text = "${ov.customerCount} ${s.summaryCustomersInvolved.lowercase()}",
                        bg = c.surfaceSubtle,
                        fg = c.textSecondary
                    )
                    if (!ov.highestLabel.isNullOrBlank() && ov.highestAmount > 0.0) {
                        MiniMetadataChip(
                            icon = Icons.Default.Star,
                            text = "${s.summaryHighestLabel}: ${ov.highestLabel} (${LedgerDateFormatter.formatCompactRupee(ov.highestAmount)})",
                            bg = c.amberTint,
                            fg = c.amberAccent
                        )
                    }
                }

                val hasChartVolume = state.points.any { it.totalPaid > 0.0 || it.totalReceived > 0.0 || it.entryCount > 0 }
                if (!hasChartVolume) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = c.surfaceSubtle,
                        border = BorderStroke(1.dp, c.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chart_empty_state")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 26.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = s.summaryNoDataPeriod,
                                tint = c.textMuted,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = s.summaryNoDataPeriod,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textSecondary
                            )
                            Text(
                                text = "Add a credit or debit entry to visualize cashflow trends.",
                                fontSize = 11.sp,
                                color = c.textMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val chartScrollState = rememberScrollState()
                    val density = LocalDensity.current
                    val slotWidthDp: Dp = when (state.granularity) {
                        SummaryGranularity.DAILY -> 34.dp
                        SummaryGranularity.MONTHLY -> 44.dp
                        SummaryGranularity.YEARLY -> 58.dp
                    }

                    LaunchedEffect(state.granularity, state.anchor, state.points.size) {
                        val targetIdx = state.points.indexOfFirst { it.isCurrentOrSelected }
                        if (targetIdx >= 0) {
                            val slotPx = with(density) { slotWidthDp.toPx() }
                            val scrollTarget = ((targetIdx - 2).coerceAtLeast(0) * slotPx).toInt()
                            chartScrollState.animateScrollTo(scrollTarget)
                        }
                    }

                    // 1. Grouped Bar Chart (Paid vs Received)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LegendDot(color = c.redDebit, label = s.paid)
                            LegendDot(color = c.greenPrimary, label = s.received)
                            LegendDot(color = c.indigoAccent, label = "Current")
                            LegendDot(color = c.amberAccent, label = s.summaryHighestLabel)
                        }

                        GroupedBarCanvasChart(
                            points = state.points,
                            selectedBucketKey = state.selectedBucketKey,
                            slotWidthDp = slotWidthDp,
                            scrollState = chartScrollState,
                            onSelectBucket = onSelectBucket
                        )
                    }

                    // Selected Bar / Point Interactive Tooltip
                    val selectedPoint = state.points.firstOrNull { it.bucketKey == state.selectedBucketKey }
                    if (selectedPoint != null) {
                        SelectedBucketTooltipCard(
                            point = selectedPoint,
                            isDrilledIn = state.drillDownBucketKey == selectedPoint.bucketKey,
                            onViewEntries = { onViewBucketEntries(selectedPoint.bucketKey) }
                        )
                    }

                    // 2. Net Balance Trend Line Chart (Net Change + Cumulative Balance)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LegendDot(color = c.blueAccent, label = s.summaryNetChange)
                            LegendDot(color = c.amberAccent, label = s.summaryCumulativeBalance)
                        }

                        NetTrendLineCanvasChart(
                            points = state.points,
                            selectedBucketKey = state.selectedBucketKey,
                            slotWidthDp = slotWidthDp,
                            scrollState = chartScrollState,
                            onSelectBucket = onSelectBucket
                        )
                    }
                }
            }
        }
    }

    if (showMonthYearPicker) {
        SummaryMonthYearPickerDialog(
            initialYear = state.anchor.year,
            initialMonthZeroBased = state.anchor.monthZeroBased,
            onDismiss = { showMonthYearPicker = false },
            onSelect = { y, m ->
                onPickAnchor(SummaryAnchor(year = y, monthZeroBased = m, dayOfMonth = 1))
                showMonthYearPicker = false
            }
        )
    }

    if (showYearPicker) {
        SummaryYearPickerDialog(
            initialYear = state.anchor.year,
            onDismiss = { showYearPicker = false },
            onSelect = { y ->
                onPickAnchor(SummaryAnchor(year = y, monthZeroBased = Calendar.JANUARY, dayOfMonth = 1))
                showYearPicker = false
            }
        )
    }
}

@Composable
private fun HighlightStatTile(
    title: String,
    value: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LedgerTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniMetadataChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    bg: Color,
    fg: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            Text(
                text = text,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = LedgerTheme.colors.textSecondary
        )
    }
}

@Composable
private fun SelectedBucketTooltipCard(
    point: SummaryChartPoint,
    isDrilledIn: Boolean,
    onViewEntries: () -> Unit
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = c.surfaceSubtle,
        border = BorderStroke(1.dp, c.indigoAccent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chart_tooltip_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = point.fullLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.textPrimary
                    )
                    Text(
                        text = "${point.entryCount} entries",
                        fontSize = 11.sp,
                        color = c.textMuted
                    )
                }

                FilledTonalButton(
                    onClick = onViewEntries,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("chart_view_entries_button")
                ) {
                    Text(
                        text = if (isDrilledIn) "Viewing" else s.summaryViewEntries,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(s.paid, fontSize = 10.sp, color = c.textMuted)
                    Text(
                        text = String.format(Locale.US, "₹%,.0f", point.totalPaid),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.redDebit
                    )
                }
                Column {
                    Text(s.received, fontSize = 10.sp, color = c.textMuted)
                    Text(
                        text = String.format(Locale.US, "₹%,.0f", point.totalReceived),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.greenPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(s.summaryNetLabel, fontSize = 10.sp, color = c.textMuted)
                    Text(
                        text = String.format(
                            Locale.US,
                            "%s₹%,.0f",
                            if (point.netChange >= 0) "+" else "-",
                            kotlin.math.abs(point.netChange)
                        ),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (point.netChange >= 0) c.greenPrimary else c.redDebit
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupedBarCanvasChart(
    points: List<SummaryChartPoint>,
    selectedBucketKey: String?,
    slotWidthDp: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onSelectBucket: (String?) -> Unit
) {
    val c = LedgerTheme.colors
    val maxVal = remember(points) {
        points.maxOfOrNull { maxOf(it.totalPaid, it.totalReceived) }?.coerceAtLeast(1.0) ?: 1.0
    }
    val density = LocalDensity.current
    val yAxisWidth = 48.dp
    val chartHeight = 166.dp

    val contentDesc = remember(points) {
        val active = points.count { it.entryCount > 0 }
        "Paid versus Received bar chart with ${points.size} periods and $active active periods"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = contentDesc }
    ) {
        val plotViewportWidth = (maxWidth - yAxisWidth).coerceAtLeast(180.dp)
        val totalCanvasWidth = maxOf(plotViewportWidth, slotWidthDp * points.size)

        Row(modifier = Modifier.fillMaxWidth()) {
            // Fixed Compact Y-Axis Labels
            Column(
                modifier = Modifier
                    .width(yAxisWidth)
                    .height(chartHeight)
                    .padding(top = 8.dp, bottom = 22.dp, end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(LedgerDateFormatter.formatCompactRupee(maxVal), fontSize = 9.5.sp, color = c.textMuted, maxLines = 1)
                Text(LedgerDateFormatter.formatCompactRupee(maxVal / 2.0), fontSize = 9.5.sp, color = c.textMuted, maxLines = 1)
                Text("₹0", fontSize = 9.5.sp, color = c.textMuted, maxLines = 1)
            }

            // Scrollable Grouped Bar Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                val labelTextColor = c.textSecondary.toArgb()
                val selectedLabelColor = c.indigoAccent.toArgb()
                val textSizePx = with(density) { 10.sp.toPx() }
                val paidColor = c.redDebit
                val recColor = c.greenPrimary
                val highlightBg = c.indigoAccent.copy(alpha = 0.14f)
                val currentBandBg = c.blueAccent.copy(alpha = 0.08f)
                val highestStarColor = c.amberAccent
                val lowestColor = c.textMuted.copy(alpha = 0.65f)
                val gridColor = c.border

                Canvas(
                    modifier = Modifier
                        .width(totalCanvasWidth)
                        .height(chartHeight)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                if (points.isNotEmpty() && size.width > 0) {
                                    val slotW = size.width.toFloat() / points.size
                                    val idx = (offset.x / slotW).toInt().coerceIn(0, points.lastIndex)
                                    onSelectBucket(points[idx].bucketKey)
                                }
                            }
                        }
                ) {
                    val topPad = 14.dp.toPx()
                    val bottomPad = 24.dp.toPx()
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(20f)
                    val baseLineY = topPad + plotH

                    // Grid lines (max, mid, 0)
                    listOf(topPad, topPad + plotH / 2f, baseLineY).forEach { y ->
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val count = points.size.coerceAtLeast(1)
                    val slotW = size.width / count
                    val barPairW = (slotW * 0.62f).coerceAtMost(26.dp.toPx())
                    val singleBarW = (barPairW * 0.44f).coerceAtLeast(4.dp.toPx())
                    val barGap = (barPairW - singleBarW * 2f).coerceAtLeast(2.dp.toPx())

                    val labelPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = textSizePx
                    }

                    points.forEachIndexed { idx, pt ->
                        val centerX = slotW * idx + slotW / 2f
                        val isSelected = pt.bucketKey == selectedBucketKey

                        if (isSelected) {
                            drawRoundRect(
                                color = highlightBg,
                                topLeft = Offset(slotW * idx + 2f, 2f),
                                size = Size(slotW - 4f, size.height - 4f),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        } else if (pt.isCurrentOrSelected) {
                            drawRoundRect(
                                color = currentBandBg,
                                topLeft = Offset(slotW * idx + 2f, 2f),
                                size = Size(slotW - 4f, size.height - 4f),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }

                        val paidH = ((pt.totalPaid / maxVal) * plotH).toFloat().coerceIn(0f, plotH)
                        val recH = ((pt.totalReceived / maxVal) * plotH).toFloat().coerceIn(0f, plotH)
                        val startX = centerX - (singleBarW + barGap / 2f)

                        if (paidH > 0f) {
                            drawRoundRect(
                                color = paidColor,
                                topLeft = Offset(startX, baseLineY - paidH),
                                size = Size(singleBarW, paidH.coerceAtLeast(3.dp.toPx())),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }

                        if (recH > 0f) {
                            drawRoundRect(
                                color = recColor,
                                topLeft = Offset(startX + singleBarW + barGap, baseLineY - recH),
                                size = Size(singleBarW, recH.coerceAtLeast(3.dp.toPx())),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }

                        val maxBarTop = baseLineY - maxOf(paidH, recH)
                        if (pt.isHighest) {
                            drawCircle(
                                color = highestStarColor,
                                radius = 3.5.dp.toPx(),
                                center = Offset(centerX, (maxBarTop - 6.dp.toPx()).coerceAtLeast(5.dp.toPx()))
                            )
                        } else if (pt.isLowest && pt.entryCount > 0) {
                            drawCircle(
                                color = lowestColor,
                                radius = 2.5.dp.toPx(),
                                center = Offset(centerX, (maxBarTop - 5.dp.toPx()).coerceAtLeast(5.dp.toPx()))
                            )
                        }

                        labelPaint.color = if (isSelected || pt.isCurrentOrSelected) selectedLabelColor else labelTextColor
                        labelPaint.isFakeBoldText = isSelected || pt.isCurrentOrSelected
                        drawContext.canvas.nativeCanvas.drawText(
                            pt.xLabel,
                            centerX,
                            size.height - 6.dp.toPx(),
                            labelPaint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetTrendLineCanvasChart(
    points: List<SummaryChartPoint>,
    selectedBucketKey: String?,
    slotWidthDp: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onSelectBucket: (String?) -> Unit
) {
    val c = LedgerTheme.colors
    val minVal = remember(points) {
        points.minOfOrNull { minOf(0.0, it.netChange, it.cumulativeBalance) } ?: 0.0
    }
    val maxVal = remember(points) {
        points.maxOfOrNull { maxOf(0.0, it.netChange, it.cumulativeBalance) } ?: 1.0
    }
    val span = (maxVal - minVal).coerceAtLeast(1.0)
    val density = LocalDensity.current
    val yAxisWidth = 48.dp
    val chartHeight = 132.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val plotViewportWidth = (maxWidth - yAxisWidth).coerceAtLeast(180.dp)
        val totalCanvasWidth = maxOf(plotViewportWidth, slotWidthDp * points.size)

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .width(yAxisWidth)
                    .height(chartHeight)
                    .padding(top = 8.dp, bottom = 22.dp, end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(LedgerDateFormatter.formatCompactRupee(maxVal, includeSign = true), fontSize = 9.5.sp, color = c.textMuted, maxLines = 1)
                Text(LedgerDateFormatter.formatCompactRupee((maxVal + minVal) / 2.0, includeSign = true), fontSize = 9.5.sp, color = c.textMuted, maxLines = 1)
                Text(LedgerDateFormatter.formatCompactRupee(minVal, includeSign = true), fontSize = 9.5.sp, color = c.textMuted, maxLines = 1)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                val netColor = c.blueAccent
                val cumColor = c.amberAccent
                val gridColor = c.border
                val zeroColor = c.textMuted.copy(alpha = 0.5f)
                val labelTextColor = c.textSecondary.toArgb()
                val selectedLabelColor = c.indigoAccent.toArgb()
                val textSizePx = with(density) { 10.sp.toPx() }
                val cardBg = c.card

                Canvas(
                    modifier = Modifier
                        .width(totalCanvasWidth)
                        .height(chartHeight)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                if (points.isNotEmpty() && size.width > 0) {
                                    val slotW = size.width.toFloat() / points.size
                                    val idx = (offset.x / slotW).toInt().coerceIn(0, points.lastIndex)
                                    onSelectBucket(points[idx].bucketKey)
                                }
                            }
                        }
                ) {
                    val topPad = 12.dp.toPx()
                    val bottomPad = 24.dp.toPx()
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(20f)

                    fun mapY(v: Double): Float {
                        val ratio = ((v - minVal) / span).toFloat().coerceIn(0f, 1f)
                        return topPad + plotH * (1f - ratio)
                    }

                    // Dashed zero baseline
                    val zeroY = mapY(0.0)
                    drawLine(
                        color = zeroColor,
                        start = Offset(0f, zeroY),
                        end = Offset(size.width, zeroY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, topPad),
                        end = Offset(size.width, topPad),
                        strokeWidth = 0.75.dp.toPx()
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, topPad + plotH),
                        end = Offset(size.width, topPad + plotH),
                        strokeWidth = 0.75.dp.toPx()
                    )

                    val count = points.size.coerceAtLeast(1)
                    val slotW = size.width / count

                    val netPath = Path()
                    val cumPath = Path()
                    val labelPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = textSizePx
                    }

                    points.forEachIndexed { idx, pt ->
                        val cx = slotW * idx + slotW / 2f
                        val netY = mapY(pt.netChange)
                        val cumY = mapY(pt.cumulativeBalance)
                        if (idx == 0) {
                            netPath.moveTo(cx, netY)
                            cumPath.moveTo(cx, cumY)
                        } else {
                            netPath.lineTo(cx, netY)
                            cumPath.lineTo(cx, cumY)
                        }
                    }

                    drawPath(
                        path = cumPath,
                        color = cumColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                        )
                    )
                    drawPath(
                        path = netPath,
                        color = netColor,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    points.forEachIndexed { idx, pt ->
                        val cx = slotW * idx + slotW / 2f
                        val netY = mapY(pt.netChange)
                        val cumY = mapY(pt.cumulativeBalance)
                        val isSelected = pt.bucketKey == selectedBucketKey

                        drawCircle(color = cumColor, radius = if (isSelected) 4.dp.toPx() else 2.5.dp.toPx(), center = Offset(cx, cumY))
                        drawCircle(color = netColor, radius = if (isSelected) 5.5.dp.toPx() else 3.5.dp.toPx(), center = Offset(cx, netY))
                        if (isSelected) {
                            drawCircle(color = cardBg, radius = 2.5.dp.toPx(), center = Offset(cx, netY))
                        }

                        labelPaint.color = if (isSelected || pt.isCurrentOrSelected) selectedLabelColor else labelTextColor
                        labelPaint.isFakeBoldText = isSelected || pt.isCurrentOrSelected
                        drawContext.canvas.nativeCanvas.drawText(
                            pt.xLabel,
                            cx,
                            size.height - 6.dp.toPx(),
                            labelPaint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChartSkeleton() {
    val c = LedgerTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chart_loading_skeleton"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.cardElevated)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.cardElevated)
        )
    }
}

@Composable
private fun SummaryMonthYearPickerDialog(
    initialYear: Int,
    initialMonthZeroBased: Int,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit
) {
    val c = LedgerTheme.colors
    val today = remember { SummaryAnchor.today() }
    var tempYear by remember(initialYear) { mutableIntStateOf(initialYear) }
    var tempMonth by remember(initialMonthZeroBased) { mutableIntStateOf(initialMonthZeroBased) }
    val monthNames = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Select Month & Year",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.cardElevated, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { tempYear -= 1 }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year", tint = c.textPrimary)
                    }
                    Text(
                        text = tempYear.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.textPrimary
                    )
                    IconButton(
                        onClick = {
                            if (tempYear < today.year) {
                                tempYear += 1
                                if (tempYear == today.year && tempMonth > today.monthZeroBased) {
                                    tempMonth = today.monthZeroBased
                                }
                            }
                        },
                        enabled = tempYear < today.year,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Next Year",
                            tint = if (tempYear < today.year) c.textPrimary else c.textMuted.copy(alpha = 0.35f)
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    items((0..11).toList()) { mIdx ->
                        val isFuture = tempYear > today.year || (tempYear == today.year && mIdx > today.monthZeroBased)
                        val isSelected = mIdx == tempMonth && !isFuture
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) c.indigoAccent else c.surfaceSubtle,
                            border = BorderStroke(1.dp, if (isSelected) c.indigoAccent else c.border),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !isFuture) { tempMonth = mIdx }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = monthNames[mIdx],
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isFuture -> c.textMuted.copy(alpha = 0.35f)
                                        isSelected -> Color.White
                                        else -> c.textPrimary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(tempYear, tempMonth) },
                colors = ButtonDefaults.buttonColors(containerColor = c.indigoAccent)
            ) {
                Text("Apply", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.textMuted)
            }
        }
    )
}

@Composable
private fun SummaryYearPickerDialog(
    initialYear: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val c = LedgerTheme.colors
    val todayYear = remember { SummaryAnchor.today().year }
    val years = remember(todayYear) { ((todayYear - 11)..todayYear).reversed().toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Select Year",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary
            )
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                items(years) { yr ->
                    val isSelected = yr == initialYear
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) c.greenPrimary else c.surfaceSubtle,
                        border = BorderStroke(1.dp, if (isSelected) c.greenPrimary else c.border),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(yr) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = yr.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) Color.White else c.textPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = c.textMuted)
            }
        }
    )
}
