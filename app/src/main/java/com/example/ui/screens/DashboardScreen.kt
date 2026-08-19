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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.data.CustomerEntity
import com.example.ui.CustomerBalanceSummary
import com.example.ui.CustomerFilter
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DailyExpenseViewModel,
    authViewModel: com.example.ui.AuthViewModel? = null,
    onCustomerClick: (CustomerEntity) -> Unit,
    onAddCustomerClick: () -> Unit,
    onEditCustomerClick: (CustomerEntity) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val dashboardSummary by viewModel.dashboardSummary.collectAsState()
    val filteredSummaries by viewModel.filteredCustomerSummaries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pagedCustomersFlow = remember(searchQuery) { viewModel.getCustomersPagedFlow(searchQuery) }
    val pagedCustomers = pagedCustomersFlow.collectAsLazyPagingItems()
    val userProfile by authViewModel?.userProfile?.collectAsState() ?: remember { mutableStateOf(null) }

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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCustomerClick,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Customer") },
                text = { Text("ADD CUSTOMER", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                containerColor = GreenPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_customer_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // Editorial Header Bar - Clickable D or title goes to Settings
            Surface(
                color = GreenPrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                .background(Color.White),
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
                                    color = GreenPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = userProfile?.name ?: "Daily Expense & Ledger",
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
                                    text = "LEDGER DASHBOARD",
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
                                            text = "$formattedCustomerCount Customers",
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
                        .background(Color.White)
                ) {
                    // Full Width Unified 4-Column Stat Banner
                    Surface(
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            HorizontalDivider(color = Slate200, thickness = 1.dp)

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
                                        text = "NET BALANCE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate500,
                                        letterSpacing = 0.3.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = netText,
                                        fontSize = getFontSizeForText(netText),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = if (overallNetBalance >= 0) GreenPrimary else RedDebit
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(28.dp),
                                    color = Slate200
                                )

                                // Col 2: You'll Get
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "YOU'LL GET",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate500,
                                        letterSpacing = 0.3.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = get,
                                        fontSize = getFontSizeForText(get),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = GreenPrimary
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(28.dp),
                                    color = Slate200
                                )

                                // Col 3: You Gave
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "YOU GAVE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate500,
                                        letterSpacing = 0.3.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = give,
                                        fontSize = getFontSizeForText(give),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = RedDebit
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(28.dp),
                                    color = Slate200
                                )

                                // Col 4: Expenses
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "EXPENSES",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate500,
                                        letterSpacing = 0.3.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = exp,
                                        fontSize = getFontSizeForText(exp),
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = RedDebit
                                    )
                                }
                            }

                            HorizontalDivider(color = Slate200, thickness = 1.dp)
                        }
                    }

                    // Search Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search name, phone, comments, amount...", fontSize = 13.sp, color = Slate500) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GreenPrimary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate700)
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Slate400,
                                focusedBorderColor = GreenPrimary,
                                cursorColor = GreenPrimary,
                                focusedTextColor = Slate900,
                                unfocusedTextColor = Slate900,
                                focusedPlaceholderColor = Slate500,
                                unfocusedPlaceholderColor = Slate500
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
                    .background(Color.White)
                    .padding(horizontal = 16.dp)
            ) {

                // Customer List with Pull-to-Refresh
                val isRefreshing by viewModel.isRefreshing.collectAsState()
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
                            containerColor = Color.White,
                            color = com.example.ui.theme.GreenPrimary
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
                                    tint = Slate400,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) "No customers found" else "No matching customer found",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate500
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap '+ ADD CUSTOMER' to create your first customer entry.",
                                    fontSize = 13.sp,
                                    color = Slate400
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
                                            color = Slate200
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
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp),
            title = { Text("Delete Customer?", fontWeight = FontWeight.ExtraBold, color = Slate900) },
            text = {
                Text(
                    text = "Are you sure you want to delete ${targetCust.name}? All transaction history for this customer will be removed.",
                    color = Slate700,
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
                    colors = ButtonDefaults.buttonColors(containerColor = RedDebit)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { customerToDelete = null },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                ) {
                    Text("Cancel", color = Slate700, fontWeight = FontWeight.SemiBold)
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
    val customer = summary.customer
    val netBalance = summary.netBalance
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val avatarColors = remember(customer.id) {
        val colorPairs = listOf(
            Color(0xFFFFE0B2) to Color(0xFFE65100), // Orange
            Color(0xFFE3F2FD) to Color(0xFF1565C0), // Blue
            Color(0xFFF3E5F5) to Color(0xFF7B1FA2), // Purple
            Color(0xFFE8F5E9) to Color(0xFF2E7D32)  // Green
        )
        colorPairs[(customer.id % colorPairs.size).toInt()]
    }

    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("customer_item_${customer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Customer Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarColors.first),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = customer.name.take(1).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = avatarColors.second
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (summary.lastTransactionDate != null) "Last entry ${sdf.format(Date(summary.lastTransactionDate))}" else if (customer.mobileNumber.isNotBlank()) customer.mobileNumber else "No phone",
                        fontSize = 12.sp,
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
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

                        val badgeBgColor = if (isPastOrToday) RedLight else GreenLight
                        val badgeTextColor = if (isPastOrToday) RedDebit else GreenPrimary

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
                                "COLLECT TODAY"
                            }
                            Text(
                                text = badgeText,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor,
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RedDebit
                    )
                    Text("Expense", fontSize = 10.sp, color = RedDebit, fontWeight = FontWeight.Bold)
                } else {
                    when {
                        netBalance > 0.01 -> {
                            Text(
                                text = String.format("₹ %,.0f", netBalance),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GreenPrimary
                            )
                            Text("You'll Get", fontSize = 10.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                        }
                        netBalance < -0.01 -> {
                            Text(
                                text = String.format("₹ %,.0f", Math.abs(netBalance)),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = RedDebit
                            )
                            Text("You'll Give", fontSize = 10.sp, color = RedDebit, fontWeight = FontWeight.Bold)
                        }
                        else -> {
                            Text(
                                text = "₹ 0",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate400
                            )
                            Text("Settled", fontSize = 10.sp, color = Slate400)
                        }
                    }
                }
            }
        }
    }
}

