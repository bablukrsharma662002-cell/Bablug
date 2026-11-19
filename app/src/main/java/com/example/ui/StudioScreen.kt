package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.RunCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.signOutUser
import com.example.data.StageCraftRepository
import com.example.engine.AnimationEvaluator
import com.example.engine.VoiceController
import com.example.model.CharacterActionType
import com.example.ui.canvas.StageCanvas
import com.example.ui.dialogs.ActionClipEditorDialog
import com.example.ui.dialogs.CharacterLibraryDialog
import com.example.ui.dialogs.CreateCharacterDialog
import com.example.ui.dialogs.ScenesManagerDialog
import com.example.ui.inspector.ActionsPanel
import com.example.ui.inspector.CastLayersPanel
import com.example.ui.inspector.FaceVoicePanel
import com.example.ui.inspector.InteractionsPanel
import com.example.ui.inspector.PropsAndStagePanel
import com.example.ui.inspector.TransformAndRigPanel
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.RigCyan
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceContainer
import com.example.ui.timeline.MultiTrackTimeline
import com.example.viewmodel.InspectorTab
import com.example.viewmodel.SaveStatus
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

@Composable
fun StudioScreen(
    currentUserId: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudioViewModel(StageCraftRepository(db), currentUserId)
            }
        },
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedScenesState by viewModel.savedScenesState.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryCharactersState.collectAsStateWithLifecycle()

    // Independent per-character Android TextToSpeech voice engine
    val voiceController = remember(context) { VoiceController(context) }
    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    // 60fps smooth playback loop using Choreographer frame timing
    LaunchedEffect(uiState.isPlaying) {
        if (uiState.isPlaying) {
            voiceController.resetPlaybackQueue()
            var lastFrameNs = withFrameNanos { it }
            while (true) {
                val nowNs = withFrameNanos { it }
                val deltaMs = ((nowNs - lastFrameNs) / 1_000_000L).coerceIn(1L, 64L)
                lastFrameNs = nowNs
                viewModel.advancePlayhead(deltaMs)
            }
        } else {
            voiceController.stop()
        }
    }

    // Trigger per-character dialogue TTS when playhead enters a dialogue clip
    LaunchedEffect(uiState.isPlaying, uiState.playheadMs / 120L, uiState.voiceEnabled) {
        if (!uiState.isPlaying || !uiState.voiceEnabled) return@LaunchedEffect
        val now = uiState.playheadMs
        if (now < 140L) {
            voiceController.resetPlaybackQueue()
        }
        uiState.scene.characters.forEach { ch ->
            if (!ch.visible) return@forEach
            val activeClip = ch.actionClips.lastOrNull { now in it.startMs..it.endMs }
            if (activeClip != null && activeClip.dialogueText.isNotBlank()) {
                voiceController.triggerClipDialogueIfNeeded(
                    clipId = "${ch.characterId}_${activeClip.clipId}",
                    text = activeClip.dialogueText,
                    pitch = ch.voicePitch,
                    rate = ch.voiceRate,
                )
            }
        }
    }

    // Auto-dismiss transient toast banner
    LaunchedEffect(uiState.statusBanner) {
        if (uiState.statusBanner != null) {
            delay(2600L)
            viewModel.clearBanner()
        }
    }

    // Evaluate current animation frame deterministically
    val evaluatedFrame by remember(uiState.scene, uiState.playheadMs) {
        derivedStateOf { AnimationEvaluator.evaluateFrame(uiState.scene, uiState.playheadMs) }
    }

    Scaffold(
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            StudioTopBar(
                sceneTitle = uiState.scene.title,
                saveStatus = uiState.saveStatus,
                onOpenScenes = viewModel::openScenesSheet,
                onOpenLibrary = viewModel::openLibraryToAdd,
                onSaveScene = viewModel::saveCurrentSceneToCloud,
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            val isExpandedWide = maxWidth >= 760.dp

            if (isExpandedWide) {
                // Canonical Tablet / Foldable Side-by-Side Studio Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StageCanvas(
                            frame = evaluatedFrame,
                            backdrop = uiState.scene.backdrop,
                            selectedCharacterId = uiState.selectedCharacterId,
                            selectedPropId = uiState.selectedPropId,
                            onSelectCharacter = viewModel::selectCharacter,
                            onSelectProp = viewModel::selectProp,
                            onDragCharacter = viewModel::moveCharacterByDelta,
                            onDragProp = viewModel::movePropByDelta,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.25f),
                        )

                        MultiTrackTimeline(
                            scene = uiState.scene,
                            selectedCharacterId = uiState.selectedCharacterId,
                            playheadMs = uiState.playheadMs,
                            isPlaying = uiState.isPlaying,
                            isLooping = uiState.isLooping,
                            playbackSpeed = uiState.playbackSpeed,
                            voiceEnabled = uiState.voiceEnabled,
                            onTogglePlay = viewModel::togglePlay,
                            onRewind = viewModel::rewind,
                            onToggleLoop = viewModel::toggleLoop,
                            onCycleSpeed = viewModel::cyclePlaybackSpeed,
                            onToggleVoice = viewModel::toggleVoice,
                            onSeek = viewModel::seekTo,
                            onSelectCharacter = viewModel::selectCharacter,
                            onAddKeyframe = viewModel::addOrUpdateKeyframeAtPlayhead,
                            onQuickAddAction = { cid ->
                                viewModel.selectCharacter(cid)
                                viewModel.addActionClip(cid, CharacterActionType.WAVE)
                            },
                            onEditClip = viewModel::openClipEditor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.95f),
                        )
                    }

                    InspectorDockCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        voiceController = voiceController,
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                // Compact Handheld Vertical Studio Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StageCanvas(
                        frame = evaluatedFrame,
                        backdrop = uiState.scene.backdrop,
                        selectedCharacterId = uiState.selectedCharacterId,
                        selectedPropId = uiState.selectedPropId,
                        onSelectCharacter = viewModel::selectCharacter,
                        onSelectProp = viewModel::selectProp,
                        onDragCharacter = viewModel::moveCharacterByDelta,
                        onDragProp = viewModel::movePropByDelta,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.35f),
                    )

                    MultiTrackTimeline(
                        scene = uiState.scene,
                        selectedCharacterId = uiState.selectedCharacterId,
                        playheadMs = uiState.playheadMs,
                        isPlaying = uiState.isPlaying,
                        isLooping = uiState.isLooping,
                        playbackSpeed = uiState.playbackSpeed,
                        voiceEnabled = uiState.voiceEnabled,
                        onTogglePlay = viewModel::togglePlay,
                        onRewind = viewModel::rewind,
                        onToggleLoop = viewModel::toggleLoop,
                        onCycleSpeed = viewModel::cyclePlaybackSpeed,
                        onToggleVoice = viewModel::toggleVoice,
                        onSeek = viewModel::seekTo,
                        onSelectCharacter = viewModel::selectCharacter,
                        onAddKeyframe = viewModel::addOrUpdateKeyframeAtPlayhead,
                        onQuickAddAction = { cid ->
                            viewModel.selectCharacter(cid)
                            viewModel.addActionClip(cid, CharacterActionType.WAVE)
                        },
                        onEditClip = viewModel::openClipEditor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.28f),
                    )

                    InspectorDockCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        voiceController = voiceController,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.37f),
                    )
                }
            }

            // Floating Status Toast Banner
            AnimatedVisibility(
                visible = uiState.statusBanner != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            ) {
                Surface(
                    color = StudioSurfaceContainer,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.border(1.dp, CinemaGold.copy(alpha = 0.65f), RoundedCornerShape(20.dp)),
                ) {
                    Text(
                        text = uiState.statusBanner.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }

    // --- Modals & Sheets ---
    if (uiState.showCreateCharacterDialog) {
        CreateCharacterDialog(
            onDismiss = viewModel::closeCreateCharacterDialog,
            onCreate = viewModel::createCharacterInScene,
        )
    }

    if (uiState.showLibrarySheet) {
        CharacterLibraryDialog(
            libraryState = libraryState,
            isReplaceMode = uiState.libraryReplaceTargetId != null,
            onDismiss = viewModel::closeLibrarySheet,
            onSelectTemplate = viewModel::addCharacterFromTemplate,
            onDeleteCloudTemplate = viewModel::deleteTemplateFromCloudLibrary,
            onCreateNewInstead = viewModel::openCreateCharacterDialog,
        )
    }

    val clipEdit = uiState.editingClipTarget
    if (clipEdit != null) {
        val charName = uiState.scene.characters
            .firstOrNull { it.characterId == clipEdit.first }?.name ?: "Character"
        ActionClipEditorDialog(
            characterName = charName,
            clip = clipEdit.second,
            sceneDurationMs = uiState.scene.durationMs,
            onDismiss = viewModel::closeClipEditor,
            onSave = { updated -> viewModel.updateActionClip(clipEdit.first, updated) },
            onDelete = { viewModel.deleteActionClip(clipEdit.first, clipEdit.second.clipId) },
        )
    }

    if (uiState.showScenesSheet) {
        ScenesManagerDialog(
            currentScene = uiState.scene,
            scenesState = savedScenesState,
            userEmail = Firebase.auth.currentUser?.email,
            onDismiss = viewModel::closeScenesSheet,
            onRenameCurrent = viewModel::renameScene,
            onCreateScene = viewModel::createNewScene,
            onLoadScene = viewModel::loadScene,
            onDeleteScene = viewModel::deleteCloudScene,
            onSignOut = {
                viewModel.closeScenesSheet()
                voiceController.stop()
                signOutUser(credentialManager, scope)
            },
        )
    }
}

@Composable
private fun StudioTopBar(
    sceneTitle: String,
    saveStatus: SaveStatus,
    onOpenScenes: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSaveScene: () -> Unit,
) {
    Surface(
        color = StudioSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenScenes() }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("scenes_button"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CinemaGold.copy(alpha = 0.16f))
                        .border(1.dp, CinemaGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MovieFilter,
                        contentDescription = "Scenes",
                        tint = CinemaGold,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sceneTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "StageCraft Studio · Tap for Scenes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = onOpenLibrary,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("top_library_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Character Library",
                        tint = RigCyan,
                    )
                }

                Surface(
                    color = when (saveStatus) {
                        SaveStatus.SYNCED -> StudioSuccess.copy(alpha = 0.16f)
                        SaveStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                        else -> CinemaGold
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clickable(enabled = saveStatus != SaveStatus.SAVING) { onSaveScene() }
                        .testTag("save_scene_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        when (saveStatus) {
                            SaveStatus.SAVING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = StudioObsidian,
                                )
                            }

                            SaveStatus.SYNCED -> {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = "Saved",
                                    tint = StudioSuccess,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            else -> {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = "Save scene to cloud",
                                    tint = if (saveStatus == SaveStatus.ERROR) MaterialTheme.colorScheme.error else StudioObsidian,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = when (saveStatus) {
                                SaveStatus.SYNCED -> "Synced"
                                SaveStatus.SAVING -> "Saving"
                                SaveStatus.ERROR -> "Retry"
                                SaveStatus.MODIFIED -> "Save"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when (saveStatus) {
                                SaveStatus.SYNCED -> StudioSuccess
                                SaveStatus.ERROR -> MaterialTheme.colorScheme.error
                                else -> StudioObsidian
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorDockCard(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    voiceController: VoiceController,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 6-Tab Studio Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurfaceContainer)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                InspectorTabItem(
                    tab = InspectorTab.CAST,
                    activeTab = uiState.activeTab,
                    icon = Icons.Rounded.Groups,
                    label = "Cast",
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.weight(1f),
                )
                InspectorTabItem(
                    tab = InspectorTab.RIG,
                    activeTab = uiState.activeTab,
                    icon = Icons.Rounded.AccessibilityNew,
                    label = "Rig",
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.weight(1f),
                )
                InspectorTabItem(
                    tab = InspectorTab.FACE_VOICE,
                    activeTab = uiState.activeTab,
                    icon = Icons.Rounded.Face,
                    label = "Voice",
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.weight(1f),
                )
                InspectorTabItem(
                    tab = InspectorTab.ACTIONS,
                    activeTab = uiState.activeTab,
                    icon = Icons.Rounded.RunCircle,
                    label = "Actions",
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.weight(1f),
                )
                InspectorTabItem(
                    tab = InspectorTab.INTERACT,
                    activeTab = uiState.activeTab,
                    icon = Icons.Rounded.Link,
                    label = "Interact",
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.weight(1f),
                )
                InspectorTabItem(
                    tab = InspectorTab.PROPS_STAGE,
                    activeTab = uiState.activeTab,
                    icon = Icons.Rounded.Category,
                    label = "Props",
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.weight(1f),
                )
            }

            // Active Inspector Panel Content
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (uiState.activeTab) {
                    InspectorTab.CAST -> {
                        CastLayersPanel(
                            characters = uiState.scene.characters,
                            selectedCharacterId = uiState.selectedCharacterId,
                            clipboard = uiState.clipboard,
                            onSelect = viewModel::selectCharacter,
                            onAddFromLibrary = viewModel::openLibraryToAdd,
                            onCreateNew = viewModel::openCreateCharacterDialog,
                            onToggleLock = viewModel::toggleLockCharacter,
                            onToggleVisible = viewModel::toggleVisibilityCharacter,
                            onMoveLayer = viewModel::moveCharacterLayer,
                            onDuplicate = { cid -> viewModel.duplicateCharacter(cid, includeAnimation = true) },
                            onReplace = viewModel::openLibraryToReplace,
                            onRename = viewModel::renameCharacter,
                            onSaveToLibrary = viewModel::saveCharacterToLibrary,
                            onDelete = viewModel::removeCharacter,
                            onToggleGroup = viewModel::toggleGroupWithSelected,
                            onUngroupAll = viewModel::ungroupAllInGroup,
                            onCopyActions = viewModel::copyCharacterActions,
                            onPasteActions = viewModel::pasteCharacterActions,
                        )
                    }

                    InspectorTab.RIG -> {
                        TransformAndRigPanel(
                            character = uiState.selectedCharacter,
                            allCharacters = uiState.scene.characters,
                            sceneDurationMs = uiState.scene.durationMs,
                            onUpdateTransform = viewModel::updateCharacterTransform,
                            onUpdateEntranceExit = viewModel::updateCharacterEntranceExit,
                            onUpdateGaze = { cid, ex, ey, lookAt ->
                                viewModel.updateCharacterExpressionAndGaze(cid, null, ex, ey, lookAt)
                            },
                            onUpdatePose = viewModel::updateCharacterPose,
                            onResetPose = viewModel::resetCharacterPose,
                            onAddKeyframe = viewModel::addOrUpdateKeyframeAtPlayhead,
                            onDeleteKeyframe = viewModel::deleteKeyframe,
                            onSeek = viewModel::seekTo,
                        )
                    }

                    InspectorTab.FACE_VOICE -> {
                        FaceVoicePanel(
                            character = uiState.selectedCharacter,
                            playheadMs = uiState.playheadMs,
                            onSetExpression = { cid, expr ->
                                viewModel.updateCharacterExpressionAndGaze(cid, expression = expr)
                            },
                            onUpdateDialogue = viewModel::updateActiveDialogue,
                            onUpdateVoice = viewModel::updateCharacterVoice,
                            onPreviewVoice = { text, pitch, rate ->
                                voiceController.previewVoice(text, pitch, rate)
                            },
                        )
                    }

                    InspectorTab.ACTIONS -> {
                        ActionsPanel(
                            character = uiState.selectedCharacter,
                            playheadMs = uiState.playheadMs,
                            onAddActionClip = { cid, action, startMs, durMs ->
                                viewModel.addActionClip(cid, action, startMs, durMs)
                            },
                            onEditClip = viewModel::openClipEditor,
                            onDeleteClip = viewModel::deleteActionClip,
                        )
                    }

                    InspectorTab.INTERACT -> {
                        InteractionsPanel(
                            characters = uiState.scene.characters,
                            props = uiState.scene.props,
                            interactions = uiState.scene.interactions,
                            selectedCharacterId = uiState.selectedCharacterId,
                            playheadMs = uiState.playheadMs,
                            onAddInteraction = viewModel::addInteraction,
                            onDeleteInteraction = viewModel::deleteInteraction,
                        )
                    }

                    InspectorTab.PROPS_STAGE -> {
                        PropsAndStagePanel(
                            backdrop = uiState.scene.backdrop,
                            durationMs = uiState.scene.durationMs,
                            props = uiState.scene.props,
                            characters = uiState.scene.characters,
                            selectedCharacterId = uiState.selectedCharacterId,
                            onSetBackdrop = viewModel::setBackdrop,
                            onSetDuration = viewModel::setSceneDuration,
                            onAddProp = viewModel::addProp,
                            onAssignHolder = viewModel::assignPropHolder,
                            onDeleteProp = viewModel::deleteProp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorTabItem(
    tab: InspectorTab,
    activeTab: InspectorTab,
    icon: ImageVector,
    label: String,
    onSelect: (InspectorTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = tab == activeTab
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CinemaGold.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onSelect(tab) }
            .padding(vertical = 5.dp, horizontal = 2.dp)
            .testTag("inspector_tab_${tab.name.lowercase()}"),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) CinemaGold else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) CinemaGold else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
