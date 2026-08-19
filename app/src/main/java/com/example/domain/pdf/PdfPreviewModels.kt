package com.example.domain.pdf

import android.net.Uri
import com.example.utils.PdfFormatType
import java.util.UUID

data class PreviewTransaction(
    val id: String = UUID.randomUUID().toString(),
    val originalIndex: Int = 0,
    var dateTimestamp: Long = 0L,
    var dateString: String = "",
    var transactionType: String = "I_PAID", // "I_PAID" or "I_RECEIVED"
    var amountText: String = "0.00",        // User editable text field
    var comment: String = "",
    var attachmentPath: String = "",
    var isError: Boolean = false,
    var errorMessage: String? = null
)

data class PdfPreviewItem(
    val uri: Uri,
    val fileName: String,
    val formatType: PdfFormatType,
    var customerName: String,     // User editable text field
    var mobileNumber: String,     // User editable text field
    var address: String = "",
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val transactions: MutableList<PreviewTransaction> = mutableListOf()
)
