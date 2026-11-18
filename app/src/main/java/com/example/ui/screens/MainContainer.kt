package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CustomerEntity
import com.example.data.TransactionEntity
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.screens.settings.SettingsRoute
import com.example.ui.theme.*
import com.example.utils.LocalAppStrings
import kotlinx.coroutines.launch
import java.util.Locale

sealed class Screen {
    object Auth : Screen()
    object HomeDashboard : Screen()
    object Dashboard : Screen() // Customer Ledger / Customer List
    object MonthlyExpenses : Screen()
    object Reports : Screen()
    data class CustomerDetail(val customerId: Long) : Screen()
    object Settings : Screen()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainContainer(
    authViewModel: AuthViewModel = viewModel(),
    dailyExpenseViewModel: DailyExpenseViewModel = viewModel()
) {
    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()
    val dashboardSummary by dailyExpenseViewModel.dashboardSummary.collectAsStateWithLifecycle()
    val customerSummaries by dailyExpenseViewModel.customerSummaries.collectAsStateWithLifecycle()

    var currentScreen by remember {
        mutableStateOf<Screen>(if (userProfile.isLoggedIn) Screen.HomeDashboard else Screen.Auth)
    }
    var lastTopLevelScreen by remember { mutableStateOf<Screen>(Screen.HomeDashboard) }
    var customerDetailReturnScreen by remember { mutableStateOf<Screen>(Screen.HomeDashboard) }

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var activeCustomerIdForEntry by remember { mutableLongStateOf(0L) }
    var initialTransactionType by remember { mutableStateOf("I_PAID") }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }

    // Quick Customer Picker for adding a transaction directly from Main Dashboard or Hamburger Menu
    var showCustomerPickerForTx by remember { mutableStateOf(false) }
    var pendingQuickTxType by remember { mutableStateOf("I_PAID") }
    var settingsInitialRoute by remember { mutableStateOf(SettingsRoute.Menu) }
    var settingsOpenedDirectly by remember { mutableStateOf(false) }
    var settingsRouteRequestId by remember { mutableIntStateOf(0) }
    var settingsOriginScreen by remember { mutableStateOf<Screen>(Screen.HomeDashboard) }

    val pendingProfileRestore by dailyExpenseViewModel.pendingProfileRestore.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val openQuickTxFlow: (String) -> Unit = { txType ->
        pendingQuickTxType = txType
        showCustomerPickerForTx = true
    }

    val navigateToTopLevel: (Screen) -> Unit = { target ->
        if (target == Screen.HomeDashboard || target == Screen.Dashboard) {
            lastTopLevelScreen = target
        }
        currentScreen = target
        scope.launch { drawerState.close() }
    }

    val openSettingsRoute: (SettingsRoute, Boolean) -> Unit = { route, direct ->
        if (currentScreen !is Screen.Settings) {
            settingsOriginScreen = currentScreen
        }
        settingsInitialRoute = route
        settingsOpenedDirectly = direct
        settingsRouteRequestId += 1
        navigateToTopLevel(Screen.Settings)
    }

    // Close drawer first on Back if open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Back handler for secondary top-level pages (Customer Ledger, Monthly Expenses, Reports)
    // Note: Screen.CustomerDetail and Screen.Settings manage their own deterministic BackHandler independently.
    BackHandler(
        enabled = !drawerState.isOpen &&
            (currentScreen is Screen.Dashboard ||
                currentScreen is Screen.MonthlyExpenses ||
                currentScreen is Screen.Reports)
    ) {
        currentScreen = Screen.HomeDashboard
        lastTopLevelScreen = Screen.HomeDashboard
    }

