package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AuthViewModel
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.settings.SettingsRoute
import com.example.ui.screens.settings.ShareSettingsSubScreen
import com.example.ui.theme.DailyExpenseTheme
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
}
