package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [
        Index("name"),
        Index("mobileNumber"),
        Index("lastUpdatedTimestamp"),
        Index("collectToday"),
        Index("collectDueDate")
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val mobileNumber: String,
    val address: String = "",
    val email: String = "",
    val notes: String = "",
    val profilePhotoUri: String? = null,
    val collectToday: Boolean = false,
    val collectDueDate: Long? = null,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
