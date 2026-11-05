package com.clean.launcher

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Pick {
    class Web(val img: WebImg) : Pick
    class Sys(val wp: SysWp, val variant: Int = 0) : Pick
}

@Composable
private fun SetupHeader(title: String, sub: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Color.White) }
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = Color.White.copy(0.6f))
        }
    }
}

/** Pré-visualização: mostra a miniatura na hora e troca pela imagem em alta qualidade assim que carregar. */
@Composable
private fun WebPreview(img: WebImg) {
    val full by produceState<ImageBitmap?>(null, img.full) { value = loadPreview(img.full) }
    Box(Modifier.fillMaxSize()) {
        NetImage(img.thumb, Modifier.fillMaxSize(), ContentScale.Crop)
        full?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High) }
        if (full == null) CircularProgressIndicator(
            Modifier.align(Alignment.TopCenter).padding(top = 48.dp).size(22.dp), color = Color.White.copy(0.7f), strokeWidth = 2.dp
        )
    }
}

/**
 * Tela de escolha do papel de parede. Aparece no primeiro uso e também em Ajustes ▸ Papel de parede.
 * Caminhos: buscar 20 fotos na web pelo termo digitado, ver as 10 categorias do sistema ou usar a galeria.
 */
@Composable
fun WallpaperSetup(vm: LauncherVM, firstRun: Boolean, onClose: () -> Unit, onGallery: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var mode by remember { mutableIntStateOf(0) }            // 0 início, 1 resultados da web, 2 opções do sistema
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WebImg>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var applying by remember { mutableStateOf(false) }

    // categoria aberta (modo 3): 4 artes prontas + fotos da web daquela categoria
    var cat by remember { mutableStateOf<SysWp?>(null) }
    var catResults by remember { mutableStateOf<List<WebImg>>(emptyList()) }
    var catLoading by remember { mutableStateOf(false) }
    var catError by remember { mutableStateOf(false) }
    var catTry by remember { mutableIntStateOf(0) }
    LaunchedEffect(cat, catTry) {
        val c = cat ?: return@LaunchedEffect
        catLoading = true
        catError = false
        catResults = emptyList()
        val r = withContext(Dispatchers.IO) { runCatching { WebImages.search(c.query, "wallpaper 4k vertical phone") } }
        catLoading = false
        val l = r.getOrNull()
        if (l == null || l.isEmpty()) catError = true else catResults = l
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        mode = 1
        loading = true
        error = null
        results = emptyList()
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { WebImages.search(q) } }
            loading = false
            val list = r.getOrNull()
            if (list == null) error = "Não consegui buscar agora. Confira a internet e tente de novo."
            else if (list.isEmpty()) error = "Não encontrei fotos para “$q”. Tente outra palavra."
            else results = list
        }
    }

    fun applyPick(p: Pick) {
        if (applying) return
        applying = true
        scope.launch {
            val dm = ctx.resources.displayMetrics
            val sc = minOf(1f, 2160f / maxOf(dm.widthPixels, dm.heightPixels))
            val tw = (dm.widthPixels * sc).toInt().coerceAtLeast(360)
            val th = (dm.heightPixels * sc).toInt().coerceAtLeast(640)
            val ok = withContext(Dispatchers.IO) {
                val bmp = when (p) {
                    is Pick.Web -> downloadWallpaper(p.img)
                    is Pick.Sys -> renderSysWallpaper(tw, th, p.wp.id, p.variant)
                }
                if (bmp != null) { vm.applyWallpaperBitmap(bmp); true } else false
            }
            applying = false
            if (ok) onClose()
            else Toast.makeText(ctx, "Essa imagem não baixou ou tem qualidade baixa. Tente outra.", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        when {
            applying -> {}
            pick != null -> pick = null
            mode == 3 -> mode = 2
            mode != 0 -> mode = 0
            else -> onClose()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFC0E0E12))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding().displayCutoutPadding()) {
            when (mode) {
                0 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        if (firstRun) "Escolha seu papel de parede" else "Trocar papel de parede",
                        fontSize = 26.sp, fontWeight = FontWeight.Light
                    )
                    Text(
                        "Digite algo que você gosta ou veja as opções prontas. Dá para mudar depois em Ajustes ▸ Papel de parede.",
                        fontSize = 14.sp, color = Color.White.copy(0.65f)
                    )
                    Glass(Modifier.fillMaxWidth(), radius = 26.dp) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Language, null, tint = Accent, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text("Buscar na web", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Personagem, filme, jogo, lugar… eu trago 20 fotos para você escolher.",
                                        fontSize = 13.sp, color = Color.White.copy(0.65f)
                                    )
                                }
                            }
                            OutlinedTextField(
                                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Ex.: Homem-Aranha, Interestelar, gatos…") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { runSearch() })
                            )
                            Button(
                                onClick = { runSearch() }, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
                            ) {
                                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Buscar 20 fotos")
                            }
                        }
                    }
                    Glass(Modifier.fillMaxWidth(), radius = 26.dp, onClick = { mode = 2 }) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Brush, null, tint = Accent, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Opções do sistema", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "10 categorias, cada uma com várias opções para comparar",
                                    fontSize = 13.sp, color = Color.White.copy(0.65f)
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(0.6f))
                        }
                    }
                    Glass(Modifier.fillMaxWidth(), radius = 26.dp, onClick = onGallery) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PhotoLibrary, null, tint = Accent, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Escolher da galeria", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text("Use uma foto que já está no celular.", fontSize = 13.sp, color = Color.White.copy(0.65f))
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(0.6f))
                        }
                    }
                    TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (firstRun) "Pular por agora" else "Fechar", color = Accent)
                    }
                }

                1 -> Column(Modifier.fillMaxSize()) {
                    SetupHeader(
                        "“${query.trim()}”",
                        when {
                            loading -> "Buscando fotos…"
                            results.isNotEmpty() -> "${results.size} fotos • toque para ver"
                            else -> ""
                        }
                    ) { mode = 0 }
                    val err = error
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Accent)
                                Spacer(Modifier.height(14.dp))
                                Text("Buscando fotos…", color = Color.White.copy(0.7f))
                            }
                        }
                        err != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(err, textAlign = TextAlign.Center, color = Color.White.copy(0.8f))
                                Button(
                                    onClick = { runSearch() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
                                ) { Text("Tentar de novo") }
                                TextButton({ mode = 0 }) { Text("Voltar", color = Accent) }
                            }
                        }
                        else -> LazyVerticalGrid(
                            GridCells.Fixed(3),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(results, key = { it.full }) { img ->
                                NetImage(
                                    img.thumb,
                                    Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(0.08f))
                                        .clickable { pick = Pick.Web(img) }
                                )
                            }
                        }
                    }
                }

                3 -> Column(Modifier.fillMaxSize()) {
                    val c = cat
                    SetupHeader("${c?.emoji ?: ""}  ${c?.title ?: ""}", "Compare as opções e toque para ver em tela cheia") { mode = 2 }
                    if (c != null) LazyVerticalGrid(
                        GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(span = { GridItemSpan(3) }) {
                            Text("ARTES DO LUME", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent,
                                letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                        }
                        items((0 until SYS_VARIANTS).toList(), key = { "s$it" }) { v ->
                            Box(
                                Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))
                                    .clickable { pick = Pick.Sys(c, v) }
                            ) {
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawSysWallpaper(c.id, v) }
                                Text("Opção ${v + 1}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                            }
                        }
                        item(span = { GridItemSpan(3) }) {
                            Text("FOTOS DA WEB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent,
                                letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
                        }
                        when {
                            catLoading -> item(span = { GridItemSpan(3) }) {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Accent)
                                }
                            }
                            catError -> item(span = { GridItemSpan(3) }) {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Sem internet ou sem fotos agora.", color = Color.White.copy(0.7f), textAlign = TextAlign.Center)
                                    TextButton({ catTry++ }) { Text("Tentar de novo", color = Accent) }
                                }
                            }
                            else -> items(catResults, key = { it.full }) { img ->
                                NetImage(
                                    img.thumb,
                                    Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(0.08f))
                                        .clickable { pick = Pick.Web(img) }
                                )
                            }
                        }
                    }
                }

                else -> Column(Modifier.fillMaxSize()) {
                    SetupHeader("Opções do sistema", "10 categorias • toque para abrir") { mode = 0 }
                    LazyVerticalGrid(
                        GridCells.Fixed(2),
                        contentPadding = PaddingValues(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(SysWallpapers, key = { it.id }) { wp ->
                            Box(
                                Modifier.aspectRatio(0.72f).clip(RoundedCornerShape(22.dp))
                                    .clickable { cat = wp; mode = 3 }
                            ) {
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawSysWallpaper(wp.id) }
                                Box(
                                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                                        .padding(12.dp)
                                ) {
                                    Text("${wp.emoji}  ${wp.title}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // pré-visualização em tela cheia + confirmar
        val p = pick
        if (p != null) {
            Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
                when (p) {
                    is Pick.Web -> WebPreview(p.img)
                    is Pick.Sys -> androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawSysWallpaper(p.wp.id, p.variant) }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(240.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f))))
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { applyPick(p) }, enabled = !applying, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
                    ) { Text(if (applying) "Aplicando…" else "Usar como papel de parede") }
                    TextButton({ if (!applying) pick = null }) { Text("Voltar", color = Color.White.copy(0.85f)) }
                }
                if (applying) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
            }
        }
    }
}
