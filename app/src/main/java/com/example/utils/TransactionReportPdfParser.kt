package com.example.utils

import com.example.domain.pdf.TransactionReportPdfParser as DomainTransactionReportPdfParser

object TransactionReportPdfParser {
    private val delegate = DomainTransactionReportPdfParser()

    fun canParse(pdfText: String): Boolean = delegate.canParse(pdfText)

    fun parse(pdfText: String, customerId: Long = 0L): PdfParseResult = delegate.parse(pdfText, customerId)
}

