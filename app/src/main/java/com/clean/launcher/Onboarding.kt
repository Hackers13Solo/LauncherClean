package com.clean.launcher

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = 6.2831855f
private const val LAST_STEP = 7

// ------------------------------------------------------------ modelos de layout da tela inicial
class LayoutModel(val id: Int, val title: String, val sub: String)

val LayoutModels = listOf(
    LayoutModel(0, "Clássico", "Widgets no topo e lista A–Z"),
    LayoutModel(1, "Grade", "Widgets no topo e apps em 4 colunas"),
    LayoutModel(2, "Limpo", "Só relógio e lista, sem widgets"),
    LayoutModel(3, "Compacto", "Pastas, mais usados e 5 colunas")
)

/** Mini-desenho de como a tela inicial fica em cada modelo. */
@Composable
fun LayoutPreview(mode: Int, modifier: Modifier = Modifier) {
    val accent = Accent
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val soft = Color.White.copy(0.20f)
        val mid = Color.White.copy(0.45f)
        fun bar(x: Float, y: Float, bw: Float, bh: Float, c: Color) =
            drawRoundRect(c, Offset(x, y), Size(bw, bh), CornerRadius(bh / 2f))

        bar(w * 0.10f, h * 0.06f, w * 0.46f, h * 0.05f, mid)           // relógio
        bar(w * 0.10f, h * 0.125f, w * 0.30f, h * 0.025f, soft)       // data

        var y = h * 0.19f
        if (mode == 0 || mode == 1) {
            drawRoundRect(soft, Offset(w * 0.10f, y), Size(w * 0.62f, h * 0.10f), CornerRadius(w * 0.05f))   // música
            y += h * 0.125f
        }
        if (mode != 2) {
            repeat(3) { i ->
                drawRoundRect(soft, Offset(w * 0.10f + i * w * 0.14f, y), Size(w * 0.11f, w * 0.11f), CornerRadius(w * 0.03f))
            }
            y += w * 0.11f + h * 0.03f
        }
        if (mode == 0 || mode == 1) {
            repeat(5) { i -> drawCircle(accent.copy(0.8f), w * 0.022f, Offset(w * 0.115f + i * w * 0.075f, y + w * 0.02f)) }
            y += h * 0.055f
        }
        if (mode == 3) {
            repeat(4) { i -> drawCircle(mid, w * 0.04f, Offset(w * 0.14f + i * w * 0.12f, y + w * 0.04f)) }
            y += h * 0.085f
        }
        y += h * 0.02f
        bar(w * 0.10f, y, w * 0.18f, h * 0.022f, accent.copy(0.9f))   // letra da seção
        y += h * 0.045f

        when (mode) {
            0, 2 -> {
                var yy = y
                while (yy < h * 0.93f) {
                    drawCircle(mid, w * 0.035f, Offset(w * 0.135f, yy + w * 0.035f))
                    bar(w * 0.22f, yy + w * 0.02f, w * (0.22f + 0.05f * ((yy * 7).toInt() % 4)), h * 0.025f, soft)
                    yy += h * 0.075f
                }
            }
            else -> {
                val cols = if (mode == 1) 4 else 5
                val gap = (w * 0.68f) / cols
                val r = gap * (if (mode == 1) 0.30f else 0.26f)
                var yy = y
                while (yy < h * 0.92f) {
                    repeat(cols) { c ->
                        drawRoundRect(mid, Offset(w * 0.10f + c * gap + (gap - 2 * r) / 2f, yy), Size(2 * r, 2 * r), CornerRadius(r * 0.6f))
                    }
                    yy += gap * 0.95f
                }
            }
        }
        // barra A–Z
        drawRoundRect(soft, Offset(w * 0.90f, h * 0.30f), Size(w * 0.05f, h * 0.55f), CornerRadius(w * 0.03f))
    }
}

/** Seletor dos 4 modelos (usado nos Ajustes e nas boas-vindas). */
@Composable
fun LayoutPicker(vm: LauncherVM, cardWidth: Dp = 132.dp) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LayoutModels.forEach { m ->
            val sel = vm.layoutMode == m.id
            val sc by animateFloatAsState(
                if (sel) 1f else 0.94f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "lay"
            )
            Column(
                Modifier.width(cardWidth).graphicsLayer { scaleX = sc; scaleY = sc }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) Accent.copy(0.20f) else Color.White.copy(0.07f))
                    .border(if (sel) 2.dp else 1.dp, if (sel) Accent else Color.White.copy(0.12f), RoundedCornerShape(20.dp))
                    .clickable { vm.setLayout(m.id) }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LayoutPreview(
                    m.id,
                    Modifier.fillMaxWidth().aspectRatio(0.62f).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0E0E16))
                )
                Spacer(Modifier.height(8.dp))
                Text(m.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(m.sub, fontSize = 11.sp, color = Color.White.copy(0.6f), textAlign = TextAlign.Center, lineHeight = 14.sp)
            }
        }
    }
}

