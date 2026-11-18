package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerEntity
import com.example.ui.CustomerBalanceSummary
import com.example.ui.CustomerFilter
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DailyExpenseViewModel,
    authViewModel: com.example.ui.AuthViewModel? = null,
    onCustomerClick: (CustomerEntity) -> Unit,
    onAddCustomerClick: () -> Unit,
    onEditCustomerClick: (CustomerEntity) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenGoogleDriveSettings: () -> Unit = onOpenSettings,
    onOpenFirebaseSettings: () -> Unit = onOpenSettings,
    onOpenMenu: (() -> Unit)? = null
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val filteredSummaries by viewModel.filteredCustomerSummaries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.customerSearchQuery.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val firebaseBackupStatus by viewModel.firebaseBackupStatus.collectAsStateWithLifecycle()
    val userProfile by authViewModel?.userProfile?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    val view = androidx.compose.ui.platform.LocalView.current
    val isImeVisible = WindowInsets.isImeVisible
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Single deterministic BackHandler when IME is visible or search has text
    BackHandler(enabled = isImeVisible || searchQuery.isNotEmpty()) {
        val liveIme = isImeVisible ||
            (androidx.core.view.ViewCompat.getRootWindowInsets(view)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
        if (liveIme) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else if (searchQuery.isNotEmpty()) {
            viewModel.clearCustomerSearchQuery()
            focusManager.clearFocus()
        }
    }

    // Clear search when leaving Dashboard page so searches never carry over
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearCustomerSearchQuery()
        }
    }

    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }

    val customerListState = rememberLazyListState()
    val topCustomerId = remember(filteredSummaries) { filteredSummaries.firstOrNull()?.customer?.id }
    LaunchedEffect(topCustomerId, filteredSummaries.size) {
        if (filteredSummaries.isNotEmpty()) {
            customerListState.animateScrollToItem(0)
        }
    }

    var isHeaderExpanded by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                val delta = available.y
                if (delta < -8f) {
                    // Scrolling top -> down: collapse header to maximize customer list space
                    isHeaderExpanded = false
                } else if (delta > 8f) {
                    // Scrolling bottom -> top: expand header to show summary and search again
                    isHeaderExpanded = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    val firstVisibleItemIndex by remember { derivedStateOf { customerListState.firstVisibleItemIndex } }
    val firstVisibleItemScrollOffset by remember { derivedStateOf { customerListState.firstVisibleItemScrollOffset } }

    LaunchedEffect(firstVisibleItemIndex, firstVisibleItemScrollOffset) {
        if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= 5) {
            isHeaderExpanded = true
        }
    }

    val overallNetBalance = dashboardSummary.totalYouWillReceive - dashboardSummary.totalYouNeedToPay

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = c.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCustomerClick,
                icon = { Icon(Icons.Default.Add, contentDescription = s.addCustomer) },
                text = { Text(s.addCustomer.uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                containerColor = c.greenPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_customer_fab")
            )
        }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.background)
        ) {
            // Editorial Header Bar - Clickable D or title goes to Settings
            Surface(
                color = c.brandHeader,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = if (onOpenMenu != null) 4.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenMenu != null) {
                        IconButton(
                            onClick = onOpenMenu,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("customer_ledger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Menu",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenSettings() }
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.card),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!userProfile?.profileImageUri.isNullOrBlank()) {
                                val imgUri = userProfile?.profileImageUri!!
                                coil.compose.AsyncImage(
                                    model = if (imgUri.startsWith("/")) java.io.File(imgUri) else imgUri,
                                    contentDescription = "Profile Photo",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Text(
                                    text = userProfile?.name?.take(1)?.uppercase() ?: "D",
                                    color = c.greenPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = userProfile?.name ?: s.appName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = s.ledgerCustomersTitle.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    letterSpacing = 0.8.sp,
                                    maxLines = 1
                                )

                                val formattedCustomerCount = remember(dashboardSummary.totalCustomersCount) {
                                    java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(dashboardSummary.totalCustomersCount)
                                }

                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = Color.White.copy(alpha = 0.22f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .clickable { onOpenSettings() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                        Text(
                                            text = "$formattedCustomerCount ${s.totalCustomersLabel}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Header Details (Stat Banner & Search Bar)
            AnimatedVisibility(
                visible = isHeaderExpanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.background)
                ) {
                    // Full Width Unified 4-Column Stat Banner
                    Surface(
                        color = c.card,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            HorizontalDivider(color = c.border, thickness = 1.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val formatSmartAmount = { amount: Double ->
                                    val absVal = kotlin.math.abs(amount)
                                    when {
                                        absVal >= 10_000_000_000 -> String.format("₹%.2fB", amount / 1_000_000_000)
                                        absVal >= 100_000_000 -> String.format("₹%.2fCr", amount / 10_000_000)
                                        absVal >= 10_000_000 -> String.format("₹%.1fCr", amount / 10_000_000)
                                        absVal >= 100_000 -> String.format("₹%.2fL", amount / 100_000)
                                        else -> String.format("₹%,.0f", amount)
                                    }
                                }

                                val getFontSizeForText = { textStr: String ->
                                    when {
                                        textStr.length > 8 -> 9.sp
                                        textStr.length > 6 -> 11.sp
                                        textStr.length > 4 -> 12.sp
                                        else -> 13.sp
                                    }
                                }

                                val netText = formatSmartAmount(overallNetBalance)
                                val get = formatSmartAmount(dashboardSummary.totalYouWillReceive)
                                val give = formatSmartAmount(dashboardSummary.totalYouNeedToPay)
                                val exp = formatSmartAmount(dashboardSummary.totalExpenses)

                                // Col 1: Net Balance
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = s.netBalance.uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textMuted,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = netText,
                                        fontSize = getFontSizeForText(netText),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = if (overallNetBalance >= 0) c.greenPrimary else c.redDebit
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(28.dp),
                                    color = c.border
                                )

                                // Col 2: You'll Get
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = s.youllGet,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textMuted,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = get,
                                        fontSize = getFontSizeForText(get),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = c.greenPrimary
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(28.dp),
                                    color = c.border
                                )

                                // Col 3: You Gave
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = s.youGave,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textMuted,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = give,
                                        fontSize = getFontSizeForText(give),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = c.redDebit
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(28.dp),
                                    color = c.border
                                )

                                // Col 4: Expenses
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = s.expenses,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textMuted,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = exp,
                                        fontSize = getFontSizeForText(exp),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = c.redDebit
                                    )
                                }
                            }

                            HorizontalDivider(color = c.border, thickness = 1.dp)
                        }
                    }

                    // Prominent Backup Status Widget
                    // Visible when:
                    // 1. Drive is NOT connected (NOT_CONFIGURED)
                    // 2. Drive is offline / backup is pending (PENDING_OFFLINE)
                    // 3. Backup is currently in progress (IN_PROGRESS)
                    // 4. Backup attempt failed (FAILED)
                    // Automatically hidden when Drive is connected and backup is successfully up-to-date (SUCCESS)
                    if (backupStatus.state != com.example.utils.BackupState.SUCCESS ||
                        (firebaseBackupStatus.state != com.example.utils.BackupState.NOT_CONFIGURED &&
                            firebaseBackupStatus.state != com.example.utils.BackupState.SUCCESS)
                    ) {
                        BackupStatusWidget(
                            backupStatus = backupStatus,
                            firebaseBackupStatus = firebaseBackupStatus,
                            onSyncNow = { viewModel.triggerDriveAutoBackupNow() },
                            onSyncFirebaseNow = { viewModel.syncFirebaseBackupNow() },
                            onOpenSettings = onOpenGoogleDriveSettings,
                            onOpenFirebaseSettings = onOpenFirebaseSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    // Search Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setCustomerSearchQuery(it) },
                            placeholder = { Text(s.searchCustomersPlaceholder, fontSize = 13.sp, color = c.textMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = c.greenPrimary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearCustomerSearchQuery() }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = c.textSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = c.borderStrong,
                                focusedBorderColor = c.greenPrimary,
                                cursorColor = c.greenPrimary,
                                focusedTextColor = c.textPrimary,
                                unfocusedTextColor = c.textPrimary,
                                focusedContainerColor = c.card,
                                unfocusedContainerColor = c.card,
                                focusedPlaceholderColor = c.textMuted,
                                unfocusedPlaceholderColor = c.textMuted
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_search_input"),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // Main Content Area (Customer List)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(c.background)
                    .padding(horizontal = 16.dp)
            ) {

                // Customer List with Pull-to-Refresh
                val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshData() },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            containerColor = c.card,
                            color = c.greenPrimary
                        )
                    }
                ) {
                    if (filteredSummaries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.GroupOff,
                                    contentDescription = null,
                                    tint = c.textMuted,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) s.noCustomersYet else s.noCustomersFound,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = c.textSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) s.tapPlusToAddCustomer else s.tryDifferentSearch,
                                    fontSize = 13.sp,
                                    color = c.textMuted
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = customerListState,
                            contentPadding = PaddingValues(bottom = 100.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                        ) {
                            itemsIndexed(filteredSummaries, key = { _, item -> item.customer.id }) { index, summary ->
                                Column {
                                    CustomerCardItem(
                                        summary = summary,
                                        onClick = { onCustomerClick(summary.customer) }
                                    )

                                    if (index < filteredSummaries.lastIndex) {
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = c.divider
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Customer Confirmation Dialog
    if (customerToDelete != null) {
        val targetCust = customerToDelete!!
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp),
            title = { Text("Delete Customer?", fontWeight = FontWeight.ExtraBold, color = c.textPrimary) },
            text = {
                Text(
                    text = "Are you sure you want to delete ${targetCust.name}? All transaction history for this customer will be removed.",
                    color = c.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(targetCust)
                        customerToDelete = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.redDebit)
                ) {
                    Text(s.remove, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { customerToDelete = null },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.borderStrong)
                ) {
                    Text(s.cancel, color = c.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
fun CustomerCardItem(
    summary: CustomerBalanceSummary,
    onClick: () -> Unit
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val customer = summary.customer
    val netBalance = summary.netBalance
    val formattedLastDate = remember(customer.lastUpdatedTimestamp, s.lastEntryPrefix) {
        val dt = com.example.utils.LedgerDateFormatter.formatLastEntryDateTime(customer.lastUpdatedTimestamp)
        if (dt.isNotBlank()) "${s.lastEntryPrefix} $dt" else ""
    }

    val colorPairs = listOf(
        c.amberTint to c.amberAccent,
        c.blueTint to c.blueAccent,
        c.indigoTint to c.indigoAccent,
        c.greenTint to c.greenPrimary
    )
    val avatarColors = colorPairs[(customer.id % colorPairs.size).toInt().coerceAtLeast(0)]

    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = c.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("customer_item_${customer.id}")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isTightWidth = maxWidth < 360.dp
            val baseDateFontSize = when {
                maxWidth < 340.dp -> 10.sp
                maxWidth < 420.dp -> 11.sp
                else -> 12.sp
            }
            var adjustedDateFontSize by remember(customer.lastUpdatedTimestamp, maxWidth) {
                mutableStateOf(baseDateFontSize)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Customer Avatar
                Box(
                    modifier = Modifier
                        .size(if (isTightWidth) 40.dp else 44.dp)
                        .clip(CircleShape)
                        .background(avatarColors.first),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer.name.take(1).uppercase(),
                        fontSize = if (isTightWidth) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatarColors.second
                    )
                }

                Spacer(modifier = Modifier.width(if (isTightWidth) 10.dp else 12.dp))

                // Info Column (weight(1f) guarantees customer name & balance never shift across 320px-768px)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val subtitleText = when {
                            formattedLastDate.isNotBlank() -> formattedLastDate
                            customer.mobileNumber.isNotBlank() -> customer.mobileNumber
                            else -> "No activity"
                        }
                        Text(
                            text = subtitleText,
                            fontSize = adjustedDateFontSize,
                            color = c.textMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layoutResult ->
                                if (layoutResult.hasVisualOverflow && adjustedDateFontSize.value > 9f) {
                                    adjustedDateFontSize = (adjustedDateFontSize.value - 1f).sp
                                }
                            },
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("customer_last_entry_${customer.id}")
                        )

                        if (customer.collectToday || customer.collectDueDate != null) {
                            val endOfTodayMs = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                set(java.util.Calendar.HOUR_OF_DAY, 23)
                                set(java.util.Calendar.MINUTE, 59)
                                set(java.util.Calendar.SECOND, 59)
                                set(java.util.Calendar.MILLISECOND, 999)
                            }.timeInMillis

                            val isPastOrToday = if (customer.collectDueDate != null) {
                                customer.collectDueDate!! <= endOfTodayMs
                            } else {
                                true
                            }

                            val badgeBgColor = if (isPastOrToday) c.redTint else c.greenTint
                            val badgeTextColor = if (isPastOrToday) c.redDebit else c.greenPrimary

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = badgeBgColor
                            ) {
                                val badgeText = if (customer.collectDueDate != null) {
                                    val sdfBadge = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply {
                                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    }
                                    "DUE: " + sdfBadge.format(Date(customer.collectDueDate!!)).uppercase()
                                } else {
                                    s.collectTodayBadge.uppercase()
                                }
                                Text(
                                    text = badgeText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Balance in Red indication for Deduction customers, or normal status
                val isDeductionCustomer = customer.name.contains("Deduction", ignoreCase = true)

                Column(horizontalAlignment = Alignment.End) {
                    if (isDeductionCustomer) {
                        val expenseVal = if (netBalance != 0.0) Math.abs(netBalance) else summary.totalPaid
                        Text(
                            text = String.format("₹ %,.0f", expenseVal),
                            fontSize = if (isTightWidth) 14.5.sp else 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.redDebit,
                            maxLines = 1
                        )
                        Text(s.expenses, fontSize = 10.sp, color = c.redDebit, fontWeight = FontWeight.Bold, maxLines = 1)
                    } else {
                        when {
                            netBalance > 0.01 -> {
                                Text(
                                    text = String.format("₹ %,.0f", netBalance),
                                    fontSize = if (isTightWidth) 14.5.sp else 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = c.greenPrimary,
                                    maxLines = 1
                                )
                                Text(s.youllGet, fontSize = 10.sp, color = c.greenPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            netBalance < -0.01 -> {
                                Text(
                                    text = String.format("₹ %,.0f", Math.abs(netBalance)),
                                    fontSize = if (isTightWidth) 14.5.sp else 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = c.redDebit,
                                    maxLines = 1
                                )
                                Text(s.youGave, fontSize = 10.sp, color = c.redDebit, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            else -> {
                                Text(
                                    text = "₹ 0",
                                    fontSize = if (isTightWidth) 14.sp else 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.textMuted,
                                    maxLines = 1
                                )
                                Text(s.settled, fontSize = 10.sp, color = c.textMuted, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class BackupDisplayData(
    val bgColor: Color,
    val borderColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color,
    val title: String,
    val subtitle: String
)

@Composable
fun BackupStatusWidget(
    backupStatus: com.example.utils.DriveBackupStatus,
    firebaseBackupStatus: com.example.utils.FirebaseBackupStatus = com.example.utils.FirebaseBackupStatus(),
    onSyncNow: () -> Unit,
    onSyncFirebaseNow: () -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFirebaseSettings: () -> Unit = onOpenSettings
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val driveDisplayData = when (backupStatus.state) {
        com.example.utils.BackupState.SUCCESS -> {
            BackupDisplayData(
                bgColor = c.greenTint,
                borderColor = c.greenBorder,
                icon = Icons.Default.CloudDone,
                iconColor = c.greenPrimary,
                title = s.driveBackupConnected,
                subtitle = backupStatus.lastBackupFormatted?.let { "Synced $it • DailyExpenseBackups" }
                    ?: "Auto-synced to DailyExpenseBackups"
            )
        }
        com.example.utils.BackupState.PENDING_OFFLINE -> {
            BackupDisplayData(
                bgColor = c.amberTint,
                borderColor = c.amberBorder,
                icon = Icons.Default.CloudQueue,
                iconColor = c.amberAccent,
                title = s.driveBackupQueued,
                subtitle = "Will upload automatically when connected"
            )
        }
        com.example.utils.BackupState.FAILED -> {
            BackupDisplayData(
                bgColor = c.redTint,
                borderColor = c.redBorder,
                icon = Icons.Default.CloudOff,
                iconColor = c.redDebit,
                title = s.driveBackupRetry,
                subtitle = backupStatus.errorMessage ?: "Tap to retry backup"
            )
        }
        com.example.utils.BackupState.IN_PROGRESS -> {
            BackupDisplayData(
                bgColor = c.blueTint,
                borderColor = c.blueBorder,
                icon = Icons.Default.Sync,
                iconColor = c.blueAccent,
                title = "${s.googleDriveTitle} • Syncing...",
                subtitle = "Uploading snapshot to DailyExpenseBackups"
            )
        }
        com.example.utils.BackupState.NOT_CONFIGURED -> {
            BackupDisplayData(
                bgColor = c.card,
                borderColor = c.border,
                icon = Icons.Default.CloudUpload,
                iconColor = c.textMuted,
                title = s.driveBackupNotConnected,
                subtitle = s.tapToConnectDrive
            )
        }
    }

    val firebaseBadgeText = when (firebaseBackupStatus.state) {
        com.example.utils.BackupState.SUCCESS -> "CLOUD SYNCED"
        com.example.utils.BackupState.PENDING_OFFLINE -> "QUEUED"
        com.example.utils.BackupState.FAILED -> "RETRY"
        com.example.utils.BackupState.IN_PROGRESS -> "SYNCING"
        com.example.utils.BackupState.NOT_CONFIGURED -> "SIGN IN"
    }

    val driveBadgeText = when (backupStatus.state) {
        com.example.utils.BackupState.SUCCESS -> "DRIVE CONNECTED"
        com.example.utils.BackupState.PENDING_OFFLINE -> "QUEUED"
        com.example.utils.BackupState.FAILED -> "FAILED"
        com.example.utils.BackupState.IN_PROGRESS -> "SYNCING"
        com.example.utils.BackupState.NOT_CONFIGURED -> "CONNECT"
    }

    Surface(
        color = driveDisplayData.bgColor,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, driveDisplayData.borderColor),
        modifier = modifier.testTag("backup_status_widget")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Google Drive Backup Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (backupStatus.state == com.example.utils.BackupState.NOT_CONFIGURED) {
                            onOpenSettings()
                        } else {
                            onSyncNow()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (backupStatus.state == com.example.utils.BackupState.IN_PROGRESS) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = driveDisplayData.iconColor
                    )
                } else {
                    Icon(
                        imageVector = driveDisplayData.icon,
                        contentDescription = "Google Drive Backup Status",
                        tint = driveDisplayData.iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driveDisplayData.title,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = driveDisplayData.subtitle,
                        fontSize = 10.sp,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = driveDisplayData.iconColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = driveBadgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = driveDisplayData.iconColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = driveDisplayData.borderColor.copy(alpha = 0.7f))

            // 2. Firebase Cloud Backup Row (Completely separate status & action)
            val fbColor = when (firebaseBackupStatus.state) {
                com.example.utils.BackupState.SUCCESS -> c.greenPrimary
                com.example.utils.BackupState.PENDING_OFFLINE -> c.amberAccent
                com.example.utils.BackupState.FAILED -> c.redDebit
                com.example.utils.BackupState.IN_PROGRESS -> c.blueAccent
                com.example.utils.BackupState.NOT_CONFIGURED -> c.textMuted
            }
            val fbSubtitle = when (firebaseBackupStatus.state) {
                com.example.utils.BackupState.NOT_CONFIGURED -> s.firebaseBackupSubtitleSignedOut
                com.example.utils.BackupState.IN_PROGRESS -> "Syncing encrypted chunks to Firebase..."
                com.example.utils.BackupState.PENDING_OFFLINE -> "Offline • Queued for cloud sync"
                com.example.utils.BackupState.FAILED -> firebaseBackupStatus.errorMessage ?: "Tap to retry Firebase backup"
                com.example.utils.BackupState.SUCCESS -> {
                    val timePart = firebaseBackupStatus.lastBackupFormatted ?: "Ready"
                    "$timePart • ${firebaseBackupStatus.snapshotCount} snapshot(s)"
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (firebaseBackupStatus.state == com.example.utils.BackupState.NOT_CONFIGURED) {
                            onOpenFirebaseSettings()
                        } else {
                            onSyncFirebaseNow()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (firebaseBackupStatus.state == com.example.utils.BackupState.IN_PROGRESS) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = fbColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = "Firebase Backup Status",
                        tint = fbColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.firebaseBackupTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = fbSubtitle,
                        fontSize = 9.5.sp,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = fbColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = firebaseBadgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = fbColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}


