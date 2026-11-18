package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.screens.settings.*
import com.example.ui.theme.*
import com.example.utils.AppPreferencesManager
import com.example.utils.BackupState
import com.example.utils.DriveBackupStatus
import com.example.utils.FirebaseBackupStatus
import com.example.utils.LocalAppStrings

@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    dailyExpenseViewModel: DailyExpenseViewModel? = null,
    onLogout: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    onOpenMenu: (() -> Unit)? = null,
    onOpenCustomerDetail: ((Long) -> Unit)? = null,
    initialRoute: SettingsRoute = SettingsRoute.Menu,
    openedDirectlyToSubRoute: Boolean = initialRoute != SettingsRoute.Menu,
    routeRequestId: Int = 0,
    onConsumedInitialRoute: () -> Unit = {}
) {
    var currentRoute by remember { mutableStateOf(initialRoute) }
    var enteredDirectly by remember { mutableStateOf(openedDirectlyToSubRoute) }
    var internalParentRoute by remember { mutableStateOf<SettingsRoute?>(null) }

    LaunchedEffect(routeRequestId, initialRoute, openedDirectlyToSubRoute) {
        currentRoute = initialRoute
        enteredDirectly = openedDirectlyToSubRoute
        internalParentRoute = null
        if (initialRoute != SettingsRoute.Menu) {
            onConsumedInitialRoute()
        }
    }

    LaunchedEffect(Unit) {
        dailyExpenseViewModel?.refreshDailyBackupsList()
    }

    val handleBackNavigation: () -> Unit = {
        val parent = internalParentRoute
        if (parent != null) {
            currentRoute = parent
            internalParentRoute = null
        } else if (currentRoute != SettingsRoute.Menu && !enteredDirectly) {
            currentRoute = SettingsRoute.Menu
        } else {
            onBackClick?.invoke()
        }
    }

    BackHandler(enabled = true) {
        handleBackNavigation()
    }

    when (currentRoute) {
        SettingsRoute.Profile -> ProfileSettingsSubScreen(
            authViewModel = authViewModel,
            onBack = handleBackNavigation
        )

        SettingsRoute.Summary -> SummarySettingsSubScreen(
            dailyExpenseViewModel = dailyExpenseViewModel,
            onBack = handleBackNavigation,
            onCustomerClick = onOpenCustomerDetail
        )

        SettingsRoute.SecurityLock -> SecurityLockSettingsSubScreen(
            authViewModel = authViewModel,
            onBack = handleBackNavigation
        )

        SettingsRoute.ThemeAppearance -> ThemeSettingsSubScreen(
            onBack = handleBackNavigation
        )

        SettingsRoute.Language -> LanguageSettingsSubScreen(
            onBack = handleBackNavigation
        )

        SettingsRoute.Notifications -> NotificationsSettingsSubScreen(
            onBack = handleBackNavigation
        )

        SettingsRoute.GoogleDrive -> GoogleDriveSettingsSubScreen(
            authViewModel = authViewModel,
            dailyExpenseViewModel = dailyExpenseViewModel,
            onBack = handleBackNavigation
        )

        SettingsRoute.FirebaseBackup -> FirebaseBackupSettingsSubScreen(
            authViewModel = authViewModel,
            dailyExpenseViewModel = dailyExpenseViewModel,
            onNavigateToGoogleAccount = {
                internalParentRoute = SettingsRoute.FirebaseBackup
                currentRoute = SettingsRoute.GoogleAccount
            },
            onBack = handleBackNavigation
        )

        SettingsRoute.GoogleAccount -> GoogleAccountSettingsSubScreen(
            authViewModel = authViewModel,
            dailyExpenseViewModel = dailyExpenseViewModel,
            onBack = handleBackNavigation
        )

        SettingsRoute.ImportExport -> ImportExportSettingsSubScreen(
            authViewModel = authViewModel,
            dailyExpenseViewModel = dailyExpenseViewModel,
            onBack = handleBackNavigation
        )

        SettingsRoute.Share -> ShareSettingsSubScreen(
            authViewModel = authViewModel,
            onBack = handleBackNavigation
        )

        SettingsRoute.AboutPermissions -> AboutPermissionsSettingsSubScreen(
            authViewModel = authViewModel,
            onLogout = onLogout,
            onBack = handleBackNavigation
        )

        SettingsRoute.Menu -> SettingsHubMenu(
            authViewModel = authViewModel,
            dailyExpenseViewModel = dailyExpenseViewModel,
            onNavigate = { target ->
                enteredDirectly = false
                currentRoute = target
            },
            onBackClick = onBackClick,
            onOpenMenu = onOpenMenu
        )
    }
}

