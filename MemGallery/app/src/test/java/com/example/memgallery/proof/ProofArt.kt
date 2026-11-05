package com.example.memgallery.proof

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

/** Generated artwork for screenshot tests: stands in for the user's photos so tests need no files or network. */
class ArtPainter(private val name: String) : Painter() {
    override val intrinsicSize: Size = when (name) {
        "wifi" -> Size(80f, 100f)
        "ticket" -> Size(84f, 100f)
        "sunset", "mountains" -> Size(100f, 112f)
        "soup", "latte", "plant" -> Size(100f, 100f)
        else -> Size(100f, 100f)
    }

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        when (name) {
            "sunset" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF3B2A6B), Color(0xFFB45A86), Color(0xFFF0A27A)), 0f, h * 0.7f))
                drawCircle(Color(0xFFFBE7B5), radius = w * 0.14f, center = Offset(w * 0.7f, h * 0.55f))
                hills(Color(0xFF7E3A66), h * 0.62f, 0.12f)
                hills(Color(0xFF4B2656), h * 0.74f, 0.08f)
                hills(Color(0xFF2E1A45), h * 0.86f, 0.05f)
            }
            "mountains" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF6FA8F0), Color(0xFFBFD9F7))))
                peak(Color(0xFF8FA8D4), w * 0.5f, h * 0.28f, w * 0.62f, h * 0.72f)
                peak(Color(0xFF5E7FB4), w * 0.25f, h * 0.45f, w * 0.5f, h * 0.78f)
                peak(Color(0xFF3F5F94), w * 0.8f, h * 0.5f, w * 0.55f, h * 0.8f)
                drawRect(Color(0xFF2E4872), topLeft = Offset(0f, h * 0.78f), size = Size(w, h * 0.22f))
                for (i in 0 until 7) tree(Color(0xFF1F3355), w * (0.08f + i * 0.14f), h * 0.97f, h * 0.2f)
            }
            "latte" -> {
                drawRect(Color(0xFFB88A5B))
                drawCircle(Color(0xFFF3EDE2), radius = w * 0.44f)
                drawCircle(Color(0xFF8A5530), radius = w * 0.33f)
                heart(Color(0xFFF0DEC2), Offset(w * 0.5f, h * 0.5f), w * 0.22f)
            }
            "wifi" -> {
                drawRect(Color(0xFFEEF1FD))
                drawRoundRect(Color.White, Offset(w * 0.12f, h * 0.2f), Size(w * 0.76f, h * 0.7f), CornerRadius(w * 0.08f))
                drawCircle(Color(0xFFD8DFF7), w * 0.13f, Offset(w * 0.5f, h * 0.42f))
                arcs(Color(0xFF3F55C8), Offset(w * 0.5f, h * 0.46f), w * 0.08f)
                drawRoundRect(Color(0xFFE6E9F6), Offset(w * 0.16f, h * 0.7f), Size(w * 0.68f, h * 0.14f), CornerRadius(w * 0.03f))
                drawRoundRect(Color(0xFF1B1F3A), Offset(w * 0.2f, h * 0.55f), Size(w * 0.6f, h * 0.035f), CornerRadius(8f))
            }
            "ticket" -> {
                drawRect(Color(0xFF0E2547))
                drawRoundRect(Color(0xFFF6F1E8), Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.9f), CornerRadius(w * 0.06f))
                drawRoundRect(Color(0xFF0E2547), Offset(w * 0.18f, h * 0.3f), Size(w * 0.32f, h * 0.1f), CornerRadius(8f))
                drawRoundRect(Color(0xFFD8553A), Offset(w * 0.62f, h * 0.3f), Size(w * 0.12f, h * 0.1f), CornerRadius(8f))
                drawRoundRect(Color(0xFF0E2547), Offset(w * 0.18f, h * 0.55f), Size(w * 0.36f, h * 0.1f), CornerRadius(8f))
            }
            "map" -> {
                drawRect(Color(0xFFE9EEE3))
                for (i in 1..3) drawRect(Color.White, Offset(w * i * 0.24f, 0f), Size(w * 0.03f, h))
                for (i in 1..3) drawRect(Color.White, Offset(0f, h * i * 0.24f), Size(w, h * 0.03f))
                drawLine(Color(0xFFBBD6EE), Offset(w * 0.5f, h), Offset(w, h * 0.55f), strokeWidth = w * 0.06f)
                drawCircle(Color(0xFFD8442F), w * 0.07f, Offset(w * 0.62f, h * 0.7f))
                drawCircle(Color.White, w * 0.025f, Offset(w * 0.62f, h * 0.7f))
            }
            "plant" -> {
                drawRect(Color(0xFFE5D6BB))
                for (i in 0 until 5) {
                    val a = -70f + i * 35f
                    val p = Path().apply {
                        moveTo(w * 0.5f, h * 0.78f)
                        val dx = Math.sin(Math.toRadians(a.toDouble())).toFloat() * w * 0.38f
                        val dy = -Math.cos(Math.toRadians(a.toDouble())).toFloat() * h * 0.55f
                        quadraticTo(w * 0.5f + dx * 0.9f, h * 0.78f + dy * 0.3f, w * 0.5f + dx, h * 0.78f + dy)
                        quadraticTo(w * 0.5f + dx * 0.4f, h * 0.78f + dy * 0.9f, w * 0.5f, h * 0.78f)
                    }
                    drawPath(p, Color(0xFF3F8A55))
                }
                drawRect(Color(0xFFD27D4C), Offset(w * 0.38f, h * 0.78f), Size(w * 0.24f, h * 0.18f))
            }
            "soup" -> {
                drawRect(Color(0xFFF2DDBE))
                drawCircle(Color.White, w * 0.4f)
                drawCircle(Color(0xFFD4832F), w * 0.32f)
                drawCircle(Color(0xFFF3D9A1), w * 0.07f, Offset(w * 0.44f, h * 0.47f))
                drawCircle(Color(0xFF5E9A3F), w * 0.035f, Offset(w * 0.6f, h * 0.55f))
            }
            "cliff" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFA6DBEF), Color(0xFFD7F0F5)), 0f, h * 0.5f))
                drawRect(Color(0xFF2FA3B0), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                val p = Path().apply { moveTo(0f, h * 0.2f); lineTo(w * 0.35f, h * 0.45f); lineTo(w * 0.3f, h); lineTo(0f, h); close() }
                drawPath(p, Color(0xFF3F5E48))
                drawCircle(Color.White, w * 0.04f, Offset(w * 0.75f, h * 0.38f))
            }
            "city" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF2E2A5E), Color(0xFFB37FB8))))
                for (i in 0 until 6) drawRect(Color(0xFF1C1840), Offset(w * (0.08f + i * 0.14f), h * (0.45f + (i % 3) * 0.08f)), Size(w * 0.11f, h))
            }
            else -> drawRect(Color(0xFFCCCCCC))
        }
    }

    private fun DrawScope.hills(color: Color, top: Float, amp: Float) {
        val p = Path().apply {
            moveTo(0f, top)
            cubicTo(size.width * 0.3f, top - size.height * amp, size.width * 0.6f, top + size.height * amp, size.width, top - size.height * amp * 0.4f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(p, color)
    }

    private fun DrawScope.peak(color: Color, cx: Float, top: Float, width: Float, base: Float) {
        val p = Path().apply { moveTo(cx - width / 2, base); lineTo(cx, top); lineTo(cx + width / 2, base); close() }
        drawPath(p, color)
    }

    private fun DrawScope.tree(color: Color, cx: Float, base: Float, height: Float) {
        val p = Path().apply { moveTo(cx - height * 0.18f, base); lineTo(cx, base - height); lineTo(cx + height * 0.18f, base); close() }
        drawPath(p, color)
    }

    private fun DrawScope.heart(color: Color, c: Offset, r: Float) {
        drawCircle(color, r * 0.55f, Offset(c.x - r * 0.45f, c.y - r * 0.2f))
        drawCircle(color, r * 0.55f, Offset(c.x + r * 0.45f, c.y - r * 0.2f))
        val p = Path().apply { moveTo(c.x - r, c.y); lineTo(c.x, c.y + r * 1.1f); lineTo(c.x + r, c.y); close() }
        drawPath(p, color)
    }

    private fun DrawScope.arcs(color: Color, c: Offset, r: Float) {
        for (i in 1..3) {
            drawArc(color, 225f, 90f, false, Offset(c.x - r * i, c.y - r * i), Size(r * 2 * i, r * 2 * i), style = Stroke(r * 0.35f))
        }
        drawCircle(color, r * 0.28f, Offset(c.x, c.y + r * 1.1f))
    }
}
