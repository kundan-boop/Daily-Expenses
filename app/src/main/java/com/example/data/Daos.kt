package com.example.data

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class CustomerWithTransactions(
    @Embedded val customer: CustomerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "customerId"
    )
    val transactions: List<TransactionEntity>
)

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY lastUpdatedTimestamp DESC, id DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Transaction
    @Query("SELECT * FROM customers ORDER BY lastUpdatedTimestamp DESC, id DESC")
    fun getCustomersWithTransactions(): Flow<List<CustomerWithTransactions>>

    @Query("SELECT * FROM customers ORDER BY lastUpdatedTimestamp DESC, id DESC")
    suspend fun getAllCustomersDirect(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun getCustomerById(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerByIdDirect(id: Long): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>): List<Long>

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET reminderEnabled = :enabled WHERE id = :customerId")
    suspend fun updateReminderEnabled(customerId: Long, enabled: Boolean)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)
}

data class SummaryPeriodTotalsTuple(
    val totalPaid: Double,
    val totalReceived: Double,
    val entryCount: Int,
    val customerCount: Int
)

data class SummaryChartBucketTuple(
    val bucketKey: String,
    val totalPaid: Double,
    val totalReceived: Double,
    val entryCount: Int
)

data class SummaryTransactionTuple(
    val id: Long,
    val customerId: Long,
    val customerName: String,
    val transactionType: String,
    val amount: Double,
    val comment: String,
    val category: String,
    val paymentMode: String,
    val dateTimestamp: Long,
    val attachmentPath: String?,
    val status: String
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY dateTimestamp DESC, id DESC")
    fun getTransactionsForCustomer(customerId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId")
    suspend fun getTransactionsForCustomerDirect(customerId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC, id DESC")
    suspend fun getAllTransactionsDirect(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY dateTimestamp DESC, id DESC")
    fun getTransactionsForCustomerPaged(customerId: Long): PagingSource<Int, TransactionEntity>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN transactionType = 'I_PAID' THEN amount ELSE 0 END), 0.0) AS totalPaid,
            COALESCE(SUM(CASE WHEN transactionType = 'I_RECEIVED' THEN amount ELSE 0 END), 0.0) AS totalReceived,
            COUNT(*) AS entryCount,
            COUNT(DISTINCT customerId) AS customerCount
        FROM transactions
        WHERE dateTimestamp >= :startMillis AND dateTimestamp < :endMillis
        """
    )
    fun observeSummaryPeriodTotals(startMillis: Long, endMillis: Long): Flow<SummaryPeriodTotalsTuple>

    @Query(
        """
        SELECT
            strftime('%Y-%m-%d', dateTimestamp / 1000, 'unixepoch', 'localtime') AS bucketKey,
            COALESCE(SUM(CASE WHEN transactionType = 'I_PAID' THEN amount ELSE 0 END), 0.0) AS totalPaid,
            COALESCE(SUM(CASE WHEN transactionType = 'I_RECEIVED' THEN amount ELSE 0 END), 0.0) AS totalReceived,
            COUNT(*) AS entryCount
        FROM transactions
        WHERE dateTimestamp >= :startMillis AND dateTimestamp < :endMillis
        GROUP BY bucketKey
        ORDER BY bucketKey ASC
        """
    )
    fun observeDailyBuckets(startMillis: Long, endMillis: Long): Flow<List<SummaryChartBucketTuple>>

    @Query(
        """
        SELECT
            strftime('%Y-%m', dateTimestamp / 1000, 'unixepoch', 'localtime') AS bucketKey,
            COALESCE(SUM(CASE WHEN transactionType = 'I_PAID' THEN amount ELSE 0 END), 0.0) AS totalPaid,
            COALESCE(SUM(CASE WHEN transactionType = 'I_RECEIVED' THEN amount ELSE 0 END), 0.0) AS totalReceived,
            COUNT(*) AS entryCount
        FROM transactions
        WHERE dateTimestamp >= :startMillis AND dateTimestamp < :endMillis
        GROUP BY bucketKey
        ORDER BY bucketKey ASC
        """
    )
    fun observeMonthlyBuckets(startMillis: Long, endMillis: Long): Flow<List<SummaryChartBucketTuple>>

    @Query(
        """
        SELECT
            strftime('%Y', dateTimestamp / 1000, 'unixepoch', 'localtime') AS bucketKey,
            COALESCE(SUM(CASE WHEN transactionType = 'I_PAID' THEN amount ELSE 0 END), 0.0) AS totalPaid,
            COALESCE(SUM(CASE WHEN transactionType = 'I_RECEIVED' THEN amount ELSE 0 END), 0.0) AS totalReceived,
            COUNT(*) AS entryCount
        FROM transactions
        WHERE dateTimestamp >= :startMillis AND dateTimestamp < :endMillis
        GROUP BY bucketKey
        ORDER BY bucketKey ASC
        """
    )
    fun observeYearlyBuckets(startMillis: Long, endMillis: Long): Flow<List<SummaryChartBucketTuple>>

    @Query(
        """
        SELECT
            t.id AS id,
            t.customerId AS customerId,
            COALESCE(c.name, 'Customer') AS customerName,
            t.transactionType AS transactionType,
            t.amount AS amount,
            t.comment AS comment,
            t.category AS category,
            t.paymentMode AS paymentMode,
            t.dateTimestamp AS dateTimestamp,
            t.attachmentPath AS attachmentPath,
            t.status AS status
        FROM transactions t
        LEFT JOIN customers c ON t.customerId = c.id
        WHERE t.dateTimestamp >= :startMillis AND t.dateTimestamp < :endMillis
        ORDER BY t.dateTimestamp DESC, t.id DESC
        """
    )
    fun getSummaryTransactionsPaged(startMillis: Long, endMillis: Long): PagingSource<Int, SummaryTransactionTuple>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE synced = 0")
    suspend fun getUnsyncedCountDirect(): Int

    @Query("UPDATE transactions SET synced = 1 WHERE synced = 0")
    suspend fun markAllSynced()

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
}

@Dao
interface MonthlyExpenseDao {
    @Query("SELECT * FROM monthly_expenses ORDER BY dateTimestamp DESC")
    suspend fun getAllExpensesDirect(): List<MonthlyExpenseEntity>

    @Query("SELECT * FROM monthly_expenses ORDER BY dateTimestamp DESC")
    fun getAllExpenses(): Flow<List<MonthlyExpenseEntity>>

    @Query("SELECT COUNT(*) FROM monthly_expenses WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM monthly_expenses WHERE synced = 0")
    suspend fun getUnsyncedCountDirect(): Int

    @Query("UPDATE monthly_expenses SET synced = 1 WHERE synced = 0")
    suspend fun markAllSynced()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: MonthlyExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: MonthlyExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: MonthlyExpenseEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long
}
