@file:OptIn(ExperimentalFoundationApi::class)

package com.clean.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.MediaStore
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

val AccentColors = listOf(
    Color(0xFFFF4D4D), Color(0xFFFF7A18), Color(0xFFFFC107), Color(0xFF2ECC71),
    Color(0xFF00E5FF), Color(0xFF4D8DFF), Color(0xFFB36BFF), Color(0xFFFF4DA6)
)

/** Cor de destaque (mutável: muda em tempo real nos Ajustes). */
var Accent by mutableStateOf(AccentColors[0])

/** Tamanhos e opções da lista de apps, compartilhados via CompositionLocal. */
data class ListStyle(val icon: Float = 52f, val text: Float = 26f, val showNew: Boolean = true, val pad: Float = 9f,
                     val bold: Boolean = false, val notif: Boolean = true)
val LocalListStyle = compositionLocalOf { ListStyle() }
val GlassFill = Color.White.copy(alpha = 0.12f)
val GlassBorder = Color.White.copy(alpha = 0.16f)

/** Cartão "vidro" translúcido usado em todo o launcher. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier.clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(0.18f), Color.White.copy(0.07f))))
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.40f), Color.White.copy(0.05f), Color.White.copy(0.20f))), shape)
    if (onClick != null || onLongClick != null)
        m = m.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    Box(m, content = content)
}

val LocalIconShape = compositionLocalOf { 0 }

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    // o estado é recriado quando o pacote muda: a linha reaproveitada nunca mostra o ícone de outro app
    var bmp by remember(pkg) { mutableStateOf(IconCache.get(pkg)) }
    LaunchedEffect(pkg) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) { IconCache.load(ctx, pkg) }
    }
    val shape = when (LocalIconShape.current) {
        1 -> CircleShape
        2 -> RoundedCornerShape(size * 0.3f)
        else -> RectangleShape
    }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(size).clip(shape), contentScale = ContentScale.Fit)
    else Spacer(Modifier.size(size))
}

@Composable
fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp).clip(CircleShape)
            .background(Color(0xFFE53935)).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(if (n > 99) "99+" else "$n", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Ícone com selo de notificações. */
@Composable
fun AppIconB(pkg: String, size: Dp) {
    Box {
        AppIcon(pkg, size)
        val n = if (LocalListStyle.current.notif) (NotifListener.counts[pkg] ?: 0) else 0
        if (n > 0) CountBadge(n, Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp))
    }
}

/** Efeito de "afundar" ao tocar + clique/clique longo. */
fun Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    this.graphicsLayer {
        scaleX = sc; scaleY = sc
        transformOrigin = TransformOrigin(0.15f, 0.5f)
    }.combinedClickable(
        interactionSource = src, indication = null,
        onClick = onClick, onLongClick = onLongClick
    )
}

/** Linhas perto do topo/rodapé da tela desvanecem e encolhem um pouco (efeito de profundidade). */
fun Modifier.edgeFx(state: LazyListState, index: Int, enabled: Boolean): Modifier =
    if (!enabled) this else this.graphicsLayer {
        val info = state.layoutInfo
        val v = info.visibleItemsInfo.firstOrNull { x -> x.index == index }
        if (v != null) {
            val vh = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
            val c = v.offset + v.size / 2f
            val d = ((c - vh / 2f) / (vh / 2f)).coerceIn(-1f, 1f)
            val k = ((abs(d) - 0.6f) / 0.4f).coerceIn(0f, 1f)
            alpha = 1f - 0.6f * k
            val sc = 1f - 0.09f * k
            scaleX = sc; scaleY = sc
            transformOrigin = TransformOrigin(0.1f, 0.5f)
        }
    }

// ------------------------------------------------------------ Fundo animado
private class Particle(
    val x: Float, val y: Float, val r: Float,
    val loops: Int, val sway: Int, val tw: Int, val ph: Float
)

private class Curtain(
    val base: Float, val amp: Float, val thick: Float, val k1: Int, val k2: Int, val ph: Float, val color: Int
)

private val CURTAINS = listOf(
    Curtain(0.16f, 0.07f, 0.34f, 4, 6, 0.0f, 1),
    Curtain(0.27f, 0.06f, 0.30f, 6, 4, 1.7f, 0),
    Curtain(0.09f, 0.08f, 0.30f, 5, 8, 3.1f, 2),
    Curtain(0.36f, 0.05f, 0.26f, 7, 5, 4.4f, 3)
)

