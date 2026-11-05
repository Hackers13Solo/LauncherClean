package com.clean.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Uma foto encontrada na web: miniatura (rápida) e imagem em tamanho completo. */
data class WebImg(val thumb: String, val full: String)

/**
 * Busca de fotos na web sem chave de API. Tenta várias fontes em sequência:
 * Bing Imagens (filtro "foto" + "vertical"), DuckDuckGo Imagens (filtro "foto") e Openverse (categoria "fotografia").
 * Se uma fonte falhar ou trouxer poucas fotos, a próxima completa a lista até chegar a 20.
 */
object WebImages {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun secure(u: String): String = if (u.startsWith("http://")) "https://" + u.removePrefix("http://") else u

    private fun open(url: String, referer: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
        if (referer != null) c.setRequestProperty("Referer", referer)
        return c
    }

    fun getText(url: String, referer: String? = null): String {
        val c = open(url, referer)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun getBytes(url: String, maxBytes: Int): ByteArray {
        val c = open(url, null)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            if (c.contentLength > maxBytes) throw IOException("arquivo grande demais")
            val out = ByteArrayOutputStream()
            c.inputStream.use { ins ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException("arquivo grande demais")
                }
            }
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }

    private fun looksLikePhoto(u: String): Boolean {
        val l = u.lowercase().substringBefore('?')
        return !(l.endsWith(".gif") || l.endsWith(".svg") || l.endsWith(".ico"))
    }

    // ---- Bing Imagens: qft = só fotos + orientação vertical
    private fun bing(q: String): List<WebImg> {
        val url = "https://www.bing.com/images/async?q=${enc(q)}&first=0&count=70&mmasync=1&adlt=strict" +
            "&qft=+filterui:photo-photo+filterui:aspect-tall"
        val html = getText(url, "https://www.bing.com/")
        val out = ArrayList<WebImg>()
        Regex("\\sm=\"(\\{[^\"]*\\})\"").findAll(html).forEach { m ->
            val raw = m.groupValues[1].replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'")
            runCatching {
                val o = JSONObject(raw)
                val full = o.optString("murl")
                val th = o.optString("turl")
                if (full.startsWith("http") && th.startsWith("http") && looksLikePhoto(full))
                    out.add(WebImg(th, secure(full)))
            }
        }
        return out
    }

    // ---- DuckDuckGo Imagens: f = ,,,type:photo,layout:Tall,
    private fun ddg(q: String): List<WebImg> {
        val home = getText("https://duckduckgo.com/?q=${enc(q)}&iax=images&ia=images")
        val vqd = Regex("vqd=[\"']?([0-9-]+)").find(home)?.groupValues?.get(1)
            ?: throw IOException("sem token")
        val f = enc(",,,type:photo,layout:Tall,")
        val json = getText(
            "https://duckduckgo.com/i.js?l=wt-wt&o=json&q=${enc(q)}&vqd=$vqd&f=$f&p=1",
            "https://duckduckgo.com/"
        )
        val arr = JSONObject(json).getJSONArray("results")
        val out = ArrayList<WebImg>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val full = o.optString("image")
            val th = o.optString("thumbnail").ifBlank { full }
            if (full.startsWith("http") && looksLikePhoto(full)) out.add(WebImg(secure(th), secure(full)))
        }
        return out
    }

    // ---- Openverse: categoria "photograph"
    private fun openverse(q: String): List<WebImg> {
        val json = getText(
            "https://api.openverse.org/v1/images/?q=${enc(q)}&page_size=20&category=photograph&aspect_ratio=tall&mature=false"
        )
        val arr = JSONObject(json).getJSONArray("results")
        val out = ArrayList<WebImg>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val full = o.optString("url")
            val th = o.optString("thumbnail").ifBlank { full }
            if (full.startsWith("http") && looksLikePhoto(full)) out.add(WebImg(secure(th), secure(full)))
        }
        return out
    }

    /** Até 20 fotos para o termo digitado. Lança exceção se nenhuma fonte responder. */
    fun search(q: String): List<WebImg> {
        val all = ArrayList<WebImg>()
        var lastError: Throwable? = null
        val sources: List<() -> List<WebImg>> = listOf(
            { bing("$q wallpaper") },
            { ddg("$q wallpaper") },
            { openverse(q) }
        )
        for (s in sources) {
            try {
                all.addAll(s())
            } catch (e: Throwable) {
                lastError = e
            }
            if (all.distinctBy { it.full }.size >= 20) break
        }
        val list = all.distinctBy { it.full }.take(20)
        if (list.isEmpty() && lastError != null) throw lastError
        return list
    }
}

