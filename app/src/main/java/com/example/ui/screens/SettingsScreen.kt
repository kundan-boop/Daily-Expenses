package com.example.ui.screens

import com.example.R
import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    dailyExpenseViewModel: DailyExpenseViewModel? = null,
    onLogout: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userProfile by authViewModel.userProfile.collectAsState()
    val allTransactions by (dailyExpenseViewModel?.allTransactions?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    var selectedSummaryTab by remember { mutableIntStateOf(0) }
    var targetCalendarMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    val summaryResult = remember(allTransactions, targetCalendarMillis, selectedSummaryTab) {
        val nowCal = java.util.Calendar.getInstance()
        val curYear = nowCal.get(java.util.Calendar.YEAR)
        val curMonth = nowCal.get(java.util.Calendar.MONTH)
        val curDay = nowCal.get(java.util.Calendar.DAY_OF_YEAR)

        val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = targetCalendarMillis }
        val targetYear = targetCal.get(java.util.Calendar.YEAR)
        val targetMonth = targetCal.get(java.util.Calendar.MONTH)
        val targetDay = targetCal.get(java.util.Calendar.DAY_OF_YEAR)

        val isToday = (targetYear == curYear && targetDay == curDay)
        val isThisMonth = (targetYear == curYear && targetMonth == curMonth)
        val isThisYear = (targetYear == curYear)

        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val monthFullNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

        val dailyBadge = if (isToday) "TODAY" else String.format(java.util.Locale.US, "%02d %s", targetCal.get(java.util.Calendar.DAY_OF_MONTH), monthNames[targetMonth])
        val monthlyBadge = if (isThisMonth) "THIS MONTH" else "${monthNames[targetMonth].uppercase()} $targetYear"
        val yearlyBadge = if (isThisYear) "THIS YEAR" else "$targetYear"

        val txCal = java.util.Calendar.getInstance()

        // Overall Totals (using current real time)
        var dGave0 = 0.0; var dGot0 = 0.0; var dCount0 = 0
        var mGave0 = 0.0; var mGot0 = 0.0; var mCount0 = 0
        var yGave0 = 0.0; var yGot0 = 0.0; var yCount0 = 0

        // Filtered Totals (using target date for tabs 1, 2, 3)
        var dGaveF = 0.0; var dGotF = 0.0; var dCountF = 0
        var mGaveF = 0.0; var mGotF = 0.0; var mCountF = 0
        var yGaveF = 0.0; var yGotF = 0.0; var yCountF = 0

        allTransactions.forEach { tx ->
            txCal.timeInMillis = tx.dateTimestamp
            val year = txCal.get(java.util.Calendar.YEAR)
            val month = txCal.get(java.util.Calendar.MONTH)
            val day = txCal.get(java.util.Calendar.DAY_OF_YEAR)

            val amt = tx.amount
            val isGave = tx.transactionType == "I_PAID"

            // Current date accumulation
            if (year == curYear) {
                if (isGave) yGave0 += amt else yGot0 += amt
                yCount0++

                if (month == curMonth) {
                    if (isGave) mGave0 += amt else mGot0 += amt
                    mCount0++

                    if (day == curDay) {
                        if (isGave) dGave0 += amt else dGot0 += amt
                        dCount0++
                    }
                }
            }

            // Target date accumulation
            if (year == targetYear) {
                if (isGave) yGaveF += amt else yGotF += amt
                yCountF++

                if (month == targetMonth) {
                    if (isGave) mGaveF += amt else mGotF += amt
                    mCountF++

                    if (day == targetDay) {
                        if (isGave) dGaveF += amt else dGotF += amt
                        dCountF++
                    }
                }
            }
        }

        val dLabel = String.format(java.util.Locale.US, "%02d %s %d", targetCal.get(java.util.Calendar.DAY_OF_MONTH), monthFullNames[targetMonth], targetYear)
        val mLabel = "${monthFullNames[targetMonth]} $targetYear"
        val yLabel = "$targetYear"
        val isCurrent = when (selectedSummaryTab) {
            1 -> isToday
            2 -> isThisMonth
            3 -> isThisYear
            else -> true
        }

        PeriodSummaryResult(
            dailySummary = FinancialPeriodSummary("Daily", if (selectedSummaryTab == 0) "TODAY" else dailyBadge, if (selectedSummaryTab == 0) dGave0 else dGaveF, if (selectedSummaryTab == 0) dGot0 else dGotF, if (selectedSummaryTab == 0) dCount0 else dCountF, Color(0xFF0284C7), Color(0xFFE0F2FE)),
            monthlySummary = FinancialPeriodSummary("Monthly", if (selectedSummaryTab == 0) "THIS MONTH" else monthlyBadge, if (selectedSummaryTab == 0) mGave0 else mGaveF, if (selectedSummaryTab == 0) mGot0 else mGotF, if (selectedSummaryTab == 0) mCount0 else mCountF, Color(0xFF4F46E5), Color(0xFFEEF2FF)),
            yearlySummary = FinancialPeriodSummary("Yearly", if (selectedSummaryTab == 0) "THIS YEAR" else yearlyBadge, if (selectedSummaryTab == 0) yGave0 else yGaveF, if (selectedSummaryTab == 0) yGot0 else yGotF, if (selectedSummaryTab == 0) yCount0 else yCountF, Color(0xFF059669), Color(0xFFECFDF5)),
            dailyLabel = dLabel,
            monthlyLabel = mLabel,
            yearlyLabel = yLabel,
            isCurrent = isCurrent
        )
    }

    val dailySummary = summaryResult.dailySummary
    val monthlySummary = summaryResult.monthlySummary
    val yearlySummary = summaryResult.yearlySummary


    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    // Profile photo launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { sourceUri ->
            try {
                val avatarFile = java.io.File(context.filesDir, "user_profile_avatar.jpg")
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    java.io.FileOutputStream(avatarFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                authViewModel.updateProfileImage(avatarFile.absolutePath)
                Toast.makeText(context, "Profile picture updated and saved!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                authViewModel.updateProfileImage(sourceUri.toString())
                Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // JSON Backup File Restore Launcher
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        authViewModel.suppressAutoLockOnce()
        uri?.let { fileUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
                if (!jsonString.isNullOrBlank()) {
                    dailyExpenseViewModel?.onBackupFileSelected(context, jsonString)
                } else {
                    Toast.makeText(context, "Selected backup file is empty!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading backup file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // JSON Backup File Create/Export Launcher
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
                    Toast.makeText(context, "Backup saved to local storage successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving backup file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val selectDriveFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        authViewModel.suppressAutoLockOnce()
        uri?.let { folderUri ->
            dailyExpenseViewModel?.saveDriveFolderUri(folderUri)
            Toast.makeText(context, "Google Drive folder selected! Auto-backups on close will keep 1 latest backup file here.", Toast.LENGTH_LONG).show()
        }
    }

    // Customer Statement Multi-PDF Document Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        authViewModel.suppressAutoLockOnce()
        if (!uris.isNullOrEmpty()) {
            dailyExpenseViewModel?.onPdfFilesSelected(context, uris)
        }
    }

    var showSampleDownloadChoiceDialog by remember { mutableStateOf(false) }

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

    val showPdfConfirmDialog by (dailyExpenseViewModel?.showPdfConfirmDialog?.collectAsState() ?: remember { mutableStateOf(false) })
    val showPdfProgressDialog by (dailyExpenseViewModel?.showPdfProgressDialog?.collectAsState() ?: remember { mutableStateOf(false) })
    val showPdfSummaryDialog by (dailyExpenseViewModel?.showPdfSummaryDialog?.collectAsState() ?: remember { mutableStateOf(false) })
    val selectedPdfUris by (dailyExpenseViewModel?.selectedPdfUris?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val pdfProgressState by (dailyExpenseViewModel?.pdfProgressState?.collectAsState() ?: remember { mutableStateOf(com.example.ui.PdfImportProgressState()) })
    val pdfSummaryResult by (dailyExpenseViewModel?.pdfSummaryResult?.collectAsState() ?: remember { mutableStateOf(null) })
    val showSamplePdfExistDialog by (dailyExpenseViewModel?.showSamplePdfExistDialog?.collectAsState() ?: remember { mutableStateOf(false) })
    val showPdfPreviewDialog by (dailyExpenseViewModel?.showPdfPreviewDialog?.collectAsState() ?: remember { mutableStateOf(false) })
    val pdfPreviewItems by (dailyExpenseViewModel?.pdfPreviewItems?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top Green Header with Rounded Bottom
        Surface(
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            color = GreenPrimary,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 16.dp, horizontal = 12.dp)
            ) {
                if (onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "Settings & Account Security",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Profile Header Card with Gallery Photo Selector
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(GreenLight)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!userProfile.profileImageUri.isNullOrBlank()) {
                            val imgUri = userProfile.profileImageUri!!
                            AsyncImage(
                                model = if (imgUri.startsWith("/")) java.io.File(imgUri) else imgUri,
                                contentDescription = "Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = userProfile.name.take(1).uppercase(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GreenPrimary
                            )
                        }

                        // Small camera overlay icon
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(GreenPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = "Select Photo",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(userProfile.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        if (userProfile.businessName.isNotBlank()) {
                            Text(userProfile.businessName, fontSize = 13.sp, color = GreenPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        if (userProfile.phone.isNotBlank()) {
                            Text(userProfile.phone, fontSize = 12.sp, color = Slate500)
                        }
                        if (userProfile.email.isNotBlank()) {
                            Text(userProfile.email, fontSize = 12.sp, color = Slate500)
                        }
                    }

                    IconButton(onClick = { showEditProfileDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = GreenPrimary)
                    }
                }
            }

            // Financial Details & Monthly Summary Card (Daily, Monthly, Yearly Matrix)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = SecondaryBlue.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Assessment, contentDescription = null, tint = SecondaryBlue, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Financial & Ledger Summary", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text("Real-time breakdown: Daily, Monthly & Yearly", fontSize = 11.sp, color = Slate500)
                            }
                        }
                    }

                    // Tab Selector: Comparison Matrix vs Individual Periods
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate100, RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val tabs = listOf("Overall", "Daily", "Monthly", "Yearly")
                        tabs.forEachIndexed { index, tabTitle ->
                            val isSelected = selectedSummaryTab == index
                            val activeColor = when(index) {
                                1 -> Color(0xFF0284C7)
                                2 -> Color(0xFF4F46E5)
                                3 -> Color(0xFF059669)
                                else -> GreenPrimary
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) activeColor else Color.Transparent)
                                    .clickable { selectedSummaryTab = index }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabTitle,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Slate700
                                )
                            }
                        }
                    }

                    // Period Navigation Bar (Switch Previous / Next Month, Year, or Day - ONLY shown for Daily, Monthly, and Yearly tabs)
                    if (selectedSummaryTab != 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate100.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = targetCalendarMillis }
                                    when (selectedSummaryTab) {
                                        1 -> cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                                        2 -> cal.add(java.util.Calendar.MONTH, -1)
                                        3 -> cal.add(java.util.Calendar.YEAR, -1)
                                    }
                                    targetCalendarMillis = cal.timeInMillis
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Period", tint = Slate700, modifier = Modifier.size(20.dp))
                            }

                            val displayPeriodText = when (selectedSummaryTab) {
                                1 -> summaryResult.dailyLabel
                                2 -> summaryResult.monthlyLabel
                                else -> summaryResult.yearlyLabel
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = SecondaryBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = displayPeriodText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )

                                if (!summaryResult.isCurrent) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = SecondaryBlue.copy(alpha = 0.15f),
                                        modifier = Modifier.clickable { targetCalendarMillis = System.currentTimeMillis() }
                                    ) {
                                        Text(
                                            text = "Current",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SecondaryBlue,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = targetCalendarMillis }
                                    when (selectedSummaryTab) {
                                        1 -> cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                                        2 -> cal.add(java.util.Calendar.MONTH, 1)
                                        3 -> cal.add(java.util.Calendar.YEAR, 1)
                                    }
                                    targetCalendarMillis = cal.timeInMillis
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Period", tint = Slate700, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    if (selectedSummaryTab == 0) {
                        // Matrix / Comparison Table View
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                        ) {
                            // Table Header Row with 3 Distinct Color Indications
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate100)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("METRIC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700, modifier = Modifier.weight(1.1f))

                                // Daily Badge
                                Surface(shape = RoundedCornerShape(4.dp), color = dailySummary.badgeBgColor, modifier = Modifier.weight(1f)) {
                                    Text("DAILY", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = dailySummary.badgeColor, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 3.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                // Monthly Badge
                                Surface(shape = RoundedCornerShape(4.dp), color = monthlySummary.badgeBgColor, modifier = Modifier.weight(1.1f)) {
                                    Text("MONTHLY", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = monthlySummary.badgeColor, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 3.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                // Yearly Badge
                                Surface(shape = RoundedCornerShape(4.dp), color = yearlySummary.badgeBgColor, modifier = Modifier.weight(1f)) {
                                    Text("YEARLY", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = yearlySummary.badgeColor, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 3.dp))
                                }
                            }

                            HorizontalDivider(color = Slate200)

                            // Row 1: You Got (Received)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1.1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("You Got", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                                }
                                Text(String.format(java.util.Locale.US, "₹%,.0f", dailySummary.totalGot), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                Text(String.format(java.util.Locale.US, "₹%,.0f", monthlySummary.totalGot), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1.1f))
                                Text(String.format(java.util.Locale.US, "₹%,.0f", yearlySummary.totalGot), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            }

                            HorizontalDivider(color = Slate200.copy(alpha = 0.5f))

                            // Row 2: You Gave (Paid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1.1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = RedDebit, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("You Gave", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                                }
                                Text(String.format(java.util.Locale.US, "₹%,.0f", dailySummary.totalGave), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedDebit, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                Text(String.format(java.util.Locale.US, "₹%,.0f", monthlySummary.totalGave), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedDebit, textAlign = TextAlign.Center, modifier = Modifier.weight(1.1f))
                                Text(String.format(java.util.Locale.US, "₹%,.0f", yearlySummary.totalGave), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedDebit, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            }

                            HorizontalDivider(color = Slate200.copy(alpha = 0.5f))

                            // Row 3: Net Balance
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate100.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Net Balance", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.weight(1.1f))

                                val dNet = dailySummary.netBalance
                                val mNet = monthlySummary.netBalance
                                val yNet = yearlySummary.netBalance

                                Text(
                                    text = String.format(java.util.Locale.US, "%s₹%,.0f", if (dNet >= 0) "+" else "", dNet),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (dNet >= 0) GreenPrimary else RedDebit,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%s₹%,.0f", if (mNet >= 0) "+" else "", mNet),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (mNet >= 0) GreenPrimary else RedDebit,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1.1f)
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%s₹%,.0f", if (yNet >= 0) "+" else "", yNet),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (yNet >= 0) GreenPrimary else RedDebit,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = Slate200.copy(alpha = 0.5f))

                            // Row 4: Transaction Count
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Txn Count", fontSize = 10.sp, color = Slate500, modifier = Modifier.weight(1.1f))
                                Text("${dailySummary.count} txns", fontSize = 10.sp, color = Slate700, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                Text("${monthlySummary.count} txns", fontSize = 10.sp, color = Slate700, textAlign = TextAlign.Center, modifier = Modifier.weight(1.1f))
                                Text("${yearlySummary.count} txns", fontSize = 10.sp, color = Slate700, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        // Single Detailed Period View Card (Daily=1, Monthly=2, Yearly=3)
                        val period = when(selectedSummaryTab) {
                            1 -> dailySummary
                            2 -> monthlySummary
                            else -> yearlySummary
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(period.badgeBgColor.copy(alpha = 0.4f))
                                .border(1.dp, period.badgeColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = RoundedCornerShape(6.dp), color = period.badgeColor) {
                                    Text(period.badgeName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }

                                Text("${period.count} Total Transactions", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate700)
                            }

                            // Net Balance Summary Box
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Net Ledger Balance (${period.periodName})", fontSize = 11.sp, color = Slate500)
                                    val net = period.netBalance
                                    Text(
                                        text = String.format(java.util.Locale.US, "%s₹%,.2f", if (net >= 0) "+" else "", net),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (net >= 0) GreenPrimary else RedDebit
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (period.netBalance >= 0) GreenLight else RedLight,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (period.netBalance >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                            contentDescription = null,
                                            tint = if (period.netBalance >= 0) GreenPrimary else RedDebit,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Two side-by-side Cards: You Got vs You Gave
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // You Got Card
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = GreenLight.copy(alpha = 0.6f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("You Got", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(String.format(java.util.Locale.US, "₹%,.0f", period.totalGot), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                                    }
                                }

                                // You Gave Card
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = RedLight.copy(alpha = 0.6f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = RedDebit, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("You Gave", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedDebit)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(String.format(java.util.Locale.US, "₹%,.0f", period.totalGave), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Slate900)
                                    }
                                }
                            }

                            // Visual Proportion Bar Chart
                            val totalVol = period.totalGot + period.totalGave
                            val gotRatio = if (totalVol > 0) (period.totalGot / totalVol).toFloat() else 0.5f
                            val gaveRatio = if (totalVol > 0) (period.totalGave / totalVol).toFloat() else 0.5f

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(String.format(java.util.Locale.US, "Got: %.0f%%", gotRatio * 100), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                                    Text(String.format(java.util.Locale.US, "Gave: %.0f%%", gaveRatio * 100), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedDebit)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Slate200)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(if (gotRatio > 0.01f) gotRatio else 0.01f)
                                            .background(GreenPrimary)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(if (gaveRatio > 0.01f) gaveRatio else 0.01f)
                                            .background(RedDebit)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Security & App Lock Card (Biometric, Phone Lock & PIN)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Security & App Lock", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        if (userProfile.isBiometricLockEnabled || userProfile.isPinEnabled) {
                            Surface(shape = RoundedCornerShape(50), color = GreenLight) {
                                Text("PROTECTED", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(50), color = Slate100) {
                                Text("DISABLED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate500, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }

                    // Phone Biometric / Device Lock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text("Phone Security Lock", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text("Use phone's own Face, Fingerprint, Pattern or PIN on opening / resuming", fontSize = 12.sp, color = Slate500)
                            }
                        }

                        Switch(
                            checked = userProfile.isBiometricLockEnabled,
                            onCheckedChange = { enabled ->
                                authViewModel.setBiometricLock(enabled)
                                if (enabled) {
                                    Toast.makeText(context, "App Lock enabled. Phone lock required to open app.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "App Lock disabled.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GreenPrimary,
                                checkedBorderColor = GreenPrimary,
                                uncheckedThumbColor = Slate500,
                                uncheckedTrackColor = Slate200,
                                uncheckedBorderColor = Slate400
                            )
                        )
                    }

                    HorizontalDivider(color = Slate200.copy(alpha = 0.6f))

                    // 4-Digit PIN Lock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text("4-Digit In-App PIN", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text("Optional custom 4-digit numerical passcode", fontSize = 12.sp, color = Slate500)
                            }
                        }

                        Switch(
                            checked = userProfile.isPinEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPinDialog = true
                                } else {
                                    authViewModel.setPin("", false)
                                    Toast.makeText(context, "In-app PIN disabled.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GreenPrimary,
                                checkedBorderColor = GreenPrimary,
                                uncheckedThumbColor = Slate500,
                                uncheckedTrackColor = Slate200,
                                uncheckedBorderColor = Slate400
                            )
                        )
                    }
                }
            }

            // Share App / APK to WhatsApp Card with Promo Banner and App Introduction
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF25D366).copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share App",
                                    tint = Color(0xFF128C7E),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share Application", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text("Send app APK & promo preview directly to WhatsApp", fontSize = 12.sp, color = Slate500)
                        }
                    }

                    // 4 Feature Points with Square Badges
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceBackground)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Core Features Overview",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )

                        // Point 1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GreenLight,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.BarChart, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Real-time Financial Dashboard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text("Live income, daily expenses & ledger totals", fontSize = 11.sp, color = Slate500)
                            }
                        }

                        // Point 2
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GreenLight,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Customer Khatabook Ledger", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text("Credit/debit entries with balance statements", fontSize = 11.sp, color = Slate500)
                            }
                        }

                        // Point 3
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = RedLight,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = RedDebit, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automated Deduction Expense Accounts", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text("Customer names containing 'Deduction' count as expense accounts", fontSize = 11.sp, color = Slate500)
                            }
                        }

                        // Point 4
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GreenLight,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Due Date Alerts & Passcode PIN Protection", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text("Collection due reminders & 4-digit PIN security lock", fontSize = 11.sp, color = Slate500)
                            }
                        }
                    }

                    val shareApkWithText: (useWhatsAppOnly: Boolean) -> Unit = { useWhatsApp ->
                        try {
                            authViewModel.suppressAutoLockOnce()

                            val srcApk = java.io.File(context.applicationInfo.sourceDir)
                            val destApk = java.io.File(context.filesDir, "DailyExpenseLedgerApp.apk")
                            srcApk.copyTo(destApk, overwrite = true)

                            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                destApk
                            )

                            val shareText = """
                                📱 *Daily Expense & Ledger*
                                Track Expenses, Manage Customers, Stay Organized.

                                ✨ *Core Application Features:*
                                1. 📊 Real-time Financial Dashboard & Income/Expense Analytics
                                2. 📒 Customer Khatabook & Credit/Debit Ledger Statements
                                3. 💸 Automated Deduction Customer Expense Tracking (Customer names containing 'Deduction' count as expense accounts)
                                4. 🔒 Due Date Collection Alerts & Passcode PIN Protection

                                Download and install the attached APK to start managing your shop ledger easily!
                            """.trimIndent()

                            val clipData = android.content.ClipData.newRawUri("Daily Expense & Ledger App", apkUri).apply {
                                addItem(android.content.ClipData.Item(shareText))
                            }

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.android.package-archive"
                                putExtra(Intent.EXTRA_STREAM, apkUri)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                putExtra("sms_body", shareText)
                                setClipData(clipData)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            val resInfoList = context.packageManager.queryIntentActivities(
                                shareIntent,
                                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                            )
                            for (resolveInfo in resInfoList) {
                                val packageName = resolveInfo.activityInfo.packageName
                                context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            if (useWhatsApp) {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Daily Expense & Ledger App Info", shareText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "App promo message copied to clipboard! Paste it in WhatsApp chat.", Toast.LENGTH_LONG).show()

                                val waIntent = Intent(shareIntent).apply {
                                    setPackage("com.whatsapp")
                                }
                                try {
                                    context.startActivity(waIntent)
                                } catch (e: Exception) {
                                    val chooser = Intent.createChooser(shareIntent, "Share App APK via")
                                    context.startActivity(chooser)
                                }
                            } else {
                                val chooser = Intent.createChooser(shareIntent, "Share App APK & Promo via")
                                context.startActivity(chooser)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { shareApkWithText(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share via WhatsApp", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { shareApkWithText(false) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate900),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Slate400),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Slate700, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share via Other Apps", fontWeight = FontWeight.Bold, color = Slate900, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Data Backup & Restore Options
            var isDriveConnected by remember { mutableStateOf(false) }
            val dailyBackupsList by (dailyExpenseViewModel?.dailyBackupsList?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
            val driveBackupsList by (dailyExpenseViewModel?.driveBackupsList?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
            val driveFolderName by (dailyExpenseViewModel?.driveFolderName?.collectAsState() ?: remember { mutableStateOf(null) })

            LaunchedEffect(Unit) {
                dailyExpenseViewModel?.refreshDailyBackupsList()
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = SecondaryBlue, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup & Restore Ledger Data", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        }

                        if (driveFolderName != null) {
                            Surface(shape = RoundedCornerShape(50), color = GreenLight) {
                                Text("DRIVE CONNECTED", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }

                    Text(
                        "Export your customer ledger data to a JSON backup file or restore previously saved backups from local storage or Google Drive.",
                        fontSize = 12.sp,
                        color = Slate500
                    )

                    HorizontalDivider(color = Slate200)

                    // Subsection 1: Local Device Storage (Existing Functionality Preserved)
                    Text("LOCAL DEVICE STORAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700, letterSpacing = 1.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                authViewModel.suppressAutoLockOnce()
                                createBackupLauncher.launch("daily_expenses_backup_${System.currentTimeMillis()}.json")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Local", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                authViewModel.suppressAutoLockOnce()
                                restoreLauncher.launch("*/*")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, GreenPrimary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Local", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    // Subsection 2: GOOGLE DRIVE AUTO-BACKUP & 8-DAY ROTATION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GOOGLE DRIVE AUTO-BACKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SecondaryBlue, letterSpacing = 0.5.sp)
                        Surface(shape = RoundedCornerShape(4.dp), color = Slate100) {
                            Text("1 LATEST BACKUP", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = SecondaryBlue, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }

                    Text(
                        "Select a Google Drive folder where auto-backups save on app close whenever data changes. Previous backup files are deleted before uploading so only 1 latest backup file is kept.",
                        fontSize = 11.sp,
                        color = Slate500
                    )

                    if (driveFolderName != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate100, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = SecondaryBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        driveFolderName ?: "Selected Drive Folder",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = GreenLight) {
                                    Text("ACTIVE", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        authViewModel.suppressAutoLockOnce()
                                        selectDriveFolderLauncher.launch(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Change Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        dailyExpenseViewModel?.clearDriveFolderUri()
                                        Toast.makeText(context, "Disconnected Google Drive folder.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate700),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, tint = Slate700, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Disconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    dailyExpenseViewModel?.triggerDriveAutoBackupNow { success ->
                                        if (success) {
                                            Toast.makeText(context, "Backup saved to Google Drive folder!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to save backup to Google Drive folder", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup to Google Drive Folder Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                authViewModel.suppressAutoLockOnce()
                                selectDriveFolderLauncher.launch(null)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Google Drive Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (driveBackupsList.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("DRIVE FOLDER BACKUPS (MAX 8 DAYS):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                            driveBackupsList.take(8).forEach { driveBackup ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Slate100, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = SecondaryBlue, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(driveBackup.dateDisplay, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(${driveBackup.fileSizeStr})", fontSize = 10.sp, color = Slate500)
                                    }

                                    IconButton(
                                        onClick = {
                                            authViewModel.suppressAutoLockOnce()
                                            try {
                                                context.contentResolver.openInputStream(driveBackup.uri)?.use { stream ->
                                                    val jsonContent = stream.bufferedReader().readText()
                                                    dailyExpenseViewModel?.onBackupFileSelected(context, jsonContent)
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error reading Drive file: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = "Restore", tint = SecondaryBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    // Subsection 3: AUTOMATED DAILY BACKUP & FIREBASE SYNC (7-Day Rolling Cycle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AUTOMATED DAILY BACKUP & FIREBASE SYNC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenPrimary, letterSpacing = 0.5.sp)
                        Surface(shape = RoundedCornerShape(4.dp), color = GreenLight) {
                            Text("7-DAY ROLLING", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }

                    Text(
                        "Automatically backs up daily to Firebase. Retains 7 days of rolling backups (on Day 8, Day 1 is overridden).",
                        fontSize = 11.sp,
                        color = Slate500
                    )

                    OutlinedButton(
                        onClick = {
                            dailyExpenseViewModel?.triggerManualDailyBackup { success ->
                                if (success) {
                                    Toast.makeText(context, "Daily backup taken & synced to Firebase!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to create daily backup", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Daily Auto-Backup Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                    }

                    if (dailyBackupsList.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("BACKUP HISTORY (MAX 7 DAYS):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                            dailyBackupsList.take(7).forEachIndexed { idx, backup ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Slate100, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Backup, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(backup.dateDisplay, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(${backup.fileSizeStr})", fontSize = 10.sp, color = Slate500)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = {
                                                authViewModel.suppressAutoLockOnce()
                                                try {
                                                    val jsonContent = backup.file.readText()
                                                    dailyExpenseViewModel?.onBackupFileSelected(context, jsonContent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = "Restore", tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                authViewModel.suppressAutoLockOnce()
                                                try {
                                                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        backup.file
                                                    )
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                                        type = "application/json"
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, "Save Backup File"))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Share", tint = SecondaryBlue, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    // Subsection 4: Customer Statement PDF Import
                    Text("CUSTOMER STATEMENT PDF IMPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenPrimary, letterSpacing = 1.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                authViewModel.suppressAutoLockOnce()
                                pdfPickerLauncher.launch("application/pdf")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Customer PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                authViewModel.suppressAutoLockOnce()
                                showSampleDownloadChoiceDialog = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, GreenPrimary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download Sample PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                        }
                    }
                }
            }

            // App About & Logout
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Daily Expense & Ledger", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Track Expenses, Manage Customers, Stay Organized.", fontSize = 12.sp, color = Slate500)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Developed by Kundansinh Khant",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            authViewModel.logout()
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedDebit),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout from Device", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var name by remember { mutableStateOf(userProfile.name) }
        var phone by remember { mutableStateOf(userProfile.phone) }
        var email by remember { mutableStateOf(userProfile.email) }
        var businessName by remember { mutableStateOf(userProfile.businessName) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = { Text("Edit Profile Details", fontWeight = FontWeight.ExtraBold, color = Slate900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Name", color = Slate700) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Slate400,
                            focusedLabelColor = GreenPrimary,
                            unfocusedLabelColor = Slate700,
                            cursorColor = GreenPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Business / Store Name", color = Slate700) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Slate400,
                            focusedLabelColor = GreenPrimary,
                            unfocusedLabelColor = Slate700,
                            cursorColor = GreenPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number", color = Slate700) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Slate400,
                            focusedLabelColor = GreenPrimary,
                            unfocusedLabelColor = Slate700,
                            cursorColor = GreenPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address", color = Slate700) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Slate400,
                            focusedLabelColor = GreenPrimary,
                            unfocusedLabelColor = Slate700,
                            cursorColor = GreenPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.updateProfile(name, phone, email, businessName)
                        showEditProfileDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Save Profile", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditProfileDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold, color = Slate700)
                }
            }
        )
    }

    // Set PIN Dialog
    if (showPinDialog) {
        var pin by remember { mutableStateOf("1234") }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = { Text("Set 4-Digit Security PIN", fontWeight = FontWeight.ExtraBold, color = Slate900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4) pin = it },
                        label = { Text("Enter 4-Digit PIN", color = Slate700) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = Slate400,
                            cursorColor = GreenPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text("Default code is set to 1234", fontSize = 12.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pin.length == 4) {
                            authViewModel.setPin(pin, true)
                            showPinDialog = false
                            Toast.makeText(context, "PIN Lock Enabled!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Enable Passcode Lock", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPinDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold, color = Slate700)
                }
            }
        )
    }

    // PDF Preview Dialog
    if (showPdfPreviewDialog && pdfPreviewItems.isNotEmpty()) {
        com.example.ui.dialogs.PdfImportPreviewDialog(
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
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = {
                Text("Customer PDF Import", fontWeight = FontWeight.ExtraBold, color = Slate900, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${selectedPdfUris.size} PDF file(s) selected",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary
                    )
                    Text(
                        text = "Press Import to process all statement files, detect customers, extract transaction rows, and migrate data into the ledger.",
                        fontSize = 13.sp,
                        color = Slate700
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { dailyExpenseViewModel?.startMultiPdfImport(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Import", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { dailyExpenseViewModel?.dismissPdfConfirmDialog() }) {
                    Text("Cancel", color = Slate500)
                }
            }
        )
    }

    // PDF Progress Dialog
    if (showPdfProgressDialog) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissable while running */ },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = {
                Text("Importing Customer PDFs...", fontWeight = FontWeight.ExtraBold, color = Slate900, fontSize = 18.sp)
            },
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
                        color = Slate700
                    )

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = GreenPrimary,
                        trackColor = Slate200
                    )

                    Text(
                        text = "$percent%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pdfProgressState.currentCustomerName.isNotBlank()) {
                        Surface(
                            color = GreenLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Current Customer:", fontSize = 11.sp, color = Slate500)
                                Text(pdfProgressState.currentCustomerName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
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
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (summary.failedPdfs == 0) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (summary.failedPdfs == 0) GreenPrimary else RedDebit,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (summary.failedPdfs == 0) "Import Completed Successfully" else "Import Completed",
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900,
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
                    SummaryRowItem("PDFs Selected", summary.pdfSelected.toString())
                    SummaryRowItem("PDFs Imported", summary.pdfImported.toString())
                    SummaryRowItem("Customers Created", summary.customersCreated.toString())
                    SummaryRowItem("Customers Updated", summary.customersUpdated.toString())
                    SummaryRowItem("Transactions Imported", String.format("%,d", summary.transactionsImported))
                    SummaryRowItem("Duplicate Transactions Skipped", summary.duplicateTransactionsSkipped.toString())
                    SummaryRowItem("Failed PDFs", summary.failedPdfs.toString(), isHighlight = summary.failedPdfs > 0)
                    SummaryRowItem("Import Time", "${summary.importTimeSeconds} Seconds")

                    if (summary.failedDetails.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Failed File Details:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedDebit)
                        summary.failedDetails.forEach { detail ->
                            Surface(
                                color = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(detail.fileName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                    Text(detail.reason, fontSize = 11.sp, color = RedDebit)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { dailyExpenseViewModel?.dismissPdfSummaryDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }

    // Sample PDF Already Exists Dialog
    if (showSamplePdfExistDialog) {
        AlertDialog(
            onDismissRequest = { dailyExpenseViewModel?.dismissSamplePdfExistDialog() },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = {
                Text("Sample PDF Already Exists", fontWeight = FontWeight.ExtraBold, color = Slate900, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Sample PDF files already exist in your Downloads folder. Do you want to replace them or keep both copies?",
                    fontSize = 14.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = { dailyExpenseViewModel?.downloadSamplePdfs(context, replace = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Replace", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dailyExpenseViewModel?.dismissSamplePdfExistDialog() }) {
                        Text("Cancel", color = Slate500)
                    }
                    OutlinedButton(
                        onClick = { dailyExpenseViewModel?.downloadSamplePdfs(context, replace = false) },
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Keep Both", fontWeight = FontWeight.Bold, color = GreenPrimary)
                    }
                }
            }
        )
    }

    // Sample Download Save-As Choice Dialog
    if (showSampleDownloadChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showSampleDownloadChoiceDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 15.dp),
            title = {
                Text("Download / Save Sample PDFs", fontWeight = FontWeight.ExtraBold, color = Slate900, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose which sample statement format you want to save to your device or Google Drive:",
                        fontSize = 13.sp,
                        color = Slate700
                    )

                    Button(
                        onClick = {
                            showSampleDownloadChoiceDialog = false
                            authViewModel.suppressAutoLockOnce()
                            saveSampleOkCreditLauncher.launch("OkCredit_Sample_Customer_Statement.pdf")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        modifier = Modifier.fillMaxWidth(),
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
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue),
                        modifier = Modifier.fillMaxWidth(),
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
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, GreenPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save All to Public Downloads Folder", fontWeight = FontWeight.Bold, color = GreenPrimary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSampleDownloadChoiceDialog = false }) {
                    Text("Close", color = Slate500)
                }
            }
        )
    }

}

@Composable
fun SummaryRowItem(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Slate700)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) RedDebit else Slate900
        )
    }
}

private data class FinancialPeriodSummary(
    val periodName: String,
    val badgeName: String,
    val totalGave: Double,
    val totalGot: Double,
    val count: Int,
    val badgeColor: Color,
    val badgeBgColor: Color
) {
    val netBalance: Double get() = totalGot - totalGave
}

private data class PeriodSummaryResult(
    val dailySummary: FinancialPeriodSummary,
    val monthlySummary: FinancialPeriodSummary,
    val yearlySummary: FinancialPeriodSummary,
    val dailyLabel: String,
    val monthlyLabel: String,
    val yearlyLabel: String,
    val isCurrent: Boolean
)

