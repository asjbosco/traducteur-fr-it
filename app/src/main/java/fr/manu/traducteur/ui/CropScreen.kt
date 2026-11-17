package fr.manu.traducteur.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/** Zone de rognage, en fractions (0 à 1) de la largeur et de la hauteur de l'image. */
private data class CropBox(val l: Float, val t: Float, val r: Float, val b: Float)

private enum class Grab { NONE, MOVE, TL, TR, BL, BR, L, R, T, B }

private const val MIN_SIZE = 0.05f

/**
 * Écran de rognage : on ajuste le cadre blanc autour du texte à traduire
 * (coins et bords à faire glisser, ou tout le cadre), on peut tourner l'image, puis on valide.
 */
@Composable
fun CropScreen(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onRotate: () -> Unit,
    onConfirm: (Float, Float, Float, Float) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var box by remember(bitmap) { mutableStateOf(CropBox(0.1f, 0.1f, 0.9f, 0.9f)) }
    val grabRadius = with(LocalDensity.current) { 36.dp.toPx() }

    Column(
        Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(),
    ) {
        Text(
            "Ajuste le cadre autour du texte à traduire",
            color = Color.White,
            modifier = Modifier.padding(16.dp),
        )

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            val cw = constraints.maxWidth.toFloat()
            val ch = constraints.maxHeight.toFloat()
            val scale = min(cw / bitmap.width, ch / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val ox = (cw - dw) / 2f
            val oy = (ch - dh) / 2f

            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())

            var grab by remember { mutableStateOf(Grab.NONE) }
            Canvas(
                Modifier.fillMaxSize().pointerInput(bitmap, cw, ch) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val l = ox + box.l * dw
                            val r = ox + box.r * dw
                            val t = oy + box.t * dh
                            val b = oy + box.b * dh
                            fun near(x: Float, y: Float) = abs(p.x - x) < grabRadius && abs(p.y - y) < grabRadius
                            val insideY = p.y in t..b
                            val insideX = p.x in l..r
                            grab = when {
                                near(l, t) -> Grab.TL
                                near(r, t) -> Grab.TR
                                near(l, b) -> Grab.BL
                                near(r, b) -> Grab.BR
                                abs(p.x - l) < grabRadius && insideY -> Grab.L
                                abs(p.x - r) < grabRadius && insideY -> Grab.R
                                abs(p.y - t) < grabRadius && insideX -> Grab.T
                                abs(p.y - b) < grabRadius && insideX -> Grab.B
                                insideX && insideY -> Grab.MOVE
                                else -> Grab.NONE
                            }
                        },
                        onDragEnd = { grab = Grab.NONE },
                        onDragCancel = { grab = Grab.NONE },
                        onDrag = { change, d ->
                            change.consume()
                            val dx = d.x / dw
                            val dy = d.y / dh
                            box = moveBox(box, grab, dx, dy)
                        },
                    )
                },
            ) {
                val l = ox + box.l * dw
                val r = ox + box.r * dw
                val t = oy + box.t * dh
                val b = oy + box.b * dh
                val dim = Color.Black.copy(alpha = 0.6f)
                // Assombrit ce qui ne sera pas traduit
                drawRect(dim, Offset(ox, oy), Size(dw, t - oy))
                drawRect(dim, Offset(ox, b), Size(dw, oy + dh - b))
                drawRect(dim, Offset(ox, t), Size(l - ox, b - t))
                drawRect(dim, Offset(r, t), Size(ox + dw - r, b - t))
                // Cadre et poignées
                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                val hr = 9.dp.toPx()
                listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
                    drawCircle(Color.White, hr, it)
                }
                listOf(Offset((l + r) / 2, t), Offset((l + r) / 2, b), Offset(l, (t + b) / 2), Offset(r, (t + b) / 2))
                    .forEach { drawCircle(Color.White, hr * 0.7f, it) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Annuler", color = Color.White) }
            OutlinedButton(onClick = onRotate) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, tint = Color.White)
                Spacer(Modifier.size(6.dp))
                Text("Tourner", color = Color.White)
            }
            Button(onClick = { onConfirm(box.l, box.t, box.r, box.b) }) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Valider")
            }
        }
    }
}

private fun moveBox(c: CropBox, g: Grab, dx: Float, dy: Float): CropBox {
    var (l, t, r, b) = c
    when (g) {
        Grab.MOVE -> {
            val mx = dx.coerceIn(-l, 1f - r)
            val my = dy.coerceIn(-t, 1f - b)
            l += mx; r += mx; t += my; b += my
        }
        Grab.TL -> { l = (l + dx).coerceIn(0f, r - MIN_SIZE); t = (t + dy).coerceIn(0f, b - MIN_SIZE) }
        Grab.TR -> { r = (r + dx).coerceIn(l + MIN_SIZE, 1f); t = (t + dy).coerceIn(0f, b - MIN_SIZE) }
        Grab.BL -> { l = (l + dx).coerceIn(0f, r - MIN_SIZE); b = (b + dy).coerceIn(t + MIN_SIZE, 1f) }
        Grab.BR -> { r = (r + dx).coerceIn(l + MIN_SIZE, 1f); b = (b + dy).coerceIn(t + MIN_SIZE, 1f) }
        Grab.L -> l = (l + dx).coerceIn(0f, r - MIN_SIZE)
        Grab.R -> r = (r + dx).coerceIn(l + MIN_SIZE, 1f)
        Grab.T -> t = (t + dy).coerceIn(0f, b - MIN_SIZE)
        Grab.B -> b = (b + dy).coerceIn(t + MIN_SIZE, 1f)
        Grab.NONE -> {}
    }
    return CropBox(l, t, r, b)
}
