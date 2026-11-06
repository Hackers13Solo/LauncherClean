@file:OptIn(ExperimentalFoundationApi::class)

package com.clean.launcher

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.pow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: LauncherVM by viewModels()
    private lateinit var media: MediaWatcher

    /** Tela cheia: esconde barra de status (e opcionalmente a de navegação). */
    private fun applyBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (vm.hideStatus) c.hide(WindowInsetsCompat.Type.statusBars()) else c.show(WindowInsetsCompat.Type.statusBars())
        if (vm.hideNav) c.hide(WindowInsetsCompat.Type.navigationBars()) else c.show(WindowInsetsCompat.Type.navigationBars())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        media = MediaWatcher(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    LauncherScreen(vm, media) { applyBars() }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); media.start(); applyBars() }
    override fun onPause() { super.onPause(); media.stop() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) vm.homeTick++   // botão Home => volta ao topo
    }
}

private sealed interface Entry {
    data class Head(val key: String) : Entry
    data class Item(val app: AppInfo, val sec: String = "") : Entry
    data class Cells(val apps: List<AppInfo>, val sec: String = "") : Entry
}

@Composable
fun LauncherScreen(vm: LauncherVM, media: MediaWatcher, onBars: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var searching by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var folderOptions by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCar by remember { mutableStateOf(false) }
    var showWp by remember { mutableStateOf(false) }   // aberto pelas boas-vindas ou pelos Ajustes
    var picked by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }   // letra tocada na barra A–Z
    var touched by remember { mutableStateOf<String?>(null) }
    var pull by remember { mutableFloatStateOf(0f) }

    val byPkg = remember(vm.apps) { vm.apps.associateBy { it.pkg } }
    val open: (AppInfo) -> Unit = {
        if (vm.hapticOpen) (ctx as? android.app.Activity)?.window?.decorView
            ?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        vm.recordLaunch(it.pkg); launchApp(ctx, it)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.setWallpaper(uri)
            vm.markWpAsked()
            showWp = false
        }
    }

    LaunchedEffect(vm.hideStatus, vm.hideNav) { onBars() }
    LaunchedEffect(vm.homeTick) {
        if (vm.homeTick > 0) {
            searching = false; openFolder = null; menuApp = null; folderOptions = null; showSettings = false; newFolder = false; showCar = false
            listState.animateScrollToItem(0)
        }
    }
    BackHandler {
        if (searching) searching = false else scope.launch { listState.animateScrollToItem(0) }
    }

    // ---- papel de parede próprio (opcional)
    val wallpaper by produceState<ImageBitmap?>(null, vm.useCustomWallpaper, vm.wallpaperVersion) {
        value = if (vm.useCustomWallpaper) withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(File(ctx.filesDir, "wallpaper.jpg").path)?.asImageBitmap()
        } else null
    }

    // ---- lista: ★ favoritos, # e A..Z (apps ocultos ficam de fora)
    val favs = vm.favorites.toList()
    val hiddenList = vm.hidden.toList()
    val visible = remember(vm.apps, hiddenList) { vm.apps.filter { it.pkg !in hiddenList } }
    val lm = vm.layoutMode
    val cols = when (lm) { 1 -> 4; 3 -> 5; else -> 0 }   // colunas da grade (0 = lista)
    val grid = cols > 0
    val sortMode = vm.sortMode
    val usageSnap = if (sortMode == 1) vm.usage.toMap() else null
    val (entries, headIndex) = remember(visible, favs, cols, sortMode, usageSnap, vm.favTop, vm.favAlpha) {
        val list = mutableListOf<Entry>()
        val idx = mutableMapOf<String, Int>()
        // "sec" entra na chave de cada linha: o mesmo app aparece em ★ e na sua letra, e as chaves não podem repetir
        fun addApps(apps: List<AppInfo>, sec: String) {
            if (grid) apps.chunked(cols).forEach { list.add(Entry.Cells(it, sec)) }
            else apps.forEach { list.add(Entry.Item(it, sec)) }
        }
        val favApps = if (!vm.favTop) emptyList()
            else if (vm.favAlpha) visible.filter { it.pkg in favs }
            else favs.mapNotNull { p -> visible.firstOrNull { it.pkg == p } }
        if (favApps.isNotEmpty()) {
            idx["★"] = list.size + 2
            list.add(Entry.Head("★"))
            addApps(favApps, "★")
        }
        visible.groupBy {
            val c = norm(it.label).firstOrNull()
            if (c != null && c in 'a'..'z') c.uppercase() else "#"
        }.toSortedMap().forEach { (k, v) ->
            idx[k] = list.size + 2
            list.add(Entry.Head(k))
            val sorted = when (sortMode) {
                1 -> v.sortedByDescending { usageSnap?.get(it.pkg) ?: 0 }
                2 -> v.sortedByDescending { it.installed }
                else -> v
            }
            addApps(sorted, k)
        }
        list to idx
    }
    val letters = remember { listOf("★", "#") + ('A'..'Z').map { it.toString() } }
    val topUsed = vm.usage.entries.sortedByDescending { it.value }
        .mapNotNull { byPkg[it.key] }.filter { it.pkg !in hiddenList }.take(vm.mostUsedN.coerceIn(3, 6))
    val date = remember {
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date()).replaceFirstChar { it.uppercase() }
    }

    // ---- puxar a lista para baixo (no topo) abre a busca
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f &&
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                ) {
                    pull += available.y
                    if (pull > 280f) { pull = 0f; searching = true }
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pull = 0f
                return Velocity.Zero
            }
        }
    }

    CompositionLocalProvider(
        LocalIconShape provides vm.iconShape,
        LocalListStyle provides ListStyle(vm.iconSize, vm.textSize, vm.newBadge, vm.rowPad, vm.boldText, vm.notifBadge)
    ) {
        Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenHpx = with(density) { maxHeight.toPx() }

            // fundo: imagem própria (ou papel de parede do sistema) + escurecimento
            val wp = wallpaper
            if (wp != null) {
                Image(
                    wp, null,
                    Modifier.fillMaxSize()
                        .graphicsLayer { val sc = if (vm.blur > 0f) 1.12f else 1f; scaleX = sc; scaleY = sc }
                        .blur(vm.blur.dp),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = vm.dim * 0.8f), Color.Black.copy(alpha = (vm.dim * 1.5f).coerceAtMost(0.95f)))
                    )
                )
            )

            FxBackground(vm.fxMask)

            if (searching) {
                SearchScreen(visible, vm.usage, onLaunch = { searching = false; open(it) },
                    onClose = { searching = false }, onLong = { menuApp = it },
                    onWeb = { q ->
                        searching = false
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(
                                    listOf(
                                        "https://www.google.com/search?q=", "https://duckduckgo.com/?q=",
                                        "https://www.bing.com/search?q=", "https://search.brave.com/search?q="
                                    )[vm.searchEngine.coerceIn(0, 3)] + Uri.encode(q)
                                ))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    })
            } else {
                val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
                val topPad = maxOf(bars.calculateTopPadding(), 24.dp) + 8.dp
                val bottomPad = maxOf(bars.calculateBottomPadding(), 12.dp)
                // área da câmera (furo/notch) e da barra de status: a lista nunca passa por baixo dela
                val safeTop = maxOf(
                    WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                )
                val safeTopPx = with(density) { safeTop.toPx() }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(top = safeTop).nestedScroll(nested),
                    contentPadding = PaddingValues(top = (topPad - safeTop).coerceAtLeast(8.dp), bottom = bottomPad + 110.dp)
                ) {
                    item {
                        Column {
                            if (vm.bigClock) ClockHeader(vm.use24h, date, vm.userName) { showSettings = true }
                            else Text(
                                date, fontSize = 15.sp, color = Color.White.copy(0.75f),
                                modifier = Modifier.padding(start = 24.dp, bottom = 10.dp)
                                    .combinedClickable(onClick = {}, onLongClick = { showSettings = true })
                            )
                            if (lm != 2) Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    if (lm == 0 || lm == 1) MusicCard(media)
                                    if (lm == 0 || lm == 1) RouteCard { showCar = true }
                                    FoldersRow(
                                        vm, byPkg,
                                        onOpen = { openFolder = it },
                                        onOptions = { folderOptions = it },
                                        onAdd = { newFolder = true }
                                    )
                                    if (vm.quickActions && (lm == 0 || lm == 1)) QuickActions(vm.apps)
                                    if (vm.showMostUsed && topUsed.isNotEmpty())
                                        MostUsedRow(topUsed, onClick = open, onLongClick = { menuApp = it })
                                }
                                if (lm == 0 || lm == 1) SidePills(vm.use24h) { showCar = true }
                            }
                        }
                    }
                    item {
                        Text(
                            if (vm.showCount) "Todos os apps (${visible.size})" else "Todos os apps", fontSize = 18.sp, color = Color.White.copy(0.85f),
                            modifier = Modifier.padding(start = 22.dp, top = 26.dp, bottom = 6.dp)
                                .combinedClickable(onClick = {}, onLongClick = { showSettings = true })
                        )
                    }
                    items(entries.size, key = { i ->
                        // chaves estáveis: ao favoritar, cada linha mantém o próprio ícone e nome (corrige a duplicação)
                        when (val e = entries[i]) {
                            is Entry.Head -> "h:" + e.key
                            is Entry.Item -> "i:" + e.sec + ":" + e.app.pkg
                            is Entry.Cells -> "c:" + e.sec + ":" + e.apps.first().pkg
                        }
                    }) { i ->
                        when (val e = entries[i]) {
                            is Entry.Head -> Text(
                                e.key, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Accent.copy(0.9f),
                                modifier = Modifier.edgeFx(listState, i + 2, vm.edgeFx)
                                    .padding(start = 24.dp, top = 18.dp, bottom = 2.dp)
                            )
                            is Entry.Item -> AppRow(
                                e.app, onClick = { open(e.app) }, onLongClick = { menuApp = e.app },
                                modifier = Modifier.edgeFx(listState, i + 2, vm.edgeFx)
                            )
                            is Entry.Cells -> Row(
                                Modifier.fillMaxWidth().edgeFx(listState, i + 2, vm.edgeFx)
                                    .padding(start = 12.dp, end = 52.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                e.apps.forEach { a -> AppCell(a, onClick = { open(a) }, onLongClick = { menuApp = a }, compact = cols >= 5) }
                                repeat(cols - e.apps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                // ---- barra A–Z: começa ao lado da lista; ao rolar, centraliza na borda e amplia
                val progress by remember {
                    derivedStateOf {
                        val items = listState.layoutInfo.visibleItemsInfo
                        if (items.isEmpty()) 0f
                        else {
                            val head = items.firstOrNull { it.index == 0 }
                            if (head == null) 1f
                            else (listState.firstVisibleItemScrollOffset / (head.size * 0.8f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        }
                    }
                }
                val headerBottom by remember {
                    derivedStateOf {
                        val items = listState.layoutInfo.visibleItemsInfo
                        if (items.isEmpty()) screenHpx * 0.5f
                        else items.firstOrNull { it.index == 0 }?.let { (it.offset + it.size).toFloat() } ?: 0f
                    }
                }
                val t by animateFloatAsState(progress, spring(stiffness = Spring.StiffnessMediumLow), label = "bar")

                // letra da seção que está no topo da lista (destaque na barra).
                // No FIM da lista (ex.: apareceu o Z) a lista não rola mais, então o topo continua sendo o "W":
                // nesse caso vale a letra tocada na barra ou, se não houve toque, a última seção visível.
                val currentLetter by remember(entries) {
                    derivedStateOf {
                        val ei = listState.firstVisibleItemIndex - 2
                        if (ei < 0 || entries.isEmpty()) null
                        else if (!listState.canScrollForward) {
                            val heads = listState.layoutInfo.visibleItemsInfo
                                .map { it.index - 2 }.filter { it in entries.indices }
                                .mapNotNull { (entries[it] as? Entry.Head)?.key }
                            val pk = picked
                            val still = pk != null && pk.second == listState.firstVisibleItemIndex &&
                                pk.third == listState.firstVisibleItemScrollOffset
                            if (still && pk != null && pk.first in heads) pk.first else heads.lastOrNull()
                        } else {
                            var k = ei.coerceAtMost(entries.lastIndex)
                            var r: String? = null
                            while (k >= 0) {
                                val en = entries[k]
                                if (en is Entry.Head) { r = en.key; break }
                                k--
                            }
                            r
                        }
                    }
                }

                // a barra sempre mostra as 28 letras; só muda de tamanho
                val bottomReserve = with(density) { (bottomPad + 100.dp).toPx() }
                val restMin = with(density) { (9.dp * letters.size).toPx() }
                val restMax = with(density) { 392.dp.toPx() }
                val restAvail = (screenHpx - headerBottom - safeTopPx - bottomReserve).coerceIn(restMin, restMax)
                val expanded = minOf(
                    screenHpx * 0.74f, with(density) { 600.dp.toPx() }, screenHpx - 2f * bottomReserve
                ).coerceAtLeast(restMin)
                val barHpx = restAvail + (expanded - restAvail) * t
                val centerTop = (screenHpx - barHpx) / 2f
                val topPx = maxOf(headerBottom + safeTopPx + with(density) { 8.dp.toPx() }, centerTop, safeTopPx + with(density) { 8.dp.toPx() })

                if (vm.showAlpha) AlphaBar(
                    letters,
                    present = headIndex.keys,
                    current = currentLetter,
                    width = lerp(30.dp, 42.dp, t),
                    height = with(density) { barHpx.toDp() },
                    haptics = vm.haptics,
                    onSelect = { l ->
                        val from = letters.indexOf(l)
                        val target = letters.drop(from).firstNotNullOfOrNull { headIndex[it] }
                            ?: headIndex.values.maxOrNull()
                        if (target != null) {
                            val key = headIndex.entries.firstOrNull { it.value == target }?.key
                            scope.launch {
                                listState.scrollToItem(target)
                                picked = key?.let { Triple(it, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
                            }
                        }
                    },
                    onActive = { touched = it },
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp)
                        .offset { IntOffset(0, topPx.roundToInt()) }
                )

                // balão com a letra enquanto você arrasta
                var lastTouched by remember { mutableStateOf("A") }
                LaunchedEffect(touched) { touched?.let { lastTouched = it } }
                val bubble by animateFloatAsState(
                    if (touched != null) 1f else 0f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "bubble"
                )
                if (bubble > 0.01f) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).padding(end = 92.dp).size(80.dp)
                            .graphicsLayer { scaleX = bubble; scaleY = bubble; alpha = bubble.coerceIn(0f, 1f) }
                            .drawBehind { drawCircle(Accent.copy(alpha = 0.30f), radius = size.minDimension * 0.78f) }
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Accent, Accent.copy(alpha = 0.72f)))),
                        contentAlignment = Alignment.Center
                    ) { Text(lastTouched, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }

                // ajustes + busca
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = bottomPad + 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Glass(Modifier.size(48.dp), radius = 24.dp, onClick = { showSettings = true }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Settings, "Ajustes", tint = Color.White.copy(0.9f), modifier = Modifier.size(22.dp))
                        }
                    }
                    Glass(Modifier.size(62.dp), radius = 31.dp, onClick = { searching = true }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Search, "Buscar", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        // ---- janelas (desenhadas no centro, dentro do launcher)
        menuApp?.let { AppMenuDialog(it, vm, null) { menuApp = null } }
        openFolder?.let { FolderDialog(vm, it, byPkg, open) { openFolder = null } }
        folderOptions?.let { FolderOptionsDialog(vm, it) { folderOptions = null } }
        if (newFolder) NameDialog("Nova pasta", "", { vm.createFolder(it); newFolder = false }, { newFolder = false })

        // ---- boas-vindas do primeiro uso
        if (!vm.onboarded) Onboarding(vm, onWallpaper = { showWp = true })

        // ---- telas cheias: ajustes por categoria e escolha do papel de parede
        if (showSettings) SettingsScreen(
            vm,
            onClose = { showSettings = false },
            onChangeWallpaper = { showWp = true },
            onGallery = { picker.launch("image/*") }
        )
        if (showCar) CarMode(onClose = { showCar = false })
        if (showWp) WallpaperSetup(
            vm, firstRun = !vm.wpAsked,
            onClose = { vm.markWpAsked(); showWp = false },
            onGallery = { picker.launch("image/*") }
        )
        }
    }
}

