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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    var gridMode by mutableStateOf(prefs.getBoolean("grid", false))
    var iconShape by mutableIntStateOf(prefs.getInt("shape", 0)) // 0 original, 1 redondo, 2 arredondado
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true))

    // novidades da v1.1
    var accentIdx by mutableIntStateOf(prefs.getInt("accent", 0))
    var fxMode by mutableIntStateOf(prefs.getInt("fx", 3))          // 0 nenhum, 1 partículas, 2 aurora, 3 ambos
    var edgeFx by mutableStateOf(prefs.getBoolean("edgeFx", true))   // desvanece/encolhe linhas nas bordas
    var bigClock by mutableStateOf(prefs.getBoolean("bigClock", true))
    var quickActions by mutableStateOf(prefs.getBoolean("quick", true))
    var use24h by mutableStateOf(prefs.getBoolean("h24", true))
    var iconSize by mutableFloatStateOf(prefs.getFloat("iconSize", 52f))
    var textSize by mutableFloatStateOf(prefs.getFloat("textSize", 26f))
    var newBadge by mutableStateOf(prefs.getBoolean("newBadge", true))

    fun saveSettings() {
        prefs.edit()
            .putBoolean("customWp", useCustomWallpaper).putFloat("dim", dim).putFloat("blur", blur)
            .putBoolean("hideStatus", hideStatus).putBoolean("hideNav", hideNav)
            .putBoolean("grid", gridMode).putInt("shape", iconShape).putBoolean("haptics", haptics)
            .putInt("accent", accentIdx).putInt("fx", fxMode).putBoolean("edgeFx", edgeFx)
            .putBoolean("bigClock", bigClock).putBoolean("quick", quickActions).putBoolean("h24", use24h)
            .putFloat("iconSize", iconSize).putFloat("textSize", textSize).putBoolean("newBadge", newBadge)
            .apply()
    }

    fun setAccent(i: Int) {
        accentIdx = i.coerceIn(0, AccentColors.lastIndex)
        Accent = AccentColors[accentIdx]
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
                while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) {
                    File(app.filesDir, "wallpaper.jpg").outputStream().use {
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                    }
                    useCustomWallpaper = true
                    wallpaperVersion++
                    saveSettings()
                }
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
        Accent = AccentColors[accentIdx.coerceIn(0, AccentColors.lastIndex)]
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
