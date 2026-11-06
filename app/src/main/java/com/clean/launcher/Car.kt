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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import android.view.ViewGroup
import kotlin.math.roundToInt
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

/** Janela flutuante do YouTube: um único WebView que continua tocando enquanto você navega na tela inicial. */
object YtWin {
    var visible by mutableStateOf(false)
    var minimized by mutableStateOf(false)
    var cfg by mutableStateOf(false)
    var url by mutableStateOf(DEFAULT_YT)
    var offX by mutableStateOf(40f)
    var offY by mutableStateOf(420f)
    var wDp by mutableStateOf(320f)
    var hDp by mutableStateOf(260f)
    private var loaded = false
    private var wv: WebView? = null
    private var loadedUrl: String? = null

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        url = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("carYtUrl", DEFAULT_YT) ?: DEFAULT_YT
    }
    fun saveUrl(ctx: Context, u: String) {
        url = u
        ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().putString("carYtUrl", u).apply()
    }
    fun open() { visible = true; minimized = false }
    fun close() {
        visible = false
        wv?.loadUrl("about:blank")   // para o áudio
        loadedUrl = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun view(ctx: Context): WebView {
        val v = wv ?: WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(android.graphics.Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                    val s = r?.url?.scheme ?: return false
                    return s != "http" && s != "https"
                }
            }
            webChromeClient = WebChromeClient()
        }.also { wv = it }
        if (loadedUrl != url) { loadedUrl = url; v.loadUrl(url) }   // sempre abre a URL padrão
        return v
    }
}

