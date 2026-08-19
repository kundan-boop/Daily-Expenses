package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MonthlyExpenseEntity
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyExpenseScreen(
    viewModel: DailyExpenseViewModel
) {
    val allExpenses by viewModel.allExpenses.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<MonthlyExpenseEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<MonthlyExpenseEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var selectedMonth by remember { mutableStateOf("2026-07") } // YYYY-MM
    var showMonthYearPickerDialog by remember { mutableStateOf(false) }

    val currentMonthExpenses = allExpenses.filter { it.monthYear == selectedMonth }
    val totalMonthExpense = currentMonthExpenses.sumOf { it.amount }

    val expenseListState = rememberLazyListState()
    val topExpenseId = remember(currentMonthExpenses) { currentMonthExpenses.firstOrNull()?.id }
    LaunchedEffect(topExpenseId, currentMonthExpenses.size) {
        if (currentMonthExpenses.isNotEmpty()) {
            expenseListState.animateScrollToItem(0)
        }
    }

    // Helper to format YYYY-MM to readable "July 2026"
    fun formatMonthYear(yearMonth: String): String {
        return try {
            val sdfInput = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val sdfOutput = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = sdfInput.parse(yearMonth)
            if (date != null) sdfOutput.format(date) else yearMonth
        } catch (e: Exception) {
            yearMonth
        }
    }

    // Helper to add/subtract months
    fun changeMonth(current: String, delta: Int): String {
        return try {
            val sdfInput = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = sdfInput.parse(current) ?: Date()
            cal.add(Calendar.MONTH, delta)
            sdfInput.format(cal.time)
        } catch (e: Exception) {
            current
        }
    }

    // Predefined list of recent months for dropdown
    val monthList = remember {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JULY, 1) // Start at current app date July 2026
        val list = mutableListOf<String>()
        for (i in 0..24) {
            list.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        list
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    expenseToEdit = null
                    showAddDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Expense", tint = Color.White) },
                text = { Text("Add Expense", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = GreenPrimary,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_expense_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceBackground)
        ) {
            // Header Card
            Card(
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = GreenPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Monthly Store & Personal Deductions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monthly Expenses",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        // Interactive Month Navigator Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { selectedMonth = changeMonth(selectedMonth, -1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = Color.White)
                            }

                            Box {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showMonthYearPickerDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = formatMonthYear(selectedMonth),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { selectedMonth = changeMonth(selectedMonth, 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOTAL DEDUCTIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500, letterSpacing = 0.5.sp)
                                Text(
                                    text = String.format("₹ %,.2f", totalMonthExpense),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RedDebit
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = RedLight
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = RedDebit,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Expense Records (${formatMonthYear(selectedMonth)})",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Slate900,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (currentMonthExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No expenses logged for ${formatMonthYear(selectedMonth)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ Add Expense' to record fuel, rent, bills, or salary",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = expenseListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentMonthExpenses, key = { it.id }) { expense ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(RedLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        when (expense.category.lowercase()) {
                                            "fuel" -> Icons.Default.LocalGasStation
                                            "rent", "shop rent" -> Icons.Default.Store
                                            "salary", "staff" -> Icons.Default.Badge
                                            "bills", "utilities" -> Icons.Default.Receipt
                                            "food", "tea" -> Icons.Default.Restaurant
                                            else -> Icons.Default.Payments
                                        },
                                        contentDescription = null,
                                        tint = RedDebit,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = expense.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Slate900
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (expense.notes.isNotBlank()) "${expense.category} • ${expense.notes}" else expense.category,
                                        fontSize = 12.sp,
                                        color = Slate500
                                    )
                                    if (expense.attachmentPath != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.AttachFile,
                                                contentDescription = null,
                                                tint = GreenPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                "Bill Attached",
                                                fontSize = 11.sp,
                                                color = GreenPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("₹ %,.0f", expense.amount),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = RedDebit
                                    )

                                    Row {
                                        IconButton(
                                            onClick = {
                                                expenseToEdit = expense
                                                showAddDialog = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = SecondaryBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                expenseToDelete = expense
                                                showDeleteConfirmDialog = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Slate400,
                                                modifier = Modifier.size(18.dp)
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
    }

    if (showAddDialog) {
        val dialogContext = LocalContext.current
        val currentEdit = expenseToEdit
        var title by remember(currentEdit) { mutableStateOf(currentEdit?.title ?: "") }
        var amountText by remember(currentEdit) {
            mutableStateOf(currentEdit?.let {
                if (it.amount % 1.0 == 0.0) it.amount.toLong().toString() else it.amount.toString()
            } ?: "")
        }
        var category by remember(currentEdit) { mutableStateOf(currentEdit?.category ?: "") }
        var notes by remember(currentEdit) { mutableStateOf(currentEdit?.notes ?: "") }
        var attachmentUri by remember(currentEdit) { mutableStateOf<String?>(currentEdit?.attachmentPath) }

        var attemptedSave by remember { mutableStateOf(false) }

        val isTitleError = attemptedSave && title.isBlank()
        val isAmountError = attemptedSave && (amountText.toDoubleOrNull() == null || amountText.toDoubleOrNull()!! <= 0)

        // Photo Picker Launcher
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { attachmentUri = it.toString() }
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                expenseToEdit = null
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
            title = {
                Text(
                    text = if (currentEdit == null) "Add Monthly Expense" else "Edit Expense Entry",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Title Field
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                        },
                        label = { Text("Expense Title *", color = if (isTitleError) RedDebit else Slate700) },
                        placeholder = { Text("e.g. Shop Rent, Fuel, Tea", color = Slate400) },
                        isError = isTitleError,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Slate400,
                            errorBorderColor = RedDebit,
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = SurfaceBackground,
                            unfocusedContainerColor = SurfaceBackground
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Amount Field
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                        },
                        label = { Text("Amount (₹) *", color = if (isAmountError) RedDebit else Slate700) },
                        placeholder = { Text("0", color = Slate400) },
                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = RedDebit) },
                        isError = isAmountError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(color = RedDebit, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedDebit,
                            unfocusedBorderColor = Slate400,
                            errorBorderColor = RedDebit,
                            focusedTextColor = RedDebit,
                            unfocusedTextColor = RedDebit,
                            focusedContainerColor = SurfaceBackground,
                            unfocusedContainerColor = SurfaceBackground
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Category Input Field (Plain Text Input - No Dropdown)
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (Optional)", color = Slate700) },
                        placeholder = { Text("e.g. Fuel, Rent, Utility", color = Slate400) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Slate400,
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = SurfaceBackground,
                            unfocusedContainerColor = SurfaceBackground
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Notes Field
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes / Remarks (Optional)", color = Slate700) },
                        placeholder = { Text("e.g. Paid via PhonePe", color = Slate400) },
                        textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Slate400,
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = SurfaceBackground,
                            unfocusedContainerColor = SurfaceBackground
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Bill Attachment Button / Banner
                    if (attachmentUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GreenLight, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = GreenPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bill / Receipt Attached", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            }
                            TextButton(onClick = { attachmentUri = null }) {
                                Text("Remove", color = RedDebit, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceBackground,
                                contentColor = SecondaryBlue
                            )
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = SecondaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Attach Bill / Receipt Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        attemptedSave = true
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank() && amount > 0) {
                            val savedAttachmentPath = if (attachmentUri?.startsWith("content://") == true) {
                                try {
                                    val uri = android.net.Uri.parse(attachmentUri)
                                    val bytes = dialogContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        val dir = java.io.File(dialogContext.filesDir, "attachments")
                                        if (!dir.exists()) dir.mkdirs()
                                        val destFile = java.io.File(dir, "expense_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.jpg")
                                        destFile.writeBytes(bytes)
                                        destFile.absolutePath
                                    } else attachmentUri
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    attachmentUri
                                }
                            } else attachmentUri

                            if (currentEdit == null) {
                                viewModel.addExpense(
                                    monthYear = selectedMonth,
                                    title = title,
                                    category = if (category.isBlank()) "General" else category,
                                    amount = amount,
                                    notes = notes,
                                    attachmentPath = savedAttachmentPath
                                )
                            } else {
                                viewModel.updateExpense(
                                    currentEdit.copy(
                                        title = title.trim(),
                                        category = if (category.isBlank()) "General" else category.trim(),
                                        amount = amount,
                                        notes = notes.trim(),
                                        attachmentPath = savedAttachmentPath
                                    )
                                )
                            }
                            showAddDialog = false
                            expenseToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text(if (currentEdit == null) "Save Expense" else "Update Expense", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    expenseToEdit = null
                }) {
                    Text("Cancel", color = Slate500)
                }
            }
        )
    }

    // Validation Delete Confirmation Popup
    if (showDeleteConfirmDialog && expenseToDelete != null) {
        val target = expenseToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                expenseToDelete = null
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RedDebit)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Expense Deletion", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this expense?\n\n'${target.title}' (₹ ${String.format("%,.0f", target.amount)})",
                    fontSize = 14.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(target)
                        showDeleteConfirmDialog = false
                        expenseToDelete = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedDebit)
                ) {
                    Text("Delete Expense", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        expenseToDelete = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                ) {
                    Text("Cancel", color = Slate700, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Comprehensive Year & Month Picker Dialog (supporting selecting 60+ years before, e.g. 1960 to 2035)
    if (showMonthYearPickerDialog) {
        val currentYearStr = selectedMonth.split("-").getOrElse(0) { "2026" }
        val currentMonthStr = selectedMonth.split("-").getOrElse(1) { "07" }

        var pickerYear by remember { mutableStateOf(currentYearStr.toIntOrNull() ?: 2026) }
        var pickerMonthIndex by remember { mutableStateOf((currentMonthStr.toIntOrNull() ?: 7) - 1) }

        val monthsNames = remember {
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        }

        AlertDialog(
            onDismissRequest = { showMonthYearPickerDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
            title = {
                Text("Select Expense Period", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Year Selector Bar with quick jump buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceBackground, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { pickerYear -= 1 }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Year", tint = GreenPrimary)
                        }

                        Text(
                            text = pickerYear.toString(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenPrimary
                        )

                        IconButton(onClick = { pickerYear += 1 }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Year", tint = GreenPrimary)
                        }
                    }

                    // Quick Jump Year Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(2026, 2025, 2020, 2010, 2000, 1990, 1980, 1970).take(4).forEach { y ->
                            FilterChip(
                                selected = pickerYear == y,
                                onClick = { pickerYear = y },
                                label = { Text(y.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GreenPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // 12-Month Selector Grid
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0..3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0..2) {
                                    val mIdx = row * 3 + col
                                    val isSel = mIdx == pickerMonthIndex
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) GreenPrimary else SurfaceBackground,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { pickerMonthIndex = mIdx }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = monthsNames[mIdx],
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isSel) Color.White else Slate900
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
                        val formattedMonth = String.format("%02d", pickerMonthIndex + 1)
                        selectedMonth = "$pickerYear-$formattedMonth"
                        showMonthYearPickerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Apply Period", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMonthYearPickerDialog = false }) {
                    Text("Cancel", color = Slate500)
                }
            }
        )
    }
}
