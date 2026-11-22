package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.data.*
import com.example.utils.SummaryAnchor
import com.example.utils.SummaryGranularity
import com.example.utils.SummaryPeriodHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val totalCustomersCount: Int = 0
)

data class RecentTransactionEntry(
    val transaction: TransactionEntity,
    val customer: CustomerEntity
)

data class PdfImportProgressState(
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentCustomerName: String = ""
)

data class SummaryChartPoint(
    val bucketKey: String,
    val xLabel: String,
    val fullLabel: String,
    val totalPaid: Double,
    val totalReceived: Double,
    val netChange: Double,
    val cumulativeBalance: Double,
    val entryCount: Int,
    val isCurrentOrSelected: Boolean = false,
    val isHighest: Boolean = false,
    val isLowest: Boolean = false
)

data class SummaryPeriodOverview(
    val totalPaid: Double = 0.0,
    val totalReceived: Double = 0.0,
    val netBalance: Double = 0.0,
    val entryCount: Int = 0,
    val customerCount: Int = 0,
    val highestLabel: String? = null,
    val highestAmount: Double = 0.0
)

data class SummaryChartUiState(
    val isLoading: Boolean = true,
    val granularity: SummaryGranularity = SummaryGranularity.MONTHLY,
    val anchor: SummaryAnchor = SummaryAnchor.today(),
    val periodLabel: String = "",
    val canStepForward: Boolean = false,
    val overview: SummaryPeriodOverview = SummaryPeriodOverview(),
    val points: List<SummaryChartPoint> = emptyList(),
    val selectedBucketKey: String? = null,
    val drillDownBucketKey: String? = null,
    val drillDownLabel: String? = null
)

data class SummaryTopOverviewState(
    val selectedTab: Int = 0, // 0=Overall(Today/ThisMonth/ThisYear), 1=Daily, 2=Monthly, 3=Yearly
    val anchor: SummaryAnchor = SummaryAnchor.today(),
    val dailyTotals: SummaryPeriodTotalsTuple = SummaryPeriodTotalsTuple(0.0, 0.0, 0, 0),
    val monthlyTotals: SummaryPeriodTotalsTuple = SummaryPeriodTotalsTuple(0.0, 0.0, 0, 0),
    val yearlyTotals: SummaryPeriodTotalsTuple = SummaryPeriodTotalsTuple(0.0, 0.0, 0, 0)
)

sealed class SummaryEntryListItem {
    data class GroupHeader(
        val groupKey: String,
        val title: String,
        val granularity: SummaryGranularity
    ) : SummaryEntryListItem()