/** Aurora: faixas de luz onduladas (verde, cor de destaque, azul e roxo) somadas ao fundo. */
private fun DrawScope.drawAurora(t: Float, accent: Color) {
    val w = size.width
    val h = size.height
    val tau = 6.2831855f
    val cols = listOf(accent, Color(0xFF2EFFB0), Color(0xFF3D8BFF), Color(0xFFB36BFF))
    CURTAINS.forEach { c ->
        val col = cols[c.color]
        val steps = 36
        val topY = FloatArray(steps + 1)
        val botY = FloatArray(steps + 1)
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val ty = h * (c.base + c.amp * sin(u * tau * 1.3f + c.ph + t * tau * c.k1))
            val th = h * c.thick * (0.7f + 0.3f * sin(u * tau * 2f + c.ph * 2f + t * tau * c.k2))
            topY[i] = ty
            botY[i] = ty + th
        }
        val p = Path()
        p.moveTo(0f, topY[0])
        for (i in 1..steps) p.lineTo(w * i / steps, topY[i])
        for (i in steps downTo 0) p.lineTo(w * i / steps, botY[i])
        p.close()
        val y0 = h * (c.base - c.amp)
        val y1 = h * (c.base + c.amp + c.thick)
        drawPath(
            p,
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.12f to col.copy(alpha = 0.60f),
                0.55f to col.copy(alpha = 0.22f),
                1f to Color.Transparent,
                startY = y0, endY = y1
            ),
            blendMode = BlendMode.Plus
        )
    }
}

/**
 * Efeitos de fundo desenhados por cima do papel de parede.
 * mask: 1 partículas, 2 aurora, 4 neve, 8 chuva (podem ser combinados).
 */
@Composable
fun FxBackground(mask: Int, modifier: Modifier = Modifier) {
    if (mask == 0) return
    val tr = rememberInfiniteTransition(label = "fx")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "t")
    val parts = remember {
        val rnd = java.util.Random(7)
        List(48) {
            Particle(
                rnd.nextFloat(), rnd.nextFloat(), 1.2f + rnd.nextFloat() * 3.0f,
                2 + rnd.nextInt(7), 1 + rnd.nextInt(4), 6 + rnd.nextInt(15), rnd.nextFloat() * 6.2831855f
            )
        }
    }
    val snow = remember {
        val rnd = java.util.Random(19)
        List(70) {
            Particle(
                rnd.nextFloat(), rnd.nextFloat(), 1.4f + rnd.nextFloat() * 2.6f,
                4 + rnd.nextInt(7), 1 + rnd.nextInt(3), 0, rnd.nextFloat() * 6.2831855f
            )
        }
    }
    val rain = remember {
        val rnd = java.util.Random(23)
        List(55) {
            Particle(rnd.nextFloat(), rnd.nextFloat(), 2f + rnd.nextFloat() * 2f, 25 + rnd.nextInt(20), 0, 0, 0f)
        }
    }
    val tau = 6.2831855f
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val dn = density
        if ((mask and 2) != 0) drawAurora(t, Accent)
        if ((mask and 1) != 0) {
            parts.forEach { p ->
                val yy = (((p.y - t * p.loops) % 1f) + 1f) % 1f
                val xx = p.x + sin(t * tau * p.sway + p.ph) * 0.03f
                val tw = 0.5f + 0.5f * sin(t * tau * p.tw + p.ph)
                val c = Offset(xx * w, yy * h)
                val edge = minOf(yy, 1f - yy).coerceAtMost(0.15f) / 0.15f   // some nas pontas
                val al = (0.20f + 0.55f * tw) * edge
                drawCircle(Accent.copy(al * 0.35f), p.r * dn * 2.4f, c)
                drawCircle(Color.White.copy(al), p.r * dn * 0.8f, c)
            }
        }
        if ((mask and 4) != 0) {
            snow.forEach { p ->
                val yy = (((p.y + t * p.loops) % 1f) + 1f) % 1f
                val xx = p.x + sin(t * tau * p.sway + p.ph) * 0.04f
                val edge = minOf(yy, 1f - yy).coerceAtMost(0.08f) / 0.08f
                drawCircle(Color.White.copy(0.85f * edge), p.r * dn, Offset(xx * w, yy * h))
            }
        }
        if ((mask and 8) != 0) {
            rain.forEach { p ->
                val yy = (((p.y + t * p.loops) % 1f) + 1f) % 1f
                val x = p.x * w
                val y = yy * h
                drawLine(
                    Color(0xFFBFD9FF).copy(0.32f), Offset(x, y), Offset(x - w * 0.012f, y + h * 0.035f),
                    strokeWidth = p.r * 0.5f * dn
                )
            }
        }
    }
}

