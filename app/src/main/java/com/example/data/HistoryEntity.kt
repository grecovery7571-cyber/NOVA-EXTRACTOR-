package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "extraction_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileSize: String,
    val fileType: String, // "APK", "IPA"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "SUCCESS", "FAILED"
    val extractedClassesCount: Int = 0,
    val extractedScriptsCount: Int = 0,
    val errorMessage: String? = null,
    val outputPath: String = ""
)
