package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("customerId"),
        Index("dateTimestamp"),
        Index("transactionType"),
        Index("paymentMode")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long,
    val transactionType: String, // "I_PAID" (Gave) or "I_RECEIVED" (Got)
    val amount: Double,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val comment: String = "",
    val category: String = "General",
    val attachmentPath: String? = null,
    val paymentMode: String = "CASH", // CASH, UPI, BANK, CARD
    val status: String = "COMPLETED" // COMPLETED, PENDING
)
