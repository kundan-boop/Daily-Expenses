package com.example.domain.pdf

import com.example.data.TransactionEntity
import com.example.utils.ExtractedCustomerInfo
import com.example.utils.PdfFormatType
import com.example.utils.PdfParseResult
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Base interface for PDF parsers.
 */
interface PdfParser {
    fun canParse(pdfText: String): Boolean
    fun parse(pdfText: String, customerId: Long = 0L): PdfParseResult
}

/**
 * Strict rule-based parser for OkCredit PDF statements.
 */
class OkCreditPdfParser : PdfParser {

    private val dateRegex = Regex(
        "(?:^|\\b)(\\d{1,2}[\\s/-][A-Za-z0-9]{3,4}[\\s/-]\\d{2,4}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{4}-\\d{2}-\\d{2})(?:\\b|$)",
        RegexOption.IGNORE_CASE
    )

    override fun canParse(pdfText: String): Boolean {
        val lowerText = pdfText.lowercase()
        val hasCustomerHeader = lowerText.contains("customer:") || lowerText.contains("customer :") || lowerText.contains("customer")
        val hasOkCreditHeader = lowerText.contains("okcredit") || lowerText.contains("powered by okcredit")
        val hasKeywords = lowerText.contains("current balance") || lowerText.contains("mobile:") || lowerText.contains("payment") || lowerText.contains("credit")
        return (hasCustomerHeader && hasOkCreditHeader) || (hasOkCreditHeader && hasKeywords) || (hasCustomerHeader && hasKeywords)
    }

