package com.balltrack.ai.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Plays a local violation clip in slow motion (0.3x speed), looping, with an
 * optional zoom focused on [zoomFocusPoint] (normalized 0-1 x,y from the
 * original frame) to draw attention to the flagged moment.
 *
 * Local-only: takes a local file path, never a URL. Player is released on
 * lifecycle stop/destroy to avoid leaking decoder resources.
 */
@Composable
fun SlowMoZoomPlayer(
    localClipPath: String,
    zoomFocusPoint: Pair<Float, Float>?,
    playbackSpeed: Float = 0.3f,
    zoomScale: Float = 1.8f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(localClipPath))))
            playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            playbackParameters = PlaybackParameters(playbackSpeed)
            prepare()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                player = exoPlayer

                // width/height are 0 until the view is actually laid out, so the
                // zoom pivot must be set after layout, not at construction time.
                if (zoomFocusPoint != null) {
                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        v.scaleX = zoomScale
                        v.scaleY = zoomScale
                        v.pivotX = zoomFocusPoint.first * v.width
                        v.pivotY = zoomFocusPoint.second * v.height
                    }
                }
            }
        }
    )
}