    val isDrawerEnabled = userProfile.isLoggedIn &&
        !userProfile.isLocked &&
        currentScreen !is Screen.Auth &&
        currentScreen !is Screen.CustomerDetail

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isDrawerEnabled,
        drawerContent = {
            AppNavigationDrawerContent(
                userName = userProfile.name,
                businessName = userProfile.businessName,
                phone = userProfile.phone,
                netBalance = dashboardSummary.totalYouWillReceive - dashboardSummary.totalYouNeedToPay,
                customersCount = dashboardSummary.totalCustomersCount,
                currentScreen = currentScreen,
                activeSettingsRoute = if (currentScreen is Screen.Settings) settingsInitialRoute else null,
                onSelectScreen = { target ->
                    if (target is Screen.Settings) {
                        openSettingsRoute(SettingsRoute.Menu, false)
                    } else {
                        navigateToTopLevel(target)
                    }
                },
                onOpenSettingsSubRoute = { route ->
                    openSettingsRoute(route, true)
                },
                onQuickTransaction = { txType ->
                    scope.launch { drawerState.close() }
                    openQuickTxFlow(txType)
                },
                onAddCustomer = {
                    scope.launch { drawerState.close() }
                    customerToEdit = null
                    showAddCustomerDialog = true
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1024.dp)
                ) {
                    if (!userProfile.isLoggedIn) {
                        AuthScreen(
                            authViewModel = authViewModel,
                            onLoginSuccess = {
                                currentScreen = Screen.HomeDashboard
                                lastTopLevelScreen = Screen.HomeDashboard
                            }
                        )
                    } else if (userProfile.isLocked) {
                        PinLockScreen(
                            authViewModel = authViewModel,
                            onUnlocked = {
                                if (currentScreen is Screen.Auth) {
                                    currentScreen = Screen.HomeDashboard
                                    lastTopLevelScreen = Screen.HomeDashboard
                                }
                            }
                        )
                    } else {
                        when (val screen = currentScreen) {
                            is Screen.Auth -> {
                                AuthScreen(
                                    authViewModel = authViewModel,
                                    onLoginSuccess = {
                                        currentScreen = Screen.HomeDashboard
                                        lastTopLevelScreen = Screen.HomeDashboard
                                    }
                                )
                            }

                            is Screen.HomeDashboard -> {
                                HomeDashboardScreen(
                                    viewModel = dailyExpenseViewModel,
                                    authViewModel = authViewModel,
                                    onOpenMenu = { scope.launch { drawerState.open() } },
                                    onQuickAddTransaction = openQuickTxFlow,
                                    onAddCustomerClick = {
                                        customerToEdit = null
                                        showAddCustomerDialog = true
                                    },
                                    onOpenCustomerList = {
                                        lastTopLevelScreen = Screen.Dashboard
                                        currentScreen = Screen.Dashboard
                                    },
                                    onCustomerClick = { customer ->
                                        lastTopLevelScreen = Screen.HomeDashboard
                                        customerDetailReturnScreen = Screen.HomeDashboard
                                        currentScreen = Screen.CustomerDetail(customer.id)
                                    },
                                    onOpenSettings = {
                                        openSettingsRoute(SettingsRoute.Menu, false)
                                    },
                                    onOpenGoogleDriveSettings = {
                                        openSettingsRoute(SettingsRoute.GoogleDrive, true)
                                    },
                                    onOpenFirebaseSettings = {
                                        openSettingsRoute(SettingsRoute.FirebaseBackup, true)
                                    }
                                )
                            }

                            is Screen.Dashboard -> {
                                DashboardScreen(
                                    viewModel = dailyExpenseViewModel,
                                    authViewModel = authViewModel,
                                    onCustomerClick = { customer ->
                                        dailyExpenseViewModel.clearCustomerSearchQuery()
                                        lastTopLevelScreen = Screen.Dashboard
                                        customerDetailReturnScreen = Screen.Dashboard
                                        currentScreen = Screen.CustomerDetail(customer.id)
                                    },
                                    onAddCustomerClick = {
                                        customerToEdit = null
                                        showAddCustomerDialog = true
                                    },
                                    onEditCustomerClick = { customer ->
                                        customerToEdit = customer
                                        showAddCustomerDialog = true
                                    },
                                    onOpenSettings = {
                                        openSettingsRoute(SettingsRoute.Menu, false)
                                    },
                                    onOpenGoogleDriveSettings = {
                                        openSettingsRoute(SettingsRoute.GoogleDrive, true)
                                    },
                                    onOpenFirebaseSettings = {
                                        openSettingsRoute(SettingsRoute.FirebaseBackup, true)
                                    },
                                    onOpenMenu = { scope.launch { drawerState.open() } }
                                )
                            }

                            is Screen.MonthlyExpenses -> {
                                MonthlyExpenseScreen(
                                    viewModel = dailyExpenseViewModel,
                                    onOpenMenu = { scope.launch { drawerState.open() } }
                                )
                            }

                            is Screen.Reports -> {
                                ReportsScreen(
                                    viewModel = dailyExpenseViewModel,
                                    onOpenMenu = { scope.launch { drawerState.open() } }
                                )
                            }

                            is Screen.CustomerDetail -> {
                                CustomerDetailScreen(
                                    customerId = screen.customerId,
                                    viewModel = dailyExpenseViewModel,
                                    onBackClick = {
                                        dailyExpenseViewModel.clearTransactionSearchQuery()
                                        currentScreen = customerDetailReturnScreen
                                    },
                                    onAddEntryClick = { type ->
                                        activeCustomerIdForEntry = screen.customerId
                                        initialTransactionType = type
                                        transactionToEdit = null
                                        showAddTransactionSheet = true
                                    },
                                    onEditEntryClick = { tx ->
                                        activeCustomerIdForEntry = screen.customerId
                                        transactionToEdit = tx
                                        showAddTransactionSheet = true
                                    },
                                    onEditCustomerClick = { liveCustomer ->
                                        customerToEdit = liveCustomer
                                        showAddCustomerDialog = true
                                    }
                                )
                            }

                            is Screen.Settings -> {
                                SettingsScreen(
                                    authViewModel = authViewModel,
                                    dailyExpenseViewModel = dailyExpenseViewModel,
                                    onLogout = { currentScreen = Screen.Auth },
                                    onBackClick = { currentScreen = settingsOriginScreen },
                                    onOpenMenu = { scope.launch { drawerState.open() } },
                                    onOpenCustomerDetail = { custId ->
                                        settingsInitialRoute = SettingsRoute.Summary
                                        customerDetailReturnScreen = Screen.Settings
                                        currentScreen = Screen.CustomerDetail(custId)
                                    },
                                    initialRoute = settingsInitialRoute,
                                    openedDirectlyToSubRoute = settingsOpenedDirectly,
                                    routeRequestId = settingsRouteRequestId
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Customer Picker Dialog (launched when adding transaction from Dashboard or Drawer Menu)
    if (showCustomerPickerForTx) {
        QuickCustomerPickerDialog(
            customers = customerSummaries.map { it.customer },
            transactionType = pendingQuickTxType,
            onCustomerSelected = { customer ->
                showCustomerPickerForTx = false
                activeCustomerIdForEntry = customer.id
                initialTransactionType = pendingQuickTxType
                transactionToEdit = null
                showAddTransactionSheet = true
            },
            onAddNewCustomer = {
                showCustomerPickerForTx = false
                customerToEdit = null
                showAddCustomerDialog = true
            },
            onDismiss = { showCustomerPickerForTx = false }
        )
    }

    // Add / Edit Customer Dialog
    if (showAddCustomerDialog) {
        AddCustomerDialog(
            customerToEdit = customerToEdit,
            viewModel = dailyExpenseViewModel,
            onDismiss = {
                showAddCustomerDialog = false
                customerToEdit = null
            }
        )
    }

    // Add / Edit Transaction Entry Bottom Sheet
    if (showAddTransactionSheet && activeCustomerIdForEntry != 0L) {
        AddTransactionSheet(
            customerId = activeCustomerIdForEntry,
            initialType = initialTransactionType,
            transactionToEdit = transactionToEdit,
            viewModel = dailyExpenseViewModel,
            onDismiss = {
                showAddTransactionSheet = false
                activeCustomerIdForEntry = 0L
                transactionToEdit = null
            }
        )
    }

    // Global Profile Conflict Confirmation Dialog when restoring a backup with differing profile data
    pendingProfileRestore?.let { pending ->
        val c = LedgerTheme.colors
        val s = LocalAppStrings.current
        AlertDialog(
            onDismissRequest = {
                dailyExpenseViewModel.resolvePendingProfileRestore(replace = false, authViewModel = authViewModel)
            },
            containerColor = c.card,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = s.replaceProfileTitle,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.textPrimary,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The restored backup contains profile details that differ from your current device profile. Replace current profile with the backed-up profile?",
                        fontSize = 13.5.sp,
                        color = c.textSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = c.cardElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Name: ${pending.name.ifBlank { "—" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            if (pending.businessName.isNotBlank()) {
                                Text("Business: ${pending.businessName}", fontSize = 12.sp, color = c.textSecondary)
                            }
                            if (pending.phone.isNotBlank()) {
                                Text("Phone: ${pending.phone}", fontSize = 12.sp, color = c.textSecondary)
                            }
                            if (pending.email.isNotBlank()) {
                                Text("Email: ${pending.email}", fontSize = 12.sp, color = c.textSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        dailyExpenseViewModel.resolvePendingProfileRestore(replace = true, authViewModel = authViewModel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.greenPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_replace_profile_button")
                ) {
                    Text(s.replaceButton, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        dailyExpenseViewModel.resolvePendingProfileRestore(replace = false, authViewModel = authViewModel)
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.borderStrong),
                    modifier = Modifier.testTag("keep_current_profile_button")
                ) {
                    Text(s.keepCurrentButton, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun AppNavigationDrawerContent(
    userName: String,
    businessName: String,
    phone: String,
    netBalance: Double,
    customersCount: Int,
    currentScreen: Screen,
    activeSettingsRoute: SettingsRoute? = null,
    onSelectScreen: (Screen) -> Unit,
    onOpenSettingsSubRoute: (SettingsRoute) -> Unit,
    onQuickTransaction: (String) -> Unit,
    onAddCustomer: () -> Unit
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    val balanceText = remember(netBalance) {
        val absVal = kotlin.math.abs(netBalance)
        val amt = if (absVal % 1.0 == 0.0) {
            String.format(Locale.US, "₹%,.0f", absVal)
        } else {
            String.format(Locale.US, "₹%,.2f", absVal)
        }
        if (netBalance < -0.01) "-$amt" else amt
    }

    ModalDrawerSheet(
        drawerContainerColor = c.card,
        modifier = Modifier.widthIn(max = 310.dp)
    ) {
        // Drawer Header Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(c.brandHeaderGradientEnd, c.brandHeader)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.take(1).uppercase().ifBlank { "D" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = businessName.ifBlank { "General Store" },
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$userName • $phone",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.netBalance,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = balanceText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Category 1: Overview
            DrawerCategoryLabel(s.drawerOverview)

            DrawerMenuItem(
                label = s.dashboard,
                icon = Icons.Default.Dashboard,
                selected = currentScreen is Screen.HomeDashboard,
                testTag = "drawer_item_dashboard",
                onClick = { onSelectScreen(Screen.HomeDashboard) }
            )

            DrawerMenuItem(
                label = s.customerLedger,
                badgeText = "$customersCount",
                icon = Icons.Default.Groups,
                selected = currentScreen is Screen.Dashboard,
                testTag = "drawer_item_customers",
                onClick = { onSelectScreen(Screen.Dashboard) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp), color = c.border)

            // Category 2: Compressed Transactions & Finance
            DrawerCategoryLabel(s.drawerTransactionsFinance)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrawerQuickPillButton(
                    label = s.quickPaidPill,
                    icon = Icons.Default.ArrowUpward,
                    bg = c.redTint,
                    fg = c.redDebit,
                    border = c.redBorder,
                    testTag = "drawer_item_quick_paid",
                    onClick = { onQuickTransaction("I_PAID") },
                    modifier = Modifier.weight(1f)
                )
                DrawerQuickPillButton(
                    label = s.quickGotPill,
                    icon = Icons.Default.ArrowDownward,
                    bg = c.greenTint,
                    fg = c.greenPrimary,
                    border = c.greenBorder,
                    testTag = "drawer_item_quick_received",
                    onClick = { onQuickTransaction("I_RECEIVED") },
                    modifier = Modifier.weight(1f)
                )
                DrawerQuickPillButton(
                    label = s.quickCustomerPill,
                    icon = Icons.Default.PersonAdd,
                    bg = c.blueTint,
                    fg = c.blueAccent,
                    border = c.blueBorder,
                    testTag = "drawer_item_add_customer",
                    onClick = onAddCustomer,
                    modifier = Modifier.weight(1f)
                )
            }

            DrawerMenuItem(
                label = s.monthlyExpenses,
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                selected = currentScreen is Screen.MonthlyExpenses,
                testTag = "drawer_item_monthly_expenses",
                onClick = { onSelectScreen(Screen.MonthlyExpenses) }
            )

            DrawerMenuItem(
                label = s.reportsAnalytics,
                icon = Icons.Default.BarChart,
                selected = currentScreen is Screen.Reports,
                testTag = "drawer_item_reports",
                onClick = { onSelectScreen(Screen.Reports) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = c.border)

            // Category 3: Account & Overview (as per Screenshot 1)
            DrawerCategoryLabel(s.accountAndOverview)

            DrawerMenuItem(
                label = s.profileTitle,
                icon = Icons.Default.Person,
                selected = activeSettingsRoute == SettingsRoute.Profile,
                testTag = "drawer_item_profile",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.Profile) }
            )

            DrawerMenuItem(
                label = s.summaryTitle,
                icon = Icons.Default.Assessment,
                selected = activeSettingsRoute == SettingsRoute.Summary,
                testTag = "drawer_item_summary",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.Summary) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = c.border)

            // Category 4: Security & Preferences (as per Screenshot 1)
            DrawerCategoryLabel(s.securityPreferences)

            DrawerMenuItem(
                label = s.securityLockTitle,
                icon = Icons.Default.Security,
                selected = activeSettingsRoute == SettingsRoute.SecurityLock,
                testTag = "drawer_item_security_lock",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.SecurityLock) }
            )

            DrawerMenuItem(
                label = s.themeTitle,
                icon = Icons.Default.DarkMode,
                selected = activeSettingsRoute == SettingsRoute.ThemeAppearance,
                testTag = "drawer_item_theme",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.ThemeAppearance) }
            )

            DrawerMenuItem(
                label = s.languageTitle,
                icon = Icons.Default.Translate,
                selected = activeSettingsRoute == SettingsRoute.Language,
                testTag = "drawer_item_language",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.Language) }
            )

            DrawerMenuItem(
                label = s.notificationsTitle,
                icon = Icons.Default.NotificationsActive,
                selected = activeSettingsRoute == SettingsRoute.Notifications,
                testTag = "drawer_item_notifications",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.Notifications) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = c.border)

            // Category 5: Cloud, Backups & Data (as per Screenshot 1)
            DrawerCategoryLabel(s.cloudBackupsData)

            DrawerMenuItem(
                label = s.driveBackupsShort,
                icon = Icons.Default.AddToDrive,
                selected = activeSettingsRoute == SettingsRoute.GoogleDrive,
                testTag = "drawer_item_drive_backups",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.GoogleDrive) }
            )

            DrawerMenuItem(
                label = s.firebaseBackupShort,
                icon = Icons.Default.CloudQueue,
                selected = activeSettingsRoute == SettingsRoute.FirebaseBackup,
                testTag = "drawer_item_firebase_backups",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.FirebaseBackup) }
            )

            DrawerMenuItem(
                label = s.googleAccountTitle,
                icon = Icons.Default.AccountCircle,
                selected = activeSettingsRoute == SettingsRoute.GoogleAccount,
                testTag = "drawer_item_google_account",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.GoogleAccount) }
            )

