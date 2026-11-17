package fr.manu.traducteur.data

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

enum class Direction(val source: String, val target: String, val label: String) {
    FR_IT(TranslateLanguage.FRENCH, TranslateLanguage.ITALIAN, "Français → Italien"),
    IT_FR(TranslateLanguage.ITALIAN, TranslateLanguage.FRENCH, "Italien → Français");

    fun reversed() = if (this == FR_IT) IT_FR else FR_IT
}

/**
 * Traduction entièrement sur l'appareil avec ML Kit.
 * Les modèles (environ 30 Mo par langue) sont téléchargés une seule fois,
 * ensuite tout fonctionne sans connexion.
 */
class OfflineTranslator {
    private val translators = mutableMapOf<Direction, Translator>()
    private val modelManager = RemoteModelManager.getInstance()

    private fun translator(d: Direction): Translator =
        translators.getOrPut(d) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(d.source)
                    .setTargetLanguage(d.target)
                    .build()
            )
        }

    suspend fun modelsReady(): Boolean {
        val downloaded = modelManager
            .getDownloadedModels(TranslateRemoteModel::class.java).await()
            .map { it.language }
            .toSet()
        return TranslateLanguage.FRENCH in downloaded && TranslateLanguage.ITALIAN in downloaded
    }

    /** Télécharge les modèles. [wifiOnly] évite d'utiliser les données mobiles. */
    suspend fun downloadModels(wifiOnly: Boolean) {
        val conditions = DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build()
        Direction.entries.forEach { translator(it).downloadModelIfNeeded(conditions).await() }
    }

    suspend fun translate(text: String, d: Direction): String =
        translator(d).translate(text).await()

    fun close() = translators.values.forEach { it.close() }
}
