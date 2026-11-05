package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.data.CustomerEntity
import com.example.data.TransactionEntity
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomerDetailScreen(
    customer: CustomerEntity,
    viewModel: DailyExpenseViewModel,
    onBackClick: () -> Unit,
    onAddEntryClick: (type: String) -> Unit, // "I_PAID" or "I_RECEIVED"
    onEditEntryClick: (TransactionEntity) -> Unit,
    onEditCustomerClick: () -> Unit
) {
    val context = LocalContext.current
    val transactionsFlow = remember(customer.id) { viewModel.getTransactionsForCustomer(customer.id) }
    val transactions by transactionsFlow.collectAsState(initial = emptyList())

    val pagedTransactionsFlow = remember(customer.id) { viewModel.getTransactionsForCustomerPagedFlow(customer.id) }
    val pagedTransactions = pagedTransactionsFlow.collectAsLazyPagingItems()

    // Independent Transaction Search State & Debounced Results from ViewModel
    val searchQuery by viewModel.transactionSearchQuery.collectAsState()
    val filteredTransactions by viewModel.filteredCustomerTransactions.collectAsState()
    var isSearchVisible by remember { mutableStateOf(false) }

    // Set active customer in ViewModel for transaction search and clear on exit
    DisposableEffect(customer.id) {
        viewModel.setActiveCustomerDetailId(customer.id)
        onDispose {
            viewModel.clearTransactionSearchQuery()
            viewModel.setActiveCustomerDetailId(null)
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 1. Keyboard-first Back behavior: closes only the keyboard & clears focus if keyboard is open
    BackHandler(enabled = isImeVisible) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    // 2. Next Back clears search text and closes search bar if open
    BackHandler(enabled = !isImeVisible && (isSearchVisible || searchQuery.isNotEmpty())) {
        isSearchVisible = false
        viewModel.clearTransactionSearchQuery()
    }

    // 3. Next Back navigates back to Customer List if search is closed
    BackHandler(enabled = !isImeVisible && !isSearchVisible && searchQuery.isEmpty()) {
        onBackClick()
    }

    val totalPaid = transactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
    val totalReceived = transactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
    val netBalance = totalPaid - totalReceived // Positive = YOU GET, Negative = YOU GIVE

    // Cumulative balance calculated chronologically (oldest to newest: dateTimestamp ASC, id ASC)
    val runningTotalsByTxId by remember(transactions) {
        derivedStateOf {
            val sorted = transactions.sortedWith(
                compareBy<TransactionEntity> { it.dateTimestamp }.thenBy { it.id }
            )
            val map = HashMap<Long, Double>(sorted.size)
            var currentBalance = 0.0
            for (tx in sorted) {
                if (tx.transactionType == "I_PAID") {
                    currentBalance += tx.amount
                } else {
                    currentBalance -= tx.amount
                }
                map[tx.id] = currentBalance
            }
            map
        }
    }

    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    var showDeleteCustomerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedTxForDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var viewingImageUri by remember { mutableStateOf<String?>(null) }
    var showPdfExportSheet by remember { mutableStateOf(false) }

    val txListState = rememberLazyListState()
    val searchTxListState = rememberLazyListState()
    val topTxId = remember(transactions) { transactions.firstOrNull()?.id }
    LaunchedEffect(topTxId, transactions.size) {
        if (transactions.isNotEmpty()) {
            txListState.animateScrollToItem(0)
            searchTxListState.animateScrollToItem(0)
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
                    // Scrolling top -> down: only customer name bar stays sticky at top
                    isHeaderExpanded = false
                } else if (delta > 8f) {
                    // Scrolling bottom -> top: customer name bar, balance card, and search become sticky together
                    isHeaderExpanded = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    val activeListState = if (searchQuery.isNotBlank()) searchTxListState else txListState
    val firstVisibleItemIndex by remember { derivedStateOf { activeListState.firstVisibleItemIndex } }
    val firstVisibleItemScrollOffset by remember { derivedStateOf { activeListState.firstVisibleItemScrollOffset } }

    LaunchedEffect(firstVisibleItemIndex, firstVisibleItemScrollOffset) {
        if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= 5) {
            isHeaderExpanded = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    // Top row: Back button, Customer Name (auto-adjusts height/lines), and Action Icons (Call, Edit, Delete)
                    val isDeduction = customer.name.contains("Deduction", ignoreCase = true)
                    val hasSubDetails = customer.mobileNumber.isNotBlank() || isDeduction || customer.collectToday || customer.collectDueDate != null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(start = 2.dp, end = 6.dp, top = 2.dp, bottom = if (hasSubDetails) 2.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Slate900
                            )
                        }

                        Text(
                            text = customer.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Slate900,
                            lineHeight = 22.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .wrapContentHeight()
                        )

                        // Right-side Action Icons (Call, Edit, Delete)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.wrapContentHeight()
                        ) {
                            if (customer.mobileNumber.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.mobileNumber}"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call Customer", tint = OceanBlue, modifier = Modifier.size(20.dp))
                                }
                            }

                            IconButton(
                                onClick = onEditCustomerClick,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = GreenPrimary, modifier = Modifier.size(20.dp))
                            }

                            IconButton(
                                onClick = { showDeleteCustomerDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Customer", tint = RedDebit, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Second row (if present): Left-aligned Phone Number & Badges directly under name
                    if (hasSubDetails) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 52.dp, end = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (customer.mobileNumber.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Phone Number",
                                        tint = Slate500,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = customer.mobileNumber,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate500
                                    )
                                }
                            }

                            if (isDeduction) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = RedLight
                                ) {
                                    Text(
                                        text = "EXPENSE ACCOUNT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RedDebit,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

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
                                    val dateStr = if (customer.collectDueDate != null) {
                                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply {
                                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        }
                                        "DUE: " + sdf.format(Date(customer.collectDueDate!!)).uppercase()
                                    } else {
                                        "COLLECT TODAY"
                                    }
                                    Text(
                                        text = dateStr,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTextColor,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // "I PAID" and "I RECEIVED" Fixed Action Bar
            Surface(
                shadowElevation = 12.dp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .navigationBarsPadding()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    // I PAID (You Gave Money) Button
                    Button(
                        onClick = { onAddEntryClick("I_PAID") },
                        colors = ButtonDefaults.buttonColors(containerColor = RedDebit),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("i_paid_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "I PAID (GAVE ₹)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // I RECEIVED (Customer Paid You) Button
                    Button(
                        onClick = { onAddEntryClick("I_RECEIVED") },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("i_received_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "I RECEIVED (GOT ₹)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SurfaceBackground)
        ) {
            val isDeductionCustomer = customer.name.contains("Deduction", ignoreCase = true)

            // Collapsible Header Details (Balance Card, Transactions Header & Search Input)
            AnimatedVisibility(
                visible = isHeaderExpanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Compact Balance Header Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isDeductionCustomer -> RedLight
                                netBalance > 0.01 -> GreenLight
                                netBalance < -0.01 -> RedLight
                                else -> Slate100
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isDeductionCustomer) "TOTAL EXPENSE" else "NET CURRENT BALANCE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate500,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = String.format("₹ %,.2f", Math.abs(netBalance)),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = when {
                                            isDeductionCustomer -> RedDebit
                                            netBalance > 0.01 -> GreenPrimary
                                            netBalance < -0.01 -> RedDebit
                                            else -> Slate700
                                        }
                                    )
                                    Text(
                                        text = when {
                                            isDeductionCustomer -> "EXPENSE ACCOUNT"
                                            netBalance > 0.01 -> "YOU GET (CUSTOMER OWES)"
                                            netBalance < -0.01 -> "YOU GIVE (YOU OWE)"
                                            else -> "SETTLED (NO BALANCE)"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isDeductionCustomer -> RedDebit
                                            netBalance > 0.01 -> GreenPrimary
                                            netBalance < -0.01 -> RedDebit
                                            else -> Slate500
                                        }
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            showPdfExportSheet = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(12.dp), tint = SecondaryBlue)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("PDF Report", fontSize = 10.sp, color = SecondaryBlue, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            val message = viewModel.getWhatsAppReminderText(customer.name, netBalance)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, message)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Send Payment Reminder"))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reminder", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Paid: ₹${String.format("%,.0f", totalPaid)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDebit
                                )
                                Text(
                                    text = "Received: ₹${String.format("%,.0f", totalReceived)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transaction History",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )

                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    viewModel.clearTransactionSearchQuery()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Transactions",
                                tint = if (isSearchVisible || searchQuery.isNotEmpty()) SecondaryBlue else Slate700,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isSearchVisible) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setTransactionSearchQuery(it) },
                            placeholder = { Text("Search transactions", fontSize = 12.sp, color = Slate400) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearTransactionSearchQuery() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Slate400, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Slate900,
                                unfocusedTextColor = Slate900,
                                focusedBorderColor = SecondaryBlue,
                                unfocusedBorderColor = Slate200,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (pagedTransactions.itemCount == 0 && transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No transaction entries yet", color = Slate500)
                        Text("Tap 'I PAID' or 'I RECEIVED' to record an entry", fontSize = 12.sp, color = Slate400)
                    }
                }
            } else if (searchQuery.isNotBlank() && filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No transactions found", fontWeight = FontWeight.SemiBold, color = Slate700)
                        Text("No entry found for '$searchQuery'", fontSize = 12.sp, color = Slate500)
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                LazyColumn(
                    state = searchTxListState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection)
                ) {
                    items(
                        items = filteredTransactions,
                        key = { it.id }
                    ) { tx ->
                        TransactionItemCard(
                            transaction = tx,
                            runningTotal = runningTotalsByTxId[tx.id],
                            sdf = sdf,
                            onEdit = { onEditEntryClick(tx) },
                            onDelete = {
                                selectedTxForDelete = tx
                                showDeleteConfirmDialog = true
                            },
                            onImageClick = { viewingImageUri = it }
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = txListState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection)
                ) {
                    items(
                        count = pagedTransactions.itemCount,
                        key = pagedTransactions.itemKey { it.id }
                    ) { index ->
                        val tx = pagedTransactions[index]
                        if (tx != null) {
                            TransactionItemCard(
                                transaction = tx,
                                runningTotal = runningTotalsByTxId[tx.id],
                                sdf = sdf,
                                onEdit = { onEditEntryClick(tx) },
                                onDelete = {
                                    selectedTxForDelete = tx
                                    showDeleteConfirmDialog = true
                                },
                                onImageClick = { viewingImageUri = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // Image Viewer Modal Dialog
    if (viewingImageUri != null) {
        AlertDialog(
            onDismissRequest = { viewingImageUri = null },
            containerColor = Color.Black,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize(),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Attachment Photo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { viewingImageUri = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = viewingImageUri,
                        contentDescription = "Transaction Receipt",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (showDeleteCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCustomerDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp),
            title = { Text("Delete Customer?", fontWeight = FontWeight.Bold, color = Slate900) },
            text = { Text("Are you sure you want to delete ${customer.name}? All transaction history for this customer will be deleted.", color = Slate700) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer)
                        showDeleteCustomerDialog = false
                        onBackClick()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedDebit)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteCustomerDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                ) {
                    Text("Cancel", color = Slate700, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showDeleteConfirmDialog && selectedTxForDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 16.dp),
            title = { Text("Delete Entry?", fontWeight = FontWeight.Bold, color = Slate900) },
            text = { Text("Are you sure you want to delete this transaction entry of ₹${selectedTxForDelete?.amount}?", color = Slate700) },
            confirmButton = {
                Button(
                    onClick = {
                        selectedTxForDelete?.let { viewModel.deleteTransaction(it) }
                        showDeleteConfirmDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedDebit)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                ) {
                    Text("Cancel", color = Slate700, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showPdfExportSheet) {
        CustomerPdfExportSheet(
            customer = customer,
            transactions = transactions,
            onDismiss = { showPdfExportSheet = false }
        )
    }
}

@Composable
fun TransactionItemCard(
    transaction: TransactionEntity,
    runningTotal: Double? = null,
    sdf: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val isPaid = transaction.transactionType == "I_PAID"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isPaid) RedLight else GreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPaid) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (isPaid) RedDebit else GreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPaid) "I Paid (Gave)" else "I Received (Got)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaid) RedDebit else GreenPrimary
                    )

                    Text(
                        text = String.format("₹%,.2f", transaction.amount),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPaid) RedDebit else GreenPrimary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (transaction.comment.isNotEmpty()) {
                    Text(
                        text = transaction.comment,
                        fontSize = 12.sp,
                        color = Slate900,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${sdf.format(Date(transaction.dateTimestamp))} • ${transaction.paymentMode}",
                        fontSize = 10.sp,
                        color = Slate400
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Entry",
                                tint = OceanBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Entry",
                                tint = RedDebit,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Dedicated Row for attached photo pills (handles >2 images cleanly without distorting layout)
                if (!transaction.attachmentPath.isNullOrBlank()) {
                    val images = transaction.attachmentPath.split("|").filter { it.isNotBlank() }
                    if (images.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(images) { idx, imgUri ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GreenLight,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.3f)),
                                    modifier = Modifier.clickable { onImageClick(imgUri) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = "View Attachment",
                                            tint = GreenPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Photo ${idx + 1}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GreenPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Small, muted running total at bottom-right of each entry card
                if (runningTotal != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formattedAmount = if (Math.abs(runningTotal) % 1.0 == 0.0) {
                            String.format(Locale.US, "₹%,.0f", Math.abs(runningTotal))
                        } else {
                            String.format(Locale.US, "₹%,.2f", Math.abs(runningTotal))
                        }
                        val totalDisplay = when {
                            runningTotal > 0.009 -> "Total: $formattedAmount"
                            runningTotal < -0.009 -> "Total: -$formattedAmount"
                            else -> "Total: ₹0"
                        }

                        Text(
                            text = totalDisplay,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate500
                        )
                    }
                }
            }
        }
    }
}

enum class PdfDateFilterMode(val label: String) {
    ALL("All Dates"),
    SPECIFIC_MONTH("Month"),
    DATE_RANGE("Date Range"),
    SPECIFIC_YEAR("Year")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPdfExportSheet(
    customer: CustomerEntity,
    transactions: List<TransactionEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val calNow = remember { Calendar.getInstance() }

    var filterMode by remember { mutableStateOf(PdfDateFilterMode.ALL) }
    var selectedMonth by remember { mutableStateOf(calNow.get(Calendar.MONTH)) }
    var selectedMonthYear by remember { mutableStateOf(calNow.get(Calendar.YEAR)) }
    var selectedYearOnly by remember { mutableStateOf(calNow.get(Calendar.YEAR)) }

    var startDateMillis by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }

    var endDateMillis by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        )
    }

    var selectedPreset by remember { mutableStateOf("Today") }

    val monthNames = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    val shortMonthNames = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }
    val availableYears = remember {
        listOf(2022, 2023, 2024, 2025, 2026, 2027, 2028, 2029, 2030)
    }

    val sdfDay = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Start Date Picker Dialog
    val startDatePickerDialog = remember(startDateMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startDateMillis = newCal.timeInMillis
                if (endDateMillis < startDateMillis) {
                    endDateMillis = Calendar.getInstance().apply {
                        timeInMillis = startDateMillis
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                }
                selectedPreset = "Custom"
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // End Date Picker Dialog
    val endDatePickerDialog = remember(endDateMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = endDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                endDateMillis = newCal.timeInMillis
                if (startDateMillis > endDateMillis) {
                    startDateMillis = Calendar.getInstance().apply {
                        timeInMillis = endDateMillis
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                selectedPreset = "Custom"
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // Compute Filtered Transactions & Period Label
    val (filteredTransactions, periodLabel) = remember(
        filterMode,
        selectedMonth,
        selectedMonthYear,
        selectedYearOnly,
        startDateMillis,
        endDateMillis,
        transactions
    ) {
        when (filterMode) {
            PdfDateFilterMode.ALL -> {
                Pair(transactions, "All Available Dates")
            }
            PdfDateFilterMode.SPECIFIC_MONTH -> {
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedMonthYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedMonthYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val label = "${monthNames[selectedMonth]} $selectedMonthYear"
                val filtered = transactions.filter { it.dateTimestamp in startCal.timeInMillis..endCal.timeInMillis }
                Pair(filtered, label)
            }
            PdfDateFilterMode.SPECIFIC_YEAR -> {
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYearOnly)
                    set(Calendar.MONTH, 0)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYearOnly)
                    set(Calendar.MONTH, 11)
                    set(Calendar.DAY_OF_MONTH, 31)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val label = "Year $selectedYearOnly"
                val filtered = transactions.filter { it.dateTimestamp in startCal.timeInMillis..endCal.timeInMillis }
                Pair(filtered, label)
            }
            PdfDateFilterMode.DATE_RANGE -> {
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = startDateMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = endDateMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val isSameDay = sdfDay.format(startCal.time) == sdfDay.format(endCal.time)
                val label = if (isSameDay) sdfDay.format(startCal.time) else "${sdfDay.format(startCal.time)} – ${sdfDay.format(endCal.time)}"
                val filtered = transactions.filter { it.dateTimestamp in startCal.timeInMillis..endCal.timeInMillis }
                Pair(filtered, label)
            }
        }
    }

    val filteredReceived = filteredTransactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
    val filteredPaid = filteredTransactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
    val filteredBalance = filteredPaid - filteredReceived
    val isDeductionCustomer = customer.name.contains("Deduction", ignoreCase = true)

    fun exportPdf() {
        if (filteredTransactions.isEmpty()) {
            Toast.makeText(context, "No data available for the selected period", Toast.LENGTH_SHORT).show()
        }
        try {
            val pdfFile = com.example.utils.PdfReportExporter.generateCustomerLedgerPdf(
                context = context,
                customerName = customer.name,
                mobileNumber = if (customer.mobileNumber.isNotBlank()) customer.mobileNumber else null,
                transactions = filteredTransactions,
                periodLabel = periodLabel
            )
            val pdfUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra(Intent.EXTRA_SUBJECT, "Customer Ledger Report ($periodLabel) - ${customer.name}")
                putExtra(Intent.EXTRA_TEXT, "Daily Expense & Ledger – Customer Report for ${customer.name} ($periodLabel)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export PDF Report"))
            onDismiss()
        } catch (e: Exception) {
            Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Export Customer PDF Report",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900
                    )
                    Text(
                        text = "Choose date, month, year, or export all transactions.",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Slate500,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Mode Selector Chips
            Text(
                text = "FILTER BY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PdfDateFilterMode.values().forEach { mode ->
                    val isSelected = filterMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { filterMode = mode },
                        label = {
                            Text(
                                text = mode.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    PdfDateFilterMode.ALL -> Icons.Default.AllInclusive
                                    PdfDateFilterMode.SPECIFIC_MONTH -> Icons.Default.CalendarMonth
                                    PdfDateFilterMode.DATE_RANGE -> Icons.Default.DateRange
                                    PdfDateFilterMode.SPECIFIC_YEAR -> Icons.Default.CalendarToday
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryNavy,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = Slate50,
                            labelColor = Slate700,
                            iconColor = Slate500
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Slate200,
                            selectedBorderColor = PrimaryNavy
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Control Section based on Selected Filter Mode
            when (filterMode) {
                PdfDateFilterMode.ALL -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SecondaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exporting all ${transactions.size} available transactions recorded for ${customer.name}.",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                        }
                    }
                }

                PdfDateFilterMode.SPECIFIC_MONTH -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Month & Year Header with Navigation Arrows
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 0) {
                                            selectedMonth = 11
                                            selectedMonthYear--
                                        } else {
                                            selectedMonth--
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = Slate700)
                                }

                                Text(
                                    text = "${monthNames[selectedMonth]} $selectedMonthYear",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )

                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 11) {
                                            selectedMonth = 0
                                            selectedMonthYear++
                                        } else {
                                            selectedMonth++
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = Slate700)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Month Grid (12 chips)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (row in 0 until 3) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (col in 0 until 4) {
                                            val mIdx = row * 4 + col
                                            val isMonthSelected = selectedMonth == mIdx
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isMonthSelected) SecondaryBlue else Color.White,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isMonthSelected) SecondaryBlue else Slate200
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { selectedMonth = mIdx }
                                            ) {
                                                Text(
                                                    text = shortMonthNames[mIdx],
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isMonthSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isMonthSelected) Color.White else Slate700,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Year Row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(availableYears) { yr ->
                                    val isYrSelected = selectedMonthYear == yr
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isYrSelected) Slate900 else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isYrSelected) Slate900 else Slate200
                                        ),
                                        modifier = Modifier.clickable { selectedMonthYear = yr }
                                    ) {
                                        Text(
                                            text = yr.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = if (isYrSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isYrSelected) Color.White else Slate700,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PdfDateFilterMode.SPECIFIC_YEAR -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { selectedYearOnly-- },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year", tint = Slate700)
                                }

                                Text(
                                    text = "Year $selectedYearOnly",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )

                                IconButton(
                                    onClick = { selectedYearOnly++ },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Year", tint = Slate700)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Year Selection Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(2023, 2024, 2025, 2026, 2027).forEach { yr ->
                                    val isYrSelected = selectedYearOnly == yr
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isYrSelected) SecondaryBlue else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isYrSelected) SecondaryBlue else Slate200
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedYearOnly = yr }
                                    ) {
                                        Text(
                                            text = yr.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = if (isYrSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isYrSelected) Color.White else Slate700,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PdfDateFilterMode.DATE_RANGE -> {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Quick Preset Chips
                            Text(
                                text = "QUICK PRESETS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate400,
                                letterSpacing = 0.5.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val presets = listOf("Today", "Yesterday", "Last 7 Days", "Last 30 Days", "This Month")
                                items(presets) { preset ->
                                    val isSelected = selectedPreset == preset
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) SecondaryBlue else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) SecondaryBlue else Slate200
                                        ),
                                        modifier = Modifier.clickable {
                                            selectedPreset = preset
                                            when (preset) {
                                                "Today" -> {
                                                    val start = Calendar.getInstance().apply {
                                                        set(Calendar.HOUR_OF_DAY, 0)
                                                        set(Calendar.MINUTE, 0)
                                                        set(Calendar.SECOND, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }
                                                    val end = Calendar.getInstance().apply {
                                                        set(Calendar.HOUR_OF_DAY, 23)
                                                        set(Calendar.MINUTE, 59)
                                                        set(Calendar.SECOND, 59)
                                                        set(Calendar.MILLISECOND, 999)
                                                    }
                                                    startDateMillis = start.timeInMillis
                                                    endDateMillis = end.timeInMillis
                                                }
                                                "Yesterday" -> {
                                                    val start = Calendar.getInstance().apply {
                                                        add(Calendar.DAY_OF_YEAR, -1)
                                                        set(Calendar.HOUR_OF_DAY, 0)
                                                        set(Calendar.MINUTE, 0)
                                                        set(Calendar.SECOND, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }
                                                    val end = Calendar.getInstance().apply {
                                                        add(Calendar.DAY_OF_YEAR, -1)
                                                        set(Calendar.HOUR_OF_DAY, 23)
                                                        set(Calendar.MINUTE, 59)
                                                        set(Calendar.SECOND, 59)
                                                        set(Calendar.MILLISECOND, 999)
                                                    }
                                                    startDateMillis = start.timeInMillis
                                                    endDateMillis = end.timeInMillis
                                                }
                                                "Last 7 Days" -> {
                                                    val start = Calendar.getInstance().apply {
                                                        add(Calendar.DAY_OF_YEAR, -6)
                                                        set(Calendar.HOUR_OF_DAY, 0)
                                                        set(Calendar.MINUTE, 0)
                                                        set(Calendar.SECOND, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }
                                                    val end = Calendar.getInstance().apply {
                                                        set(Calendar.HOUR_OF_DAY, 23)
                                                        set(Calendar.MINUTE, 59)
                                                        set(Calendar.SECOND, 59)
                                                        set(Calendar.MILLISECOND, 999)
                                                    }
                                                    startDateMillis = start.timeInMillis
                                                    endDateMillis = end.timeInMillis
                                                }
                                                "Last 30 Days" -> {
                                                    val start = Calendar.getInstance().apply {
                                                        add(Calendar.DAY_OF_YEAR, -29)
                                                        set(Calendar.HOUR_OF_DAY, 0)
                                                        set(Calendar.MINUTE, 0)
                                                        set(Calendar.SECOND, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }
                                                    val end = Calendar.getInstance().apply {
                                                        set(Calendar.HOUR_OF_DAY, 23)
                                                        set(Calendar.MINUTE, 59)
                                                        set(Calendar.SECOND, 59)
                                                        set(Calendar.MILLISECOND, 999)
                                                    }
                                                    startDateMillis = start.timeInMillis
                                                    endDateMillis = end.timeInMillis
                                                }
                                                "This Month" -> {
                                                    val start = Calendar.getInstance().apply {
                                                        set(Calendar.DAY_OF_MONTH, 1)
                                                        set(Calendar.HOUR_OF_DAY, 0)
                                                        set(Calendar.MINUTE, 0)
                                                        set(Calendar.SECOND, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }
                                                    val end = Calendar.getInstance().apply {
                                                        set(Calendar.HOUR_OF_DAY, 23)
                                                        set(Calendar.MINUTE, 59)
                                                        set(Calendar.SECOND, 59)
                                                        set(Calendar.MILLISECOND, 999)
                                                    }
                                                    startDateMillis = start.timeInMillis
                                                    endDateMillis = end.timeInMillis
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Slate700,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Start & End Date Pickers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // From Date
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { startDatePickerDialog.show() }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("From Date", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(13.dp), tint = SecondaryBlue)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = sdfDay.format(Date(startDateMillis)),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                        }
                                    }
                                }

                                // To Date
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { endDatePickerDialog.show() }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("To Date", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(13.dp), tint = SecondaryBlue)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = sdfDay.format(Date(endDateMillis)),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Summary Snapshot Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (filteredTransactions.isEmpty()) Color(0xFFFFFBEB) else Slate50
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (filteredTransactions.isEmpty()) Color(0xFFFDE68A) else Slate200
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Report Period: $periodLabel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (filteredTransactions.isEmpty()) Color(0xFFFEF3C7) else Slate200
                        ) {
                            Text(
                                text = "${filteredTransactions.size} entries",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (filteredTransactions.isEmpty()) Color(0xFFB45309) else Slate700,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (filteredTransactions.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "No data available for the selected period",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB45309)
                            )
                        }
                        Text(
                            text = "No records exist for $periodLabel. Exporting will generate a clean ledger with a clear \"No data available\" message.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Received", fontSize = 10.sp, color = Slate500)
                                Text(
                                    String.format("₹ %,.2f", filteredReceived),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                            }

                            Column {
                                Text("Total Paid", fontSize = 10.sp, color = Slate500)
                                Text(
                                    String.format("₹ %,.2f", filteredPaid),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDebit
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (isDeductionCustomer) "Total Expense" else "Net Balance",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                                Text(
                                    String.format("₹ %,.2f", Math.abs(filteredBalance)),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDeductionCustomer) RedDebit else if (filteredBalance >= 0) GreenPrimary else RedDebit
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("Cancel", color = Slate700, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { exportPdf() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                    modifier = Modifier
                        .weight(2f)
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (filteredTransactions.isEmpty()) "Export PDF (0 entries)" else "Export PDF (${filteredTransactions.size})",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

