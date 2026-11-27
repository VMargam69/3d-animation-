package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SavedPoseEntity
import com.example.ui.components.StudioFilterChip
import com.example.ui.theme.CharcoalCanvas
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PanelNearBlack
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun PoseLibrarySheet(
    poses: List<SavedPoseEntity>,
    currentFrame: Int,
    poseBlendWeight: Float,
    onChangeBlendWeight: (Float) -> Unit,
    onSaveCurrentPose: (name: String, category: String) -> Unit,
    onApplyPose: (pose: SavedPoseEntity, blendWeight: Float, insertKeyframes: Boolean) -> Unit,
    onMirrorPose: () -> Unit,
    onDeletePose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var poseNameInput by remember { mutableStateOf("") }
    var saveCategory by remember { mutableStateOf("Custom") }
    var filterCategory by remember { mutableStateOf("All") }

    val saveCategories = listOf("Custom", "Action", "Social", "Combat", "Dance")
    val filterCategories = listOf("All", "Custom", "Action", "Social", "Combat", "Dance", "Reset")
    val filteredPoses = remember(poses, filterCategory) {
        if (filterCategory == "All") poses
        else poses.filter { it.category.equals(filterCategory, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CharcoalCanvas)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("pose_library_sheet"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header with PRO badge & Current Frame indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(22.dp))
            Text("Pose Library", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Surface(
                color = KeyframeAmber.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, KeyframeAmber.copy(alpha = 0.6f))
            ) {
                Text(
                    "PRO",
                    style = MaterialTheme.typography.labelSmall,
                    color = KeyframeAmber,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text(
                    "Playhead F$currentFrame",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = StudioCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Save Current Character Pose Bar
        Surface(
            color = PanelNearBlack,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = poseNameInput,
                        onValueChange = { poseNameInput = it },
                        placeholder = { Text("Name current pose (e.g. Hero Landing)", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("pose_name_input")
                    )
                    Button(
                        onClick = {
                            onSaveCurrentPose(poseNameInput, saveCategory)
                            poseNameInput = ""
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.height(50.dp).testTag("save_current_pose_button")
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelLarge)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(saveCategories) { cat ->
                        StudioFilterChip(
                            label = cat,
                            selected = saveCategory == cat,
                            onClick = { saveCategory = cat },
                            accentColor = StudioCyan,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        // Pose Blend Weight + Mirror L<->R Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Blend ${(poseBlendWeight * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = StudioCyan,
                modifier = Modifier.width(68.dp)
            )
            Slider(
                value = poseBlendWeight,
                onValueChange = onChangeBlendWeight,
                valueRange = 0.1f..1.0f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = StudioCyan,
                    activeTrackColor = ElectricIndigo,
                    inactiveTrackColor = HairlineBorder
                )
            )
            OutlinedButton(
                onClick = onMirrorPose,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp).testTag("pose_mirror_button")
            ) {
                Icon(Icons.Rounded.Flip, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Mirror L↔R", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            }
        }

        // Category Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filterCategories) { cat ->
                StudioFilterChip(
                    label = cat,
                    selected = filterCategory == cat,
                    onClick = { filterCategory = cat },
                    accentColor = ElectricIndigo,
                    modifier = Modifier.height(30.dp)
                )
            }
        }

        HorizontalDivider(color = HairlineBorder)

        // Saved Poses Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 18.dp)
        ) {
            items(filteredPoses, key = { it.id }) { pose ->
                PoseLibraryCard(
                    pose = pose,
                    onApply = { onApplyPose(pose, poseBlendWeight, false) },
                    onApplyAndKey = { onApplyPose(pose, poseBlendWeight, true) },
                    onDelete = if (!pose.isBuiltIn) { { onDeletePose(pose.id) } } else null
                )
            }
        }
    }
}

@Composable
private fun PoseLibraryCard(
    pose: SavedPoseEntity,
    onApply: () -> Unit,
    onApplyAndKey: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val accent = Color(pose.accentHex)
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth().testTag("pose_card_${pose.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accent.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                ) {
                    Text(
                        pose.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete pose", tint = DangerRed, modifier = Modifier.size(16.dp))
                    }
                } else {
                    Text(
                        "${pose.boneCount}b",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = TextMuted
                    )
                }
            }

            Text(
                text = pose.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onApply,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text("Apply", style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
                }
                Button(
                    onClick = onApplyAndKey,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.25f).height(34.dp)
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = KeyframeAmber, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("+ Key", style = MaterialTheme.typography.labelSmall, color = TextPrimary, maxLines = 1)
                }
            }
        }
    }
}
