package fr.manu.traducteur.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Lecture à voix haute avec le moteur de synthèse vocale du téléphone. */
class Speaker(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    /** Renvoie un message d'erreur, ou null si la lecture a démarré. */
    fun speak(text: String, lang: String): String? {
        if (!ready) return "La synthèse vocale n'est pas encore prête, réessaie dans un instant."
        val locale = if (lang == "it") Locale.ITALY else Locale.FRANCE
        when (tts.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED ->
                return "Voix ${if (lang == "it") "italienne" else "française"} absente : " +
                    "installe-la dans Paramètres › Synthèse vocale."
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "trad")
        return null
    }

    fun stop() = tts.stop()

    fun shutdown() = tts.shutdown()
}
