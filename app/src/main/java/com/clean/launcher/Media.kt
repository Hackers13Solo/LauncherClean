package com.clean.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Serviço usado para (1) ler a mídia tocando e (2) contar notificações. Requer acesso às notificações. */
class NotifListener : NotificationListenerService() {
    companion object {
        var count by mutableIntStateOf(0)
    }

    private fun update() {
        count = runCatching { activeNotifications.count { !it.isOngoing } }.getOrDefault(0)
    }

    override fun onListenerConnected() = update()
    override fun onListenerDisconnected() { count = 0 }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = update()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = update()
}

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val pkg: String
)

class MediaWatcher(private val ctx: Context) {
    var info by mutableStateOf<MediaInfo?>(null)
        private set
    var hasAccess by mutableStateOf(false)
        private set

    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val cn = ComponentName(ctx, NotifListener::class.java)
    private var controller: MediaController? = null

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
    }
    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn)
            pick(msm.getActiveSessions(cn))
            hasAccess = true
        } catch (e: SecurityException) {
            hasAccess = false
            info = null
        }
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(cb)
        controller = null
    }

    private fun pick(list: List<MediaController>?) {
        controller?.unregisterCallback(cb)
        controller = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        controller?.registerCallback(cb)
        refresh()
    }

    private fun refresh() {
        val c = controller
        val m = c?.metadata
        if (c == null || m == null) { info = null; return }
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) { info = null; return }
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
        info = MediaInfo(
            title, artist, art?.asImageBitmap(),
            c.playbackState?.state == PlaybackState.STATE_PLAYING, c.packageName
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}
