package com.example.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.data.TransactionEntity
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    customerId: Long,
    initialType: String = "I_PAID", // "I_PAID" or "I_RECEIVED"
    transactionToEdit: TransactionEntity? = null,
    viewModel: DailyExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current

    var transactionType by remember(transactionToEdit) {
        mutableStateOf(transactionToEdit?.transactionType ?: initialType)
    }
    var amountText by remember(transactionToEdit) {
        mutableStateOf(
            transactionToEdit?.let {
                if (it.amount % 1.0 == 0.0) it.amount.toLong().toString() else it.amount.toString()
            } ?: ""
        )
    }
    var commentText by remember(transactionToEdit) {
        mutableStateOf(transactionToEdit?.comment ?: "")
    }
    var paymentMode by remember(transactionToEdit) {
        mutableStateOf(transactionToEdit?.paymentMode ?: "None")
    }
    var selectedCategory by remember(transactionToEdit) {
        mutableStateOf(transactionToEdit?.category ?: "General")
    }
    var selectedDateMillis by remember(transactionToEdit) {
        mutableLongStateOf(transactionToEdit?.dateTimestamp ?: System.currentTimeMillis())
    }

    // Multiple Attachment URIs handled as list, stored as pipe-separated string
    var attachmentUris by remember(transactionToEdit) {
        mutableStateOf<List<String>>(
            transactionToEdit?.attachmentPath?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }

    var attemptedSave by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val isAmountError = attemptedSave && (amountText.toDoubleOrNull() == null || amountText.toDoubleOrNull()!! <= 0)
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Multiple Gallery Photo Picker Launcher
    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newUris = uris.map { it.toString() }
            attachmentUris = (attachmentUris + newUris).distinct()
        }
    }

    // Date Picker Dialog
    val datePickerDialog = remember(selectedDateMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateMillis = updated.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    var isSheetImeVisible by remember { mutableStateOf(false) }
    var lastSheetImeCloseTime by remember { mutableLongStateOf(0L) }
    var currentDialogView by remember { mutableStateOf<android.view.View?>(null) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (targetValue == SheetValue.Hidden) {
                val liveIme = isSheetImeVisible || (currentDialogView?.let {
                    ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
                } == true)
                // Block swipe/scrim hide if keyboard is open or just closed
                !liveIme && (System.currentTimeMillis() - lastSheetImeCloseTime > 300L)
            } else {
                true
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = {
            val liveIme = isSheetImeVisible || (currentDialogView?.let {
                ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
            } == true)
            if (!liveIme && System.currentTimeMillis() - lastSheetImeCloseTime > 300L) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = c.card,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        val dialogView = LocalView.current
        val imeVisibleInDialog = WindowInsets.isImeVisible
        val sheetKeyboardController = LocalSoftwareKeyboardController.current
        val sheetFocusManager = LocalFocusManager.current

        LaunchedEffect(dialogView) {
            currentDialogView = dialogView
        }
        LaunchedEffect(imeVisibleInDialog) {
            if (isSheetImeVisible && !imeVisibleInDialog) {
                lastSheetImeCloseTime = System.currentTimeMillis()
            }
            isSheetImeVisible = imeVisibleInDialog
        }

        // Keyboard-first Back handling inside ModalBottomSheet:
        // 1st Back when IME is open hides keyboard only (preserving popup & typed data).
        // Next Back when IME is already closed dismisses the popup.
        BackHandler(enabled = true) {
            val liveIme = imeVisibleInDialog ||
                (ViewCompat.getRootWindowInsets(dialogView)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
            if (liveIme) {
                lastSheetImeCloseTime = System.currentTimeMillis()
                sheetKeyboardController?.hide()
                sheetFocusManager.clearFocus()
            } else if (System.currentTimeMillis() - lastSheetImeCloseTime > 300L) {
                sheetFocusManager.clearFocus()
                onDismiss()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (transactionToEdit == null) "Add Transaction Entry" else "Edit Transaction Entry",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.textPrimary
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = s.close, tint = c.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction Type Toggle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { transactionType = "I_PAID" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (transactionType == "I_PAID") c.redDebit else c.redTint,
                        contentColor = if (transactionType == "I_PAID") Color.White else c.redDebit
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        s.iPaidGave,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { transactionType = "I_RECEIVED" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (transactionType == "I_RECEIVED") c.greenPrimary else c.greenTint,
                        contentColor = if (transactionType == "I_RECEIVED") Color.White else c.greenPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        s.iReceivedGot,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Input Field
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount (₹) *", color = if (isAmountError) c.redDebit else c.textSecondary, fontWeight = FontWeight.Bold) },
                placeholder = { Text("0", color = c.textMuted, fontSize = 26.sp, fontWeight = FontWeight.Bold) },
                isError = isAmountError,
                prefix = {
                    Text(
                        "₹ ",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (transactionType == "I_PAID") c.redDebit else c.greenPrimary
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (transactionType == "I_PAID") c.redDebit else c.greenPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (transactionType == "I_PAID") c.redDebit else c.greenPrimary,
                    unfocusedBorderColor = c.borderStrong,
                    errorBorderColor = c.redDebit,
                    focusedTextColor = if (transactionType == "I_PAID") c.redDebit else c.greenPrimary,
                    unfocusedTextColor = if (transactionType == "I_PAID") c.redDebit else c.greenPrimary,
                    focusedContainerColor = c.surfaceSubtle,
                    unfocusedContainerColor = c.surfaceSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_amount_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Date Selector & Payment Mode Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Date Button
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = c.surfaceSubtle,
                        contentColor = c.textPrimary
                    )
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.blueAccent)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(sdf.format(Date(selectedDateMillis)), fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Bold)
                }

                // Payment Mode Selector (Default "None")
                var expandedMode by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expandedMode = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.borderStrong),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = c.surfaceSubtle,
                            contentColor = c.textPrimary
                        )
                    ) {
                        Text("Mode: $paymentMode", fontSize = 13.sp, color = c.textPrimary, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = c.textSecondary)
                    }

                    DropdownMenu(
                        expanded = expandedMode,
                        onDismissRequest = { expandedMode = false },
                        modifier = Modifier.background(c.card)
                    ) {
                        listOf("None", "CASH", "PhonePe", "Paytm", "GPay", "UPI", "BANK", "CARD").forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode, color = c.textPrimary, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    paymentMode = mode
                                    expandedMode = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Comment / Notes Field
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                label = { Text("Comment / Bill Description", color = c.textSecondary, fontWeight = FontWeight.Medium) },
                placeholder = { Text("e.g. Grocery items, Bill #104", color = c.textMuted) },
                textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.greenPrimary,
                    unfocusedBorderColor = c.borderStrong,
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary,
                    focusedContainerColor = c.surfaceSubtle,
                    unfocusedContainerColor = c.surfaceSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_comment_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bill / Receipt Multiple Attachment Photos
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Attach Bill Photos / Receipts (${attachmentUris.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)

                    TextButton(onClick = { multiPhotoPickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.blueAccent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Photos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.blueAccent)
                    }
                }

                if (attachmentUris.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(attachmentUris) { uriStr ->
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.cardElevated)
                            ) {
                                AsyncImage(
                                    model = uriStr,
                                    contentDescription = "Attachment",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .clickable {
                                            attachmentUris = attachmentUris.filter { it != uriStr }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = s.remove,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Entry Button
            Button(
                enabled = !isSaving,
                onClick = {
                    attemptedSave = true
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        isSaving = true
                        coroutineScope.launch {
                            val joinedAttachmentPath = withContext(Dispatchers.IO) {
                                val attachmentsDirPath = java.io.File(context.filesDir, "attachments").absolutePath
                                val permanentUris = attachmentUris.map { uriStr ->
                                    if (uriStr.startsWith(attachmentsDirPath) && java.io.File(uriStr).exists()) {
                                        uriStr
                                    } else {
                                        com.example.utils.ImageUtils.compressAndSaveImage(context, uriStr, "attachments") ?: uriStr
                                    }
                                }
                                if (permanentUris.isNotEmpty()) permanentUris.joinToString("|") else null
                            }

                            if (transactionToEdit == null) {
                                viewModel.addTransaction(
                                    customerId = customerId,
                                    transactionType = transactionType,
                                    amount = amount,
                                    comment = commentText,
                                    category = selectedCategory,
                                    paymentMode = paymentMode,
                                    dateTimestamp = selectedDateMillis,
                                    attachmentPath = joinedAttachmentPath,
                                    onSuccess = {
                                        isSaving = false
                                        onDismiss()
                                    },
                                    onError = { err ->
                                        isSaving = false
                                        android.widget.Toast.makeText(
                                            context,
                                            "Failed to save entry: ${err.localizedMessage ?: "Unknown error"}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            } else {
                                viewModel.updateTransaction(
                                    transaction = transactionToEdit.copy(
                                        transactionType = transactionType,
                                        amount = amount,
                                        comment = commentText.trim(),
                                        category = selectedCategory,
                                        paymentMode = paymentMode,
                                        dateTimestamp = selectedDateMillis,
                                        attachmentPath = joinedAttachmentPath
                                    ),
                                    onSuccess = {
                                        isSaving = false
                                        onDismiss()
                                    },
                                    onError = { err ->
                                        isSaving = false
                                        android.widget.Toast.makeText(
                                            context,
                                            "Failed to update entry: ${err.localizedMessage ?: "Unknown error"}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transactionType == "I_PAID") c.redDebit else c.greenPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_entry_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (transactionToEdit == null) s.save else "UPDATE ENTRY",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}
