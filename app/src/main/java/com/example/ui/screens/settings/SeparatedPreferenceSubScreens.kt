package com.example.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.AuthViewModel
import com.example.ui.theme.*
import com.example.utils.AppPreferencesManager
import com.example.utils.BackupManager
import com.example.utils.LocalAppStrings
import com.example.utils.ThemeMode

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

@Composable
fun NotificationsSettingsSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val notificationsEnabled by AppPreferencesManager.notificationsEnabledFlow.collectAsStateWithLifecycle()

    SettingsSubScreenScaffold(
        title = s.notificationsTitle,
        subtitle = s.notificationsSubtitle,
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
                            Text(s.dueAndBackupAlerts, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            Text(s.dueAndBackupAlertsDesc, fontSize = 12.sp, color = c.textMuted)
                        }
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            AppPreferencesManager.setNotificationsEnabled(context, enabled)
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

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = c.cardElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = s.alertsInfoNote,
                        fontSize = 12.sp,
                        color = c.textSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
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
