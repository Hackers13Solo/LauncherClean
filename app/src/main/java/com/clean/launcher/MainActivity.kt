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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
    data class Item(val app: AppInfo) : Entry
    data class Cells(val apps: List<AppInfo>) : Entry
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
    var touched by remember { mutableStateOf<String?>(null) }
    var pull by remember { mutableFloatStateOf(0f) }

    val byPkg = remember(vm.apps) { vm.apps.associateBy { it.pkg } }
    val open: (AppInfo) -> Unit = { vm.recordLaunch(it.pkg); launchApp(ctx, it) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setWallpaper(uri)
    }

    LaunchedEffect(vm.hideStatus, vm.hideNav) { onBars() }
    LaunchedEffect(vm.homeTick) {
        if (vm.homeTick > 0) {
            searching = false; openFolder = null; menuApp = null; folderOptions = null; showSettings = false
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
    val grid = vm.gridMode
    val (entries, headIndex) = remember(visible, favs, grid) {
        val list = mutableListOf<Entry>()
        val idx = mutableMapOf<String, Int>()
        fun addApps(apps: List<AppInfo>) {
            if (grid) apps.chunked(4).forEach { list.add(Entry.Cells(it)) }
            else apps.forEach { list.add(Entry.Item(it)) }
        }
        val favApps = visible.filter { it.pkg in favs }
        if (favApps.isNotEmpty()) {
            idx["★"] = list.size + 2
            list.add(Entry.Head("★"))
            addApps(favApps)
        }
        visible.groupBy {
            val c = norm(it.label).firstOrNull()
            if (c != null && c in 'a'..'z') c.uppercase() else "#"
        }.toSortedMap().forEach { (k, v) ->
            idx[k] = list.size + 2
            list.add(Entry.Head(k))
            addApps(v)
        }
        list to idx
    }
    val letters = remember { listOf("★", "#") + ('A'..'Z').map { it.toString() } }
    val topUsed = vm.usage.entries.sortedByDescending { it.value }
        .mapNotNull { byPkg[it.key] }.filter { it.pkg !in hiddenList }.take(4)
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
        LocalListStyle provides ListStyle(vm.iconSize, vm.textSize, vm.newBadge)
    ) {
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
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = vm.dim * 0.8f), Color.Black.copy(alpha = (vm.dim * 1.5f).coerceAtMost(0.95f)))
                    )
                )
            )

            FxBackground(vm.fxMode)

            if (searching) {
                SearchScreen(visible, vm.usage, onLaunch = { searching = false; open(it) },
                    onClose = { searching = false }, onLong = { menuApp = it },
                    onWeb = { q ->
                        searching = false
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    })
            } else {
                val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
                val topPad = maxOf(bars.calculateTopPadding(), 24.dp) + 8.dp
                val bottomPad = maxOf(bars.calculateBottomPadding(), 12.dp)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.nestedScroll(nested),
                    contentPadding = PaddingValues(top = topPad, bottom = bottomPad + 110.dp)
                ) {
                    item {
                        Column {
                            if (vm.bigClock) ClockHeader(vm.use24h, date) { showSettings = true }
                            else Text(
                                date, fontSize = 15.sp, color = Color.White.copy(0.75f),
                                modifier = Modifier.padding(start = 24.dp, bottom = 10.dp)
                                    .combinedClickable(onClick = {}, onLongClick = { showSettings = true })
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    MusicCard(media)
                                    FoldersRow(
                                        vm, byPkg,
                                        onOpen = { openFolder = it },
                                        onOptions = { folderOptions = it },
                                        onAdd = { newFolder = true }
                                    )
                                    if (vm.quickActions) QuickActions()
                                    if (topUsed.isNotEmpty())
                                        MostUsedRow(topUsed, onClick = open, onLongClick = { menuApp = it })
                                }
                                SidePills(vm.use24h)
                            }
                        }
                    }
                    item {
                        Text(
                            "Todos os apps (${visible.size})", fontSize = 18.sp, color = Color.White.copy(0.85f),
                            modifier = Modifier.padding(start = 22.dp, top = 26.dp, bottom = 6.dp)
                                .combinedClickable(onClick = {}, onLongClick = { showSettings = true })
                        )
                    }
                    items(entries.size) { i ->
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
                                e.apps.forEach { a -> AppCell(a, onClick = { open(a) }, onLongClick = { menuApp = a }) }
                                repeat(4 - e.apps.size) { Spacer(Modifier.weight(1f)) }
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

                // letra da seção que está no topo da lista (destaque na barra)
                val currentLetter by remember(entries) {
                    derivedStateOf {
                        val ei = listState.firstVisibleItemIndex - 2
                        if (ei < 0 || entries.isEmpty()) null
                        else {
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
                val restAvail = (screenHpx - headerBottom - bottomReserve).coerceIn(restMin, restMax)
                val expanded = minOf(
                    screenHpx * 0.74f, with(density) { 600.dp.toPx() }, screenHpx - 2f * bottomReserve
                ).coerceAtLeast(restMin)
                val barHpx = restAvail + (expanded - restAvail) * t
                val centerTop = (screenHpx - barHpx) / 2f
                val topPx = maxOf(headerBottom + with(density) { 8.dp.toPx() }, centerTop)

                AlphaBar(
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
                        if (target != null) scope.launch { listState.scrollToItem(target) }
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

        // ---- diálogos
        menuApp?.let { AppMenuDialog(it, vm, null) { menuApp = null } }
        openFolder?.let { FolderDialog(vm, it, byPkg, open) { openFolder = null } }
        folderOptions?.let { FolderOptionsDialog(vm, it) { folderOptions = null } }
        if (newFolder) NameDialog("Nova pasta", "", { vm.createFolder(it); newFolder = false }, { newFolder = false })
        if (showSettings) SettingsDialog(vm, onPickWallpaper = { picker.launch("image/*") }) { showSettings = false }
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

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        // melhor resultado fica colado no campo de busca (embaixo)
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
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