@Composable
private fun SettingsHubMenu(
    authViewModel: AuthViewModel,
    dailyExpenseViewModel: DailyExpenseViewModel?,
    onNavigate: (SettingsRoute) -> Unit,
    onBackClick: (() -> Unit)?,
    onOpenMenu: (() -> Unit)?
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current

    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()
    val themeMode by AppPreferencesManager.themeModeFlow.collectAsStateWithLifecycle()
    val selectedLanguage by AppPreferencesManager.languageFlow.collectAsStateWithLifecycle()
    val notificationsEnabled by AppPreferencesManager.notificationsEnabledFlow.collectAsStateWithLifecycle()

    val driveFolderName by (dailyExpenseViewModel?.driveFolderName?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })
    val driveStatus by (dailyExpenseViewModel?.backupStatus?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(DriveBackupStatus()) })
    val firebaseStatus by (dailyExpenseViewModel?.firebaseBackupStatus?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(FirebaseBackupStatus()) })
    val googleAccount by (dailyExpenseViewModel?.googleAccountInfo?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 14.dp, horizontal = 8.dp)
            ) {
                if (onOpenMenu != null) {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Open Menu",
                            tint = Color.White
                        )
                    }
                } else if (onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s.back,
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = s.settingsTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 52.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category 1: Account & Overview
                SettingsMenuCategory(title = s.accountAndOverview) {
                    SettingsMenuItemRow(
                        icon = Icons.Default.Person,
                        iconBg = c.greenTint,
                        iconTint = c.greenPrimary,
                        title = s.profileTitle,
                        subtitle = userProfile.businessName.ifBlank { s.profileSubtitle },
                        testTag = "settings_menu_profile",
                        onClick = { onNavigate(SettingsRoute.Profile) }
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 56.dp))
                    SettingsMenuItemRow(
                        icon = Icons.Default.Assessment,
                        iconBg = c.blueTint,
                        iconTint = c.blueAccent,
                        title = s.summaryTitle,
                        subtitle = s.summarySubtitle,
                        testTag = "settings_menu_summary",
                        onClick = { onNavigate(SettingsRoute.Summary) }
                    )
                }

                // Category 2: Security & Preferences (Strictly Separated Individual Menus)
                val isProtected = userProfile.isBiometricLockEnabled || userProfile.isPinEnabled
                SettingsMenuCategory(title = s.securityPreferences) {
                    SettingsMenuItemRow(
                        icon = Icons.Default.Security,
                        iconBg = if (isProtected) c.greenTint else c.cardElevated,
                        iconTint = if (isProtected) c.greenPrimary else c.textSecondary,
                        title = s.securityLockTitle,
                        subtitle = s.securityLockSubtitle,
                        badgeText = if (isProtected) "ON" else "OFF",
                        badgeBg = if (isProtected) c.greenTint else c.cardElevated,
                        badgeColor = if (isProtected) c.greenPrimary else c.textSecondary,
                        testTag = "settings_menu_security_lock",
                        onClick = { onNavigate(SettingsRoute.SecurityLock) }
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 56.dp))
                    SettingsMenuItemRow(
                        icon = Icons.Default.DarkMode,
                        iconBg = c.indigoTint,
                        iconTint = c.indigoAccent,
                        title = s.themeTitle,
                        subtitle = s.themeSubtitle,
                        badgeText = themeMode.name,
                        badgeBg = c.indigoTint,
                        badgeColor = c.indigoAccent,
                        testTag = "settings_menu_theme",
                        onClick = { onNavigate(SettingsRoute.ThemeAppearance) }
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 56.dp))
                    SettingsMenuItemRow(
                        icon = Icons.Default.Translate,
                        iconBg = c.blueTint,
                        iconTint = c.blueAccent,
                        title = s.languageTitle,
                        subtitle = s.languageSubtitle,
                        badgeText = selectedLanguage.uppercase(),
                        badgeBg = c.blueTint,
                        badgeColor = c.blueAccent,
                        testTag = "settings_menu_language",
                        onClick = { onNavigate(SettingsRoute.Language) }
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 56.dp))
                    SettingsMenuItemRow(
                        icon = Icons.Default.NotificationsActive,
                        iconBg = c.amberTint,
                        iconTint = c.amberAccent,
                        title = s.notificationsTitle,
                        subtitle = s.notificationsSubtitle,
                        badgeText = if (notificationsEnabled) "ON" else "OFF",
                        badgeBg = if (notificationsEnabled) c.greenTint else c.cardElevated,
                        badgeColor = if (notificationsEnabled) c.greenPrimary else c.textSecondary,
                        testTag = "settings_menu_notifications",
                        onClick = { onNavigate(SettingsRoute.Notifications) }
                    )
                }

                // Category 3: Cloud, Backups & Data (Strictly Separated Individual Menus)
                val driveBadge = when {
                    driveFolderName == null -> "NOT SET"
                    driveStatus.state == BackupState.PENDING_OFFLINE -> "QUEUED"
                    driveStatus.state == BackupState.FAILED -> "RETRY"
                    else -> "ACTIVE"
                }
                val firebaseBadge = when {
                    googleAccount == null -> "SIGN IN"
                    firebaseStatus.state == BackupState.PENDING_OFFLINE -> "QUEUED"
                    firebaseStatus.state == BackupState.FAILED -> "ERROR"
                    else -> "${firebaseStatus.snapshotCount}/7"
                }
                SettingsMenuCategory(title = s.cloudBackupsData) {
                    SettingsMenuItemRow(
                        icon = Icons.Default.AddToDrive,
                        iconBg = c.blueTint,
                        iconTint = c.blueAccent,
                        title = s.googleDriveTitle,
                        subtitle = driveFolderName?.let { "DailyExpenseBackups • $it" } ?: s.googleDriveSubtitleEmpty,
                        badgeText = driveBadge,
                        badgeBg = if (driveFolderName != null) c.blueTint else c.cardElevated,
                        badgeColor = if (driveFolderName != null) c.blueAccent else c.textSecondary,
                        testTag = "settings_menu_google_drive",
                        onClick = { onNavigate(SettingsRoute.GoogleDrive) }
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 56.dp))
                    SettingsMenuItemRow(
                        icon = Icons.Default.CloudQueue,
                        iconBg = c.greenTint,
                        iconTint = c.greenPrimary,
                        title = s.firebaseBackupTitle,
                        subtitle = if (googleAccount != null) s.firebaseBackupSubtitleSignedIn else s.firebaseBackupSubtitleSignedOut,
                        badgeText = firebaseBadge,
                        badgeBg = if (googleAccount != null) c.greenTint else c.cardElevated,
                        badgeColor = if (googleAccount != null) c.greenPrimary else c.textSecondary,
                        testTag = "settings_menu_firebase_backup",
                        onClick = { onNavigate(SettingsRoute.FirebaseBackup) }
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 56.dp))
                    SettingsMenuItemRow(
                        icon = Icons.Default.AccountCircle,
                        iconBg = c.indigoTint,
                        iconTint = c.indigoAccent,
                        title = s.googleAccountTitle,
                        subtitle = googleAccount?.email ?: s.googleAccountSubtitleEmpty,
                        badgeText = if (googleAccount != null) "CONNECTED" else null,
                        badgeBg = c.greenTint,
                        badgeColor = c.greenPrimary,
                        testTag = "settings_menu_google_account",
                        onClick = { onNavigate(SettingsRoute.GoogleAccount) }
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 56.dp))
                    SettingsMenuItemRow(
                        icon = Icons.Default.ImportExport,
                        iconBg = c.amberTint,
                        iconTint = c.amberAccent,
                        title = s.importExportTitle,
                        subtitle = s.importExportSubtitle,
                        testTag = "settings_menu_import_export",
                        onClick = { onNavigate(SettingsRoute.ImportExport) }
                    )
                }

                // Category 4: App & Account
                SettingsMenuCategory(title = s.appAndAccount) {
                    SettingsMenuItemRow(
                        icon = Icons.Default.Share,
                        iconBg = c.greenTint,
                        iconTint = c.greenPrimary,
                        title = s.shareAppTitle,
                        subtitle = s.shareAppSubtitle,
                        testTag = "settings_menu_share",
                        onClick = { onNavigate(SettingsRoute.Share) }
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(start = 56.dp))
                    SettingsMenuItemRow(
                        icon = Icons.Default.Info,
                        iconBg = c.redTint,
                        iconTint = c.redDebit,
                        title = s.aboutPermissionsTitle,
                        subtitle = s.aboutPermissionsSubtitle,
                        testTag = "settings_menu_about_permissions",
                        onClick = { onNavigate(SettingsRoute.AboutPermissions) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SettingsMenuCategory(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LedgerTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = c.textMuted,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            border = BorderStroke(1.dp, c.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsMenuItemRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeBg: Color? = null,
    badgeColor: Color? = null,
    testTag: String,
    onClick: () -> Unit
) {
    val c = LedgerTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconBg,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!badgeText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = badgeBg ?: c.cardElevated,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor ?: c.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
