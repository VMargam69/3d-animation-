package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.AccessibilityNew
import com.example.engine.model.AnimationClipTrack
import com.example.engine.model.InterpolationType
import com.example.engine.model.Keyframe
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.ui.theme.CharcoalCanvas
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PanelNearBlack
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BottomTimelinePanel(
    scene: SceneDocument,
    selectedNodeId: String?,
    selectedKeyframeId: String?,
    hasCopiedKeyframe: Boolean,
    onTogglePlay: () -> Unit,
    onScrubFrame: (Float) -> Unit,
    onStepFrame: (Int) -> Unit,
    onToggleLoop: () -> Unit,
    onToggleAutoKey: () -> Unit,
    onInsertKeyframe: () -> Unit,
    onSelectKeyframe: (String?) -> Unit,
    onDuplicateKeyframe: () -> Unit,
    onCopyKeyframe: () -> Unit,
    onPasteKeyframe: () -> Unit,
    onDeleteKeyframe: () -> Unit,
    onMoveSelectedKeyframe: (Int) -> Unit,
    onUpdateKeyframeInterpolation: (InterpolationType) -> Unit,
    onOpenPoseLibrary: () -> Unit,
    onToggleTrackVisibility: (String) -> Unit,
    onToggleTrackLock: (String) -> Unit,
    onSelectNode: (String) -> Unit,
    onUpdateClip: (AnimationClipTrack) -> Unit,
    onDeleteClip: (String) -> Unit,
    onZoomTimeline: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tl = scene.timeline
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    val selectedClip = tl.clips.firstOrNull { it.id == selectedClipId }
    val density = LocalDensity.current
    val pxPerFrame = with(density) { (7.5f * tl.zoom).dp.toPx() }
    val trackWidthDp = ((tl.totalFrames + 12) * 7.5f * tl.zoom).dp

    val sec = tl.currentFrame / tl.fps.coerceAtLeast(1)
    val mins = (sec / 60f).toInt()
    val remSec = sec - mins * 60f
    val timecode = String.format(Locale.US, "%02d:%04.1f • F%03d/%d", mins, remSec, tl.currentFrame.roundToInt(), tl.totalFrames)

    Surface(
        color = PanelNearBlack,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("bottom_timeline_panel")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Playback & Keyframe Control Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalCanvas)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onScrubFrame(0f) },
                    modifier = Modifier.size(34.dp).testTag("timeline_jump_start")
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Jump to start", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                Surface(
                    onClick = onTogglePlay,
                    shape = RoundedCornerShape(8.dp),
                    color = if (tl.isPlaying) StudioCyan else ElectricIndigo,
                    modifier = Modifier.size(36.dp).testTag("timeline_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (tl.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = if (tl.isPlaying) CharcoalCanvas else TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = { onStepFrame(-1) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous frame", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onStepFrame(1) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next frame", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier.size(32.dp).testTag("timeline_loop_toggle")
                ) {
                    Icon(
                        Icons.Rounded.Repeat,
                        contentDescription = "Toggle loop",
                        tint = if (tl.isLooping) StudioCyan else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Tabular Timecode pill
                Surface(
                    color = SurfaceCard,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(
                        text = timecode,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = StudioCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Auto-Key record button
                IconButton(
                    onClick = onToggleAutoKey,
                    modifier = Modifier.size(32.dp).testTag("timeline_autokey_toggle")
                ) {
                    Icon(
                        Icons.Rounded.FiberManualRecord,
                        contentDescription = "Toggle auto keyframe",
                        tint = if (tl.autoKey) DangerRed else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Pose Library quick trigger right on the Timeline
                Surface(
                    onClick = onOpenPoseLibrary,
                    shape = RoundedCornerShape(8.dp),
                    color = ElectricIndigo.copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.height(30.dp).testTag("timeline_pose_library_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Rounded.AccessibilityNew, contentDescription = "Pose Library", tint = StudioCyan, modifier = Modifier.size(14.dp))
                        Text("Poses", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }

                // Insert Keyframe button
                Surface(
                    onClick = onInsertKeyframe,
                    shape = RoundedCornerShape(8.dp),
                    color = KeyframeAmber.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, KeyframeAmber),
                    modifier = Modifier.height(30.dp).testTag("timeline_add_keyframe_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Keyframe", tint = KeyframeAmber, modifier = Modifier.size(14.dp))
                        Text("Key", style = MaterialTheme.typography.labelSmall, color = KeyframeAmber)
                    }
                }

                IconButton(onClick = { onZoomTimeline(0.8f) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out timeline", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { onZoomTimeline(1.25f) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in timeline", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // Contextual Selected Keyframe Edit Bar + Pro Easing Curves
            if (selectedKeyframeId != null || hasCopiedKeyframe) {
                val selKf = tl.keyframes.firstOrNull { it.id == selectedKeyframeId }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (selKf != null) "Key F${selKf.frame} (${selKf.property.label})" else "Clipboard ready",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = KeyframeAmber,
                            modifier = Modifier.weight(1f)
                        )
                        if (selKf != null) {
                            Surface(
                                onClick = { onMoveSelectedKeyframe(selKf.frame - 5) },
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceCard
                            ) { Text("-5F", style = MaterialTheme.typography.labelSmall, color = TextPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) }
                            Surface(
                                onClick = { onMoveSelectedKeyframe(selKf.frame + 5) },
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceCard
                            ) { Text("+5F", style = MaterialTheme.typography.labelSmall, color = TextPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) }
                            IconButton(onClick = onDuplicateKeyframe, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Add, contentDescription = "Duplicate keyframe", tint = StudioCyan, modifier = Modifier.size(15.dp))
                            }
                            IconButton(onClick = onCopyKeyframe, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy keyframe", tint = TextPrimary, modifier = Modifier.size(15.dp))
                            }
                            IconButton(onClick = onDeleteKeyframe, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete keyframe", tint = DangerRed, modifier = Modifier.size(15.dp))
                            }
                        }
                        if (hasCopiedKeyframe) {
                            IconButton(onClick = onPasteKeyframe, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste keyframe at playhead", tint = StudioCyan, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    if (selKf != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Curve:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            InterpolationType.entries.forEach { curve ->
                                val active = selKf.interpolation == curve
                                Surface(
                                    onClick = { onUpdateKeyframeInterpolation(curve) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (active) ElectricIndigo else SurfaceCard,
                                    border = BorderStroke(1.dp, if (active) StudioCyan else HairlineBorder)
                                ) {
                                    Text(
                                        curve.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) TextPrimary else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Contextual Selected Animation Clip Inspector Bar (Speed / Loop / Delete)
            if (selectedClip != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = selectedClip.clipName,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { spd ->
                        val active = kotlin.math.abs(selectedClip.speed - spd) < 0.05f
                        Surface(
                            onClick = { onUpdateClip(selectedClip.copy(speed = spd)) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (active) ElectricIndigo else SurfaceCard,
                            border = BorderStroke(1.dp, if (active) StudioCyan else HairlineBorder)
                        ) {
                            Text(
                                text = "${spd}x",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = { onUpdateClip(selectedClip.copy(loop = !selectedClip.loop)) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (selectedClip.loop) StudioCyan.copy(alpha = 0.22f) else SurfaceCard,
                        border = BorderStroke(1.dp, if (selectedClip.loop) StudioCyan else HairlineBorder)
                    ) {
                        Text(
                            text = if (selectedClip.loop) "Loop" else "Once",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedClip.loop) StudioCyan else TextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            onDeleteClip(selectedClip.id)
                            selectedClipId = null
                        },
                        modifier = Modifier.size(28.dp).testTag("timeline_delete_clip_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete clip", tint = DangerRed, modifier = Modifier.size(16.dp))
                    }
                }
            }

            HorizontalDivider(color = HairlineBorder, thickness = 1.dp)

            val playheadX = (tl.currentFrame * pxPerFrame).roundToInt()

            // 2A. Pinned Header Row: LAYERS Header + Horizontally-Synced Frame Ruler
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(CharcoalCanvas)
            ) {
                Box(
                    modifier = Modifier
                        .width(126.dp)
                        .fillMaxHeight()
                        .border(0.5.dp, HairlineBorder)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("LAYERS (${scene.nodes.size})", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .width(trackWidthDp)
                            .fillMaxHeight()
                            .pointerInput(tl.totalFrames, pxPerFrame) {
                                detectTapGestures { offset ->
                                    onScrubFrame((offset.x / pxPerFrame).coerceIn(0f, tl.totalFrames.toFloat()))
                                }
                            }
                            .pointerInput(tl.totalFrames, pxPerFrame) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    onScrubFrame((change.position.x / pxPerFrame).coerceIn(0f, tl.totalFrames.toFloat()))
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (f in 0..tl.totalFrames step 5) {
                                val x = f * pxPerFrame
                                val isMajor = f % 10 == 0
                                drawLine(
                                    color = if (isMajor) TextSecondary else HairlineBorder,
                                    start = Offset(x, if (isMajor) 8f else 15f),
                                    end = Offset(x, size.height),
                                    strokeWidth = if (isMajor) 1.5f else 1f
                                )
                            }
                        }
                        for (f in 0..tl.totalFrames step 10) {
                            Text(
                                text = "$f",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = TextMuted,
                                modifier = Modifier.offset { IntOffset((f * pxPerFrame + 3f).roundToInt(), 2) }
                            )
                        }
                        // Playhead handle cap on ruler
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(playheadX - 6, 0) }
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(StudioCyan)
                        )
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(playheadX - 1, 0) }
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(StudioCyan)
                        )
                    }
                }
            }

            // 2B. Synchronized Vertically-Scrollable Split Layer Headers + Track Lanes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .verticalScroll(verticalScrollState)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Left Layer Headers (always aligned 1:1 with right tracks!)
                    Column(
                        modifier = Modifier
                            .width(126.dp)
                            .background(PanelNearBlack)
                            .border(0.5.dp, HairlineBorder)
                    ) {
                        scene.nodes.forEach { node ->
                            val isSel = node.id == selectedNodeId
                            val isHidden = tl.hiddenTrackNodeIds.contains(node.id)
                            val isLocked = tl.lockedTrackNodeIds.contains(node.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(if (isSel) ElectricIndigo.copy(alpha = 0.20f) else Color.Transparent)
                                    .clickable { onSelectNode(node.id) }
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isHidden) TextMuted else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onToggleTrackVisibility(node.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle layer visibility",
                                        tint = if (isHidden) TextMuted else StudioCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleTrackLock(node.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                        contentDescription = "Toggle layer lock",
                                        tint = if (isLocked) KeyframeAmber else TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = HairlineBorder.copy(alpha = 0.5f))
                        }
                    }

                    // Right Horizontally-Synced Multi-Track Lanes + Continuous Cyan Playhead Line
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        Column(modifier = Modifier.width(trackWidthDp)) {
                            scene.nodes.forEach { node ->
                                TimelineTrackRow(
                                    node = node,
                                    keyframes = tl.keyframes.filter { it.targetNodeId == node.id },
                                    clips = tl.clips.filter { it.targetNodeId == node.id },
                                    selectedKeyframeId = selectedKeyframeId,
                                    selectedClipId = selectedClipId,
                                    pxPerFrame = pxPerFrame,
                                    isSelectedNode = node.id == selectedNodeId,
                                    onSelectKeyframe = { kf ->
                                        selectedClipId = null
                                        onSelectKeyframe(kf.id)
                                        onScrubFrame(kf.frame.toFloat())
                                    },
                                    onSelectClip = { clipId ->
                                        onSelectKeyframe(null)
                                        selectedClipId = if (selectedClipId == clipId) null else clipId
                                    }
                                )
                                HorizontalDivider(color = HairlineBorder.copy(alpha = 0.5f))
                            }
                        }

                        // Continuous Vertical Studio Cyan Playhead Needle across all tracks
                        val contentHeightDp = (scene.nodes.size * 35).coerceAtLeast(126).dp
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(playheadX - 1, 0) }
                                .width(2.dp)
                                .height(contentHeightDp)
                                .background(StudioCyan)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTrackRow(
    node: SceneNode,
    keyframes: List<Keyframe>,
    clips: List<AnimationClipTrack>,
    selectedKeyframeId: String?,
    selectedClipId: String?,
    pxPerFrame: Float,
    isSelectedNode: Boolean,
    onSelectKeyframe: (Keyframe) -> Unit,
    onSelectClip: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(if (isSelectedNode) ElectricIndigo.copy(alpha = 0.08f) else Color.Transparent)
    ) {
        // Animation Clip Blocks (tap to select and adjust speed/loop/delete safely)
        clips.forEach { clip ->
            val startPx = (clip.startFrame * pxPerFrame).roundToInt()
            val widthDp = ((clip.durationFrames * pxPerFrame) / LocalDensity.current.density).dp.coerceAtLeast(52.dp)
            val isClipSel = clip.id == selectedClipId
            Surface(
                onClick = { onSelectClip(clip.id) },
                shape = RoundedCornerShape(6.dp),
                color = Color(clip.colorHex).copy(alpha = if (isClipSel) 0.52f else 0.32f),
                border = BorderStroke(if (isClipSel) 1.5.dp else 1.dp, if (isClipSel) StudioCyan else Color(clip.colorHex)),
                modifier = Modifier
                    .offset { IntOffset(startPx, 4) }
                    .width(widthDp)
                    .height(26.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (kotlin.math.abs(clip.speed - 1f) > 0.05f) "${clip.clipName} (${clip.speed}x)" else clip.clipName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Keyframe Amber Diamond Nodes
        keyframes.forEach { kf ->
            val xPx = (kf.frame * pxPerFrame).roundToInt()
            val isSel = kf.id == selectedKeyframeId
            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx - 7, 10) }
                    .size(14.dp)
                    .rotate(45f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSel) StudioCyan else KeyframeAmber)
                    .border(1.dp, CharcoalCanvas, RoundedCornerShape(2.dp))
                    .clickable { onSelectKeyframe(kf) }
            )
        }
    }
}
