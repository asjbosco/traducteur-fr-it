package fr.manu.traducteur.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Lit le texte d'une image, entièrement sur le téléphone (français et italien = alphabet latin). */
class TextScanner {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        // On recolle les lignes d'un même bloc pour garder des phrases entières.
        return result.textBlocks.joinToString("\n\n") { block ->
            block.lines.joinToString(" ") { it.text }
        }.trim()
    }
}
