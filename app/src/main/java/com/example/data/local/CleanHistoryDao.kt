package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanHistoryDao {
    @Query("SELECT * FROM clean_history ORDER BY timestampMillis DESC")
    fun getAllHistory(): Flow<List<CleanHistoryEntity>>

    @Query("SELECT COALESCE(SUM(bytesFreed), 0) FROM clean_history")
    fun getTotalBytesFreed(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: CleanHistoryEntity)

    @Query("DELETE FROM clean_history")
    suspend fun clearAll()
}
