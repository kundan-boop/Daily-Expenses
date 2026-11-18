package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    viewModel: DailyExpenseViewModel,
    onOpenMenu: (() -> Unit)? = null
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val allExpenses by viewModel.allExpenses.collectAsStateWithLifecycle()

    var selectedReportMonthYear by remember { mutableStateOf("2026-07") } // YYYY-MM
    var showMonthPickerModal by remember { mutableStateOf(false) }

    val monthExpenses = allExpenses.filter { it.monthYear == selectedReportMonthYear }
    val monthExpensesTotal = monthExpenses.sumOf { it.amount }

    val totalPaid = allTransactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
    val totalReceived = allTransactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }

    fun formatReadableMonth(ym: String): String {
        return try {
            val sdfIn = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val sdfOut = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val d = sdfIn.parse(ym)
            if (d != null) sdfOut.format(d) else ym
        } catch (e: Exception) {
            ym
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        // Top Green Header with Rounded Bottom and Centered Heading
        Surface(
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            color = c.brandHeader,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 16.dp, horizontal = 12.dp)
            ) {
                if (onOpenMenu != null) {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Menu",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    text = s.financialReportsTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month Selector Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text("Select Report Month", fontSize = 12.sp, color = c.textMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = formatReadableMonth(selectedReportMonthYear),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.greenPrimary
                        )
                    }

                    Button(
                        onClick = { showMonthPickerModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Month", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Month Breakdown Overview
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Statement for ${formatReadableMonth(selectedReportMonthYear)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SummaryRow(title = "Monthly Expense Items", amount = monthExpenses.size.toDouble(), color = c.textPrimary, isCount = true)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = c.divider)
                    SummaryRow(title = s.totalSpent, amount = monthExpensesTotal, color = c.redDebit)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = c.divider)
                    SummaryRow(title = "Overall Outstanding (${s.youllGet})", amount = dashboardSummary.totalYouWillReceive, color = c.greenPrimary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = c.divider)
                    SummaryRow(title = "Overall Payable (${s.youGave})", amount = dashboardSummary.totalYouNeedToPay, color = c.redDebit)
                }
            }

            // Download / Export Month Report Action
            Button(
                onClick = {
                    try {
                        val readableMonth = formatReadableMonth(selectedReportMonthYear)
                        val pdfFile = com.example.utils.PdfReportExporter.generateMonthlyReportPdf(
                            context = context,
                            monthPeriodStr = readableMonth,
                            monthExpensesTotal = monthExpensesTotal,
                            monthExpenses = monthExpenses,
                            totalYouWillReceive = dashboardSummary.totalYouWillReceive,
                            totalYouNeedToPay = dashboardSummary.totalYouNeedToPay,
                            totalCustomersCount = dashboardSummary.totalCustomersCount
                        )
                        val pdfUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            pdfFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, pdfUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Financial Report - $readableMonth")
                            putExtra(Intent.EXTRA_TEXT, "Daily Expense & Ledger – Monthly Financial Statement PDF for $readableMonth")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Download / Share $readableMonth Report PDF"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error generating PDF report: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Download ${formatReadableMonth(selectedReportMonthYear)} Report PDF",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Month Selector Dialog for Reports (supporting any month e.g., July 2026, Jan 2026, 1960 to 2035)
    if (showMonthPickerModal) {
        val curY = selectedReportMonthYear.split("-").getOrElse(0) { "2026" }.toIntOrNull() ?: 2026
        val curM = (selectedReportMonthYear.split("-").getOrElse(1) { "07" }.toIntOrNull() ?: 7) - 1

        var tempYear by remember { mutableStateOf(curY) }
        var tempMonthIdx by remember { mutableStateOf(curM) }

        val monthsNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        AlertDialog(
            onDismissRequest = { showMonthPickerModal = false },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
            title = {
                Text("Select Report Month & Year", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Year Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.surfaceSubtle, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { tempYear -= 1 }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = c.greenPrimary)
                        }

                        Text(
                            text = tempYear.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.greenPrimary
                        )

                        IconButton(onClick = { tempYear += 1 }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.greenPrimary)
                        }
                    }

                    // Quick Jump Years
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(2026, 2025, 2024, 2020).forEach { y ->
                            FilterChip(
                                selected = tempYear == y,
                                onClick = { tempYear = y },
                                label = { Text(y.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = c.greenPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = c.surfaceSubtle,
                                    labelColor = c.textSecondary
                                )
                            )
                        }
                    }

                    // Months Grid
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in 0..3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0..2) {
                                    val idx = r * 3 + col
                                    val isSel = idx == tempMonthIdx
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) c.greenPrimary else c.surfaceSubtle,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { tempMonthIdx = idx }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = monthsNames[idx],
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isSel) Color.White else c.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mStr = String.format("%02d", tempMonthIdx + 1)
                        selectedReportMonthYear = "$tempYear-$mStr"
                        showMonthPickerModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary)
                ) {
                    Text(s.done, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMonthPickerModal = false }) {
                    Text(s.cancel, color = c.textMuted)
                }
            }
        )
    }
}

@Composable
fun SummaryRow(title: String, amount: Double, color: Color = LedgerTheme.colors.textPrimary, isCount: Boolean = false) {
    val c = LedgerTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.textSecondary)
        Text(
            text = if (isCount) "${amount.toInt()} items" else String.format("₹ %,.2f", amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}
