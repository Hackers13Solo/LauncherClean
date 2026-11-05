package com.clean.launcher

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** hues: deslocamento de matiz (graus) das opções 3 e 4; query: termo usado para buscar fotos da categoria na web. */
class SysWp(val id: Int, val title: String, val emoji: String, val query: String, val hues: List<Float>)

/** Quantas artes prontas cada categoria tem (opção 1 = original, 2 = espelhada, 3 e 4 = outras cores). */
const val SYS_VARIANTS = 4

/** As 10 categorias de papel de parede que acompanham o launcher (desenhadas por código, sem internet). */
val SysWallpapers = listOf(
    SysWp(0, "Desenho", "🎨", "cartoon illustration", listOf(40f, -60f)),
    SysWp(1, "Paisagem", "🌄", "landscape scenery", listOf(60f, -70f)),
    SysWp(2, "Natureza", "🌿", "nature forest", listOf(70f, -50f)),
    SysWp(3, "Anime", "🌸", "anime art", listOf(100f, -60f)),
    SysWp(4, "Jogos", "🎮", "gaming neon", listOf(90f, -90f)),
    SysWp(5, "Terror", "🕯️", "horror dark", listOf(200f, 100f)),
    SysWp(6, "Halloween", "🎃", "halloween", listOf(90f, -40f)),
    SysWp(7, "Natal", "🎄", "christmas", listOf(25f, -30f)),
    SysWp(8, "Páscoa", "🐰", "easter", listOf(120f, -90f)),
    SysWp(9, "Espaço", "🪐", "space galaxy", listOf(90f, -80f))
)

private const val TAU = 6.2831855f

fun renderSysWallpaper(w: Int, h: Int, id: Int, variant: Int = 0): Bitmap {
    val img = ImageBitmap(w, h)
    val canvas = Canvas(img)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawSysWallpaper(id, variant)
    }
    return img.asAndroidBitmap()
}

// ------------------------------------------------------------ utilidades de desenho
private fun DrawScope.sky(c1: Color, c2: Color, c3: Color? = null, c4: Color? = null) {
    drawRect(Brush.verticalGradient(listOfNotNull(c1, c2, c3, c4)))
}

private fun DrawScope.glow(cx: Float, cy: Float, r: Float, c: Color) {
    drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
}

private fun DrawScope.ridge(y0: Float, amp: Float, freq: Float, ph: Float, color: Color) {
    val w = size.width
    val h = size.height
    val p = Path()
    p.moveTo(0f, h)
    val steps = 60
    for (i in 0..steps) {
        val u = i / steps.toFloat()
        val y = h * y0 + h * amp * (0.6f * sin(u * freq * TAU + ph) + 0.4f * sin(u * freq * 2.3f * TAU + ph * 1.7f))
        p.lineTo(u * w, y)
    }
    p.lineTo(w, h)
    p.close()
    drawPath(p, color)
}

private fun DrawScope.stars(n: Int, seed: Int, maxY: Float, alpha: Float = 0.9f) {
    val rnd = Random(seed)
    val w = size.width
    val h = size.height
    repeat(n) {
        val r = w * (0.0015f + rnd.nextFloat() * 0.0035f)
        drawCircle(Color.White.copy(alpha * (0.4f + 0.6f * rnd.nextFloat())), r, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * maxY))
    }
}

private fun DrawScope.bat(cx: Float, cy: Float, s: Float, color: Color) {
    val p = Path()
    p.moveTo(cx, cy)
    p.cubicTo(cx - s * 0.4f, cy - s * 0.9f, cx - s * 0.9f, cy - s * 0.6f, cx - s, cy - s * 0.1f)
    p.cubicTo(cx - s * 0.8f, cy + s * 0.05f, cx - s * 0.6f, cy - s * 0.05f, cx - s * 0.5f, cy + s * 0.12f)
    p.cubicTo(cx - s * 0.35f, cy + s * 0.3f, cx - s * 0.15f, cy + s * 0.25f, cx, cy + s * 0.3f)
    p.cubicTo(cx + s * 0.15f, cy + s * 0.25f, cx + s * 0.35f, cy + s * 0.3f, cx + s * 0.5f, cy + s * 0.12f)
    p.cubicTo(cx + s * 0.6f, cy - s * 0.05f, cx + s * 0.8f, cy + s * 0.05f, cx + s, cy - s * 0.1f)
    p.cubicTo(cx + s * 0.9f, cy - s * 0.6f, cx + s * 0.4f, cy - s * 0.9f, cx, cy)
    p.close()
    drawPath(p, color)
}

