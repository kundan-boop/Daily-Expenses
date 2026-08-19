package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AuthViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLockSecurityTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        val prefs = app.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testAppLockDisabledByDefault() {
        val viewModel = AuthViewModel(app)
        val profile = viewModel.userProfile.value

        // App lock must be disabled by default and app should NOT be locked
        assertFalse("App lock should be disabled by default", profile.isBiometricLockEnabled)
        assertFalse("PIN should be disabled by default", profile.isPinEnabled)
        assertFalse("App should not be locked by default", profile.isLocked)

        // Backgrounding the app when lock is disabled should NOT lock the app
        viewModel.lockApp()
        assertFalse("App should remain unlocked on backgrounding when lock is disabled", viewModel.userProfile.value.isLocked)
    }

    @Test
    fun testAppLockEnabledRequiresAuthenticationOnBackgrounding() {
        val viewModel = AuthViewModel(app)

        // User manually enables App Lock in Settings
        viewModel.setBiometricLock(true)
        assertTrue("Biometric/Phone lock should be enabled", viewModel.userProfile.value.isBiometricLockEnabled)

        // When app is backgrounded, it locks
        viewModel.lockApp()
        assertTrue("App should be locked when backgrounded with security enabled", viewModel.userProfile.value.isLocked)

        // Unlocking via phone biometrics/credential success
        viewModel.unlockAppDirectly()
        assertFalse("App should be unlocked after successful biometric/phone lock check", viewModel.userProfile.value.isLocked)
    }

    @Test
    fun testAppLockDisablingClearsLock() {
        val viewModel = AuthViewModel(app)

        viewModel.setBiometricLock(true)
        viewModel.lockApp()
        assertTrue(viewModel.userProfile.value.isLocked)

        // When user disables App Lock in Settings
        viewModel.setBiometricLock(false)
        assertFalse(viewModel.userProfile.value.isBiometricLockEnabled)
        assertFalse("App should be unlocked when lock is turned off", viewModel.userProfile.value.isLocked)
    }
}
