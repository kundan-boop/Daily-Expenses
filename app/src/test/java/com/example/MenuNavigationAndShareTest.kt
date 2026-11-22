package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CustomerEntity
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.settings.FirebaseBackupSettingsSubScreen
import com.example.ui.screens.settings.ImportExportSettingsSubScreen
import com.example.ui.screens.settings.NotificationsSettingsSubScreen
import com.example.ui.screens.settings.SettingsRoute
import com.example.ui.screens.settings.ShareSettingsSubScreen
import com.example.ui.theme.DailyExpenseTheme
import com.example.utils.AppPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MenuNavigationAndShareTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun directGoogleDriveEntry_backReturnsDirectlyToCallerInsteadOfSettingsAll() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val authViewModel = AuthViewModel(app)
        var backClicks = 0

        composeTestRule.setContent {
            DailyExpenseTheme {
                SettingsScreen(
                    authViewModel = authViewModel,
                    dailyExpenseViewModel = null,
                    onLogout = {},
                    onBackClick = { backClicks++ },
                    initialRoute = SettingsRoute.GoogleDrive,
                    openedDirectlyToSubRoute = true,
                    routeRequestId = 1
                )
            }
        }

        composeTestRule.onNodeWithText("Google Drive").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backClicks)
    }

    @Test
    fun settingsHubEntry_backFromGoogleDriveReturnsToSettingsHubFirst() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val authViewModel = AuthViewModel(app)
        var exitedSettings = 0

        composeTestRule.setContent {
            DailyExpenseTheme {
                SettingsScreen(
                    authViewModel = authViewModel,
                    dailyExpenseViewModel = null,
                    onLogout = {},
                    onBackClick = { exitedSettings++ },
                    initialRoute = SettingsRoute.Menu,
                    openedDirectlyToSubRoute = false,
                    routeRequestId = 1
                )
            }
        }

        composeTestRule.onNodeWithTag("settings_menu_google_drive").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Google Drive").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(0, exitedSettings)
        composeTestRule.onNodeWithTag("settings_menu_google_drive").assertExists()
    }

    @Test
    fun shareScreen_displaysUpdatedCoreFeaturesOverview() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val authViewModel = AuthViewModel(app)

        composeTestRule.setContent {
            DailyExpenseTheme {
                ShareSettingsSubScreen(
                    authViewModel = authViewModel,
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Multi-PDF Import & Statement Export").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Dual 7-Day Cloud Backups & App Lock").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Monthly Expenses & Receipt Gallery").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun notificationsSettings_showsOnlyDueCustomersAndDisablesSwitchesWhenMasterOff() = runBlocking {
        val app: Application = ApplicationProvider.getApplicationContext()
        AppDatabase.resetInstanceForRecovery()
        val db = AppDatabase.getDatabase(app)
        withContext(Dispatchers.IO) { db.clearAllTables() }

        val withDueId = db.customerDao().insertCustomer(
            CustomerEntity(
                name = "Due Customer",
                mobileNumber = "9000011111",
                collectDueDate = 1_795_000_000_000L,
                reminderEnabled = true
            )
        )
        val noDueId = db.customerDao().insertCustomer(
            CustomerEntity(
                name = "Regular Customer",
                mobileNumber = "9000022222",
                collectDueDate = null,
                reminderEnabled = true
            )
        )

        AppPreferencesManager.init(app)
        AppPreferencesManager.setDueReminderEnabled(app, false)
        val ledgerVm = DailyExpenseViewModel(app)
        delay(200)

        composeTestRule.setContent {
            DailyExpenseTheme {
                NotificationsSettingsSubScreen(
                    dailyExpenseViewModel = ledgerVm,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("due_customer_row_$withDueId").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("due_customer_row_$noDueId").assertDoesNotExist()
        composeTestRule.onNodeWithTag("customer_due_reminder_switch_$withDueId").assertIsNotEnabled()

        withContext(Dispatchers.IO) { db.clearAllTables() }
        AppDatabase.resetInstanceForRecovery()
    }

    @Test
    fun firebaseBackupSettings_showsLocalBackupCard_andImportExportHidesRollingCard() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val authVm = AuthViewModel(app)
        val ledgerVm = DailyExpenseViewModel(app)

        composeTestRule.setContent {
            DailyExpenseTheme {
                FirebaseBackupSettingsSubScreen(
                    authViewModel = authVm,
                    dailyExpenseViewModel = ledgerVm,
                    onNavigateToGoogleAccount = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("local_backup_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("last_local_backup_date_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("local_backup_now_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun importExportSettings_keepsManualExportImportAndPdfOnly() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val authVm = AuthViewModel(app)
        val ledgerVm = DailyExpenseViewModel(app)

        composeTestRule.setContent {
            DailyExpenseTheme {
                ImportExportSettingsSubScreen(
                    authViewModel = authVm,
                    dailyExpenseViewModel = ledgerVm,
                    onBack = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("export_local_json_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_customer_pdf_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("LOCAL DAILY BACKUPS").assertDoesNotExist()
    }
}
