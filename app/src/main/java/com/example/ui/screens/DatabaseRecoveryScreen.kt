package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseSafetyManager
import com.example.data.DbStartupState
import com.example.ui.theme.LedgerTheme
import com.example.utils.LedgerDateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DatabaseRecoveryScreen(
    state: DbStartupState.MigrationFailed,
    onRetry: () -> Unit,
    onRestoreCompleted: () -> Unit
) {
    val c = LedgerTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    val exportSafetyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    DatabaseSafetyManager.exportSafetyCopyToUri(context, uri)
                }
                Toast.makeText(
                    context,
                    if (ok) "Safety database copy exported." else "Could not export safety copy.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Surface(
        color = c.background,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = c.redTint),
                border = BorderStroke(1.dp, c.redBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Database upgrade error",
                        tint = c.redDebit,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Database Upgrade Could Not Complete",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your existing ledger data has NOT been deleted or wiped.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.redDebit
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = c.card),
                border = BorderStroke(1.dp, c.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "What happened",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                    Text(
                        text = state.errorMessage,
                        fontSize = 13.sp,
                        color = c.textSecondary,
                        modifier = Modifier.testTag("db_recovery_error_message")
                    )

                    if (state.hasSafetyCopy) {
                        val savedTime = if (state.safetyCopyTimestamp > 0L) {
                            LedgerDateFormatter.formatDateTimeWithYear(state.safetyCopyTimestamp)
                        } else {
                            "Before update"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = c.greenTint,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Pre-update safety copy is available (captured: $savedTime).",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = c.greenPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    actionFeedback?.let { feedback ->
                        Text(
                            text = feedback,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.redDebit
                        )
                    }

                    if (state.hasSafetyCopy) {
                        Button(
                            enabled = !isWorking,
                            onClick = {
                                isWorking = true
                                actionFeedback = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        DatabaseSafetyManager.restoreFromSafetyCopy(context)
                                    }
                                    isWorking = false
                                    if (result.isSuccess) {
                                        onRestoreCompleted()
                                    } else {
                                        actionFeedback = "Restore could not reopen the database: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("restore_safety_copy_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isWorking) "Restoring..." else "Restore from Pre-Update Safety Copy",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            enabled = !isWorking,
                            onClick = {
                                exportSafetyLauncher.launch("daily_expense_db_safety.db")
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, c.borderStrong),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("export_safety_copy_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = c.textPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Safety Copy to Phone Storage", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        enabled = !isWorking,
                        onClick = {
                            isWorking = true
                            scope.launch {
                                val retryState = withContext(Dispatchers.IO) {
                                    DatabaseSafetyManager.prepareAndVerifySync(context)
                                }
                                isWorking = false
                                if (retryState is DbStartupState.Ready) {
                                    onRestoreCompleted()
                                } else {
                                    onRetry()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, c.borderStrong),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("retry_db_open_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = c.textPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry Opening Database", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = { showDetails = !showDetails },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(if (showDetails) "Hide Technical Details" else "Show Technical Details", color = c.textMuted)
                    }

                    if (showDetails) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = c.cardElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.technicalDetail,
                                fontSize = 11.sp,
                                color = c.textMuted,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