@Composable
fun SearchScreen(
    apps: List<AppInfo>,
    usage: Map<String, Int>,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit,
    onLong: (AppInfo) -> Unit,
    onWeb: (String) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(q, apps) {
        val n = norm(q.trim())
        if (n.isEmpty()) apps.sortedByDescending { usage[it.pkg] ?: 0 }   // mais usados ficam perto do campo
        else apps.filter { norm(it.label).contains(n) }
            .sortedBy { if (norm(it.label).startsWith(n)) 0 else 1 }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ctx = LocalContext.current
    val calc = remember(q) { evalMath(q) }

    Column(Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding().imePadding()) {
        // melhor resultado fica colado no campo de busca (embaixo)
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            if (calc != null) item(key = "calc") {
                val txt = formatMath(calc)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("resultado", txt))
                        Toast.makeText(ctx, "Resultado copiado", Toast.LENGTH_SHORT).show()
                    }.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("=", fontSize = 30.sp, color = Accent, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(txt, fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Light)
                        Text("Toque para copiar", fontSize = 12.sp, color = Color.White.copy(0.5f))
                    }
                }
            }
            items(results, key = { it.pkg }) { a ->
                AppRow(a, onClick = { onLaunch(a) }, onLongClick = { onLong(a) })
            }
            if (q.isNotBlank()) item(key = "web") {
                Row(
                    Modifier.fillMaxWidth().clickable { onWeb(q.trim()) }
                        .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Accent, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(18.dp))
                    Text("Pesquisar “${q.trim()}” na web", fontSize = 18.sp, color = Color.White.copy(0.9f),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
        Glass(Modifier.fillMaxWidth().padding(16.dp), radius = 30.dp) {
            TextField(
                value = q, onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Buscar apps…", color = Color.White.copy(0.5f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val first = results.firstOrNull()
                    if (first != null) onLaunch(first) else if (q.isNotBlank()) onWeb(q.trim())
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Accent
                )
            )
        }
    }
}


