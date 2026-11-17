package fr.manu.traducteur.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Lit le texte d'une image, entièrement sur le téléphone (français et italien = alphabet latin). */
class TextScanner {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(bitmap: Bitmap): String {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        // On recolle les lignes d'un même bloc pour garder des phrases entières.
        return result.textBlocks.joinToString("\n\n") { block ->
            block.lines.joinToString(" ") { it.text }
        }.trim()
    }

    companion object {
        private const val MAX_SIDE = 2560

        /** Charge une image dans le bon sens (orientation de l'appareil photo), réduite si très grande. */
        suspend fun load(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val scale = max(1f, max(w, h) / MAX_SIDE.toFloat())
                    if (scale > 1f) decoder.setTargetSize((w / scale).roundToInt(), (h / scale).roundToInt())
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
                val bmp = cr.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("image illisible")
                val degrees = cr.openInputStream(uri).use { input ->
                    if (input == null) 0 else ExifInterface(input).rotationDegrees
                }
                if (degrees == 0) bmp else rotate(bmp, degrees.toFloat())
            }
        }

        fun rotate(src: Bitmap, degrees: Float): Bitmap =
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees) }, true)

        /** Découpe la zone choisie ; [left]..[bottom] sont des fractions (0 à 1) de l'image. */
        fun crop(src: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
            val x = (left * src.width).roundToInt().coerceIn(0, src.width - 1)
            val y = (top * src.height).roundToInt().coerceIn(0, src.height - 1)
            val w = ((right - left) * src.width).roundToInt().coerceIn(1, src.width - x)
            val h = ((bottom - top) * src.height).roundToInt().coerceIn(1, src.height - y)
            return Bitmap.createBitmap(src, x, y, w, h)
        }
    }
}