// ------------------------------------------------------------ Relógio grande
@Composable
fun ClockHeader(use24h: Boolean, date: String, name: String, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000) }
    }
    val cal = remember(now / 1000) { Calendar.getInstance().apply { timeInMillis = now } }
    val h24 = cal.get(Calendar.HOUR_OF_DAY)
    val hh = if (use24h) h24 else (if (h24 % 12 == 0) 12 else h24 % 12)
    val mm = cal.get(Calendar.MINUTE)
    val greeting = when (h24) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    val tr = rememberInfiniteTransition(label = "colon")
    val colonA by tr.animateFloat(1f, 0.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "c")
    Column(
        Modifier.padding(start = 24.dp, bottom = 12.dp).combinedClickable(
            onClick = {
                runCatching { ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            onLongClick = onLong
        )
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%02d".format(hh), fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, color = Color.White)
            Text(":", fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, color = Accent.copy(alpha = colonA))
            Text("%02d".format(mm), fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, color = Color.White)
            if (!use24h) Text(
                if (h24 < 12) " AM" else " PM", fontSize = 18.sp, color = Color.White.copy(0.6f),
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
        val hello = if (name.isBlank()) greeting else "$greeting, ${name.trim()}"
        Text("$hello  •  $date", fontSize = 15.sp, color = Color.White.copy(0.78f))
    }
}

// ------------------------------------------------------------ Atalhos rápidos
@Composable
private fun QuickBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .background(if (active) Accent.copy(0.92f) else GlassFill)
            .border(1.dp, GlassBorder, CircleShape)
            .pressable(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
}

/** Abre a calculadora: apps conhecidos, qualquer app com "calc" no nome ou o atalho padrão do Android. */
fun openCalculator(ctx: Context, apps: List<AppInfo>) {
    val known = listOf(
        "com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator",
        "com.miui.calculator", "com.coloros.calculator", "com.oneplus.calculator", "com.oppo.calculator",
        "com.realme.calculator", "com.vivo.calculator", "com.huawei.calculator", "com.motorola.calculator",
        "com.asus.calculator", "com.lge.calculator", "com.sonyericsson.calculator", "com.transsion.calculator",
        "com.nothing.calculator"
    )
    val byKnown = apps.firstOrNull { it.pkg in known }
    val byName = apps.firstOrNull {
        val l = norm(it.label)
        l.contains("calculadora") || l.contains("calculator") || it.pkg.lowercase().contains("calculator")
    }
    val target = byKnown ?: byName
    if (target != null) { launchApp(ctx, target); return }
    val ok = runCatching {
        ctx.startActivity(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!ok) Toast.makeText(ctx, "Nenhum app de calculadora encontrado", Toast.LENGTH_SHORT).show()
}

@Composable
fun QuickActions(apps: List<AppInfo>) {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val camId = remember {
        runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }
    var torch by remember { mutableStateOf(false) }
    DisposableEffect(camId) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == camId) torch = enabled
            }
        }
        runCatching { cm.registerTorchCallback(cb, null) }
        onDispose { runCatching { cm.unregisterTorchCallback(cb) } }
    }
    fun go(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickBtn(Icons.Rounded.FlashOn, torch) { camId?.let { id -> runCatching { cm.setTorchMode(id, !torch) } } }
        QuickBtn(Icons.Rounded.Wifi, false) { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        QuickBtn(Icons.Rounded.Bluetooth, false) { go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        QuickBtn(Icons.Rounded.PhotoCamera, false) { go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        QuickBtn(Icons.Rounded.Calculate, false) { openCalculator(ctx, apps) }
    }
}

// ------------------------------------------------------------ Música
@Composable
fun Equalizer(playing: Boolean, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "eq")
    val b0 by tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "b0")
    val b1 by tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "b1")
    val b2 by tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "b2")
    val b3 by tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(490, easing = LinearEasing), RepeatMode.Reverse), label = "b3")
    androidx.compose.foundation.Canvas(modifier) {
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * 3) / 4f
        listOf(b0, b1, b2, b3).forEachIndexed { i, v ->
            val hgt = size.height * (if (playing) v else 0.2f)
            drawRoundRect(
                Accent, Offset(i * (bw + gap), size.height - hgt), Size(bw, hgt), CornerRadius(bw / 2f)
            )
        }
    }
}

