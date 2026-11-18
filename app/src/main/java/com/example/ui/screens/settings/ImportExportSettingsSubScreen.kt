package com.example.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.PdfImportProgressState
import com.example.ui.dialogs.PdfImportPreviewDialog
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportExportSettingsSubScreen(
    authViewModel: AuthViewModel,
    dailyExpenseViewModel: DailyExpenseViewModel?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()

    val dailyBackupsList by (dailyExpenseViewModel?.dailyBackupsList?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) })
    val showPdfConfirmDialog by (dailyExpenseViewModel?.showPdfConfirmDialog?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })
    val showPdfProgressDialog by (dailyExpenseViewModel?.showPdfProgressDialog?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })
    val showPdfSummaryDialog by (dailyExpenseViewModel?.showPdfSummaryDialog?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })
    val selectedPdfUris by (dailyExpenseViewModel?.selectedPdfUris?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) })
    val pdfProgressState by (dailyExpenseViewModel?.pdfProgressState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(PdfImportProgressState()) })
    val pdfSummaryResult by (dailyExpenseViewModel?.pdfSummaryResult?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })
    val showSamplePdfExistDialog by (dailyExpenseViewModel?.showSamplePdfExistDialog?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })
    val showPdfPreviewDialog by (dailyExpenseViewModel?.showPdfPreviewDialog?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })
    val pdfPreviewItems by (dailyExpenseViewModel?.pdfPreviewItems?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) })

    var showSampleDownloadChoiceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dailyExpenseViewModel?.refreshDailyBackupsList()
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        authViewModel.suppressAutoLockOnce()
        uri?.let { fileUri ->
            coroutineScope.launch {
                try {
                    val jsonString = dailyExpenseViewModel?.exportAllDataJson() ?: ""
                    context.contentResolver.openOutputStream(fileUri)?.use { output ->
                        output.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Full ledger & profile backup exported!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving backup file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        authViewModel.suppressAutoLockOnce()
        uri?.let { fileUri ->
            try {
                val jsonString = context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }
                if (!jsonString.isNullOrBlank()) {
                    dailyExpenseViewModel?.restoreFullBackupFromJson(jsonString, authViewModel) { summary ->
                        showRestoreSummaryToast(context, summary)
                    }
                } else {
                    Toast.makeText(context, "Selected backup file is empty!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading backup file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        authViewModel.suppressAutoLockOnce()
        if (!uris.isNullOrEmpty()) {
            dailyExpenseViewModel?.onPdfFilesSelected(context, uris)
        }
    }

    val saveSampleOkCreditLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        authViewModel.suppressAutoLockOnce()
        uri?.let { fileUri ->
            dailyExpenseViewModel?.saveSampleOkCreditToUri(context, fileUri)
        }
    }

    val saveSampleReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        authViewModel.suppressAutoLockOnce()
        uri?.let { fileUri ->
            dailyExpenseViewModel?.saveSampleReportToUri(context, fileUri)
        }
    }

    SettingsSubScreenScaffold(
        title = s.importExportTitle,
        subtitle = s.importExportSubtitle,
        onBack = onBack
    ) {
        // Section 1: Local JSON Backup & Restore
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("LOCAL JSON BACKUP & RESTORE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, letterSpacing = 0.8.sp)
                Text(
                    text = "Export or restore your full ledger (customers, transactions, attachments, monthly expenses & profile) as a local JSON file.",
                    fontSize = 12.sp,
                    color = c.textMuted
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            authViewModel.suppressAutoLockOnce()
                            createBackupLauncher.launch("daily_expenses_backup_${System.currentTimeMillis()}.json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .widthIn(min = 130.dp)
                            .heightIn(min = 48.dp)
                            .testTag("export_local_json_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Local", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            authViewModel.suppressAutoLockOnce()
                            restoreLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.greenPrimary),
                        border = BorderStroke(1.5.dp, c.greenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .widthIn(min = 130.dp)
                            .heightIn(min = 48.dp)
                            .testTag("import_local_json_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Local", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary, maxLines = 1)
                    }
                }
            }
        }

        // Section 2: Local 2-Day Rolling Daily Backups
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("LOCAL DAILY BACKUPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary, letterSpacing = 0.8.sp)
                    Surface(shape = RoundedCornerShape(4.dp), color = c.greenTint) {
                        Text("2-DAY ROLLING", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = c.greenPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Text(
                    text = "Creates an on-device daily JSON backup containing ledger data and user profile. Retains the latest 2 days locally.",
                    fontSize = 11.5.sp,
                    color = c.textMuted
                )

                OutlinedButton(
                    onClick = {
                        dailyExpenseViewModel?.triggerManualDailyBackup { success ->
                            Toast.makeText(
                                context,
                                if (success) "Local daily backup saved!" else "Failed to create daily backup",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.greenPrimary),
                    border = BorderStroke(1.dp, c.greenPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Local Daily Backup Now", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary)
                }

                if (dailyBackupsList.isNotEmpty()) {
                    Text("ON-DEVICE SNAPSHOTS (MAX 2 DAYS):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.textMuted)
                    dailyBackupsList.take(2).forEach { backup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.cardElevated, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(backup.dateDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(backup.fileSizeStr, fontSize = 10.5.sp, color = c.textMuted)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        authViewModel.suppressAutoLockOnce()
                                        try {
                                            val jsonContent = backup.file.readText()
                                            dailyExpenseViewModel?.restoreFullBackupFromJson(jsonContent, authViewModel) { summary ->
                                                showRestoreSummaryToast(context, summary)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore Local Backup", tint = c.greenPrimary, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        authViewModel.suppressAutoLockOnce()
                                        try {
                                            val fileUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                backup.file
                                            )
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                                type = "application/json"
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Backup File"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Local Backup", tint = c.blueAccent, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Customer Statement PDF Import
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("CUSTOMER STATEMENT PDF IMPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary, letterSpacing = 0.8.sp)
                Text(
                    text = "Import OkCredit or Ledger PDF statements with an editable preview before committing.",
                    fontSize = 12.sp,
                    color = c.textMuted
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            authViewModel.suppressAutoLockOnce()
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .widthIn(min = 135.dp)
                            .heightIn(min = 48.dp)
                            .testTag("import_customer_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import PDF", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            authViewModel.suppressAutoLockOnce()
                            showSampleDownloadChoiceDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.greenPrimary),
                        border = BorderStroke(1.5.dp, c.greenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .widthIn(min = 135.dp)
                            .heightIn(min = 48.dp)
                            .testTag("download_sample_pdf_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sample PDFs", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary, maxLines = 1)
                    }
                }
            }
        }
    }

    // PDF Import Preview Dialog
    if (showPdfPreviewDialog && pdfPreviewItems.isNotEmpty()) {
        PdfImportPreviewDialog(
            previewItems = pdfPreviewItems,
            onDismiss = { dailyExpenseViewModel?.dismissPdfPreviewDialog() },
            onCustomerNameChanged = { idx, name -> dailyExpenseViewModel?.updatePreviewCustomerName(idx, name) },
            onMobileNumberChanged = { idx, mob -> dailyExpenseViewModel?.updatePreviewMobileNumber(idx, mob) },
            onAmountChanged = { idx, txId, amt -> dailyExpenseViewModel?.updatePreviewTransactionAmount(idx, txId, amt) },
            onTransactionTypeChanged = { idx, txId, type -> dailyExpenseViewModel?.updatePreviewTransactionType(idx, txId, type) },
            onDeleteTransaction = { idx, txId -> dailyExpenseViewModel?.deletePreviewTransaction(idx, txId) },
            onAddTransaction = { idx -> dailyExpenseViewModel?.addPreviewTransaction(idx) },
            onCommentChanged = { idx, txId, comment -> dailyExpenseViewModel?.updatePreviewTransactionComment(idx, txId, comment) },
            onDateChanged = { idx, txId, date -> dailyExpenseViewModel?.updatePreviewTransactionDate(idx, txId, date) },
            onConfirmImport = { dailyExpenseViewModel?.confirmAndImportPdfPreview(context) }
        )
    }

    // PDF Confirm Dialog
    if (showPdfConfirmDialog) {
        AlertDialog(
            onDismissRequest = { dailyExpenseViewModel?.dismissPdfConfirmDialog() },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = { Text("Customer PDF Import", fontWeight = FontWeight.ExtraBold, color = c.textPrimary, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${selectedPdfUris.size} PDF file(s) selected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary)
                    Text(
                        "Press Import to process statement files, detect customers, extract transaction rows, and migrate data into the ledger.",
                        fontSize = 13.sp,
                        color = c.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { dailyExpenseViewModel?.startMultiPdfImport(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Import", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { dailyExpenseViewModel?.dismissPdfConfirmDialog() }) {
                    Text(s.cancel, color = c.textMuted)
                }
            }
        )
    }

    // PDF Progress Dialog
    if (showPdfProgressDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = { Text("Importing Customer PDFs...", fontWeight = FontWeight.ExtraBold, color = c.textPrimary, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val progressFraction = if (pdfProgressState.totalCount > 0) {
                        pdfProgressState.currentIndex.toFloat() / pdfProgressState.totalCount.toFloat()
                    } else 0f
                    val percent = (progressFraction * 100).toInt()

                    Text(
                        text = "Processing ${pdfProgressState.currentIndex} of ${pdfProgressState.totalCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textSecondary
                    )

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = c.greenPrimary,
                        trackColor = c.border
                    )

                    Text(
                        text = "$percent%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.greenPrimary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pdfProgressState.currentCustomerName.isNotBlank()) {
                        Surface(color = c.greenTint, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Current Customer:", fontSize = 11.sp, color = c.textMuted)
                                Text(pdfProgressState.currentCustomerName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // PDF Import Summary Dialog
    if (showPdfSummaryDialog && pdfSummaryResult != null) {
        val summary = pdfSummaryResult!!
        AlertDialog(
            onDismissRequest = { dailyExpenseViewModel?.dismissPdfSummaryDialog() },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (summary.failedPdfs == 0) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (summary.failedPdfs == 0) c.greenPrimary else c.redDebit,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (summary.failedPdfs == 0) "Import Completed Successfully" else "Import Completed",
                        fontWeight = FontWeight.ExtraBold,
                        color = c.textPrimary,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfSummaryStatRow("PDFs Selected", summary.pdfSelected.toString())
                    PdfSummaryStatRow("PDFs Imported", summary.pdfImported.toString())
                    PdfSummaryStatRow("Customers Created", summary.customersCreated.toString())
                    PdfSummaryStatRow("Customers Updated", summary.customersUpdated.toString())
                    PdfSummaryStatRow("Transactions Imported", String.format("%,d", summary.transactionsImported))
                    PdfSummaryStatRow("Duplicate Transactions Skipped", summary.duplicateTransactionsSkipped.toString())
                    PdfSummaryStatRow("Failed PDFs", summary.failedPdfs.toString(), isHighlight = summary.failedPdfs > 0)
                    PdfSummaryStatRow("Import Time", "${summary.importTimeSeconds} Seconds")

                    if (summary.failedDetails.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Failed File Details:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.redDebit)
                        summary.failedDetails.forEach { detail ->
                            Surface(color = c.redTint, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(detail.fileName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                    Text(detail.reason, fontSize = 11.sp, color = c.redDebit)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { dailyExpenseViewModel?.dismissPdfSummaryDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(s.done, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }

    // Sample PDF Already Exists Dialog
    if (showSamplePdfExistDialog) {
        AlertDialog(
            onDismissRequest = { dailyExpenseViewModel?.dismissSamplePdfExistDialog() },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = { Text("Sample PDF Already Exists", fontWeight = FontWeight.ExtraBold, color = c.textPrimary, fontSize = 18.sp) },
            text = {
                Text(
                    "Sample PDF files already exist in your Downloads folder. Do you want to replace them or keep both copies?",
                    fontSize = 14.sp,
                    color = c.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { dailyExpenseViewModel?.downloadSamplePdfs(context, replace = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Replace", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dailyExpenseViewModel?.dismissSamplePdfExistDialog() }) {
                        Text(s.cancel, color = c.textMuted)
                    }
                    OutlinedButton(
                        onClick = { dailyExpenseViewModel?.downloadSamplePdfs(context, replace = false) },
                        border = BorderStroke(1.dp, c.greenPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Keep Both", fontWeight = FontWeight.Bold, color = c.greenPrimary)
                    }
                }
            }
        )
    }

    // Sample Download Save-As Choice Dialog
    if (showSampleDownloadChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showSampleDownloadChoiceDialog = false },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = { Text("Download / Save Sample PDFs", fontWeight = FontWeight.ExtraBold, color = c.textPrimary, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose which sample statement format you want to save to your device or Google Drive:",
                        fontSize = 13.sp,
                        color = c.textSecondary
                    )

                    Button(
                        onClick = {
                            showSampleDownloadChoiceDialog = false
                            authViewModel.suppressAutoLockOnce()
                            saveSampleOkCreditLauncher.launch("OkCredit_Sample_Customer_Statement.pdf")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save OkCredit Sample PDF As...", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = {
                            showSampleDownloadChoiceDialog = false
                            authViewModel.suppressAutoLockOnce()
                            saveSampleReportLauncher.launch("Transaction_Report_Sample.pdf")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.blueAccent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Transaction Report PDF As...", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            showSampleDownloadChoiceDialog = false
                            authViewModel.suppressAutoLockOnce()
                            dailyExpenseViewModel?.onDownloadSamplePdfClicked(context)
                        },
                        border = BorderStroke(1.5.dp, c.greenPrimary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save All to Public Downloads Folder", fontWeight = FontWeight.Bold, color = c.greenPrimary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSampleDownloadChoiceDialog = false }) {
                    Text(s.close, color = c.textMuted)
                }
            }
        )
    }
}

@Composable
private fun PdfSummaryStatRow(label: String, value: String, isHighlight: Boolean = false) {
    val c = LedgerTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = c.textSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) c.redDebit else c.textPrimary
        )
    }
}