    override fun parse(pdfText: String, customerId: Long): PdfParseResult {
        val rawLines = pdfText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        if (!canParse(pdfText)) {
            return PdfParseResult(
                isSuccess = false,
                errorMessage = "OkCredit header or customer details not found in PDF."
            )
        }

        // 1. Customer Name Extraction
        // Rule: find line "Customer:" and take text on it (or next line if on separate line)
        var customerName = ""
        var mobileNumber = ""

        for (i in rawLines.indices) {
            val line = rawLines[i]
            val lowerLine = line.lowercase()

            if (lowerLine.contains("customer:") || lowerLine.contains("customer :") || lowerLine == "customer") {
                val idx = lowerLine.indexOf("customer")
                val afterColon = if (idx != -1) line.substring(idx).replace(Regex("(?i)^customer[:\\s]*"), "").trim() else ""

                if (afterColon.isNotEmpty() && !afterColon.startsWith(":") && !afterColon.startsWith("(")) {
                    customerName = afterColon
                } else {
                    for (j in (i + 1) until minOf(i + 5, rawLines.size)) {
                        val nextLine = rawLines[j].trim()
                        val nextLower = nextLine.lowercase()
                        if (nextLine.isNotEmpty() &&
                            !nextLower.startsWith("mobile") &&
                            !nextLower.startsWith("date") &&
                            !nextLower.startsWith("payment") &&
                            !nextLower.startsWith("credit") &&
                            !nextLower.contains("current balance") &&
                            !nextLower.contains("total balance") &&
                            !nextLower.contains("powered by") &&
                            !nextLower.contains("account owner") &&
                            !nextLower.startsWith("customer")
                        ) {
                            customerName = nextLine
                            break
                        }
                    }
                }
                if (customerName.isNotEmpty()) break
            }
        }

        // 2. Mobile Number Extraction
        for (i in rawLines.indices) {
            val line = rawLines[i]
            val lowerLine = line.lowercase()
            if (lowerLine.contains("mobile:") || lowerLine.contains("mobile :") || lowerLine.startsWith("mobile")) {
                val afterColon = line.replace(Regex("(?i)^mobile[:\\s]*"), "").trim()
                val phoneMatch = Regex("\\b\\d{10,12}\\b").find(afterColon)
                    ?: if (i + 1 < rawLines.size) Regex("\\b\\d{10,12}\\b").find(rawLines[i + 1]) else null
                if (phoneMatch != null) {
                    mobileNumber = phoneMatch.value
                }
            }
        }

        customerName = customerName
            .replace(Regex("(?i)^customer[:\\s]*"), "")
            .trim()

        val extractedCustomer = ExtractedCustomerInfo(
            name = customerName,
            mobileNumber = mobileNumber,
            address = "",
            email = "",
            notes = "",
            formatType = PdfFormatType.OK_CREDIT_STATEMENT
        )

        // Extract summary counts & totals for Payment vs Credit to enforce strict column mapping
        val rxPaymentCount = Regex("Payment\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)
        val rxCreditCount = Regex("Credit\\s*\\(\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)
        val paymentCount = rxPaymentCount.find(pdfText)?.groupValues?.get(1)?.toIntOrNull()
        val creditCount = rxCreditCount.find(pdfText)?.groupValues?.get(1)?.toIntOrNull()

        val rxPaymentTotal = Regex("(?:Total\\s+Payment|Payment)\\s*₹?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val rxCreditTotal = Regex("(?:Total\\s+Credit|Credit)\\s*₹?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val paymentTotal = rxPaymentTotal.find(pdfText)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val creditTotal = rxCreditTotal.find(pdfText)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        // 3. Extract Row-wise Transactions (Date | Notes | Payment | Credit)
        val transactions = mutableListOf<TransactionEntity>()
        var i = 0
        while (i < rawLines.size) {
            val line = rawLines[i]
            val lowerLine = line.lowercase()

            if (isSummaryOrHeaderRow(lowerLine)) {
                i++
                continue
            }

            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                val dateStr = dateMatch.value.trim()
                val dateTimestamp = parseDateToTimestamp(dateStr)

                val blockLines = mutableListOf<String>()
                blockLines.add(line)

                var j = i + 1
                while (j < rawLines.size) {
                    val nextLine = rawLines[j]
                    val nextLower = nextLine.lowercase()

                    if (dateRegex.containsMatchIn(nextLine) || isSummaryOrHeaderRow(nextLower)) {
                        break
                    }
                    blockLines.add(nextLine)
                    j++
                }

                val blockText = blockLines.joinToString(" ")
                val lowerBlock = blockText.lowercase()

                if (isSummaryOrHeaderRow(lowerBlock)) {
                    i = j
                    continue
                }

                // Payment -> I_RECEIVED, Credit -> I_PAID
                var type = "I_PAID"
                if (paymentCount == 0 || paymentTotal == 0.0) {
                    type = "I_PAID"
                } else if (creditCount == 0 || creditTotal == 0.0) {
                    type = "I_RECEIVED"
                } else {
                    if (lowerBlock.contains("payment") || lowerBlock.contains("you got") || lowerBlock.contains("received") || lowerBlock.contains("you received")) {
                        type = "I_RECEIVED"
                    } else if (lowerBlock.contains("credit") || lowerBlock.contains("you gave")) {
                        type = "I_PAID"
                    } else if (lowerBlock.contains("paid") || lowerBlock.contains("gave")) {
                        type = "I_PAID"
                    } else if (lowerBlock.contains("got")) {
                        type = "I_RECEIVED"
                    }
                }

                val amount = extractMonetaryAmount(blockText, dateStr)

                var comment = blockText
                    .replace(dateStr, "")
                    .replace(Regex("(?:₹|Rs\\.?)\\s*\\d+(?:,\\d+)*(?:\\.\\d+)?", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("(?i)\\b(credit|payment|received|got|gave|paid|you got|you gave)\\b"), "")
                if (amount != null && amount > 0) {
                    comment = comment.replace(amount.toString(), "")
                        .replace(amount.toInt().toString(), "")
                }
                comment = comment.replace(Regex("\\s+"), " ").trim()

                if (amount != null && amount > 0) {
                    transactions.add(
                        TransactionEntity(
                            id = 0,
                            customerId = customerId,
                            transactionType = type,
                            amount = amount,
                            dateTimestamp = dateTimestamp,
                            comment = comment.ifBlank { "OkCredit Entry" },
                            category = "General",
                            attachmentPath = "",
                            paymentMode = "None",
                            status = "COMPLETED"
                        )
                    )
                }

                i = j - 1
            }
            i++
        }

        if (transactions.isEmpty()) {
            return PdfParseResult(
                isSuccess = false,
                customerInfo = extractedCustomer,
                errorMessage = "No valid dated transaction entries found in OkCredit statement."
            )
        }

        return PdfParseResult(
            isSuccess = true,
            customerInfo = extractedCustomer,
            transactions = transactions
        )
    }

    private fun isSummaryOrHeaderRow(lowerLine: String): Boolean {
        return lowerLine.contains("current balance") ||
                lowerLine.contains("total balance") ||
                lowerLine.contains("net balance") ||
                lowerLine.contains("closing balance") ||
                lowerLine.contains("powered by") ||
                lowerLine.contains("payment (") ||
                lowerLine.contains("credit (") ||
                lowerLine.startsWith("customer:") ||
                lowerLine.startsWith("customer :") ||
                lowerLine.startsWith("mobile:") ||
                lowerLine.contains("total amount") ||
                lowerLine.contains("yet to receive") ||
                lowerLine.contains("grand total") ||
                lowerLine.contains("subtotal") ||
                lowerLine.contains("account owner") ||
                lowerLine.contains("statement date") ||
                lowerLine.contains("generated on") ||
                lowerLine.contains("total payment") ||
                lowerLine.contains("total credit") ||
                lowerLine.contains("total") ||
                lowerLine.startsWith("total ") ||
                lowerLine == "total" ||
                lowerLine.startsWith("grand total") ||
                lowerLine.startsWith("balance")
    }

    private fun extractMonetaryAmount(blockText: String, dateStr: String): Double? {
        val rxAmount = Regex("(?:₹|Rs\\.?)\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val matchAmount = rxAmount.find(blockText)
        var amount: Double? = matchAmount?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        if (amount == null) {
            val allNumbers = Regex("\\b\\d+(?:,\\d+)*(?:\\.\\d+)?\\b").findAll(blockText)
            val dateParts = dateStr.split(Regex("[\\s/-]"))
            for (n in allNumbers.toList().reversed()) {
                val candidate = n.value.replace(",", "").toDoubleOrNull()
                if (candidate != null && candidate > 0 && candidate != dateStr.toDoubleOrNull()) {
                    if (!dateParts.contains(n.value) && (candidate > 31 || n.value.contains("."))) {
                        amount = candidate
                        break
                    }
                }
            }
        }
        return amount
    }

    private fun parseDateToTimestamp(dateStr: String): Long {
        val cleanDate = dateStr.trim().replace(",", " ").replace(Regex("\\s+"), " ")
        val dateFormats = listOf(
            SimpleDateFormat("dd MMM yyyy", Locale.US),
            SimpleDateFormat("dd-MMM-yyyy", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yy", Locale.US)
        )

        for (format in dateFormats) {
            try {
                format.isLenient = true
                val date = format.parse(cleanDate)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}

/**
 * Strict rule-based parser for Transaction Report PDFs.
 */
class TransactionReportPdfParser : PdfParser {

    private val dateRegex = Regex(
        "(?:^|\\b)(\\d{1,2}[\\s/-][A-Za-z0-9]{3,4}[\\s/-]\\d{2,4}|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{4}-\\d{2}-\\d{2})(?:\\b|$)",
        RegexOption.IGNORE_CASE
    )

    override fun canParse(pdfText: String): Boolean {
        val lowerText = pdfText.lowercase()
        val isReportHeader = lowerText.contains("transaction report") || lowerText.contains("report summary")
        val isReportTo = lowerText.contains("to,") || lowerText.contains("to\n") || lowerText.contains("to ") || lowerText.contains("to:") || lowerText.startsWith("to")
        val isReportColumns = lowerText.contains("received") || lowerText.contains("paid")
        return isReportHeader || (isReportTo && isReportColumns)
    }

    override fun parse(pdfText: String, customerId: Long): PdfParseResult {
        val rawLines = pdfText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        if (!canParse(pdfText)) {
            return PdfParseResult(
                isSuccess = false,
                errorMessage = "Transaction Report headers or 'To,' marker not found."
            )
        }

        // 1. Customer Name Extraction
        var customerName = ""

        // Strategy A: Check lines before "Transaction Report" or "Report Summary" header
        val topCandidates = mutableListOf<String>()
        for (i in rawLines.indices) {
            val line = rawLines[i].trim()
            val lower = line.lowercase()
            if (lower.contains("transaction report") || lower.contains("report summary")) {
                break
            }
            val isDate = Regex("\\d{1,2}\\s+[a-z]{3}\\s+\\d{2,4}", RegexOption.IGNORE_CASE).containsMatchIn(lower) || lower.contains(" 20")
            val isNoise = lower.startsWith("page") || lower.startsWith("date") || lower.startsWith("pdf") || lower.contains("statement") || lower.contains("mobile") || lower.contains("customer")
            if (line.isNotEmpty() && !isDate && !isNoise && line.length in 2..60) {
                topCandidates.add(line)
            }
        }

        // Strategy B: Check "To," marker
        var toMarkerIndex = -1
        for (i in rawLines.indices) {
            val line = rawLines[i]
            val lowerLine = line.lowercase()

            val isDateRangeHeader = Regex("\\d{1,2}\\s+[a-z]{3}\\s+\\d{2,4}\\s+to\\s+\\d{1,2}\\s+[a-z]{3}\\s+\\d{2,4}", RegexOption.IGNORE_CASE).containsMatchIn(lowerLine) ||
                    (lowerLine.contains(" to ") && lowerLine.contains(" 20"))

            if (isDateRangeHeader) continue

            val isToMarker = lowerLine == "to," || lowerLine == "to:" || lowerLine == "to" ||
                    lowerLine.startsWith("to,") || lowerLine.startsWith("to:") ||
                    (lowerLine.startsWith("to ") && !lowerLine.startsWith("total")) ||
                    lowerLine.contains("to,") || lowerLine.contains("to:")

            if (isToMarker) {
                toMarkerIndex = i
                break
            }
        }

        val toCandidates = mutableListOf<String>()
        if (toMarkerIndex != -1) {
            val inlineName = rawLines[toMarkerIndex].replace(Regex("(?i)^.*to[,:\\s]*"), "")
                .replace(Regex("(?i)report summary.*|transactions count.*|total amount.*|yet to receive.*|₹.*"), "")
                .trim()

            val inlineLower = inlineName.lowercase()
            if (inlineName.isNotEmpty() &&
                !inlineLower.contains("total") &&
                !inlineLower.contains("summary") &&
                !inlineLower.contains("report") &&
                !inlineLower.contains("received") &&
                !inlineLower.contains("paid") &&
                !inlineLower.startsWith("date")
            ) {
                toCandidates.add(inlineName)
            }

            var j = toMarkerIndex + 1
            while (j < rawLines.size && toCandidates.size < 3) {
                val nextLine = rawLines[j]
                val nextLower = nextLine.lowercase()

                if (nextLower.startsWith("date") ||
                    nextLower.startsWith("report summary") ||
                    nextLower.startsWith("transactions count") ||
                    nextLower.startsWith("total amount") ||
                    nextLower.startsWith("yet to receive") ||
                    (nextLower.contains("date") && (nextLower.contains("received") || nextLower.contains("paid") || nextLower.contains("notes")))
                ) {
                    break
                }

                val cleanedLine = nextLine
                    .replace(Regex("(?i)report summary.*|transactions count.*|total amount.*|yet to receive.*|₹.*"), "")
                    .trim()

                val cleanedLower = cleanedLine.lowercase()
                if (cleanedLine.isNotEmpty() &&
                    !cleanedLower.startsWith("date") &&
                    !cleanedLower.startsWith("total") &&
                    !cleanedLower.contains("amount")
                ) {
                    toCandidates.add(cleanedLine)
                }
                j++
            }
        }

        if (topCandidates.isNotEmpty()) {
            val topName = topCandidates.joinToString(" ")
            val toText = toCandidates.joinToString(" ").lowercase()
            val isToAddress = toText.contains("city") || toText.contains("block") || toText.contains("flat") ||
                    toText.contains("road") || toText.contains("rent") || toText.contains("soc") || toText.contains("chanakyapuri")
            if (toCandidates.isEmpty() || isToAddress) {
                customerName = topName
            } else {
                customerName = toCandidates.joinToString(" ")
            }
        } else if (toCandidates.isNotEmpty()) {
            customerName = toCandidates.joinToString(" ")
        }

        customerName = customerName
            .replace(Regex("(?i)^to[,:\\s]*"), "")
            .trim()

        val extractedCustomer = ExtractedCustomerInfo(
            name = customerName,
            mobileNumber = "",
            address = "",
            email = "",
            notes = "",
            formatType = PdfFormatType.TRANSACTION_REPORT
        )

        // 2. Extract Header Summary Totals (Report Summary)
        val rxRecHeader = Regex("Total Amount Received\\s*₹?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val rxPaidHeader = Regex("Total Amount Paid\\s*₹?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)

        val summaryTotalReceived = rxRecHeader.find(pdfText)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val summaryTotalPaid = rxPaidHeader.find(pdfText)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        // 3. Detect column layout order from header
        var receivedBeforePaid = true
        for (line in rawLines) {
            val lower = line.lowercase()
            if (lower.contains("received") && lower.contains("paid")) {
                val idxRec = lower.indexOf("received")
                val idxPaid = lower.indexOf("paid")
                if (idxRec != -1 && idxPaid != -1) {
                    receivedBeforePaid = idxRec < idxPaid
                }
                break
            }
        }

        // 4. Find table start index ("date notes received paid")
        var tableHeaderIndex = -1
        for (idx in rawLines.indices) {
            val lower = rawLines[idx].lowercase()
            if (lower.contains("date") && (lower.contains("notes") || lower.contains("particulars") || lower.contains("description") || lower.contains("received") || lower.contains("paid"))) {
                tableHeaderIndex = idx
                break
            }
        }
        if (tableHeaderIndex == -1) {
            for (idx in rawLines.indices) {
                val lower = rawLines[idx].lowercase()
                if (lower.contains("received") && lower.contains("paid")) {
                    tableHeaderIndex = idx
                    break
                }
            }
        }

        // 5. Extract Row-wise Transactions starting strictly AFTER table header
        val transactions = mutableListOf<TransactionEntity>()
        var i = if (tableHeaderIndex != -1) tableHeaderIndex + 1 else 0
        while (i < rawLines.size) {
            val line = rawLines[i]
            val lowerLine = line.lowercase()

            if (isSummaryOrHeaderRow(lowerLine)) {
                i++
                continue
            }

            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                val dateStr = dateMatch.value.trim()
                val dateTimestamp = parseDateToTimestamp(dateStr)

                val blockLines = mutableListOf<String>()
                blockLines.add(line)

                var j = i + 1
                while (j < rawLines.size) {
                    val nextLine = rawLines[j]
                    val nextLower = nextLine.lowercase()

                    if (dateRegex.containsMatchIn(nextLine) || isSummaryOrHeaderRow(nextLower)) {
                        break
                    }
                    blockLines.add(nextLine)
                    j++
                }

                val blockText = blockLines.joinToString(" ")
                val lowerBlock = blockText.lowercase()

                if (isSummaryOrHeaderRow(lowerBlock)) {
                    i = j
                    continue
                }

                val blockTextWithoutDate = blockText.replace(dateStr, "")
                val allNumbers = Regex("\\b\\d+(?:,\\d+)*(?:\\.\\d+)?\\b").findAll(blockTextWithoutDate)
                    .map { it.value.replace(",", "") }
                    .mapNotNull { it.toDoubleOrNull() }
                    .toList()

                var receivedAmt = 0.0
                var paidAmt = 0.0

                if (allNumbers.isNotEmpty()) {
                    if (allNumbers.size >= 2) {
                        val first = allNumbers[0]
                        val second = allNumbers[1]
                        if (receivedBeforePaid) {
                            receivedAmt = first
                            paidAmt = second
                        } else {
                            paidAmt = first
                            receivedAmt = second
                        }
                    } else if (allNumbers.size == 1) {
                        val singleAmt = allNumbers[0]
                        if (summaryTotalReceived == 0.0 && (summaryTotalPaid ?: 0.0) > 0.0) {
                            paidAmt = singleAmt
                        } else if (summaryTotalPaid == 0.0 && (summaryTotalReceived ?: 0.0) > 0.0) {
                            receivedAmt = singleAmt
                        } else if (lowerBlock.contains("paid") || lowerBlock.contains("gave") || lowerBlock.contains("maintenance") || lowerBlock.contains("fee") || lowerBlock.contains("debit") || lowerBlock.contains("bill") || lowerBlock.contains("rent") || lowerBlock.contains("gas") || lowerBlock.contains("kariyanu") || lowerBlock.contains("expense")) {
                            paidAmt = singleAmt
                        } else if (lowerBlock.contains("received") || lowerBlock.contains("got") || lowerBlock.contains("credit") || lowerBlock.contains("advance")) {
                            receivedAmt = singleAmt
                        } else if ((summaryTotalPaid ?: 0.0) > (summaryTotalReceived ?: 0.0)) {
                            paidAmt = singleAmt
                        } else {
                            receivedAmt = singleAmt
                        }
                    }
                }

                var comment = blockText.replace(dateStr, "")
                if (receivedAmt > 0) {
                    val formattedAmt = String.format(Locale.US, "%.2f", receivedAmt)
                    comment = comment.replace(formattedAmt, "").replace(receivedAmt.toString(), "").replace(receivedAmt.toInt().toString(), "")
                }
                if (paidAmt > 0) {
                    val formattedAmt = String.format(Locale.US, "%.2f", paidAmt)
                    comment = comment.replace(formattedAmt, "").replace(paidAmt.toString(), "").replace(paidAmt.toInt().toString(), "")
                }
                comment = comment.replace(Regex("(?i)\\b(received|paid|balance|total|date|notes)\\b"), "")
                    .replace(Regex("\\s+"), " ").trim()

                if (receivedAmt > 0) {
                    transactions.add(
                        TransactionEntity(
                            id = 0,
                            customerId = customerId,
                            transactionType = "I_RECEIVED",
                            amount = receivedAmt,
                            dateTimestamp = dateTimestamp,
                            comment = comment.ifBlank { "Received Entry" },
                            category = "General",
                            attachmentPath = "",
                            paymentMode = "None",
                            status = "COMPLETED"
                        )
                    )
                }

                if (paidAmt > 0) {
                    transactions.add(
                        TransactionEntity(
                            id = 0,
                            customerId = customerId,
                            transactionType = "I_PAID",
                            amount = paidAmt,
                            dateTimestamp = dateTimestamp,
                            comment = comment.ifBlank { "Paid Entry" },
                            category = "General",
                            attachmentPath = "",
                            paymentMode = "None",
                            status = "COMPLETED"
                        )
                    )
                }

                i = j - 1
            }
            i++
        }

        if (transactions.isEmpty()) {
            return PdfParseResult(
                isSuccess = false,
                customerInfo = extractedCustomer,
                errorMessage = "No valid dated transaction entries found in Transaction Report."
            )
        }

        return PdfParseResult(
            isSuccess = true,
            customerInfo = extractedCustomer,
            transactions = transactions
        )
    }

    private fun isSummaryOrHeaderRow(lowerLine: String): Boolean {
        return lowerLine.contains("total amount") ||
                lowerLine.contains("transactions count") ||
                lowerLine.contains("yet to receive") ||
                lowerLine.contains("report summary") ||
                lowerLine.contains("transaction report") ||
                lowerLine.contains("subtotal") ||
                lowerLine.contains("grand total") ||
                lowerLine.contains("net balance") ||
                lowerLine.contains("closing balance") ||
                lowerLine.contains("total received") ||
                lowerLine.contains("total paid") ||
                (lowerLine.contains("date") && lowerLine.contains("notes") && (lowerLine.contains("received") || lowerLine.contains("paid"))) ||
                (lowerLine.contains("date") && (lowerLine.contains("received") || lowerLine.contains("paid"))) ||
                lowerLine.startsWith("total ") ||
                lowerLine == "total" ||
                lowerLine.startsWith("grand total") ||
                lowerLine.startsWith("summary")
    }

    private fun parseDateToTimestamp(dateStr: String): Long {
        val cleanDate = dateStr.trim().replace(",", " ").replace(Regex("\\s+"), " ")
        val dateFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("dd MMM yyyy", Locale.US),
            SimpleDateFormat("dd-MMM-yyyy", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yy", Locale.US)
        )

        for (format in dateFormats) {
            try {
                format.isLenient = true
                val date = format.parse(cleanDate)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}

/**
 * Data container representing an isolated scope for a single PDF file import task.
 */
data class SinglePdfImportScope(
    val sourceIdentifier: String,
    val pdfText: String,
    val formatType: PdfFormatType,
    val parseResult: PdfParseResult
) {
    /**
     * Converts the parsed scope data into structured JSON for easy app readability.
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("sourceIdentifier", sourceIdentifier)
        json.put("formatType", formatType.name)
        json.put("isSuccess", parseResult.isSuccess)
        json.put("errorMessage", parseResult.errorMessage ?: "")

        val customerInfo = parseResult.customerInfo
        if (customerInfo != null) {
            val custObj = JSONObject().apply {
                put("name", customerInfo.name)
                put("mobileNumber", customerInfo.mobileNumber)
                put("address", customerInfo.address)
                put("email", customerInfo.email)
                put("notes", customerInfo.notes)
            }
            json.put("customer", custObj)
        }

        val txArray = JSONArray()
        for (tx in parseResult.transactions) {
            val txObj = JSONObject().apply {
                put("transactionType", tx.transactionType)
                put("amount", tx.amount)
                put("dateTimestamp", tx.dateTimestamp)
                put("comment", tx.comment)
                put("category", tx.category)
                put("paymentMode", tx.paymentMode)
            }
            txArray.put(txObj)
        }
        json.put("transactions", txArray)

        return json.toString(2)
    }
}

/**
 * Manager to route PDF text to appropriate parser implementation in strict file-level isolation.
 */
object PdfImportManager {

    fun createFreshParser(pdfText: String): PdfParser? {
        val okCreditParser = OkCreditPdfParser()
        if (okCreditParser.canParse(pdfText)) return okCreditParser

        val reportParser = TransactionReportPdfParser()
        if (reportParser.canParse(pdfText)) return reportParser

        return null
    }

    fun getParser(pdfText: String): PdfParser? {
        return createFreshParser(pdfText)
    }

    /**
     * Parses PDF text using a fresh, isolated parser instance for the document.
     */
    fun parse(pdfText: String, customerId: Long = 0L): PdfParseResult {
        val parser = createFreshParser(pdfText)
            ?: return PdfParseResult(isSuccess = false, errorMessage = "Unsupported PDF format.")
        return parser.parse(pdfText, customerId)
    }

    /**
     * Creates an isolated SinglePdfImportScope container ensuring zero state leakage across batch imports.
     */
    fun parseInIsolatedScope(sourceIdentifier: String, pdfText: String, customerId: Long = 0L): SinglePdfImportScope {
        val parser = createFreshParser(pdfText)
        if (parser == null) {
            return SinglePdfImportScope(
                sourceIdentifier = sourceIdentifier,
                pdfText = pdfText,
                formatType = PdfFormatType.UNSUPPORTED,
                parseResult = PdfParseResult(isSuccess = false, errorMessage = "Unsupported PDF format.")
            )
        }

        val formatType = when (parser) {
            is OkCreditPdfParser -> PdfFormatType.OK_CREDIT_STATEMENT
            is TransactionReportPdfParser -> PdfFormatType.TRANSACTION_REPORT
            else -> PdfFormatType.UNSUPPORTED
        }

        val result = parser.parse(pdfText, customerId)
        return SinglePdfImportScope(
            sourceIdentifier = sourceIdentifier,
            pdfText = pdfText,
            formatType = formatType,
            parseResult = result
        )
    }
}
