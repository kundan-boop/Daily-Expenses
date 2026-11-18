package com.example.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.AuthViewModel
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareSettingsSubScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val shareApkWithText: (useWhatsAppOnly: Boolean) -> Unit = { useWhatsApp ->
        try {
            authViewModel.suppressAutoLockOnce()

            val srcApk = File(context.applicationInfo.sourceDir)
            val destApk = File(context.filesDir, "DailyExpenseLedgerApp.apk")
            srcApk.copyTo(destApk, overwrite = true)

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destApk
            )

            val shareText = """
                📱 *Daily Expense & Ledger*
                Smart Business Khatabook, Expense Tracker & Cloud Ledger.

                ✨ *Core Application Features:*
                1. 📊 Real-Time Business Dashboard & Net Balance Analytics
                2. 📒 Customer Khatabook Ledger with Running Balance Statements
                3. 💳 Payment Modes (Cash, UPI, Bank, Card) & Custom Categories
                4. 📅 Collect Today & Due Date Reminder Filters
                5. 💸 Automated Deduction Expense Accounts Tracking
                6. 🧾 Monthly Shop Expenses with Compressed Bill Receipts
                7. 📄 Multi-PDF Import & Custom Period PDF Statement Export
                8. 🖼️ Full-Screen Zoomable Receipt Gallery & Album Download
                9. ☁️ 7-Day Google Drive & Firebase Cloud Backups + JSON Restore
                10. 🔒 Biometric / 4-Digit PIN Lock, Dark Mode & Multi-Language (EN/HI/GU)

                Download and install the attached APK to manage your shop ledger effortlessly!
            """.trimIndent()

            val clipData = ClipData.newRawUri("Daily Expense & Ledger App", apkUri).apply {
                addItem(ClipData.Item(shareText))
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
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                context.grantUriPermission(
                    resolveInfo.activityInfo.packageName,
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            if (useWhatsApp) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Daily Expense & Ledger App Info", shareText))
                Toast.makeText(context, "App promo message copied! Paste it in WhatsApp chat.", Toast.LENGTH_LONG).show()

                val waIntent = Intent(shareIntent).apply { setPackage("com.whatsapp") }
                try {
                    context.startActivity(waIntent)
                } catch (_: Exception) {
                    context.startActivity(Intent.createChooser(shareIntent, "Share App APK via"))
                }
            } else {
                context.startActivity(Intent.createChooser(shareIntent, "Share App APK & Promo via"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val c = LedgerTheme.colors
    val s = LocalAppStrings.current

    SettingsSubScreenScaffold(
        title = s.shareAppTitle,
        subtitle = s.shareAppSubtitle,
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = c.greenTint,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.shareAppTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text(s.shareAppSubtitle, fontSize = 12.sp, color = c.textMuted)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceSubtle)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(s.coreFeaturesOverview, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    FeatureHighlightRow(
                        icon = Icons.Default.BarChart,
                        iconBg = c.greenTint,
                        iconTint = c.greenPrimary,
                        title = "Real-Time Dashboard & Financial Analytics",
                        subtitle = "Live net balance, You'll Get / You Gave totals & visual breakdown charts"
                    )
                    FeatureHighlightRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        iconBg = c.greenTint,
                        iconTint = c.greenPrimary,
                        title = "Customer Khatabook & Running Ledger",
                        subtitle = "Credit/debit entries with Cash/UPI/Bank/Card modes & running per-entry balance"
                    )
                    FeatureHighlightRow(
                        icon = Icons.Default.EventAvailable,
                        iconBg = c.amberTint,
                        iconTint = c.amberAccent,
                        title = "Due Date Tracking & Deduction Accounts",
                        subtitle = "Collect Today due filters plus automated Deduction expense account totals"
                    )
                    FeatureHighlightRow(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        iconBg = c.blueTint,
                        iconTint = c.blueAccent,
                        title = "Monthly Expenses & Receipt Gallery",
                        subtitle = "Track monthly shop expenses with pinch-to-zoom receipt viewer & gallery save"
                    )
                    FeatureHighlightRow(
                        icon = Icons.Default.PictureAsPdf,
                        iconBg = c.indigoTint,
                        iconTint = c.indigoAccent,
                        title = "Multi-PDF Import & Statement Export",
                        subtitle = "Import OkCredit / Ledger PDFs & export custom Month, Year or Date Range reports"
                    )
                    FeatureHighlightRow(
                        icon = Icons.Default.CloudDone,
                        iconBg = c.blueTint,
                        iconTint = c.blueAccent,
                        title = "Dual 7-Day Cloud Backups & App Lock",
                        subtitle = "Google Drive & Firebase snapshots, Biometric/PIN lock, Dark Mode & EN/HI/GU languages"
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { shareApkWithText(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .widthIn(min = 140.dp)
                            .heightIn(min = 48.dp)
                            .testTag("share_whatsapp_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.shareViaWhatsApp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 12.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { shareApkWithText(false) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary),
                        border = BorderStroke(1.5.dp, c.borderStrong),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .widthIn(min = 140.dp)
                            .heightIn(min = 48.dp)
                            .testTag("share_other_apps_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.shareOtherApps, fontWeight = FontWeight.Bold, color = c.textPrimary, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    val c = LedgerTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = iconBg,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text(subtitle, fontSize = 11.sp, color = c.textMuted)
        }
    }
}
