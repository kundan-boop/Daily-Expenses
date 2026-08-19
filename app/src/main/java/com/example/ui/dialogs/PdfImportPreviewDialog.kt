package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.pdf.PdfPreviewItem
import com.example.domain.pdf.PreviewTransaction
import com.example.ui.theme.*
import com.example.utils.PdfFormatType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportPreviewDialog(
    previewItems: List<PdfPreviewItem>,
    onDismiss: () -> Unit,
    onCustomerNameChanged: (fileIndex: Int, newName: String) -> Unit,
    onMobileNumberChanged: (fileIndex: Int, newMobile: String) -> Unit,
    onAmountChanged: (fileIndex: Int, txId: String, newAmountText: String) -> Unit,
    onTransactionTypeChanged: (fileIndex: Int, txId: String, newType: String) -> Unit,
    onDeleteTransaction: (fileIndex: Int, txId: String) -> Unit,
    onAddTransaction: (fileIndex: Int) -> Unit = {},
    onCommentChanged: (fileIndex: Int, txId: String, newComment: String) -> Unit = { _, _, _ -> },
    onDateChanged: (fileIndex: Int, txId: String, newDate: String) -> Unit = { _, _, _ -> },
    onConfirmImport: () -> Unit
) {
    if (previewItems.isEmpty()) return

    var selectedFileIndex by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current

    val currentItem = previewItems.getOrNull(selectedFileIndex) ?: previewItems.first()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 820.dp)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                tonalElevation = 6.dp
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Bar / Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GreenLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Preview,
                                    contentDescription = null,
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Preview & Edit PDF Import",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = "Verify extracted names, add missing rows, then save",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate700,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Multi-File Tabs (if > 1 file selected)
                if (previewItems.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedFileIndex,
                        edgePadding = 0.dp,
                        containerColor = Slate100,
                        indicator = {},
                        divider = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        previewItems.forEachIndexed { idx, item ->
                            val isSelected = selectedFileIndex == idx
                            Tab(
                                selected = isSelected,
                                onClick = { selectedFileIndex = idx },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) GreenPrimary else Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isSuccess) Icons.Default.PictureAsPdf else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else if (item.isSuccess) Slate700 else RedDebit,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.fileName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Slate700,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // File Status Badge / Info
                Surface(
                    color = if (currentItem.isSuccess) GreenLight else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (currentItem.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (currentItem.isSuccess) GreenPrimary else RedDebit,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentItem.fileName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val formatName = when (currentItem.formatType) {
                                PdfFormatType.OK_CREDIT_STATEMENT -> "OkCredit Statement"
                                PdfFormatType.TRANSACTION_REPORT -> "Transaction Report Summary"
                                PdfFormatType.UNSUPPORTED -> "Unsupported Format"
                            }
                            Text(
                                text = if (currentItem.isSuccess) "Format: $formatName (${currentItem.transactions.size} transactions)" else "Error: ${currentItem.errorMessage ?: "Parsing failed"}",
                                fontSize = 11.sp,
                                color = if (currentItem.isSuccess) GreenPrimary else RedDebit
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Alert / Warning banner if parsing failed or details missing
                val hasWarningOrError = !currentItem.isSuccess || currentItem.customerName.isBlank() || currentItem.transactions.isEmpty() || !currentItem.errorMessage.isNullOrBlank()
                if (hasWarningOrError) {
                    Surface(
                        color = if (!currentItem.isSuccess) Color(0xFFFFEBEE) else Color(0xFFFFF3CD),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (!currentItem.isSuccess) RedDebit.copy(alpha = 0.4f) else Color(0xFFFFEEBA)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (!currentItem.isSuccess) Icons.Default.Error else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (!currentItem.isSuccess) RedDebit else Color(0xFF856404),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentItem.errorMessage ?: if (currentItem.customerName.isBlank() && currentItem.transactions.isEmpty()) {
                                    "Customer name and transactions not found in PDF. Type name & click '+ Add Tx' below to enter manually."
                                } else if (currentItem.customerName.isBlank()) {
                                    "Customer name not found in PDF. Please enter customer name below."
                                } else {
                                    "No valid transactions found in PDF. Click '+ Add Tx' below to add transactions manually."
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!currentItem.isSuccess) RedDebit else Color(0xFF856404)
                            )
                        }
                    }
                }

                // Editable Customer Info Card & Transaction List (Always accessible)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Customer Editable Header Section
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate100),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Customer Info",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate800
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = currentItem.customerName,
                                    onValueChange = { onCustomerNameChanged(selectedFileIndex, it) },
                                    label = { Text("Customer Name *") },
                                    isError = currentItem.customerName.isBlank(),
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = if (currentItem.customerName.isBlank()) RedDebit else GreenPrimary) },
                                    singleLine = true,
                                    supportingText = if (currentItem.customerName.isBlank()) {
                                        { Text("⚠️ Customer name required before importing", color = RedDebit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                    } else null,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GreenPrimary,
                                        unfocusedBorderColor = if (currentItem.customerName.isBlank()) RedDebit else Slate300,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = currentItem.mobileNumber,
                                    onValueChange = { onMobileNumberChanged(selectedFileIndex, it) },
                                    label = { Text("Mobile Number (Optional)") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Slate500) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GreenPrimary,
                                        unfocusedBorderColor = Slate300,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Transaction List Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Transactions (${currentItem.transactions.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )

                                val totalReceived = currentItem.transactions
                                    .filter { it.transactionType == "I_RECEIVED" }
                                    .sumOf { it.amountText.replace(",", "").toDoubleOrNull() ?: 0.0 }
                                val totalPaid = currentItem.transactions
                                    .filter { it.transactionType == "I_PAID" }
                                    .sumOf { it.amountText.replace(",", "").toDoubleOrNull() ?: 0.0 }

                                Text(
                                    text = "Rec: ₹${String.format(Locale.US, "%.0f", totalReceived)} | Paid: ₹${String.format(Locale.US, "%.0f", totalPaid)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate600
                                )
                            }

                            // Add Transaction Button
                            Button(
                                onClick = { onAddTransaction(selectedFileIndex) },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenLight, contentColor = GreenPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Tx", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                        if (currentItem.transactions.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = null,
                                            tint = Color(0xFFF57F17),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "No transactions fetched automatically",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Click '+ Add Tx' above to manually add transactions.",
                                            fontSize = 12.sp,
                                            color = Slate600,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Transaction Rows
                        itemsIndexed(currentItem.transactions, key = { _, tx -> tx.id }) { _, tx ->
                            PreviewTransactionRow(
                                tx = tx,
                                onAmountChanged = { newAmt -> onAmountChanged(selectedFileIndex, tx.id, newAmt) },
                                onCommentChanged = { newComment -> onCommentChanged(selectedFileIndex, tx.id, newComment) },
                                onDateChanged = { newDate -> onDateChanged(selectedFileIndex, tx.id, newDate) },
                                onTypeToggled = {
                                    val newType = if (tx.transactionType == "I_RECEIVED") "I_PAID" else "I_RECEIVED"
                                    onTransactionTypeChanged(selectedFileIndex, tx.id, newType)
                                },
                                onDelete = { onDeleteTransaction(selectedFileIndex, tx.id) }
                            )
                        }
                    }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, color = Slate600)
                    }

                    val validItemsCount = previewItems.count {
                        it.customerName.isNotBlank() && it.transactions.any { tx -> (tx.amountText.replace(",", "").toDoubleOrNull() ?: 0.0) > 0.0 }
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onConfirmImport()
                        },
                        enabled = validItemsCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenPrimary,
                            disabledContainerColor = Slate300
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (validItemsCount > 0) "Import ($validItemsCount File${if (validItemsCount > 1) "s" else ""})" else "Enter Required Info",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun PreviewTransactionRow(
    tx: PreviewTransaction,
    onAmountChanged: (String) -> Unit,
    onCommentChanged: (String) -> Unit,
    onDateChanged: (String) -> Unit,
    onTypeToggled: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Transaction Type Badge Button
                Surface(
                    onClick = onTypeToggled,
                    shape = RoundedCornerShape(8.dp),
                    color = if (tx.transactionType == "I_RECEIVED") GreenLight else Color(0xFFFFEBEE),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (tx.transactionType == "I_RECEIVED") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = if (tx.transactionType == "I_RECEIVED") GreenPrimary else RedDebit,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (tx.transactionType == "I_RECEIVED") "RECEIVED" else "PAID",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (tx.transactionType == "I_RECEIVED") GreenPrimary else RedDebit
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Date
                OutlinedTextField(
                    value = tx.dateString,
                    onValueChange = onDateChanged,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = Slate200,
                        focusedContainerColor = Slate100,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Editable Amount Field
                OutlinedTextField(
                    value = tx.amountText,
                    onValueChange = onAmountChanged,
                    prefix = { Text("₹", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = Slate300,
                        focusedContainerColor = Slate100,
                        unfocusedContainerColor = Slate100
                    ),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900),
                    modifier = Modifier
                        .widthIn(min = 70.dp, max = 110.dp)
                        .weight(0.85f)
                        .height(44.dp)
                )

                // Delete Action Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Row",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Editable Comment / Notes Field
            OutlinedTextField(
                value = tx.comment,
                onValueChange = onCommentChanged,
                placeholder = { Text("Add notes / transaction description", fontSize = 11.sp, color = Slate400) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = Slate200,
                    focusedContainerColor = Slate100,
                    unfocusedContainerColor = Slate100
                ),
                shape = RoundedCornerShape(6.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            )

            val parsedAmt = tx.amountText.replace(",", "").toDoubleOrNull() ?: 0.0
            val isRowError = tx.isError || parsedAmt <= 0.0
            if (isRowError) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFEBEE), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = RedDebit,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tx.errorMessage ?: if (parsedAmt <= 0.0) "Invalid amount (₹0.00). Please enter amount above." else "Transaction error",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedDebit
                    )
                }
            }

            if (tx.attachmentPath.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                val paths = tx.attachmentPath.split(Regex("[|,]")).map { it.trim() }.filter { it.isNotBlank() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    paths.take(4).forEach { imgPath ->
                        coil.compose.AsyncImage(
                            model = imgPath,
                            contentDescription = "Attachment",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Slate300, RoundedCornerShape(6.dp))
                        )
                    }
                    Text(
                        text = "📷 ${paths.size} image${if (paths.size > 1) "s" else ""} attached",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate600
                    )
                }
            }
        }
    }
}
