package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.data.CustomerEntity
import com.example.data.DailyExpenseRepository
import com.example.data.TransactionEntity
import kotlinx.coroutines.flow.first
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

import com.example.domain.pdf.PdfImportManager
import com.example.domain.pdf.OkCreditPdfParser
import com.example.domain.pdf.TransactionReportPdfParser
import com.example.domain.pdf.PdfPreviewItem
import com.example.domain.pdf.PreviewTransaction
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import android.os.Environment
import android.media.MediaScannerConnection

enum class PdfFormatType {
    OK_CREDIT_STATEMENT,
    TRANSACTION_REPORT,
    UNSUPPORTED
}

data class ExtractedCustomerInfo(
    val name: String,
    val mobileNumber: String = "",
    val address: String = "",
    val email: String = "",
    val notes: String = "",
    val formatType: PdfFormatType
)

data class PdfParseResult(
    val isSuccess: Boolean,
    val customerInfo: ExtractedCustomerInfo? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val errorMessage: String? = null
)

data class Phase1Result(
    val formatType: PdfFormatType,
    val extractedCustomer: ExtractedCustomerInfo?,
    val matchedCustomer: CustomerEntity?,
    val isNewCustomerCreated: Boolean,
    val rawPdfText: String,
    val errorMessage: String? = null
)

object PdfImportEngine {

    private var isPdfBoxInitialized = false

