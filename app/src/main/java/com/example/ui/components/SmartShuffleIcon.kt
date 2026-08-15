package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.SpotifyGreen

/**
 * Custom stylized Smart Shuffle Icon with crossing curved arrows and sparkles,
 * matching the neon-green design language in the reference image.
 */
@Composable
fun SmartShuffleIcon(
    modifier: Modifier = Modifier,
    tint: Color = SpotifyGreen
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun draw4PointStar(centerX: Float, centerY: Float, starSize: Float) {
            val path = Path().apply {
                val r = starSize / 2f
                moveTo(centerX, centerY - r)
                quadraticTo(centerX, centerY, centerX + r, centerY)
                quadraticTo(centerX, centerY, centerX, centerY + r)
                quadraticTo(centerX, centerY, centerX - r, centerY)
                quadraticTo(centerX, centerY, centerX, centerY - r)
                close()
            }
            drawPath(path = path, color = tint)
        }

        val strokeWidth = w * 0.12f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Lower curve: bottom-left to top-right
        val path1 = Path().apply {
            moveTo(w * 0.18f, h * 0.72f)
            cubicTo(w * 0.35f, h * 0.72f, w * 0.45f, h * 0.28f, w * 0.72f, h * 0.28f)
        }
        drawPath(path = path1, color = tint, style = stroke)

        // Arrow head 1 pointing top-right
        val arrowHead1 = Path().apply {
            moveTo(w * 0.58f, h * 0.14f)
            lineTo(w * 0.78f, h * 0.28f)
            lineTo(w * 0.58f, h * 0.42f)
        }
        drawPath(path = arrowHead1, color = tint, style = stroke)

        // Upper curve: top-left to bottom-right
        val path2 = Path().apply {
            moveTo(w * 0.18f, h * 0.28f)
            cubicTo(w * 0.35f, h * 0.28f, w * 0.45f, h * 0.72f, w * 0.72f, h * 0.72f)
        }
        drawPath(path = path2, color = tint, style = stroke)

        // Arrow head 2 pointing bottom-right
        val arrowHead2 = Path().apply {
            moveTo(w * 0.58f, h * 0.58f)
            lineTo(w * 0.78f, h * 0.72f)
            lineTo(w * 0.58f, h * 0.86f)
        }
        drawPath(path = arrowHead2, color = tint, style = stroke)

        // Prominent 4-point sparkle star on upper curve (top-left loop)
        draw4PointStar(centerX = w * 0.28f, centerY = h * 0.28f, starSize = w * 0.34f)

        // Surrounding small sparkle stars as in reference image
        draw4PointStar(centerX = w * 0.10f, centerY = h * 0.12f, starSize = w * 0.11f)
        draw4PointStar(centerX = w * 0.90f, centerY = h * 0.15f, starSize = w * 0.11f)
        draw4PointStar(centerX = w * 0.92f, centerY = h * 0.66f, starSize = w * 0.09f)
        draw4PointStar(centerX = w * 0.08f, centerY = h * 0.82f, starSize = w * 0.09f)
        draw4PointStar(centerX = w * 0.45f, centerY = h * 0.93f, starSize = w * 0.08f)
    }
}
