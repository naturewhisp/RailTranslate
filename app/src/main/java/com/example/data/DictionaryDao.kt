package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary ORDER BY term ASC")
    fun getAllTerms(): Flow<List<DictionaryTerm>>

    @Query("SELECT * FROM dictionary ORDER BY term ASC")
    suspend fun getAllTermsSync(): List<DictionaryTerm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerm(term: DictionaryTerm)

    @Query("DELETE FROM dictionary WHERE id = :id")
    suspend fun deleteTermById(id: Int)
    
    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun getCount(): Int
}