@Composable
fun MusicCard(media: MediaWatcher) {
    val ctx = LocalContext.current
    val info = media.info
    Glass(
        Modifier.fillMaxWidth().height(210.dp),
        radius = 32.dp,
        onClick = {
            if (!media.hasAccess) openListenerSettings(ctx)
            else info?.let { launchPkg(ctx, it.pkg) }
        }
    ) {
        if (info == null) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier.size(84.dp).align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(22.dp)).background(Color.White.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(44.dp))
                }
                Column {
                    Text("Música", fontSize = 22.sp, fontWeight = FontWeight.Light, color = Color.White)
                    Text(
                        if (media.hasAccess) "Inicie a música ou conecte os fones"
                        else "Toque para permitir o acesso à música",
                        fontSize = 14.sp, color = Color.White.copy(0.6f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            if (info.art != null)
                Image(
                    info.art, null, Modifier.fillMaxSize().blur(28.dp).alpha(0.38f),
                    contentScale = ContentScale.Crop
                )
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (info.art != null)
                            Image(info.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(0.8f))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(info.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(info.artist, fontSize = 14.sp, color = Color.White.copy(0.65f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ media.previous() }) {
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Color.White)
                            .combinedClickable(onClick = { media.playPause() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (info.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                            tint = Color.Black, modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton({ media.next() }) {
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
            }
            Equalizer(info.playing, Modifier.align(Alignment.TopEnd).padding(16.dp).size(width = 24.dp, height = 18.dp))
        }
    }
}

// ------------------------------------------------------------ Pílulas laterais
@Composable
private fun rememberBattery(): IntState {
    val ctx = LocalContext.current
    val st = remember { mutableIntStateOf(100) }
    DisposableEffect(Unit) {
        fun read(i: Intent?) {
            if (i == null) return
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0) st.intValue = l * 100 / s
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = read(i)
        }
        read(ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return st
}

@Composable
fun SidePills(use24h: Boolean = true, onCar: () -> Unit = {}) {
    val ctx = LocalContext.current
    val battery = rememberBattery().intValue
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val vol by produceState(0) {
        while (true) {
            val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            value = am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / mx
            delay(400)
        }
    }
    val pattern = if (use24h) "HH mm" else "hh mm"
    val hhmm by produceState(SimpleDateFormat(pattern, Locale.getDefault()).format(Date()), use24h) {
        val f = SimpleDateFormat(pattern, Locale.getDefault())
        while (true) { value = f.format(Date()); delay(10_000) }
    }
    val parts = hhmm.split(" ")
    val low = battery <= 15
    val ringColor = when { battery <= 15 -> Color(0xFFFF5252); battery <= 40 -> Color(0xFFFFC107); else -> Color(0xFF4CAF50) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // bateria (toque abre o uso de bateria)
        Glass(
            Modifier.size(width = 58.dp, height = 66.dp), radius = 29.dp,
            onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.size(44.dp)) {
                    val sw = 4.dp.toPx()
                    val tl = Offset(sw / 2, sw / 2)
                    val sz = Size(size.width - sw, size.height - sw)
                    drawArc(Color.White.copy(0.2f), 0f, 360f, false, tl, sz, style = Stroke(sw))
                    drawArc(ringColor.copy(0.28f), -90f, 360f * battery / 100f, false, tl, sz,
                        style = Stroke(sw * 2.2f, cap = StrokeCap.Round))
                    drawArc(ringColor, -90f, 360f * battery / 100f, false, tl, sz,
                        style = Stroke(sw, cap = StrokeCap.Round))
                }
                Text("$battery", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        // hora (toque abre os alarmes)
        Glass(
            Modifier.size(width = 58.dp, height = 66.dp), radius = 29.dp,
            onClick = { runCatching { ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(parts[0], fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(parts.getOrElse(1) { "" }, fontSize = 17.sp, color = Color.White.copy(0.7f))
            }
        }
        // volume: toque mostra o painel, arrastar para cima/baixo ajusta
        Glass(
            Modifier.size(width = 58.dp, height = 70.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                    }
                }
                .pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(onDragStart = { acc = 0f }) { _, dy ->
                        acc += dy
                        while (acc <= -26f) {
                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                            acc += 26f
                        }
                        while (acc >= 26f) {
                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                            acc -= 26f
                        }
                    }
                },
            radius = 29.dp
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    when { vol == 0 -> Icons.Rounded.VolumeOff; vol < 50 -> Icons.Rounded.VolumeDown; else -> Icons.Rounded.VolumeUp },
                    null, tint = Color.White.copy(0.75f), modifier = Modifier.size(18.dp)
                )
                Text("$vol%", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
        // modo carro (layout vertical de multimídia)
        Glass(Modifier.size(width = 58.dp, height = 58.dp), radius = 29.dp, onClick = onCar) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DirectionsCar, "Modo carro", tint = Color.White.copy(0.9f), modifier = Modifier.size(26.dp))
            }
        }
    }
}

// ------------------------------------------------------------ Pastas
@Composable
fun FoldersRow(
    vm: LauncherVM,
    byPkg: Map<String, AppInfo>,
    onOpen: (String) -> Unit,
    onOptions: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        vm.folders.forEach { f ->
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Glass(Modifier.size(72.dp), radius = 22.dp, onClick = { onOpen(f.name) }, onLongClick = { onOptions(f.name) }) {
                    val badge = f.apps.sumOf { NotifListener.counts[it] ?: 0 }
                    if (badge > 0) CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(5.dp))
                    Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val shown = f.apps.filter { it in byPkg }.take(4)
                        (0 until 2).forEach { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (0 until 2).forEach { c ->
                                    val p = shown.getOrNull(r * 2 + c)
                                    if (p != null) AppIcon(p, 25.dp) else Spacer(Modifier.size(25.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(f.name.uppercase(), fontSize = 13.sp, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            }
        }
        Glass(Modifier.size(72.dp), radius = 22.dp, onClick = onAdd) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, "Nova pasta", tint = Color.White.copy(0.8f), modifier = Modifier.size(30.dp))
            }
        }
    }
}

/**
 * Janela no CENTRO da tela, desenhada dentro do próprio launcher (sem Dialog do sistema):
 * fundo só escurecido, sem desfoque, e sempre centralizada.
 */
@Composable
fun CenterOverlay(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "overlay")
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f * a))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .systemBarsPadding().displayCutoutPadding().imePadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .graphicsLayer { alpha = a; scaleX = 0.92f + 0.08f * a; scaleY = 0.92f + 0.08f * a }
                .pointerInput(Unit) { detectTapGestures { } },
            content = content
        )
    }
}

