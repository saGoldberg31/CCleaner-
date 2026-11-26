package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clean_history")
data class CleanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    val bytesFreed: Long,
    val filesRemoved: Int,
    val categoriesSummary: String,
    val durationMillis: Long
)
