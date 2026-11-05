package com.clean.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(val label: String, val pkg: String, val component: ComponentName)
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
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { IconCache.clear(); reload() }
    }

    init {
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
                    AppInfo(
                        it.loadLabel(pm).toString(),
                        it.activityInfo.packageName,
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name)
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
        }
    }

    private fun save() {
        val arr = JSONArray()
        folders.forEach { arr.put(JSONObject().put("name", it.name).put("apps", JSONArray(it.apps))) }
        prefs.edit().putString("folders", arr.toString()).putString("favs", JSONArray(favorites.toList()).toString()).apply()
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
