package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary")
data class DictionaryTerm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val term: String,
    val translation: String,
    val category: String = "Ferroviario"
)
