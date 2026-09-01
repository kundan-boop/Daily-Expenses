package com.example.data

import android.content.Context
import androidx.room.withTransaction
import com.example.utils.BackupManager
import kotlinx.coroutines.flow.Flow

class DailyExpenseRepository(private val db: AppDatabase, private val context: Context? = null) {
    val customerDao = db.customerDao()
    val transactionDao = db.transactionDao()
    val monthlyExpenseDao = db.monthlyExpenseDao()
    val categoryDao = db.categoryDao()

    private fun onDataModified() {
        context?.let { BackupManager.notifyDataChanged(it) }
    }

    suspend fun <T> runInTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val customersWithTransactions: Flow<List<CustomerWithTransactions>> = customerDao.getCustomersWithTransactions()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allExpenses: Flow<List<MonthlyExpenseEntity>> = monthlyExpenseDao.getAllExpenses()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    fun getCustomerById(id: Long): Flow<CustomerEntity?> = customerDao.getCustomerById(id)

    fun getCustomersPaged(query: String = ""): androidx.paging.PagingSource<Int, CustomerEntity> {
        return if (query.isBlank()) customerDao.getCustomersPaged() else customerDao.searchCustomersPaged(query)
    }

    fun getTransactionsForCustomerPaged(customerId: Long): androidx.paging.PagingSource<Int, TransactionEntity> {
        return transactionDao.getTransactionsForCustomerPaged(customerId)
    }

    fun getAllTransactionsPaged(): androidx.paging.PagingSource<Int, TransactionEntity> {
        return transactionDao.getAllTransactionsPaged()
    }

    fun getTransactionsForCustomer(customerId: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsForCustomer(customerId)

    suspend fun getTransactionsForCustomerDirect(customerId: Long): List<TransactionEntity> =
        transactionDao.getTransactionsForCustomerDirect(customerId)

    fun getExpensesByMonth(monthYear: String): Flow<List<MonthlyExpenseEntity>> =
        monthlyExpenseDao.getExpensesByMonth(monthYear)

    suspend fun insertCustomer(customer: CustomerEntity): Long {
        val id = customerDao.insertCustomer(customer)
        onDataModified()
        return id
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.updateCustomer(customer)
        onDataModified()
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)
        onDataModified()
    }

    suspend fun deleteCustomerById(id: Long) {
        customerDao.deleteCustomerById(id)
        onDataModified()
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = transactionDao.insertTransaction(transaction)
        // Update customer's lastUpdatedTimestamp
        val customer = customerDao.getCustomerByIdDirect(transaction.customerId)
        if (customer != null) {
            customerDao.updateCustomer(customer.copy(lastUpdatedTimestamp = System.currentTimeMillis()))
        }
        onDataModified()
        return id
    }

    suspend fun insertTransactionsBatch(transactions: List<TransactionEntity>): List<Long> {
        if (transactions.isEmpty()) return emptyList()
        val ids = transactionDao.insertTransactions(transactions)
        val customerIds = transactions.map { it.customerId }.distinct()
        val now = System.currentTimeMillis()
        customerIds.forEach { cId ->
            val cust = customerDao.getCustomerByIdDirect(cId)
            if (cust != null) {
                customerDao.updateCustomer(cust.copy(lastUpdatedTimestamp = now))
            }
        }
        onDataModified()
        return ids
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
        val customer = customerDao.getCustomerByIdDirect(transaction.customerId)
        if (customer != null) {
            customerDao.updateCustomer(customer.copy(lastUpdatedTimestamp = System.currentTimeMillis()))
        }
        onDataModified()
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
        val customer = customerDao.getCustomerByIdDirect(transaction.customerId)
        if (customer != null) {
            customerDao.updateCustomer(customer.copy(lastUpdatedTimestamp = System.currentTimeMillis()))
        }
        onDataModified()
    }

    suspend fun insertExpense(expense: MonthlyExpenseEntity): Long {
        val id = monthlyExpenseDao.insertExpense(expense)
        onDataModified()
        return id
    }

    suspend fun updateExpense(expense: MonthlyExpenseEntity) {
        monthlyExpenseDao.updateExpense(expense)
        onDataModified()
    }

    suspend fun deleteExpense(expense: MonthlyExpenseEntity) {
        monthlyExpenseDao.deleteExpense(expense)
        onDataModified()
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        val id = categoryDao.insertCategory(category)
        onDataModified()
        return id
    }

    suspend fun getAllActiveImagePaths(): Set<String> {
        val custPhotos = customerDao.getAllCustomersDirect().mapNotNull { it.profilePhotoUri }
        val txAttachments = transactionDao.getAllTransactionsDirect().mapNotNull { it.attachmentPath }
        val expAttachments = monthlyExpenseDao.getAllExpensesDirect().mapNotNull { it.attachmentPath }
        return (custPhotos + txAttachments + expAttachments).toSet()
    }

    suspend fun vacuumDatabase() {
        DatabaseManager.optimizeDatabase(db)
    }
}