    private fun initPdfBoxIfNeeded(context: Context) {
        if (!isPdfBoxInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isPdfBoxInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Reads text from the given PDF Uri.
     */
    fun extractPdfText(context: Context, pdfUri: Uri): String {
        initPdfBoxIfNeeded(context)
        var inputStream: InputStream? = null
        var document: PDDocument? = null
        return try {
            inputStream = context.contentResolver.openInputStream(pdfUri)
            if (inputStream == null) return ""
            document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.getText(document) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            try { document?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Automatically detects the PDF format based on recognition keywords.
     */
    fun detectFormat(pdfText: String): PdfFormatType {
        val okCreditParser = OkCreditPdfParser()
        if (okCreditParser.canParse(pdfText)) {
            return PdfFormatType.OK_CREDIT_STATEMENT
        }
        val reportParser = TransactionReportPdfParser()
        if (reportParser.canParse(pdfText)) {
            return PdfFormatType.TRANSACTION_REPORT
        }
        return PdfFormatType.UNSUPPORTED
    }

    /**
     * Parse PDF text using format-specific strict rule parsers.
     */
    fun parsePdf(pdfText: String, formatType: PdfFormatType, customerId: Long = 0L): PdfParseResult {
        return when (formatType) {
            PdfFormatType.OK_CREDIT_STATEMENT -> OkCreditPdfParser().parse(pdfText, customerId)
            PdfFormatType.TRANSACTION_REPORT -> TransactionReportPdfParser().parse(pdfText, customerId)
            PdfFormatType.UNSUPPORTED -> PdfParseResult(
                isSuccess = false,
                errorMessage = "Unsupported PDF format."
            )
        }
    }

    /**
     * Extracts customer information according to the format specification.
     */
    fun extractCustomerInfo(pdfText: String, formatType: PdfFormatType): ExtractedCustomerInfo? {
        val result = parsePdf(pdfText, formatType)
        return result.customerInfo
    }

    /**
     * Extracts every transaction row using strict parser rules.
     */
    fun extractTransactionsFromPdfText(
        pdfText: String,
        formatType: PdfFormatType,
        customerId: Long
    ): List<TransactionEntity> {
        val result = parsePdf(pdfText, formatType, customerId)
        return result.transactions
    }

    fun validateOkCreditBalance(pdfText: String, transactions: List<TransactionEntity>): String? {
        val balanceRegex = Regex("Current Balance\\s*₹?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val match = balanceRegex.find(pdfText) ?: return null
        val pdfBalance = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

        val totalCredit = transactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
        val totalPayment = transactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
        val calculatedBalance = totalCredit - totalPayment

        if (kotlin.math.abs(pdfBalance - calculatedBalance) > 1.0) {
            return "Balance mismatch (PDF Current Balance: ₹$pdfBalance, Calculated: ₹$calculatedBalance)"
        }
        return null
    }

    fun validateTransactionReportTotals(pdfText: String, transactions: List<TransactionEntity>): String? {
        val totalReceived = transactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
        val totalPaid = transactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }

        val rxRec = Regex("Total Amount Received\\s*₹?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val rxPaid = Regex("Total Amount Paid\\s*₹?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)

        val matchRec = rxRec.find(pdfText)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val matchPaid = rxPaid.find(pdfText)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        if (matchRec != null && kotlin.math.abs(matchRec - totalReceived) > 1.0) {
            return "Balance mismatch (PDF Total Received: ₹$matchRec vs Calculated: ₹$totalReceived)"
        }
        if (matchPaid != null && kotlin.math.abs(matchPaid - totalPaid) > 1.0) {
            return "Balance mismatch (PDF Total Paid: ₹$matchPaid vs Calculated: ₹$totalPaid)"
        }

        return null
    }

    /**
     * Phase 2 Result Data Class
     */
    data class Phase2Result(
        val customerId: Long,
        val extractedTransactions: List<TransactionEntity>,
        val insertedTransactionIds: List<Long> = emptyList(),
        val errorMessage: String? = null
    )

    private val dateFormats = listOf(
        java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.US),
        java.text.SimpleDateFormat("dd MMM yyyy hh:mm a", java.util.Locale.US),
        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US),
        java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.US),
        java.text.SimpleDateFormat("dd MMM yy", java.util.Locale.US)
    )

    fun parseDateToTimestamp(dateStr: String): Long {
        val cleanDate = dateStr.trim()
            .replace(",", " ")
            .replace(Regex("\\s+"), " ")

        for (format in dateFormats) {
            try {
                format.isLenient = true
                val date = format.parse(cleanDate)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }

        val dateRegex = Regex("(\\d{1,2}[\\s/-][A-Za-z]{3,4}[\\s/-]\\d{2,4}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{4}-\\d{2}-\\d{2})")
        val match = dateRegex.find(cleanDate)
        if (match != null) {
            val matchedStr = match.value
            for (format in dateFormats) {
                try {
                    format.isLenient = true
                    val date = format.parse(matchedStr)
                    if (date != null) return date.time
                } catch (_: Exception) {}
            }
        }

        return System.currentTimeMillis()
    }

    data class Phase3Result(
        val formatType: PdfFormatType,
        val customer: CustomerEntity,
        val isNewCustomer: Boolean,
        val transactions: List<TransactionEntity>,
        val jsonPayload: String,
        val errorMessage: String? = null
    )

    fun generateBackupJson(customer: CustomerEntity, transactions: List<TransactionEntity>): String {
        val json = org.json.JSONObject()
        val customerObj = org.json.JSONObject().apply {
            put("id", customer.id)
            put("name", customer.name)
            put("mobileNumber", customer.mobileNumber)
            put("address", customer.address)
            put("email", customer.email)
            put("notes", customer.notes)
            put("collectToday", customer.collectToday)
            put("collectDueDate", customer.collectDueDate ?: 0L)
            put("lastUpdatedTimestamp", customer.lastUpdatedTimestamp)
        }

        val txArray = org.json.JSONArray()
        for (tx in transactions) {
            val txObj = org.json.JSONObject().apply {
                put("id", tx.id)
                put("customerId", tx.customerId)
                put("transactionType", tx.transactionType)
                put("amount", tx.amount)
                put("dateTimestamp", tx.dateTimestamp)
                put("comment", tx.comment)
                put("category", tx.category.ifBlank { "General" })
                put("paymentMode", tx.paymentMode.ifBlank { "None" })
                put("attachmentPath", tx.attachmentPath)
            }
            txArray.put(txObj)
        }

        json.put("customer", customerObj)
        json.put("transactions", txArray)
        json.put("customers", org.json.JSONArray().put(customerObj))

        return json.toString(2)
    }

    data class PdfFailureDetail(
        val fileName: String,
        val reason: String
    )

    data class MultiPdfImportSummary(
        val pdfSelected: Int,
        val pdfImported: Int,
        val customersCreated: Int,
        val customersUpdated: Int,
        val transactionsImported: Int,
        val duplicateTransactionsSkipped: Int,
        val failedPdfs: Int,
        val importTimeSeconds: Long,
        val failedDetails: List<PdfFailureDetail>
    )

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { p ->
                val cut = p.lastIndexOf('/')
                if (cut != -1) p.substring(cut + 1) else p
            }
        }
        return name
    }

    /**
     * Phase 4: Processes multiple PDF files sequentially while preventing duplicate customers and transactions.
     * Guaranteed strict isolation per PDF.
     */
    suspend fun processMultiplePdfs(
        context: Context,
        pdfUris: List<Uri>,
        repository: DailyExpenseRepository,
        onProgress: (currentIndex: Int, totalCount: Int, currentCustomerName: String) -> Unit
    ): MultiPdfImportSummary {
        val startTime = System.currentTimeMillis()
        var pdfImported = 0
        var customersCreated = 0
        var customersUpdated = 0
        var transactionsImported = 0
        var duplicateTransactionsSkipped = 0
        var failedPdfs = 0
        val failedDetails = mutableListOf<PdfFailureDetail>()

        val processedPdfHashes = mutableSetOf<String>()

        for ((index, uri) in pdfUris.withIndex()) {
            val fileName = getFileNameFromUri(context, uri) ?: "PDF #${index + 1}"
            try {
                val pdfText = extractPdfText(context, uri)
                if (pdfText.isBlank()) {
                    failedPdfs++
                    failedDetails.add(PdfFailureDetail(fileName, "Corrupted or unreadable PDF."))
                    continue
                }

                val pdfHash = pdfText.trim().hashCode().toString()
                if (processedPdfHashes.contains(pdfHash)) {
                    failedPdfs++
                    failedDetails.add(PdfFailureDetail(fileName, "Duplicate PDF file ignored."))
                    continue
                }
                processedPdfHashes.add(pdfHash)

                val scope = PdfImportManager.parseInIsolatedScope(
                    sourceIdentifier = fileName,
                    pdfText = pdfText,
                    customerId = 0L
                )

                val parseResult = scope.parseResult
                if (!parseResult.isSuccess || parseResult.customerInfo == null || parseResult.transactions.isEmpty()) {
                    failedPdfs++
                    failedDetails.add(
                        PdfFailureDetail(
                            fileName,
                            parseResult.errorMessage ?: "No valid dated transaction entries found."
                        )
                    )
                    continue
                }

                val custInfo = parseResult.customerInfo
                val cleanName = custInfo.name.trim()
                if (cleanName.isBlank()) {
                    failedPdfs++
                    failedDetails.add(PdfFailureDetail(fileName, "Customer name not found in PDF."))
                    continue
                }

                onProgress(index + 1, pdfUris.size, cleanName)

                val extractedTxs = parseResult.transactions

                repository.runInTransaction {
                    val existingCustomers = repository.allCustomers.first()
                    val matchedCustomer = existingCustomers.find {
                        it.name.trim().equals(cleanName, ignoreCase = true)
                    }

                    val isNewCustomer: Boolean
                    val customer: CustomerEntity
                    if (matchedCustomer != null) {
                        isNewCustomer = false
                        customer = matchedCustomer
                        customersUpdated++
                    } else {
                        isNewCustomer = true
                        val newCust = CustomerEntity(
                            name = cleanName,
                            mobileNumber = custInfo.mobileNumber.trim(),
                            address = custInfo.address.trim(),
                            email = custInfo.email.trim(),
                            notes = custInfo.notes.trim(),
                            collectToday = false,
                            collectDueDate = null,
                            createdTimestamp = System.currentTimeMillis(),
                            lastUpdatedTimestamp = System.currentTimeMillis()
                        )
                        val newId = repository.insertCustomer(newCust)
                        customer = newCust.copy(id = newId)
                        customersCreated++
                    }

                    val existingDbTxs = repository.getTransactionsForCustomerDirect(customer.id)

                    for (tx in extractedTxs) {
                        val txWithCustId = tx.copy(customerId = customer.id)
                        val isDuplicate = existingDbTxs.any { dbTx ->
                            dbTx.customerId == customer.id &&
                            dbTx.transactionType == txWithCustId.transactionType &&
                            kotlin.math.abs(dbTx.amount - txWithCustId.amount) < 0.01 &&
                            dbTx.dateTimestamp == txWithCustId.dateTimestamp &&
                            dbTx.comment.trim().equals(txWithCustId.comment.trim(), ignoreCase = true)
                        }

                        if (isDuplicate) {
                            duplicateTransactionsSkipped++
                        } else {
                            repository.insertTransaction(txWithCustId)
                            transactionsImported++
                        }
                    }
                }

                pdfImported++
            } catch (e: Exception) {
                failedPdfs++
                failedDetails.add(PdfFailureDetail(fileName, e.message ?: "Import error."))
            }
        }

        val elapsedTime = maxOf(1L, (System.currentTimeMillis() - startTime) / 1000)
        return MultiPdfImportSummary(
            pdfSelected = pdfUris.size,
            pdfImported = pdfImported,
            customersCreated = customersCreated,
            customersUpdated = customersUpdated,
            transactionsImported = transactionsImported,
            duplicateTransactionsSkipped = duplicateTransactionsSkipped,
            failedPdfs = failedPdfs,
            importTimeSeconds = elapsedTime,
            failedDetails = failedDetails
        )
    }

    /**
     * Generates editable preview items for selected PDF URIs before committing to DB.
     */
    suspend fun generatePreviewItems(
        context: Context,
        pdfUris: List<Uri>
    ): List<PdfPreviewItem> {
        val previewItems = mutableListOf<PdfPreviewItem>()

        for ((index, uri) in pdfUris.withIndex()) {
            val fileName = getFileNameFromUri(context, uri) ?: "Statement_${index + 1}.pdf"
            val pdfText = extractPdfText(context, uri)

            if (pdfText.isBlank()) {
                previewItems.add(
                    PdfPreviewItem(
                        uri = uri,
                        fileName = fileName,
                        formatType = PdfFormatType.UNSUPPORTED,
                        customerName = "",
                        mobileNumber = "",
                        address = "",
                        isSuccess = false,
                        errorMessage = "Unable to read text from PDF file.",
                        transactions = mutableListOf()
                    )
                )
                continue
            }

            val scope = PdfImportManager.parseInIsolatedScope(
                sourceIdentifier = fileName,
                pdfText = pdfText,
                customerId = 0L
            )

            val result = scope.parseResult
            val custInfo = result.customerInfo ?: ExtractedCustomerInfo(name = "", formatType = scope.formatType)
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US)

            val extractedImagePaths = try {
                com.example.utils.ImageUtils.extractImagesFromPdf(context, uri)
            } catch (_: Exception) {
                emptyList()
            }

            val txPreviews = result.transactions.mapIndexed { txIdx, tx ->
                val attachPath = if (!tx.attachmentPath.isNullOrBlank()) {
                    tx.attachmentPath!!
                } else if (extractedImagePaths.isNotEmpty()) {
                    if (extractedImagePaths.size == result.transactions.size) {
                        extractedImagePaths[txIdx]
                    } else {
                        extractedImagePaths.joinToString("|")
                    }
                } else {
                    ""
                }

                PreviewTransaction(
                    originalIndex = txIdx,
                    dateTimestamp = tx.dateTimestamp,
                    dateString = dateFormat.format(java.util.Date(tx.dateTimestamp)),
                    transactionType = tx.transactionType,
                    amountText = String.format(java.util.Locale.US, "%.2f", tx.amount),
                    comment = tx.comment,
                    attachmentPath = attachPath,
                    isError = tx.amount <= 0.0,
                    errorMessage = if (tx.amount <= 0.0) "Invalid transaction amount" else null
                )
            }.toMutableList()

            val isSupported = scope.formatType != PdfFormatType.UNSUPPORTED
            val isSuccess = isSupported && custInfo.name.isNotBlank() && txPreviews.isNotEmpty()
            val errorMsg = when {
                !isSupported -> "Unsupported PDF format. You can fill customer details and add transactions manually."
                custInfo.name.isBlank() && txPreviews.isEmpty() -> "Customer name and transactions not fetched. Please fill customer name and add transactions below."
                custInfo.name.isBlank() -> "Customer name not fetched. Please type customer name below."
                txPreviews.isEmpty() -> "No transactions fetched from PDF. Click 'Add Tx' below to add manually."
                !result.isSuccess -> result.errorMessage ?: "Partial PDF import: please review details below."
                else -> result.errorMessage
            }

            previewItems.add(
                PdfPreviewItem(
                    uri = uri,
                    fileName = fileName,
                    formatType = scope.formatType,
                    customerName = custInfo.name,
                    mobileNumber = custInfo.mobileNumber,
                    address = custInfo.address,
                    isSuccess = isSuccess,
                    errorMessage = errorMsg,
                    transactions = txPreviews
                )
            )
        }

        return previewItems
    }

