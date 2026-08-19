package com.example.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.TransactionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfReportExporter {

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("None")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }

    private fun loadScaledBitmap(context: Context, pathOrUri: String, maxDim: Int = 800): android.graphics.Bitmap? {
        if (pathOrUri.isBlank()) return null
        return try {
            val bytes = ImageUtils.readImageBytes(context, pathOrUri) ?: return null
            if (bytes.isEmpty()) return null

            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sampleSize = 1
            while (origW / sampleSize > maxDim || origH / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null

            val scale = Math.min(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
            if (scale < 1.0f) {
                val targetW = (bmp.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bmp.height * scale).toInt().coerceAtLeast(1)
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                if (scaled != bmp) bmp.recycle()
                scaled
            } else {
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateTransactionCardHeight(
        tx: TransactionEntity,
        context: Context,
        commentPaint: Paint,
        printableWidth: Float
    ): Float {
        val commentText = if (tx.comment.isNotBlank()) tx.comment else "None"
        val wrappedCommentLines = wrapText(commentText, commentPaint, printableWidth - 120f)
        val commentHeight = wrappedCommentLines.size * 14f
        val hasAttachments = !tx.attachmentPath.isNullOrBlank()
        val extraImgHeight = if (hasAttachments) 130f else 0f
        return 65f + commentHeight + extraImgHeight
    }

    private fun calculateTotalPages(
        context: Context,
        customerName: String,
        mobileNumber: String?,
        transactions: List<TransactionEntity>
    ): Int {
        var pageCount = 1
        var currentY = 215f // Header height on Page 1

        val commentPaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.DEFAULT
        }

        val footerHeight = 150f

        for (tx in transactions) {
            val cardHeight = calculateTransactionCardHeight(tx, context, commentPaint, 523f) + 10f
            if (currentY + cardHeight > 780f) {
                pageCount++
                currentY = 70f + cardHeight
            } else {
                currentY += cardHeight
            }
        }

        if (currentY + footerHeight > 780f) {
            pageCount++
        }

        return pageCount
    }

    fun generateCustomerLedgerPdf(
        context: Context,
        customerName: String,
        mobileNumber: String?,
        transactions: List<TransactionEntity>
    ): File {
        val pdfDocument = PdfDocument()

        val totalPaid = transactions.filter { it.transactionType == "I_PAID" }.sumOf { it.amount }
        val totalReceived = transactions.filter { it.transactionType == "I_RECEIVED" }.sumOf { it.amount }
        val netBalance = totalPaid - totalReceived

        val reportDateStr = SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val isDeductionCustomer = customerName.contains("Deduction", ignoreCase = true)

        val totalPages = calculateTotalPages(context, customerName, mobileNumber, transactions)

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val labelBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85) // Slate 700
            textSize = 10f
            typeface = Typeface.DEFAULT
        }

        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 163, 74) // Green 600
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 38, 38) // Red 600
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var currentY = 40f

        fun drawPageHeader(isFirstPage: Boolean) {
            if (isFirstPage) {
                // Main Title - Centered Bold
                canvas.drawText("Daily Expense & Ledger – Customer Report", 595f / 2f, currentY, titlePaint)
                currentY += 20f

                // Header Divider
                paint.color = Color.rgb(203, 213, 225) // Slate 300
                paint.strokeWidth = 1.5f
                canvas.drawLine(36f, currentY, 559f, currentY, paint)
                currentY += 15f

                // Customer Info Card Box
                val cardRect = RectF(36f, currentY, 559f, currentY + 115f)
                paint.color = Color.rgb(248, 250, 252) // Slate 50
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(cardRect, 8f, 8f, paint)

                paint.color = Color.rgb(226, 232, 240) // Slate 200
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(cardRect, 8f, 8f, paint)

                // Fill Customer Info
                var infoY = currentY + 20f

                // Column 1
                canvas.drawText("Customer Name:", 50f, infoY, labelBoldPaint)
                canvas.drawText(customerName, 145f, infoY, labelBoldPaint)

                infoY += 18f
                canvas.drawText("Mobile Number:", 50f, infoY, labelBoldPaint)
                canvas.drawText(if (mobileNumber.isNullOrBlank()) "N/A" else mobileNumber, 145f, infoY, valuePaint)

                infoY += 18f
                canvas.drawText("Report Generated:", 50f, infoY, labelBoldPaint)
                canvas.drawText(reportDateStr, 145f, infoY, valuePaint)

                // Column 2 (Financial Totals)
                infoY = currentY + 20f
                canvas.drawText("Total Received:", 320f, infoY, labelBoldPaint)
                canvas.drawText(String.format("₹ %,.2f", totalReceived), 420f, infoY, greenPaint)

                infoY += 18f
                canvas.drawText("Total Paid:", 320f, infoY, labelBoldPaint)
                canvas.drawText(String.format("₹ %,.2f", totalPaid), 420f, infoY, redPaint)

                infoY += 18f
                val balanceLabel = if (isDeductionCustomer) "Total Expense:" else "Current Balance:"
                canvas.drawText(balanceLabel, 320f, infoY, labelBoldPaint)

                val balanceValStr = String.format("₹ %,.2f", Math.abs(netBalance))
                val balancePaint = when {
                    isDeductionCustomer -> redPaint
                    netBalance > 0.01 -> greenPaint
                    netBalance < -0.01 -> redPaint
                    else -> valuePaint
                }
                canvas.drawText(balanceValStr, 420f, infoY, balancePaint)

                if (!isDeductionCustomer) {
                    infoY += 16f
                    val statusStr = when {
                        netBalance > 0.01 -> "(You'll Get)"
                        netBalance < -0.01 -> "(You'll Give)"
                        else -> "(Settled)"
                    }
                    canvas.drawText(statusStr, 420f, infoY, valuePaint)
                }

                currentY += 128f

                // Horizontal Divider separating header from transaction list
                paint.color = Color.rgb(203, 213, 225)
                paint.strokeWidth = 1f
                canvas.drawLine(36f, currentY, 559f, currentY, paint)
                currentY += 15f
            } else {
                val contTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(15, 23, 42)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("Daily Expense & Ledger – Customer Report ($customerName)", 36f, 40f, contTitlePaint)
                paint.color = Color.rgb(203, 213, 225)
                paint.strokeWidth = 1f
                canvas.drawLine(36f, 48f, 559f, 48f, paint)
                currentY = 60f
            }
        }

        fun drawPageFooter() {
            paint.color = Color.rgb(226, 232, 240)
            paint.strokeWidth = 1f
            canvas.drawLine(36f, 810f, 559f, 810f, paint)

            val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 9f
                typeface = Typeface.DEFAULT
            }
            canvas.drawText("Daily Expense & Ledger", 36f, 824f, footerTextPaint)

            val pageNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 9f
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("Page $pageNumber of $totalPages", 559f, 824f, pageNumPaint)
        }

        drawPageHeader(true)

        val dateSdf = SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault())

        val txCommentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
            typeface = Typeface.DEFAULT
        }

        for (tx in transactions) {
            val formattedDate = dateSdf.format(Date(tx.dateTimestamp))
            val isReceived = tx.transactionType == "I_RECEIVED"
            val typeText = if (isReceived) "I Received" else "I Paid"

            val attachmentPaths = tx.attachmentPath?.split(Regex("[|,]"))?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val attachmentText = if (attachmentPaths.isNotEmpty()) {
                if (attachmentPaths.size == 1) "1 Photo" else "${attachmentPaths.size} Photos"
            } else "None"

            val commentText = if (tx.comment.isNotBlank()) tx.comment else "None"
            val wrappedCommentLines = wrapText(commentText, txCommentPaint, 380f)

            val cardContentHeight = calculateTransactionCardHeight(tx, context, txCommentPaint, 523f)
            val cardTotalHeight = cardContentHeight + 10f

            if (currentY + cardTotalHeight > 780f) {
                drawPageFooter()
                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                drawPageHeader(false)
            }

            val txBoxRect = RectF(36f, currentY, 559f, currentY + cardContentHeight)

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(255, 255, 255)
            canvas.drawRoundRect(txBoxRect, 6f, 6f, paint)

            val accentColor = if (isReceived) Color.rgb(22, 163, 74) else Color.rgb(220, 38, 38)
            paint.color = accentColor
            val accentRect = RectF(36f, currentY, 42f, currentY + cardContentHeight)
            canvas.drawRoundRect(accentRect, 6f, 6f, paint)

            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(226, 232, 240)
            paint.strokeWidth = 1f
            canvas.drawRoundRect(txBoxRect, 6f, 6f, paint)

            var itemY = currentY + 16f
            val startX = 52f

            // Date & Time
            canvas.drawText("Date & Time:", startX, itemY, labelBoldPaint)
            canvas.drawText(formattedDate, startX + 75f, itemY, valuePaint)

            // Type
            canvas.drawText("Type:", 350f, itemY, labelBoldPaint)
            val typePaint = if (isReceived) greenPaint else redPaint
            canvas.drawText(typeText, 390f, itemY, typePaint)

            itemY += 16f

            // Amount & Payment Mode
            canvas.drawText("Amount:", startX, itemY, labelBoldPaint)
            canvas.drawText(String.format("₹ %,.2f", tx.amount), startX + 75f, itemY, if (isReceived) greenPaint else redPaint)

            canvas.drawText("Payment Mode:", 350f, itemY, labelBoldPaint)
            canvas.drawText(tx.paymentMode, 440f, itemY, valuePaint)

            itemY += 16f

            // Attachments
            canvas.drawText("Attachments:", startX, itemY, labelBoldPaint)
            canvas.drawText(attachmentText, startX + 75f, itemY, valuePaint)

            itemY += 16f

            // Comment
            canvas.drawText("Comment:", startX, itemY, labelBoldPaint)
            val commentStartX = startX + 75f
            for (line in wrappedCommentLines) {
                canvas.drawText(line, commentStartX, itemY, txCommentPaint)
                itemY += 14f
            }

            // Draw image thumbnails if present
            if (attachmentPaths.isNotEmpty()) {
                itemY += 4f
                var imgX = startX + 75f
                for (path in attachmentPaths.take(4)) {
                    val bmp = loadScaledBitmap(context, path, 800)
                    if (bmp != null) {
                        val imgRect = RectF(imgX, itemY, imgX + 110f, itemY + 110f)
                        paint.style = Paint.Style.FILL
                        canvas.drawBitmap(bmp, null, imgRect, paint)

                        paint.style = Paint.Style.STROKE
                        paint.color = Color.rgb(203, 213, 225)
                        paint.strokeWidth = 1f
                        canvas.drawRoundRect(imgRect, 6f, 6f, paint)

                        imgX += 118f
                    }
                }
            }

            currentY += cardTotalHeight
        }

        val footerBlockHeight = 140f
        if (currentY + footerBlockHeight > 780f) {
            drawPageFooter()
            pdfDocument.finishPage(page)

            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas

            drawPageHeader(false)
        }

        currentY += 10f

        // Draw Summary Footer Block
        val summaryRect = RectF(36f, currentY, 559f, currentY + 110f)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(241, 245, 249) // Slate 100
        canvas.drawRoundRect(summaryRect, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(203, 213, 225) // Slate 300
        paint.strokeWidth = 1f
        canvas.drawRoundRect(summaryRect, 8f, 8f, paint)

        var sumY = currentY + 20f

        canvas.drawText("Total Transactions:", 50f, sumY, labelBoldPaint)
        canvas.drawText("${transactions.size}", 160f, sumY, valuePaint)

        canvas.drawText("Total Received:", 320f, sumY, labelBoldPaint)
        canvas.drawText(String.format("₹ %,.2f", totalReceived), 420f, sumY, greenPaint)

        sumY += 18f

        canvas.drawText("Total Paid:", 320f, sumY, labelBoldPaint)
        canvas.drawText(String.format("₹ %,.2f", totalPaid), 420f, sumY, redPaint)

        val balanceLabel = if (isDeductionCustomer) "Total Expense:" else "Current Balance:"
        canvas.drawText(balanceLabel, 50f, sumY, labelBoldPaint)

        val balanceValStr = String.format("₹ %,.2f", Math.abs(netBalance))
        val balancePaint = when {
            isDeductionCustomer -> redPaint
            netBalance > 0.01 -> greenPaint
            netBalance < -0.01 -> redPaint
            else -> valuePaint
        }
        canvas.drawText(balanceValStr, 160f, sumY, balancePaint)

        sumY += 28f

        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        canvas.drawLine(50f, sumY, 545f, sumY, paint)

        sumY += 20f

        val thankYouPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Thank you for using Daily Expense & Ledger..!", 595f / 2f, sumY, thankYouPaint)

        drawPageFooter()

        pdfDocument.finishPage(page)

        val cleanName = customerName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val pdfFile = File(context.cacheDir, "Ledger_Report_${cleanName}.pdf")
        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return pdfFile
    }

    fun generateMonthlyReportPdf(
        context: Context,
        monthPeriodStr: String,
        monthExpensesTotal: Double,
        monthExpenses: List<com.example.data.MonthlyExpenseEntity>,
        totalYouWillReceive: Double,
        totalYouNeedToPay: Double,
        totalCustomersCount: Int
    ): File {
        val pdfDocument = PdfDocument()
        val reportDateStr = SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault()).format(Date())

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val labelBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
            typeface = Typeface.DEFAULT
        }

        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 163, 74)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 38, 38)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var currentY = 40f

        canvas.drawText("Daily Expense & Ledger – Monthly Statement", 595f / 2f, currentY, titlePaint)
        currentY += 20f

        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1.5f
        canvas.drawLine(36f, currentY, 559f, currentY, paint)
        currentY += 15f

        // Info Card Box
        val cardRect = RectF(36f, currentY, 559f, currentY + 90f)
        paint.color = Color.rgb(248, 250, 252)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(cardRect, 8f, 8f, paint)

        paint.color = Color.rgb(226, 232, 240)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(cardRect, 8f, 8f, paint)

        var infoY = currentY + 20f
        canvas.drawText("Month Period:", 50f, infoY, labelBoldPaint)
        canvas.drawText(monthPeriodStr, 145f, infoY, labelBoldPaint)

        canvas.drawText("Total Monthly Expenses:", 310f, infoY, labelBoldPaint)
        canvas.drawText(String.format("₹ %,.2f", monthExpensesTotal), 440f, infoY, redPaint)

        infoY += 20f
        canvas.drawText("Report Generated:", 50f, infoY, labelBoldPaint)
        canvas.drawText(reportDateStr, 145f, infoY, valuePaint)

        canvas.drawText("Total Expense Items:", 310f, infoY, labelBoldPaint)
        canvas.drawText("${monthExpenses.size}", 440f, infoY, valuePaint)

        currentY += 105f

        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        canvas.drawLine(36f, currentY, 559f, currentY, paint)
        currentY += 20f

        // Expenses breakdown section
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("EXPENSE ITEMS BREAKDOWN", 36f, currentY, sectionTitlePaint)
        currentY += 15f

        val dateSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        if (monthExpenses.isEmpty()) {
            canvas.drawText("No specific store expenses recorded for this month.", 50f, currentY, valuePaint)
            currentY += 20f
        } else {
            for ((idx, exp) in monthExpenses.withIndex()) {
                if (currentY + 50f > 780f) {
                    paint.color = Color.rgb(226, 232, 240)
                    paint.strokeWidth = 1f
                    canvas.drawLine(36f, 810f, 559f, 810f, paint)
                    canvas.drawText("Daily Expense & Ledger", 36f, 824f, valuePaint)

                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = 50f
                }

                val itemBox = RectF(36f, currentY, 559f, currentY + 40f)
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(255, 255, 255)
                canvas.drawRoundRect(itemBox, 4f, 4f, paint)

                paint.style = Paint.Style.STROKE
                paint.color = Color.rgb(226, 232, 240)
                paint.strokeWidth = 1f
                canvas.drawRoundRect(itemBox, 4f, 4f, paint)

                var lineY = currentY + 16f
                canvas.drawText("${idx + 1}. ${exp.title}", 48f, lineY, labelBoldPaint)
                canvas.drawText("Category: ${exp.category}", 280f, lineY, valuePaint)
                canvas.drawText(String.format("₹ %,.2f", exp.amount), 450f, lineY, redPaint)

                lineY += 14f
                canvas.drawText("Date: ${dateSdf.format(Date(exp.dateTimestamp))}", 48f, lineY, valuePaint)
                if (exp.notes.isNotBlank()) {
                    canvas.drawText("Notes: ${exp.notes}", 280f, lineY, valuePaint)
                }

                currentY += 46f
            }
        }

        currentY += 15f

        // Ledger Summary Section
        if (currentY + 130f > 780f) {
            paint.color = Color.rgb(226, 232, 240)
            paint.strokeWidth = 1f
            canvas.drawLine(36f, 810f, 559f, 810f, paint)

            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            currentY = 50f
        }

        canvas.drawText("OVERALL LEDGER POSITION", 36f, currentY, sectionTitlePaint)
        currentY += 15f

        val summaryRect = RectF(36f, currentY, 559f, currentY + 95f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRoundRect(summaryRect, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        canvas.drawRoundRect(summaryRect, 8f, 8f, paint)

        var sumY = currentY + 20f
        canvas.drawText("Overall Outstanding (You Get):", 50f, sumY, labelBoldPaint)
        canvas.drawText(String.format("₹ %,.2f", totalYouWillReceive), 230f, sumY, greenPaint)

        canvas.drawText("Overall Payable (You Give):", 320f, sumY, labelBoldPaint)
        canvas.drawText(String.format("₹ %,.2f", totalYouNeedToPay), 470f, sumY, redPaint)

        sumY += 20f
        canvas.drawText("Total Active Customers:", 50f, sumY, labelBoldPaint)
        canvas.drawText("$totalCustomersCount Customers", 230f, sumY, valuePaint)

        sumY += 25f
        paint.color = Color.rgb(203, 213, 225)
        paint.strokeWidth = 1f
        canvas.drawLine(50f, sumY, 545f, sumY, paint)

        sumY += 18f
        val thankYouPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Thank you for using Daily Expense & Ledger..!", 595f / 2f, sumY, thankYouPaint)

        // Footer
        paint.color = Color.rgb(226, 232, 240)
        paint.strokeWidth = 1f
        canvas.drawLine(36f, 810f, 559f, 810f, paint)
        canvas.drawText("Daily Expense & Ledger", 36f, 824f, valuePaint)

        pdfDocument.finishPage(page)

        val cleanPeriod = monthPeriodStr.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val pdfFile = File(context.cacheDir, "Monthly_Report_${cleanPeriod}.pdf")
        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return pdfFile
    }
}
