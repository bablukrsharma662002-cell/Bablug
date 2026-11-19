package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StageCraftRepository
import com.example.model.ActionClip
import com.example.model.AnimationScene
import com.example.model.BackdropType
import com.example.model.BodyPose
import com.example.model.CharacterActionType
import com.example.model.CharacterAppearance
import com.example.model.CharacterInteraction
import com.example.model.CharacterKeyframe
import com.example.model.CharacterTemplate
import com.example.model.DepthPlane
import com.example.model.FacialExpression
import com.example.model.InteractionType
import com.example.model.PropType
import com.example.model.SceneCharacter
import com.example.model.SceneProp
import com.example.model.StarterContent
import com.example.model.TransitionStyle
import com.example.model.newUid
import com.example.ui.theme.CharacterTrackPalette
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class SaveStatus {
    SYNCED,
    MODIFIED,
    SAVING,
    ERROR,
}

enum class InspectorTab {
    CAST,
    RIG,
    FACE_VOICE,
    ACTIONS,
    INTERACT,
    PROPS_STAGE,
}

data class CopiedActionTrack(
    val sourceName: String,
    val clips: List<ActionClip>,
    val keyframes: List<CharacterKeyframe>,
)

data class StudioUiState(
    val scene: AnimationScene = StarterContent.createShowcaseScene(),
    val selectedCharacterId: String? = "char_mia_1",
    val selectedPropId: String? = null,
    val playheadMs: Long = 3800L,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val voiceEnabled: Boolean = true,
    val activeTab: InspectorTab = InspectorTab.CAST,
    val saveStatus: SaveStatus = SaveStatus.MODIFIED,
    val statusBanner: String? = null,
    val clipboard: CopiedActionTrack? = null,
    val showLibrarySheet: Boolean = false,
    val libraryReplaceTargetId: String? = null,
    val showCreateCharacterDialog: Boolean = false,
    val editingClipTarget: Pair<String, ActionClip>? = null,
    val showScenesSheet: Boolean = false,
) {
    val selectedCharacter: SceneCharacter?
        get() = scene.characters.firstOrNull { it.characterId == selectedCharacterId }

    val selectedProp: SceneProp?
        get() = scene.props.firstOrNull { it.propId == selectedPropId }
}

