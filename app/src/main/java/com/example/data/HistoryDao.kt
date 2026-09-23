package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<TranslationHistory>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    suspend fun getAllHistorySync(): List<TranslationHistory>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(history: TranslationHistory)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
