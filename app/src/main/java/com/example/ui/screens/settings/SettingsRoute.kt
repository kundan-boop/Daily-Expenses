package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.BackupState
import com.example.utils.LocalAppStrings

enum class SettingsRoute(val title: String) {
    Menu("Settings & Account"),
    Profile("Profile"),
    Summary("Financial Summary"),
    SecurityLock("Security & App Lock"),
    ThemeAppearance("Theme & Appearance"),
    Language("Language"),
    Notifications("Notifications & Alerts"),
    GoogleDrive("Google Drive Backups"),
    FirebaseBackup("Firebase Cloud Backup"),
    GoogleAccount("Google Account"),
    ImportExport("Import / Export & PDFs"),
    Share("Share Application"),
    AboutPermissions("About, Permissions & Logout")
}

@Composable
fun SettingsSubScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    useInternalScroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        Surface(
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            color = c.brandHeader,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = s.back,
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val baseMod = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
            val bodyMod = if (useInternalScroll) {
                baseMod
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            } else {
                baseMod.fillMaxHeight()
            }
            Column(
                modifier = bodyMod,
                verticalArrangement = if (useInternalScroll) Arrangement.spacedBy(14.dp) else Arrangement.Top,
                content = content
            )
        }
    }
}

@Composable
fun BackupStateStatusBanner(
    state: BackupState,
    successText: String,
    offlineText: String,
    failedText: String,
    inProgressText: String,
    notConfiguredText: String
) {
    val c = LedgerTheme.colors
    val bgColor = when (state) {
        BackupState.SUCCESS -> c.greenTint
        BackupState.PENDING_OFFLINE -> c.amberTint
        BackupState.FAILED -> c.redTint
        BackupState.IN_PROGRESS -> c.blueTint
        BackupState.NOT_CONFIGURED -> c.cardElevated
    }
    val borderColor = when (state) {
        BackupState.SUCCESS -> c.greenBorder
        BackupState.PENDING_OFFLINE -> c.amberBorder
        BackupState.FAILED -> c.redBorder
        BackupState.IN_PROGRESS -> c.blueBorder
        BackupState.NOT_CONFIGURED -> c.border
    }
    val textColor = when (state) {
        BackupState.SUCCESS -> c.greenPrimary
        BackupState.PENDING_OFFLINE -> c.amberAccent
        BackupState.FAILED -> c.redDebit
        BackupState.IN_PROGRESS -> c.blueAccent
        BackupState.NOT_CONFIGURED -> c.textSecondary
    }
    val icon: ImageVector = when (state) {
        BackupState.SUCCESS -> Icons.Default.CheckCircle
        BackupState.PENDING_OFFLINE -> Icons.Default.CloudSync
        BackupState.FAILED -> Icons.Default.ErrorOutline
        BackupState.IN_PROGRESS -> Icons.Default.Sync
        BackupState.NOT_CONFIGURED -> Icons.Default.CloudOff
    }
    val message = when (state) {
        BackupState.SUCCESS -> successText
        BackupState.PENDING_OFFLINE -> offlineText
        BackupState.FAILED -> failedText
        BackupState.IN_PROGRESS -> inProgressText
        BackupState.NOT_CONFIGURED -> notConfiguredText
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun showRestoreSummaryToast(
    context: android.content.Context,
    summary: DailyExpenseViewModel.FullBackupRestoreSummary
) {
    val msg = if (summary.success) {
        "Restored ${summary.customersAdded} new customers, ${summary.transactionsAdded} transactions, ${summary.expensesAdded} expenses."
    } else {
        "Restore failed: ${summary.errorMessage ?: "Invalid backup data"}"
    }
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}
