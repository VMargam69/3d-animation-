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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ViewportSurface(
    renderConfig: RenderConfig,
    isPlaying: Boolean,
    onOrbit: (deltaYaw: Float, deltaPitch: Float) -> Unit,
    onPan: (deltaX: Float, deltaY: Float) -> Unit,
    onZoom: (zoomFactor: Float) -> Unit,
    onSelectNodeOrBone: (nodeId: String?, boneId: String?) -> Unit,
    onDoubleTapFocus: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val renderer = remember { GlSceneRenderer() }
    var glViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val latestOrbit by rememberUpdatedState(onOrbit)
    val latestPan by rememberUpdatedState(onPan)
    val latestZoom by rememberUpdatedState(onZoom)
    val latestSelect by rememberUpdatedState(onSelectNodeOrBone)
    val latestFocus by rememberUpdatedState(onDoubleTapFocus)

    LaunchedEffect(renderConfig, isPlaying) {
        renderer.config = renderConfig
        glViewRef?.let { view ->
            view.renderMode = if (isPlaying) {
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
            view.requestRender()
        }
    }

    // Properly synchronize GLSurfaceView with Android lifecycle so returning from background never stalls
    DisposableEffect(lifecycleOwner, glViewRef) {
        val view = glViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    view?.onResume()
                    view?.requestRender()
                }
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            glViewRef?.queueEvent { renderer.releaseGl() }
            glViewRef?.onPause()
            glViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("gl_3d_viewport")
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val (hitNode, hitBone) = renderer.pickAtScreenPoint(offset.x, offset.y)
                        latestSelect(hitNode, hitBone)
                    },
                    onDoubleTap = {
                        latestFocus?.invoke()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (kotlin.math.abs(zoom - 1f) > 0.008f) {
                        latestZoom(zoom)
                        latestPan(pan.x * 0.0055f, pan.y * 0.0055f)
                    } else if (pan.x != 0f || pan.y != 0f) {
                        latestOrbit(-pan.x * 0.34f, pan.y * 0.34f)
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
                    preserveEGLContextOnPause = true
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
