package com.example

import com.example.data.CustomerEntity
import com.example.data.TransactionEntity
import com.example.ui.CustomerBalanceSummary
import org.junit.Assert.*
import org.junit.Test

class CustomerSortingTest {

    @Test
    fun testCustomerBalanceCalculation() {
        val customer = CustomerEntity(
            id = 1L,
            name = "Ramesh Kumar",
            mobileNumber = "9876543210",
            createdTimestamp = 1000L,
            lastUpdatedTimestamp = 1000L
        )

        val tx1 = TransactionEntity(
            id = 1L,
            customerId = 1L,
            transactionType = "I_PAID",
            amount = 1500.0,
            comment = "Grocery credit",
            category = "General",
            paymentMode = "Cash",
            dateTimestamp = 1000L
        )

        val tx2 = TransactionEntity(
            id = 2L,
            customerId = 1L,
            transactionType = "I_RECEIVED",
            amount = 500.0,
            comment = "Partial payment",
            category = "General",
            paymentMode = "UPI",
            dateTimestamp = 2000L
        )

        val transactions = listOf(tx1, tx2)
        val paid = transactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
        val received = transactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
        val net = paid - received

        val summary = CustomerBalanceSummary(
            customer = customer,
            totalPaid = paid,
            totalReceived = received,
            netBalance = net,
            lastTransactionDate = transactions.maxOfOrNull { it.dateTimestamp }
        )

        assertEquals(1500.0, summary.totalPaid, 0.01)
        assertEquals(500.0, summary.totalReceived, 0.01)
        assertEquals(1000.0, summary.netBalance, 0.01) // Ramesh owes 1000 (YOU GET)
        assertEquals(2000L, summary.lastTransactionDate)
    }

    @Test
    fun testNewCustomerAppearsAtTop() {
        val cust1 = CustomerEntity(id = 1L, name = "Old Customer", mobileNumber = "111", lastUpdatedTimestamp = 1000L)
        val cust2 = CustomerEntity(id = 2L, name = "New Customer", mobileNumber = "222", lastUpdatedTimestamp = 5000L)
        val cust3 = CustomerEntity(id = 3L, name = "Mid Customer", mobileNumber = "333", lastUpdatedTimestamp = 3000L)

        val sortedList = listOf(cust1, cust2, cust3).sortedByDescending { it.lastUpdatedTimestamp }

        assertEquals("New Customer", sortedList.first().name)
        assertEquals(2L, sortedList.first().id)
    }
}
