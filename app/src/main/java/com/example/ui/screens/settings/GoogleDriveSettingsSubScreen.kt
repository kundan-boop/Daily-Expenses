package com.example.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.AppPreferencesManager
import com.example.utils.BackupState
import com.example.utils.DriveBackupStatus
import com.example.utils.LocalAppStrings

@Composable
fun GoogleDriveSettingsSubScreen(
    authViewModel: AuthViewModel,
    dailyExpenseViewModel: DailyExpenseViewModel?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val driveBackupsList by (dailyExpenseViewModel?.driveBackupsList?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) })
    val driveFolderName by (dailyExpenseViewModel?.driveFolderName?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })
    val backupStatus by (dailyExpenseViewModel?.backupStatus?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(DriveBackupStatus()) })
    val driveAutoBackupEnabled by AppPreferencesManager.driveAutoBackupEnabledFlow.collectAsStateWithLifecycle()

    var isSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dailyExpenseViewModel?.refreshDriveBackupsList()
    }

    val selectDriveFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        authViewModel.suppressAutoLockOnce()
        uri?.let { folderUri ->
            dailyExpenseViewModel?.saveDriveFolderUri(folderUri)
            Toast.makeText(
                context,
                "Google Drive connected! Backups are stored in 'DailyExpenseBackups' folder.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val c = LedgerTheme.colors
    val s = LocalAppStrings.current

    SettingsSubScreenScaffold(
        title = s.googleDriveTitle,
        subtitle = "7-day verified snapshot retention in DailyExpenseBackups",
        onBack = onBack
    ) {
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
                        Icon(Icons.Default.AddToDrive, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Drive Folder Connection", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (driveFolderName != null) c.greenTint else c.cardElevated
                    ) {
                        Text(
                            text = if (driveFolderName != null) "CONNECTED" else "NOT SET",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (driveFolderName != null) c.greenPrimary else c.textMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                BackupStateStatusBanner(
                    state = backupStatus.state,
                    successText = backupStatus.lastBackupFormatted?.let { "Last synced: $it (Up to date)" }
                        ?: "Drive auto-backup active & up to date",
                    offlineText = "Offline: Backup queued, will upload automatically when online",
                    failedText = "Sync failed: ${backupStatus.errorMessage ?: "Retry required"}",
                    inProgressText = "Uploading verified backup to DailyExpenseBackups...",
                    notConfiguredText = "Select your Google Drive folder to enable automatic 7-day backups"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Auto-Backup to Drive", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text("Keep 7 daily snapshots & overwrite older backups only after verified write", fontSize = 11.5.sp, color = c.textMuted)
                    }
                    Switch(
                        checked = driveAutoBackupEnabled,
                        onCheckedChange = { enabled ->
                            AppPreferencesManager.setDriveAutoBackupEnabled(context, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.blueAccent,
                            uncheckedThumbColor = c.textMuted,
                            uncheckedTrackColor = c.border
                        )
                    )
                }

                if (driveFolderName != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.cardElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(driveFolderName ?: "Selected Drive Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Target: DailyExpenseBackups/", fontSize = 10.sp, color = c.textMuted)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                authViewModel.suppressAutoLockOnce()
                                selectDriveFolderLauncher.launch(null)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.borderStrong),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                dailyExpenseViewModel?.clearDriveFolderUri()
                                Toast.makeText(context, "Disconnected Google Drive folder.", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, c.borderStrong),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textSecondary, maxLines = 1)
                        }
                    }

                    Button(
                        onClick = {
                            if (isSyncing) return@Button
                            isSyncing = true
                            dailyExpenseViewModel?.triggerDriveAutoBackupNow { success ->
                                isSyncing = false
                                Toast.makeText(
                                    context,
                                    if (success) "Verified backup saved to Google Drive!" else "Failed to upload Drive backup",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !isSyncing && backupStatus.state != BackupState.IN_PROGRESS,
                        colors = ButtonDefaults.buttonColors(containerColor = c.blueAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync to Google Drive Now", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            authViewModel.suppressAutoLockOnce()
                            selectDriveFolderLauncher.launch(null)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.blueAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Google Drive Folder", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 7-Day Drive Snapshot History
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
                    Text("Drive Snapshots (Max 7 Days)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Surface(shape = RoundedCornerShape(6.dp), color = c.cardElevated) {
                        Text("${driveBackupsList.size} / 7", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = c.blueAccent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }

                if (driveBackupsList.isEmpty()) {
                    Text(
                        text = if (driveFolderName != null) "No Drive snapshots found yet. Tap 'Sync to Google Drive Now' above." else "Connect a Google Drive folder to see backup history.",
                        fontSize = 12.sp,
                        color = c.textMuted
                    )
                } else {
                    driveBackupsList.take(7).forEach { driveBackup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.cardElevated, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(driveBackup.dateDisplay, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                    Text(driveBackup.fileSizeStr, fontSize = 10.5.sp, color = c.textMuted)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    authViewModel.suppressAutoLockOnce()
                                    try {
                                        context.contentResolver.openInputStream(driveBackup.uri)?.use { stream ->
                                            val jsonContent = stream.bufferedReader().readText()
                                            dailyExpenseViewModel?.restoreFullBackupFromJson(jsonContent, authViewModel) { summary ->
                                                showRestoreSummaryToast(context, summary)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error reading Drive file: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, c.blueAccent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore Drive Backup", tint = c.blueAccent, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(s.restore, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.blueAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}