            DrawerMenuItem(
                label = s.pdfImportExportShort,
                icon = Icons.Default.PictureAsPdf,
                selected = activeSettingsRoute == SettingsRoute.ImportExport,
                testTag = "drawer_item_pdf_tools",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.ImportExport) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = c.border)

            // Category 6: App & Account (as per Screenshot 1)
            DrawerCategoryLabel(s.appAndAccount)

            DrawerMenuItem(
                label = s.shareAppTitle,
                icon = Icons.Default.Share,
                selected = activeSettingsRoute == SettingsRoute.Share,
                testTag = "drawer_item_share_app",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.Share) }
            )

            DrawerMenuItem(
                label = s.aboutPermissionsTitle,
                icon = Icons.Default.Info,
                selected = activeSettingsRoute == SettingsRoute.AboutPermissions,
                testTag = "drawer_item_about_permissions",
                onClick = { onOpenSettingsSubRoute(SettingsRoute.AboutPermissions) }
            )

            DrawerMenuItem(
                label = s.allSettings,
                icon = Icons.Default.Settings,
                selected = currentScreen is Screen.Settings && activeSettingsRoute == SettingsRoute.Menu,
                testTag = "drawer_item_settings",
                onClick = { onSelectScreen(Screen.Settings) }
            )
        }
    }
}

