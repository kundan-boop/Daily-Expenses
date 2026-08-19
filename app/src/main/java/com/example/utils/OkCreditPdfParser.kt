package com.example.utils

import com.example.domain.pdf.OkCreditPdfParser as DomainOkCreditPdfParser

object OkCreditPdfParser {
    private val delegate = DomainOkCreditPdfParser()

    fun canParse(pdfText: String): Boolean = delegate.canParse(pdfText)

    fun parse(pdfText: String, customerId: Long = 0L): PdfParseResult = delegate.parse(pdfText, customerId)
}

