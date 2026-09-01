package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import com.example.utils.PdfImportEngine
import com.example.utils.PdfImportEngine.MultiPdfImportSummary
import com.example.domain.pdf.PdfPreviewItem
import com.example.domain.pdf.PreviewTransaction

enum class CustomerFilter {
    ALL, YOU_GET, YOU_GIVE, COLLECT_TODAY, SETTLED
}

data class CustomerBalanceSummary(
    val customer: CustomerEntity,
    val totalPaid: Double,     // I Paid (You gave to customer)
    val totalReceived: Double, // I Received (Customer paid you)
    val netBalance: Double,    // totalPaid - totalReceived (Positive = YOU GET, Negative = YOU GIVE)
    val lastTransactionDate: Long?,
    val transactionComments: List<String> = emptyList(),
    val transactionAmounts: List<Double> = emptyList()
)

data class DashboardSummary(
    val totalYouWillReceive: Double = 0.0,
    val totalYouNeedToPay: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalBalance: Double = 0.0,
    val totalCustomersCount: Int = 0,
    val thisMonthCollection: Double = 0.0,
    val todayCollection: Double = 0.0
)

data class PdfImportProgressState(
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentCustomerName: String = ""
)

class DailyExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DailyExpenseRepository

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _dailyBackupsList = MutableStateFlow<List<com.example.utils.DailyBackupInfo>>(emptyList())
    val dailyBackupsList: StateFlow<List<com.example.utils.DailyBackupInfo>> = _dailyBackupsList.asStateFlow()

    private val _driveBackupsList = MutableStateFlow<List<com.example.utils.DriveBackupInfo>>(emptyList())
    val driveBackupsList: StateFlow<List<com.example.utils.DriveBackupInfo>> = _driveBackupsList.asStateFlow()

    private val _driveFolderName = MutableStateFlow<String?>(com.example.utils.BackupManager.getDriveFolderName(application))
    val driveFolderName: StateFlow<String?> = _driveFolderName.asStateFlow()

    private val _hasPendingDriveBackup = MutableStateFlow(com.example.utils.BackupManager.hasPendingDriveBackup(application))
    val hasPendingDriveBackup: StateFlow<Boolean> = _hasPendingDriveBackup.asStateFlow()

    val backupStatus: StateFlow<com.example.utils.DriveBackupStatus> = com.example.utils.BackupManager.backupStatusFlow

    fun refreshDailyBackupsList() {
        com.example.utils.BackupManager.refreshBackupStatus(getApplication())
        val list = com.example.utils.BackupManager.getDailyBackups(getApplication())
        _dailyBackupsList.value = list
        refreshDriveBackupsList()
    }

    fun refreshDriveBackupsList() {
        _driveFolderName.value = com.example.utils.BackupManager.getDriveFolderName(getApplication())
        _hasPendingDriveBackup.value = com.example.utils.BackupManager.hasPendingDriveBackup(getApplication())
        com.example.utils.BackupManager.refreshBackupStatus(getApplication())
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = com.example.utils.BackupManager.getDriveBackups(getApplication())
            _driveBackupsList.value = list
            _hasPendingDriveBackup.value = com.example.utils.BackupManager.hasPendingDriveBackup(getApplication())
            com.example.utils.BackupManager.refreshBackupStatus(getApplication())
        }
    }

    fun saveDriveFolderUri(uri: android.net.Uri) {
        com.example.utils.BackupManager.saveDriveFolderUri(getApplication(), uri)
        refreshDriveBackupsList()
    }

    fun clearDriveFolderUri() {
        com.example.utils.BackupManager.clearDriveFolderUri(getApplication())
        refreshDriveBackupsList()
    }

    fun triggerDriveAutoBackupNow(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.example.utils.BackupManager.enqueueDriveBackupWork(getApplication())
                val isOnline = com.example.utils.BackupManager.isNetworkAvailable(getApplication())
                if (!isOnline) {
                    com.example.utils.BackupManager.setPendingDriveBackup(getApplication(), true)
                    com.example.utils.BackupManager.updateBackupStatus(
                        getApplication(),
                        com.example.utils.BackupState.PENDING_OFFLINE,
                        com.example.utils.BackupManager.getLastDriveBackupTime(getApplication()),
                        "Offline: backup queued"
                    )
                    refreshDriveBackupsList()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete?.invoke(false)
                    }
                    return@launch
                }

                com.example.utils.BackupManager.updateBackupStatus(
                    getApplication(),
                    com.example.utils.BackupState.IN_PROGRESS,
                    com.example.utils.BackupManager.getLastDriveBackupTime(getApplication()),
                    null
                )

                val jsonStr = exportAllDataJson()
                val success = if (jsonStr.isNotBlank()) {
                    com.example.utils.BackupManager.performDailyBackup(getApplication(), jsonStr)
                    com.example.utils.BackupManager.performDriveAutoBackup(getApplication(), jsonStr)
                } else false

                if (success) {
                    val now = System.currentTimeMillis()
                    com.example.utils.BackupManager.setPendingDriveBackup(getApplication(), false)
                    com.example.utils.BackupManager.markBackupCompleted(getApplication())
                    com.example.utils.BackupManager.updateBackupStatus(
                        getApplication(),
                        com.example.utils.BackupState.SUCCESS,
                        now,
                        null
                    )
                } else {
                    com.example.utils.BackupManager.setPendingDriveBackup(getApplication(), true)
                    com.example.utils.BackupManager.updateBackupStatus(
                        getApplication(),
                        com.example.utils.BackupState.FAILED,
                        com.example.utils.BackupManager.getLastDriveBackupTime(getApplication()),
                        "Manual backup upload failed"
                    )
                }
                refreshDriveBackupsList()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun triggerManualDailyBackup(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonStr = exportAllDataJson()
                if (jsonStr.isNotBlank()) {
                    val info = com.example.utils.BackupManager.performDailyBackup(getApplication(), jsonStr)
                    refreshDailyBackupsList()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete?.invoke(info != null)
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete?.invoke(false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            triggerManualDailyBackup()
            kotlinx.coroutines.delay(650)
            _isRefreshing.value = false
        }
    }

    // Multi-PDF Import State
    private val _showPdfConfirmDialog = MutableStateFlow(false)
    val showPdfConfirmDialog: StateFlow<Boolean> = _showPdfConfirmDialog.asStateFlow()

    private val _showPdfProgressDialog = MutableStateFlow(false)
    val showPdfProgressDialog: StateFlow<Boolean> = _showPdfProgressDialog.asStateFlow()

    private val _showPdfSummaryDialog = MutableStateFlow(false)
    val showPdfSummaryDialog: StateFlow<Boolean> = _showPdfSummaryDialog.asStateFlow()

    private val _selectedPdfUris = MutableStateFlow<List<android.net.Uri>>(emptyList())
    val selectedPdfUris: StateFlow<List<android.net.Uri>> = _selectedPdfUris.asStateFlow()

    private val _pdfProgressState = MutableStateFlow(PdfImportProgressState())
    val pdfProgressState: StateFlow<PdfImportProgressState> = _pdfProgressState.asStateFlow()

    private val _pdfSummaryResult = MutableStateFlow<MultiPdfImportSummary?>(null)
    val pdfSummaryResult: StateFlow<MultiPdfImportSummary?> = _pdfSummaryResult.asStateFlow()

    private val _showPdfPreviewDialog = MutableStateFlow(false)
    val showPdfPreviewDialog: StateFlow<Boolean> = _showPdfPreviewDialog.asStateFlow()

    private val _pdfPreviewItems = MutableStateFlow<List<PdfPreviewItem>>(emptyList())
    val pdfPreviewItems: StateFlow<List<PdfPreviewItem>> = _pdfPreviewItems.asStateFlow()

    private val _showSamplePdfExistDialog = MutableStateFlow(false)
    val showSamplePdfExistDialog: StateFlow<Boolean> = _showSamplePdfExistDialog.asStateFlow()

    fun onDownloadSamplePdfClicked(context: android.content.Context) {
        if (PdfImportEngine.checkSamplePdfsExist(context)) {
            _showSamplePdfExistDialog.value = true
        } else {
            downloadSamplePdfs(context, replace = true)
        }
    }

    fun dismissSamplePdfExistDialog() {
        _showSamplePdfExistDialog.value = false
    }

    fun downloadSamplePdfs(context: android.content.Context, replace: Boolean) {
        _showSamplePdfExistDialog.value = false
        val files = PdfImportEngine.createSamplePdfs(context, replace)
        if (files.isNotEmpty()) {
            android.widget.Toast.makeText(context, "Downloaded ${files.size} sample PDFs to Downloads folder!", android.widget.Toast.LENGTH_LONG).show()
        } else {
            android.widget.Toast.makeText(context, "Could not save sample PDFs.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun saveSampleOkCreditToUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bytes = PdfImportEngine.generateOkCreditSamplePdfBytes(context)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                    os.flush()
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "OkCredit Sample PDF saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error saving sample PDF: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveSampleReportToUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bytes = PdfImportEngine.generateTransactionReportSamplePdfBytes(context)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                    os.flush()
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Transaction Report Sample PDF saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error saving sample PDF: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun onPdfFilesSelected(context: android.content.Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        _selectedPdfUris.value = uris
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val previewItems = PdfImportEngine.generatePreviewItems(context, uris)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _pdfPreviewItems.value = previewItems
                _showPdfPreviewDialog.value = true
            }
        }
    }

    fun onBackupFileSelected(context: android.content.Context, jsonString: String) {
        if (jsonString.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val previewItems = parseBackupJsonToPreviewItems(context, jsonString)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _pdfPreviewItems.value = previewItems
                _showPdfPreviewDialog.value = true
            }
        }
    }

    suspend fun parseBackupJsonToPreviewItems(context: android.content.Context, jsonString: String): List<PdfPreviewItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val previewItems = mutableListOf<PdfPreviewItem>()
        try {
            val rootObj = org.json.JSONObject(jsonString)
            val custArray = rootObj.optJSONArray("customers") ?: org.json.JSONArray()
            val txArray = rootObj.optJSONArray("transactions") ?: org.json.JSONArray()

            val custMap = mutableMapOf<Long, PdfPreviewItem>()

            for (i in 0 until custArray.length()) {
                val obj = custArray.getJSONObject(i)
                val oldId = obj.optLong("id", i.toLong() + 1)
                val nameVal = obj.optString("name", "Customer ${i + 1}").trim()
                val mobileVal = obj.optString("mobileNumber", "").trim()
                val addressVal = obj.optString("address", "").trim()

                val item = PdfPreviewItem(
                    uri = android.net.Uri.EMPTY,
                    fileName = "Backup: $nameVal",
                    formatType = com.example.utils.PdfFormatType.TRANSACTION_REPORT,
                    customerName = nameVal,
                    mobileNumber = mobileVal,
                    address = addressVal,
                    isSuccess = true,
                    errorMessage = if (nameVal.isBlank()) "Missing customer name" else null,
                    transactions = mutableListOf()
                )
                custMap[oldId] = item
                previewItems.add(item)
            }

            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US)

            for (i in 0 until txArray.length()) {
                val obj = txArray.getJSONObject(i)
                val oldCustId = obj.optLong("customerId", 0L)
                val targetItem = custMap[oldCustId] ?: previewItems.firstOrNull()

                if (targetItem != null) {
                    val txType = obj.optString("transactionType", "I_PAID")
                    val txAmount = obj.optDouble("amount", 0.0)
                    val txDate = obj.optLong("dateTimestamp", System.currentTimeMillis())
                    val txComment = obj.optString("comment", "").trim()

                    val restoredAttachPath = when {
                        obj.has("attachmentBase64List") -> restoreBase64JsonArrayToLocalPaths(context, obj.getJSONArray("attachmentBase64List"), "attachments")
                        obj.has("attachmentBase64") && obj.getString("attachmentBase64").isNotBlank() -> saveBase64ToLocalFile(obj.getString("attachmentBase64"), "attachments")
                        else -> obj.optString("attachmentPath", "").ifBlank { null }
                    }

                    val previewTx = PreviewTransaction(
                        originalIndex = i,
                        dateTimestamp = txDate,
                        dateString = dateFormat.format(java.util.Date(txDate)),
                        transactionType = txType,
                        amountText = String.format(java.util.Locale.US, "%.2f", txAmount),
                        comment = txComment,
                        attachmentPath = restoredAttachPath ?: ""
                    )
                    targetItem.transactions.add(previewTx)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext previewItems
    }

    fun onPdfFilesSelected(uris: List<android.net.Uri>) {
        if (uris.isNotEmpty()) {
            _selectedPdfUris.value = uris
            _showPdfConfirmDialog.value = true
        }
    }

    fun dismissPdfPreviewDialog() {
        _showPdfPreviewDialog.value = false
        _pdfPreviewItems.value = emptyList()
        _selectedPdfUris.value = emptyList()
    }

    fun updatePreviewCustomerName(fileIndex: Int, newName: String) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            currentList[fileIndex].customerName = newName
            _pdfPreviewItems.value = currentList
        }
    }

    fun updatePreviewMobileNumber(fileIndex: Int, newMobile: String) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            currentList[fileIndex].mobileNumber = newMobile
            _pdfPreviewItems.value = currentList
        }
    }

    fun updatePreviewTransactionAmount(fileIndex: Int, txId: String, newAmountText: String) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            val item = currentList[fileIndex]
            val txIndex = item.transactions.indexOfFirst { it.id == txId }
            if (txIndex != -1) {
                item.transactions[txIndex].amountText = newAmountText
                _pdfPreviewItems.value = currentList
            }
        }
    }

    fun updatePreviewTransactionType(fileIndex: Int, txId: String, newType: String) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            val item = currentList[fileIndex]
            val txIndex = item.transactions.indexOfFirst { it.id == txId }
            if (txIndex != -1) {
                item.transactions[txIndex].transactionType = newType
                _pdfPreviewItems.value = currentList
            }
        }
    }

    fun deletePreviewTransaction(fileIndex: Int, txId: String) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            val item = currentList[fileIndex]
            item.transactions.removeAll { it.id == txId }
            _pdfPreviewItems.value = currentList
        }
    }

    fun addPreviewTransaction(fileIndex: Int) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            val item = currentList[fileIndex]
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US)
            val now = System.currentTimeMillis()
            val newTx = com.example.domain.pdf.PreviewTransaction(
                id = java.util.UUID.randomUUID().toString(),
                originalIndex = item.transactions.size,
                dateTimestamp = now,
                dateString = dateFormat.format(java.util.Date(now)),
                transactionType = "I_PAID",
                amountText = "0.00",
                comment = "Manual Entry"
            )
            item.transactions.add(0, newTx)
            _pdfPreviewItems.value = currentList
        }
    }

    fun updatePreviewTransactionComment(fileIndex: Int, txId: String, newComment: String) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            val item = currentList[fileIndex]
            val txIndex = item.transactions.indexOfFirst { it.id == txId }
            if (txIndex != -1) {
                item.transactions[txIndex].comment = newComment
                _pdfPreviewItems.value = currentList
            }
        }
    }

    fun updatePreviewTransactionAttachment(fileIndex: Int, txId: String, newAttachmentPath: String) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            val item = currentList[fileIndex]
            val txIndex = item.transactions.indexOfFirst { it.id == txId }
            if (txIndex != -1) {
                item.transactions[txIndex].attachmentPath = newAttachmentPath
                item.transactions[txIndex].isError = false
                item.transactions[txIndex].errorMessage = null
                _pdfPreviewItems.value = currentList
            }
        }
    }

    fun updatePreviewTransactionDate(fileIndex: Int, txId: String, newDateString: String) {
        val currentList = _pdfPreviewItems.value.map { item ->
            val txList = item.transactions.map { it.copy() }.toMutableList()
            item.copy(transactions = txList)
        }.toMutableList()
        if (fileIndex in currentList.indices) {
            val item = currentList[fileIndex]
            val txIndex = item.transactions.indexOfFirst { it.id == txId }
            if (txIndex != -1) {
                item.transactions[txIndex].dateString = newDateString
                _pdfPreviewItems.value = currentList
            }
        }
    }

    fun confirmAndImportPdfPreview(context: android.content.Context) {
        val items = _pdfPreviewItems.value
        if (items.isEmpty()) return

        _showPdfPreviewDialog.value = false
        _showPdfProgressDialog.value = true

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val summary = PdfImportEngine.commitPreviewItems(
                context = context,
                items = items,
                repository = repository,
                onProgress = { currentIdx, total, customerName ->
                    _pdfProgressState.value = PdfImportProgressState(currentIdx, total, customerName)
                }
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _pdfSummaryResult.value = summary
                _showPdfProgressDialog.value = false
                _showPdfSummaryDialog.value = true
                _pdfPreviewItems.value = emptyList()
                _selectedPdfUris.value = emptyList()
            }
        }
    }

    fun dismissPdfConfirmDialog() {
        _showPdfConfirmDialog.value = false
        _selectedPdfUris.value = emptyList()
    }

    fun startMultiPdfImport(context: android.content.Context) {
        val uris = _selectedPdfUris.value
        if (uris.isEmpty()) return

        _showPdfConfirmDialog.value = false
        _showPdfProgressDialog.value = true

        viewModelScope.launch {
            val summary = PdfImportEngine.processMultiplePdfs(
                context = context,
                pdfUris = uris,
                repository = repository,
                onProgress = { currentIdx, total, customerName ->
                    _pdfProgressState.value = PdfImportProgressState(currentIdx, total, customerName)
                }
            )
            _pdfSummaryResult.value = summary
            _showPdfProgressDialog.value = false
            _showPdfSummaryDialog.value = true
            _selectedPdfUris.value = emptyList()
        }
    }

    fun dismissPdfSummaryDialog() {
        _showPdfSummaryDialog.value = false
        _pdfSummaryResult.value = null
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = DailyExpenseRepository(database, application)

        // Background maintenance: orphaned image cleanup & temp cache cleanup
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val activePaths = repository.getAllActiveImagePaths()
                com.example.utils.ImageUtils.cleanOrphanedImages(getApplication(), activePaths)
                com.example.utils.ImageUtils.cleanupTempCacheFiles(getApplication())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Automated Daily Backup Check (2-Day Rolling Cycle)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(2000)
            refreshDailyBackupsList()
            if (com.example.utils.BackupManager.isBackupNeededToday(getApplication())) {
                triggerManualDailyBackup()
            }
        }

        // Real-time automatic syncing of monthly expenses into ledger
        viewModelScope.launch {
            repository.allExpenses.collect {
                syncMonthlyExpensesToLedger()
            }
        }
    }

    val allCategories: StateFlow<List<CategoryEntity>> = repository.allCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CustomerFilter.ALL)
    val selectedFilter: StateFlow<CustomerFilter> = _selectedFilter.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    val customersWithTransactions: StateFlow<List<CustomerWithTransactions>> =
        repository.customersWithTransactions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val customerSummaries: StateFlow<List<CustomerBalanceSummary>> = customersWithTransactions
        .map { list ->
            list.map { cwt ->
                val paid = cwt.transactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
                val received = cwt.transactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
                val lastTx = cwt.transactions.maxOfOrNull { it.dateTimestamp }
                CustomerBalanceSummary(
                    customer = cwt.customer,
                    totalPaid = paid,
                    totalReceived = received,
                    netBalance = paid - received,
                    lastTransactionDate = lastTx,
                    transactionComments = cwt.transactions.map { it.comment },
                    transactionAmounts = cwt.transactions.map { it.amount }
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredCustomerSummaries: StateFlow<List<CustomerBalanceSummary>> = combine(
        customerSummaries,
        _searchQuery,
        _selectedFilter
    ) { summaries, query, filter ->
        summaries.filter { summary ->
            val cleanQuery = query.trim()
            val matchesQuery = cleanQuery.isBlank() ||
                    summary.customer.name.contains(cleanQuery, ignoreCase = true) ||
                    summary.customer.mobileNumber.contains(cleanQuery, ignoreCase = true) ||
                    summary.transactionComments.any { it.contains(cleanQuery, ignoreCase = true) } ||
                    summary.transactionAmounts.any { it.toString().contains(cleanQuery) } ||
                    summary.netBalance.toString().contains(cleanQuery)

            val matchesFilter = when (filter) {
                CustomerFilter.ALL -> true
                CustomerFilter.YOU_GET -> summary.netBalance > 0.01
                CustomerFilter.YOU_GIVE -> summary.netBalance < -0.01
                CustomerFilter.COLLECT_TODAY -> summary.customer.collectToday
                CustomerFilter.SETTLED -> Math.abs(summary.netBalance) <= 0.01
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val dashboardSummary: StateFlow<DashboardSummary> = customerSummaries.map { summaries ->
        var receive = 0.0
        var pay = 0.0
        var expenses = 0.0
        summaries.forEach { s ->
            val isDeduction = s.customer.name.contains("Deduction", ignoreCase = true)
            if (isDeduction) {
                val exp = if (s.netBalance != 0.0) kotlin.math.abs(s.netBalance) else s.totalPaid
                expenses += exp
            } else {
                if (s.netBalance > 0) {
                    receive += s.netBalance
                } else if (s.netBalance < 0) {
                    pay += Math.abs(s.netBalance)
                }
            }
        }

        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val currentDay = cal.get(Calendar.DAY_OF_YEAR)

        var monthColl = 0.0
        var todayColl = 0.0

        // We can calculate from all transactions
        DashboardSummary(
            totalYouWillReceive = receive,
            totalYouNeedToPay = pay,
            totalExpenses = expenses,
            totalBalance = receive - pay,
            totalCustomersCount = summaries.size,
            thisMonthCollection = monthColl,
            todayCollection = todayColl
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardSummary()
    )

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allExpenses: StateFlow<List<MonthlyExpenseEntity>> = repository.allExpenses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getCustomersPagedFlow(query: String = ""): Flow<PagingData<CustomerEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            repository.getCustomersPaged(query)
        }.flow.cachedIn(viewModelScope)
    }

    fun getTransactionsForCustomerPagedFlow(customerId: Long): Flow<PagingData<TransactionEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            repository.getTransactionsForCustomerPaged(customerId)
        }.flow.cachedIn(viewModelScope)
    }

    fun setFilter(filter: CustomerFilter) {
        _selectedFilter.value = filter
    }

    fun selectCustomer(customer: CustomerEntity?) {
        _selectedCustomer.value = customer
    }

    fun getTransactionsForCustomer(customerId: Long): Flow<List<TransactionEntity>> {
        return repository.getTransactionsForCustomer(customerId)
    }

    // Customer Actions
    fun addCustomer(
        name: String,
        mobileNumber: String,
        address: String = "",
        email: String = "",
        notes: String = "",
        collectToday: Boolean = false,
        collectDueDate: Long? = null,
        profilePhotoUri: String? = null
    ) {
        viewModelScope.launch {
            repository.insertCustomer(
                CustomerEntity(
                    name = name.trim(),
                    mobileNumber = mobileNumber.trim(),
                    address = address.trim(),
                    email = email.trim(),
                    notes = notes.trim(),
                    collectToday = collectToday,
                    collectDueDate = collectDueDate,
                    profilePhotoUri = profilePhotoUri,
                    createdTimestamp = System.currentTimeMillis(),
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.updateCustomer(customer.copy(lastUpdatedTimestamp = System.currentTimeMillis()))
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            if (_selectedCustomer.value?.id == customer.id) {
                _selectedCustomer.value = null
            }
        }
    }

    fun toggleCollectToday(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.updateCustomer(customer.copy(collectToday = !customer.collectToday))
        }
    }

    // Transaction Actions
    fun addTransaction(
        customerId: Long,
        transactionType: String, // "I_PAID" or "I_RECEIVED"
        amount: Double,
        comment: String,
        category: String,
        paymentMode: String,
        dateTimestamp: Long = System.currentTimeMillis(),
        attachmentPath: String? = null
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                TransactionEntity(
                    customerId = customerId,
                    transactionType = transactionType,
                    amount = amount,
                    comment = comment.trim(),
                    category = category,
                    paymentMode = paymentMode,
                    dateTimestamp = dateTimestamp,
                    attachmentPath = attachmentPath
                )
            )
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    // Monthly Expense Actions
    fun addExpense(
        monthYear: String,
        title: String,
        category: String,
        amount: Double,
        notes: String = "",
        attachmentPath: String? = null
    ) {
        viewModelScope.launch {
            repository.insertExpense(
                MonthlyExpenseEntity(
                    monthYear = monthYear,
                    title = title.trim(),
                    category = category,
                    amount = amount,
                    notes = notes.trim(),
                    attachmentPath = attachmentPath
                )
            )
            syncMonthlyExpensesToLedger()
        }
    }

    fun updateExpense(expense: MonthlyExpenseEntity) {
        viewModelScope.launch {
            repository.updateExpense(expense)
            syncMonthlyExpensesToLedger()
        }
    }

    fun deleteExpense(expense: MonthlyExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            syncMonthlyExpensesToLedger()
        }
    }

    fun syncMonthlyExpensesToLedger() {
        viewModelScope.launch {
            val expenses = repository.allExpenses.firstOrNull() ?: emptyList()
            val grouped = expenses.groupBy { it.monthYear }
            val sdfInput = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val sdfOutput = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

            val currentCustomers = repository.allCustomers.firstOrNull() ?: emptyList()

            grouped.forEach { (monthKey, list) ->
                val totalExpense = list.sumOf { it.amount }
                val readableMonth = try {
                    val date = sdfInput.parse(monthKey)
                    if (date != null) sdfOutput.format(date) else monthKey
                } catch (e: Exception) {
                    monthKey
                }
                val customerName = "$readableMonth Deduction"
                val existingCustomer = currentCustomers.find { it.name.equals(customerName, ignoreCase = true) }

                if (totalExpense > 0) {
                    if (existingCustomer == null) {
                        val newCustId = repository.insertCustomer(
                            CustomerEntity(
                                name = customerName,
                                mobileNumber = "System Expense",
                                address = "Store Operational Expenses",
                                notes = "Auto-added monthly expense total for $readableMonth",
                                collectToday = false
                            )
                        )
                        repository.insertTransaction(
                            TransactionEntity(
                                customerId = newCustId,
                                transactionType = "I_PAID",
                                amount = totalExpense,
                                comment = "Total Store Expense ($readableMonth)",
                                category = "Expense",
                                paymentMode = "AUTO"
                            )
                        )
                    } else {
                        val txs = repository.getTransactionsForCustomer(existingCustomer.id).firstOrNull() ?: emptyList()
                        val tx = txs.firstOrNull()
                        if (tx != null && tx.amount != totalExpense) {
                            repository.updateTransaction(tx.copy(amount = totalExpense, transactionType = "I_PAID"))
                        }
                    }
                } else if (existingCustomer != null) {
                    repository.deleteCustomer(existingCustomer)
                }
            }
        }
    }

    fun addCustomCategory(name: String, colorHex: String = "#2E7D32") {
        viewModelScope.launch {
            repository.insertCategory(
                CategoryEntity(
                    name = name.trim(),
                    isCustom = true,
                    colorHex = colorHex
                )
            )
        }
    }

    // Share & Reminder Helpers
    fun getWhatsAppReminderText(customerName: String, pendingBalance: Double): String {
        val formattedAmount = String.format("₹%,.2f", Math.abs(pendingBalance))
        return if (pendingBalance > 0) {
            "Hello $customerName,\n\nThis is a friendly payment reminder from Daily Expense & Ledger. Your current pending balance is $formattedAmount.\n\nPlease make the payment at your earliest convenience via Cash / UPI.\n\nThank you!"
        } else {
            "Hello $customerName,\n\nThank you for your business! Your current balance with us is settled ($formattedAmount).\n\nThank you!"
        }
    }

    fun getSMSText(customerName: String, pendingBalance: Double): String {
        val formattedAmount = String.format("₹%,.2f", Math.abs(pendingBalance))
        return "Dear $customerName, your pending balance on Daily Expense & Ledger is $formattedAmount. Please settle at your convenience. Thanks!"
    }

    fun generateCSVExport(customerName: String, transactions: List<TransactionEntity>): String {
        val sb = StringBuilder()
        val totalPaid = transactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
        val totalReceived = transactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
        val netBalance = totalPaid - totalReceived

        val reportDateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        sb.append("====================================================\n")
        sb.append("   DAILY EXPENSE & LEDGER – CUSTOMER REPORT         \n")
        sb.append("====================================================\n")
        sb.append("Customer Name   : $customerName\n")
        sb.append("Generated On    : $reportDateStr\n")
        sb.append("Total Paid (Gave): ₹${String.format("%,.2f", totalPaid)}\n")
        sb.append("Total Received  : ₹${String.format("%,.2f", totalReceived)}\n")
        sb.append("Net Balance     : ₹${String.format("%,.2f", Math.abs(netBalance))} (${if (netBalance >= 0) "Customer Owes You" else "You Owe Customer"})\n")
        sb.append("====================================================\n\n")

        sb.append("Date & Time, Type, Amount (₹), Mode, Category, Running Balance (₹), Remarks\n")
        sb.append("--------------------------------------------------------------------------------\n")

        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        var currentRunningBalance = 0.0

        transactions.sortedBy { it.dateTimestamp }.forEach { tx ->
            val dateStr = sdf.format(Date(tx.dateTimestamp))
            val isPaid = tx.transactionType == "I_PAID"
            if (isPaid) currentRunningBalance += tx.amount else currentRunningBalance -= tx.amount

            val typeStr = if (isPaid) "I PAID (GAVE)" else "I RECEIVED (GOT)"
            val commentClean = if (tx.comment.isBlank()) "-" else tx.comment.replace(",", " ")
            sb.append("$dateStr, $typeStr, ${String.format("%.2f", tx.amount)}, ${tx.paymentMode}, ${tx.category}, ${String.format("%.2f", currentRunningBalance)}, $commentClean\n")
        }
        sb.append("--------------------------------------------------------------------------------\n")
        sb.append("Thank you for using Daily Expense & Ledger..!\n")
        return sb.toString()
    }

    private fun readBytesFromPathOrUri(context: android.content.Context, pathOrUri: String): ByteArray? {
        return com.example.utils.ImageUtils.readImageBytes(context, pathOrUri)
    }

    private fun compressAndEncodeToBase64(bytes: ByteArray): String? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            var sampleSize = 1
            val maxDimension = 1600
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                val widthRatio = Math.round(options.outWidth.toFloat() / maxDimension.toFloat())
                val heightRatio = Math.round(options.outHeight.toFloat() / maxDimension.toFloat())
                sampleSize = Math.max(widthRatio, heightRatio)
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            val compressedBytes = stream.toByteArray()
            bitmap.recycle()
            android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun imageStringToJsonArray(context: android.content.Context, multiPathString: String?): org.json.JSONArray {
        val jsonArray = org.json.JSONArray()
        if (multiPathString.isNullOrBlank()) return jsonArray
        val paths = multiPathString.split(Regex("[|,]")).map { it.trim() }.filter { it.isNotBlank() }
        paths.forEach { path ->
            val bytes = readBytesFromPathOrUri(context, path)
            if (bytes != null && bytes.isNotEmpty()) {
                val base64 = compressAndEncodeToBase64(bytes)
                if (!base64.isNullOrBlank()) {
                    jsonArray.put(base64)
                }
            }
        }
        return jsonArray
    }

    private fun restoreBase64JsonArrayToLocalPaths(context: android.content.Context, jsonArray: org.json.JSONArray?, subDir: String): String? {
        if (jsonArray == null || jsonArray.length() == 0) return null
        val savedPaths = mutableListOf<String>()

        for (i in 0 until jsonArray.length()) {
            val base64Str = jsonArray.optString(i, "").trim()
            if (base64Str.isNotBlank()) {
                val savedPath = com.example.utils.ImageUtils.compressAndSaveImage(context, base64Str, subDir)
                if (savedPath != null) {
                    savedPaths.add(savedPath)
                }
            }
        }
        return if (savedPaths.isNotEmpty()) savedPaths.joinToString("|") else null
    }

    private fun saveBase64ToLocalFile(base64Str: String?, subDir: String): String? {
        if (base64Str.isNullOrBlank()) return null
        val context = getApplication<android.app.Application>()
        return com.example.utils.ImageUtils.compressAndSaveImage(context, base64Str, subDir)
    }

    suspend fun exportAllDataJson(): String {
        val customers = repository.allCustomers.firstOrNull() ?: emptyList()
        val transactions = repository.allTransactions.firstOrNull() ?: emptyList()
        val expenses = repository.allExpenses.firstOrNull() ?: emptyList()

        val rootObj = org.json.JSONObject()
        val custArray = org.json.JSONArray()
        val txArray = org.json.JSONArray()
        val expArray = org.json.JSONArray()
        val context = getApplication<android.app.Application>()

        customers.forEach { c ->
            val obj = org.json.JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("mobileNumber", c.mobileNumber)
                put("address", c.address)
                put("email", c.email)
                put("notes", c.notes)
                put("profilePhotoUri", c.profilePhotoUri ?: "")
                put("collectToday", c.collectToday)
                put("collectDueDate", c.collectDueDate ?: 0L)
                put("lastUpdatedTimestamp", c.lastUpdatedTimestamp)

                val photoArray = imageStringToJsonArray(context, c.profilePhotoUri)
                if (photoArray.length() > 0) {
                    put("profilePhotoBase64List", photoArray)
                    put("profilePhotoBase64", photoArray.optString(0, ""))
                }
            }
            custArray.put(obj)
        }

        transactions.forEach { t ->
            val obj = org.json.JSONObject().apply {
                put("id", t.id)
                put("customerId", t.customerId)
                put("transactionType", t.transactionType)
                put("amount", t.amount)
                put("dateTimestamp", t.dateTimestamp)
                put("comment", t.comment)
                put("category", t.category)
                put("paymentMode", t.paymentMode)
                put("attachmentPath", t.attachmentPath ?: "")

                val attachArray = imageStringToJsonArray(context, t.attachmentPath)
                if (attachArray.length() > 0) {
                    put("attachmentBase64List", attachArray)
                    put("attachmentBase64", attachArray.optString(0, ""))
                }
            }
            txArray.put(obj)
        }

        expenses.forEach { exp ->
            val obj = org.json.JSONObject().apply {
                put("id", exp.id)
                put("monthYear", exp.monthYear)
                put("title", exp.title)
                put("category", exp.category)
                put("amount", exp.amount)
                put("dateTimestamp", exp.dateTimestamp)
                put("notes", exp.notes)
                put("attachmentPath", exp.attachmentPath ?: "")

                val attachArray = imageStringToJsonArray(context, exp.attachmentPath)
                if (attachArray.length() > 0) {
                    put("attachmentBase64List", attachArray)
                    put("attachmentBase64", attachArray.optString(0, ""))
                }
            }
            expArray.put(obj)
        }

        rootObj.put("version", 2)
        rootObj.put("customers", custArray)
        rootObj.put("transactions", txArray)
        rootObj.put("monthlyExpenses", expArray)

        return rootObj.toString(2)
    }

    fun importAllDataJsonAsync(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = importAllDataJson(jsonString)
            onResult(result)
        }
    }

    suspend fun importAllDataJson(jsonString: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val rootObj = org.json.JSONObject(jsonString)
            val custArray = rootObj.getJSONArray("customers")
            val txArray = rootObj.getJSONArray("transactions")
            val context = getApplication<android.app.Application>()

            val idMap = mutableMapOf<Long, Long>()

            // Fetch current existing customers & transactions in bulk to prevent creating duplicates or doing thousands of DB queries
            val existingCustomers = repository.allCustomers.firstOrNull() ?: emptyList()
            val existingTxs = repository.allTransactions.firstOrNull() ?: emptyList()

            // Pre-build set of existing transactions for O(1) duplicate checks
            val existingTxKeySet = mutableSetOf<String>()
            existingTxs.forEach { tx ->
                val key = "${tx.customerId}_${tx.transactionType}_${(tx.amount * 100).toLong()}_${tx.comment.trim()}"
                existingTxKeySet.add(key)
            }

            for (i in 0 until custArray.length()) {
                val obj = custArray.getJSONObject(i)
                val oldId = obj.optLong("id", 0L)
                val nameVal = obj.getString("name").trim()
                val mobileVal = obj.optString("mobileNumber", "").trim()
                val dueDateVal = obj.optLong("collectDueDate", 0L)

                val restoredPhotoUri = when {
                    obj.has("profilePhotoBase64List") -> restoreBase64JsonArrayToLocalPaths(context, obj.getJSONArray("profilePhotoBase64List"), "profile_photos")
                    obj.has("profilePhotoBase64") && obj.getString("profilePhotoBase64").isNotBlank() -> saveBase64ToLocalFile(obj.getString("profilePhotoBase64"), "profile_photos")
                    else -> {
                        val rawUri = obj.optString("profilePhotoUri", "").trim()
                        if (rawUri.isNotBlank() && java.io.File(rawUri).exists()) rawUri else null
                    }
                }

                // Check if customer already exists in DB (matching by name case-insensitive OR mobile number)
                val matchingCustomer = existingCustomers.find { existing ->
                    existing.name.trim().equals(nameVal, ignoreCase = true) ||
                    (mobileVal.isNotBlank() && existing.mobileNumber.trim() == mobileVal)
                }

                val targetCustId = if (matchingCustomer != null) {
                    if (matchingCustomer.profilePhotoUri.isNullOrBlank() && restoredPhotoUri != null) {
                        repository.updateCustomer(matchingCustomer.copy(profilePhotoUri = restoredPhotoUri))
                    }
                    matchingCustomer.id
                } else {
                    repository.insertCustomer(
                        CustomerEntity(
                            name = nameVal,
                            mobileNumber = mobileVal,
                            address = obj.optString("address", ""),
                            email = obj.optString("email", ""),
                            notes = obj.optString("notes", ""),
                            profilePhotoUri = restoredPhotoUri,
                            collectToday = obj.optBoolean("collectToday", false),
                            collectDueDate = if (dueDateVal > 0) dueDateVal else null,
                            lastUpdatedTimestamp = obj.optLong("lastUpdatedTimestamp", System.currentTimeMillis())
                        )
                    )
                }

                if (oldId != 0L) {
                    idMap[oldId] = targetCustId
                }
            }

            val newTransactionsToInsert = mutableListOf<TransactionEntity>()

            for (i in 0 until txArray.length()) {
                val obj = txArray.getJSONObject(i)
                val oldCustId = obj.getLong("customerId")
                val targetCustId = idMap[oldCustId] ?: oldCustId

                val txType = obj.getString("transactionType")
                val txAmount = obj.getDouble("amount")
                val txDate = obj.optLong("dateTimestamp", System.currentTimeMillis())
                val txComment = obj.optString("comment", "").trim()
                val txCategory = obj.optString("category", "General")
                val txPaymentMode = obj.optString("paymentMode", "CASH")

                val restoredAttachPath = when {
                    obj.has("attachmentBase64List") -> restoreBase64JsonArrayToLocalPaths(context, obj.getJSONArray("attachmentBase64List"), "attachments")
                    obj.has("attachmentBase64") && obj.getString("attachmentBase64").isNotBlank() -> saveBase64ToLocalFile(obj.getString("attachmentBase64"), "attachments")
                    else -> {
                        val rawPath = obj.optString("attachmentPath", "").trim()
                        if (rawPath.isNotBlank() && java.io.File(rawPath).exists()) rawPath else null
                    }
                }

                val txKey = "${targetCustId}_${txType}_${(txAmount * 100).toLong()}_${txComment}"

                if (!existingTxKeySet.contains(txKey)) {
                    existingTxKeySet.add(txKey) // Mark as added in batch to avoid duplicate within JSON itself
                    newTransactionsToInsert.add(
                        TransactionEntity(
                            customerId = targetCustId,
                            transactionType = txType,
                            amount = txAmount,
                            dateTimestamp = txDate,
                            comment = txComment,
                            category = txCategory,
                            paymentMode = txPaymentMode,
                            attachmentPath = restoredAttachPath
                        )
                    )
                }
            }

            // Insert transactions in chunks of 200 for optimal performance
            newTransactionsToInsert.chunked(200).forEach { chunk ->
                repository.insertTransactionsBatch(chunk)
            }

            // Restore Monthly Expenses if present in JSON
            if (rootObj.has("monthlyExpenses")) {
                val expArray = rootObj.getJSONArray("monthlyExpenses")
                val existingExpenses = repository.allExpenses.firstOrNull() ?: emptyList()
                val existingExpKeySet = existingExpenses.map { "${it.monthYear}_${it.title.trim()}_${(it.amount * 100).toLong()}" }.toSet()

                for (i in 0 until expArray.length()) {
                    val obj = expArray.getJSONObject(i)
                    val monthYear = obj.optString("monthYear", "")
                    val title = obj.optString("title", "").trim()
                    val category = obj.optString("category", "General")
                    val amount = obj.optDouble("amount", 0.0)
                    val dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis())
                    val notes = obj.optString("notes", "")

                    val restoredAttachPath = when {
                        obj.has("attachmentBase64List") -> restoreBase64JsonArrayToLocalPaths(context, obj.getJSONArray("attachmentBase64List"), "attachments")
                        obj.has("attachmentBase64") && obj.getString("attachmentBase64").isNotBlank() -> saveBase64ToLocalFile(obj.getString("attachmentBase64"), "attachments")
                        else -> {
                            val rawPath = obj.optString("attachmentPath", "").trim()
                            if (rawPath.isNotBlank() && java.io.File(rawPath).exists()) rawPath else null
                        }
                    }

                    val key = "${monthYear}_${title}_${(amount * 100).toLong()}"
                    if (title.isNotBlank() && amount > 0.0 && !existingExpKeySet.contains(key)) {
                        repository.insertExpense(
                            MonthlyExpenseEntity(
                                monthYear = monthYear,
                                title = title,
                                category = category,
                                amount = amount,
                                dateTimestamp = dateTimestamp,
                                notes = notes,
                                attachmentPath = restoredAttachPath
                            )
                        )
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
