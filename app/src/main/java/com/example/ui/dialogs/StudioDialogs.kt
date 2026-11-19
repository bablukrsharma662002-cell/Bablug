package com.example.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.engine.EvaluatedCharacterState
import com.example.engine.MouthShape
import com.example.model.ActionClip
import com.example.model.AnimationScene
import com.example.model.BodyPose
import com.example.model.CharacterAccessory
import com.example.model.CharacterActionType
import com.example.model.CharacterAppearance
import com.example.model.CharacterTemplate
import com.example.model.FacialExpression
import com.example.model.HairStyle
import com.example.model.SceneCharacter
import com.example.model.StarterContent
import com.example.model.parseHexColor
import com.example.ui.canvas.drawCharacterRig
import com.example.ui.inspector.SectionLabel
import com.example.ui.inspector.StudioSlider
import com.example.ui.theme.CharacterTrackPalette
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.HairColorPalette
import com.example.ui.theme.OutfitColorPalette
import com.example.ui.theme.RigCyan
import com.example.ui.theme.SkinTonePalette
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceContainer
import com.example.ui.theme.VoiceCoral
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateCharacterDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        appearance: CharacterAppearance,
        voicePitch: Float,
        voiceRate: Float,
        saveToLibrary: Boolean,
    ) -> Unit,
) {
    BackHandler { onDismiss() }

    var name by remember { mutableStateOf("Nova") }
    var skinHex by remember { mutableStateOf(SkinTonePalette[1]) }
    var hairHex by remember { mutableStateOf(HairColorPalette[0]) }
    var outfitHex by remember { mutableStateOf(OutfitColorPalette[0]) }
    var pantsHex by remember { mutableStateOf("#1E293B") }
    var accentHex by remember { mutableStateOf(CharacterTrackPalette[0]) }
    var hairStyle by remember { mutableStateOf(HairStyle.SPIKY) }
    var accessory by remember { mutableStateOf(CharacterAccessory.HEADPHONES) }
    var voicePitch by remember { mutableFloatStateOf(1.05f) }
    var voiceRate by remember { mutableFloatStateOf(1.0f) }
    var saveToLibrary by remember { mutableStateOf(true) }

    val previewAppearance = CharacterAppearance(
        skinColorHex = skinHex,
        hairColorHex = hairHex,
        outfitColorHex = outfitHex,
        pantsColorHex = pantsHex,
        accentColorHex = accentHex,
        hairStyle = hairStyle,
        accessory = accessory,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Create New Character",
                        style = MaterialTheme.typography.titleLarge,
                        color = CinemaGold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                // Live Articulated Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioObsidian)
                        .border(1.dp, parseHexColor(accentHex).copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                        val previewChar = SceneCharacter(
                            characterId = "preview",
                            name = name,
                            appearance = previewAppearance,
                        )
                        val eval = EvaluatedCharacterState(
                            character = previewChar,
                            x = 0.5f,
                            y = 0.88f,
                            scale = 1.0f,
                            rotationDeg = 0f,
                            facingRight = true,
                            alpha = 1f,
                            expression = FacialExpression.HAPPY,
                            eyeLookX = 0.2f,
                            eyeLookY = 0f,
                            blinkClosed = false,
                            mouthShape = MouthShape.SMILE,
                            activeDialogue = "",
                            activeAction = CharacterActionType.IDLE,
                            pose = BodyPose(rightArmAngle = -115f, rightElbowAngle = -20f),
                            sitFactor = 0f,
                            bobOffsetY = 0f,
                            handAnchorX = 0.5f,
                            handAnchorY = 0.6f,
                            emotionBadge = "",
                        )
                        drawCharacterRig(eval, isSelected = false, baseUnit = size.height * 0.52f)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Character Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_character_name_input"),
                )

                SectionLabel("Skin Tone")
                ColorSwatchRow(SkinTonePalette, skinHex) { skinHex = it }

                SectionLabel("Hair Color & Style")
                ColorSwatchRow(HairColorPalette, hairHex) { hairHex = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HairStyle.entries.forEach { style ->
                        FilterChip(
                            selected = hairStyle == style,
                            onClick = { hairStyle = style },
                            label = { Text(style.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                SectionLabel("Outfit & Track Color")
                ColorSwatchRow(OutfitColorPalette, outfitHex) {
                    outfitHex = it
                    accentHex = it
                }

                SectionLabel("Accessory")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CharacterAccessory.entries.forEach { acc ->
                        FilterChip(
                            selected = accessory == acc,
                            onClick = { accessory = acc },
                            label = { Text(acc.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                StudioSlider(
                    label = "Voice Pitch",
                    value = voicePitch,
                    range = 0.6f..1.7f,
                    valueText = String.format("%.2fx", voicePitch),
                    accent = VoiceCoral,
                    onValueChange = { voicePitch = it },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Save to Cloud Character Library", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = saveToLibrary, onCheckedChange = { saveToLibrary = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onCreate(name, previewAppearance, voicePitch, voiceRate, saveToLibrary)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinemaGold,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_create_character_button"),
                    ) {
                        Text("Add to Scene")
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    colors: List<String>,
    selectedHex: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { hex ->
            val isSelected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex))
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) CinemaGold else Color.White.copy(alpha = 0.25f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
fun CharacterLibraryDialog(
    libraryState: UiState<List<CharacterTemplate>>,
    isReplaceMode: Boolean,
    onDismiss: () -> Unit,
    onSelectTemplate: (CharacterTemplate) -> Unit,
    onDeleteCloudTemplate: (String) -> Unit,
    onCreateNewInstead: () -> Unit,
) {
    BackHandler { onDismiss() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isReplaceMode) "Replace Character From Library" else "Saved Character Library",
                            style = MaterialTheme.typography.titleMedium,
                            color = CinemaGold,
                        )
                        Text(
                            text = if (isReplaceMode) {
                                "Pick a look & voice; existing timeline stays intact."
                            } else {
                                "Add unlimited characters to your current scene."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close")
                    }
                }

                val cloudChars = (libraryState as? UiState.Success)?.data ?: emptyList()

                if (libraryState is UiState.Loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Syncing saved characters…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (libraryState is UiState.Error) {
                    Text(
                        text = libraryState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (cloudChars.isNotEmpty()) {
                        item { SectionLabel("My Saved Cloud Characters (${cloudChars.size})") }
                        items(cloudChars, key = { it.id }) { tpl ->
                            LibraryCharacterRow(
                                template = tpl,
                                canDelete = true,
                                actionLabel = if (isReplaceMode) "Replace" else "Add",
                                onSelect = { onSelectTemplate(tpl) },
                                onDelete = { onDeleteCloudTemplate(tpl.id) },
                            )
                        }
                    }

                    item { SectionLabel("Studio Cast Presets") }
                    items(StarterContent.builtInLibrary, key = { it.id }) { tpl ->
                        LibraryCharacterRow(
                            template = tpl,
                            canDelete = false,
                            actionLabel = if (isReplaceMode) "Replace" else "Add",
                            onSelect = { onSelectTemplate(tpl) },
                            onDelete = {},
                        )
                    }
                }

                if (!isReplaceMode) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onCreateNewInstead()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Custom Character")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCharacterRow(
    template: CharacterTemplate,
    canDelete: Boolean,
    actionLabel: String,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = StudioSurfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(template.outfitColorHex))
                    .border(2.dp, parseHexColor(template.accentColorHex), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = template.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Text(
                    text = "${HairStyle.fromRaw(template.hairStyle).label} · ${CharacterAccessory.fromRaw(template.accessory).label} · Pitch ${String.format("%.1fx", template.voicePitch)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Delete, "Delete saved character", modifier = Modifier.size(16.dp))
                }
            }
            Surface(
                color = CinemaGold,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioObsidian,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionClipEditorDialog(
    characterName: String,
    clip: ActionClip,
    sceneDurationMs: Long,
    onDismiss: () -> Unit,
    onSave: (ActionClip) -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler { onDismiss() }

    var action by remember { mutableStateOf(clip.action) }
    var expression by remember { mutableStateOf(clip.expression) }
    var startSec by remember { mutableFloatStateOf(clip.startMs / 1000f) }
    var durSec by remember { mutableFloatStateOf(clip.durationMs / 1000f) }
    var dialogue by remember { mutableStateOf(clip.dialogueText) }
    var lipSync by remember { mutableStateOf(clip.lipSyncEnabled) }
    val maxSec = (sceneDurationMs / 1000f).coerceAtLeast(2f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Clip · $characterName") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel("Action")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CharacterActionType.entries.forEach { type ->
                        FilterChip(
                            selected = action == type,
                            onClick = { action = type },
                            label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                StudioSlider(
                    label = "Start Time",
                    value = startSec,
                    range = 0f..(maxSec - 0.5f).coerceAtLeast(0.5f),
                    valueText = String.format("%.1fs", startSec),
                    onValueChange = { startSec = it },
                )
                StudioSlider(
                    label = "Duration",
                    value = durSec,
                    range = 0.5f..(maxSec - startSec).coerceAtLeast(0.5f),
                    valueText = String.format("%.1fs", durSec),
                    onValueChange = { durSec = it },
                )

                SectionLabel("Expression")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FacialExpression.entries.forEach { expr ->
                        FilterChip(
                            selected = expression == expr,
                            onClick = { expression = expr },
                            label = { Text(expr.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                OutlinedTextField(
                    value = dialogue,
                    onValueChange = { dialogue = it.take(240) },
                    label = { Text("Dialogue (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enable Lip-Sync", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = lipSync, onCheckedChange = { lipSync = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        clip.copy(
                            action = action,
                            expression = expression,
                            startMs = (startSec * 1000f).toLong(),
                            durationMs = (durSec * 1000f).toLong(),
                            dialogueText = dialogue,
                            lipSyncEnabled = lipSync,
                        )
                    )
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ScenesManagerDialog(
    currentScene: AnimationScene,
    scenesState: UiState<List<AnimationScene>>,
    userEmail: String?,
    onDismiss: () -> Unit,
    onRenameCurrent: (String) -> Unit,
    onCreateScene: (String, Boolean) -> Unit,
    onLoadScene: (AnimationScene) -> Unit,
    onDeleteScene: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    BackHandler { onDismiss() }

    var sceneTitleDraft by remember(currentScene.id, currentScene.title) {
        mutableStateOf(currentScene.title)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Cloud Scenes & Studio", style = MaterialTheme.typography.titleMedium, color = CinemaGold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close")
                    }
                }

                // Current scene title editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = sceneTitleDraft,
                        onValueChange = { sceneTitleDraft = it.take(80) },
                        label = { Text("Current Scene Title") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { onRenameCurrent(sceneTitleDraft) }) {
                        Text("Set")
                    }
                }

                // Quick New Scene Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onCreateScene("New Multi-Cast Scene", false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Blank Scene", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onCreateScene("Park Meetup Demo", true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Movie, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("3-Cast Demo", style = MaterialTheme.typography.labelSmall)
                    }
                }

                SectionLabel("Saved Cloud Scenes")
                when (scenesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        }
                    }

                    is UiState.Error -> {
                        Text(scenesState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    is UiState.Success -> {
                        if (scenesState.data.isEmpty()) {
                            Text(
                                text = "No saved scenes yet. Tap Save in the top studio bar to sync this scene to the cloud.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(scenesState.data, key = { it.id }) { scene ->
                                    val isActive = scene.id == currentScene.id
                                    Surface(
                                        color = StudioSurfaceContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = if (isActive) 1.2.dp else 0.6.dp,
                                                color = if (isActive) CinemaGold else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                            .clickable { onLoadScene(scene) },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(scene.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                                Text(
                                                    text = "${scene.characters.size} cast · ${scene.props.size} props · ${scene.interactions.size} interactions",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = RigCyan,
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteScene(scene.id) },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(Icons.Rounded.Delete, "Delete scene", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Account Footer + Sign Out
                Surface(
                    color = StudioObsidian,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = userEmail ?: "Signed in with Google",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }
        }
    }
}
