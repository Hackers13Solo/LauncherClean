@file:OptIn(ExperimentalFoundationApi::class)

package com.clean.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.clip
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
import java.util.Locale

val Accent = Color(0xFFFF4D4D)
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
    var m = modifier.clip(shape).background(GlassFill).border(1.dp, GlassBorder, shape)
    if (onClick != null || onLongClick != null)
        m = m.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    Box(m, content = content)
}

val LocalIconShape = compositionLocalOf { 0 }

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState(IconCache.get(pkg), pkg) {
        if (value == null) value = withContext(Dispatchers.IO) { IconCache.load(ctx, pkg) }
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
        val n = NotifListener.counts[pkg] ?: 0
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

// ------------------------------------------------------------ Música
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
fun SidePills() {
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
    val hhmm by produceState(SimpleDateFormat("HH mm", Locale.getDefault()).format(Date())) {
        val f = SimpleDateFormat("HH mm", Locale.getDefault())
        while (true) { value = f.format(Date()); delay(10_000) }
    }
    val parts = hhmm.split(" ")
    val low = battery <= 15

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
                    drawArc(if (low) Accent else Color.White, -90f, 360f * battery / 100f, false, tl, sz,
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

@Composable
fun FolderDialog(vm: LauncherVM, name: String, byPkg: Map<String, AppInfo>, onLaunch: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    val folder = vm.folders.firstOrNull { it.name == name }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    if (folder == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    val apps = folder.apps.mapNotNull { byPkg[it] }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(30.dp), color = Color(0xF01B1B1F), contentColor = Color.White) {
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
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xF01B1B1F), contentColor = Color.White) {
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B1F),
        titleContentColor = Color.White,
        title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true) },
        confirmButton = { TextButton({ if (t.isNotBlank()) onOk(t.trim()) }) { Text("OK", color = Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar", color = Color.White.copy(0.7f)) } }
    )
}

@Composable
fun AppMenuDialog(app: AppInfo, vm: LauncherVM, fromFolder: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0 principal, 1 escolher pasta, 2 nova pasta
    val isFav = app.pkg in vm.favorites

    if (mode == 2) {
        NameDialog("Nova pasta", "", { vm.createFolder(it, app.pkg); onDismiss() }, onDismiss)
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xF01B1B1F), contentColor = Color.White) {
            Column(Modifier.padding(vertical = 12.dp)) {
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
                    MenuRow(Icons.Rounded.Folder, "Adicionar à pasta") { mode = 1 }
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
@Composable
fun AppRow(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .pressable(onClick, onLongClick)
            .padding(start = 22.dp, end = 52.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconB(app.pkg, 52.dp)
        Spacer(Modifier.width(20.dp))
        Text(app.label, fontSize = 26.sp, fontWeight = FontWeight.Light, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RowScope.AppCell(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.weight(1f).pressable(onClick, onLongClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconB(app.pkg, 54.dp)
        Spacer(Modifier.height(5.dp))
        Text(app.label, fontSize = 12.sp, color = Color.White.copy(0.92f), maxLines = 1,
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
 * Barra alfabética. Quando não há espaço (altura por letra < 14dp) mostra uma letra sim, outra não.
 */
@Composable
fun AlphaBar(
    letters: List<String>,
    width: Dp,
    height: Dp,
    fontSize: TextUnit,
    haptics: Boolean,
    onSelect: (String) -> Unit,
    onActive: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val step = if (height / letters.size < 14.dp) 2 else 1
    val shown = remember(letters, step) { letters.filterIndexed { i, _ -> i % step == 0 || i == letters.lastIndex } }
    val shownNow by rememberUpdatedState(shown)
    var h by remember { mutableIntStateOf(1) }
    var last by remember { mutableStateOf<String?>(null) }
    val select by rememberUpdatedState(onSelect)
    val active by rememberUpdatedState(onActive)
    val hapticOn by rememberUpdatedState(haptics)
    val haptic = LocalHapticFeedback.current

    fun at(y: Float) {
        val list = shownNow
        val i = (y / h * list.size).toInt().coerceIn(0, list.lastIndex)
        val l = list[i]
        if (l != last) {
            last = l
            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            select(l)
            active(l)
        }
    }
    fun end() { last = null; active(null) }

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
            Modifier.fillMaxSize().padding(vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            shown.forEach {
                Text(it, fontSize = fontSize, fontWeight = FontWeight.Medium,
                    color = if (it == "★") Color(0xFFFFC107) else Color.White)
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

@Composable
private fun SectionTitle(t: String) =
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun SwitchRow(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 16.sp, color = Color.White)
        Switch(value, onChange)
    }
}

@Composable
private fun SliderRow(text: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(text, fontSize = 14.sp, color = Color.White.copy(0.8f))
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
fun SettingsDialog(vm: LauncherVM, onPickWallpaper: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = Color(0xF21B1B1F), contentColor = Color.White,
            modifier = Modifier.heightIn(max = 640.dp)
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                SectionTitle("PAPEL DE PAREDE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Escolher imagem", vm.useCustomWallpaper) { onPickWallpaper() }
                    Chip("Do sistema", !vm.useCustomWallpaper) { vm.useCustomWallpaper = false; vm.saveSettings() }
                }
                SliderRow("Escurecer fundo", vm.dim, 0f..0.85f) { vm.dim = it; vm.saveSettings() }
                SliderRow("Desfoque (só com imagem própria, Android 12+)", vm.blur, 0f..25f) { vm.blur = it; vm.saveSettings() }

                SectionTitle("TELA")
                SwitchRow("Ocultar barra de status", vm.hideStatus) { vm.hideStatus = it; vm.saveSettings() }
                SwitchRow("Ocultar barra de navegação", vm.hideNav) { vm.hideNav = it; vm.saveSettings() }

                SectionTitle("LISTA DE APPS")
                SwitchRow("Exibir em grade (4 colunas)", vm.gridMode) { vm.gridMode = it; vm.saveSettings() }
                SwitchRow("Vibração ao usar a barra A–Z", vm.haptics) { vm.haptics = it; vm.saveSettings() }
                Text("Formato dos ícones", fontSize = 14.sp, color = Color.White.copy(0.8f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Original", vm.iconShape == 0) { vm.iconShape = 0; vm.saveSettings() }
                    Chip("Redondo", vm.iconShape == 1) { vm.iconShape = 1; vm.saveSettings() }
                    Chip("Arredondado", vm.iconShape == 2) { vm.iconShape = 2; vm.saveSettings() }
                }

                val hiddenApps = vm.apps.filter { it.pkg in vm.hidden }
                if (hiddenApps.isNotEmpty()) {
                    SectionTitle("APPS OCULTOS")
                    hiddenApps.forEach { a ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a.pkg, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton({ vm.toggleHidden(a.pkg) }) { Text("Mostrar", color = Accent) }
                        }
                    }
                }

                SectionTitle("SISTEMA")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Launcher padrão", false) {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    Chip("Acesso às notificações", false) { openListenerSettings(ctx) }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onDismiss, Modifier.align(Alignment.End)) { Text("Fechar", color = Accent) }
            }
        }
    }
}
