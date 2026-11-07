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

private fun launch(ctx: Context, i: Intent?, adjacent: Boolean): Boolean {
    i ?: return false
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or (if (adjacent) Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT else 0))
    return runCatching { ctx.startActivity(i) }.isSuccess
}

private fun youtubeIntent(ctx: Context): Intent? {
    val u = CarPrefs.ytUrl(ctx)
    return if (u.isBlank()) ctx.packageManager.getLaunchIntentForPackage(YT_PKG)
    else Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage(YT_PKG)
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
        if (!launch(app, youtubeIntent(app), false)) {
            Toast.makeText(app, "YouTube não encontrado", Toast.LENGTH_SHORT).show(); return
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
fun GpsChooserDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { navApps(ctx) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Qual app de GPS?") },
        text = {
            Column {
                Text(
                    if (apps.isEmpty()) "Nenhum app de GPS encontrado no celular."
                    else "Escolha uma vez e o Lume lembra. Dá para trocar em Ajustes ▸ Modo carro.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                apps.forEach { a ->
                    Text(a.label, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable { onPick(a.pkg) }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
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

/** Ligado ao botão do carro: escolhe o GPS na primeira vez e depois sempre abre a tela dividida. */
@Composable
fun CarSplitLayer(show: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var chooser by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        if (CarPrefs.gps(ctx) == null) chooser = true else { CarSplit.start(ctx); onDone() }
    }
    if (chooser) GpsChooserDialog(
        onPick = { CarPrefs.setGps(ctx, it); chooser = false; CarSplit.start(ctx); onDone() },
        onDismiss = { chooser = false; onDone() }
    )
}
