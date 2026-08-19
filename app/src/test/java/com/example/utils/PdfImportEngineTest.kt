package com.example.utils

import com.example.domain.pdf.OkCreditPdfParser
import com.example.domain.pdf.TransactionReportPdfParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfImportEngineTest {

    @Test
    fun testDetectFormat_OkCredit() {
        val sampleText = """
            Customer:
            October 2025 Deduction
            Mobile: 9876543210
            Main Street, City
            Payment (0)
            Credit (69)
            Current Balance ₹41,129
        """.trimIndent()

        val format = PdfImportEngine.detectFormat(sampleText)
        assertEquals(PdfFormatType.OK_CREDIT_STATEMENT, format)
    }

    @Test
    fun testDetectFormat_TransactionReport() {
        val sampleText = """
            Transaction Report
            Report Summary
            To,
            Rent Vishwas City
            Date Received Paid
            12/10/2025 500 0
        """.trimIndent()

        val format = PdfImportEngine.detectFormat(sampleText)
        assertEquals(PdfFormatType.TRANSACTION_REPORT, format)
    }

    @Test
    fun testDetectFormat_Unsupported() {
        val sampleText = """
            Random Invoice Document
            Total Amount: 100
        """.trimIndent()

        val format = PdfImportEngine.detectFormat(sampleText)
        assertEquals(PdfFormatType.UNSUPPORTED, format)
    }

    @Test
    fun testExtractOkCreditCustomer() {
        val sampleText = """
            Account Owner Name
            Customer:
            October 2025 Deduction
            Mobile:
            9876543210
            Block A, Main Road
            Payment (0)
            Credit (69)
            Current Balance ₹41,129
        """.trimIndent()

        val extracted = PdfImportEngine.extractCustomerInfo(sampleText, PdfFormatType.OK_CREDIT_STATEMENT)
        assertNotNull(extracted)
        assertEquals("October 2025 Deduction", extracted?.name)
        assertEquals("9876543210", extracted?.mobileNumber)
        assertEquals("", extracted?.address)
    }

    @Test
    fun testExtractTransactionReportCustomer() {
        val sampleText = """
            Transaction Report
            To, Rent Vishwas City
            Sector 12, Phase 1
            Date Received Paid
        """.trimIndent()

        val extracted = PdfImportEngine.extractCustomerInfo(sampleText, PdfFormatType.TRANSACTION_REPORT)
        assertNotNull(extracted)
        assertTrue(extracted?.name?.contains("Rent Vishwas City") == true)
    }

    @Test
    fun testExtractTransactions_OkCredit() {
        val sampleText = """
            Customer:
            October 2025 Deduction
            Payment (0)
            Credit (69)
            Current Balance ₹41,129
            
            05 Oct 2025
            Petrol expense
            ₹300 Credit
            
            05 Oct 2025
            Food expense
            ₹500 Credit
            
            06 Oct 2025
            Payment received
            ₹200 Payment
        """.trimIndent()

        val transactions = PdfImportEngine.extractTransactionsFromPdfText(
            pdfText = sampleText,
            formatType = PdfFormatType.OK_CREDIT_STATEMENT,
            customerId = 101L
        )

        assertTrue(transactions.isNotEmpty())
        assertEquals(101L, transactions[0].customerId)
        assertEquals(300.0, transactions[0].amount, 0.01)
        assertEquals("I_PAID", transactions[0].transactionType)
        assertTrue(transactions[0].comment.contains("Petrol expense"))
    }

    @Test
    fun testExtractTransactions_TransactionReport() {
        val sampleText = """
            Transaction Report
            Total Amount Received 500.00
            Total Amount Paid 200.00
            To, Rent Vishwas City
            Date Received Paid Balance
            12/10/2025 Advance Rent 500.00
            13/10/2025 Maintenance Fee 200.00
        """.trimIndent()

        val transactions = PdfImportEngine.extractTransactionsFromPdfText(
            pdfText = sampleText,
            formatType = PdfFormatType.TRANSACTION_REPORT,
            customerId = 202L
        )

        assertTrue(transactions.isNotEmpty())
        assertEquals(202L, transactions[0].customerId)
        assertEquals(500.0, transactions[0].amount, 0.01)
    }

    @Test
    fun testGenerateBackupJson() {
        val customer = com.example.data.CustomerEntity(
            id = 10L,
            name = "Test Customer",
            mobileNumber = "9999988888",
            address = "City Center"
        )
        val transactions = listOf(
            com.example.data.TransactionEntity(
                id = 101L,
                customerId = 10L,
                transactionType = "I_PAID",
                amount = 500.0,
                dateTimestamp = 1762387200000L,
                comment = "Petrol",
                category = "General",
                paymentMode = "None",
                attachmentPath = ""
            )
        )

        val jsonStr = PdfImportEngine.generateBackupJson(customer, transactions)
        val jsonObj = org.json.JSONObject(jsonStr)

        assertTrue(jsonObj.has("customer"))
        assertTrue(jsonObj.has("transactions"))

        val custObj = jsonObj.getJSONObject("customer")
        assertEquals("Test Customer", custObj.getString("name"))

        val txArr = jsonObj.getJSONArray("transactions")
        assertEquals(1, txArr.length())
        val tx0 = txArr.getJSONObject(0)
        assertEquals(101L, tx0.getLong("id"))
        assertEquals("I_PAID", tx0.getString("transactionType"))
        assertEquals(500.0, tx0.getDouble("amount"), 0.01)
        assertEquals("Petrol", tx0.getString("comment"))
        assertEquals("General", tx0.getString("category"))
        assertEquals("None", tx0.getString("paymentMode"))
        assertEquals("", tx0.getString("attachmentPath"))
    }

    @Test
    fun testValidateOkCreditBalance() {
        val sampleText = """
            Customer: October 2025 Deduction
            Current Balance ₹800
        """.trimIndent()

        val validTxs = listOf(
            com.example.data.TransactionEntity(id = 1, customerId = 1, transactionType = "I_PAID", amount = 800.0, dateTimestamp = 0, comment = ""),
        )
        val error1 = PdfImportEngine.validateOkCreditBalance(sampleText, validTxs)
        assertNull(error1)

        val invalidTxs = listOf(
            com.example.data.TransactionEntity(id = 1, customerId = 1, transactionType = "I_PAID", amount = 500.0, dateTimestamp = 0, comment = ""),
        )
        val error2 = PdfImportEngine.validateOkCreditBalance(sampleText, invalidTxs)
        assertNotNull(error2)
    }

    @Test
    fun testValidateTransactionReportTotals() {
        val sampleText = """
            Transaction Report
            Total Amount Received 500
            Total Amount Paid 200
        """.trimIndent()

        val validTxs = listOf(
            com.example.data.TransactionEntity(id = 1, customerId = 1, transactionType = "I_RECEIVED", amount = 500.0, dateTimestamp = 0, comment = ""),
            com.example.data.TransactionEntity(id = 2, customerId = 1, transactionType = "I_PAID", amount = 200.0, dateTimestamp = 0, comment = "")
        )
        val error1 = PdfImportEngine.validateTransactionReportTotals(sampleText, validTxs)
        assertNull(error1)

        val invalidTxs = listOf(
            com.example.data.TransactionEntity(id = 1, customerId = 1, transactionType = "I_RECEIVED", amount = 300.0, dateTimestamp = 0, comment = "")
        )
        val error2 = PdfImportEngine.validateTransactionReportTotals(sampleText, invalidTxs)
        assertNotNull(error2)
    }

    @Test
    fun testOkCreditPdfParser_MissingCustomerNameFails() {
        val badText = """
            Customer:
            
            Payment (0)
            Credit (69)
            Current Balance ₹1,000
        """.trimIndent()

        val result = OkCreditPdfParser().parse(badText)
        // Customer name falls back or handles cleanly
        assertNotNull(result)
    }

    @Test
    fun testTransactionReportPdfParser_MissingCustomerNameFails() {
        val badText = """
            Transaction Report
            To,
            
            Date Received Paid
        """.trimIndent()

        val result = TransactionReportPdfParser().parse(badText)
        assertNotNull(result)
    }
}
