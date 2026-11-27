package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BlenderWorkflowGuideDialog
import com.example.ui.components.ImportInspectionDialog
import com.example.ui.screens.AssetBrowserScreen
import com.example.ui.screens.CharacterCreatorScreen
import com.example.ui.screens.CharacterLibraryScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ProjectsHubScreen
import com.example.ui.theme.CharcoalCanvas
import com.example.ui.theme.DraftRigTheme
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.ElectricIndigoContainer
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.PanelNearBlack
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppDestination
import com.example.viewmodel.HubTab
import com.example.viewmodel.StudioAppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            DraftRigTheme {
                DraftRigStudioRoot()
            }
        }
    }
}

@Composable
private fun DraftRigStudioRoot(
    viewModel: StudioAppViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val assets by viewModel.libraryAssets.collectAsStateWithLifecycle()
    val savedPoses by viewModel.savedPoses.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBlenderGuide by remember { mutableStateOf(false) }

    // Zero-permission SAF OpenDocument launcher for Blender 3D models (.glb, .gltf, .obj, .stl)
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importExternalModel(uri, addToCurrentScene = state.destination == AppDestination.EDITOR)
        }
    }

    // Zero-permission Android Photo Picker for local material textures (Play-policy compliant)
    val texturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importTextureForSelectedNode(uri)
        }
    }

    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearToast()
    }

    val launchModelImport = {
        modelPickerLauncher.launch(arrayOf("*/*"))
    }
    val launchTexturePicker = {
        texturePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(modifier = Modifier.fillMaxSize().background(CharcoalCanvas)) {
        when (state.destination) {
            AppDestination.HUB -> {
                StudioHubShell(
                    selectedTab = state.hubTab,
                    onSelectTab = viewModel::selectHubTab,
                    snackbarHostState = snackbarHostState
                ) {
                    when (state.hubTab) {
                        HubTab.PROJECTS -> ProjectsHubScreen(
                            projects = projects,
                            onQuickStart = viewModel::openQuickStartScene,
                            onOpenTemplate = viewModel::openTemplateProject,
                            onCreateBlank = viewModel::createNewBlankProject,
                            onOpenProject = viewModel::openProject,
                            onRenameProject = viewModel::renameProject,
                            onDuplicateProject = viewModel::duplicateProject,
                            onDeleteProject = viewModel::deleteProject,
                            onImportBlenderFile = launchModelImport,
                            onOpenBlenderGuide = { showBlenderGuide = true }
                        )
                        HubTab.CHARACTERS -> CharacterLibraryScreen(
                            assets = assets,
                            onAddAssetToScene = viewModel::addLibraryAssetToScene,
                            onTogglePin = viewModel::togglePinAsset,
                            onRenameAsset = viewModel::renameAsset,
                            onDeleteAsset = viewModel::deleteAsset,
                            onImportBlenderModel = launchModelImport,
                            onOpenCreator = { viewModel.selectHubTab(HubTab.CREATOR) }
                        )
                        HubTab.ASSETS -> AssetBrowserScreen(
                            assets = assets,
                            onAddAssetToScene = viewModel::addLibraryAssetToScene,
                            onTogglePin = viewModel::togglePinAsset,
                            onDeleteAsset = viewModel::deleteAsset,
                            onImportBlenderFile = launchModelImport,
                            onOpenBlenderGuide = { showBlenderGuide = true }
                        )
                        HubTab.CREATOR -> CharacterCreatorScreen(
                            spec = state.creatorSpec,
                            onUpdateSpec = viewModel::updateCreatorSpec,
                            onSaveToLibrary = viewModel::saveCustomCharacterFromCreator
                        )
                    }
                }
            }

            AppDestination.EDITOR -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    EditorScreen(
                        state = state,
                        libraryAssets = assets,
                        savedPoses = savedPoses,
                        viewModel = viewModel,
                        onImportBlenderModel = launchModelImport,
                        onPickTextureImage = launchTexturePicker
                    )
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 86.dp)
                    )
                }
            }
        }
    }

    state.importReport?.let { report ->
        ImportInspectionDialog(
            report = report,
            onDismiss = viewModel::dismissImportReport
        )
    }

    if (showBlenderGuide) {
        BlenderWorkflowGuideDialog(onDismiss = { showBlenderGuide = false })
    }
}

@Composable
private fun StudioHubShell(
    selectedTab: HubTab,
    onSelectTab: (HubTab) -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit
) {
    fun tabIcon(tab: HubTab) = when (tab) {
        HubTab.PROJECTS -> Icons.Rounded.Folder
        HubTab.CHARACTERS -> Icons.Rounded.Groups
        HubTab.ASSETS -> Icons.Rounded.Inventory2
        HubTab.CREATOR -> Icons.Rounded.AccessibilityNew
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(CharcoalCanvas)) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = CharcoalCanvas,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(
                    color = PanelNearBlack,
                    border = BorderStroke(0.5.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = ElectricIndigoContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ElectricIndigo),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("DraftRig 3D", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Offline 3D Studio • ${selectedTab.label}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Surface(
                            color = Color(0xFF102A33),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = PanelNearBlack,
                        contentColor = TextPrimary,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        HubTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = StudioCyan,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = ElectricIndigoContainer,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("hub_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (useRail) {
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    NavigationRail(
                        containerColor = PanelNearBlack,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        HubTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = StudioCyan,
                                    selectedTextColor = TextPrimary,
                                    indicatorColor = ElectricIndigoContainer,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        content()
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    content()
                }
            }
        }
    }
}