    /**
     * Commits confirmed/edited preview items into the database in strict document-level transaction isolation.
     */
    suspend fun commitPreviewItems(
        context: Context,
        items: List<PdfPreviewItem>,
        repository: DailyExpenseRepository,
        onProgress: (currentIndex: Int, totalCount: Int, currentCustomerName: String) -> Unit
    ): MultiPdfImportSummary {
        val startTime = System.currentTimeMillis()
        var pdfImported = 0
        var customersCreated = 0
        var customersUpdated = 0
        var transactionsImported = 0
        var duplicateTransactionsSkipped = 0
        var failedPdfs = 0
        val failedDetails = mutableListOf<PdfFailureDetail>()

        for ((index, item) in items.withIndex()) {
            val fileName = item.fileName
            val cleanName = item.customerName.trim()
            val cleanMobile = item.mobileNumber.trim()

            val validTxs = item.transactions.filter { (it.amountText.replace(",", "").toDoubleOrNull() ?: 0.0) > 0.0 }
            if (cleanName.isBlank() || validTxs.isEmpty()) {
                failedPdfs++
                failedDetails.add(
                    PdfFailureDetail(
                        fileName = fileName,
                        reason = if (cleanName.isBlank()) "Customer name is blank. Please enter customer name." else "No valid transactions with amount > 0. Please click '+ Add Tx' to add manually."
                    )
                )
                continue
            }

            onProgress(index + 1, items.size, cleanName)

            try {
                repository.runInTransaction {
                    val existingCustomers = repository.allCustomers.first()
                    val matchedCustomer = existingCustomers.find {
                        it.name.trim().equals(cleanName, ignoreCase = true)
                    }

                    val customer: CustomerEntity
                    if (matchedCustomer != null) {
                        customer = matchedCustomer
                        customersUpdated++
                    } else {
                        val newCust = CustomerEntity(
                            name = cleanName,
                            mobileNumber = cleanMobile,
                            address = "",
                            email = "",
                            notes = "",
                            collectToday = false,
                            collectDueDate = null,
                            createdTimestamp = System.currentTimeMillis(),
                            lastUpdatedTimestamp = System.currentTimeMillis()
                        )
                        val newId = repository.insertCustomer(newCust)
                        customer = newCust.copy(id = newId)
                        customersCreated++
                    }

                    val existingDbTxs = repository.getTransactionsForCustomerDirect(customer.id)

                    for (txPreview in item.transactions) {
                        val amt = txPreview.amountText.replace(",", "").toDoubleOrNull() ?: 0.0
                        if (amt <= 0.0) continue

                        val txEntity = TransactionEntity(
                            id = 0,
                            customerId = customer.id,
                            transactionType = txPreview.transactionType,
                            amount = amt,
                            dateTimestamp = txPreview.dateTimestamp,
                            comment = txPreview.comment.ifBlank { "Imported Entry" },
                            category = "General",
                            attachmentPath = txPreview.attachmentPath,
                            paymentMode = "None",
                            status = "COMPLETED"
                        )

                        val isDuplicate = existingDbTxs.any { dbTx ->
                            dbTx.customerId == customer.id &&
                            dbTx.transactionType == txEntity.transactionType &&
                            kotlin.math.abs(dbTx.amount - txEntity.amount) < 0.01 &&
                            dbTx.dateTimestamp == txEntity.dateTimestamp &&
                            dbTx.comment.trim().equals(txEntity.comment.trim(), ignoreCase = true)
                        }

                        if (isDuplicate) {
                            duplicateTransactionsSkipped++
                        } else {
                            repository.insertTransaction(txEntity)
                            transactionsImported++
                        }
                    }
                }
                pdfImported++
            } catch (e: Exception) {
                failedPdfs++
                failedDetails.add(PdfFailureDetail(fileName, e.message ?: "Failed to import."))
            }
        }

        val elapsedTime = maxOf(1L, (System.currentTimeMillis() - startTime) / 1000)
        return MultiPdfImportSummary(
            pdfSelected = items.size,
            pdfImported = pdfImported,
            customersCreated = customersCreated,
            customersUpdated = customersUpdated,
            transactionsImported = transactionsImported,
            duplicateTransactionsSkipped = duplicateTransactionsSkipped,
            failedPdfs = failedPdfs,
            importTimeSeconds = elapsedTime,
            failedDetails = failedDetails
        )
    }