    data class EntryRow(
        val tuple: SummaryTransactionTuple
    ) : SummaryEntryListItem()
}

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

    // Isolated Firebase Cloud Backup & Google Account connection states
    val firebaseBackupStatus: StateFlow<com.example.utils.FirebaseBackupStatus> =
        com.example.utils.AppPreferencesManager.firebaseBackupStatusFlow

    private val _firebaseBackupsList = MutableStateFlow<List<com.example.data.remote.FirebaseBackupSnapshot>>(emptyList())
    val firebaseBackupsList: StateFlow<List<com.example.data.remote.FirebaseBackupSnapshot>> = _firebaseBackupsList.asStateFlow()

    private val _isFirebaseBusy = MutableStateFlow(false)
    val isFirebaseBusy: StateFlow<Boolean> = _isFirebaseBusy.asStateFlow()

    private val _firebaseConnectionMessage = MutableStateFlow<String?>(null)
    val firebaseConnectionMessage: StateFlow<String?> = _firebaseConnectionMessage.asStateFlow()

    private val _googleAccountInfo = MutableStateFlow<com.example.auth.GoogleSignInAccountInfo?>(
        com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()
    )
    val googleAccountInfo: StateFlow<com.example.auth.GoogleSignInAccountInfo?> = _googleAccountInfo.asStateFlow()

    val isNetworkOnline: StateFlow<Boolean> = com.example.utils.BackupManager.isOnlineFlow
    val lastLocalBackupTime: StateFlow<Long> = com.example.utils.BackupManager.lastLocalBackupTimeFlow

    data class PendingProfileRestore(
        val name: String,
        val businessName: String,
        val phone: String,
        val email: String,
        val profileImageBase64: String?
    )

    data class FullBackupRestoreSummary(
        val success: Boolean,
        val customersAdded: Int = 0,
        val transactionsAdded: Int = 0,
        val expensesAdded: Int = 0,
        val hasProfileInBackup: Boolean = false,
        val errorMessage: String? = null
    )

    private val _pendingProfileRestore = MutableStateFlow<PendingProfileRestore?>(null)
    val pendingProfileRestore: StateFlow<PendingProfileRestore?> = _pendingProfileRestore.asStateFlow()

    fun refreshFirebaseState() {
        val account = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()
        _googleAccountInfo.value = account
        val ctx = getApplication<Application>()
        if (account == null) {
            _firebaseBackupsList.value = emptyList()
            com.example.utils.AppPreferencesManager.refreshFirebaseStatus(
                context = ctx,
                isSignedIn = false,
                userEmail = null,
                snapshotCount = 0
            )
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (com.google.firebase.FirebaseApp.getApps(ctx).isNotEmpty()) {
                    val repo = com.example.data.remote.FirebaseBackupRepository(ctx)
                    val listRes = repo.listBackups()
                    listRes.onSuccess { list ->
                        _firebaseBackupsList.value = list
                        com.example.utils.AppPreferencesManager.refreshFirebaseStatus(
                            context = ctx,
                            isSignedIn = true,
                            userEmail = account.email,
                            snapshotCount = list.size
                        )
                    }.onFailure { err ->
                        com.example.utils.AppPreferencesManager.refreshFirebaseStatus(
                            context = ctx,
                            isSignedIn = true,
                            userEmail = account.email
                        )
                        _firebaseConnectionMessage.value = err.localizedMessage
                    }
                }
            } catch (e: Exception) {
                _firebaseConnectionMessage.value = e.localizedMessage
            }
        }
    }

    fun onGoogleAccountSignedIn() {
        val account = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()
        _googleAccountInfo.value = account
        val ctx = getApplication<Application>()
        com.example.utils.AppPreferencesManager.refreshFirebaseStatus(
            context = ctx,
            isSignedIn = account != null,
            userEmail = account?.email
        )
        refreshFirebaseState()
    }

    fun signOutGoogleAccount(onDone: (() -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ctx = getApplication<Application>()
            com.example.auth.GoogleAuthHelper.signOut(ctx)
            _googleAccountInfo.value = null
            _firebaseBackupsList.value = emptyList()
            _firebaseConnectionMessage.value = "Signed out of Google cloud backup account. Local ledger stays active offline."
            com.example.utils.AppPreferencesManager.refreshFirebaseStatus(
                context = ctx,
                isSignedIn = false,
                userEmail = null,
                snapshotCount = 0
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone?.invoke()
            }
        }
    }

    fun testFirebaseConnection(onComplete: ((Boolean, String) -> Unit)? = null) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isFirebaseBusy.value = true
            try {
                val repo = com.example.data.remote.FirebaseBackupRepository(ctx)
                val res = repo.testConnection()
                val ok = res.isSuccess
                val msg = res.getOrElse { it.localizedMessage ?: "Firebase connection test failed" }
                _firebaseConnectionMessage.value = msg
                if (ok) refreshFirebaseState()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete?.invoke(ok, msg)
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Firebase connection error"
                _firebaseConnectionMessage.value = msg
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete?.invoke(false, msg)
                }
            } finally {
                _isFirebaseBusy.value = false
            }
        }
    }

    fun syncFirebaseBackupNow(onComplete: ((Boolean, String) -> Unit)? = null) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isFirebaseBusy.value = true
            try {
                if (!com.example.utils.BackupManager.isFirebaseSignedIn(ctx)) {
                    val msg = "Please sign in with Google in Settings -> Google Account first"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete?.invoke(false, msg)
                    }
                    return@launch
                }
                if (!com.example.utils.BackupManager.isNetworkAvailable(ctx)) {
                    com.example.utils.AppPreferencesManager.setPendingFirebaseBackup(ctx, true)
                    com.example.utils.AppPreferencesManager.updateFirebaseStatus(
                        context = ctx,
                        state = com.example.utils.BackupState.PENDING_OFFLINE,
                        errorMessage = "Offline: queued for automatic upload",
                        userEmail = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()?.email
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete?.invoke(false, "Offline: Firebase sync queued for reconnection")
                    }
                    return@launch
                }
                com.example.utils.AppPreferencesManager.updateFirebaseStatus(
                    context = ctx,
                    state = com.example.utils.BackupState.IN_PROGRESS,
                    errorMessage = null,
                    userEmail = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()?.email
                )
                val pkg = com.example.utils.BackupManager.exportFullBackupPackage(ctx)
                if (pkg.jsonString.isBlank()) {
                    throw IllegalStateException("Backup export is empty")
                }
                val repo = com.example.data.remote.FirebaseBackupRepository(ctx)
                val res = repo.uploadDailyBackup(
                    jsonString = pkg.jsonString,
                    customerCount = pkg.customerCount,
                    transactionCount = pkg.transactionCount,
                    hasUserProfile = pkg.hasUserProfile
                )
                if (res.isSuccess) {
                    val snapshots = repo.listBackups().getOrNull() ?: emptyList()
                    _firebaseBackupsList.value = snapshots
                    val now = System.currentTimeMillis()
                    com.example.utils.AppPreferencesManager.setPendingFirebaseBackup(ctx, false)
                    com.example.utils.AppPreferencesManager.updateFirebaseStatus(
                        context = ctx,
                        state = com.example.utils.BackupState.SUCCESS,
                        timestamp = now,
                        errorMessage = null,
                        userEmail = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()?.email,
                        snapshotCount = snapshots.size
                    )
                    com.example.utils.BackupManager.markAllCloudSyncedIfReady(
                        ctx,
                        driveSucceeded = false,
                        firebaseSucceeded = true
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete?.invoke(true, "Synced to Firebase Firestore (${snapshots.size} backup(s))")
                    }
                } else {
                    val errMsg = res.exceptionOrNull()?.localizedMessage ?: "Firebase upload failed"
                    com.example.utils.AppPreferencesManager.setPendingFirebaseBackup(ctx, true)
                    com.example.utils.AppPreferencesManager.updateFirebaseStatus(
                        context = ctx,
                        state = com.example.utils.BackupState.FAILED,
                        errorMessage = errMsg,
                        userEmail = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo()?.email
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete?.invoke(false, errMsg)
                    }
                }
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: "Firebase backup failed"
                com.example.utils.AppPreferencesManager.updateFirebaseStatus(
                    context = ctx,
                    state = com.example.utils.BackupState.FAILED,
                    errorMessage = errMsg
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete?.invoke(false, errMsg)
                }
            } finally {
                _isFirebaseBusy.value = false
            }
        }
    }

    fun restoreFirebaseBackup(
        backupId: String,
        authViewModel: AuthViewModel?,
        onComplete: (FullBackupRestoreSummary) -> Unit
    ) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isFirebaseBusy.value = true
            try {
                val repo = com.example.data.remote.FirebaseBackupRepository(ctx)
                val downloadRes = repo.downloadBackupJson(backupId)
                if (downloadRes.isFailure) {
                    val summary = FullBackupRestoreSummary(
                        success = false,
                        errorMessage = downloadRes.exceptionOrNull()?.localizedMessage ?: "Download failed"
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onComplete(summary)
                    }
                    return@launch
                }
                val json = downloadRes.getOrThrow()
                val summary = performFullBackupImportInternal(json, authViewModel)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(summary)
                }
            } catch (e: Exception) {
                val summary = FullBackupRestoreSummary(success = false, errorMessage = e.localizedMessage)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(summary)
                }
            } finally {
                _isFirebaseBusy.value = false
            }
        }
    }

    fun refreshDailyBackupsList() {
        com.example.utils.BackupManager.refreshBackupStatus(getApplication())
        val list = com.example.utils.BackupManager.getDailyBackups(getApplication())
        _dailyBackupsList.value = list
        refreshDriveBackupsList()
        refreshFirebaseState()
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
                    com.example.utils.BackupManager.updateBackupStatus(
                        getApplication(),
                        com.example.utils.BackupState.SUCCESS,
                        now,
                        null
                    )
                    com.example.utils.BackupManager.markAllCloudSyncedIfReady(
                        getApplication(),
                        driveSucceeded = true,
                        firebaseSucceeded = false
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

    val pendingSyncCount: StateFlow<Int> = combine(
        repository.observeUnsyncedEntriesCount(),
        com.example.utils.BackupManager.pendingOfflineMutationsFlow,
        hasPendingDriveBackup
    ) { unsyncedDb, offlineMutations, pendingDrive ->
        val pendingFirebase = com.example.utils.AppPreferencesManager.hasPendingFirebaseBackup(getApplication())
        val total = unsyncedDb + offlineMutations
        if (total == 0 && (pendingDrive || pendingFirebase)) 1 else total
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val showDashboardBackupAlert: StateFlow<Boolean> = combine(
        driveFolderName,
        googleAccountInfo,
        pendingSyncCount
    ) { driveFolder, accountInfo, pendingCount ->
        com.example.utils.BackupManager.shouldShowBackupAlert(
            googleConnected = driveFolder != null,
            firebaseConnected = accountInfo != null,
            pendingCount = pendingCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.utils.BackupManager.shouldShowBackupAlert(
            googleConnected = com.example.utils.BackupManager.getDriveFolderName(application) != null,
            firebaseConnected = com.example.auth.GoogleAuthHelper.getCurrentAccountInfo() != null,
            pendingCount = 0
        )
    )

    // 1. Independent Customer List Search State (Customer List page only)
    private val _customerSearchQuery = MutableStateFlow("")
    val customerSearchQuery: StateFlow<String> = _customerSearchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val debouncedCustomerSearch = _customerSearchQuery.debounce { q ->
        if (q.isBlank()) 0L else 250L
    }

    // 2. Independent Transaction Search State (Customer Details page only)
    private val _activeCustomerDetailId = MutableStateFlow<Long?>(null)
    private val _transactionSearchQuery = MutableStateFlow("")
    val transactionSearchQuery: StateFlow<String> = _transactionSearchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val debouncedTxSearch = _transactionSearchQuery.debounce { q ->
        if (q.isBlank()) 0L else 250L
    }

    private val _selectedFilter = MutableStateFlow(CustomerFilter.ALL)
    val selectedFilter: StateFlow<CustomerFilter> = _selectedFilter.asStateFlow()

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
        debouncedCustomerSearch,
        _selectedFilter
    ) { summaries, query, filter ->
        val cleanQuery = query.trim()
        summaries.filter { summary ->
            // Customer search: Matches against customer fields only (name, mobileNumber, address, email, notes).
            // Strictly does not search inside transactions (amount, comment, category, payment mode, etc.).
            val matchesQuery = cleanQuery.isBlank() ||
                    summary.customer.name.contains(cleanQuery, ignoreCase = true) ||
                    summary.customer.mobileNumber.contains(cleanQuery, ignoreCase = true) ||
                    summary.customer.address.contains(cleanQuery, ignoreCase = true) ||
                    summary.customer.email.contains(cleanQuery, ignoreCase = true) ||
                    summary.customer.notes.contains(cleanQuery, ignoreCase = true)

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

        DashboardSummary(
            totalYouWillReceive = receive,
            totalYouNeedToPay = pay,
            totalExpenses = expenses,
            totalBalance = receive - pay,
            totalCustomersCount = summaries.size
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

    val recentTransactions: StateFlow<List<RecentTransactionEntry>> = combine(
        allTransactions,
        customerSummaries
    ) { transactions, summaries ->
        val customerMap = summaries.associate { it.customer.id to it.customer }
        transactions.mapNotNull { tx ->
            customerMap[tx.customerId]?.let { cust ->
                RecentTransactionEntry(transaction = tx, customer = cust)
            }
        }.take(30)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allExpenses: StateFlow<List<MonthlyExpenseEntity>> = repository.allExpenses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredCustomerTransactions: StateFlow<List<TransactionEntity>> = combine(
        _activeCustomerDetailId.flatMapLatest { custId ->
            if (custId != null) repository.getTransactionsForCustomer(custId) else flowOf(emptyList())
        },
        debouncedTxSearch
    ) { txList, query ->
        val clean = query.trim().lowercase()
        if (clean.isBlank()) {
            txList
        } else {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            txList.filter { tx ->
                // Transaction search: Searches only transactions of the active customer.
                // Matches against amount, comment, category, payment mode, date, and status.
                val commentMatches = tx.comment.lowercase().contains(clean)
                val categoryMatches = tx.category.lowercase().contains(clean)
                val modeMatches = tx.paymentMode.lowercase().contains(clean)
                val statusMatches = tx.status.lowercase().contains(clean)
                val typeMatches = if (clean == "paid" || clean == "gave") tx.transactionType == "I_PAID"
                                  else if (clean == "received" || clean == "got") tx.transactionType == "I_RECEIVED"
                                  else false
                val amountMatches = tx.amount.toString().contains(clean) ||
                        String.format(Locale.US, "%.2f", tx.amount).contains(clean) ||
                        String.format(Locale.US, "%.0f", tx.amount).contains(clean)
                val dateStr = sdf.format(Date(tx.dateTimestamp)).lowercase()
                val dateMatches = dateStr.contains(clean)

                commentMatches || categoryMatches || modeMatches || statusMatches || amountMatches || dateMatches || typeMatches
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setCustomerSearchQuery(query: String) {
        _customerSearchQuery.value = query
    }

    fun clearCustomerSearchQuery() {
        _customerSearchQuery.value = ""
    }

    fun setTransactionSearchQuery(query: String) {
        _transactionSearchQuery.value = query
    }

    fun clearTransactionSearchQuery() {
        _transactionSearchQuery.value = ""
    }

    fun setActiveCustomerDetailId(customerId: Long?) {
        _activeCustomerDetailId.value = customerId
        _transactionSearchQuery.value = "" // Always start with empty search when entering/re-entering
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

    fun getTransactionsForCustomer(customerId: Long): Flow<List<TransactionEntity>> {
        return repository.getTransactionsForCustomer(customerId)
    }

    fun getCustomerFlow(customerId: Long): Flow<CustomerEntity?> {
        return repository.getCustomerById(customerId)
    }

    // ---- Summary Tab (SQLite half-open boundaries + Graph + Paging 3 Entries) ----
    private val _summaryTopTab = MutableStateFlow(0)
    private val _summaryGranularity = MutableStateFlow(SummaryGranularity.MONTHLY)
    private val _summaryAnchor = MutableStateFlow(SummaryAnchor.today())
    private val _summarySelectedBucketKey = MutableStateFlow<String?>(null)
    private val _summaryDrillDownKey = MutableStateFlow<String?>(null)

    fun setSummaryTopTab(tab: Int) {
        _summaryTopTab.value = tab.coerceIn(0, 3)
        when (tab) {
            1 -> setSummaryGranularity(SummaryGranularity.DAILY)
            2 -> setSummaryGranularity(SummaryGranularity.MONTHLY)
            3 -> setSummaryGranularity(SummaryGranularity.YEARLY)
        }
    }

    fun setSummaryGranularity(granularity: SummaryGranularity) {
        _summaryGranularity.value = granularity
        _summarySelectedBucketKey.value = null
        _summaryDrillDownKey.value = null
    }

    fun setSummaryAnchor(anchor: SummaryAnchor) {
        _summaryAnchor.value = SummaryPeriodHelper.clampToToday(anchor)
        _summarySelectedBucketKey.value = null
        _summaryDrillDownKey.value = null
    }

    fun stepSummaryPeriod(delta: Int) {
        val current = _summaryAnchor.value
        val next = SummaryPeriodHelper.stepAnchor(_summaryGranularity.value, current, delta)
        _summaryAnchor.value = next
        _summarySelectedBucketKey.value = null
        _summaryDrillDownKey.value = null
    }

    fun resetSummaryToCurrent() {
        _summaryAnchor.value = SummaryAnchor.today()
        _summarySelectedBucketKey.value = null
        _summaryDrillDownKey.value = null
    }

    fun selectSummaryChartBucket(bucketKey: String?) {
        _summarySelectedBucketKey.value = if (_summarySelectedBucketKey.value == bucketKey) null else bucketKey
    }

    fun openChartBucketEntries(bucketKey: String) {
        val range = SummaryPeriodHelper.parseBucketKeyToRange(bucketKey)
        if (range != null) {
            _summaryDrillDownKey.value = bucketKey
        }
    }

    fun clearChartDrillDown() {
        _summaryDrillDownKey.value = null
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summaryTopOverviewState: StateFlow<SummaryTopOverviewState> = combine(
        _summaryTopTab,
        _summaryAnchor
    ) { tab, anchor ->
        tab to anchor
    }.flatMapLatest { (tab, anchor) ->
        val effectiveAnchor = if (tab == 0) SummaryAnchor.today() else anchor
        val dayRange = SummaryPeriodHelper.dayRange(effectiveAnchor.year, effectiveAnchor.monthZeroBased, effectiveAnchor.dayOfMonth)
        val monthRange = SummaryPeriodHelper.monthRange(effectiveAnchor.year, effectiveAnchor.monthZeroBased)
        val yearRange = SummaryPeriodHelper.yearRange(effectiveAnchor.year)
        combine(
            repository.observeSummaryPeriodTotals(dayRange.startMillis, dayRange.endMillis),
            repository.observeSummaryPeriodTotals(monthRange.startMillis, monthRange.endMillis),
            repository.observeSummaryPeriodTotals(yearRange.startMillis, yearRange.endMillis)
        ) { d, m, y ->
            SummaryTopOverviewState(
                selectedTab = tab,
                anchor = anchor,
                dailyTotals = d,
                monthlyTotals = m,
                yearlyTotals = y
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SummaryTopOverviewState()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summaryChartState: StateFlow<SummaryChartUiState> = combine(
        _summaryGranularity,
        _summaryAnchor,
        _summarySelectedBucketKey,
        _summaryDrillDownKey
    ) { granularity, anchor, selectedKey, drillKey ->
        Quad(granularity, anchor, selectedKey, drillKey)
    }.flatMapLatest { (granularity, anchor, selectedKey, drillKey) ->
        val overviewRange = SummaryPeriodHelper.periodRange(granularity, anchor)
        val chartRange = SummaryPeriodHelper.chartRange(granularity, anchor)
        val bucketsFlow = when (granularity) {
            SummaryGranularity.DAILY -> repository.observeDailyBuckets(chartRange.startMillis, chartRange.endMillis)
            SummaryGranularity.MONTHLY -> repository.observeMonthlyBuckets(chartRange.startMillis, chartRange.endMillis)
            SummaryGranularity.YEARLY -> repository.observeYearlyBuckets(chartRange.startMillis, chartRange.endMillis)
        }
        combine(
            repository.observeSummaryPeriodTotals(overviewRange.startMillis, overviewRange.endMillis),
            bucketsFlow
        ) { overviewTotals, rawBuckets ->
            buildSummaryChartUiState(
                granularity = granularity,
                anchor = anchor,
                selectedBucketKey = selectedKey,
                drillDownBucketKey = drillKey,
                overviewTotals = overviewTotals,
                rawBuckets = rawBuckets
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SummaryChartUiState(
            isLoading = true,
            periodLabel = SummaryPeriodHelper.formatPeriodLabel(SummaryGranularity.MONTHLY, SummaryAnchor.today())
        )
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summaryGroupSubtotalsMap: StateFlow<Map<String, SummaryChartBucketTuple>> = combine(
        _summaryGranularity,
        _summaryAnchor,
        _summaryDrillDownKey
    ) { granularity, anchor, drillKey ->
        Triple(granularity, anchor, drillKey)
    }.flatMapLatest { (granularity, anchor, drillKey) ->
        val range = drillKey?.let { SummaryPeriodHelper.parseBucketKeyToRange(it) }
            ?: SummaryPeriodHelper.periodRange(granularity, anchor)
        val effectiveGranularity = when {
            drillKey == null -> granularity
            drillKey.count { it == '-' } == 2 -> SummaryGranularity.DAILY
            drillKey.count { it == '-' } == 1 -> SummaryGranularity.MONTHLY
            else -> SummaryGranularity.YEARLY
        }
        when (effectiveGranularity) {
            SummaryGranularity.DAILY -> flowOf(emptyMap())
            SummaryGranularity.MONTHLY -> repository.observeDailyBuckets(range.startMillis, range.endMillis)
                .map { list -> list.associateBy { it.bucketKey } }
            SummaryGranularity.YEARLY -> repository.observeMonthlyBuckets(range.startMillis, range.endMillis)
                .map { list -> list.associateBy { it.bucketKey } }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summaryEntriesPagedFlow: Flow<PagingData<SummaryEntryListItem>> = combine(
        _summaryGranularity,
        _summaryAnchor,
        _summaryDrillDownKey
    ) { granularity, anchor, drillKey ->
        Triple(granularity, anchor, drillKey)
    }.flatMapLatest { (granularity, anchor, drillKey) ->
        val range = drillKey?.let { SummaryPeriodHelper.parseBucketKeyToRange(it) }
            ?: SummaryPeriodHelper.periodRange(granularity, anchor)
        val effectiveGranularity = when {
            drillKey == null -> granularity
            drillKey.count { it == '-' } == 2 -> SummaryGranularity.DAILY
            drillKey.count { it == '-' } == 1 -> SummaryGranularity.MONTHLY
            else -> SummaryGranularity.YEARLY
        }
        Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            repository.getSummaryTransactionsPaged(range.startMillis, range.endMillis)
        }.flow.map { pagingData ->
            val rows = pagingData.map { tuple -> SummaryEntryListItem.EntryRow(tuple) }
            when (effectiveGranularity) {
                SummaryGranularity.DAILY -> rows.map { it as SummaryEntryListItem }
                SummaryGranularity.MONTHLY -> rows.insertSeparators { before: SummaryEntryListItem.EntryRow?, after: SummaryEntryListItem.EntryRow? ->
                    if (after == null) return@insertSeparators null
                    val afterDay = SummaryPeriodHelper.localDayKeyFromMillis(after.tuple.dateTimestamp)
                    val beforeDay = before?.let { SummaryPeriodHelper.localDayKeyFromMillis(it.tuple.dateTimestamp) }
                    if (beforeDay != afterDay) {
                        SummaryEntryListItem.GroupHeader(
                            groupKey = afterDay,
                            title = SummaryPeriodHelper.formatDayHeaderLabel(afterDay),
                            granularity = SummaryGranularity.MONTHLY
                        )
                    } else null
                }
                SummaryGranularity.YEARLY -> rows.insertSeparators { before: SummaryEntryListItem.EntryRow?, after: SummaryEntryListItem.EntryRow? ->
                    if (after == null) return@insertSeparators null
                    val afterMonth = SummaryPeriodHelper.localMonthKeyFromMillis(after.tuple.dateTimestamp)
                    val beforeMonth = before?.let { SummaryPeriodHelper.localMonthKeyFromMillis(it.tuple.dateTimestamp) }
                    if (beforeMonth != afterMonth) {
                        SummaryEntryListItem.GroupHeader(
                            groupKey = afterMonth,
                            title = SummaryPeriodHelper.formatMonthHeaderLabel(afterMonth),
                            granularity = SummaryGranularity.YEARLY
                        )
                    } else null
                }
            }
        }
    }.cachedIn(viewModelScope)

    private fun buildSummaryChartUiState(
        granularity: SummaryGranularity,
        anchor: SummaryAnchor,
        selectedBucketKey: String?,
        drillDownBucketKey: String?,
        overviewTotals: SummaryPeriodTotalsTuple,
        rawBuckets: List<SummaryChartBucketTuple>
    ): SummaryChartUiState {
        val bucketByKey = rawBuckets.associateBy { it.bucketKey }
        val today = SummaryAnchor.today()
        val monthShortNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        val basePoints: List<SummaryChartPoint> = when (granularity) {
            SummaryGranularity.DAILY -> {
                val daysCount = SummaryPeriodHelper.daysInMonth(anchor.year, anchor.monthZeroBased)
                val selectedDayKey = SummaryPeriodHelper.dayBucketKey(anchor.year, anchor.monthZeroBased, anchor.dayOfMonth)
                var runningCum = 0.0
                (1..daysCount).map { day ->
                    val key = SummaryPeriodHelper.dayBucketKey(anchor.year, anchor.monthZeroBased, day)
                    val b = bucketByKey[key]
                    val paid = b?.totalPaid ?: 0.0
                    val rec = b?.totalReceived ?: 0.0
                    val net = rec - paid
                    runningCum += net
                    SummaryChartPoint(
                        bucketKey = key,
                        xLabel = day.toString(),
                        fullLabel = SummaryPeriodHelper.formatDayHeaderLabel(key),
                        totalPaid = paid,
                        totalReceived = rec,
                        netChange = net,
                        cumulativeBalance = runningCum,
                        entryCount = b?.entryCount ?: 0,
                        isCurrentOrSelected = (key == selectedDayKey)
                    )
                }
            }
            SummaryGranularity.MONTHLY -> {
                val activeMonthKey = SummaryPeriodHelper.monthBucketKey(anchor.year, anchor.monthZeroBased)
                var runningCum = 0.0
                (0..11).map { m ->
                    val key = SummaryPeriodHelper.monthBucketKey(anchor.year, m)
                    val b = bucketByKey[key]
                    val paid = b?.totalPaid ?: 0.0
                    val rec = b?.totalReceived ?: 0.0
                    val net = rec - paid
                    runningCum += net
                    SummaryChartPoint(
                        bucketKey = key,
                        xLabel = monthShortNames[m],
                        fullLabel = "${monthShortNames[m]} ${anchor.year}",
                        totalPaid = paid,
                        totalReceived = rec,
                        netChange = net,
                        cumulativeBalance = runningCum,
                        entryCount = b?.entryCount ?: 0,
                        isCurrentOrSelected = (key == activeMonthKey)
                    )
                }
            }
            SummaryGranularity.YEARLY -> {
                val activeYearKey = anchor.year.toString()
                val sorted = if (rawBuckets.isEmpty()) {
                    emptyList()
                } else {
                    rawBuckets.sortedBy { it.bucketKey }
                }
                var runningCum = 0.0
                sorted.map { b ->
                    val net = b.totalReceived - b.totalPaid
                    runningCum += net
                    SummaryChartPoint(
                        bucketKey = b.bucketKey,
                        xLabel = b.bucketKey,
                        fullLabel = b.bucketKey,
                        totalPaid = b.totalPaid,
                        totalReceived = b.totalReceived,
                        netChange = net,
                        cumulativeBalance = runningCum,
                        entryCount = b.entryCount,
                        isCurrentOrSelected = (b.bucketKey == activeYearKey)
                    )
                }
            }
        }

        val activePointsWithVolume = basePoints.filter { it.entryCount > 0 || it.totalPaid > 0.0 || it.totalReceived > 0.0 }
        val highestPoint = activePointsWithVolume.maxByOrNull { it.totalPaid + it.totalReceived }
        val lowestPoint = activePointsWithVolume.minByOrNull { it.totalPaid + it.totalReceived }

        val decoratedPoints = basePoints.map { pt ->
            pt.copy(
                isHighest = highestPoint != null && pt.bucketKey == highestPoint.bucketKey,
                isLowest = lowestPoint != null && pt.bucketKey == lowestPoint.bucketKey && highestPoint?.bucketKey != lowestPoint.bucketKey
            )
        }

        val drillDownLabel = drillDownBucketKey?.let { key ->
            when (key.count { it == '-' }) {
                2 -> SummaryPeriodHelper.formatDayHeaderLabel(key)
                1 -> SummaryPeriodHelper.formatMonthHeaderLabel(key)
                else -> key
            }
        }

        return SummaryChartUiState(
            isLoading = false,
            granularity = granularity,
            anchor = anchor,
            periodLabel = SummaryPeriodHelper.formatPeriodLabel(granularity, anchor),
            canStepForward = SummaryPeriodHelper.canStepForward(granularity, anchor, today),
            overview = SummaryPeriodOverview(
                totalPaid = overviewTotals.totalPaid,
                totalReceived = overviewTotals.totalReceived,
                netBalance = overviewTotals.totalReceived - overviewTotals.totalPaid,
                entryCount = overviewTotals.entryCount,
                customerCount = overviewTotals.customerCount,
                highestLabel = highestPoint?.fullLabel,
                highestAmount = highestPoint?.let { it.totalPaid + it.totalReceived } ?: 0.0
            ),
            points = decoratedPoints,
            selectedBucketKey = selectedBucketKey ?: decoratedPoints.firstOrNull { it.isCurrentOrSelected && it.entryCount > 0 }?.bucketKey,
            drillDownBucketKey = drillDownBucketKey,
            drillDownLabel = drillDownLabel
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    // Customer Actions
    fun addCustomer(
        name: String,
        mobileNumber: String,
        address: String = "",
        email: String = "",
        notes: String = "",
        collectToday: Boolean = false,
        collectDueDate: Long? = null,
        reminderEnabled: Boolean = true,
        profilePhotoUri: String? = null,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.insertCustomer(
                        CustomerEntity(
                            name = name.trim(),
                            mobileNumber = mobileNumber.trim(),
                            address = address.trim(),
                            email = email.trim(),
                            notes = notes.trim(),
                            collectToday = collectToday,
                            collectDueDate = collectDueDate,
                            reminderEnabled = reminderEnabled,
                            profilePhotoUri = profilePhotoUri,
                            createdTimestamp = System.currentTimeMillis(),
                            lastUpdatedTimestamp = System.currentTimeMillis()
                        )
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateCustomer(
        customer: CustomerEntity,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateCustomer(customer.copy(lastUpdatedTimestamp = System.currentTimeMillis()))
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateCustomerReminderEnabled(customerId: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCustomerReminderEnabled(customerId, enabled)
        }
    }

    fun deleteCustomer(
        customer: CustomerEntity,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteCustomer(customer)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun toggleCollectToday(
        customer: CustomerEntity,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateCustomer(
                        customer.copy(
                            collectToday = !customer.collectToday,
                            lastUpdatedTimestamp = System.currentTimeMillis()
                        )
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
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
        attachmentPath: String? = null,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
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
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateTransaction(
        transaction: TransactionEntity,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateTransaction(transaction)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteTransaction(
        transaction: TransactionEntity,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteTransaction(transaction)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun removeTransactionImage(
        transaction: TransactionEntity,
        imagePathToRemove: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val remaining = transaction.attachmentPath
                        .orEmpty()
                        .split("|")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it != imagePathToRemove.trim() }
                    val updatedPath = if (remaining.isNotEmpty()) remaining.joinToString("|") else null
                    repository.updateTransaction(transaction.copy(attachmentPath = updatedPath))
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    // Monthly Expense Actions
    fun addExpense(
        monthYear: String,
        title: String,
        category: String,
        amount: Double,
        notes: String = "",
        attachmentPath: String? = null,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
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
                }
                syncMonthlyExpensesToLedger()
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateExpense(
        expense: MonthlyExpenseEntity,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateExpense(expense)
                }
                syncMonthlyExpensesToLedger()
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteExpense(
        expense: MonthlyExpenseEntity,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteExpense(expense)
                }
                syncMonthlyExpensesToLedger()
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
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
        return com.example.utils.BackupManager.exportDatabaseToJson(getApplication())
    }

    fun importAllDataJsonAsync(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = importAllDataJson(jsonString)
            onResult(result)
        }
    }

    fun restoreFullBackupFromJson(
        jsonString: String,
        authViewModel: AuthViewModel?,
        onComplete: (FullBackupRestoreSummary) -> Unit
    ) {
        viewModelScope.launch {
            val summary = performFullBackupImportInternal(jsonString, authViewModel)
            onComplete(summary)
        }
    }

    fun resolvePendingProfileRestore(replace: Boolean, authViewModel: AuthViewModel) {
        val pending = _pendingProfileRestore.value ?: return
        _pendingProfileRestore.value = null
        if (!replace) return

        val ctx = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val savedAvatarPath = if (!pending.profileImageBase64.isNullOrBlank()) {
                com.example.utils.ImageUtils.saveProfileAvatar(ctx, pending.profileImageBase64)
            } else null

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                authViewModel.applyRestoredProfile(
                    name = pending.name,
                    businessName = pending.businessName,
                    phone = pending.phone,
                    email = pending.email,
                    profileImagePath = savedAvatarPath
                )
            }
        }
    }

    suspend fun importAllDataJson(jsonString: String): Boolean {
        return performFullBackupImportInternal(jsonString, authViewModel = null).success
    }

    private fun optStringSafe(obj: org.json.JSONObject, key: String, fallback: String = ""): String {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        val value = obj.optString(key, fallback)
        return if (value.equals("null", ignoreCase = true)) fallback else value
    }

    internal suspend fun performFullBackupImportInternal(
        jsonString: String,
        authViewModel: AuthViewModel?
    ): FullBackupRestoreSummary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val rootObj = org.json.JSONObject(jsonString)
            val custArray = rootObj.optJSONArray("customers") ?: org.json.JSONArray()
            val txArray = rootObj.optJSONArray("transactions") ?: org.json.JSONArray()
            val context = getApplication<android.app.Application>()

            val idMap = mutableMapOf<Long, Long>()

            // Fetch current existing customers & transactions in bulk
            val existingCustomers = repository.allCustomers.firstOrNull() ?: emptyList()
            val existingTxs = repository.allTransactions.firstOrNull() ?: emptyList()

            val existingTxKeySet = mutableSetOf<String>()
            existingTxs.forEach { tx ->
                val key = "${tx.customerId}_${tx.transactionType}_${(tx.amount * 100).toLong()}_${tx.dateTimestamp}_${tx.comment.trim()}"
                existingTxKeySet.add(key)
            }

            var customersAdded = 0
            for (i in 0 until custArray.length()) {
                val obj = custArray.getJSONObject(i)
                val oldId = obj.optLong("id", 0L)
                val nameVal = optStringSafe(obj, "name").trim()
                if (nameVal.isBlank()) continue
                val mobileVal = optStringSafe(obj, "mobileNumber").trim()
                val dueDateVal = if (!obj.isNull("collectDueDate")) obj.optLong("collectDueDate", 0L) else 0L

                val restoredPhotoUri = when {
                    obj.has("profilePhotoBase64List") && !obj.isNull("profilePhotoBase64List") ->
                        restoreBase64JsonArrayToLocalPaths(context, obj.getJSONArray("profilePhotoBase64List"), "profile_photos")
                    obj.has("profilePhotoBase64") && optStringSafe(obj, "profilePhotoBase64").isNotBlank() ->
                        saveBase64ToLocalFile(optStringSafe(obj, "profilePhotoBase64"), "profile_photos")
                    else -> {
                        val rawUri = optStringSafe(obj, "profilePhotoUri").trim()
                        if (rawUri.isNotBlank() && java.io.File(rawUri).exists()) rawUri else null
                    }
                }

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
                    customersAdded++
                    repository.insertCustomer(
                        CustomerEntity(
                            name = nameVal,
                            mobileNumber = mobileVal,
                            address = optStringSafe(obj, "address"),
                            email = optStringSafe(obj, "email"),
                            notes = optStringSafe(obj, "notes"),
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
                val oldCustId = obj.optLong("customerId", 0L)
                val targetCustId = idMap[oldCustId] ?: oldCustId
                if (targetCustId <= 0L) continue

                val txType = optStringSafe(obj, "transactionType", "I_PAID")
                val txAmount = obj.optDouble("amount", 0.0)
                val txDate = obj.optLong("dateTimestamp", System.currentTimeMillis())
                val txComment = optStringSafe(obj, "comment").trim()
                val txCategory = optStringSafe(obj, "category", "General").ifBlank { "General" }
                val txPaymentMode = optStringSafe(obj, "paymentMode", "CASH").ifBlank { "CASH" }

                val restoredAttachPath = when {
                    obj.has("attachmentBase64List") && !obj.isNull("attachmentBase64List") ->
                        restoreBase64JsonArrayToLocalPaths(context, obj.getJSONArray("attachmentBase64List"), "attachments")
                    obj.has("attachmentBase64") && optStringSafe(obj, "attachmentBase64").isNotBlank() ->
                        saveBase64ToLocalFile(optStringSafe(obj, "attachmentBase64"), "attachments")
                    else -> {
                        val rawPath = optStringSafe(obj, "attachmentPath").trim()
                        if (rawPath.isNotBlank() && java.io.File(rawPath).exists()) rawPath else null
                    }
                }

                val txKey = "${targetCustId}_${txType}_${(txAmount * 100).toLong()}_${txDate}_${txComment}"

                if (!existingTxKeySet.contains(txKey)) {
                    existingTxKeySet.add(txKey)
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

            newTransactionsToInsert.chunked(200).forEach { chunk ->
                repository.insertTransactionsBatch(chunk)
            }

            // Restore Monthly Expenses if present in JSON
            var expensesAdded = 0
            if (rootObj.has("monthlyExpenses") && !rootObj.isNull("monthlyExpenses")) {
                val expArray = rootObj.getJSONArray("monthlyExpenses")
                val existingExpenses = repository.allExpenses.firstOrNull() ?: emptyList()
                val existingExpKeySet = existingExpenses.map { "${it.monthYear}_${it.title.trim()}_${(it.amount * 100).toLong()}" }.toSet()

                for (i in 0 until expArray.length()) {
                    val obj = expArray.getJSONObject(i)
                    val monthYear = optStringSafe(obj, "monthYear")
                    val title = optStringSafe(obj, "title").trim()
                    val category = optStringSafe(obj, "category", "General").ifBlank { "General" }
                    val amount = obj.optDouble("amount", 0.0)
                    val dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis())
                    val notes = optStringSafe(obj, "notes")

                    val restoredAttachPath = when {
                        obj.has("attachmentBase64List") && !obj.isNull("attachmentBase64List") ->
                            restoreBase64JsonArrayToLocalPaths(context, obj.getJSONArray("attachmentBase64List"), "attachments")
                        obj.has("attachmentBase64") && optStringSafe(obj, "attachmentBase64").isNotBlank() ->
                            saveBase64ToLocalFile(optStringSafe(obj, "attachmentBase64"), "attachments")
                        else -> {
                            val rawPath = optStringSafe(obj, "attachmentPath").trim()
                            if (rawPath.isNotBlank() && java.io.File(rawPath).exists()) rawPath else null
                        }
                    }

                    val key = "${monthYear}_${title}_${(amount * 100).toLong()}"
                    if (title.isNotBlank() && amount > 0.0 && !existingExpKeySet.contains(key)) {
                        expensesAdded++
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

            // Handle optional userProfile restoration (conflict-aware; legacy backups without userProfile leave current profile untouched)
            var hasProfile = false
            val profileObj = rootObj.optJSONObject("userProfile")
            if (profileObj != null) {
                hasProfile = true
                val bName = optStringSafe(profileObj, "name")
                val bBusiness = optStringSafe(profileObj, "businessName")
                val bPhone = optStringSafe(profileObj, "phone")
                val bEmail = optStringSafe(profileObj, "email")
                val bImgBase64 = optStringSafe(profileObj, "profileImageBase64").ifBlank { null }

                if (authViewModel != null) {
                    val current = authViewModel.userProfile.value
                    val differs = current.name != bName ||
                        current.businessName != bBusiness ||
                        current.phone != bPhone ||
                        current.email != bEmail ||
                        (bImgBase64 != null && current.profileImageUri == null)

                    if (authViewModel.hasExistingCustomProfile() && differs) {
                        _pendingProfileRestore.value = PendingProfileRestore(
                            name = bName,
                            businessName = bBusiness,
                            phone = bPhone,
                            email = bEmail,
                            profileImageBase64 = bImgBase64
                        )
                    } else {
                        val savedAvatarPath = if (!bImgBase64.isNullOrBlank()) {
                            com.example.utils.ImageUtils.saveProfileAvatar(context, bImgBase64)
                        } else null
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            authViewModel.applyRestoredProfile(
                                name = bName,
                                businessName = bBusiness,
                                phone = bPhone,
                                email = bEmail,
                                profileImagePath = savedAvatarPath
                            )
                        }
                    }
                }
            }

            FullBackupRestoreSummary(
                success = true,
                customersAdded = customersAdded,
                transactionsAdded = newTransactionsToInsert.size,
                expensesAdded = expensesAdded,
                hasProfileInBackup = hasProfile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            FullBackupRestoreSummary(success = false, errorMessage = e.localizedMessage ?: "Invalid backup JSON")
        }
    }
}
