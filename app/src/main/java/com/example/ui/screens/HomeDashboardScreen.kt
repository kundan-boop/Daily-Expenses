package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerEntity
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.RecentTransactionEntry
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private enum class RecentEntryFilter {
    ALL,
    CREDIT,
    DEBIT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    viewModel: DailyExpenseViewModel,
    authViewModel: AuthViewModel,
    onOpenMenu: () -> Unit,
    onQuickAddTransaction: (type: String) -> Unit,
    onAddCustomerClick: () -> Unit,
    onOpenCustomerList: () -> Unit,
    onCustomerClick: (CustomerEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGoogleDriveSettings: () -> Unit = onOpenSettings,
    onOpenFirebaseSettings: () -> Unit = onOpenSettings
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val recentTransactions by viewModel.recentTransactions.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val firebaseBackupStatus by viewModel.firebaseBackupStatus.collectAsStateWithLifecycle()
    val showDashboardBackupAlert by viewModel.showDashboardBackupAlert.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var selectedEntryFilter by remember { mutableStateOf(RecentEntryFilter.ALL) }
    val filteredRecent = remember(recentTransactions, selectedEntryFilter) {
        when (selectedEntryFilter) {
            RecentEntryFilter.ALL -> recentTransactions
            RecentEntryFilter.CREDIT -> recentTransactions.filter { it.transaction.transactionType == "I_RECEIVED" }
            RecentEntryFilter.DEBIT -> recentTransactions.filter { it.transaction.transactionType == "I_PAID" }
        }
    }

    val overallNetBalance = dashboardSummary.totalYouWillReceive - dashboardSummary.totalYouNeedToPay

    val showCloudBanner = showDashboardBackupAlert

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = c.background
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.background)
        ) {
            // Top App Bar with Hamburger Menu Icon
            Surface(
                color = c.brandHeader,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hamburger_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Menu",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.businessName.ifBlank { s.appName },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = s.mainDashboard,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                            letterSpacing = 0.8.sp
                        )
                    }

                    val customerCountText = remember(dashboardSummary.totalCustomersCount) {
                        NumberFormat.getNumberInstance(Locale.US).format(dashboardSummary.totalCustomersCount)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenCustomerList() }
                            .testTag("header_customers_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = s.customerLedger,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = customerCountText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshData() },
                state = pullState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = c.card,
                        color = c.greenPrimary
                    )
                }
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    // Cloud Backup Banner (Drive & Firebase shown separately)
                    item(key = "drive_backup_banner") {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showCloudBanner,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            BackupStatusWidget(
                                backupStatus = backupStatus,
                                firebaseBackupStatus = firebaseBackupStatus,
                                pendingSyncCount = pendingSyncCount,
                                onSyncNow = { viewModel.triggerDriveAutoBackupNow() },
                                onSyncFirebaseNow = { viewModel.syncFirebaseBackupNow() },
                                onOpenSettings = onOpenGoogleDriveSettings,
                                onOpenFirebaseSettings = onOpenFirebaseSettings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 1. Current Balance Hero Card
                    item(key = "balance_hero_card") {
                        val formatCurrency = { amount: Double ->
                            val absVal = kotlin.math.abs(amount)
                            val formatted = if (absVal % 1.0 == 0.0) {
                                String.format(Locale.US, "₹%,.0f", absVal)
                            } else {
                                String.format(Locale.US, "₹%,.2f", absVal)
                            }
                            if (amount < -0.01) "-$formatted" else formatted
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_balance_card")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = if (c.isDark) {
                                                listOf(Color(0xFF0F3D24), c.brandHeader, Color(0xFF166534))
                                            } else {
                                                listOf(Color(0xFF1B5E20), GreenPrimary, Color(0xFF388E3C))
                                            }
                                        )
                                    )
                                    .padding(18.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column {
                                            Text(
                                                text = s.currentNetBalance,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.8f),
                                                letterSpacing = 0.9.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatCurrency(overallNetBalance),
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color.White.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = when {
                                                    overallNetBalance > 0.01 -> s.toCollect
                                                    overallNetBalance < -0.01 -> s.toPay
                                                    else -> s.settled
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    // 3-Column Credit / Debit / Expenses Summary
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color.White.copy(alpha = 0.14f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp, horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BalanceMiniStat(
                                                label = s.youllGet,
                                                value = formatCurrency(dashboardSummary.totalYouWillReceive),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(26.dp)
                                                    .background(Color.White.copy(alpha = 0.25f))
                                            )
                                            BalanceMiniStat(
                                                label = s.youGave,
                                                value = formatCurrency(dashboardSummary.totalYouNeedToPay),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(26.dp)
                                                    .background(Color.White.copy(alpha = 0.25f))
                                            )
                                            BalanceMiniStat(
                                                label = s.expenses,
                                                value = formatCurrency(dashboardSummary.totalExpenses),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Quick Action Buttons for Adding Transactions & Customers
                    item(key = "quick_actions_section") {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = c.card),
                            border = BorderStroke(1.dp, c.border),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = s.quickActions,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = c.textPrimary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onQuickAddTransaction("I_PAID") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = c.redDebit,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 50.dp)
                                            .testTag("dashboard_quick_paid_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = s.iPaidGave,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Button(
                                        onClick = { onQuickAddTransaction("I_RECEIVED") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = c.greenPrimary,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 50.dp)
                                            .testTag("dashboard_quick_received_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = s.iReceivedGot,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onAddCustomerClick,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, c.borderStrong),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("dashboard_quick_add_customer_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            tint = c.greenPrimary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = s.addCustomer,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = c.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onOpenCustomerList,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, c.borderStrong),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("dashboard_open_ledger_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Groups,
                                            contentDescription = null,
                                            tint = c.blueAccent,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = s.customerLedger,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = c.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Recent Credit / Debit Entries Header + Filter Pills
                    item(key = "recent_entries_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = s.recentCreditDebitEntries,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = c.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                TextButton(
                                    onClick = onOpenCustomerList,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                ) {
                                    Text(
                                        text = s.allCustomers,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.greenPrimary,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = c.greenPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RecentEntryFilter.values().forEach { filter ->
                                    val selected = selectedEntryFilter == filter
                                    val filterLabel = when (filter) {
                                        RecentEntryFilter.ALL -> s.all
                                        RecentEntryFilter.CREDIT -> s.received
                                        RecentEntryFilter.DEBIT -> s.paid
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedEntryFilter = filter },
                                        label = {
                                            Text(
                                                text = filterLabel,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = c.greenTint,
                                            selectedLabelColor = c.greenPrimary,
                                            containerColor = c.card,
                                            labelColor = c.textSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Empty state or Recent Transactions List
                    if (filteredRecent.isEmpty()) {
                        item(key = "empty_recent_entries") {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = c.card),
                                border = BorderStroke(1.dp, c.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = c.textMuted,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = s.noRecentEntries,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = s.noRecentEntriesHint,
                                        fontSize = 12.sp,
                                        color = c.textMuted
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredRecent,
                            key = { "recent_tx_${it.transaction.id}" }
                        ) { entry ->
                            RecentEntryCard(
                                entry = entry,
                                onClick = { onCustomerClick(entry.customer) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceMiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.78f),
            letterSpacing = 0.4.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentEntryCard(
    entry: RecentTransactionEntry,
    onClick: () -> Unit
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val tx = entry.transaction
    val isPaid = tx.transactionType == "I_PAID"
    val formattedAmount = if (tx.amount % 1.0 == 0.0) {
        String.format(Locale.US, "₹%,.0f", tx.amount)
    } else {
        String.format(Locale.US, "₹%,.2f", tx.amount)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("recent_entry_card_${tx.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isPaid) c.redTint else c.greenTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPaid) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (isPaid) "Debit" else "Credit",
                    tint = if (isPaid) c.redDebit else c.greenPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.customer.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val desc = buildString {
                    if (tx.comment.isNotBlank()) {
                        append(tx.comment)
                        append(" • ")
                    }
                    append(com.example.utils.LedgerDateFormatter.formatDateTimeWithYear(tx.dateTimestamp))
                }
                Text(
                    text = desc,
                    fontSize = 11.5.sp,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (tx.category.isNotBlank() || tx.paymentMode.isNotBlank()) {
                    Text(
                        text = "${tx.category} • ${tx.paymentMode}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPaid) c.redDebit else c.greenPrimary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isPaid) c.redTint else c.greenTint
                ) {
                    Text(
                        text = if (isPaid) s.gaveCredit.uppercase() else s.gotPayment.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaid) c.redDebit else c.greenPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
