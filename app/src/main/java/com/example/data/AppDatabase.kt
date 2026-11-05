package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CustomerEntity::class,
        TransactionEntity::class,
        MonthlyExpenseEntity::class,
        CategoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun monthlyExpenseDao(): MonthlyExpenseDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `iconName` TEXT NOT NULL,
                        `isCustom` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `monthly_expenses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `monthYear` TEXT NOT NULL DEFAULT '',
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `dateTimestamp` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `attachmentPath` TEXT
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `collectToday` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `collectDueDate` INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_customerId` ON `transactions` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateTimestamp` ON `transactions` (`dateTimestamp`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_mobileNumber` ON `customers` (`mobileNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_lastUpdatedTimestamp` ON `customers` (`lastUpdatedTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_collectToday` ON `customers` (`collectToday`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_collectDueDate` ON `customers` (`collectDueDate`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionType` ON `transactions` (`transactionType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_paymentMode` ON `transactions` (`paymentMode`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monthly_expenses_monthYear` ON `monthly_expenses` (`monthYear`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monthly_expenses_dateTimestamp` ON `monthly_expenses` (`dateTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monthly_expenses_category` ON `monthly_expenses` (`category`)")
            }
        }

        private fun addColumnIfNotExists(db: SupportSQLiteDatabase, tableName: String, columnName: String, columnSqlDef: String) {
            var exists = false
            try {
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1) {
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == columnName) {
                                exists = true
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (!exists) {
                try {
                    db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnSqlDef")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure categories table has isCustom
                addColumnIfNotExists(db, "categories", "isCustom", "INTEGER NOT NULL DEFAULT 0")

                // Ensure monthly_expenses table has monthYear, notes, attachmentPath
                addColumnIfNotExists(db, "monthly_expenses", "monthYear", "TEXT NOT NULL DEFAULT ''")
                addColumnIfNotExists(db, "monthly_expenses", "notes", "TEXT NOT NULL DEFAULT ''")
                addColumnIfNotExists(db, "monthly_expenses", "attachmentPath", "TEXT")

                // Populate monthYear for any existing monthly_expenses records where monthYear is empty
                try {
                    db.execSQL("""
                        UPDATE `monthly_expenses` 
                        SET `monthYear` = strftime('%Y-%m', `dateTimestamp` / 1000, 'unixepoch', 'localtime') 
                        WHERE `monthYear` = '' OR `monthYear` IS NULL
                    """.trimIndent())
                } catch (_: Exception) {}

                // Ensure all required indices exist
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_mobileNumber` ON `customers` (`mobileNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_lastUpdatedTimestamp` ON `customers` (`lastUpdatedTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_collectToday` ON `customers` (`collectToday`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_collectDueDate` ON `customers` (`collectDueDate`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_customerId` ON `transactions` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateTimestamp` ON `transactions` (`dateTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionType` ON `transactions` (`transactionType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_paymentMode` ON `transactions` (`paymentMode`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monthly_expenses_monthYear` ON `monthly_expenses` (`monthYear`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monthly_expenses_dateTimestamp` ON `monthly_expenses` (`dateTimestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_monthly_expenses_category` ON `monthly_expenses` (`category`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daily_expense_db"
                )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.execSQL("PRAGMA foreign_keys = ON;")
                    db.execSQL("PRAGMA synchronous = NORMAL;")
                    db.execSQL("PRAGMA temp_store = MEMORY;")
                    db.execSQL("PRAGMA cache_size = -8000;") // 8 MB cache
                } catch (_: Exception) {}
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                try {
                    val defaultCategories = listOf(
                        Triple("Food", "#FF9800", "Restaurant"),
                        Triple("Fuel", "#E91E63", "LocalGasStation"),
                        Triple("Medical", "#F44336", "MedicalServices"),
                        Triple("Travel", "#2196F3", "DirectionsCar"),
                        Triple("Shopping", "#9C27B0", "ShoppingBag"),
                        Triple("Salary", "#4CAF50", "Payments"),
                        Triple("Rent", "#795548", "Home"),
                        Triple("Bills", "#607D8B", "ReceiptLong"),
                        Triple("Education", "#3F51B5", "School"),
                        Triple("Investment", "#009688", "TrendingUp"),
                        Triple("Miscellaneous", "#9E9E9E", "MoreHoriz")
                    )
                    for ((name, color, icon) in defaultCategories) {
                        db.execSQL(
                            "INSERT OR IGNORE INTO `categories` (`name`, `isCustom`, `iconName`, `colorHex`) VALUES ('$name', 0, '$icon', '$color')"
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
