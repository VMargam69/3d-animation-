package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.model.ExportFormat
import com.example.engine.model.ExportResolution
import com.example.engine.model.RenderQuality
import com.example.ui.components.StudioFilterChip
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PanelNearBlack
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ExportUiState

@Composable
fun ExportStudioDialog(
    state: ExportUiState,
    totalFrames: Int,
    onUpdateConfig: (ExportFormat, ExportResolution, Int, Int, Int, RenderQuality) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!state.isExporting) onDismiss() },
        containerColor = PanelNearBlack,
        titleContentColor = TextPrimary,
        icon = { Icon(Icons.Rounded.MovieCreation, contentDescription = null, tint = StudioCyan) },
        title = { Text("Offline Local Render & Export", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Format", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        StudioFilterChip(
                            label = fmt.label,
                            selected = state.format == fmt,
                            onClick = {
                                if (!state.isExporting) {
                                    onUpdateConfig(fmt, state.resolution, state.fps, state.startFrame, state.endFrame, state.quality)
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_format_${fmt.name}")
                        )
                    }
                }

                if (state.format == ExportFormat.MP4) {
                    Text("Resolution", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExportResolution.entries.forEach { res ->
                            StudioFilterChip(
                                label = res.label.substringBefore(' '),
                                selected = state.resolution == res,
                                onClick = {
                                    if (!state.isExporting) {
                                        onUpdateConfig(state.format, res, state.fps, state.startFrame, state.endFrame, state.quality)
                                    }
                                },
                                accentColor = StudioCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Frame Rate & Quality", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(24, 30, 60).forEach { fps ->
                            StudioFilterChip(
                                label = "$fps FPS",
                                selected = state.fps == fps,
                                onClick = {
                                    if (!state.isExporting) {
                                        onUpdateConfig(state.format, state.resolution, fps, state.startFrame, state.endFrame, state.quality)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RenderQuality.entries.forEach { q ->
                            StudioFilterChip(
                                label = q.label.substringBefore(' '),
                                selected = state.quality == q,
                                onClick = {
                                    if (!state.isExporting) {
                                        onUpdateConfig(state.format, state.resolution, state.fps, state.startFrame, state.endFrame, q)
                                    }
                                },
                                accentColor = StudioCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Range: F${state.startFrame} → F${state.endFrame.coerceAtMost(totalFrames)}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily), color = TextPrimary)
                            Text(
                                String.format("%.1fs", (state.endFrame - state.startFrame).coerceAtLeast(1) / state.fps.toFloat()),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = StudioCyan
                            )
                        }
                    }
                } else {
                    Text(
                        "Exports the current 3D scene geometry, normals, UVs, and PBR materials as a standard glTF 2.0 binary (.glb) compatible with Blender.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (state.isExporting) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = StudioCyan,
                            trackColor = HairlineBorder
                        )
                        Text(
                            text = "Rendering locally on GPU... ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = StudioCyan
                        )
                    }
                }

                state.errorMessage?.let { err ->
                    Text(err, style = MaterialTheme.typography.bodySmall, color = DangerRed)
                }

                if (state.completedUri != null && state.completedFileName != null) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                Text("Saved: ${state.completedFileName}", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = if (state.format == ExportFormat.MP4) "video/mp4" else "model/gltf-binary"
                                        putExtra(Intent.EXTRA_STREAM, state.completedUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share 3D Export"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, StudioCyan)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, tint = StudioCyan)
                                Spacer(Modifier.width(6.dp))
                                Text("Share to Social / Apps", color = TextPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.isExporting) {
                TextButton(onClick = onCancelExport) { Text("Cancel Render", color = DangerRed) }
            } else {
                Button(
                    onClick = onStartExport,
                    modifier = Modifier.testTag("start_export_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Text(if (state.format == ExportFormat.MP4) "Render MP4" else "Export GLB")
                }
            }
        },
        dismissButton = {
            if (!state.isExporting) {
                TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
            }
        }
    )
}
