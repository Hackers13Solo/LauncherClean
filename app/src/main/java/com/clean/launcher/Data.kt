package com.clean.launcher

import android.app.ActivityOptions
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(val label: String, val pkg: String, val component: ComponentName, val installed: Long = 0L)
data class Folder(val name: String, val apps: List<String>)

fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

object IconCache {
    private val map = ConcurrentHashMap<String, ImageBitmap>()
    fun get(pkg: String): ImageBitmap? = map[pkg]
    fun load(ctx: Context, pkg: String): ImageBitmap? =
        map[pkg] ?: runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
        }.getOrNull()?.also { map[pkg] = it }
    fun clear() = map.clear()
}

fun launchApp(ctx: Context, app: AppInfo) {
    runCatching {
        val anim = ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out)
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            anim.toBundle()
        )
    }
}

fun launchPkg(ctx: Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
        runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun openAppInfo(ctx: Context, pkg: String) = runCatching {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun uninstallApp(ctx: Context, pkg: String) = runCatching {
    ctx.startActivity(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun openListenerSettings(ctx: Context) = runCatching {
    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

class LauncherVM(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    val folders = mutableStateListOf<Folder>()
    val favorites = mutableStateListOf<String>()
    var homeTick by mutableIntStateOf(0)

    // apps ocultos e contagem de uso ("mais usados")
    val hidden = mutableStateListOf<String>()
    val usage = mutableStateMapOf<String, Int>()

    // ajustes
    var useCustomWallpaper by mutableStateOf(prefs.getBoolean("customWp", false))
    var wallpaperVersion by mutableIntStateOf(0)
    var dim by mutableFloatStateOf(prefs.getFloat("dim", 0.35f))
    var blur by mutableFloatStateOf(prefs.getFloat("blur", 0f))
    var hideStatus by mutableStateOf(prefs.getBoolean("hideStatus", true))
    var hideNav by mutableStateOf(prefs.getBoolean("hideNav", false))
    // modelo da tela inicial: 0 clássico, 1 grade, 2 limpo, 3 compacto (quem usava "grade" continua com grade)
    var layoutMode by mutableIntStateOf(prefs.getInt("layout", if (prefs.getBoolean("grid", false)) 1 else 0))
    var iconShape by mutableIntStateOf(prefs.getInt("shape", 0)) // 0 original, 1 redondo, 2 arredondado
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true))

    // novidades da v1.1
    var accentIdx by mutableIntStateOf(prefs.getInt("accent", 0))
    // efeitos de fundo em bits: 1 partículas, 2 aurora, 4 neve, 8 chuva. Padrão: só partículas (aurora desligada)
    var fxMask by mutableIntStateOf(loadFx())
    var edgeFx by mutableStateOf(prefs.getBoolean("edgeFx", true))   // desvanece/encolhe linhas nas bordas
    var bigClock by mutableStateOf(prefs.getBoolean("bigClock", true))
    var quickActions by mutableStateOf(prefs.getBoolean("quick", true))
    var use24h by mutableStateOf(prefs.getBoolean("h24", true))
    var iconSize by mutableFloatStateOf(prefs.getFloat("iconSize", 52f))
    var textSize by mutableFloatStateOf(prefs.getFloat("textSize", 26f))
    var newBadge by mutableStateOf(prefs.getBoolean("newBadge", true))

    // novidades da v1.2
    var wpAsked by mutableStateOf(prefs.getBoolean("wpAsked2", false))   // já mostrou a tela de papel de parede?
    var autoAccent by mutableStateOf(prefs.getBoolean("autoAccent", false))
    var accentCustom by mutableIntStateOf(prefs.getInt("accentCustom", AccentColors[0].toArgb()))
    var userName by mutableStateOf(prefs.getString("userName", "") ?: "")
    var showMostUsed by mutableStateOf(prefs.getBoolean("showMostUsed", true))
    var rowPad by mutableFloatStateOf(prefs.getFloat("rowPad", 9f))

    // novidades da v1.3
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded13", false))   // já passou pelas boas-vindas?

    /** Padrão novo: só partículas. Na atualização para a v1.3 a aurora é desligada uma única vez. */
    private fun loadFx(): Int {
        var m = prefs.getInt("fx", 1)
        if (!prefs.getBoolean("fxMig13", false)) {
            m = m and 2.inv()
            prefs.edit().putInt("fx", m).putBoolean("fxMig13", true).apply()
        }
        return m
    }

    fun saveSettings() {
        prefs.edit()
            .putBoolean("customWp", useCustomWallpaper).putFloat("dim", dim).putFloat("blur", blur)
            .putBoolean("hideStatus", hideStatus).putBoolean("hideNav", hideNav)
            .putInt("layout", layoutMode).putInt("shape", iconShape).putBoolean("haptics", haptics)
            .putInt("accent", accentIdx).putInt("fx", fxMask).putBoolean("edgeFx", edgeFx)
            .putBoolean("bigClock", bigClock).putBoolean("quick", quickActions).putBoolean("h24", use24h)
            .putFloat("iconSize", iconSize).putFloat("textSize", textSize).putBoolean("newBadge", newBadge)
            .putBoolean("wpAsked2", wpAsked).putBoolean("autoAccent", autoAccent).putInt("accentCustom", accentCustom)
            .putString("userName", userName).putBoolean("showMostUsed", showMostUsed).putFloat("rowPad", rowPad)
            .putBoolean("onboarded13", onboarded)
            .apply()
    }

    fun setLayout(m: Int) {
        layoutMode = m.coerceIn(0, 3)
        saveSettings()
    }

    fun finishOnboarding() {
        onboarded = true
        wpAsked = true
        saveSettings()
    }

    fun replayOnboarding() {
        onboarded = false
        saveSettings()
    }

    fun markWpAsked() {
        wpAsked = true
        saveSettings()
    }

    /** Liga/desliga um efeito de fundo (bit) sem mexer nos outros. */
    fun setFx(bit: Int, on: Boolean) {
        fxMask = if (on) fxMask or bit else fxMask and bit.inv()
        saveSettings()
    }

    /** Escolhe uma das cores prontas (desliga a cor automática). */
    fun setAccent(i: Int) {
        accentIdx = i.coerceIn(0, AccentColors.lastIndex)
        Accent = AccentColors[accentIdx]
        autoAccent = false
        saveSettings()
    }

    /** Cor própria (usada pela cor automática do papel de parede). */
    private fun setCustomAccent(c: Color) {
        accentIdx = -1
        accentCustom = c.toArgb()
        Accent = c
        saveSettings()
    }

    /** Recalcula a cor de destaque a partir do papel de parede atual. */
    fun refreshAutoAccent() {
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(app.filesDir, "wallpaper.jpg")
            if (!f.exists()) return@launch
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = BitmapFactory.decodeFile(f.path, opts) ?: return@launch
            dominantAccent(bmp)?.let { setCustomAccent(it) }
        }
    }

    /** Salva a imagem como papel de parede do launcher (chamar fora da thread principal). */
    fun applyWallpaperBitmap(bmp: Bitmap) {
        File(app.filesDir, "wallpaper.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 96, it) }
        useCustomWallpaper = true
        wallpaperVersion++
        wpAsked = true
        if (autoAccent) dominantAccent(bmp)?.let { setCustomAccent(it) }
        saveSettings()
    }

    fun resetUsage() {
        usage.clear()
        save()
    }

    fun setWallpaper(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cr = app.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 3000 || bounds.outHeight / sample > 3000) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) applyWallpaperBitmap(bmp)
            }
        }
    }

    fun recordLaunch(pkg: String) {
        usage[pkg] = (usage[pkg] ?: 0) + 1
        save()
    }

    fun toggleHidden(pkg: String) {
        if (!hidden.remove(pkg)) hidden.add(pkg)
        save()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { IconCache.clear(); reload() }
    }

    init {
        Accent = if (accentIdx in AccentColors.indices) AccentColors[accentIdx] else Color(accentCustom)
        loadPrefs()
        reload()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, receiver, f, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(receiver) }
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = app.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            apps = pm.queryIntentActivities(i, 0)
                .map {
                    val pkg = it.activityInfo.packageName
                    AppInfo(
                        it.loadLabel(pm).toString(),
                        pkg,
                        ComponentName(pkg, it.activityInfo.name),
                        runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
                    )
                }
                .filter { it.pkg != app.packageName }
                .distinctBy { it.pkg }
                .sortedBy { norm(it.label) }
        }
    }

    // ---------- persistência ----------
    private fun loadPrefs() {
        runCatching {
            val arr = JSONArray(prefs.getString("folders", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = o.getJSONArray("apps")
                folders.add(Folder(o.getString("name"), List(a.length()) { a.getString(it) }))
            }
            val fav = JSONArray(prefs.getString("favs", "[]"))
            for (i in 0 until fav.length()) favorites.add(fav.getString(i))
            val hid = JSONArray(prefs.getString("hidden", "[]"))
            for (i in 0 until hid.length()) hidden.add(hid.getString(i))
            val u = JSONObject(prefs.getString("usage", "{}"))
            u.keys().forEach { k -> usage[k] = u.getInt(k) }
        }
    }

    private fun save() {
        val arr = JSONArray()
        folders.forEach { arr.put(JSONObject().put("name", it.name).put("apps", JSONArray(it.apps))) }
        val u = JSONObject()
        usage.forEach { (k, v) -> u.put(k, v) }
        prefs.edit()
            .putString("folders", arr.toString())
            .putString("favs", JSONArray(favorites.toList()).toString())
            .putString("hidden", JSONArray(hidden.toList()).toString())
            .putString("usage", u.toString())
            .apply()
    }

    // ---------- favoritos ----------
    fun toggleFav(pkg: String) {
        if (!favorites.remove(pkg)) favorites.add(pkg)
        save()
    }

    // ---------- pastas ----------
    fun createFolder(name: String, firstPkg: String? = null) {
        if (name.isBlank() || folders.any { it.name == name }) return
        folders.add(Folder(name, listOfNotNull(firstPkg)))
        save()
    }

    fun addToFolder(name: String, pkg: String) {
        val i = folders.indexOfFirst { it.name == name }
        if (i < 0 || pkg in folders[i].apps) return
        folders[i] = folders[i].copy(apps = folders[i].apps + pkg)
        save()
    }

    fun removeFromFolder(name: String, pkg: String) {
        val i = folders.indexOfFirst { it.name == name }
        if (i < 0) return
        folders[i] = folders[i].copy(apps = folders[i].apps - pkg)
        save()
    }

    fun renameFolder(old: String, new: String) {
        val i = folders.indexOfFirst { it.name == old }
        if (i < 0 || new.isBlank() || folders.any { it.name == new }) return
        folders[i] = folders[i].copy(name = new)
        save()
    }

    fun deleteFolder(name: String) {
        folders.removeAll { it.name == name }
        save()
    }
}
