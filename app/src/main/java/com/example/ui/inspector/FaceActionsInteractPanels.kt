package com.example.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ActionClip
import com.example.model.BackdropType
import com.example.model.CharacterActionType
import com.example.model.CharacterInteraction
import com.example.model.FacialExpression
import com.example.model.InteractionType
import com.example.model.PropType
import com.example.model.SceneCharacter
import com.example.model.SceneProp
import com.example.model.parseHexColor
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.RigCyan
import com.example.ui.theme.StudioSurfaceContainer
import com.example.ui.theme.VoiceCoral

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaceVoicePanel(
    character: SceneCharacter?,
    playheadMs: Long,
    onSetExpression: (String, FacialExpression) -> Unit,
    onUpdateDialogue: (String, String, Boolean) -> Unit,
    onUpdateVoice: (String, Float?, Float?, Float?) -> Unit,
    onPreviewVoice: (String, Float, Float) -> Unit,
) {
    if (character == null) {
        EmptySelectionPrompt("Select a character to control its facial expression, independent dialogue, voice pitch, and phoneme lip-sync.")
        return
    }

    val activeClip = character.actionClips.lastOrNull { playheadMs in it.startMs..it.endMs }
    var dialogueDraft by remember(character.characterId, activeClip?.clipId) {
        mutableStateOf(activeClip?.dialogueText.orEmpty())
    }
    var lipSyncChecked by remember(character.characterId, activeClip?.clipId) {
        mutableStateOf(activeClip?.lipSyncEnabled ?: true)
    }

    LaunchedEffect(activeClip?.dialogueText, activeClip?.lipSyncEnabled) {
        dialogueDraft = activeClip?.dialogueText.orEmpty()
        lipSyncChecked = activeClip?.lipSyncEnabled ?: true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("${character.name} · Facial Expressions")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FacialExpression.entries.forEach { expr ->
                val isSelected = (activeClip?.expression ?: character.expression) == expr
                FilterChip(
                    selected = isSelected,
                    onClick = { onSetExpression(character.characterId, expr) },
                    label = { Text(expr.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        SectionLabel("Independent Dialogue & Lip-Sync")
        OutlinedTextField(
            value = dialogueDraft,
            onValueChange = { next ->
                dialogueDraft = next.take(240)
                onUpdateDialogue(character.characterId, dialogueDraft, lipSyncChecked)
            },
            label = { Text("Spoken Line at Current Timecode") },
            placeholder = { Text("Type dialogue for ${character.name}…") },
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dialogue_input"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = lipSyncChecked,
                    onCheckedChange = { checked ->
                        lipSyncChecked = checked
                        onUpdateDialogue(character.characterId, dialogueDraft, checked)
                    },
                )
                Text("Phoneme Lip-Sync", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { onPreviewVoice(dialogueDraft, character.voicePitch, character.voiceRate) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoiceCoral,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("preview_voice_button"),
            ) {
                Icon(Icons.Rounded.RecordVoiceOver, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.preview_voice), style = MaterialTheme.typography.labelSmall)
            }
        }

        StudioSlider(
            label = "Voice Pitch",
            value = character.voicePitch,
            range = 0.55f..1.8f,
            valueText = String.format("%.2fx", character.voicePitch),
            accent = VoiceCoral,
            onValueChange = { onUpdateVoice(character.characterId, it, null, null) },
        )
        StudioSlider(
            label = "Speech Rate",
            value = character.voiceRate,
            range = 0.6f..1.6f,
            valueText = String.format("%.2fx", character.voiceRate),
            accent = VoiceCoral,
            onValueChange = { onUpdateVoice(character.characterId, null, it, null) },
        )
        StudioSlider(
            label = "Lip-Sync Intensity",
            value = character.lipSyncIntensity,
            range = 0f..1.5f,
            valueText = "${(character.lipSyncIntensity * 100).toInt()}%",
            accent = CinemaGold,
            onValueChange = { onUpdateVoice(character.characterId, null, null, it) },
        )
        Spacer(Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionsPanel(
    character: SceneCharacter?,
    playheadMs: Long,
    onAddActionClip: (String, CharacterActionType, Long, Long) -> Unit,
    onEditClip: (String, ActionClip) -> Unit,
    onDeleteClip: (String, String) -> Unit,
) {
    if (character == null) {
        EmptySelectionPrompt("Select a character to assign simultaneous actions (Walk, Sit, Stand, Talk, Listen, Eat, Fight, Dance, Hand Over, etc.).")
        return
    }

    var durationSec by remember { mutableFloatStateOf(2.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Assign Action at ${String.format("%.1fs", playheadMs / 1000f)}")
            Text(
                text = character.name,
                style = MaterialTheme.typography.labelMedium,
                color = CinemaGold,
            )
        }

        StudioSlider(
            label = "New Clip Duration",
            value = durationSec,
            range = 0.8f..6.0f,
            valueText = String.format("%.1fs", durationSec),
            onValueChange = { durationSec = it },
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CharacterActionType.entries.forEach { action ->
                val color = parseHexColor(action.colorHex, CinemaGold)
                Surface(
                    color = color.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .clickable {
                            onAddActionClip(
                                character.characterId,
                                action,
                                playheadMs,
                                (durationSec * 1000f).toLong(),
                            )
                        }
                        .testTag("action_chip_${action.name.lowercase()}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = color, modifier = Modifier.size(14.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (character.actionClips.isNotEmpty()) {
            SectionLabel("${character.name}'s Track Sequence (${character.actionClips.size})")
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                character.actionClips.forEach { clip ->
                    val c = parseHexColor(clip.action.colorHex, CinemaGold)
                    Surface(
                        color = StudioSurfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.8.dp, c.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable { onEditClip(character.characterId, clip) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(c, RoundedCornerShape(3.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${clip.action.label} (${String.format("%.1fs", clip.startMs / 1000f)} → ${String.format("%.1fs", clip.endMs / 1000f)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                )
                                if (clip.dialogueText.isNotBlank()) {
                                    Text(
                                        text = "\"${clip.dialogueText}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteClip(character.characterId, clip.clipId) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete action clip",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractionsPanel(
    characters: List<SceneCharacter>,
    props: List<SceneProp>,
    interactions: List<CharacterInteraction>,
    selectedCharacterId: String?,
    playheadMs: Long,
    onAddInteraction: (
        type: InteractionType,
        sourceId: String,
        targetId: String,
        propId: String,
        startMs: Long,
        durationMs: Long,
        emotionLabel: String,
    ) -> Unit,
    onDeleteInteraction: (String) -> Unit,
) {
    var selectedType by remember { mutableStateOf(InteractionType.CONVERSATION) }
    var sourceId by remember(selectedCharacterId, characters.size) {
        mutableStateOf(selectedCharacterId ?: characters.firstOrNull()?.characterId.orEmpty())
    }
    var targetId by remember(sourceId, characters.size) {
        mutableStateOf(
            characters.firstOrNull { it.characterId != sourceId }?.characterId.orEmpty()
        )
    }
    var selectedPropId by remember(props.size) {
        mutableStateOf(props.firstOrNull()?.propId.orEmpty())
    }
    var durationSec by remember { mutableFloatStateOf(2.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (characters.size < 2) {
            EmptySelectionPrompt("Add at least 2 characters to the scene to create character-to-character interactions (conversation, handshake, passing objects, reactions).")
            return
        }

        SectionLabel("Interaction Type")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            InteractionType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        SectionLabel("From Character")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            characters.forEach { ch ->
                FilterChip(
                    selected = sourceId == ch.characterId,
                    onClick = {
                        sourceId = ch.characterId
                        if (targetId == ch.characterId) {
                            targetId = characters.firstOrNull { it.characterId != ch.characterId }?.characterId.orEmpty()
                        }
                    },
                    label = { Text(ch.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        SectionLabel("With Target Character")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            characters.filter { it.characterId != sourceId }.forEach { ch ->
                FilterChip(
                    selected = targetId == ch.characterId,
                    onClick = { targetId = ch.characterId },
                    label = { Text(ch.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        if (selectedType.usesProp) {
            SectionLabel("Object / Prop to Pass or Give")
            if (props.isEmpty()) {
                Text(
                    text = "Add a prop in the Props tab first to pass objects between characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoiceCoral,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    props.forEach { prop ->
                        FilterChip(
                            selected = selectedPropId == prop.propId,
                            onClick = { selectedPropId = prop.propId },
                            label = { Text(prop.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }

        StudioSlider(
            label = "Duration (starts at ${String.format("%.1fs", playheadMs / 1000f)})",
            value = durationSec,
            range = 0.8f..6.0f,
            valueText = String.format("%.1fs", durationSec),
            accent = RigCyan,
            onValueChange = { durationSec = it },
        )

        Button(
            onClick = {
                onAddInteraction(
                    selectedType,
                    sourceId,
                    targetId,
                    selectedPropId,
                    playheadMs,
                    (durationSec * 1000f).toLong(),
                    selectedType.label,
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = RigCyan, contentColor = MaterialTheme.colorScheme.onSecondary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_interaction_button"),
        ) {
            Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Link ${selectedType.label}")
        }

        SectionLabel("Active Scene Interactions (${interactions.size})")
        if (interactions.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_interactions_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val nameMap = characters.associate { it.characterId to it.name }
            val propMap = props.associate { it.propId to it.name }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                interactions.forEach { inter ->
                    val sName = nameMap[inter.sourceCharacterId] ?: "Character"
                    val tName = nameMap[inter.targetCharacterId] ?: "Character"
                    val propSuffix = propMap[inter.propId]?.let { " · $it" } ?: ""
                    Surface(
                        color = StudioSurfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.8.dp, RigCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${inter.type.label}: $sName → $tName$propSuffix",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                )
                                Text(
                                    text = "${String.format("%.1fs", inter.startMs / 1000f)} – ${String.format("%.1fs", inter.endMs / 1000f)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RigCyan,
                                )
                            }
                            IconButton(
                                onClick = { onDeleteInteraction(inter.interactionId) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Rounded.Delete, "Delete interaction", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropsAndStagePanel(
    backdrop: BackdropType,
    durationMs: Long,
    props: List<SceneProp>,
    characters: List<SceneCharacter>,
    selectedCharacterId: String?,
    onSetBackdrop: (BackdropType) -> Unit,
    onSetDuration: (Long) -> Unit,
    onAddProp: (PropType, String) -> Unit,
    onAssignHolder: (String, String) -> Unit,
    onDeleteProp: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("Stage Backdrop")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BackdropType.entries.forEach { bg ->
                FilterChip(
                    selected = backdrop == bg,
                    onClick = { onSetBackdrop(bg) },
                    label = { Text(bg.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        StudioSlider(
            label = "Scene Timeline Length",
            value = durationMs / 1000f,
            range = 4f..30f,
            valueText = "${(durationMs / 1000L)}s",
            onValueChange = { onSetDuration((it.toLong()) * 1000L) },
        )

        SectionLabel("Add Interactive Prop / Object")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PropType.entries.forEach { propType ->
                Surface(
                    color = StudioSurfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(0.8.dp, CinemaGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable {
                            val holder = if (propType == PropType.CHAIR) "" else selectedCharacterId.orEmpty()
                            onAddProp(propType, holder)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = CinemaGold, modifier = Modifier.size(14.dp))
                        Text(propType.label, style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }

        SectionLabel("Props on Stage (${props.size})")
        if (props.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_props_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                props.forEach { prop ->
                    Surface(
                        color = StudioSurfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = prop.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onDeleteProp(prop.propId) },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(Icons.Rounded.Delete, "Remove prop", modifier = Modifier.size(16.dp))
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FilterChip(
                                    selected = prop.holderCharacterId.isBlank(),
                                    onClick = { onAssignHolder(prop.propId, "") },
                                    label = { Text("On Stage", style = MaterialTheme.typography.labelSmall) },
                                )
                                characters.forEach { ch ->
                                    FilterChip(
                                        selected = prop.holderCharacterId == ch.characterId,
                                        onClick = { onAssignHolder(prop.propId, ch.characterId) },
                                        label = { Text("Held by ${ch.name}", style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
