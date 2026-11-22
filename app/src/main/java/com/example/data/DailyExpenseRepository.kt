package com.example.data

import android.content.Context
import androidx.room.withTransaction
import com.example.utils.BackupManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DailyExpenseRepository(private val db: AppDatabase, private val context: Context? = null) {
    val customerDao = db.customerDao()
    val transactionDao = db.transactionDao()
    val monthlyExpenseDao = db.monthlyExpenseDao()
    val categoryDao = db.categoryDao()

    private fun isOffline(): Boolean = context != null && !BackupManager.isNetworkAvailable(context)

    private fun onDataModified() {
        context?.let { BackupManager.notifyDataChanged(it) }
    }

    private fun recordOfflineMutationIfNeeded() {
        val ctx = context ?: return
        if (!BackupManager.isNetworkAvailable(ctx)) {
            BackupManager.incrementPendingOfflineMutations(ctx)
        }
    }

    suspend fun <T> runInTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val customersWithTransactions: Flow<List<CustomerWithTransactions>> = customerDao.getCustomersWithTransactions()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allExpenses: Flow<List<MonthlyExpenseEntity>> = monthlyExpenseDao.getAllExpenses()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    fun observeUnsyncedEntriesCount(): Flow<Int> = combine(
        transactionDao.observeUnsyncedCount(),
        monthlyExpenseDao.observeUnsyncedCount()
    ) { txCount, expCount -> txCount + expCount }

    fun getCustomerById(id: Long): Flow<CustomerEntity?> = customerDao.getCustomerById(id)

    fun getTransactionsForCustomerPaged(customerId: Long): androidx.paging.PagingSource<Int, TransactionEntity> {
        return transactionDao.getTransactionsForCustomerPaged(customerId)
    }

    fun observeSummaryPeriodTotals(startMillis: Long, endMillis: Long): Flow<SummaryPeriodTotalsTuple> =
        transactionDao.observeSummaryPeriodTotals(startMillis, endMillis)

    fun observeDailyBuckets(startMillis: Long, endMillis: Long): Flow<List<SummaryChartBucketTuple>> =
        transactionDao.observeDailyBuckets(startMillis, endMillis)

    fun observeMonthlyBuckets(startMillis: Long, endMillis: Long): Flow<List<SummaryChartBucketTuple>> =
        transactionDao.observeMonthlyBuckets(startMillis, endMillis)

    fun observeYearlyBuckets(startMillis: Long, endMillis: Long): Flow<List<SummaryChartBucketTuple>> =
        transactionDao.observeYearlyBuckets(startMillis, endMillis)

    fun getSummaryTransactionsPaged(startMillis: Long, endMillis: Long): androidx.paging.PagingSource<Int, SummaryTransactionTuple> =
        transactionDao.getSummaryTransactionsPaged(startMillis, endMillis)

    fun getTransactionsForCustomer(customerId: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsForCustomer(customerId)

    suspend fun getTransactionsForCustomerDirect(customerId: Long): List<TransactionEntity> =
        transactionDao.getTransactionsForCustomerDirect(customerId)

    suspend fun insertCustomer(customer: CustomerEntity): Long {
        val id = customerDao.insertCustomer(customer)
        recordOfflineMutationIfNeeded()
        onDataModified()
        return id
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.updateCustomer(customer)
        recordOfflineMutationIfNeeded()
        onDataModified()
    }

    suspend fun updateCustomerReminderEnabled(customerId: Long, enabled: Boolean) {
        customerDao.updateReminderEnabled(customerId, enabled)
        onDataModified()
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)
        recordOfflineMutationIfNeeded()
        onDataModified()
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val entryToInsert = if (isOffline()) transaction.copy(synced = false) else transaction.copy(synced = true)
        val id = transactionDao.insertTransaction(entryToInsert)
        // Update customer's lastUpdatedTimestamp
        val customer = customerDao.getCustomerByIdDirect(entryToInsert.customerId)
        if (customer != null) {
            customerDao.updateCustomer(customer.copy(lastUpdatedTimestamp = System.currentTimeMillis()))
        }
        onDataModified()
        return id
    }

    suspend fun insertTransactionsBatch(transactions: List<TransactionEntity>): List<Long> {
        if (transactions.isEmpty()) return emptyList()
        val offline = isOffline()
        val adjusted = transactions.map { if (offline) it.copy(synced = false) else it.copy(synced = true) }
        val ids = transactionDao.insertTransactions(adjusted)
        val customerIds = adjusted.map { it.customerId }.distinct()
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
        val entryToUpdate = if (isOffline()) transaction.copy(synced = false) else transaction.copy(synced = true)
        transactionDao.updateTransaction(entryToUpdate)
        val customer = customerDao.getCustomerByIdDirect(entryToUpdate.customerId)
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
        recordOfflineMutationIfNeeded()
        onDataModified()
    }

    suspend fun insertExpense(expense: MonthlyExpenseEntity): Long {
        val entryToInsert = if (isOffline()) expense.copy(synced = false) else expense.copy(synced = true)
        val id = monthlyExpenseDao.insertExpense(entryToInsert)
        onDataModified()
        return id
    }

    suspend fun updateExpense(expense: MonthlyExpenseEntity) {
        val entryToUpdate = if (isOffline()) expense.copy(synced = false) else expense.copy(synced = true)
        monthlyExpenseDao.updateExpense(entryToUpdate)
        onDataModified()
    }

    suspend fun deleteExpense(expense: MonthlyExpenseEntity) {
        monthlyExpenseDao.deleteExpense(expense)
        recordOfflineMutationIfNeeded()
        onDataModified()
    }

    suspend fun markAllEntriesSynced() {
        transactionDao.markAllSynced()
        monthlyExpenseDao.markAllSynced()
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
}