// ------------------------------------------------------------ pecinhas de animação
/** Aparece suavemente (sobe + fade) depois de `delayMs`. */
@Composable
private fun Reveal(delayMs: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(550, easing = FastOutSlowInEasing), label = "reveal")
    Box(modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 48f }) { content() }
}

private fun flamePaths(c: Offset, s: Float): Pair<Path, Path> {
    fun px(x: Float) = c.x + (x - 54f) * s
    fun py(y: Float) = c.y + (y - 54f) * s
    val outer = Path().apply {
        moveTo(px(54f), py(22f))
        cubicTo(px(58f), py(31f), px(71f), py(41f), px(71f), py(58f))
        cubicTo(px(71f), py(70f), px(63f), py(80f), px(54f), py(80f))
        cubicTo(px(45f), py(80f), px(37f), py(70f), px(37f), py(58f))
        cubicTo(px(37f), py(50f), px(41f), py(44f), px(46f), py(39f))
        cubicTo(px(46f), py(45f), px(48f), py(48f), px(51f), py(50f))
        cubicTo(px(49f), py(42f), px(50f), py(30f), px(54f), py(22f))
        close()
    }
    val inner = Path().apply {
        moveTo(px(54f), py(52f))
        cubicTo(px(58f), py(57f), px(63f), py(61f), px(63f), py(68f))
        cubicTo(px(63f), py(73f), px(59f), py(77f), px(54f), py(77f))
        cubicTo(px(49f), py(77f), px(45f), py(73f), px(45f), py(68f))
        cubicTo(px(45f), py(61f), px(50f), py(57f), px(54f), py(52f))
        close()
    }
    return outer to inner
}

@Composable
private fun LogoAnimado(modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "logo")
    val pulse by tr.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val spin by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "s")
    val flick by tr.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        if (shown) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "pop"
    )
    val accent = Accent
    Canvas(modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f) }) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(accent.copy(0.55f), Color.Transparent), c, r * pulse), r * pulse, c)
        rotate(spin, c) {
            for (i in 0 until 10) {
                val a = i * TWO_PI / 10f
                drawCircle(Color.White.copy(if (i % 2 == 0) 0.85f else 0.4f), r * 0.025f, c + Offset(cos(a), sin(a)) * (r * 0.86f))
            }
        }
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFF3A1C9E), Color(0xFF8E2DE2), Color(0xFFFF5E7E)), Offset(0f, 0f), Offset(size.width, size.height)),
            r * 0.66f, c
        )
        val (outer, inner) = flamePaths(c, (r * 0.66f * 2f / 108f) * 1.55f * flick)
        drawPath(outer, Brush.verticalGradient(listOf(Color.White, Color(0xFFFFE3F0)), c.y - r * 0.5f, c.y + r * 0.5f))
        drawPath(inner, Brush.verticalGradient(listOf(Color(0xFFFFB300), Color(0xFFFF4D6D)), c.y, c.y + r * 0.5f))
    }
}

private class Confetto(val x: Float, val y: Float, val v: Float, val s: Float, val rot: Float, val c: Color)

@Composable
private fun Confetti(modifier: Modifier) {
    val items = remember {
        val rnd = java.util.Random(5)
        val cols = listOf(Color(0xFFFF4D4D), Color(0xFFFFC107), Color(0xFF2ECC71), Color(0xFF00E5FF), Color(0xFFB36BFF), Color(0xFFFF4DA6))
        List(60) { Confetto(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 0.9f, 6f + rnd.nextFloat() * 8f, rnd.nextFloat() * 360f, cols[rnd.nextInt(cols.size)]) }
    }
    val tr = rememberInfiniteTransition(label = "confetti")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        items.forEach { p ->
            val yy = (((p.y + t * p.v) % 1f) + 1f) % 1f
            val xx = p.x + sin((t * 2f + p.y) * TWO_PI) * 0.03f
            val c = Offset(xx * size.width, yy * size.height)
            rotate(p.rot + t * 360f * p.v, c) {
                drawRoundRect(p.c.copy(0.9f), Offset(c.x - p.s / 2f, c.y - p.s / 4f), Size(p.s * density, p.s * 0.5f * density), CornerRadius(2f))
            }
        }
    }
}