@Composable
fun FolderDialog(vm: LauncherVM, name: String, byPkg: Map<String, AppInfo>, onLaunch: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    val folder = vm.folders.firstOrNull { it.name == name }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    if (folder == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val apps = folder.apps.mapNotNull { byPkg[it] }

    CenterOverlay(onDismiss) {
        Surface(shape = RoundedCornerShape(30.dp), color = Color(0xF21B1B1F), contentColor = Color.White) {
            Column(Modifier.padding(20.dp)) {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                if (apps.isEmpty()) {
                    Text("Pasta vazia. Segure um app na lista e escolha “Adicionar à pasta”.",
                        color = Color.White.copy(0.65f), fontSize = 14.sp)
                } else {
                    LazyVerticalGrid(
                        GridCells.Fixed(4), Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(apps, key = { it.pkg }) { a ->
                            Column(
                                Modifier.combinedClickable(
                                    onClick = { onLaunch(a); onDismiss() },
                                    onLongClick = { menuApp = a }
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppIconB(a.pkg, 52.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(a.label, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis, color = Color.White.copy(0.9f))
                            }
                        }
                    }
                }
            }
        }
    }
    menuApp?.let { AppMenuDialog(it, vm, name) { menuApp = null } }
}

@Composable
fun FolderOptionsDialog(vm: LauncherVM, name: String, onDismiss: () -> Unit) {
    var rename by remember { mutableStateOf(false) }
    if (rename) {
        NameDialog("Renomear pasta", name, { vm.renameFolder(name, it); onDismiss() }, onDismiss)
        return
    }
    CenterOverlay(onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xF21B1B1F), contentColor = Color.White) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(name, Modifier.padding(horizontal = 22.dp, vertical = 10.dp), fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold)
                MenuRow(Icons.Rounded.Edit, "Renomear") { rename = true }
                MenuRow(Icons.Rounded.Delete, "Excluir pasta") { vm.deleteFolder(name); onDismiss() }
            }
        }
    }
}