    /**
     * Generate byte array for OkCredit Sample PDF.
     */
    fun generateOkCreditSamplePdfBytes(context: Context): ByteArray {
        initPdfBoxIfNeeded(context)
        val okCreditDoc = PDDocument()
        return try {
            val page1 = PDPage()
            okCreditDoc.addPage(page1)
            val stream1 = PDPageContentStream(okCreditDoc, page1)
            stream1.beginText()
            stream1.setFont(PDType1Font.HELVETICA_BOLD, 14f)
            stream1.newLineAtOffset(50f, 750f)
            stream1.showText("Customer:")
            stream1.newLineAtOffset(0f, -20f)
            stream1.showText("October 2025 Deduction")
            stream1.newLineAtOffset(0f, -20f)
            stream1.setFont(PDType1Font.HELVETICA, 11f)
            stream1.showText("Mobile: 9876543210")
            stream1.newLineAtOffset(0f, -15f)
            stream1.showText("Sector 12, Phase 1")
            stream1.newLineAtOffset(0f, -25f)
            stream1.showText("Payment (0)")
            stream1.newLineAtOffset(0f, -15f)
            stream1.showText("Credit (69)")
            stream1.newLineAtOffset(0f, -15f)
            stream1.showText("Current Balance Rs 800")
            stream1.newLineAtOffset(0f, -30f)

            stream1.showText("05 Oct 2025")
            stream1.newLineAtOffset(0f, -15f)
            stream1.showText("Petrol expense")
            stream1.newLineAtOffset(0f, -15f)
            stream1.showText("Rs 300 Credit")
            stream1.newLineAtOffset(0f, -25f)

            stream1.showText("05 Oct 2025")
            stream1.newLineAtOffset(0f, -15f)
            stream1.showText("Food expense")
            stream1.newLineAtOffset(0f, -15f)
            stream1.showText("Rs 500 Credit")
            stream1.newLineAtOffset(0f, -25f)

            stream1.endText()
            stream1.close()

            val baos = ByteArrayOutputStream()
            okCreditDoc.save(baos)
            baos.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        } finally {
            try { okCreditDoc.close() } catch (_: Exception) {}
        }
    }

