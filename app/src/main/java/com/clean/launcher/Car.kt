package com.clean.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val YT_PKG = "com.google.android.youtube"

/** Preferências do modo carro. */
object CarPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun gps(ctx: Context): String? = p(ctx).getString("carGps", null)?.ifBlank { null }
    fun setGps(ctx: Context, pkg: String?) { p(ctx).edit().putString("carGps", pkg ?: "").apply() }
    /** Página do YouTube. Vazio = abre o YouTube normal, sem link direto. */
    fun ytUrl(ctx: Context): String = p(ctx).getString("carYt", "") ?: ""
    fun setYtUrl(ctx: Context, u: String) { p(ctx).edit().putString("carYt", u).apply() }
    /** App de cima (YouTube). Se nada foi escolhido, usa o YouTube oficial ou outro app com "youtube" no nome do pacote. */
    fun top(ctx: Context): String? {
        p(ctx).getString("carTop", null)?.ifBlank { null }?.let { if (installed(ctx, it)) return it }
        if (installed(ctx, YT_PKG)) return YT_PKG
        return allApps(ctx).firstOrNull { it.pkg.contains("youtube", true) }?.pkg
    }
    fun setTop(ctx: Context, pkg: String?) { p(ctx).edit().putString("carTop", pkg ?: "").apply() }
    fun topLabel(ctx: Context): String? = top(ctx)?.let { label(ctx, it) }
    fun installed(ctx: Context, pkg: String) = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)
    fun label(ctx: Context, pkg: String): String? =
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    fun gpsLabel(ctx: Context): String? = gps(ctx)?.let { pkg ->
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }
}

/** Serviço de acessibilidade usado só para pedir ao Android que divida a tela (não lê nada da tela). */
class SplitService : AccessibilityService() {
    override fun onServiceConnected() { inst = this }
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { if (inst === this) inst = null; return super.onUnbind(intent) }
    companion object { @Volatile var inst: SplitService? = null }
}

class NavApp(val label: String, val pkg: String)

/** Apps de GPS instalados: Waze, Google Maps e qualquer app que abra links de mapa. */
fun navApps(ctx: Context): List<NavApp> {
    val pm = ctx.packageManager
    val found = LinkedHashMap<String, String>()
    fun add(pkg: String) {
        if (pkg == ctx.packageName) return
        runCatching { found[pkg] = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
    }
    add("com.waze")
    add("com.google.android.apps.maps")
    runCatching {
        pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=a")), 0)
            .forEach { add(it.activityInfo.packageName) }
    }
    return found.map { NavApp(it.value, it.key) }
}

/** Todos os apps com ícone no celular; os que têm "youtube" no nome vêm primeiro. */
fun allApps(ctx: Context): List<NavApp> {
    val pm = ctx.packageManager
    val li = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching { pm.queryIntentActivities(li, 0) }.getOrDefault(emptyList())
        .map { NavApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedWith(compareBy({ !(it.pkg.contains("youtube", true) || it.label.contains("youtube", true)) }, { it.label.lowercase() }))
}

private fun launch(ctx: Context, i: Intent?, adjacent: Boolean): Boolean {
    i ?: return false
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or (if (adjacent) Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT else 0))
    return runCatching { ctx.startActivity(i) }.isSuccess
}

/** Abre o app de cima. Com link salvo, abre direto nele; se o app não aceitar o link, abre normal. */
private fun launchTop(ctx: Context): Boolean {
    val pkg = CarPrefs.top(ctx) ?: return false
    val u = CarPrefs.ytUrl(ctx)
    if (u.isNotBlank() && launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage(pkg), false)) return true
    return launch(ctx, ctx.packageManager.getLaunchIntentForPackage(pkg), false)
}

/**
 * Tela dividida de verdade: YouTube em cima, GPS embaixo.
 * 1) abre o YouTube; 2) o serviço de acessibilidade pede a divisão de tela (o YouTube vai para cima);
 * 3) quando o Lume reaparece na metade de baixo, ele abre o GPS ali (o Android só deixa abrir o GPS
 * a partir de um app que está visível).
 */
