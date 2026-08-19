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

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: Long)

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR mobileNumber LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY lastUpdatedTimestamp DESC, id DESC")
    fun getCustomersPaged(): PagingSource<Int, CustomerEntity>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR mobileNumber LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCustomersPaged(query: String): PagingSource<Int, CustomerEntity>
}

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

    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC, id DESC")
    fun getAllTransactionsPaged(): PagingSource<Int, TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT SUM(amount) FROM transactions WHERE customerId = :customerId AND transactionType = 'I_PAID'")
    fun getTotalPaidForCustomer(customerId: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE customerId = :customerId AND transactionType = 'I_RECEIVED'")
    fun getTotalReceivedForCustomer(customerId: Long): Flow<Double?>
}

@Dao
interface MonthlyExpenseDao {
    @Query("SELECT * FROM monthly_expenses ORDER BY dateTimestamp DESC")
    suspend fun getAllExpensesDirect(): List<MonthlyExpenseEntity>

    @Query("SELECT * FROM monthly_expenses ORDER BY dateTimestamp DESC")
    fun getAllExpenses(): Flow<List<MonthlyExpenseEntity>>

    @Query("SELECT * FROM monthly_expenses WHERE monthYear = :monthYear ORDER BY dateTimestamp DESC")
    fun getExpensesByMonth(monthYear: String): Flow<List<MonthlyExpenseEntity>>

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

    @Query("DELETE FROM categories WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomCategory(id: Long)
}
