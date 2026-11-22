package com.example.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.AppPreferencesManager
import com.example.utils.BackupManager
import com.example.utils.BackupState
import com.example.utils.FirebaseBackupStatus
import com.example.utils.LedgerDateFormatter
import com.example.utils.LocalAppStrings
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirebaseBackupSettingsSubScreen(
    authViewModel: AuthViewModel,
    dailyExpenseViewModel: DailyExpenseViewModel?,
    onNavigateToGoogleAccount: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val googleAccount by (dailyExpenseViewModel?.googleAccountInfo?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })
    val firebaseStatus by (dailyExpenseViewModel?.firebaseBackupStatus?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(FirebaseBackupStatus()) })
    val firebaseBackups by (dailyExpenseViewModel?.firebaseBackupsList?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) })
    val isFirebaseBusy by (dailyExpenseViewModel?.isFirebaseBusy?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) })
    val firebaseAutoBackupEnabled by AppPreferencesManager.firebaseAutoBackupEnabledFlow.collectAsStateWithLifecycle()
    val dailyBackupsList by (dailyExpenseViewModel?.dailyBackupsList?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) })
    val lastLocalBackupTime by (dailyExpenseViewModel?.lastLocalBackupTime?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(0L) })

    LaunchedEffect(Unit) {
        dailyExpenseViewModel?.refreshFirebaseState()
        dailyExpenseViewModel?.refreshDailyBackupsList()
    }

    SettingsSubScreenScaffold(
        title = s.firebaseBackupTitle,
        subtitle = "Encrypted Firestore snapshots with 7-day retention",
        onBack = onBack
    ) {
        // Account & Status Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Firestore Cloud Sync", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (googleAccount != null) c.greenTint else c.cardElevated
                    ) {
                        Text(
                            text = if (googleAccount != null) "READY" else "SIGN IN",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (googleAccount != null) c.greenPrimary else c.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                BackupStateStatusBanner(
                    state = firebaseStatus.state,
                    successText = firebaseStatus.lastBackupFormatted?.let {
                        "Last Firebase sync: $it (${firebaseStatus.snapshotCount} snapshot(s))"
                    } ?: "Signed in to Firebase. Tap Sync Now to upload your first backup.",
                    offlineText = "Offline: Firebase sync queued and will upload automatically when online",
                    failedText = "Firebase sync failed: ${firebaseStatus.errorMessage ?: "Retry required"}",
                    inProgressText = "Uploading verified chunked backup to Firebase Firestore...",
                    notConfiguredText = "Sign in with Google Account to enable Firebase Cloud Backup"
                )

                if (googleAccount != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.cardElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = googleAccount?.displayName ?: "Google User",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = googleAccount?.email ?: "",
                                    fontSize = 11.sp,
                                    color = c.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = onNavigateToGoogleAccount) {
                                Text("Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.blueAccent)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Auto-Backup to Firebase", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            Text("Keep 7 daily snapshots; older backups are pruned only after verified write", fontSize = 11.5.sp, color = c.textMuted)
                        }
                        Switch(
                            checked = firebaseAutoBackupEnabled,
                            onCheckedChange = { enabled ->
                                AppPreferencesManager.setFirebaseAutoBackupEnabled(context, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = c.greenPrimary,
                                uncheckedThumbColor = c.textMuted,
                                uncheckedTrackColor = c.border
                            ),
                            modifier = Modifier.testTag("firebase_auto_backup_switch")
                        )
                    }

                    Button(
                        onClick = {
                            dailyExpenseViewModel?.syncFirebaseBackupNow { ok, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isFirebaseBusy && firebaseStatus.state != BackupState.IN_PROGRESS,
                        colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("firebase_sync_now_button")
                    ) {
                        if (isFirebaseBusy) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync to Firebase Now", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else {
                    Button(
                        onClick = onNavigateToGoogleAccount,
                        colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("firebase_open_google_account_button")
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign In via Google Account", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 7-Day Cloud Snapshots Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cloud Snapshots (Max 7 Days)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Surface(shape = RoundedCornerShape(6.dp), color = c.greenTint) {
                        Text(
                            text = "${firebaseBackups.size} / 7",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.greenPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (googleAccount == null) {
                    Text(
                        text = "Your local ledger works completely offline. Sign in with Google when you want cross-device cloud backups.",
                        fontSize = 12.sp,
                        color = c.textMuted
                    )
                } else if (firebaseBackups.isEmpty()) {
                    Text(
                        text = "No verified cloud snapshots yet. Tap 'Sync to Firebase Now' above.",
                        fontSize = 12.sp,
                        color = c.textMuted
                    )
                } else {
                    firebaseBackups.take(7).forEach { snapshot ->
                        val sizeLabel = remember(snapshot.totalBytes) {
                            when {
                                snapshot.totalBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", snapshot.totalBytes / (1024.0 * 1024.0))
                                snapshot.totalBytes >= 1024 -> String.format(Locale.US, "%d KB", snapshot.totalBytes / 1024)
                                else -> "${snapshot.totalBytes} B"
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = c.cardElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = snapshot.backupDate,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = c.textPrimary
                                        )
                                        Text(
                                            text = "${snapshot.customerCount} customers • ${snapshot.transactionCount} entries • $sizeLabel",
                                            fontSize = 11.sp,
                                            color = c.textSecondary
                                        )
                                        if (snapshot.hasUserProfile) {
                                            Text(
                                                text = "Profile included",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = c.greenPrimary
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        dailyExpenseViewModel?.restoreFirebaseBackup(snapshot.backupId, authViewModel) { summary ->
                                            showRestoreSummaryToast(context, summary)
                                        }
                                    },
                                    enabled = !isFirebaseBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, c.greenPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore Cloud Snapshot", tint = c.greenPrimary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(s.restore, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local Device Backup Card (Every 7 days • max 4 local copies)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("local_backup_card")
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Local Device Backup", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = c.greenTint) {
                        Text(
                            text = "EVERY 7 DAYS • MAX ${BackupManager.MAX_LOCAL_BACKUP_COPIES}",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.greenPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                val lastBackupLabel = when {
                    lastLocalBackupTime > 0L -> LedgerDateFormatter.formatDateTimeWithYear(lastLocalBackupTime)
                    dailyBackupsList.isNotEmpty() -> dailyBackupsList.first().dateDisplay
                    else -> "Never"
                }

                Text(
                    text = "Last local backup: $lastBackupLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textSecondary,
                    modifier = Modifier.testTag("last_local_backup_date_text")
                )

                Text(
                    text = "Automatically saves an on-device ledger + profile snapshot every 7 days and keeps up to ${BackupManager.MAX_LOCAL_BACKUP_COPIES} latest copies.",
                    fontSize = 11.5.sp,
                    color = c.textMuted
                )

                OutlinedButton(
                    onClick = {
                        dailyExpenseViewModel?.triggerManualDailyBackup { success ->
                            Toast.makeText(
                                context,
                                if (success) "Local backup saved!" else "Failed to create local backup",
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
                        .testTag("local_backup_now_button")
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup Now", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary)
                }

                if (dailyBackupsList.isNotEmpty()) {
                    val retainedCopies = dailyBackupsList.take(BackupManager.MAX_LOCAL_BACKUP_COPIES)
                    Text(
                        text = "LOCAL COPIES (${retainedCopies.size} / ${BackupManager.MAX_LOCAL_BACKUP_COPIES}):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textMuted
                    )
                    retainedCopies.forEach { backup ->
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
                                    Text(
                                        backup.dateDisplay,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
    }
}