@Composable
private fun DrawerCategoryLabel(title: String) {
    val c = LedgerTheme.colors
    Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = c.textMuted,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun DrawerQuickPillButton(
    label: String,
    icon: ImageVector,
    bg: Color,
    fg: Color,
    border: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    iconTint: Color? = null,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val c = LedgerTheme.colors
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: if (selected) c.greenPrimary else c.textSecondary,
                modifier = Modifier.size(19.dp)
            )
        },
        badge = if (badgeText != null) {
            {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) c.greenPrimary else c.cardElevated
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else c.textSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        } else null,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = c.greenTint,
            selectedTextColor = c.greenPrimary,
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = c.textPrimary
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .height(40.dp)
            .testTag(testTag)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickCustomerPickerDialog(
    customers: List<CustomerEntity>,
    transactionType: String,
    onCustomerSelected: (CustomerEntity) -> Unit,
    onAddNewCustomer: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LedgerTheme.colors
    val s = LocalAppStrings.current
    var searchText by remember { mutableStateOf("") }
    val filteredCustomers = remember(customers, searchText) {
        val clean = searchText.trim()
        if (clean.isBlank()) customers
        else customers.filter {
            it.name.contains(clean, ignoreCase = true) ||
                it.mobileNumber.contains(clean, ignoreCase = true)
        }
    }

    var dialogViewRef by remember { mutableStateOf<android.view.View?>(null) }
    var lastImeCloseTime by remember { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = {
            val liveIme = dialogViewRef?.let {
                ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
            } == true
            if (!liveIme && System.currentTimeMillis() - lastImeCloseTime > 300L) {
                onDismiss()
            }
        },
        containerColor = c.card,
        shape = RoundedCornerShape(20.dp),
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false
        ),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .imePadding()
            .padding(horizontal = 16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (transactionType == "I_PAID") s.selectCustomerPaid else s.selectCustomerReceived,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.textPrimary
                    )
                    Text(
                        text = s.selectCustomerHint,
                        fontSize = 12.sp,
                        color = c.textMuted
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = s.close, tint = c.textSecondary)
                }
            }
        },
        text = {
            val view = LocalView.current
            val isImeVisible = WindowInsets.isImeVisible
            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            var prevIme by remember { mutableStateOf(false) }

            LaunchedEffect(view) { dialogViewRef = view }
            LaunchedEffect(isImeVisible) {
                if (prevIme && !isImeVisible) {
                    lastImeCloseTime = System.currentTimeMillis()
                }
                prevIme = isImeVisible
            }

            BackHandler(enabled = true) {
                val liveIme = isImeVisible ||
                    (ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                if (liveIme) {
                    lastImeCloseTime = System.currentTimeMillis()
                    keyboardController?.hide()
                    focusManager.clearFocus()
                } else if (System.currentTimeMillis() - lastImeCloseTime > 300L) {
                    onDismiss()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text(s.searchCustomersPlaceholder, fontSize = 13.sp, color = c.textMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = c.greenPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (filteredCustomers.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (customers.isEmpty()) s.noCustomersYet else s.noCustomersFound,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = s.tapPlusToAddCustomer,
                            fontSize = 12.sp,
                            color = c.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { cust ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = c.surfaceSubtle,
                                border = BorderStroke(1.dp, c.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCustomerSelected(cust) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(c.greenTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cust.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = c.greenPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cust.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = c.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (cust.mobileNumber.isNotBlank()) {
                                            Text(
                                                text = cust.mobileNumber,
                                                fontSize = 11.5.sp,
                                                color = c.textMuted
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = c.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onAddNewCustomer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, c.greenPrimary)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = c.greenPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(s.addNewCustomer, color = c.greenPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel, color = c.textSecondary)
            }
        }
    )
}