// ------------------------------------------------------------ mini calculadora da busca
private class MathParser(val s: String) {
    var i = 0
    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
    fun expr(): Double {
        var v = term()
        while (peek() == '+' || peek() == '-') {
            val o = s[i++]
            val r = term()
            v = if (o == '+') v + r else v - r
        }
        return v
    }
    private fun term(): Double {
        var v = power()
        while (peek() == '*' || peek() == '/') {
            val o = s[i++]
            val r = power()
            v = if (o == '*') v * r else v / r
        }
        return v
    }
    private fun power(): Double {
        val b = unary()
        if (peek() == '^') { i++; return b.pow(power()) }
        return b
    }
    private fun unary(): Double {
        if (peek() == '-') { i++; return -unary() }
        if (peek() == '+') { i++; return unary() }
        return atom()
    }
    private fun atom(): Double {
        if (peek() == '(') {
            i++
            val v = expr()
            if (peek() != ')') throw IllegalArgumentException()
            i++
            return v
        }
        val st = i
        while (peek().isDigit() || peek() == '.') i++
        if (st == i) throw IllegalArgumentException()
        return s.substring(st, i).toDouble()
    }
}

/** Resolve contas digitadas na busca (ex.: 12*3+4, 2^10, (5+7)/3). Retorna null se não for uma conta. */
fun evalMath(src: String): Double? {
    val s = src.trim().replace(',', '.').replace('×', '*').replace('÷', '/')
        .replace(Regex("(?<=[0-9)])\\s*[xX]\\s*(?=[0-9(])"), "*").replace(" ", "")
    if (s.length < 3 || !Regex("[0-9+\\-*/^().]+").matches(s)) return null
    if (!Regex("[+\\-*/^]").containsMatchIn(s.drop(1))) return null
    return runCatching {
        val p = MathParser(s)
        val v = p.expr()
        if (p.i != s.length || v.isNaN() || v.isInfinite()) null else v
    }.getOrNull()
}

fun formatMath(v: Double): String =
    if (abs(v) < 1e15 && v == v.toLong().toDouble()) v.toLong().toString()
    else String.format(Locale("pt", "BR"), "%.8f", v).trimEnd('0').trimEnd(',')
