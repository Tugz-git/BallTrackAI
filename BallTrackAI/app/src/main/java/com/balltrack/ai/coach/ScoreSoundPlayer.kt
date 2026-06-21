package com.balltrack.ai.coach

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.edit

class ScoreSoundPlayer(private val context: Context) {
    private val prefs = context.getSharedPreferences("sound_prefs", Context.MODE_PRIVATE)
    private var player: MediaPlayer? = null

    enum class SoundEvent { MAKE, MISS, STREAK, SESSION_END, MENU_CLICK }

    fun play(event: SoundEvent) {
        if (!isEnabled(event)) return
        release()
        val customUri = customUri(event)
        player = if (customUri != null) {
            MediaPlayer().apply { setDataSource(context, Uri.parse(customUri)); prepare() }
        } else {
            val res = defaultRes(event) ?: return
            MediaPlayer.create(context, res)
        }
        player?.setOnCompletionListener { release() }
        player?.start()
    }

    fun setCustomSound(event: SoundEvent, uri: Uri?) {
        prefs.edit {
            if (uri == null) remove(uriKey(event))
            else {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                putString(uriKey(event), uri.toString())
            }
        }
    }

    fun customUri(event: SoundEvent): String? = prefs.getString(uriKey(event), null)
    fun isEnabled(event: SoundEvent) = prefs.getBoolean(enabledKey(event), event != SoundEvent.MISS)
    fun setEnabled(event: SoundEvent, v: Boolean) = prefs.edit { putBoolean(enabledKey(event), v) }
    fun clearCustom(event: SoundEvent) = setCustomSound(event, null)

    private fun defaultRes(event: SoundEvent): Int? = when (event) {
        SoundEvent.MAKE -> com.balltrack.ai.R.raw.sound_make
        SoundEvent.MISS -> com.balltrack.ai.R.raw.sound_miss
        SoundEvent.STREAK -> com.balltrack.ai.R.raw.sound_streak
        SoundEvent.SESSION_END -> com.balltrack.ai.R.raw.sound_session_end
        SoundEvent.MENU_CLICK -> com.balltrack.ai.R.raw.sound_menu_click
    }

    private fun release() { player?.let { if (it.isPlaying) it.stop(); it.release() }; player = null }
    private fun uriKey(e: SoundEvent) = "uri_${e.name}"
    private fun enabledKey(e: SoundEvent) = "enabled_${e.name}"
    fun shutdown() = release()
}