    /**
     * Generate byte array for Transaction Report Sample PDF.
     */
    fun generateTransactionReportSamplePdfBytes(context: Context): ByteArray {
        initPdfBoxIfNeeded(context)
        val reportDoc = PDDocument()
        return try {
            val page2 = PDPage()
            reportDoc.addPage(page2)
            val stream2 = PDPageContentStream(reportDoc, page2)
            stream2.beginText()
            stream2.setFont(PDType1Font.HELVETICA_BOLD, 14f)
            stream2.newLineAtOffset(50f, 750f)
            stream2.showText("Transaction Report")
            stream2.newLineAtOffset(0f, -20f)
            stream2.setFont(PDType1Font.HELVETICA, 11f)
            stream2.showText("To,")
            stream2.newLineAtOffset(0f, -15f)
            stream2.showText("Rent Vishwas City")
            stream2.newLineAtOffset(0f, -15f)
            stream2.showText("Sector 12, Phase 1")
            stream2.newLineAtOffset(0f, -25f)
            stream2.showText("Date Notes Received Paid Balance")
            stream2.newLineAtOffset(0f, -20f)

            stream2.showText("12/10/2025 500 0 500")
            stream2.newLineAtOffset(0f, -15f)
            stream2.showText("Advance Rent")
            stream2.newLineAtOffset(0f, -25f)

            stream2.showText("13/10/2025 0 200 300")
            stream2.newLineAtOffset(0f, -15f)
            stream2.showText("Maintenance Fee")
            stream2.newLineAtOffset(0f, -25f)

            stream2.showText("Total Amount Received 500")
            stream2.newLineAtOffset(0f, -15f)
            stream2.showText("Total Amount Paid 200")
            stream2.newLineAtOffset(0f, -15f)
            stream2.showText("Yet to Receive 300")
            stream2.endText()
            stream2.close()

            val baos = ByteArrayOutputStream()
            reportDoc.save(baos)
            baos.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        } finally {
            try { reportDoc.close() } catch (_: Exception) {}
        }
    }

