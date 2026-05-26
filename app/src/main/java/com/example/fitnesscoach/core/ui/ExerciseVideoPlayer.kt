package com.example.fitnesscoach.core.ui

import android.net.Uri
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun ExerciseVideoPlayer(
    videoResId: Int,
    active: Boolean = true,
    showController: Boolean = true,
    useTextureView: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoResId) {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/$videoResId")
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(active, exoPlayer) {
        if (active) {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            exoPlayer.pause()
            exoPlayer.clearVideoSurface()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.pause()
            exoPlayer.clearVideoSurface()
            exoPlayer.clearVideoTextureView(null)
            exoPlayer.release()
        }
    }

    if (useTextureView) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    if (active) {
                        exoPlayer.setVideoTextureView(this)
                    }
                }
            },
            update = { textureView ->
                if (active) {
                    exoPlayer.setVideoTextureView(textureView)
                } else {
                    exoPlayer.clearVideoTextureView(textureView)
                }
            },
            modifier = modifier
        )
    } else {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = if (active) exoPlayer else null
                    useController = showController
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = { playerView ->
                playerView.player = if (active) exoPlayer else null
                playerView.useController = showController
            },
            modifier = modifier
        )
    }
}
