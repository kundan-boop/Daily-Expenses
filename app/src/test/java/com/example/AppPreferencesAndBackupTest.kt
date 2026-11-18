package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.example.data.AppDatabase
import com.example.data.CustomerEntity
import com.example.data.MonthlyExpenseEntity
import com.example.data.TransactionEntity
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.utils.AppPreferencesManager
import com.example.utils.BackupManager
import com.example.utils.BackupState
import com.example.utils.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppPreferencesAndBackupTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppPreferencesManager.init(app)
    }

    @Test
    fun preferencesManager_persistsThemeLanguageAlertsAndAutoBackupToggles() {
        AppPreferencesManager.setThemeMode(app, ThemeMode.DARK)
        AppPreferencesManager.setLanguage(app, "Gujarati")
        AppPreferencesManager.setNotificationsEnabled(app, false)
        AppPreferencesManager.setDriveAutoBackupEnabled(app, false)
        AppPreferencesManager.setFirebaseAutoBackupEnabled(app, false)

        AppPreferencesManager.init(app)

        assertEquals(ThemeMode.DARK, AppPreferencesManager.themeModeFlow.value)
        assertEquals("Gujarati", AppPreferencesManager.languageFlow.value)
        assertFalse(AppPreferencesManager.notificationsEnabledFlow.value)
        assertFalse(AppPreferencesManager.isDriveAutoBackupEnabled(app))
        assertFalse(AppPreferencesManager.isFirebaseAutoBackupEnabled(app))
    }

    @Test
    fun isolatedBackupStatuses_doNotMixBetweenDriveAndFirebase() {
        AppPreferencesManager.updateFirebaseStatus(
            context = app,
            state = BackupState.SUCCESS,
            timestamp = 1_700_000_000_000L,
            userEmail = "owner@example.com",
            snapshotCount = 3
        )
        BackupManager.refreshBackupStatus(app)

        assertEquals(BackupState.SUCCESS, AppPreferencesManager.firebaseBackupStatusFlow.value.state)
        assertEquals(3, AppPreferencesManager.firebaseBackupStatusFlow.value.snapshotCount)
        assertEquals(BackupState.NOT_CONFIGURED, BackupManager.backupStatusFlow.value.state)
    }

    @Test
    fun exportFullBackupPackage_includesProfileCustomersTransactionsAndExpenses() = runBlocking {
        app.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
            .putString("user_name", "Aarav Patel")
            .putString("business_name", "Patel Mart")
            .putString("user_phone", "9876543210")
            .putString("user_email", "aarav@patelmart.in")
            .commit()

        val db = AppDatabase.getDatabase(app)
        val cid = db.customerDao().insertCustomer(
            CustomerEntity(name = "Ramesh Traders", mobileNumber = "9000011111")
        )
        db.transactionDao().insertTransaction(
            TransactionEntity(
                customerId = cid,
                transactionType = "I_PAID",
                amount = 450.0,
                comment = "Flour stock"
            )
        )
        db.monthlyExpenseDao().insertExpense(
            MonthlyExpenseEntity(
                monthYear = "2026-11",
                title = "Shop Electricity",
                category = "Utilities",
                amount = 1200.0
            )
        )

        val pkg = BackupManager.exportFullBackupPackage(app)
        assertTrue(pkg.hasUserProfile)
        assertTrue(pkg.customerCount >= 1)
        assertTrue(pkg.transactionCount >= 1)

        val json = JSONObject(pkg.jsonString)
        assertTrue(json.has("userProfile"))
        val profileObj = json.getJSONObject("userProfile")
        assertEquals("Aarav Patel", profileObj.getString("name"))
        assertEquals("Patel Mart", profileObj.getString("businessName"))
        assertTrue(json.getJSONArray("monthlyExpenses").length() >= 1)
    }

    @Test
    fun restoreFullBackup_detectsProfileConflictAndRespectsLegacyBackups() = runBlocking {
        app.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
            .putString("user_name", "Current Owner")
            .putString("business_name", "Current Store")
            .putString("user_phone", "9999988888")
            .putString("user_email", "current@store.com")
            .commit()

        val authVm = AuthViewModel(app)
        val ledgerVm = DailyExpenseViewModel(app)

        // 1. Legacy backup without userProfile must preserve current profile untouched
        val legacyJson = JSONObject().apply {
            put("version", 1)
            put("customers", JSONArray())
            put("transactions", JSONArray())
        }.toString()

        val legacySummary = ledgerVm.performFullBackupImportInternal(legacyJson, authVm)
        assertTrue(legacySummary.success)
        assertFalse(legacySummary.hasProfileInBackup)
        assertNull(ledgerVm.pendingProfileRestore.value)
        assertEquals("Current Owner", authVm.userProfile.value.name)

        // 2. Full backup with different profile triggers pendingProfileRestore conflict confirmation
        val conflictJson = JSONObject().apply {
            put("version", 2)
            put("userProfile", JSONObject().apply {
                put("name", "Restored Owner")
                put("businessName", "Restored Mart")
                put("phone", "7777766666")
                put("email", "restored@mart.com")
            })
            put("customers", JSONArray())
            put("transactions", JSONArray())
            put("monthlyExpenses", JSONArray())
        }.toString()

        val conflictSummary = ledgerVm.performFullBackupImportInternal(conflictJson, authVm)
        assertTrue(conflictSummary.success)
        assertTrue(conflictSummary.hasProfileInBackup)
        assertNotNull(ledgerVm.pendingProfileRestore.value)
        assertEquals("Current Owner", authVm.userProfile.value.name)

        // Confirm replace
        ledgerVm.resolvePendingProfileRestore(replace = true, authViewModel = authVm)
        delay(250)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(ledgerVm.pendingProfileRestore.value)
        assertEquals("Restored Owner", authVm.userProfile.value.name)
        assertEquals("Restored Mart", authVm.userProfile.value.businessName)
    }

    @Test
    fun removeTransactionImage_onlyDeletesTargetedPathFromPipeDelimitedList() = runBlocking {
        val db = AppDatabase.getDatabase(app)
        val cid = db.customerDao().insertCustomer(
            CustomerEntity(name = "Gallery Customer", mobileNumber = "9000022222")
        )
        val txId = db.transactionDao().insertTransaction(
            TransactionEntity(
                customerId = cid,
                transactionType = "I_PAID",
                amount = 150.0,
                attachmentPath = "/tmp/img_one.jpg|/tmp/img_two.jpg|/tmp/img_three.jpg"
            )
        )
        val inserted = db.transactionDao().getTransactionsForCustomer(cid).first().first { it.id == txId }
        val ledgerVm = DailyExpenseViewModel(app)

        ledgerVm.removeTransactionImage(inserted, "/tmp/img_two.jpg")
        delay(250)

        val updated = db.transactionDao().getTransactionsForCustomer(cid).first().first { it.id == txId }
        assertEquals("/tmp/img_one.jpg|/tmp/img_three.jpg", updated.attachmentPath)
    }
}
