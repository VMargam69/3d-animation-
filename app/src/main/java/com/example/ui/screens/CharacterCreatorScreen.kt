package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.gl.RenderConfig
import com.example.engine.gl.ViewportSurface
import com.example.engine.model.AccessoryKind
import com.example.engine.model.CharacterArchetype
import com.example.engine.model.CharacterBuildSpec
import com.example.engine.model.CharacterCategory
import com.example.engine.model.CharacterMeshBuilder
import com.example.engine.model.CostumePreset
import com.example.engine.model.LightData
import com.example.engine.model.LightType
import com.example.engine.model.NodeType
import com.example.engine.model.SceneDocument
import com.example.engine.model.SceneNode
import com.example.engine.model.Transform3D
import com.example.engine.model.Vec3
import com.example.engine.model.ViewportCameraState
import com.example.ui.components.AxisSliderRow
import com.example.ui.components.ColorSwatchGrid
import com.example.ui.components.StudioFilterChip
import com.example.ui.theme.CharcoalCanvas
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.PanelNearBlack
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CharacterCreatorScreen(
    spec: CharacterBuildSpec,
    onUpdateSpec: ((CharacterBuildSpec) -> CharacterBuildSpec) -> Unit,
    onSaveToLibrary: (openInScene: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var previewYaw by remember { mutableFloatStateOf(24f) }
    var previewPitch by remember { mutableFloatStateOf(14f) }
    var previewDist by remember { mutableFloatStateOf(4.2f) }

    val previewConfig = remember(spec, previewYaw, previewPitch, previewDist) {
        val charNode = CharacterMeshBuilder.buildRiggedCharacterNode(spec)
        val keyLight = SceneNode(
            id = "creator_key",
            name = "Key",
            nodeType = NodeType.LIGHT,
            transform = Transform3D(position = Vec3(2.8f, 3.8f, 3.4f)),
            light = LightData(LightType.POINT, 0xFFFFFFFF, 1.8f)
        )
        val rimLight = SceneNode(
            id = "creator_rim",
            name = "Rim",
            nodeType = NodeType.LIGHT,
            transform = Transform3D(position = Vec3(-2.6f, 2.2f, -2.5f)),
            light = LightData(LightType.POINT, 0xFF22D3EE, 1.3f)
        )
        RenderConfig(
            scene = SceneDocument(
                nodes = listOf(charNode, keyLight, rimLight),
                viewportCamera = ViewportCameraState(
                    target = Vec3(0f, 1.0f, 0f),
                    yawDeg = previewYaw,
                    pitchDeg = previewPitch,
                    distance = previewDist
                )
            ),
            selectedNodeId = charNode.id,
            showGrid = true,
            showSkeleton = true,
            showGizmo = false
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalCanvas)
    ) {
        // Live Genuine OpenGL ES 3.0 Character Turntable Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ElectricIndigo.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            ViewportSurface(
                renderConfig = previewConfig,
                isPlaying = false,
                onOrbit = { dy, dp ->
                    previewYaw = (previewYaw + dy) % 360f
                    previewPitch = (previewPitch + dp).coerceIn(-65f, 75f)
                },
                onPan = { _, _ -> },
                onZoom = { z -> previewDist = (previewDist / z).coerceIn(2.2f, 8.5f) },
                onSelectNodeOrBone = { _, _ -> }
            )
            Surface(
                color = PanelNearBlack.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
            ) {
                Text(
                    text = "LIVE 3D RIG PREVIEW • DRAG TO ORBIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Scrollable Creator Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = spec.name,
                onValueChange = { v -> onUpdateSpec { it.copy(name = v) } },
                label = { Text("Character Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("creator_name_input")
            )

            // Costume Presets
            Text("Costume & Outfit System", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CostumePreset.entries) { costume ->
                    StudioFilterChip(
                        label = costume.label,
                        selected = spec.costume == costume,
                        onClick = {
                            onUpdateSpec {
                                it.copy(
                                    costume = costume,
                                    primaryColor = costume.primaryColor,
                                    secondaryColor = costume.secondaryColor,
                                    accessory = costume.accessory
                                )
                            }
                        }
                    )
                }
            }

            // Archetype & Category
            Text("3D Rig Archetype", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CharacterArchetype.entries) { arch ->
                    StudioFilterChip(
                        label = arch.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                        selected = spec.bodyType == arch,
                        onClick = { onUpdateSpec { it.copy(bodyType = arch) } },
                        accentColor = StudioCyan
                    )
                }
            }

            Text("Library Category", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(
                    listOf(
                        CharacterCategory.CUSTOM, CharacterCategory.DESI, CharacterCategory.WESTERN,
                        CharacterCategory.INDONESIAN, CharacterCategory.VILLAGE, CharacterCategory.BIRDS,
                        CharacterCategory.ANIMALS, CharacterCategory.VEHICLES, CharacterCategory.RELIGIOUS,
                        CharacterCategory.HORROR
                    )
                ) { cat ->
                    StudioFilterChip(
                        label = cat.label,
                        selected = spec.category == cat,
                        onClick = { onUpdateSpec { it.copy(category = cat) } }
                    )
                }
            }

            // Accessories
            Text("Accessories & Gear", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AccessoryKind.entries) { acc ->
                    StudioFilterChip(
                        label = acc.label,
                        selected = spec.accessory == acc,
                        onClick = { onUpdateSpec { it.copy(accessory = acc) } },
                        accentColor = StudioCyan
                    )
                }
            }

            // Proportions & Material
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Proportions & Surface", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    AxisSliderRow("H", spec.heightScale, 0.75f..1.4f, { v -> onUpdateSpec { it.copy(heightScale = v) } })
                    AxisSliderRow("W", spec.torsoWidth, 0.75f..1.35f, { v -> onUpdateSpec { it.copy(torsoWidth = v) } })
                    AxisSliderRow("Hd", spec.headScale, 0.75f..1.35f, { v -> onUpdateSpec { it.copy(headScale = v) } })
                    AxisSliderRow("Lm", spec.limbThickness, 0.75f..1.35f, { v -> onUpdateSpec { it.copy(limbThickness = v) } })
                    AxisSliderRow("Rg", spec.roughness, 0.08f..1.0f, { v -> onUpdateSpec { it.copy(roughness = v) } })
                    AxisSliderRow("Mt", spec.metallic, 0.0f..1.0f, { v -> onUpdateSpec { it.copy(metallic = v) } })
                }
            }

            Text("Primary Costume Color", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            ColorSwatchGrid(
                selectedHex = spec.primaryColor,
                onSelect = { hex -> onUpdateSpec { it.copy(primaryColor = hex) } }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSaveToLibrary(false) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("creator_save_only_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, tint = StudioCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Save Library", color = TextPrimary)
                }
                Button(
                    onClick = { onSaveToLibrary(true) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("creator_save_animate_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save & Animate")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