class StudioViewModel(
    private val repository: StageCraftRepository,
    private val currentUserId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StudioUiState(scene = StarterContent.createShowcaseScene(currentUserId))
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var hasAutoLoadedCloudScene = false

    val savedScenesState: StateFlow<UiState<List<AnimationScene>>> =
        repository.observeScenes(currentUserId)
            .map<List<AnimationScene>, UiState<List<AnimationScene>>> { scenes ->
                if (!hasAutoLoadedCloudScene && scenes.isNotEmpty() && _uiState.value.scene.id.isBlank()) {
                    hasAutoLoadedCloudScene = true
                    val latest = scenes.first()
                    _uiState.update {
                        it.copy(
                            scene = latest,
                            selectedCharacterId = latest.characters.lastOrNull()?.characterId,
                            selectedPropId = null,
                            saveStatus = SaveStatus.SYNCED,
                        )
                    }
                }
                UiState.Success(scenes)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing scenes", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load cloud scenes"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val libraryCharactersState: StateFlow<UiState<List<CharacterTemplate>>> =
        repository.observeCharacterLibrary(currentUserId)
            .map<List<CharacterTemplate>, UiState<List<CharacterTemplate>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing character library", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load character library"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    // --- Navigation / Dialog Toggles ---

    fun selectTab(tab: InspectorTab) = _uiState.update { it.copy(activeTab = tab) }
    fun clearBanner() = _uiState.update { it.copy(statusBanner = null) }

    fun openLibraryToAdd() = _uiState.update {
        it.copy(showLibrarySheet = true, libraryReplaceTargetId = null)
    }

    fun openLibraryToReplace(characterId: String) = _uiState.update {
        it.copy(showLibrarySheet = true, libraryReplaceTargetId = characterId)
    }

    fun closeLibrarySheet() = _uiState.update {
        it.copy(showLibrarySheet = false, libraryReplaceTargetId = null)
    }

    fun openCreateCharacterDialog() = _uiState.update { it.copy(showCreateCharacterDialog = true) }
    fun closeCreateCharacterDialog() = _uiState.update { it.copy(showCreateCharacterDialog = false) }
    fun openScenesSheet() = _uiState.update { it.copy(showScenesSheet = true) }
    fun closeScenesSheet() = _uiState.update { it.copy(showScenesSheet = false) }

    fun openClipEditor(characterId: String, clip: ActionClip) = _uiState.update {
        it.copy(editingClipTarget = characterId to clip)
    }

    fun closeClipEditor() = _uiState.update { it.copy(editingClipTarget = null) }

    // --- Playback Controls ---

    fun togglePlay() = _uiState.update { state ->
        val restart = !state.isPlaying && state.playheadMs >= state.scene.durationMs - 40L
        state.copy(
            isPlaying = !state.isPlaying,
            playheadMs = if (restart) 0L else state.playheadMs,
        )
    }

    fun setPlaying(playing: Boolean) = _uiState.update { it.copy(isPlaying = playing) }
    fun seekTo(ms: Long) = _uiState.update { state ->
        state.copy(playheadMs = ms.coerceIn(0L, state.scene.durationMs))
    }

    fun advancePlayhead(deltaMs: Long) = _uiState.update { state ->
        if (!state.isPlaying) return@update state
        val next = state.playheadMs + (deltaMs * state.playbackSpeed).toLong()
        if (next >= state.scene.durationMs) {
            if (state.isLooping) {
                state.copy(playheadMs = 0L)
            } else {
                state.copy(playheadMs = state.scene.durationMs, isPlaying = false)
            }
        } else {
            state.copy(playheadMs = next.coerceAtLeast(0L))
        }
    }

    fun rewind() = _uiState.update { it.copy(playheadMs = 0L) }
    fun toggleLoop() = _uiState.update { it.copy(isLooping = !it.isLooping) }
    fun toggleVoice() = _uiState.update { it.copy(voiceEnabled = !it.voiceEnabled) }

    fun cyclePlaybackSpeed() = _uiState.update { state ->
        val next = when {
            state.playbackSpeed < 0.8f -> 1.0f
            state.playbackSpeed < 1.3f -> 1.5f
            else -> 0.5f
        }
        state.copy(playbackSpeed = next)
    }

    // --- Scene & Cloud Persistence ---

    private fun mutateScene(
        banner: String? = null,
        transform: (AnimationScene) -> AnimationScene,
    ) {
        _uiState.update { state ->
            state.copy(
                scene = transform(state.scene),
                saveStatus = SaveStatus.MODIFIED,
                statusBanner = banner ?: state.statusBanner,
            )
        }
    }

    fun saveCurrentSceneToCloud() {
        val sceneToSave = _uiState.value.scene
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        viewModelScope.launch {
            val result = repository.saveScene(sceneToSave)
            result.onSuccess { docId ->
                hasAutoLoadedCloudScene = true
                _uiState.update { state ->
                    state.copy(
                        scene = state.scene.copy(id = docId, userId = currentUserId),
                        saveStatus = SaveStatus.SYNCED,
                        statusBanner = "Scene saved to cloud",
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        saveStatus = SaveStatus.ERROR,
                        statusBanner = err.localizedMessage ?: "Failed to save scene",
                    )
                }
            }
        }
    }

    fun loadScene(scene: AnimationScene) {
        hasAutoLoadedCloudScene = true
        _uiState.update {
            it.copy(
                scene = scene,
                selectedCharacterId = scene.characters.lastOrNull()?.characterId,
                selectedPropId = null,
                playheadMs = 0L,
                isPlaying = false,
                saveStatus = SaveStatus.SYNCED,
                showScenesSheet = false,
                statusBanner = "Loaded \"${scene.title}\"",
            )
        }
    }

    fun createNewScene(title: String, useShowcaseStarter: Boolean) {
        val fresh = if (useShowcaseStarter) {
            StarterContent.createShowcaseScene(currentUserId).copy(
                id = "",
                title = title.trim().ifBlank { "Park Meetup" }.take(80),
            )
        } else {
            AnimationScene(
                id = "",
                userId = currentUserId,
                title = title.trim().ifBlank { "New Scene" }.take(80),
                backdrop = BackdropType.STUDIO,
                durationMs = 12000L,
            )
        }
        hasAutoLoadedCloudScene = true
        _uiState.update {
            it.copy(
                scene = fresh,
                selectedCharacterId = fresh.characters.lastOrNull()?.characterId,
                selectedPropId = null,
                playheadMs = 0L,
                isPlaying = false,
                saveStatus = SaveStatus.MODIFIED,
                showScenesSheet = false,
                statusBanner = "Created \"${fresh.title}\"",
            )
        }
    }

    fun deleteCloudScene(sceneId: String) {
        viewModelScope.launch {
            val result = repository.deleteScene(sceneId)
            result.onSuccess {
                _uiState.update { state ->
                    val cleared = if (state.scene.id == sceneId) {
                        state.scene.copy(id = "")
                    } else {
                        state.scene
                    }
                    state.copy(scene = cleared, statusBanner = "Scene deleted from cloud")
                }
            }.onFailure { err ->
                _uiState.update { it.copy(statusBanner = err.localizedMessage ?: "Delete failed") }
            }
        }
    }

    fun renameScene(title: String) {
        val clean = title.trim().ifBlank { return }.take(80)
        mutateScene { it.copy(title = clean) }
    }

    fun setBackdrop(backdrop: BackdropType) {
        mutateScene { it.copy(backdrop = backdrop) }
    }

    fun setSceneDuration(durationMs: Long) {
        val clamped = durationMs.coerceIn(4000L, 30000L)
        _uiState.update { state ->
            state.copy(
                scene = state.scene.copy(
                    durationMs = clamped,
                    characters = state.scene.characters.map { ch ->
                        if (ch.exitMs == state.scene.durationMs || ch.exitMs > clamped) {
                            ch.copy(exitMs = clamped)
                        } else ch
                    },
                ),
                playheadMs = state.playheadMs.coerceAtMost(clamped),
                saveStatus = SaveStatus.MODIFIED,
            )
        }
    }

    // --- Selection ---

    fun selectCharacter(characterId: String?) {
        _uiState.update { it.copy(selectedCharacterId = characterId, selectedPropId = null) }
    }

    fun selectProp(propId: String?) {
        _uiState.update { it.copy(selectedPropId = propId, selectedCharacterId = null) }
    }

    // --- Multiple Character CRUD ---

    fun addCharacterFromTemplate(template: CharacterTemplate) {
        val currentChars = _uiState.value.scene.characters
        val replaceId = _uiState.value.libraryReplaceTargetId
        if (replaceId != null) {
            replaceCharacterAppearance(replaceId, template)
            return
        }
        val nextLayer = (currentChars.maxOfOrNull { it.layerOrder } ?: -1) + 1
        val spawnX = (0.20f + ((currentChars.size * 0.16f) % 0.64f)).coerceIn(0.15f, 0.85f)
        val spawnY = if (currentChars.size % 2 == 0) 0.74f else 0.70f
        val newChar = SceneCharacter(
            characterId = newUid("char"),
            name = uniqueCharacterName(template.name, currentChars),
            appearance = template.toAppearance(),
            x = spawnX,
            y = spawnY,
            scale = 1.0f,
            facingRight = spawnX < 0.5f,
            depthPlane = DepthPlane.MIDGROUND,
            layerOrder = nextLayer,
            expression = FacialExpression.HAPPY,
            voicePitch = template.voicePitch.toFloat().coerceIn(0.5f, 2.0f),
            voiceRate = template.voiceRate.toFloat().coerceIn(0.5f, 2.0f),
            entranceMs = 0L,
            exitMs = _uiState.value.scene.durationMs,
            actionClips = listOf(
                ActionClip(
                    action = CharacterActionType.IDLE,
                    startMs = 0L,
                    durationMs = 3000L,
                    expression = FacialExpression.HAPPY,
                )
            ),
        )
        _uiState.update { state ->
            state.copy(
                scene = state.scene.copy(characters = state.scene.characters + newChar),
                selectedCharacterId = newChar.characterId,
                selectedPropId = null,
                showLibrarySheet = false,
                saveStatus = SaveStatus.MODIFIED,
                statusBanner = "Added ${newChar.name} to scene",
            )
        }
    }

    fun createCharacterInScene(
        name: String,
        appearance: CharacterAppearance,
        voicePitch: Float,
        voiceRate: Float,
        saveToLibrary: Boolean,
    ) {
        val cleanName = name.trim().ifBlank { "Character" }.take(60)
        val template = CharacterTemplate(
            name = cleanName,
            skinColorHex = appearance.skinColorHex,
            hairColorHex = appearance.hairColorHex,
            outfitColorHex = appearance.outfitColorHex,
            pantsColorHex = appearance.pantsColorHex,
            accentColorHex = appearance.accentColorHex,
            hairStyle = appearance.hairStyle.name,
            accessory = appearance.accessory.name,
            voicePitch = voicePitch.toDouble(),
            voiceRate = voiceRate.toDouble(),
        )
        addCharacterFromTemplate(template)
        _uiState.update { it.copy(showCreateCharacterDialog = false) }
        if (saveToLibrary) {
            saveTemplateToCloudLibrary(template)
        }
    }

    fun saveCharacterToLibrary(characterId: String) {
        val ch = _uiState.value.scene.characters.firstOrNull { it.characterId == characterId } ?: return
        val template = CharacterTemplate(
            name = ch.name,
            skinColorHex = ch.appearance.skinColorHex,
            hairColorHex = ch.appearance.hairColorHex,
            outfitColorHex = ch.appearance.outfitColorHex,
            pantsColorHex = ch.appearance.pantsColorHex,
            accentColorHex = ch.appearance.accentColorHex,
            hairStyle = ch.appearance.hairStyle.name,
            accessory = ch.appearance.accessory.name,
            voicePitch = ch.voicePitch.toDouble(),
            voiceRate = ch.voiceRate.toDouble(),
        )
        saveTemplateToCloudLibrary(template)
    }

    private fun saveTemplateToCloudLibrary(template: CharacterTemplate) {
        viewModelScope.launch {
            repository.saveCharacterTemplate(template)
                .onSuccess {
                    _uiState.update { it.copy(statusBanner = "Saved ${template.name} to Character Library") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusBanner = err.localizedMessage ?: "Failed to save character") }
                }
        }
    }

    fun deleteTemplateFromCloudLibrary(templateId: String) {
        if (templateId.startsWith("preset_")) return
        viewModelScope.launch {
            repository.deleteCharacterTemplate(templateId)
                .onSuccess {
                    _uiState.update { it.copy(statusBanner = "Removed from Character Library") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusBanner = err.localizedMessage ?: "Delete failed") }
                }
        }
    }

    fun replaceCharacterAppearance(characterId: String, template: CharacterTemplate) {
        mutateScene(banner = "Replaced character with ${template.name}") { scene ->
            scene.copy(
                characters = scene.characters.map { ch ->
                    if (ch.characterId == characterId) {
                        ch.copy(
                            name = template.name,
                            appearance = template.toAppearance(),
                            voicePitch = template.voicePitch.toFloat().coerceIn(0.5f, 2.0f),
                            voiceRate = template.voiceRate.toFloat().coerceIn(0.5f, 2.0f),
                        )
                    } else ch
                }
            )
        }
        _uiState.update { it.copy(showLibrarySheet = false, libraryReplaceTargetId = null) }
    }

    fun duplicateCharacter(characterId: String, includeAnimation: Boolean = true) {
        val currentChars = _uiState.value.scene.characters
        val source = currentChars.firstOrNull { it.characterId == characterId } ?: return
        val nextLayer = (currentChars.maxOfOrNull { it.layerOrder } ?: 0) + 1
        val accentHex = CharacterTrackPalette[currentChars.size % CharacterTrackPalette.size]
        val clone = source.copy(
            characterId = newUid("char"),
            name = uniqueCharacterName("${source.name} Copy", currentChars),
            appearance = source.appearance.copy(accentColorHex = accentHex),
            x = (source.x + 0.11f).coerceIn(0.12f, 0.88f),
            y = (source.y + 0.02f).coerceIn(0.40f, 0.88f),
            layerOrder = nextLayer,
            locked = false,
            actionClips = if (includeAnimation) {
                source.actionClips.map { it.copy(clipId = newUid("clip")) }
            } else emptyList(),
            keyframes = if (includeAnimation) {
                source.keyframes.map {
                    it.copy(
                        keyframeId = newUid("kf"),
                        x = (it.x + 0.11f).coerceIn(0.10f, 0.90f),
                    )
                }
            } else emptyList(),
        )
        _uiState.update { state ->
            state.copy(
                scene = state.scene.copy(characters = state.scene.characters + clone),
                selectedCharacterId = clone.characterId,
                saveStatus = SaveStatus.MODIFIED,
                statusBanner = if (includeAnimation) {
                    "Duplicated ${source.name} with animation"
                } else {
                    "Duplicated ${source.name}"
                },
            )
        }
    }

    fun removeCharacter(characterId: String) {
        _uiState.update { state ->
            val remaining = state.scene.characters.filterNot { it.characterId == characterId }
            val cleanedProps = state.scene.props.map { prop ->
                if (prop.holderCharacterId == characterId) prop.copy(holderCharacterId = "") else prop
            }
            val cleanedInts = state.scene.interactions.filterNot {
                it.sourceCharacterId == characterId || it.targetCharacterId == characterId
            }
            state.copy(
                scene = state.scene.copy(
                    characters = remaining,
                    props = cleanedProps,
                    interactions = cleanedInts,
                ),
                selectedCharacterId = if (state.selectedCharacterId == characterId) {
                    remaining.lastOrNull()?.characterId
                } else state.selectedCharacterId,
                saveStatus = SaveStatus.MODIFIED,
                statusBanner = "Character removed",
            )
        }
    }

    fun renameCharacter(characterId: String, newName: String) {
        val clean = newName.trim().ifBlank { return }.take(60)
        updateCharacter(characterId) { it.copy(name = clean) }
    }

    // --- Layer Management ---

    fun toggleLockCharacter(characterId: String) {
        updateCharacter(characterId) { it.copy(locked = !it.locked) }
    }

    fun toggleVisibilityCharacter(characterId: String) {
        updateCharacter(characterId) { it.copy(visible = !it.visible) }
    }

    fun moveCharacterLayer(characterId: String, delta: Int) {
        mutateScene { scene ->
            val sorted = scene.characters.sortedBy { it.layerOrder }.toMutableList()
            val idx = sorted.indexOfFirst { it.characterId == characterId }
            if (idx == -1) return@mutateScene scene
            val targetIdx = (idx + delta).coerceIn(0, sorted.lastIndex)
            if (idx == targetIdx) return@mutateScene scene
            val item = sorted.removeAt(idx)
            sorted.add(targetIdx, item)
            val reindexed = sorted.mapIndexed { i, ch -> ch.copy(layerOrder = i) }
            scene.copy(characters = reindexed)
        }
    }

    fun assignGroup(characterId: String, groupName: String) {
        updateCharacter(characterId) { it.copy(groupId = groupName) }
    }

    fun toggleGroupWithSelected(characterId: String) {
        val chars = _uiState.value.scene.characters
        val target = chars.firstOrNull { it.characterId == characterId } ?: return
        if (target.groupId.isNotBlank()) {
            updateCharacter(characterId) { it.copy(groupId = "") }
            _uiState.update { it.copy(statusBanner = "Ungrouped ${target.name}") }
        } else {
            val activeGroup = _uiState.value.selectedCharacter?.groupId?.takeIf { it.isNotBlank() } ?: "Group A"
            updateCharacter(characterId) { it.copy(groupId = activeGroup) }
            _uiState.update { it.copy(statusBanner = "Added ${target.name} to $activeGroup") }
        }
    }

    fun ungroupAllInGroup(groupId: String) {
        if (groupId.isBlank()) return
        mutateScene(banner = "Ungrouped $groupId") { scene ->
            scene.copy(
                characters = scene.characters.map { ch ->
                    if (ch.groupId == groupId) ch.copy(groupId = "") else ch
                }
            )
        }
    }

    // --- Copy / Paste / Duplicate Character Actions ---

    fun copyCharacterActions(characterId: String) {
        val ch = _uiState.value.scene.characters.firstOrNull { it.characterId == characterId } ?: return
        _uiState.update {
            it.copy(
                clipboard = CopiedActionTrack(
                    sourceName = ch.name,
                    clips = ch.actionClips,
                    keyframes = ch.keyframes,
                ),
                statusBanner = "Copied ${ch.actionClips.size} actions from ${ch.name}",
            )
        }
    }

    fun pasteCharacterActions(targetCharacterId: String) {
        val clip = _uiState.value.clipboard ?: return
        val target = _uiState.value.scene.characters.firstOrNull { it.characterId == targetCharacterId } ?: return
        if (target.locked) {
            _uiState.update { it.copy(statusBanner = "${target.name} is locked") }
            return
        }
        updateCharacter(targetCharacterId) { ch ->
            ch.copy(
                actionClips = clip.clips.map { it.copy(clipId = newUid("clip")) },
                keyframes = clip.keyframes.map {
                    it.copy(
                        keyframeId = newUid("kf"),
                        x = ch.x,
                        y = ch.y,
                    )
                },
            )
        }
        _uiState.update { it.copy(statusBanner = "Pasted actions from ${clip.sourceName} onto ${target.name}") }
    }

    // --- Transform, Depth, Entrance/Exit, Pose, Face & Voice ---

    fun moveCharacterByDelta(characterId: String, dxNorm: Float, dyNorm: Float) {
        val scene = _uiState.value.scene
        val dragged = scene.characters.firstOrNull { it.characterId == characterId } ?: return
        if (dragged.locked) return
        val groupToMove = dragged.groupId
        val playhead = _uiState.value.playheadMs

        mutateScene { current ->
            current.copy(
                characters = current.characters.map { ch ->
                    val shouldMove = ch.characterId == characterId ||
                        (groupToMove.isNotBlank() && ch.groupId == groupToMove && !ch.locked)
                    if (!shouldMove) {
                        ch
                    } else {
                        val nextX = (ch.x + dxNorm).coerceIn(0.06f, 0.94f)
                        val nextY = (ch.y + dyNorm).coerceIn(0.34f, 0.92f)
                        // Also shift matching keyframe at playhead if one exists within 200ms
                        val updatedKfs = ch.keyframes.map { kf ->
                            if (kotlin.math.abs(kf.timeMs - playhead) <= 220L) {
                                kf.copy(
                                    x = (kf.x + dxNorm).coerceIn(0.06f, 0.94f),
                                    y = (kf.y + dyNorm).coerceIn(0.34f, 0.92f),
                                )
                            } else kf
                        }
                        ch.copy(x = nextX, y = nextY, keyframes = updatedKfs)
                    }
                }
            )
        }
    }

    fun updateCharacterTransform(
        characterId: String,
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotationDeg: Float? = null,
        facingRight: Boolean? = null,
        depthPlane: DepthPlane? = null,
    ) {
        updateCharacterIfUnlocked(characterId) { ch ->
            ch.copy(
                x = (x ?: ch.x).coerceIn(0.06f, 0.94f),
                y = (y ?: ch.y).coerceIn(0.34f, 0.92f),
                scale = (scale ?: ch.scale).coerceIn(0.45f, 2.0f),
                rotationDeg = (rotationDeg ?: ch.rotationDeg).coerceIn(-180f, 180f),
                facingRight = facingRight ?: ch.facingRight,
                depthPlane = depthPlane ?: ch.depthPlane,
            )
        }
    }

    fun updateCharacterEntranceExit(
        characterId: String,
        entranceMs: Long? = null,
        exitMs: Long? = null,
        entranceStyle: TransitionStyle? = null,
        exitStyle: TransitionStyle? = null,
    ) {
        val maxMs = _uiState.value.scene.durationMs
        updateCharacterIfUnlocked(characterId) { ch ->
            val newIn = (entranceMs ?: ch.entranceMs).coerceIn(0L, maxMs - 400L)
            val newOut = (exitMs ?: ch.exitMs).coerceIn(newIn + 400L, maxMs)
            ch.copy(
                entranceMs = newIn,
                exitMs = newOut,
                entranceStyle = entranceStyle ?: ch.entranceStyle,
                exitStyle = exitStyle ?: ch.exitStyle,
            )
        }
    }

    fun updateCharacterExpressionAndGaze(
        characterId: String,
        expression: FacialExpression? = null,
        eyeLookX: Float? = null,
        eyeLookY: Float? = null,
        lookAtCharacterId: String? = null,
    ) {
        val playhead = _uiState.value.playheadMs
        updateCharacterIfUnlocked(characterId) { ch ->
            val updatedClips = if (expression != null) {
                ch.actionClips.map { clip ->
                    if (playhead in clip.startMs..clip.endMs) clip.copy(expression = expression) else clip
                }
            } else ch.actionClips
            ch.copy(
                expression = expression ?: ch.expression,
                eyeLookX = (eyeLookX ?: ch.eyeLookX).coerceIn(-1f, 1f),
                eyeLookY = (eyeLookY ?: ch.eyeLookY).coerceIn(-1f, 1f),
                lookAtCharacterId = lookAtCharacterId ?: ch.lookAtCharacterId,
                actionClips = updatedClips,
            )
        }
    }

    fun updateCharacterPose(characterId: String, transform: (BodyPose) -> BodyPose) {
        updateCharacterIfUnlocked(characterId) { ch ->
            ch.copy(bodyPose = transform(ch.bodyPose))
        }
    }

    fun resetCharacterPose(characterId: String) {
        updateCharacterIfUnlocked(characterId) { it.copy(bodyPose = BodyPose()) }
    }

    fun updateCharacterVoice(
        characterId: String,
        voicePitch: Float? = null,
        voiceRate: Float? = null,
        lipSyncIntensity: Float? = null,
    ) {
        updateCharacterIfUnlocked(characterId) { ch ->
            ch.copy(
                voicePitch = (voicePitch ?: ch.voicePitch).coerceIn(0.5f, 2.0f),
                voiceRate = (voiceRate ?: ch.voiceRate).coerceIn(0.5f, 2.0f),
                lipSyncIntensity = (lipSyncIntensity ?: ch.lipSyncIntensity).coerceIn(0f, 1.5f),
            )
        }
    }

    fun updateActiveDialogue(characterId: String, dialogueText: String, lipSyncEnabled: Boolean) {
        val playhead = _uiState.value.playheadMs
        val maxMs = _uiState.value.scene.durationMs
        updateCharacterIfUnlocked(characterId) { ch ->
            val activeIdx = ch.actionClips.indexOfLast { playhead in it.startMs..it.endMs }
            if (activeIdx >= 0) {
                val updated = ch.actionClips.toMutableList()
                val old = updated[activeIdx]
                updated[activeIdx] = old.copy(
                    dialogueText = dialogueText.take(240),
                    lipSyncEnabled = lipSyncEnabled,
                    action = if (dialogueText.isNotBlank() && old.action == CharacterActionType.IDLE) {
                        CharacterActionType.TALK
                    } else old.action,
                )
                ch.copy(actionClips = updated)
            } else {
                val newClip = ActionClip(
                    action = CharacterActionType.TALK,
                    startMs = playhead.coerceAtMost((maxMs - 1000L).coerceAtLeast(0L)),
                    durationMs = 2800L.coerceAtMost((maxMs - playhead).coerceAtLeast(800L)),
                    expression = ch.expression,
                    dialogueText = dialogueText.take(240),
                    lipSyncEnabled = lipSyncEnabled,
                )
                ch.copy(actionClips = (ch.actionClips + newClip).sortedBy { it.startMs })
            }
        }
    }

    // --- Action Clips & Keyframes ---

    fun addActionClip(
        characterId: String,
        action: CharacterActionType,
        startMs: Long = _uiState.value.playheadMs,
        durationMs: Long = 2400L,
        dialogue: String = "",
    ) {
        val maxMs = _uiState.value.scene.durationMs
        val clampedStart = startMs.coerceIn(0L, (maxMs - 600L).coerceAtLeast(0L))
        val clampedDur = durationMs.coerceIn(500L, (maxMs - clampedStart).coerceAtLeast(500L))
        updateCharacterIfUnlocked(characterId) { ch ->
            val newClip = ActionClip(
                action = action,
                startMs = clampedStart,
                durationMs = clampedDur,
                expression = ch.expression,
                dialogueText = dialogue.take(240),
                lipSyncEnabled = true,
            )
            ch.copy(actionClips = (ch.actionClips + newClip).sortedBy { it.startMs })
        }
        _uiState.update { it.copy(statusBanner = "Added ${action.label} clip") }
    }

    fun updateActionClip(characterId: String, updatedClip: ActionClip) {
        val maxMs = _uiState.value.scene.durationMs
        val clampedStart = updatedClip.startMs.coerceIn(0L, (maxMs - 400L).coerceAtLeast(0L))
        val clampedDur = updatedClip.durationMs.coerceIn(400L, (maxMs - clampedStart).coerceAtLeast(400L))
        val clean = updatedClip.copy(
            startMs = clampedStart,
            durationMs = clampedDur,
            dialogueText = updatedClip.dialogueText.take(240),
        )
        updateCharacterIfUnlocked(characterId) { ch ->
            ch.copy(
                actionClips = ch.actionClips.map { if (it.clipId == clean.clipId) clean else it }
                    .sortedBy { it.startMs }
            )
        }
        _uiState.update { it.copy(editingClipTarget = null) }
    }

    fun deleteActionClip(characterId: String, clipId: String) {
        updateCharacterIfUnlocked(characterId) { ch ->
            ch.copy(actionClips = ch.actionClips.filterNot { it.clipId == clipId })
        }
        _uiState.update { it.copy(editingClipTarget = null) }
    }

    fun addOrUpdateKeyframeAtPlayhead(characterId: String) {
        val playhead = _uiState.value.playheadMs
        updateCharacterIfUnlocked(characterId) { ch ->
            val newKf = CharacterKeyframe(
                timeMs = playhead,
                x = ch.x,
                y = ch.y,
                scale = ch.scale,
                rotationDeg = ch.rotationDeg,
                facingRight = ch.facingRight,
                expression = ch.expression,
                eyeLookX = ch.eyeLookX,
                eyeLookY = ch.eyeLookY,
                pose = ch.bodyPose,
            )
            val filtered = ch.keyframes.filterNot { kotlin.math.abs(it.timeMs - playhead) <= 150L }
            ch.copy(keyframes = (filtered + newKf).sortedBy { it.timeMs })
        }
        _uiState.update {
            it.copy(statusBanner = "Keyframe pinned at ${String.format("%.1fs", playhead / 1000f)}")
        }
    }

    fun deleteKeyframe(characterId: String, keyframeId: String) {
        updateCharacterIfUnlocked(characterId) { ch ->
            ch.copy(keyframes = ch.keyframes.filterNot { it.keyframeId == keyframeId })
        }
    }

    // --- Props ---

    fun addProp(type: PropType, holderCharacterId: String = "") {
        val existingCount = _uiState.value.scene.props.size
        val prop = SceneProp(
            name = type.label,
            type = type,
            x = (0.30f + ((existingCount * 0.14f) % 0.45f)),
            y = 0.78f,
            scale = 1.0f,
            holderCharacterId = holderCharacterId,
        )
        _uiState.update { state ->
            state.copy(
                scene = state.scene.copy(props = state.scene.props + prop),
                selectedPropId = prop.propId,
                saveStatus = SaveStatus.MODIFIED,
                statusBanner = "Added ${prop.name} prop",
            )
        }
    }

    fun assignPropHolder(propId: String, holderCharacterId: String) {
        mutateScene { scene ->
            scene.copy(
                props = scene.props.map { prop ->
                    if (prop.propId == propId) prop.copy(holderCharacterId = holderCharacterId) else prop
                }
            )
        }
    }

    fun movePropByDelta(propId: String, dxNorm: Float, dyNorm: Float) {
        mutateScene { scene ->
            scene.copy(
                props = scene.props.map { prop ->
                    if (prop.propId == propId) {
                        prop.copy(
                            holderCharacterId = "",
                            x = (prop.x + dxNorm).coerceIn(0.06f, 0.94f),
                            y = (prop.y + dyNorm).coerceIn(0.35f, 0.92f),
                        )
                    } else prop
                }
            )
        }
    }

    fun deleteProp(propId: String) {
        _uiState.update { state ->
            state.copy(
                scene = state.scene.copy(
                    props = state.scene.props.filterNot { it.propId == propId },
                    interactions = state.scene.interactions.map { inter ->
                        if (inter.propId == propId) inter.copy(propId = "") else inter
                    },
                ),
                selectedPropId = if (state.selectedPropId == propId) null else state.selectedPropId,
                saveStatus = SaveStatus.MODIFIED,
            )
        }
    }

    // --- Character Interactions ---

    fun addInteraction(
        type: InteractionType,
        sourceCharacterId: String,
        targetCharacterId: String,
        propId: String,
        startMs: Long,
        durationMs: Long,
        emotionLabel: String = "",
    ) {
        if (sourceCharacterId.isBlank() || targetCharacterId.isBlank() || sourceCharacterId == targetCharacterId) {
            _uiState.update { it.copy(statusBanner = "Pick two different characters to interact") }
            return
        }
        val maxMs = _uiState.value.scene.durationMs
        val clampedStart = startMs.coerceIn(0L, (maxMs - 500L).coerceAtLeast(0L))
        val clampedDur = durationMs.coerceIn(500L, (maxMs - clampedStart).coerceAtLeast(500L))
        val newInter = CharacterInteraction(
            type = type,
            sourceCharacterId = sourceCharacterId,
            targetCharacterId = targetCharacterId,
            propId = if (type.usesProp) propId else "",
            startMs = clampedStart,
            durationMs = clampedDur,
            emotionLabel = emotionLabel.take(40),
        )
        mutateScene(banner = "Linked ${type.label} interaction") { scene ->
            val updatedProps = if (type.usesProp && propId.isNotBlank()) {
                scene.props.map { prop ->
                    if (prop.propId == propId && prop.holderCharacterId.isBlank()) {
                        prop.copy(holderCharacterId = sourceCharacterId)
                    } else prop
                }
            } else scene.props
            scene.copy(
                props = updatedProps,
                interactions = (scene.interactions + newInter).sortedBy { it.startMs },
            )
        }
    }

    fun deleteInteraction(interactionId: String) {
        mutateScene(banner = "Interaction removed") { scene ->
            scene.copy(interactions = scene.interactions.filterNot { it.interactionId == interactionId })
        }
    }

    // --- Helpers ---

    private fun updateCharacter(
        characterId: String,
        transform: (SceneCharacter) -> SceneCharacter,
    ) {
        mutateScene { scene ->
            scene.copy(
                characters = scene.characters.map { ch ->
                    if (ch.characterId == characterId) transform(ch) else ch
                }
            )
        }
    }

    private fun updateCharacterIfUnlocked(
        characterId: String,
        transform: (SceneCharacter) -> SceneCharacter,
    ) {
        val ch = _uiState.value.scene.characters.firstOrNull { it.characterId == characterId } ?: return
        if (ch.locked) {
            _uiState.update { it.copy(statusBanner = "${ch.name} is locked — unlock in Cast to edit") }
            return
        }
        updateCharacter(characterId, transform)
    }

    private fun uniqueCharacterName(base: String, existing: List<SceneCharacter>): String {
        val trimmed = base.trim().ifBlank { "Character" }
        if (existing.none { it.name.equals(trimmed, ignoreCase = true) }) return trimmed
        var idx = 2
        while (existing.any { it.name.equals("$trimmed $idx", ignoreCase = true) }) {
            idx++
        }
        return "$trimmed $idx"
    }

    private companion object {
        const val TAG = "StudioViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