// ------------------------------------------------------------ Menus / diálogos
@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = 16.sp, color = Color.White)
    }
}

@Composable
fun NameDialog(title: String, initial: String = "", onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CenterOverlay(onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xF21B1B1F), contentColor = Color.White) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    t, { t = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) }
                    TextButton({ if (t.isNotBlank()) onOk(t.trim()) }) { Text("OK", color = Accent) }
                }
            }
        }
    }
}

@Composable
fun AppMenuDialog(app: AppInfo, vm: LauncherVM, fromFolder: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0 principal, 1 escolher pasta, 2 nova pasta, 3 renomear
    val isFav = app.pkg in vm.favorites

    if (mode == 2) {
        NameDialog("Nova pasta", "", { vm.createFolder(it, app.pkg); onDismiss() }, onDismiss)
        return
    }
    if (mode == 3) {
        NameDialog("Renomear app", app.label, { vm.setNick(app.pkg, it); onDismiss() }, onDismiss)
        return
    }
    CenterOverlay(onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xF21B1B1F), contentColor = Color.White) {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.pkg, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                if (mode == 0) {
                    MenuRow(Icons.Rounded.Star, if (isFav) "Remover dos favoritos" else "Favoritar") {
                        vm.toggleFav(app.pkg); onDismiss()
                    }
                    val opens = vm.usage[app.pkg] ?: 0
                    Text(
                        if (opens == 0) "Ainda não aberto pelo Lume" else "Aberto $opens ${if (opens == 1) "vez" else "vezes"}",
                        fontSize = 12.sp, color = Color.White.copy(0.5f),
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp)
                    )
                    if (isFav && vm.favorites.firstOrNull() != app.pkg)
                        MenuRow(Icons.Rounded.Star, "Mover para o topo dos favoritos") { vm.moveFavTop(app.pkg); onDismiss() }
                    MenuRow(Icons.Rounded.Folder, "Adicionar à pasta") { mode = 1 }
                    MenuRow(Icons.Rounded.Edit, "Renomear app") { mode = 3 }
                    MenuRow(Icons.Rounded.Share, "Compartilhar app") {
                        runCatching {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, app.label + ": https://play.google.com/store/apps/details?id=" + app.pkg)
                            ctx.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        onDismiss()
                    }
                    MenuRow(Icons.Rounded.ShoppingCart, "Ver na Play Store") {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + app.pkg))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        onDismiss()
                    }
                    if (fromFolder != null)
                        MenuRow(Icons.Rounded.Close, "Remover desta pasta") {
                            vm.removeFromFolder(fromFolder, app.pkg); onDismiss()
                        }
                    MenuRow(Icons.Rounded.VisibilityOff, "Ocultar app") { vm.toggleHidden(app.pkg); onDismiss() }
                    MenuRow(Icons.Rounded.Info, "Informações do app") { openAppInfo(ctx, app.pkg); onDismiss() }
                    MenuRow(Icons.Rounded.Delete, "Desinstalar") { uninstallApp(ctx, app.pkg); onDismiss() }
                } else {
                    vm.folders.forEach { f ->
                        MenuRow(Icons.Rounded.Folder, f.name) { vm.addToFolder(f.name, app.pkg); onDismiss() }
                    }
                    MenuRow(Icons.Rounded.Add, "Nova pasta…") { mode = 2 }
                }
            }
        }
    }
}

