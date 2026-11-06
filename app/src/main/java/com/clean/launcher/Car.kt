package com.clean.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEFAULT_YT = "https://youtube.com/playlist?list=PLarb-HH7nDPw&si=m7b3k5OX6_x9RRQq"
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private fun mapsUrl(dest: String) =
    "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=" + Uri.encode(dest)

/** Trajeto ativo, compartilhado entre o modo carro e a tela inicial (persistido). */
object CarState {
    var route by mutableStateOf<String?>(null)
    private var loaded = false
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        route = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("carRoute", null)?.ifBlank { null }
    }
    fun set(ctx: Context, r: String?) {
        route = r
        ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putString("carRoute", r ?: "").apply()
    }
}

/** WebView embutido no Compose. Só recarrega quando a URL muda. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun Web(url: String, desktop: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val last = remember { arrayOfNulls<String>(1) }
    val wv = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setGeolocationEnabled(true)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            if (desktop) settings.userAgentString = DESKTOP_UA
            setBackgroundColor(android.graphics.Color.BLACK)
            webViewClient = object : WebViewClient() {
                // não deixa a página abrir outros apps (intent://, vnd.youtube:, etc.)
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                    val s = r?.url?.scheme ?: return false
                    return s != "http" && s != "https"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(origin: String?, cb: GeolocationPermissions.Callback?) {
                    cb?.invoke(origin, true, false)
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { wv.stopLoading(); wv.destroy() } }
    AndroidView(factory = { wv }, modifier = modifier, update = {
        if (last[0] != url) { last[0] = url; it.loadUrl(url) }
    })
}

@Composable
private fun carBattery(): State<Int> {
    val ctx = LocalContext.current
    return produceState(0) {
        while (true) {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (l >= 0) value = l * 100 / s.coerceAtLeast(1)
            delay(15_000)
        }
    }
}

/** Modo carro: layout vertical tipo multimídia, com YouTube, mapa/trajeto, hora, data e bateria. */
@Composable
fun CarMode(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var ytUrl by remember { mutableStateOf(prefs.getString("carYtUrl", DEFAULT_YT) ?: DEFAULT_YT) }
    var dest by remember { mutableStateOf(prefs.getString("carDest", "") ?: "") }
    LaunchedEffect(Unit) { CarState.load(ctx) }
    val route = CarState.route   // destino com trajeto ativo (também aparece na tela inicial)
    var ytOpen by remember { mutableStateOf(false) }
    var ytCfg by remember { mutableStateOf(false) }

    val battery by carBattery()
    val now by produceState(Date()) { while (true) { value = Date(); delay(1000) } }
    val hhmm = remember(now.time / 1000) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now.time / 60000) {
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(now).replaceFirstChar { it.uppercase() }
    }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            locPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    BackHandler { if (ytOpen) ytOpen = false else onClose() }

    fun start() {
        val d = dest.trim()
        if (d.isEmpty()) { Toast.makeText(ctx, "Digite para onde quer ir", Toast.LENGTH_SHORT).show(); return }
        prefs.edit().putString("carDest", d).apply()
        CarState.set(ctx, d)
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1118), Color(0xFF05080C))))
    ) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- cabeçalho: hora, data e bateria
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(hhmm, fontSize = 56.sp, fontWeight = FontWeight.Light, color = Color.White)
                    Text(date, fontSize = 15.sp, color = Color.White.copy(0.75f))
                }
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.1f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.BatteryFull, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$battery%", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(10.dp))
                Glass(Modifier.size(46.dp), radius = 23.dp, onClick = onClose) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Fechar", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // ---- widget do YouTube
            Glass(Modifier.fillMaxWidth().height(96.dp), radius = 24.dp, onClick = { ytOpen = true }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFF0033)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("YouTube", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(
                            "Toque para abrir a playlist padrão", fontSize = 13.sp, color = Color.White.copy(0.7f),
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { ytCfg = true }) {
                        Icon(Icons.Rounded.Settings, "Configurar URL", tint = Color.White.copy(0.85f))
                    }
                }
            }

            // ---- widget do mapa / trajeto
            Glass(Modifier.fillMaxWidth().weight(1f), radius = 24.dp) {
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = dest, onValueChange = { dest = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Para onde você quer ir?") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { start() }),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { start() }, shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.Navigation, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (route == null) "Iniciar" else "Atualizar")
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(0.4f))) {
                        val r = route
                        if (r == null) {
                            Text(
                                "Digite o destino e toque em Iniciar para ver o trajeto aqui.",
                                color = Color.White.copy(0.7f), fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp)
                            )
                        } else {
                            Web(mapsUrl(r), desktop = true, modifier = Modifier.fillMaxSize())
                        }
                    }
                    if (route != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val d = route.orEmpty()
                            val ok = runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(d)))
                                        .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.isSuccess
                            if (!ok) Toast.makeText(ctx, "Google Maps não encontrado", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f)) { Text("Google Maps", fontSize = 13.sp) }
                        OutlinedButton(onClick = {
                            val d = route.orEmpty()
                            val ok = runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?navigate=yes&q=" + Uri.encode(d)))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.isSuccess
                            if (!ok) Toast.makeText(ctx, "Waze não encontrado", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f)) { Text("Waze", fontSize = 13.sp) }
                        OutlinedButton(onClick = { CarState.set(ctx, null) }, modifier = Modifier.weight(1f)) {
                            Text("Parar", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ---- janela do YouTube: sempre abre a URL padrão salva
        if (ytOpen) Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { ytOpen = false }) { Icon(Icons.Rounded.ArrowBack, "Voltar", tint = Color.White) }
                    Text("YouTube", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { ytCfg = true }) { Icon(Icons.Rounded.Settings, "URL padrão", tint = Color.White) }
                }
                Web(ytUrl, desktop = false, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }

        // ---- configurar URL padrão
        if (ytCfg) {
            var text by remember { mutableStateOf(ytUrl) }
            AlertDialog(
                onDismissRequest = { ytCfg = false },
                title = { Text("URL padrão do YouTube") },
                text = {
                    Column {
                        Text("Essa página abre sempre que você entrar no YouTube.", fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        var u = text.trim().ifEmpty { DEFAULT_YT }
                        if (!u.startsWith("http")) u = "https://$u"
                        ytUrl = u
                        prefs.edit().putString("carYtUrl", u).apply()
                        ytCfg = false
                    }) { Text("Salvar") }
                },
                dismissButton = { TextButton(onClick = { ytCfg = false }) { Text("Cancelar") } }
            )
        }
    }
}

/** Card da tela inicial: mostra o trajeto em andamento. Tocar abre o modo carro. */
@Composable
fun RouteCard(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { CarState.load(ctx) }
    val r = CarState.route ?: return
    Glass(Modifier.fillMaxWidth().height(190.dp), radius = 24.dp) {
        Box(Modifier.fillMaxSize()) {
            Web(mapsUrl(r), desktop = true, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
            // camada transparente: o mapa não rouba a rolagem da tela inicial
            Box(Modifier.fillMaxSize().clickable(onClick = onOpen))
            Row(
                Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(0.65f)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(r, color = Color.White, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