/** Camada da janelinha do YouTube (fica por cima da tela inicial e do modo carro). */
@Composable
fun YoutubeLayer(docked: Boolean = false) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { YtWin.load(ctx) }
    if (YtWin.visible && !docked) YoutubeWindow()
    if (YtWin.cfg) {
        var text by remember { mutableStateOf(YtWin.url) }
        AlertDialog(
            onDismissRequest = { YtWin.cfg = false },
            title = { Text("URL padrão do YouTube") },
            text = {
                Column {
                    Text("Essa página abre sempre que você abrir a janela do YouTube.", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    var u = text.trim().ifEmpty { DEFAULT_YT }
                    if (!u.startsWith("http")) u = "https://$u"
                    YtWin.saveUrl(ctx, u)
                    YtWin.cfg = false
                    if (YtWin.visible) YtWin.view(ctx)   // recarrega com a nova URL
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { YtWin.cfg = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun YoutubeWindow() {
    val ctx = LocalContext.current
    val d = LocalDensity.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .offset { IntOffset(YtWin.offX.roundToInt(), YtWin.offY.roundToInt()) }
            .width(YtWin.wDp.dp)
            .shadow(18.dp, shape)
            .clip(shape)
            .background(Color(0xFF111418))
            .border(1.dp, Color.White.copy(0.18f), shape)
    ) {
        // barra de título (arraste para mover a janela)
        Row(
            Modifier.fillMaxWidth().height(38.dp).background(Color(0xFF1E232A))
                .pointerInput(Unit) {
                    detectDragGestures { c, drag ->
                        c.consume()
                        YtWin.offX += drag.x
                        YtWin.offY += drag.y
                    }
                }
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.PlayCircle, null, tint = Color(0xFFFF0033), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("YouTube", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            @Composable fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: () -> Unit) =
                Box(Modifier.size(38.dp).clickable(onClick = on), contentAlignment = Alignment.Center) {
                    Icon(icon, desc, tint = Color.White.copy(0.9f), modifier = Modifier.size(18.dp))
                }
            Btn(Icons.Rounded.Settings, "URL padrão") { YtWin.cfg = true }
            Btn(if (YtWin.minimized) Icons.Rounded.OpenInFull else Icons.Rounded.Remove, "Minimizar") { YtWin.minimized = !YtWin.minimized }
            Btn(Icons.Rounded.Close, "Fechar") { YtWin.close() }
        }
        // conteúdo (minimizado: continua tocando, só esconde o vídeo)
        Box(Modifier.fillMaxWidth().height(if (YtWin.minimized) 1.dp else YtWin.hDp.dp)) {
            AndroidView(
                factory = { YtWin.view(ctx).also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize()
            )
            if (!YtWin.minimized) Box(
                Modifier.align(Alignment.BottomEnd).size(34.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { c, drag ->
                            c.consume()
                            YtWin.wDp = (YtWin.wDp + drag.x / d.density).coerceIn(200f, 460f)
                            YtWin.hDp = (YtWin.hDp + drag.y / d.density).coerceIn(120f, 700f)
                        }
                    },
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(Icons.Rounded.DragHandle, "Redimensionar", tint = Color.White.copy(0.8f),
                    modifier = Modifier.padding(6.dp).size(18.dp))
            }
        }
    }
}

/** Mapa com rota feito em OpenStreetMap + OSRM (funciona dentro do WebView, sem chave de API). */
private fun routeHtml(dest: String): String = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css">
<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
<style>
html,body,#m{height:100%;margin:0;background:#0b1118}
.leaflet-tile-pane{filter:brightness(.85) contrast(1.05)}
#info{position:absolute;left:8px;right:8px;bottom:8px;z-index:999;background:rgba(10,14,20,.88);color:#fff;
 font:600 15px sans-serif;padding:10px 14px;border-radius:14px;display:none}
#info small{display:block;font-weight:400;opacity:.8;margin-top:2px;font-size:13px}
</style></head><body><div id="m"></div><div id="info"></div>
<script>
var DEST=${org.json.JSONObject.quote(dest)};
var map=L.map('m',{zoomControl:false,attributionControl:false}).setView([-26.48,-49.07],13);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);
var me=null,target=null,line=null,tm=null,lastReq=0,fitted=false,info=document.getElementById('info');
function say(t,s){info.style.display='block';info.innerHTML=t+(s?'<small>'+s+'</small>':'')}
function step(st){
  var m=st.maneuver||{},n=st.name?(' em '+st.name):'';
  var mod={left:'à esquerda','slight left':'levemente à esquerda','sharp left':'fechado à esquerda',right:'à direita','slight right':'levemente à direita','sharp right':'fechado à direita',straight:'em frente',uturn:'faça o retorno'}[m.modifier]||'';
  if(m.type==='arrive')return 'Chegada ao destino';
  if(m.type==='depart')return 'Siga'+n;
  if(m.type==='roundabout'||m.type==='rotary')return 'Na rotatória, saia'+n;
  return 'Vire '+mod+n;
}
function route(){
  if(!me||!target)return;
  lastReq=Date.now();
  fetch('https://router.project-osrm.org/route/v1/driving/'+me[1]+','+me[0]+';'+target[1]+','+target[0]+'?overview=full&geometries=geojson&steps=true')
   .then(function(r){return r.json()}).then(function(j){
     if(!j.routes||!j.routes.length){say('Sem rota encontrada');return}
     var r=j.routes[0];
     if(line)map.removeLayer(line);
     line=L.geoJSON(r.geometry,{style:{color:'#4da3ff',weight:6,opacity:.95}}).addTo(map);
     if(!fitted){map.fitBounds(line.getBounds(),{padding:[30,60]});fitted=true}
     var km=(r.distance/1000).toFixed(1).replace('.',','),min=Math.round(r.duration/60);
     var st=r.legs[0].steps[1]||r.legs[0].steps[0];
     say(km+' km · '+min+' min',st?step(st):'');
   }).catch(function(){say('Sem conexão para calcular a rota')});
}
function setMe(lat,lon){
  me=[lat,lon];
  if(!me_m){me_m=L.circleMarker(me,{radius:8,color:'#fff',weight:3,fillColor:'#2f80ff',fillOpacity:1}).addTo(map)}
  else me_m.setLatLng(me);
  if(Date.now()-lastReq>30000)route();
}
var me_m=null;
fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=br&q='+encodeURIComponent(DEST))
 .then(function(r){return r.json()}).then(function(j){
   if(!j.length){say('Destino não encontrado','Tente incluir a cidade no nome');return}
   target=[parseFloat(j[0].lat),parseFloat(j[0].lon)];
   tm=L.circleMarker(target,{radius:9,color:'#fff',weight:3,fillColor:'#ff3b30',fillOpacity:1}).addTo(map);
   map.setView(target,14);
   say('Calculando rota...','Aguardando sua localização');
   route();
 }).catch(function(){say('Sem conexão para buscar o destino')});
if(navigator.geolocation){
  navigator.geolocation.watchPosition(function(p){setMe(p.coords.latitude,p.coords.longitude)},
   function(){if(!me)say('Ative o GPS e permita a localização')},{enableHighAccuracy:true,maximumAge:5000});
}
</script></body></html>
"""

/** WebView do mapa com rota até [dest]. Recarrega só quando o destino muda. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun RouteMap(dest: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val last = remember { arrayOfNulls<String>(1) }
    val wv = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setGeolocationEnabled(true)
            setBackgroundColor(android.graphics.Color.parseColor("#0B1118"))
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(origin: String?, cb: GeolocationPermissions.Callback?) {
                    cb?.invoke(origin, true, false)
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { wv.stopLoading(); wv.destroy() } }
    AndroidView(factory = { wv }, modifier = modifier, update = {
        if (last[0] != dest) {
            last[0] = dest
            it.loadDataWithBaseURL("https://lume.local/", routeHtml(dest), "text/html", "utf-8", null)
        }
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

/** Janela acoplada do modo carro: barra de título + conteúdo, igual à janelinha flutuante. */
@Composable
private fun CarWindow(
    modifier: Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(Color(0xFF111418)).border(1.dp, Color.White.copy(0.18f), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().height(38.dp).background(Color(0xFF1E232A)).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            actions()
        }
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun WinBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: () -> Unit) =
    Box(Modifier.size(38.dp).clickable(onClick = on), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White.copy(0.9f), modifier = Modifier.size(18.dp))
    }

@Composable
private fun WinText(label: String, on: () -> Unit) =
    Box(Modifier.height(38.dp).clickable(onClick = on).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Color(0xFFB57BFF), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }

/** Modo carro: horário no topo e, abaixo, o YouTube (metade de cima) e o mapa (metade de baixo) em janelas. */
@Composable
fun CarMode(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var dest by remember { mutableStateOf(prefs.getString("carDest", "") ?: "") }
    LaunchedEffect(Unit) { CarState.load(ctx) }
    val route = CarState.route   // destino com trajeto ativo (também aparece na tela inicial)
    LaunchedEffect(Unit) { YtWin.load(ctx) }

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

    BackHandler { onClose() }

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

            // ---- janela do YouTube (metade de cima)
            CarWindow(
                Modifier.fillMaxWidth().weight(1f), "YouTube", Icons.Rounded.PlayCircle, Color(0xFFFF0033),
                actions = {
                    WinBtn(Icons.Rounded.Settings, "URL padrão") { YtWin.cfg = true }
                    if (YtWin.visible) WinBtn(Icons.Rounded.Close, "Fechar") { YtWin.close() }
                }
            ) {
                if (YtWin.visible) {
                    AndroidView(
                        factory = { YtWin.view(ctx).also { (it.parent as? ViewGroup)?.removeView(it) } },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().clickable { YtWin.open() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFF0033)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                        Spacer(Modifier.height(10.dp))
                        Text("Toque para abrir o YouTube", fontSize = 14.sp, color = Color.White.copy(0.75f))
                    }
                }
            }

            // ---- janela do mapa / trajeto (metade de baixo)
            CarWindow(
                Modifier.fillMaxWidth().weight(1f), "Mapa", Icons.Rounded.Map, Color(0xFF34C759),
                actions = {
                    if (route != null) {
                        WinText("Maps") {
                            val ok = runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(route)))
                                        .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.isSuccess
                            if (!ok) Toast.makeText(ctx, "Google Maps não encontrado", Toast.LENGTH_SHORT).show()
                        }
                        WinText("Waze") {
                            val ok = runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?navigate=yes&q=" + Uri.encode(route)))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.isSuccess
                            if (!ok) Toast.makeText(ctx, "Waze não encontrado", Toast.LENGTH_SHORT).show()
                        }
                        WinText("Parar") { CarState.set(ctx, null) }
                    }
                }
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.4f))) {
                        val r = route
                        if (r == null) {
                            Text(
                                "Digite o destino (com a cidade) e toque em Iniciar para ver o trajeto aqui.",
                                color = Color.White.copy(0.7f), fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp)
                            )
                        } else {
                            RouteMap(r, Modifier.fillMaxSize())
                        }
                    }
                }
            }
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
            RouteMap(r, Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
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