// ------------------------------------------------------------ bitmaps / cache
fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
    if (b.outWidth <= 0 || b.outHeight <= 0) return null
    var s = 1
    while (b.outWidth / s > maxDim || b.outHeight / s > maxDim) s *= 2
    val o = BitmapFactory.Options().apply { inSampleSize = s }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
}

object NetCache {
    private val lru = LruCache<String, ImageBitmap>(90)
    fun get(url: String): ImageBitmap? = lru.get(url)
    fun put(url: String, b: ImageBitmap) { lru.put(url, b) }
}

suspend fun loadThumb(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    NetCache.get(url) ?: runCatching {
        val bytes = WebImages.getBytes(url, 6_000_000)
        decodeSampled(bytes, 720)?.asImageBitmap()
    }.getOrNull()?.also { NetCache.put(url, it) }
}

/** Baixa a foto em tamanho completo; se falhar, usa a miniatura como plano B. */
suspend fun downloadWallpaper(img: WebImg): Bitmap? = withContext(Dispatchers.IO) {
    for (u in listOf(img.full, img.thumb)) {
        val bmp = runCatching { decodeSampled(WebImages.getBytes(u, 30_000_000), 2400) }.getOrNull()
        if (bmp != null && minOf(bmp.width, bmp.height) >= 200) return@withContext bmp
    }
    null
}

@Composable
fun NetImage(url: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    var status by remember(url) { mutableIntStateOf(if (NetCache.get(url) != null) 1 else 0) } // 0 carregando, 1 ok, 2 erro
    LaunchedEffect(url) {
        if (status == 0) status = if (loadThumb(url) != null) 1 else 2
    }
    val bmp = if (status == 1) NetCache.get(url) else null
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            bmp != null -> Image(bmp, null, Modifier.fillMaxSize(), contentScale = scale)
            status == 2 -> Icon(Icons.Rounded.BrokenImage, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(28.dp))
            else -> CircularProgressIndicator(
                color = Color.White.copy(0.5f), strokeWidth = 2.dp, modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Cor vibrante dominante da imagem (usada na "cor de destaque automática"). Null se for quase cinza. */
fun dominantAccent(src: Bitmap): Color? {
    val s = Bitmap.createScaledBitmap(src, 32, 32, true)
    val px = IntArray(32 * 32)
    s.getPixels(px, 0, 32, 0, 0, 32, 32)
    val hsv = FloatArray(3)
    val wSum = FloatArray(12)
    val rS = FloatArray(12)
    val gS = FloatArray(12)
    val bS = FloatArray(12)
    for (p in px) {
        android.graphics.Color.colorToHSV(p, hsv)
        if (hsv[1] < 0.28f || hsv[2] < 0.30f) continue
        val wgt = hsv[1] * hsv[2]
        val k = (hsv[0] / 30f).toInt().coerceIn(0, 11)
        wSum[k] += wgt
        rS[k] += android.graphics.Color.red(p) * wgt
        gS[k] += android.graphics.Color.green(p) * wgt
        bS[k] += android.graphics.Color.blue(p) * wgt
    }
    var best = -1
    for (i in 0..11) if (best < 0 || wSum[i] > wSum[best]) best = i
    if (best < 0 || wSum[best] < 1.5f) return null
    val r = (rS[best] / wSum[best]).toInt()
    val g = (gS[best] / wSum[best]).toInt()
    val b = (bS[best] / wSum[best]).toInt()
    android.graphics.Color.colorToHSV(android.graphics.Color.rgb(r, g, b), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.65f)
    hsv[2] = hsv[2].coerceAtLeast(0.90f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