// ------------------------------------------------------------ etapas
@Composable
private fun StepTitle(title: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Reveal(0) { Text(title, fontSize = 30.sp, fontWeight = FontWeight.Light, color = Color.White, lineHeight = 36.sp) }
        Reveal(120) { Text(sub, fontSize = 15.sp, color = Color.White.copy(0.7f), lineHeight = 21.sp) }
    }
}

@Composable
private fun FxRow(text: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(0.08f)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 16.sp, color = Color.White)
            Text(hint, fontSize = 12.sp, color = Color.White.copy(0.55f))
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White))
    }
}

@Composable
private fun BigAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Glass(Modifier.fillMaxWidth(), radius = 24.dp, onClick = onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Accent.copy(0.25f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(hint, fontSize = 13.sp, color = Color.White.copy(0.65f))
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(0.6f))
        }
    }
}

@Composable
private fun StepBody(step: Int, vm: LauncherVM, onWallpaper: () -> Unit) {
    val ctx = LocalContext.current
    when (step) {
        0 -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LogoAnimado(Modifier.size(230.dp))
            Spacer(Modifier.height(18.dp))
            Reveal(350) { Text("Bem-vindo ao", fontSize = 20.sp, color = Color.White.copy(0.75f)) }
            Reveal(600) {
                Text("Lume", fontSize = 60.sp, fontWeight = FontWeight.ExtraLight, color = Color.White, letterSpacing = 8.sp)
            }
            Spacer(Modifier.height(10.dp))
            Reveal(950) {
                Text(
                    "Seu celular com a sua cara.\nVamos configurar tudo em poucos passos.",
                    fontSize = 16.sp, color = Color.White.copy(0.7f), textAlign = TextAlign.Center, lineHeight = 22.sp
                )
            }
        }

        1 -> Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            StepTitle("Como posso te chamar?", "Seu nome aparece na saudação da tela inicial. Pode deixar em branco.")
            Reveal(250) {
                OutlinedTextField(
                    vm.userName, { vm.userName = it.take(20); vm.saveSettings() },
                    Modifier.fillMaxWidth(), singleLine = true, label = { Text("Seu nome") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(0.3f),
                        focusedLabelColor = Accent, unfocusedLabelColor = Color.White.copy(0.6f), cursorColor = Accent
                    )
                )
            }
            Reveal(400) {
                val n = vm.userName.trim()
                Text(
                    if (n.isEmpty()) "Olá! 👋" else "Prazer, $n! 👋",
                    fontSize = 26.sp, fontWeight = FontWeight.Light, color = Accent
                )
            }
        }

        2 -> Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            StepTitle("Escolha o layout", "Como a sua tela inicial vai ficar. Dá para trocar depois em Ajustes ▸ Tela inicial.")
            Reveal(250) { LayoutPicker(vm, cardWidth = 150.dp) }
        }

        3 -> Column(Modifier.fillMaxSize().padding(top = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            StepTitle("Sua cor e seus ícones", "Toque para ver mudando na hora.")
            Reveal(250) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentColors.chunked(4).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEachIndexed { i, c ->
                                val idx = r * 4 + i
                                val sel = vm.accentIdx == idx && !vm.autoAccent
                                val sz by animateDpAsState(if (sel) 58.dp else 50.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "sw")
                                Box(
                                    Modifier.size(58.dp), contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(sz).clip(CircleShape).background(c)
                                            .then(if (sel) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                            .clickable { vm.setAccent(idx) },
                                        contentAlignment = Alignment.Center
                                    ) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White) }
                                }
                            }
                        }
                    }
                }
            }
            Reveal(400) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FORMATO DOS ÍCONES", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Original", vm.iconShape == 0) { vm.iconShape = 0; vm.saveSettings() }
                        Chip("Redondo", vm.iconShape == 1) { vm.iconShape = 1; vm.saveSettings() }
                        Chip("Arredondado", vm.iconShape == 2) { vm.iconShape = 2; vm.saveSettings() }
                    }
                }
            }
        }

        4 -> Column(Modifier.fillMaxSize().padding(top = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StepTitle("Efeitos de fundo", "Ligue e veja ao vivo atrás desta tela. Tudo pode ser mudado depois.")
            Reveal(200) { FxRow("Partículas brilhantes", "Luzinhas flutuando", (vm.fxMask and 1) != 0) { vm.setFx(1, it) } }
            Reveal(320) { FxRow("Neve", "Flocos caindo devagar", (vm.fxMask and 4) != 0) { vm.setFx(4, it) } }
            Reveal(440) { FxRow("Chuva", "Chuva fininha na tela", (vm.fxMask and 8) != 0) { vm.setFx(8, it) } }
            Reveal(560) { FxRow("Aurora", "Faixas de luz em movimento", (vm.fxMask and 2) != 0) { vm.setFx(2, it) } }
        }

        5 -> Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            StepTitle("Papel de parede", "Busque na web, veja as categorias do sistema ou use uma foto da galeria.")
            Reveal(250) { BigAction(Icons.Rounded.Wallpaper, "Escolher papel de parede", "Fotos em alta qualidade e artes prontas", onWallpaper) }
            Reveal(400) {
                Text(
                    if (vm.useCustomWallpaper) "✔ Papel de parede escolhido!" else "Você pode pular e escolher depois.",
                    fontSize = 14.sp, color = if (vm.useCustomWallpaper) Accent else Color.White.copy(0.6f)
                )
            }
        }

        6 -> Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StepTitle("Últimos ajustes", "Para o Lume funcionar completo, faça estas duas coisas rápidas.")
            Reveal(250) {
                BigAction(Icons.Rounded.Home, "Tornar o Lume o launcher padrão", "Escolha o Lume e toque em “Sempre”") {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            Reveal(400) {
                BigAction(Icons.Rounded.Notifications, "Acesso às notificações", "Liga o player de música e os selos nos ícones") {
                    openListenerSettings(ctx)
                }
            }
        }

        else -> Box(Modifier.fillMaxSize()) {
            Confetti(Modifier.fillMaxSize())
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(150); shown = true }
            val pop by animateFloatAsState(
                if (shown) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "check"
            )
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(120.dp).graphicsLayer { scaleX = pop; scaleY = pop }.clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent, Accent.copy(0.6f)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(70.dp)) }
                Spacer(Modifier.height(26.dp))
                Reveal(400) {
                    val n = vm.userName.trim()
                    Text(
                        if (n.isEmpty()) "Tudo pronto!" else "Tudo pronto, $n!",
                        fontSize = 32.sp, fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(8.dp))
                Reveal(650) {
                    Text(
                        "Seu Lume está com a sua cara.\nSegure o relógio ou toque na engrenagem para ajustar o que quiser.",
                        fontSize = 15.sp, color = Color.White.copy(0.7f), textAlign = TextAlign.Center, lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

/** Boas-vindas do primeiro uso: várias etapas com transições, efeitos ao vivo e confete no final. */
@Composable
fun Onboarding(vm: LauncherVM, onWallpaper: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    fun go(to: Int) {
        step = to.coerceIn(0, LAST_STEP)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    BackHandler(enabled = step > 0) { go(step - 1) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0A14), Color(0xFF1A1236), Color(0xFF0A0A14))))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        FxBackground(vm.fxMask)

        Column(Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding().imePadding().padding(horizontal = 24.dp)) {
            // topo: voltar e pular
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step in 1 until LAST_STEP) IconButton({ go(step - 1) }) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Color.White) }
                else Spacer(Modifier.size(48.dp))
                Spacer(Modifier.weight(1f))
                if (step in 1 until LAST_STEP) TextButton({ go(LAST_STEP) }) { Text("Pular", color = Color.White.copy(0.7f)) }
            }

            // conteúdo, com transição entre as etapas
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState)
                            (slideInHorizontally(tween(480)) { it / 3 } + fadeIn(tween(480)) + scaleIn(tween(480), 0.92f)) togetherWith
                                (slideOutHorizontally(tween(320)) { -it / 3 } + fadeOut(tween(260)) + scaleOut(tween(320), 0.92f))
                        else
                            (slideInHorizontally(tween(480)) { -it / 3 } + fadeIn(tween(480)) + scaleIn(tween(480), 0.92f)) togetherWith
                                (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(260)) + scaleOut(tween(320), 0.92f))
                    },
                    label = "onboarding"
                ) { s -> StepBody(s, vm, onWallpaper) }
            }

            // rodapé: pontinhos + botão
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp, top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0..LAST_STEP) {
                        val w by animateDpAsState(if (i == step) 26.dp else 8.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "dot")
                        Box(
                            Modifier.height(8.dp).width(w).clip(CircleShape)
                                .background(if (i == step) Accent else Color.White.copy(if (i < step) 0.5f else 0.2f))
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { if (step >= LAST_STEP) vm.finishOnboarding() else go(step + 1) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
                ) {
                    Text(
                        when (step) { 0 -> "Vamos começar"; LAST_STEP -> "Entrar no Lume"; else -> "Continuar" },
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
