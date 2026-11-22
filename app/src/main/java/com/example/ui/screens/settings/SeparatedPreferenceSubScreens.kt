package com.example.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*
import com.example.utils.AppPreferencesManager
import com.example.utils.BackupManager
import com.example.utils.DueReminderHelper
import com.example.utils.LocalAppStrings
import com.example.utils.ThemeMode
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityLockSettingsSubScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()
    var showPinDialog by remember { mutableStateOf(false) }

    SettingsSubScreenScaffold(
        title = s.securityLockTitle,
        subtitle = s.securityLockSubtitle,
        onBack = onBack
    ) {
        val isProtected = userProfile.isBiometricLockEnabled || userProfile.isPinEnabled
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.securityLockTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isProtected) c.greenTint else c.cardElevated
                    ) {
                        Text(
                            text = if (isProtected) s.protectedBadge else s.disabledBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isProtected) c.greenPrimary else c.textMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.padding(end = 8.dp)) {
                            Text(s.phoneSecurityLock, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            Text(s.phoneSecurityLockDesc, fontSize = 12.sp, color = c.textMuted)
                        }
                    }
                    Switch(
                        checked = userProfile.isBiometricLockEnabled,
                        onCheckedChange = { enabled ->
                            authViewModel.setBiometricLock(enabled)
                            Toast.makeText(
                                context,
                                if (enabled) "App Lock enabled." else "App Lock disabled.",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.greenPrimary,
                            uncheckedThumbColor = c.textMuted,
                            uncheckedTrackColor = c.border
                        ),
                        modifier = Modifier.testTag("switch_phone_security_lock")
                    )
                }

                HorizontalDivider(color = c.border.copy(alpha = 0.7f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.padding(end = 8.dp)) {
                            Text(s.fourDigitPin, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            Text(s.fourDigitPinDesc, fontSize = 12.sp, color = c.textMuted)
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
                            checkedTrackColor = c.greenPrimary,
                            uncheckedThumbColor = c.textMuted,
                            uncheckedTrackColor = c.border
                        ),
                        modifier = Modifier.testTag("switch_pin_lock")
                    )
                }

                if (userProfile.isPinEnabled) {
                    OutlinedButton(
                        onClick = { showPinDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, c.greenPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Password, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.changePin, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.greenPrimary)
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("1234") }
        var dialogViewRef by remember { mutableStateOf<android.view.View?>(null) }
        var lastImeCloseTime by remember { mutableLongStateOf(0L) }

        AlertDialog(
            onDismissRequest = {
                val liveIme = dialogViewRef?.let {
                    ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
                } == true
                if (!liveIme && System.currentTimeMillis() - lastImeCloseTime > 300L) {
                    showPinDialog = false
                }
            },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .imePadding()
                .padding(horizontal = 15.dp),
            title = { Text(s.setPinDialogTitle, fontWeight = FontWeight.ExtraBold, color = c.textPrimary) },
            text = {
                val dialogView = LocalView.current
                val keyboardController = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                val isImeVisible = WindowInsets.isImeVisible
                var prevIme by remember { mutableStateOf(false) }

                LaunchedEffect(dialogView) { dialogViewRef = dialogView }
                LaunchedEffect(isImeVisible) {
                    if (prevIme && !isImeVisible) lastImeCloseTime = System.currentTimeMillis()
                    prevIme = isImeVisible
                }

                BackHandler(enabled = true) {
                    val liveIme = isImeVisible ||
                        (ViewCompat.getRootWindowInsets(dialogView)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                    if (liveIme) {
                        lastImeCloseTime = System.currentTimeMillis()
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    } else if (System.currentTimeMillis() - lastImeCloseTime > 300L) {
                        showPinDialog = false
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }
                            if (digitsOnly.length <= 4) pin = digitsOnly
                        },
                        label = { Text("PIN (4 digits)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = TextStyle(color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Enter any 4 numeric digits (default: 1234)", fontSize = 12.sp, color = c.greenPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pin.length == 4) {
                            authViewModel.setPin(pin, true)
                            showPinDialog = false
                            Toast.makeText(context, "PIN Lock Enabled!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary)
                ) {
                    Text(s.save, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showPinDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.borderStrong)
                ) {
                    Text(s.cancel, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                }
            }
        )
    }
}

@Composable
fun ThemeSettingsSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val themeMode by AppPreferencesManager.themeModeFlow.collectAsStateWithLifecycle()

    SettingsSubScreenScaffold(
        title = s.themeTitle,
        subtitle = s.themeSubtitle,
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
                Text(s.chooseAppTheme, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)

                val options = listOf(
                    Triple(ThemeMode.SYSTEM, s.themeSystemTitle, s.themeSystemDesc) to Icons.Default.BrightnessAuto,
                    Triple(ThemeMode.LIGHT, s.themeLightTitle, s.themeLightDesc) to Icons.Default.LightMode,
                    Triple(ThemeMode.DARK, s.themeDarkTitle, s.themeDarkDesc) to Icons.Default.DarkMode
                )

                options.forEach { (meta, icon) ->
                    val (mode, title, desc) = meta
                    val selected = themeMode == mode
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) c.greenTint else c.surfaceSubtle,
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.greenPrimary else c.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppPreferencesManager.setThemeMode(context, mode)
                            }
                            .testTag("theme_option_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (selected) c.greenPrimary else c.cardElevated,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (selected) Color.White else c.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                Text(desc, fontSize = 11.5.sp, color = c.textMuted)
                            }
                            RadioButton(
                                selected = selected,
                                onClick = { AppPreferencesManager.setThemeMode(context, mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = c.greenPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSettingsSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val selectedLanguage by AppPreferencesManager.languageFlow.collectAsStateWithLifecycle()

    SettingsSubScreenScaffold(
        title = s.languageTitle,
        subtitle = s.languageSubtitle,
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
                Text(s.chooseAppLanguage, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(s.languageApplyHint, fontSize = 12.sp, color = c.textMuted)

                val languages = listOf(
                    Triple("English", "English", "Default business ledger language"),
                    Triple("Hindi", "हिन्दी (Hindi)", "मेनू, डैशबोर्ड और खाता बही हिंदी में"),
                    Triple("Gujarati", "ગુજરાતી (Gujarati)", "મેનૂ, ડેશબોર્ડ અને ખાતાવહી ગુજરાતીમાં")
                )

                languages.forEach { (key, display, sub) ->
                    val selected = selectedLanguage.equals(key, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) c.blueTint else c.surfaceSubtle,
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.blueAccent else c.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { AppPreferencesManager.setLanguage(context, key) }
                            .testTag("language_option_${key.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (selected) c.blueAccent else c.cardElevated,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = if (selected) Color.White else c.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(display, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                Text(sub, fontSize = 11.5.sp, color = c.textMuted)
                            }
                            RadioButton(
                                selected = selected,
                                onClick = { AppPreferencesManager.setLanguage(context, key) },
                                colors = RadioButtonDefaults.colors(selectedColor = c.blueAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsSubScreen(
    dailyExpenseViewModel: DailyExpenseViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val dueReminderEnabled by AppPreferencesManager.dueReminderEnabledFlow.collectAsStateWithLifecycle()
    val reminderHour by AppPreferencesManager.dueReminderHourFlow.collectAsStateWithLifecycle()
    val reminderMinute by AppPreferencesManager.dueReminderMinuteFlow.collectAsStateWithLifecycle()
    val customerSummaries by (dailyExpenseViewModel?.customerSummaries?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList()) })

    var showTimePickerDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Notification permission denied. Enable it in Android Settings to receive due date alerts.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val dueCustomers = remember(customerSummaries) {
        customerSummaries
            .filter { it.customer.collectDueDate != null }
            .sortedBy { it.customer.collectDueDate }
    }

    SettingsSubScreenScaffold(
        title = s.notificationsTitle,
        subtitle = s.notificationsSubtitle,
        onBack = onBack
    ) {
        // Master Due Reminders & Daily Schedule Card
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
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = c.blueTint,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = c.blueAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.dueAndBackupAlerts, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (dueReminderEnabled) c.greenTint else c.cardElevated
                                ) {
                                    Text(
                                        text = if (dueReminderEnabled) "ON" else "OFF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (dueReminderEnabled) c.greenPrimary else c.textMuted,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(s.dueAndBackupAlertsDesc, fontSize = 12.sp, color = c.textMuted)
                        }
                    }
                    Switch(
                        checked = dueReminderEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !DueReminderHelper.canPostNotifications(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            AppPreferencesManager.setDueReminderEnabled(context, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.greenPrimary,
                            uncheckedThumbColor = c.textMuted,
                            uncheckedTrackColor = c.border
                        ),
                        modifier = Modifier.testTag("switch_notifications")
                    )
                }

                HorizontalDivider(color = c.border.copy(alpha = 0.6f))

                // Daily Reminder Time Row
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.cardElevated,
                    border = BorderStroke(1.dp, c.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (dueReminderEnabled) 1f else 0.55f)
                        .clickable(enabled = dueReminderEnabled) { showTimePickerDialog = true }
                        .testTag("due_reminder_time_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Daily Reminder Time", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                Text("Runs once daily for customers due or overdue", fontSize = 11.sp, color = c.textMuted)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = c.greenTint
                        ) {
                            Text(
                                text = DueReminderHelper.formatReminderTime(reminderHour, reminderMinute),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = c.greenPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = c.cardElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (dueReminderEnabled) {
                            "Daily notifications fire once per day only for enabled customers whose due date has arrived and balance is pending."
                        } else {
                            "Master switch is OFF. Turn it ON to schedule daily due-date notifications and control individual customers."
                        },
                        fontSize = 11.5.sp,
                        color = c.textSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Per-Customer Due Date Reminders Card (shows ONLY customers who have a due date)
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
                    Text(
                        text = "Customers With Due Dates",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                    Surface(shape = RoundedCornerShape(50), color = c.blueTint) {
                        Text(
                            text = "${dueCustomers.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.blueAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (dueCustomers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = c.cardElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("no_due_customers_empty_state")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "No customers have a due date yet",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                            Text(
                                text = "Set a collection due date on any customer profile to manage their reminders here.",
                                fontSize = 11.5.sp,
                                color = c.textMuted
                            )
                        }
                    }
                } else {
                    dueCustomers.forEach { summary ->
                        val cust = summary.customer
                        val dueMillis = cust.collectDueDate ?: 0L
                        val isOverdueOrToday = DueReminderHelper.isDueOrOverdue(dueMillis) && summary.netBalance > 0.01
                        val balanceText = String.format(Locale.US, "₹%,.0f", kotlin.math.abs(summary.netBalance))
                        val reminderMsg = DueReminderHelper.buildReminderMessage(cust.name, summary.netBalance, cust.collectDueDate)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = c.surfaceSubtle,
                            border = BorderStroke(1.dp, if (isOverdueOrToday && dueReminderEnabled) c.redDebit.copy(alpha = 0.4f) else c.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (dueReminderEnabled) 1f else 0.45f)
                                .testTag("due_customer_row_${cust.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = cust.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Due: ${DueReminderHelper.formatDueDateUtc(dueMillis)} • $balanceText",
                                        fontSize = 11.5.sp,
                                        color = if (isOverdueOrToday) c.redDebit else c.textMuted,
                                        fontWeight = if (isOverdueOrToday) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // User-initiated WhatsApp tap-to-send
                                IconButton(
                                    onClick = {
                                        try {
                                            context.startActivity(DueReminderHelper.buildWhatsAppIntent(cust.mobileNumber, reminderMsg))
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "WhatsApp is not installed on this device.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = dueReminderEnabled,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("customer_whatsapp_reminder_${cust.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Send WhatsApp Reminder to ${cust.name}",
                                        tint = c.greenPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // User-initiated SMS tap-to-send via ACTION_SENDTO (Play Policy compliant)
                                IconButton(
                                    onClick = {
                                        try {
                                            context.startActivity(DueReminderHelper.buildSmsIntent(cust.mobileNumber, reminderMsg))
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "No SMS app found on this device.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = dueReminderEnabled,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("customer_sms_reminder_${cust.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sms,
                                        contentDescription = "Send SMS Reminder to ${cust.name}",
                                        tint = c.blueAccent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                Switch(
                                    checked = cust.reminderEnabled,
                                    enabled = dueReminderEnabled,
                                    onCheckedChange = { enabled ->
                                        dailyExpenseViewModel?.updateCustomerReminderEnabled(cust.id, enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = c.greenPrimary,
                                        uncheckedThumbColor = c.textMuted,
                                        uncheckedTrackColor = c.border,
                                        disabledCheckedTrackColor = c.greenPrimary.copy(alpha = 0.4f),
                                        disabledUncheckedTrackColor = c.border.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.testTag("customer_due_reminder_switch_${cust.id}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Daily Reminder Time", fontWeight = FontWeight.ExtraBold, color = c.textPrimary) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AppPreferencesManager.setDueReminderTime(context, timePickerState.hour, timePickerState.minute)
                        showTimePickerDialog = false
                        Toast.makeText(
                            context,
                            "Daily reminder scheduled for ${DueReminderHelper.formatReminderTime(timePickerState.hour, timePickerState.minute)}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_due_reminder_time_button")
                ) {
                    Text(s.save, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text(s.cancel, color = c.textMuted)
                }
            }
        )
    }
}

@Composable
fun AboutPermissionsSettingsSubScreen(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current

    val contactsGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val driveConnected = remember { BackupManager.getDriveFolderName(context) != null }

    SettingsSubScreenScaffold(
        title = s.aboutPermissionsTitle,
        subtitle = s.aboutPermissionsSubtitle,
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
                Text(s.permissionsHeader, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)

                PermissionStatusRow(
                    label = s.contactsPermissionLabel,
                    status = if (contactsGranted) s.grantedStatus else s.onRequestStatus,
                    isActive = contactsGranted
                )
                PermissionStatusRow(
                    label = s.driveAccessLabel,
                    status = if (driveConnected) s.grantedStatus else s.notConfiguredStatus,
                    isActive = driveConnected
                )

                HorizontalDivider(color = c.border.copy(alpha = 0.7f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.appVersionLabel, fontSize = 13.sp, color = c.textSecondary)
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                }
                Text(
                    text = s.developedByLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.greenPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        authViewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.redDebit),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("logout_device_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.logoutFromDevice, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(label: String, status: String, isActive: Boolean) {
    val c = LedgerTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = c.textSecondary)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isActive) c.greenTint else c.cardElevated
        ) {
            Text(
                text = status,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) c.greenPrimary else c.textSecondary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