// ------------------------------------------------------------ Lista
fun isNewApp(app: AppInfo): Boolean =
    app.installed > 0L && System.currentTimeMillis() - app.installed < 3L * 24 * 3600 * 1000

@Composable
fun NewTag() {
    Box(
        Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50)).background(Accent)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) { Text("NOVO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
fun AppRow(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val st = LocalListStyle.current
    Row(
        modifier.fillMaxWidth()
            .pressable(onClick, onLongClick)
            .padding(start = 22.dp, end = 52.dp, top = st.pad.dp, bottom = st.pad.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconB(app.pkg, st.icon.dp)
        Spacer(Modifier.width(20.dp))
        Text(app.label, fontSize = st.text.sp, fontWeight = if (st.bold) FontWeight.Medium else FontWeight.Light, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (st.showNew && isNewApp(app)) NewTag()
    }
}

@Composable
fun RowScope.AppCell(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit, compact: Boolean = false) {
    Column(
        Modifier.weight(1f).pressable(onClick, onLongClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconB(app.pkg, if (compact) 46.dp else 54.dp)
        Spacer(Modifier.height(5.dp))
        Text(app.label, fontSize = if (compact) 11.sp else 12.sp, color = Color.White.copy(0.92f), maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun MostUsedRow(apps: List<AppInfo>, onClick: (AppInfo) -> Unit, onLongClick: (AppInfo) -> Unit) {
    Column {
        Text("Mais usados", fontSize = 13.sp, color = Color.White.copy(0.6f))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            apps.forEach { a ->
                Box(Modifier.pressable({ onClick(a) }, { onLongClick(a) })) { AppIconB(a.pkg, 50.dp) }
            }
        }
    }
}

/**
 * Barra alfabética com TODAS as letras: o tamanho da fonte se ajusta à altura disponível.
 * Letra da seção atual fica na cor de destaque, letras sem apps ficam esmaecidas
 * e as letras perto do dedo crescem (efeito lupa).
 */
@Composable
fun AlphaBar(
    letters: List<String>,
    present: Set<String>,
    current: String?,
    width: Dp,
    height: Dp,
    haptics: Boolean,
    onSelect: (String) -> Unit,
    onActive: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val n = letters.size
    val density = LocalDensity.current
    var h by remember { mutableIntStateOf(1) }
    var last by remember { mutableStateOf<String?>(null) }
    var touchY by remember { mutableStateOf<Float?>(null) }
    val select by rememberUpdatedState(onSelect)
    val active by rememberUpdatedState(onActive)
    val hapticOn by rememberUpdatedState(haptics)
    val haptic = LocalHapticFeedback.current

    val slotPx = with(density) { (height - 8.dp).toPx() } / n
    val fsPx = minOf(slotPx * 0.62f, with(density) { 17.sp.toPx() })
    val fs = with(density) { fsPx.toSp() }

    fun at(y: Float) {
        touchY = y
        val i = (y / h * n).toInt().coerceIn(0, n - 1)
        val l = letters[i]
        if (l != last) {
            last = l
            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            select(l)
            active(l)
        }
    }
    fun end() { last = null; touchY = null; active(null) }

    Glass(
        modifier.size(width, height).onSizeChanged { h = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { o -> at(o.y); tryAwaitRelease(); end() })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { at(it.y) },
                    onDragEnd = { end() },
                    onDragCancel = { end() }
                ) { c, _ -> at(c.position.y) }
            },
        radius = width / 2
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEachIndexed { i, l ->
                val has = l in present || l == "★"
                val isCur = l == current
                Box(
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        val ty = touchY
                        var sc = 1f
                        if (ty != null) {
                            val yc = (i + 0.5f) * h / n
                            val d = (ty - yc) / (h.toFloat() / n)
                            sc = 1f + 0.9f * exp(-(d * d) / 5f)
                        } else if (isCur) sc = 1.25f
                        scaleX = sc; scaleY = sc
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        l, fontSize = fs, lineHeight = fs,
                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, softWrap = false,
                        color = when {
                            l == "★" -> Color(0xFFFFC107)
                            isCur -> Accent
                            has -> Color.White
                            else -> Color.White.copy(0.28f)
                        }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ Ajustes
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Accent.copy(0.85f) else Color.White.copy(0.12f))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(text, fontSize = 14.sp, color = Color.White) }
}