private fun DrawScope.branch(x: Float, y: Float, len: Float, ang: Float, depth: Int, sw: Float, color: Color) {
    if (depth == 0 || len < 4f) return
    val x2 = x + cos(ang) * len
    val y2 = y + sin(ang) * len
    drawLine(color, Offset(x, y), Offset(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
    branch(x2, y2, len * 0.72f, ang - 0.45f, depth - 1, sw * 0.7f, color)
    branch(x2, y2, len * 0.72f, ang + 0.40f, depth - 1, sw * 0.7f, color)
    if (depth > 3) branch(x2, y2, len * 0.6f, ang - 0.05f, depth - 2, sw * 0.6f, color)
}

private fun DrawScope.pumpkin(cx: Float, cy: Float, r: Float) {
    drawOval(Color(0xFFD95F00), Offset(cx - r, cy - r * 0.78f), Size(r * 2f, r * 1.56f))
    drawOval(Color(0xFFFF7A00), Offset(cx - r * 0.7f, cy - r * 0.8f), Size(r * 1.4f, r * 1.6f))
    drawOval(Color(0xFFFF8F1F), Offset(cx - r * 0.3f, cy - r * 0.82f), Size(r * 0.6f, r * 1.64f))
    drawRect(Color(0xFF2F6B1F), Offset(cx - r * 0.09f, cy - r * 1.05f), Size(r * 0.18f, r * 0.3f))
    val eyes = Path()
    eyes.moveTo(cx - r * 0.5f, cy - r * 0.15f); eyes.lineTo(cx - r * 0.2f, cy - r * 0.15f); eyes.lineTo(cx - r * 0.35f, cy - r * 0.45f); eyes.close()
    eyes.moveTo(cx + r * 0.5f, cy - r * 0.15f); eyes.lineTo(cx + r * 0.2f, cy - r * 0.15f); eyes.lineTo(cx + r * 0.35f, cy - r * 0.45f); eyes.close()
    drawPath(eyes, Color(0xFF2B0A00))
    val mouth = Path()
    mouth.moveTo(cx - r * 0.55f, cy + r * 0.15f)
    mouth.lineTo(cx - r * 0.3f, cy + r * 0.4f); mouth.lineTo(cx - r * 0.15f, cy + r * 0.2f)
    mouth.lineTo(cx, cy + r * 0.42f); mouth.lineTo(cx + r * 0.15f, cy + r * 0.2f)
    mouth.lineTo(cx + r * 0.3f, cy + r * 0.4f); mouth.lineTo(cx + r * 0.55f, cy + r * 0.15f)
    mouth.lineTo(cx, cy + r * 0.1f); mouth.close()
    drawPath(mouth, Color(0xFF2B0A00))
    glow(cx, cy + r * 0.1f, r * 1.6f, Color(0x33FFC53D))
}

private fun DrawScope.xmasTree(cx: Float, baseY: Float, th: Float, seed: Int) {
    val w = th * 0.62f
    drawRect(Color(0xFF5A3A1F), Offset(cx - w * 0.07f, baseY - th * 0.08f), Size(w * 0.14f, th * 0.1f))
    val greens = listOf(Color(0xFF0B5A32), Color(0xFF0F7A41), Color(0xFF14994F))
    for (i in 0 until 3) {
        val top = baseY - th * (0.2f + 0.27f * (i + 1))
        val bot = baseY - th * (0.05f + 0.27f * i)
        val half = w * (0.5f - 0.1f * i)
        val p = Path()
        p.moveTo(cx, top); p.lineTo(cx + half, bot); p.lineTo(cx - half, bot); p.close()
        drawPath(p, greens[i])
    }
    val rnd = Random(seed)
    val cols = listOf(Color(0xFFFF4D4D), Color(0xFFFFD43B), Color(0xFF4DA6FF), Color(0xFFFF8AD8))
    repeat(10) {
        val yy = baseY - th * (0.15f + 0.6f * rnd.nextFloat())
        val spread = w * 0.42f * (1f - (baseY - yy) / th)
        drawCircle(cols[rnd.nextInt(cols.size)], th * 0.018f, Offset(cx + (rnd.nextFloat() * 2f - 1f) * spread, yy))
    }
    val topY = baseY - th * 1.01f
    glow(cx, topY, th * 0.12f, Color(0x88FFE066))
    drawCircle(Color(0xFFFFE066), th * 0.03f, Offset(cx, topY))
}

private fun DrawScope.egg(cx: Float, cy: Float, r: Float, c1: Color, c2: Color) {
    val rect = Rect(cx - r * 0.78f, cy - r, cx + r * 0.78f, cy + r)
    val path = Path()
    path.addOval(rect)
    drawPath(path, c1)
    clipPath(path) {
        drawRect(c2, Offset(rect.left, cy - r * 0.25f), Size(rect.width, r * 0.3f))
        for (i in 0 until 5) {
            drawCircle(c2, r * 0.07f, Offset(rect.left + rect.width * (0.15f + 0.175f * i), cy + r * 0.45f))
        }
        drawCircle(Color.White.copy(0.35f), r * 0.18f, Offset(cx - r * 0.3f, cy - r * 0.5f))
    }
}

// ------------------------------------------------------------ variações
private fun hueMatrix(deg: Float): ColorMatrix {
    val r = deg * 3.1415927f / 180f
    val c = cos(r)
    val n = sin(r)
    return ColorMatrix(
        floatArrayOf(
            0.213f + c * 0.787f - n * 0.213f, 0.715f - c * 0.715f - n * 0.715f, 0.072f - c * 0.072f + n * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + n * 0.143f, 0.715f + c * 0.285f + n * 0.140f, 0.072f - c * 0.072f - n * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - n * 0.787f, 0.715f - c * 0.715f + n * 0.715f, 0.072f + c * 0.928f + n * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

/**
 * Desenha a arte da categoria `id`. variant 0 = original, 1 = espelhada,
 * 2 = outras cores (matiz A), 3 = espelhada + outras cores (matiz B).
 */
fun DrawScope.drawSysWallpaper(id: Int, variant: Int = 0) {
    if (variant <= 0) { drawSysBase(id); return }
    val hues = SysWallpapers.getOrNull(id)?.hues ?: emptyList()
    val flip = variant == 1 || variant == 3
    val hue = when (variant) { 2 -> hues.getOrElse(0) { 40f }; 3 -> hues.getOrElse(1) { -50f }; else -> 0f }
    val w = size.width
    val h = size.height
    drawIntoCanvas { canvas ->
        val paint = Paint()
        if (hue != 0f) paint.colorFilter = ColorFilter.colorMatrix(hueMatrix(hue))
        canvas.saveLayer(Rect(0f, 0f, w, h), paint)
        if (flip) withTransform({ scale(-1f, 1f, Offset(w / 2f, h / 2f)) }) { drawSysBase(id) }
        else drawSysBase(id)
        canvas.restore()
    }
}

// ------------------------------------------------------------ as 10 artes
private fun DrawScope.drawSysBase(id: Int) {
    val w = size.width
    val h = size.height
    when (id) {
        0 -> { // Desenho
            sky(Color(0xFF7FD3FF), Color(0xFFCFF1FF))
            val sc = Offset(w * 0.74f, h * 0.16f)
            for (k in 0 until 12) {
                val a = k * TAU / 12f
                val d = Offset(cos(a), sin(a))
                drawLine(Color(0xFFFFD43B), sc + d * (w * 0.15f), sc + d * (w * 0.23f), strokeWidth = w * 0.022f, cap = StrokeCap.Round)
            }
            drawCircle(Color(0xFFFFE066), w * 0.13f, sc)
            drawCircle(Color(0xFF3B2A00), w * 0.012f, sc + Offset(-w * 0.04f, -w * 0.015f))
            drawCircle(Color(0xFF3B2A00), w * 0.012f, sc + Offset(w * 0.04f, -w * 0.015f))
            drawArc(Color(0xFF3B2A00), 20f, 140f, false, sc + Offset(-w * 0.045f, -w * 0.02f), Size(w * 0.09f, w * 0.07f), style = Stroke(w * 0.008f, cap = StrokeCap.Round))
            fun cloud(cx: Float, cy: Float, s: Float) {
                drawCircle(Color.White, s * 0.6f, Offset(cx - s * 0.8f, cy))
                drawCircle(Color.White, s * 0.85f, Offset(cx, cy - s * 0.35f))
                drawCircle(Color.White, s * 0.65f, Offset(cx + s * 0.85f, cy))
                drawRect(Color.White, Offset(cx - s * 0.8f, cy - s * 0.1f), Size(s * 1.65f, s * 0.6f))
            }
            cloud(w * 0.25f, h * 0.24f, w * 0.07f)
            cloud(w * 0.62f, h * 0.36f, w * 0.05f)
            ridge(0.70f, 0.03f, 1.2f, 0.5f, Color(0xFF86DB6C))
            ridge(0.80f, 0.035f, 1.6f, 2f, Color(0xFF55C25A))
            ridge(0.91f, 0.025f, 2.1f, 4f, Color(0xFF2FA04A))
            val rnd = Random(11)
            val fl = listOf(Color(0xFFFF5C8A), Color(0xFFFFD43B), Color(0xFFFFFFFF), Color(0xFFB36BFF))
            repeat(26) {
                val x = rnd.nextFloat() * w
                val y = h * (0.84f + 0.15f * rnd.nextFloat())
                drawLine(Color(0xFF1E7D36), Offset(x, y), Offset(x, y + w * 0.03f), strokeWidth = w * 0.004f)
                drawCircle(fl[rnd.nextInt(fl.size)], w * 0.012f, Offset(x, y))
            }
        }
        1 -> { // Paisagem
            sky(Color(0xFF1B2A6B), Color(0xFF7A4FA0), Color(0xFFFF8A5B), Color(0xFFFFD9A0))
            glow(w * 0.5f, h * 0.55f, w * 0.9f, Color(0x55FFB36B))
            drawCircle(Color(0xFFFFF1C9), w * 0.14f, Offset(w * 0.5f, h * 0.52f))
            stars(60, 5, 0.35f, 0.7f)
            ridge(0.58f, 0.045f, 1.1f, 0.3f, Color(0xFF6B4A8F))
            ridge(0.68f, 0.05f, 1.5f, 2.2f, Color(0xFF4B3470))
            ridge(0.79f, 0.045f, 1.9f, 4.1f, Color(0xFF2E2150))
            ridge(0.90f, 0.03f, 2.6f, 1.1f, Color(0xFF160F30))
        }
        2 -> { // Natureza
            sky(Color(0xFF0E3B2E), Color(0xFF2E8B57), Color(0xFFB8F2A0))
            glow(w * 0.8f, h * 0.05f, w * 1.1f, Color(0x77FFF7B0))
            for (k in 0 until 5) {
                val x0 = w * (0.55f + 0.1f * k)
                val p = Path()
                p.moveTo(w * 0.85f, -h * 0.02f); p.lineTo(x0, h * 0.85f); p.lineTo(x0 + w * 0.1f, h * 0.85f); p.close()
                drawPath(p, Color.White.copy(0.06f))
            }
            ridge(0.55f, 0.04f, 1.4f, 0.8f, Color(0xFF2F8F4E))
            ridge(0.66f, 0.05f, 1.9f, 2.5f, Color(0xFF1F7A3F))
            ridge(0.78f, 0.045f, 2.3f, 4.5f, Color(0xFF14602F))
            ridge(0.90f, 0.03f, 3.1f, 1.3f, Color(0xFF0B4222))
            val rnd = Random(21)
            repeat(40) {
                val c = Offset(rnd.nextFloat() * w, h * (0.3f + 0.65f * rnd.nextFloat()))
                glow(c.x, c.y, w * 0.03f, Color(0x88EFFF8A))
                drawCircle(Color(0xFFF8FFB0), w * 0.003f, c)
            }
        }
        3 -> { // Anime
            sky(Color(0xFF1B1464), Color(0xFF7B3FA0), Color(0xFFFF7BAC), Color(0xFFFFC2A1))
            glow(w * 0.5f, h * 0.30f, w * 0.75f, Color(0x55FFE6F7))
            drawCircle(Color(0xFFFFF3FA), w * 0.17f, Offset(w * 0.5f, h * 0.30f))
            drawCircle(Color(0xFFF4DDEB), w * 0.03f, Offset(w * 0.46f, h * 0.27f))
            drawCircle(Color(0xFFF4DDEB), w * 0.02f, Offset(w * 0.54f, h * 0.33f))
            stars(50, 9, 0.28f, 0.8f)
            val rnd = Random(3)
            var x = 0f
            while (x < w) {
                val bw = w * (0.05f + rnd.nextFloat() * 0.07f)
                val bh = h * (0.10f + rnd.nextFloat() * 0.22f)
                drawRect(Color(0xFF14102E), Offset(x, h - bh), Size(bw + 1f, bh))
                var wy = h - bh + bw * 0.25f
                while (wy < h - bw * 0.3f) {
                    var wx = x + bw * 0.18f
                    while (wx < x + bw - bw * 0.2f) {
                        if (rnd.nextFloat() > 0.55f) drawRect(Color(0xFFFFE08A), Offset(wx, wy), Size(bw * 0.1f, bw * 0.13f))
                        wx += bw * 0.26f
                    }
                    wy += bw * 0.3f
                }
                x += bw
            }
            repeat(70) {
                val r = w * (0.006f + rnd.nextFloat() * 0.010f)
                drawOval(Color(0xFFFFB7D5).copy(0.85f), Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.9f), Size(r * 1.7f, r))
            }
        }
        4 -> { // Jogos (estilo retrô neon)
            sky(Color(0xFF0B0221), Color(0xFF2A0A5E), Color(0xFFB5179E))
            stars(70, 4, 0.5f, 0.8f)
            drawCircle(
                Brush.verticalGradient(listOf(Color(0xFFFFD60A), Color(0xFFFF006E)), startY = h * 0.28f, endY = h * 0.56f),
                w * 0.26f, Offset(w * 0.5f, h * 0.44f)
            )
            val gy = h * 0.56f
            drawRect(Color(0xFF12002E), Offset(0f, gy), Size(w, h - gy))
            val neon = Color(0xFFFF2BD6)
            for (i in 1..14) {
                val y = gy + (h - gy) * (i / 14f).pow(2.2f)
                drawLine(neon.copy(0.85f), Offset(0f, y), Offset(w, y), strokeWidth = w * 0.003f)
            }
            for (k in -10..10) {
                drawLine(Color(0xFF29D8FF).copy(0.8f), Offset(w * 0.5f + k * w * 0.02f, gy), Offset(w * 0.5f + k * w * 0.30f, h), strokeWidth = w * 0.003f)
            }
            drawLine(neon, Offset(0f, gy), Offset(w, gy), strokeWidth = w * 0.006f)
        }
        5 -> { // Terror
            sky(Color(0xFF050005), Color(0xFF1C0008), Color(0xFF3B0A0F))
            glow(w * 0.7f, h * 0.2f, w * 0.65f, Color(0x66FF1A1A))
            drawCircle(Color(0xFFB3141B), w * 0.14f, Offset(w * 0.7f, h * 0.2f))
            drawCircle(Color(0xFF8E0F15), w * 0.03f, Offset(w * 0.67f, h * 0.18f))
            drawCircle(Color(0xFF8E0F15), w * 0.02f, Offset(w * 0.74f, h * 0.23f))
            branch(w * 0.22f, h * 0.9f, h * 0.17f, -1.6f, 8, w * 0.018f, Color(0xFF040004))
            branch(w * 0.88f, h * 0.9f, h * 0.12f, -1.75f, 7, w * 0.012f, Color(0xFF040004))
            ridge(0.88f, 0.02f, 1.7f, 0.9f, Color(0xFF050003))
            val rnd = Random(13)
            repeat(7) {
                val cx = rnd.nextFloat() * w
                val cy = h * (0.82f + 0.15f * rnd.nextFloat())
                drawOval(Color.White.copy(0.07f), Offset(cx - w * 0.3f, cy - h * 0.02f), Size(w * 0.6f, h * 0.05f))
            }
            for (sx in listOf(0.5f, 0.56f)) {
                val ex = w * sx
                drawCircle(Color(0xFFFF2B2B), w * 0.006f, Offset(ex, h * 0.78f))
            }
        }
        6 -> { // Halloween
            sky(Color(0xFF1A0533), Color(0xFF6B1E8E), Color(0xFFFF7A18))
            glow(w * 0.5f, h * 0.3f, w * 0.8f, Color(0x66FFB347))
            drawCircle(Color(0xFFFFB347), w * 0.24f, Offset(w * 0.5f, h * 0.3f))
            drawCircle(Color(0xFFF59A2B), w * 0.04f, Offset(w * 0.43f, h * 0.27f))
            drawCircle(Color(0xFFF59A2B), w * 0.028f, Offset(w * 0.58f, h * 0.34f))
            val bc = Color(0xFF12001F)
            bat(w * 0.50f, h * 0.30f, w * 0.06f, bc)
            bat(w * 0.20f, h * 0.18f, w * 0.045f, bc)
            bat(w * 0.80f, h * 0.14f, w * 0.05f, bc)
            bat(w * 0.72f, h * 0.45f, w * 0.035f, bc)
            bat(w * 0.28f, h * 0.48f, w * 0.03f, bc)
            ridge(0.80f, 0.03f, 1.3f, 1.0f, Color(0xFF220A33))
            ridge(0.90f, 0.02f, 2.0f, 3.0f, Color(0xFF12001F))
            pumpkin(w * 0.25f, h * 0.88f, w * 0.13f)
            pumpkin(w * 0.68f, h * 0.91f, w * 0.17f)
            pumpkin(w * 0.93f, h * 0.87f, w * 0.09f)
        }
        7 -> { // Natal
            sky(Color(0xFF050B2E), Color(0xFF12307A), Color(0xFF3A6EA5))
            stars(80, 17, 0.5f, 0.9f)
            drawCircle(Color(0xFFFFF8E1), w * 0.07f, Offset(w * 0.8f, h * 0.12f))
            glow(w * 0.8f, h * 0.12f, w * 0.3f, Color(0x44FFF8E1))
            ridge(0.78f, 0.03f, 1.1f, 0.4f, Color(0xFFDCEBFA))
            xmasTree(w * 0.22f, h * 0.82f, h * 0.22f, 1)
            xmasTree(w * 0.72f, h * 0.80f, h * 0.30f, 2)
            xmasTree(w * 0.48f, h * 0.86f, h * 0.16f, 3)
            ridge(0.90f, 0.025f, 1.6f, 2.2f, Color(0xFFF4F9FF))
            val rnd = Random(31)
            repeat(130) {
                drawCircle(Color.White.copy(0.5f + 0.5f * rnd.nextFloat()), w * (0.002f + 0.005f * rnd.nextFloat()), Offset(rnd.nextFloat() * w, rnd.nextFloat() * h))
            }
        }
        8 -> { // Páscoa
            sky(Color(0xFFFFD6E8), Color(0xFFE3D4FF), Color(0xFFCDF5E5))
            glow(w * 0.2f, h * 0.1f, w * 0.7f, Color(0x66FFFFFF))
            ridge(0.80f, 0.025f, 1.2f, 0.7f, Color(0xFF9BE58A))
            ridge(0.90f, 0.02f, 1.8f, 2.6f, Color(0xFF66CC72))
            val cols = listOf(
                Color(0xFFFF8FB8) to Color(0xFFFFFFFF), Color(0xFF8FD3FF) to Color(0xFFFFE066),
                Color(0xFFFFE066) to Color(0xFFFF8FB8), Color(0xFFB9A2FF) to Color(0xFFFFFFFF),
                Color(0xFF9BE58A) to Color(0xFFFF8FB8)
            )
            val spots = listOf(
                Triple(0.22f, 0.30f, 0.11f), Triple(0.70f, 0.22f, 0.09f), Triple(0.50f, 0.48f, 0.13f),
                Triple(0.18f, 0.66f, 0.10f), Triple(0.82f, 0.62f, 0.12f), Triple(0.45f, 0.84f, 0.09f),
                Triple(0.80f, 0.88f, 0.08f)
            )
            spots.forEachIndexed { i, s ->
                val (a, b) = cols[i % cols.size]
                egg(w * s.first, h * s.second, w * s.third, a, b)
            }
            val rnd = Random(41)
            val fl = listOf(Color(0xFFFF5C8A), Color(0xFFFFD43B), Color(0xFFFFFFFF))
            repeat(24) {
                val x = rnd.nextFloat() * w
                val y = h * (0.92f + 0.07f * rnd.nextFloat())
                drawCircle(fl[rnd.nextInt(fl.size)], w * 0.012f, Offset(x, y))
                drawCircle(Color(0xFFFFC857), w * 0.004f, Offset(x, y))
            }
        }
        else -> { // Espaço
            sky(Color(0xFF03030F), Color(0xFF0B0B2E))
            glow(w * 0.2f, h * 0.25f, w * 0.9f, Color(0x553D1E8F))
            glow(w * 0.95f, h * 0.55f, w * 0.9f, Color(0x44B5179E))
            glow(w * 0.3f, h * 0.9f, w * 0.8f, Color(0x331E88E5))
            stars(160, 7, 1f, 0.9f)
            val pc = Offset(w * 0.68f, h * 0.68f)
            val pr = w * 0.19f
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC38A), Color(0xFFB4501F), Color(0xFF4A1A0C)), Offset(pc.x - pr * 0.3f, pc.y - pr * 0.3f), pr * 1.4f), pr, pc)
            rotate(-22f, pc) {
                drawOval(Color(0xFFFFE0B0).copy(0.65f), Offset(pc.x - pr * 1.7f, pc.y - pr * 0.3f), Size(pr * 3.4f, pr * 0.6f), style = Stroke(w * 0.012f))
                drawOval(Color(0xFFFFE0B0).copy(0.35f), Offset(pc.x - pr * 1.45f, pc.y - pr * 0.24f), Size(pr * 2.9f, pr * 0.48f), style = Stroke(w * 0.008f))
            }
            drawCircle(Brush.radialGradient(listOf(Color(0xFFE8E8F5), Color(0xFF8E8EA8)), Offset(w * 0.2f, h * 0.2f), w * 0.07f), w * 0.05f, Offset(w * 0.2f, h * 0.2f))
            drawLine(Brush.linearGradient(listOf(Color.White, Color.Transparent), Offset(w * 0.8f, h * 0.1f), Offset(w * 0.58f, h * 0.16f)), Offset(w * 0.8f, h * 0.1f), Offset(w * 0.58f, h * 0.16f), strokeWidth = w * 0.004f, cap = StrokeCap.Round)
        }
    }
}
