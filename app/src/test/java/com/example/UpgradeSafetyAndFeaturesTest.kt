package com.example

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CustomerEntity
import com.example.data.DatabaseSafetyManager
import com.example.data.DbStartupState
import com.example.data.TransactionEntity
import com.example.ui.AuthViewModel
import com.example.ui.DailyExpenseViewModel
import com.example.utils.AppPreferencesManager
import com.example.utils.ImageUtils
import com.example.utils.ThemeMode
import com.example.utils.stringsForLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpgradeSafetyAndFeaturesTest {

    @Test
    fun roomMigration_v5ToV6ToV7_preservesAllExistingDataAndAddsCompositeIndex() = runBlocking {
        val app: Application = ApplicationProvider.getApplicationContext()
        val dbName = "upgrade_safety_test_db"
        app.deleteDatabase(dbName)
        val dbFile = app.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create pre-existing v5 SQLite database matching installed schema
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `customers` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `mobileNumber` TEXT NOT NULL,
                    `address` TEXT NOT NULL,
                    `email` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `profilePhotoUri` TEXT,
                    `collectToday` INTEGER NOT NULL,
                    `collectDueDate` INTEGER,
                    `createdTimestamp` INTEGER NOT NULL,
                    `lastUpdatedTimestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `customerId` INTEGER NOT NULL,
                    `transactionType` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `dateTimestamp` INTEGER NOT NULL,
                    `comment` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `attachmentPath` TEXT,
                    `paymentMode` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `monthly_expenses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `monthYear` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `dateTimestamp` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    `attachmentPath` TEXT
                )
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `isCustom` INTEGER NOT NULL,
                    `iconName` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL
                )
                """.trimIndent()
            )

            // Seed real user data at v5
            sqlite.execSQL(
                """
                INSERT INTO `customers` (`id`, `name`, `mobileNumber`, `address`, `email`, `notes`, `profilePhotoUri`, `collectToday`, `collectDueDate`, `createdTimestamp`, `lastUpdatedTimestamp`)
                VALUES (101, 'Kiran Provision', '9876501234', 'MG Road', 'kiran@store.in', 'VIP customer', '/data/user/0/app/files/profile_photos/kiran.jpg', 1, 1770000000000, 1700000000000, 1760000000000)
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO `transactions` (`id`, `customerId`, `transactionType`, `amount`, `dateTimestamp`, `comment`, `category`, `attachmentPath`, `paymentMode`, `status`)
                VALUES (501, 101, 'I_PAID', 2450.75, 1760000000000, 'Sugar & Tea', 'Inventory', '/data/user/0/app/files/attachments/bill_501.jpg', 'UPI', 'COMPLETED')
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO `monthly_expenses` (`id`, `monthYear`, `title`, `category`, `amount`, `dateTimestamp`, `notes`, `attachmentPath`)
                VALUES (301, '2026-10', 'Shop Rent', 'Rent', 15000.0, 1760000000000, 'October rent', NULL)
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO `categories` (`id`, `name`, `isCustom`, `iconName`, `colorHex`)
                VALUES (11, 'Wholesale', 1, 'Store', '#2E7D32')
                """.trimIndent()
            )
            sqlite.version = 5
        } finally {
            sqlite.close()
        }

        // Open through Room with additive migrations v5->v6->v7
        val roomDb = Room.databaseBuilder(app, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        try {
            val openDb = roomDb.openHelper.writableDatabase
            assertEquals(7, openDb.version)

            // Verify composite index created by MIGRATION_6_7
            val indexNames = mutableSetOf<String>()
            openDb.query("PRAGMA index_list(`transactions`)").use { cursor ->
                val nameCol = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameCol != -1) indexNames.add(cursor.getString(nameCol))
                }
            }
            assertTrue(
                "Expected composite index index_transactions_customerId_dateTimestamp, found: $indexNames",
                indexNames.contains("index_transactions_customerId_dateTimestamp")
            )

            // Verify 100% of data preserved intact
            val customers = roomDb.customerDao().getAllCustomersDirect()
            assertEquals(1, customers.size)
            val customer = customers.first()
            assertEquals(101L, customer.id)
            assertEquals("Kiran Provision", customer.name)
            assertEquals("9876501234", customer.mobileNumber)
            assertEquals("MG Road", customer.address)
            assertEquals("kiran@store.in", customer.email)
            assertEquals("VIP customer", customer.notes)
            assertEquals("/data/user/0/app/files/profile_photos/kiran.jpg", customer.profilePhotoUri)
            assertTrue(customer.collectToday)
            assertEquals(1770000000000L, customer.collectDueDate)

            val txs = roomDb.transactionDao().getAllTransactionsDirect()
            assertEquals(1, txs.size)
            val tx = txs.first()
            assertEquals(501L, tx.id)
            assertEquals(101L, tx.customerId)
            assertEquals("I_PAID", tx.transactionType)
            assertEquals(2450.75, tx.amount, 0.0001)
            assertEquals("Sugar & Tea", tx.comment)
            assertEquals("Inventory", tx.category)
            assertEquals("/data/user/0/app/files/attachments/bill_501.jpg", tx.attachmentPath)
            assertEquals("UPI", tx.paymentMode)
            assertEquals("COMPLETED", tx.status)

            val expenses = roomDb.monthlyExpenseDao().getAllExpensesDirect()
            assertEquals(1, expenses.size)
            assertEquals("Shop Rent", expenses.first().title)
            assertEquals(15000.0, expenses.first().amount, 0.0001)

            val categories = roomDb.categoryDao().getAllCategories().first()
            assertEquals(1, categories.size)
            assertEquals("Wholesale", categories.first().name)
            assertTrue(categories.first().isCustom)
        } finally {
            roomDb.close()
            app.deleteDatabase(dbName)
        }
    }

    @Test
    fun appPreferencesAndLegacyUserBackupPrefs_remainUnchangedOnInit() {
        val app: Application = ApplicationProvider.getApplicationContext()

        app.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", ThemeMode.DARK.name)
            .putString("app_language", "Hindi")
            .putBoolean("notifications_enabled", false)
            .putBoolean("drive_auto_backup_enabled", false)
            .putBoolean("firebase_auto_backup_enabled", false)
            .commit()

        app.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
            .putString("user_name", "Existing Owner")
            .putString("business_name", "Existing Mart")
            .putString("pin_code", "4321")
            .putBoolean("is_biometric_enabled", true)
            .commit()

        app.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE).edit()
            .putString("drive_folder_uri", "content://com.google.android.apps.docs/tree/existing123")
            .commit()

        AppPreferencesManager.init(app)

        // Verify AppPreferencesManager values loaded without reset
        assertEquals(ThemeMode.DARK, AppPreferencesManager.themeModeFlow.value)
        assertEquals("Hindi", AppPreferencesManager.languageFlow.value)
        assertFalse(AppPreferencesManager.notificationsEnabledFlow.value)
        assertFalse(AppPreferencesManager.driveAutoBackupEnabledFlow.value)
        assertFalse(AppPreferencesManager.firebaseAutoBackupEnabledFlow.value)

        // Verify legacy user_prefs and backup_prefs were not touched
        val userPrefs = app.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        assertEquals("Existing Owner", userPrefs.getString("user_name", null))
        assertEquals("Existing Mart", userPrefs.getString("business_name", null))
        assertEquals("4321", userPrefs.getString("pin_code", null))
        assertTrue(userPrefs.getBoolean("is_biometric_enabled", false))

        val backupPrefs = app.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
        assertEquals(
            "content://com.google.android.apps.docs/tree/existing123",
            backupPrefs.getString("drive_folder_uri", null)
        )
    }

    @Test
    fun imageCompression_scalesToMax1600JpegAndPreservesExistingAttachments() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val attachmentsDir = File(app.filesDir, "attachments").apply { mkdirs() }

        // Pre-existing attachment on device before upgrade
        val legacyBytes = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x7F)
        val legacyFile = File(attachmentsDir, "legacy_receipt_99.jpg")
        legacyFile.writeBytes(legacyBytes)

        val largeBitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(30, 120, 60))
        }

        val savedPath = try {
            ImageUtils.compressAndSaveBitmap(app, largeBitmap, "attachments")
        } finally {
            largeBitmap.recycle()
        }

        assertNotNull(savedPath)
        val savedFile = File(savedPath!!)
        assertTrue(savedFile.exists())
        assertTrue(savedFile.name.endsWith(".jpg"))
        assertEquals(attachmentsDir.absolutePath, savedFile.parentFile?.absolutePath)

        // Verify JPEG magic header (0xFF, 0xD8) and scaled dimensions <= 1600px
        val savedBytes = savedFile.readBytes()
        assertTrue(savedBytes.size > 4)
        assertEquals(0xFF.toByte(), savedBytes[0])
        assertEquals(0xD8.toByte(), savedBytes[1])

        val decodedBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(savedFile.absolutePath, decodedBounds)
        assertEquals(1600, decodedBounds.outWidth)
        assertEquals(1200, decodedBounds.outHeight)

        // Verify legacy file was not overwritten or deleted
        assertTrue(legacyFile.exists())
        assertArrayEquals(legacyBytes, legacyFile.readBytes())

        savedFile.delete()
        legacyFile.delete()
    }

    @Test
    fun localizationStrings_returnDistinctNonEmptyTranslationsForEnglishHindiGujarati() {
        val en = stringsForLanguage("English")
        val hi = stringsForLanguage("Hindi")
        val gu = stringsForLanguage("Gujarati")

        assertTrue(en.settingsTitle.isNotBlank())
        assertTrue(hi.settingsTitle.isNotBlank())
        assertTrue(gu.settingsTitle.isNotBlank())

        assertNotEquals(en.settingsTitle, hi.settingsTitle)
        assertNotEquals(en.settingsTitle, gu.settingsTitle)
        assertNotEquals(hi.settingsTitle, gu.settingsTitle)

        assertNotEquals(en.mainDashboard, hi.mainDashboard)
        assertNotEquals(en.mainDashboard, gu.mainDashboard)
        assertNotEquals(en.customerLedger, hi.customerLedger)
        assertNotEquals(en.customerLedger, gu.customerLedger)

        // Fallback to English on unknown language string
        assertEquals(en, stringsForLanguage("Unknown"))
    }

    @Test
    fun preMigrationSafetyCopy_capturedBeforeMigration_keptUntilNextSuccessfulLaunch() {
        val app: Application = ApplicationProvider.getApplicationContext()
        AppDatabase.resetInstanceForRecovery()
        DatabaseSafetyManager.resetForTesting()
        app.getSharedPreferences("db_safety_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(app.filesDir, "db_migration_safety").deleteRecursively()
        app.deleteDatabase(AppDatabase.DATABASE_NAME)

        val dbFile = app.getDatabasePath(AppDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()

        // Create pre-update v5 database with real customer row
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            sqlite.execSQL("INSERT INTO `customers` (`id`, `name`) VALUES (1, 'Pre-Update Customer')")
            sqlite.version = 5
        } finally {
            sqlite.close()
        }

        // First launch on new versionCode = 100 -> safety copy must be created BEFORE opener runs and KEPT after first launch
        val stateLaunch1 = DatabaseSafetyManager.prepareAndVerifySync(
            context = app,
            currentVersionCodeOverride = 100L,
            dbVersionOpener = { ctx ->
                val migrated = SQLiteDatabase.openDatabase(
                    ctx.getDatabasePath(AppDatabase.DATABASE_NAME).absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                migrated.use { s ->
                    assertEquals(5, s.version)
                    s.version = AppDatabase.DATABASE_VERSION
                    s.version
                }
            }
        )
        assertTrue(stateLaunch1 is DbStartupState.Ready)
        assertTrue((stateLaunch1 as DbStartupState.Ready).hasPreservedSafetyCopy)
        assertTrue(DatabaseSafetyManager.hasValidSafetyCopy(app))

        // Next launch on same versionCode = 100 -> safety copy is retired cleanly after next successful launch
        DatabaseSafetyManager.resetForTesting()
        val stateLaunch2 = DatabaseSafetyManager.prepareAndVerifySync(
            context = app,
            currentVersionCodeOverride = 100L,
            dbVersionOpener = { AppDatabase.DATABASE_VERSION }
        )
        assertTrue(stateLaunch2 is DbStartupState.Ready)
        assertFalse((stateLaunch2 as DbStartupState.Ready).hasPreservedSafetyCopy)
        assertFalse(DatabaseSafetyManager.hasValidSafetyCopy(app))

        app.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    @Test
    fun migrationFailure_doesNotWipeData_andRestoresFromSafetyCopy() {
        val app: Application = ApplicationProvider.getApplicationContext()
        AppDatabase.resetInstanceForRecovery()
        DatabaseSafetyManager.resetForTesting()
        app.getSharedPreferences("db_safety_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(app.filesDir, "db_migration_safety").deleteRecursively()
        app.deleteDatabase(AppDatabase.DATABASE_NAME)

        val dbFile = app.getDatabasePath(AppDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            sqlite.execSQL("INSERT INTO `customers` (`id`, `name`) VALUES (42, 'Protected Owner Customer')")
            sqlite.version = 5
        } finally {
            sqlite.close()
        }

        // Launch 1 fails during migration -> DB file stays intact & MigrationFailed exposes safety copy
        val failedState = DatabaseSafetyManager.prepareAndVerifySync(
            context = app,
            currentVersionCodeOverride = 101L,
            dbVersionOpener = {
                // Simulate failed migration writing corrupted/partial marker
                throw IllegalStateException("Simulated migration failure")
            }
        )
        assertTrue(failedState is DbStartupState.MigrationFailed)
        val failure = failedState as DbStartupState.MigrationFailed
        assertTrue(failure.hasSafetyCopy)
        assertTrue(failure.errorMessage.contains("Simulated migration failure"))
        assertTrue("Database file must never be deleted on failure", dbFile.exists() && dbFile.length() > 0L)

        // Restore from safety copy restores the DB and succeeds when opener succeeds
        val restored = DatabaseSafetyManager.restoreFromSafetyCopy(
            context = app,
            currentVersionCodeOverride = 101L,
            dbVersionOpener = { ctx ->
                val db = SQLiteDatabase.openDatabase(ctx.getDatabasePath(AppDatabase.DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                db.use { s ->
                    s.rawQuery("SELECT name FROM customers WHERE id = 42", null).use { c ->
                        assertTrue(c.moveToFirst())
                        assertEquals("Protected Owner Customer", c.getString(0))
                    }
                    s.version
                }
            }
        )
        assertTrue(restored.isSuccess)
        assertTrue(DatabaseSafetyManager.startupState.value is DbStartupState.Ready)

        app.deleteDatabase(AppDatabase.DATABASE_NAME)
        File(app.filesDir, "db_migration_safety").deleteRecursively()
    }

    @Test
    fun restoreFullBackup_acceptsVersion12BackupWithoutProfileAndPreservesExistingData() = runBlocking {
        val app: Application = ApplicationProvider.getApplicationContext()
        AppDatabase.resetInstanceForRecovery()
        val db = AppDatabase.getDatabase(app)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }

        app.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
            .putString("user_name", "Phone Owner")
            .putString("business_name", "Phone Mart")
            .commit()

        val existingCid = db.customerDao().insertCustomer(
            CustomerEntity(name = "Existing Local Customer", mobileNumber = "9111100000")
        )
        db.transactionDao().insertTransaction(
            TransactionEntity(
                customerId = existingCid,
                transactionType = "I_PAID",
                amount = 750.0,
                comment = "Already On Phone"
            )
        )

        val authVm = AuthViewModel(app)
        val ledgerVm = DailyExpenseViewModel(app)

        // Simulate v1.2 legacy backup: no userProfile, no monthlyExpenses, JSON nulls, two identical-amount entries on different dates
        val v12Json = JSONObject().apply {
            put("version", 1)
            put("customers", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 7L)
                    put("name", "Legacy v1.2 Customer")
                    put("mobileNumber", "9888877777")
                    put("address", JSONObject.NULL)
                    put("email", JSONObject.NULL)
                    put("notes", JSONObject.NULL)
                    put("profilePhotoUri", JSONObject.NULL)
                    put("collectToday", false)
                    put("collectDueDate", JSONObject.NULL)
                })
            })
            put("transactions", JSONArray().apply {
                put(JSONObject().apply {
                    put("customerId", 7L)
                    put("transactionType", "I_PAID")
                    put("amount", 500.0)
                    put("dateTimestamp", 1_700_000_000_000L)
                    put("comment", "")
                    put("category", JSONObject.NULL)
                    put("paymentMode", JSONObject.NULL)
                    put("attachmentPath", JSONObject.NULL)
                })
                put(JSONObject().apply {
                    put("customerId", 7L)
                    put("transactionType", "I_PAID")
                    put("amount", 500.0)
                    put("dateTimestamp", 1_700_086_400_000L)
                    put("comment", "")
                })
            })
        }.toString()

        val summary = ledgerVm.performFullBackupImportInternal(v12Json, authVm)
        assertTrue(summary.success)
        assertEquals(1, summary.customersAdded)
        assertEquals(2, summary.transactionsAdded)
        assertFalse(summary.hasProfileInBackup)
        assertEquals("Phone Owner", authVm.userProfile.value.name)

        val allCustomers = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.customerDao().getAllCustomersDirect()
        }
        assertEquals(2, allCustomers.size)
        val restoredCust = allCustomers.first { it.name == "Legacy v1.2 Customer" }
        assertEquals("", restoredCust.address)
        assertEquals("", restoredCust.notes)

        val restoredTxs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.transactionDao().getTransactionsForCustomerDirect(restoredCust.id)
        }
        assertEquals(2, restoredTxs.size)
        assertEquals("General", restoredTxs.first().category)
        assertEquals("CASH", restoredTxs.first().paymentMode)

        // Pre-existing customer & transaction stayed untouched
        val existingTxs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.transactionDao().getTransactionsForCustomerDirect(existingCid)
        }
        assertEquals(1, existingTxs.size)
        assertEquals("Already On Phone", existingTxs.first().comment)

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }
        AppDatabase.resetInstanceForRecovery()
    }
}
