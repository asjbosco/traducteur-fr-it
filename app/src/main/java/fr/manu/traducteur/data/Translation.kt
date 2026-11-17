package fr.manu.traducteur.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class Translation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String,
    val translatedText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
)
