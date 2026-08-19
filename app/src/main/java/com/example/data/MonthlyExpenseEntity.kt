package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monthly_expenses",
    indices = [
        Index("monthYear"),
        Index("dateTimestamp"),
        Index("category")
    ]
)
data class MonthlyExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val monthYear: String, // e.g. "2026-07"
    val title: String,
    val category: String,
    val amount: Double,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val notes: String = "",
    val attachmentPath: String? = null
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isCustom: Boolean = false,
    val iconName: String = "Category",
    val colorHex: String = "#2E7D32"
)
