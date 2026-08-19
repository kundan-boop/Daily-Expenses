package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CustomerEntity
import com.example.data.TransactionEntity
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.theme.*

sealed class Screen {
    object Auth : Screen()
    object Dashboard : Screen()
    data class CustomerDetail(val customer: CustomerEntity) : Screen()
    object Settings : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(
    authViewModel: AuthViewModel = viewModel(),
    dailyExpenseViewModel: DailyExpenseViewModel = viewModel()
) {
    val userProfile by authViewModel.userProfile.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(if (userProfile.isLoggedIn) Screen.Dashboard else Screen.Auth) }

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }

    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var activeCustomerIdForEntry by remember { mutableLongStateOf(0L) }
    var initialTransactionType by remember { mutableStateOf("I_PAID") }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }

    // Intercept back button on CustomerDetail or Settings to navigate back to Dashboard
    BackHandler(enabled = currentScreen is Screen.CustomerDetail || currentScreen is Screen.Settings) {
        currentScreen = Screen.Dashboard
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
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
                    onLoginSuccess = { currentScreen = Screen.Dashboard }
                )
            } else if (userProfile.isLocked) {
                PinLockScreen(
                    authViewModel = authViewModel,
                    onUnlocked = {
                        if (currentScreen is Screen.Auth) {
                            currentScreen = Screen.Dashboard
                        }
                    }
                )
            } else {
                when (val screen = currentScreen) {
                    is Screen.Auth -> {
                        AuthScreen(
                            authViewModel = authViewModel,
                            onLoginSuccess = { currentScreen = Screen.Dashboard }
                        )
                    }

                    is Screen.Dashboard -> {
                    DashboardScreen(
                        viewModel = dailyExpenseViewModel,
                        authViewModel = authViewModel,
                        onCustomerClick = { customer ->
                            currentScreen = Screen.CustomerDetail(customer)
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
                            currentScreen = Screen.Settings
                        }
                    )
                }

                is Screen.CustomerDetail -> {
                    CustomerDetailScreen(
                        customer = screen.customer,
                        viewModel = dailyExpenseViewModel,
                        onBackClick = { currentScreen = Screen.Dashboard },
                        onAddEntryClick = { type ->
                            activeCustomerIdForEntry = screen.customer.id
                            initialTransactionType = type
                            transactionToEdit = null
                            showAddTransactionSheet = true
                        },
                        onEditEntryClick = { tx ->
                            activeCustomerIdForEntry = screen.customer.id
                            transactionToEdit = tx
                            showAddTransactionSheet = true
                        },
                        onEditCustomerClick = {
                            customerToEdit = screen.customer
                            showAddCustomerDialog = true
                        }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        authViewModel = authViewModel,
                        dailyExpenseViewModel = dailyExpenseViewModel,
                        onLogout = { currentScreen = Screen.Auth },
                        onBackClick = { currentScreen = Screen.Dashboard }
                    )
                }
            }
            }
        }
    }
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
}