    /**
     * Helper to check if sample PDF files already exist in public Downloads folder.
     */
    fun checkSamplePdfsExist(context: Context): Boolean {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val f1 = File(downloadsDir, "OkCredit_Sample_Customer_Statement.pdf")
        val f2 = File(downloadsDir, "Transaction_Report_Sample.pdf")
        if (f1.exists() || f2.exists()) return true
        val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appDownloads != null) {
            val af1 = File(appDownloads, "OkCredit_Sample_Customer_Statement.pdf")
            val af2 = File(appDownloads, "Transaction_Report_Sample.pdf")
            if (af1.exists() || af2.exists()) return true
        }
        return false
    }

    private fun writeBytesToDownloads(context: Context, baseName: String, bytes: ByteArray, replace: Boolean): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        var targetFile = File(downloadsDir, "$baseName.pdf")
        if (!replace && targetFile.exists()) {
            var count = 1
            while (targetFile.exists()) {
                targetFile = File(downloadsDir, "${baseName}_($count).pdf")
                count++
            }
        }

        return try {
            if (targetFile.exists() && replace) {
                targetFile.delete()
            }
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf("application/pdf"),
                null
            )
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                var appFile = File(appDownloads, "$baseName.pdf")
                if (!replace && appFile.exists()) {
                    var count = 1
                    while (appFile.exists()) {
                        appFile = File(appDownloads, "${baseName}_($count).pdf")
                        count++
                    }
                }
                if (appFile.exists() && replace) {
                    appFile.delete()
                }
                FileOutputStream(appFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
                appFile
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        }
    }

    /**
     * Helper to generate sample PDF files in Downloads folder.
     */
    fun createSamplePdfs(context: Context, replace: Boolean = true): List<File> {
        val createdFiles = mutableListOf<File>()
        val bytes1 = generateOkCreditSamplePdfBytes(context)
        if (bytes1.isNotEmpty()) {
            val f1 = writeBytesToDownloads(context, "OkCredit_Sample_Customer_Statement", bytes1, replace)
            if (f1 != null) createdFiles.add(f1)
        }
        val bytes2 = generateTransactionReportSamplePdfBytes(context)
        if (bytes2.isNotEmpty()) {
            val f2 = writeBytesToDownloads(context, "Transaction_Report_Sample", bytes2, replace)
            if (f2 != null) createdFiles.add(f2)
        }
        return createdFiles
    }

    suspend fun executePhase3(
        context: Context,
        pdfUri: Uri,
        repository: DailyExpenseRepository
    ): Phase3Result {
        val phase1 = executePhase1(context, pdfUri, repository)
        if (phase1.matchedCustomer == null) {
            return Phase3Result(
                formatType = phase1.formatType,
                customer = CustomerEntity(name = "Unknown", mobileNumber = ""),
                isNewCustomer = false,
                transactions = emptyList(),
                jsonPayload = "",
                errorMessage = phase1.errorMessage ?: "Failed to extract/create customer."
            )
        }

        val customer = phase1.matchedCustomer
        val phase2 = executePhase2(context, pdfUri, customer.id, repository)

        val jsonPayload = generateBackupJson(customer, phase2.extractedTransactions)

        return Phase3Result(
            formatType = phase1.formatType,
            customer = customer,
            isNewCustomer = phase1.isNewCustomerCreated,
            transactions = phase2.extractedTransactions,
            jsonPayload = jsonPayload,
            errorMessage = phase2.errorMessage
        )
    }

    suspend fun executePhase2(
        context: Context,
        pdfUri: Uri,
        customerId: Long,
        repository: DailyExpenseRepository? = null
    ): Phase2Result {
        val pdfText = extractPdfText(context, pdfUri)
        if (pdfText.isBlank()) {
            return Phase2Result(
                customerId = customerId,
                extractedTransactions = emptyList(),
                errorMessage = "Unable to read text from PDF file."
            )
        }

        val formatType = detectFormat(pdfText)
        if (formatType == PdfFormatType.UNSUPPORTED) {
            return Phase2Result(
                customerId = customerId,
                extractedTransactions = emptyList(),
                errorMessage = "Unsupported PDF format."
            )
        }

        val transactions = extractTransactionsFromPdfText(pdfText, formatType, customerId)

        val insertedIds = mutableListOf<Long>()
        if (repository != null && transactions.isNotEmpty()) {
            for (tx in transactions) {
                val id = repository.insertTransaction(tx)
                insertedIds.add(id)
            }
        }

        return Phase2Result(
            customerId = customerId,
            extractedTransactions = transactions,
            insertedTransactionIds = insertedIds
        )
    }

    suspend fun executePhase1(
        context: Context,
        pdfUri: Uri,
        repository: DailyExpenseRepository
    ): Phase1Result {
        val pdfText = extractPdfText(context, pdfUri)
        if (pdfText.isBlank()) {
            return Phase1Result(
                formatType = PdfFormatType.UNSUPPORTED,
                extractedCustomer = null,
                matchedCustomer = null,
                isNewCustomerCreated = false,
                rawPdfText = "",
                errorMessage = "Unable to read text from PDF file."
            )
        }

        val formatType = detectFormat(pdfText)
        if (formatType == PdfFormatType.UNSUPPORTED) {
            return Phase1Result(
                formatType = PdfFormatType.UNSUPPORTED,
                extractedCustomer = null,
                matchedCustomer = null,
                isNewCustomerCreated = false,
                rawPdfText = pdfText,
                errorMessage = "Unsupported PDF format. Could not match OkCredit or Transaction Report keywords."
            )
        }

        val extractedInfo = extractCustomerInfo(pdfText, formatType)
            ?: return Phase1Result(
                formatType = formatType,
                extractedCustomer = null,
                matchedCustomer = null,
                isNewCustomerCreated = false,
                rawPdfText = pdfText,
                errorMessage = "Failed to extract customer information from PDF."
            )

        val cleanName = extractedInfo.name.trim()
        val existingCustomers = repository.allCustomers.first()
        val existingMatch = existingCustomers.find {
            it.name.trim().equals(cleanName, ignoreCase = true)
        }

        return if (existingMatch != null) {
            Phase1Result(
                formatType = formatType,
                extractedCustomer = extractedInfo,
                matchedCustomer = existingMatch,
                isNewCustomerCreated = false,
                rawPdfText = pdfText
            )
        } else {
            val newCustomer = CustomerEntity(
                name = cleanName,
                mobileNumber = extractedInfo.mobileNumber.trim(),
                address = extractedInfo.address.trim(),
                email = extractedInfo.email.trim(),
                notes = extractedInfo.notes.trim(),
                collectToday = false,
                collectDueDate = null,
                createdTimestamp = System.currentTimeMillis(),
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
            val insertedId = repository.insertCustomer(newCustomer)
            val createdCustomer = newCustomer.copy(id = insertedId)

            Phase1Result(
                formatType = formatType,
                extractedCustomer = extractedInfo,
                matchedCustomer = createdCustomer,
                isNewCustomerCreated = true,
                rawPdfText = pdfText
            )
        }
    }
}
