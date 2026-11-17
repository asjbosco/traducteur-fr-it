package fr.manu.traducteur.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Dictée « appuyer pour parler » : le micro reste actif tant que le bouton est maintenu.
 * Le moteur Android s'arrête de lui-même après un silence : on le relance aussitôt
 * et on met bout à bout les morceaux reconnus, jusqu'à ce que le bouton soit relâché.
 */
class PushToTalk(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } else {
            null
        }

    private var holding = false
    private var active = false
    private var finished = true
    private var langTag = "fr-FR"
    private val committed = StringBuilder()
    private var partial = ""

    val available: Boolean get() = recognizer != null

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.best()?.let {
                    partial = it
                    onPartial(currentText())
                }
            }

            override fun onResults(results: Bundle?) {
                active = false
                results.best()?.let { append(it) }
                partial = ""
                onPartial(currentText())
                if (holding) listenSoon() else finish()
            }

            override fun onError(error: Int) {
                active = false
                if (!holding) {
                    finish()
                    return
                }
                when (error) {
                    // Silence ou rien compris : on continue d'écouter tant que le bouton est appuyé.
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> {
                        if (partial.isNotBlank()) append(partial)
                        partial = ""
                        listenSoon()
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Autorise l'accès au micro pour dicter.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        fail("Pas de connexion : installe la langue hors ligne dans les réglages de dictée Google.")
                    else -> fail("La dictée s'est arrêtée (erreur $error).")
                }
            }
        })
    }

    fun start(lang: String) {
        val r = recognizer ?: run {
            onError("Aucune reconnaissance vocale sur ce téléphone (installe l'application Google).")
            return
        }
        handler.removeCallbacksAndMessages(null)
        if (active) r.cancel()
        langTag = lang
        committed.clear()
        partial = ""
        holding = true
        finished = false
        listen()
    }

    fun stop() {
        if (!holding) return
        holding = false
        handler.removeCallbacksAndMessages(null)
        if (active) {
            // Le moteur renvoie le dernier morceau (onResults ou onError), puis on termine.
            recognizer?.stopListening()
            // Sécurité : si le moteur ne répond pas, on termine quand même.
            handler.postDelayed({ if (!finished) { recognizer?.cancel(); active = false; finish() } }, 2500)
        } else {
            finish()
        }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
        active = true
        recognizer?.startListening(intent)
    }

    private fun listenSoon() = handler.postDelayed({ if (holding) listen() }, 150)

    private fun append(text: String) {
        if (text.isBlank()) return
        if (committed.isNotEmpty()) committed.append(' ')
        committed.append(text.trim())
    }

    private fun currentText(): String =
        if (partial.isBlank()) committed.toString() else (committed.toString() + " " + partial).trim()

    private fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        if (partial.isNotBlank()) append(partial)
        partial = ""
        onFinal(committed.toString().trim())
    }

    private fun fail(message: String) {
        holding = false
        finished = true
        handler.removeCallbacksAndMessages(null)
        onError(message)
    }

    private fun Bundle?.best(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}
