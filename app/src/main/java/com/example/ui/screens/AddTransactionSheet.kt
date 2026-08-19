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
import com.example.data.TransactionEntity
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    customerId: Long,
    initialType: String = "I_PAID", // "I_PAID" or "I_RECEIVED"
    transactionToEdit: TransactionEntity? = null,
    viewModel: DailyExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
    var selectedDate by remember(transactionToEdit) {
        mutableStateOf(Calendar.getInstance().apply {
            if (transactionToEdit != null) {
                timeInMillis = transactionToEdit.dateTimestamp
            }
        })
    }

    // Multiple Attachment URIs handled as list, stored as pipe-separated string
    var attachmentUris by remember(transactionToEdit) {
        mutableStateOf<List<String>>(
            transactionToEdit?.attachmentPath?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }

    var attemptedSave by remember { mutableStateOf(false) }
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
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            selectedDate.set(Calendar.YEAR, year)
            selectedDate.set(Calendar.MONTH, month)
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        },
        selectedDate.get(Calendar.YEAR),
        selectedDate.get(Calendar.MONTH),
        selectedDate.get(Calendar.DAY_OF_MONTH)
    )

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 16.dp)
                .navigationBarsPadding()
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
                    color = Slate900
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate700)
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
                        containerColor = if (transactionType == "I_PAID") RedDebit else RedLight,
                        contentColor = if (transactionType == "I_PAID") Color.White else RedDebit
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        "I PAID (GAVE ₹)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { transactionType = "I_RECEIVED" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (transactionType == "I_RECEIVED") GreenPrimary else GreenLight,
                        contentColor = if (transactionType == "I_RECEIVED") Color.White else GreenPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        "I RECEIVED (GOT ₹)",
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
                label = { Text("Amount (₹) *", color = if (isAmountError) RedDebit else Slate700, fontWeight = FontWeight.Bold) },
                placeholder = { Text("0", color = Slate400, fontSize = 26.sp, fontWeight = FontWeight.Bold) },
                isError = isAmountError,
                prefix = {
                    Text(
                        "₹ ",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (transactionType == "I_PAID") RedDebit else GreenPrimary
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (transactionType == "I_PAID") RedDebit else GreenPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (transactionType == "I_PAID") RedDebit else GreenPrimary,
                    unfocusedBorderColor = Slate400,
                    errorBorderColor = RedDebit,
                    focusedTextColor = if (transactionType == "I_PAID") RedDebit else GreenPrimary,
                    unfocusedTextColor = if (transactionType == "I_PAID") RedDebit else GreenPrimary,
                    focusedContainerColor = SurfaceBackground,
                    unfocusedContainerColor = SurfaceBackground
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceBackground,
                        contentColor = Slate900
                    )
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = SecondaryBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(sdf.format(selectedDate.time), fontSize = 13.sp, color = Slate900, fontWeight = FontWeight.Bold)
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
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceBackground,
                            contentColor = Slate900
                        )
                    ) {
                        Text("Mode: $paymentMode", fontSize = 13.sp, color = Slate900, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Slate700)
                    }

                    DropdownMenu(
                        expanded = expandedMode,
                        onDismissRequest = { expandedMode = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        listOf("None", "CASH", "PhonePe", "Paytm", "GPay", "UPI", "BANK", "CARD").forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode, color = Slate900, fontWeight = FontWeight.Bold) },
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
                label = { Text("Comment / Bill Description", color = Slate700, fontWeight = FontWeight.Medium) },
                placeholder = { Text("e.g. Grocery items, Bill #104", color = Slate400) },
                textStyle = LocalTextStyle.current.copy(color = Slate900, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = Slate400,
                    focusedTextColor = Slate900,
                    unfocusedTextColor = Slate900,
                    focusedContainerColor = SurfaceBackground,
                    unfocusedContainerColor = SurfaceBackground
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
                    Text("Attach Bill Photos / Receipts (${attachmentUris.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)

                    TextButton(onClick = { multiPhotoPickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Photos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SecondaryBlue)
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
                                    .background(Slate200)
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
                                        contentDescription = "Remove",
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
            val permanentUris = attachmentUris.map { uriStr ->
                com.example.utils.ImageUtils.compressAndSaveImage(context, uriStr, "attachments") ?: uriStr
            }
            val joinedAttachmentPath = if (permanentUris.isNotEmpty()) permanentUris.joinToString("|") else null

            Button(
                onClick = {
                    attemptedSave = true
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        if (transactionToEdit == null) {
                            viewModel.addTransaction(
                                customerId = customerId,
                                transactionType = transactionType,
                                amount = amount,
                                comment = commentText,
                                category = selectedCategory,
                                paymentMode = paymentMode,
                                dateTimestamp = selectedDate.timeInMillis,
                                attachmentPath = joinedAttachmentPath
                            )
                        } else {
                            viewModel.updateTransaction(
                                transactionToEdit.copy(
                                    transactionType = transactionType,
                                    amount = amount,
                                    comment = commentText.trim(),
                                    category = selectedCategory,
                                    paymentMode = paymentMode,
                                    dateTimestamp = selectedDate.timeInMillis,
                                    attachmentPath = joinedAttachmentPath
                                )
                            )
                        }
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transactionType == "I_PAID") RedDebit else GreenPrimary
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
                    if (transactionToEdit == null) "SAVE ENTRY" else "UPDATE ENTRY",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}
