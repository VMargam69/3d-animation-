package com.example.engine.gl

import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ViewportSurface(
    renderConfig: RenderConfig,
    isPlaying: Boolean,
    onOrbit: (deltaYaw: Float, deltaPitch: Float) -> Unit,
    onPan: (deltaX: Float, deltaY: Float) -> Unit,
    onZoom: (zoomFactor: Float) -> Unit,
    onSelectNodeOrBone: (nodeId: String?, boneId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val renderer = remember { GlSceneRenderer() }
    var glViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    val latestOrbit by rememberUpdatedState(onOrbit)
    val latestPan by rememberUpdatedState(onPan)
    val latestZoom by rememberUpdatedState(onZoom)
    val latestSelect by rememberUpdatedState(onSelectNodeOrBone)

    LaunchedEffect(renderConfig, isPlaying) {
        renderer.config = renderConfig
        glViewRef?.let { view ->
            // Thermal & battery optimization: render on-demand when paused, continuous only while playing
            view.renderMode = if (isPlaying) {
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
            view.requestRender()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            glViewRef?.onPause()
            glViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("gl_3d_viewport")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val (hitNode, hitBone) = renderer.pickAtScreenPoint(offset.x, offset.y)
                    latestSelect(hitNode, hitBone)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (kotlin.math.abs(zoom - 1f) > 0.008f) {
                        latestZoom(zoom)
                        latestPan(pan.x * 0.006f, pan.y * 0.006f)
                    } else if (pan.x != 0f || pan.y != 0f) {
                        latestOrbit(-pan.x * 0.35f, pan.y * 0.35f)
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    renderer.config = renderConfig
                    glViewRef = this
                }
            },
            update = { view ->
                renderer.config = renderConfig
                view.requestRender()
            }
        )
    }
}
