@file:OptIn(ExperimentalFoundationApi::class)

package com.clean.launcher

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: LauncherVM by viewModels()
    private lateinit var media: MediaWatcher

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
                    LauncherScreen(vm, media)
                }
            }
        }
    }

    override fun onResume() { super.onResume(); media.start() }
    override fun onPause() { super.onPause(); media.stop() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) vm.homeTick++   // botão Home => volta ao topo
    }
}

private sealed interface Entry {
    data class Head(val key: String) : Entry
    data class Item(val app: AppInfo) : Entry
}

@Composable
fun LauncherScreen(vm: LauncherVM, media: MediaWatcher) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var searching by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var folderOptions by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf(false) }

    val byPkg = remember(vm.apps) { vm.apps.associateBy { it.pkg } }

    LaunchedEffect(vm.homeTick) {
        if (vm.homeTick > 0) {
            searching = false; openFolder = null; menuApp = null; folderOptions = null
            listState.animateScrollToItem(0)
        }
    }
    BackHandler {
        if (searching) searching = false else scope.launch { listState.animateScrollToItem(0) }
    }

    // ---- monta lista: ★ favoritos, # e A..Z
    val favs = vm.favorites.toList()
    val (entries, headIndex) = remember(vm.apps, favs) {
        val list = mutableListOf<Entry>()
        val idx = mutableMapOf<String, Int>()
        val favApps = vm.apps.filter { it.pkg in favs }
        if (favApps.isNotEmpty()) {
            idx["★"] = list.size + 2
            list.add(Entry.Head("★"))
            favApps.forEach { list.add(Entry.Item(it)) }
        }
        vm.apps.groupBy {
            val c = norm(it.label).firstOrNull()
            if (c != null && c in 'a'..'z') c.uppercase() else "#"
        }.toSortedMap().forEach { (k, v) ->
            idx[k] = list.size + 2
            list.add(Entry.Head(k))
            v.forEach { list.add(Entry.Item(it)) }
        }
        list to idx
    }
    val letters = remember { listOf("★", "#") + ('A'..'Z').map { it.toString() } }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0x66000000), Color(0x99000000)))
        )
    ) {
        if (searching) {
            SearchScreen(vm, onClose = { searching = false }, onLong = { menuApp = it })
        } else {
            val bars = WindowInsets.systemBars.asPaddingValues()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = bars.calculateTopPadding() + 16.dp,
                    bottom = bars.calculateBottomPadding() + 110.dp
                )
            ) {
                item {
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
                        }
                        SidePills(media)
                    }
                }
                item {
                    Text(
                        "Todos os apps", fontSize = 18.sp, color = Color.White.copy(0.85f),
                        modifier = Modifier.padding(start = 22.dp, top = 26.dp, bottom = 6.dp)
                    )
                }
                items(entries.size) { i ->
                    when (val e = entries[i]) {
                        is Entry.Head -> Text(
                            e.key, fontSize = 17.sp, color = Color.White.copy(0.55f),
                            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 2.dp)
                        )
                        is Entry.Item -> AppRow(
                            e.app,
                            onClick = { launchApp(ctx, e.app) },
                            onLongClick = { menuApp = e.app }
                        )
                    }
                }
            }

            AlphaBar(
                letters,
                onSelect = { l ->
                    val from = letters.indexOf(l)
                    val target = letters.drop(from).firstNotNullOfOrNull { headIndex[it] }
                        ?: headIndex.values.maxOrNull()
                    if (target != null) scope.launch { listState.scrollToItem(target) }
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, bottom = 70.dp)
                    .fillMaxHeight(0.6f)
            )

            val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Glass(
                Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = nav + 20.dp).size(62.dp),
                radius = 31.dp,
                onClick = { searching = true }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, "Buscar", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    // ---- diálogos
    menuApp?.let { AppMenuDialog(it, vm, null) { menuApp = null } }
    openFolder?.let { FolderDialog(vm, it, byPkg) { openFolder = null } }
    folderOptions?.let { FolderOptionsDialog(vm, it) { folderOptions = null } }
    if (newFolder) NameDialog("Nova pasta", "", { vm.createFolder(it); newFolder = false }, { newFolder = false })
}

@Composable
fun SearchScreen(vm: LauncherVM, onClose: () -> Unit, onLong: (AppInfo) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(q, vm.apps) {
        val n = norm(q.trim())
        if (n.isEmpty()) vm.apps
        else vm.apps.filter { norm(it.label).contains(n) }
            .sortedBy { if (norm(it.label).startsWith(n)) 0 else 1 }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        // melhor resultado fica colado no campo de busca (embaixo)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            items(results, key = { it.pkg }) { a ->
                AppRow(a, onClick = { onClose(); launchApp(ctx, a) }, onLongClick = { onLong(a) })
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
                    results.firstOrNull()?.let { onClose(); launchApp(ctx, it) }
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