object CarSplit {
    private val h = Handler(Looper.getMainLooper())
    private var waitingSince = 0L

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        val gps = CarPrefs.gps(app)
        if (gps == null) { Toast.makeText(app, "Escolha o app de GPS primeiro", Toast.LENGTH_SHORT).show(); return }
        if (!launchTop(app)) {
            Toast.makeText(app, "App do YouTube não encontrado. Escolha outro em Ajustes ▸ Modo carro", Toast.LENGTH_LONG).show(); return
        }
        val svc = SplitService.inst
        if (svc == null) {
            Toast.makeText(app, "Para dividir a tela sozinho, ative o Lume em Acessibilidade (Ajustes ▸ Modo carro)", Toast.LENGTH_LONG).show()
            h.postDelayed({ openGps(app) }, 1200)
            return
        }
        h.postDelayed({
            waitingSince = SystemClock.elapsedRealtime()
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            // se o Lume não reaparecer sozinho, tenta abrir o GPS mesmo assim
            h.postDelayed({ if (waitingSince != 0L) openGps(app) }, 3500)
        }, 1500)
    }

    private fun openGps(ctx: Context) {
        waitingSince = 0L
        val gps = CarPrefs.gps(ctx) ?: return
        if (!launch(ctx, ctx.packageManager.getLaunchIntentForPackage(gps), true))
            Toast.makeText(ctx, "App de GPS não encontrado. Escolha outro em Ajustes ▸ Modo carro", Toast.LENGTH_LONG).show()
    }

    /** Chamado pela tela inicial quando ela fica visível. */
    fun onLumeShown(ctx: Context) {
        if (waitingSince != 0L && SystemClock.elapsedRealtime() - waitingSince < 8000) openGps(ctx)
    }
}

@Composable
fun AppPickerDialog(title: String, hint: String, apps: List<NavApp>, searchable: Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, apps) { if (q.isBlank()) apps else apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(if (apps.isEmpty()) "Nenhum app encontrado no celular." else hint, fontSize = 13.sp)
                if (searchable) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Buscar app") })
                }
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(shown.size) { n ->
                        val a = shown[n]
                        Text(a.label, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(a.pkg) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun GpsChooserDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AppPickerDialog(
        "Qual app de GPS?", "Escolha uma vez e o Lume lembra. Dá para trocar em Ajustes ▸ Modo carro.",
        remember { navApps(ctx) }, false, onPick, onDismiss
    )
}

@Composable
fun TopAppChooserDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AppPickerDialog(
        "Qual app abre em cima?", "O Lume não achou o YouTube. Escolha o app de vídeo que você usa (os com \"YouTube\" no nome vêm primeiro).",
        remember { allApps(ctx) }, true, onPick, onDismiss
    )
}

@Composable
fun YtUrlDialog(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(CarPrefs.ytUrl(ctx)) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Página do YouTube") },
        text = {
            Column {
                Text(
                    "Deixe vazio para abrir o YouTube normal. Com um link (playlist, canal ou vídeo), o app abre direto nessa página.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var u = text.trim()
                if (u.isNotEmpty() && !u.startsWith("http")) u = "https://$u"
                CarPrefs.setYtUrl(ctx, u)
                onDone()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } }
    )
}

/** Ligado ao botão do carro: pergunta o que falta (GPS e app de cima) e depois sempre abre a tela dividida. */
@Composable
fun CarSplitLayer(show: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(0) }   // 0 nada · 1 escolhe GPS · 2 escolhe app de cima
    fun next() {
        step = when {
            CarPrefs.gps(ctx) == null -> 1
            CarPrefs.top(ctx) == null -> 2
            else -> { CarSplit.start(ctx); onDone(); 0 }
        }
    }
    LaunchedEffect(show) { if (show) next() }
    if (step == 1) GpsChooserDialog(onPick = { CarPrefs.setGps(ctx, it); next() }, onDismiss = { step = 0; onDone() })
    if (step == 2) TopAppChooserDialog(onPick = { CarPrefs.setTop(ctx, it); next() }, onDismiss = { step = 0; onDone() })
}
