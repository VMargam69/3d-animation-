package com.example.engine.gl

import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.engine.model.EditorMode
import com.example.engine.model.IkLimb
import com.example.engine.model.Vec3
import kotlin.math.abs

@Composable
fun ViewportSurface(
    renderConfig: RenderConfig,
    isPlaying: Boolean,
    onOrbit: (deltaYaw: Float, deltaPitch: Float) -> Unit,
    onPan: (deltaX: Float, deltaY: Float) -> Unit,
    onZoom: (zoomFactor: Float) -> Unit,
    onSelectNodeOrBone: (nodeId: String?, boneId: String?) -> Unit,
    onSelectIkLimb: ((nodeId: String, limb: IkLimb) -> Unit)? = null,
    onDragIkHandle: ((nodeId: String, limb: IkLimb, targetWorldPos: Vec3) -> Unit)? = null,
    onDoubleTapFocus: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val renderer = remember { GlSceneRenderer() }
    var glViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val latestConfig by rememberUpdatedState(renderConfig)
    val latestOrbit by rememberUpdatedState(onOrbit)
    val latestPan by rememberUpdatedState(onPan)
    val latestZoom by rememberUpdatedState(onZoom)
    val latestSelect by rememberUpdatedState(onSelectNodeOrBone)
    val latestSelectIk by rememberUpdatedState(onSelectIkLimb)
    val latestDragIk by rememberUpdatedState(onDragIkHandle)
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

    var lastTapMs by remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("gl_3d_viewport")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val cfg = latestConfig

                    // Check if touch landed directly on a 3D IK diamond handle
                    val ikHit = if (cfg.ikEnabled && (cfg.editorMode == EditorMode.POSE || cfg.showSkeleton)) {
                        renderer.pickIkHandleAtScreenPoint(downPos.x, downPos.y)
                    } else null

                    var ikAnchorWorld: Vec3? = null
                    if (ikHit != null) {
                        latestSelectIk?.invoke(ikHit.first, ikHit.second)
                        ikAnchorWorld = renderer.getIkHandleWorldPos(ikHit.first, ikHit.second)
                    }

                    var totalMove = 0f
                    var prevSinglePos = downPos
                    var multiTouchUsed = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            multiTouchUsed = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (abs(zoom - 1f) > 0.004f) {
                                latestZoom(zoom)
                            }
                            if (pan != Offset.Zero) {
                                latestPan(pan.x * 0.0055f, pan.y * 0.0055f)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (!multiTouchUsed) {
                            val change = pressed.first()
                            val delta = change.position - prevSinglePos
                            prevSinglePos = change.position
                            totalMove += delta.getDistance()

                            if (delta != Offset.Zero) {
                                if (ikHit != null && ikAnchorWorld != null && latestDragIk != null) {
                                    // Direct 3D IK handle dragging!
                                    val nextWorld = renderer.projectScreenDragToWorldPoint(ikAnchorWorld, delta.x, delta.y)
                                    ikAnchorWorld = nextWorld
                                    latestDragIk?.invoke(ikHit.first, ikHit.second, nextWorld)
                                    change.consume()
                                } else if (totalMove > 6f) {
                                    latestOrbit(-delta.x * 0.34f, delta.y * 0.34f)
                                    change.consume()
                                }
                            }
                        }
                    }

                    // Handle tap / double-tap if the user didn't drag
                    if (!multiTouchUsed && totalMove <= 10f && ikHit == null) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapMs < 290L) {
                            lastTapMs = 0L
                            latestFocus?.invoke()
                        } else {
                            lastTapMs = now
                            val (hitNode, hitBone) = renderer.pickAtScreenPoint(downPos.x, downPos.y)
                            latestSelect(hitNode, hitBone)
                        }
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
