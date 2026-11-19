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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.GroupRemove
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BodyPose
import com.example.model.DepthPlane
import com.example.model.SceneCharacter
import com.example.model.TransitionStyle
import com.example.model.parseHexColor
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.RigCyan
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceContainer
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.VoiceCoral
import com.example.viewmodel.CopiedActionTrack

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CastLayersPanel(
    characters: List<SceneCharacter>,
    selectedCharacterId: String?,
    clipboard: CopiedActionTrack?,
    onSelect: (String) -> Unit,
    onAddFromLibrary: () -> Unit,
    onCreateNew: () -> Unit,
    onToggleLock: (String) -> Unit,
    onToggleVisible: (String) -> Unit,
    onMoveLayer: (String, Int) -> Unit,
    onDuplicate: (String) -> Unit,
    onReplace: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onSaveToLibrary: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
    onUngroupAll: (String) -> Unit,
    onCopyActions: (String) -> Unit,
    onPasteActions: (String) -> Unit,
) {
    var renamingChar by remember { mutableStateOf<SceneCharacter?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Quick Cast Creation Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onCreateNew,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaGold,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_character_button"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Character", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            FilledTonalButton(
                onClick = onAddFromLibrary,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_from_library_button"),
            ) {
                Icon(Icons.Rounded.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("From Library", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Active Groups Banner
        val activeGroups = characters.map { it.groupId }.filter { it.isNotBlank() }.distinct()
        if (activeGroups.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                activeGroups.forEach { group ->
                    Surface(
                        color = RigCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onUngroupAll(group) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "$group (${characters.count { it.groupId == group }})",
                                style = MaterialTheme.typography.labelSmall,
                                color = RigCyan,
                            )
                            Icon(
                                imageVector = Icons.Rounded.GroupRemove,
                                contentDescription = "Ungroup $group",
                                tint = RigCyan,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        if (characters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.empty_cast_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = characters.sortedByDescending { it.layerOrder },
                    key = { it.characterId },
                ) { ch ->
                    val isSelected = ch.characterId == selectedCharacterId
                    val accent = parseHexColor(ch.appearance.accentColorHex, CinemaGold)
                    Surface(
                        color = if (isSelected) StudioSurfaceHigh else StudioSurfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 1.3.dp else 0.7.dp,
                                color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(ch.characterId) }
                            .testTag("layer_card_${ch.characterId}"),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(ch.appearance.outfitColorHex))
                                        .border(2.dp, accent, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = ch.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(
                                            text = ch.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (ch.groupId.isNotBlank()) {
                                            Text(
                                                text = ch.groupId,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = RigCyan,
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${ch.depthPlane.label} · Layer ${ch.layerOrder + 1} · ${ch.actionClips.size} actions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                // Quick Layer Controls: Up, Down, Hide/Show, Lock
                                IconButton(
                                    onClick = { onMoveLayer(ch.characterId, 1) },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, "Move layer up", modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = { onMoveLayer(ch.characterId, -1) },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, "Move layer down", modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = { onToggleVisible(ch.characterId) },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        imageVector = if (ch.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = "Toggle visibility",
                                        tint = if (ch.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleLock(ch.characterId) },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        imageVector = if (ch.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                        contentDescription = "Toggle lock",
                                        tint = if (ch.locked) CinemaGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }

                            // Expanded Action Chips for Selected Character
                            if (isSelected) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    CompactLayerChip(
                                        icon = Icons.Rounded.Edit,
                                        label = "Rename",
                                        onClick = {
                                            renamingChar = ch
                                            renameText = ch.name
                                        },
                                    )
                                    CompactLayerChip(
                                        icon = Icons.Rounded.ContentCopy,
                                        label = "Duplicate",
                                        onClick = { onDuplicate(ch.characterId) },
                                    )
                                    CompactLayerChip(
                                        icon = Icons.Rounded.SwapHoriz,
                                        label = "Replace",
                                        onClick = { onReplace(ch.characterId) },
                                    )
                                    CompactLayerChip(
                                        icon = if (ch.groupId.isBlank()) Icons.Rounded.GroupAdd else Icons.Rounded.GroupRemove,
                                        label = if (ch.groupId.isBlank()) "Group" else "Ungroup",
                                        tint = RigCyan,
                                        onClick = { onToggleGroup(ch.characterId) },
                                    )
                                    CompactLayerChip(
                                        icon = Icons.Rounded.ContentCopy,
                                        label = "Copy Anim",
                                        onClick = { onCopyActions(ch.characterId) },
                                    )
                                    if (clipboard != null) {
                                        CompactLayerChip(
                                            icon = Icons.Rounded.ContentPaste,
                                            label = "Paste Anim",
                                            tint = CinemaGold,
                                            onClick = { onPasteActions(ch.characterId) },
                                        )
                                    }
                                    CompactLayerChip(
                                        icon = Icons.Rounded.BookmarkAdd,
                                        label = "Save Lib",
                                        onClick = { onSaveToLibrary(ch.characterId) },
                                    )
                                    CompactLayerChip(
                                        icon = Icons.Rounded.Delete,
                                        label = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                        onClick = { onDelete(ch.characterId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val targetRename = renamingChar
    if (targetRename != null) {
        AlertDialog(
            onDismissRequest = { renamingChar = null },
            title = { Text("Rename Character") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(60) },
                    label = { Text("Character Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(targetRename.characterId, renameText)
                        renamingChar = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingChar = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CompactLayerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        color = StudioObsidian,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .border(0.8.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransformAndRigPanel(
    character: SceneCharacter?,
    allCharacters: List<SceneCharacter>,
    sceneDurationMs: Long,
    onUpdateTransform: (
        characterId: String,
        x: Float?,
        y: Float?,
        scale: Float?,
        rotationDeg: Float?,
        facingRight: Boolean?,
        depthPlane: DepthPlane?,
    ) -> Unit,
    onUpdateEntranceExit: (
        characterId: String,
        entranceMs: Long?,
        exitMs: Long?,
        entranceStyle: TransitionStyle?,
        exitStyle: TransitionStyle?,
    ) -> Unit,
    onUpdateGaze: (String, Float?, Float?, String?) -> Unit,
    onUpdatePose: (String, (BodyPose) -> BodyPose) -> Unit,
    onResetPose: (String) -> Unit,
    onAddKeyframe: (String) -> Unit,
    onDeleteKeyframe: (String, String) -> Unit,
    onSeek: (Long) -> Unit,
) {
    if (character == null) {
        EmptySelectionPrompt("Tap any character on stage or in Cast to control its transform, depth plane, eye gaze, and articulated body rig.")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header + Flip + Pin Keyframe
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${character.name} · Transform & Rig",
                style = MaterialTheme.typography.titleSmall,
                color = CinemaGold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        onUpdateTransform(character.characterId, null, null, null, null, !character.facingRight, null)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Rounded.Flip, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (character.facingRight) "Facing R" else "Facing L", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { onAddKeyframe(character.characterId) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaGold, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Icon(Icons.Rounded.Diamond, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ Keyframe", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Depth Plane Positioning (Background / Midground / Foreground)
        SectionLabel("Foreground / Background Depth")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DepthPlane.entries.forEach { plane ->
                FilterChip(
                    selected = character.depthPlane == plane,
                    onClick = { onUpdateTransform(character.characterId, null, null, null, null, null, plane) },
                    label = { Text(plane.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        // Transform Sliders
        StudioSlider(
            label = "Position X",
            value = character.x,
            range = 0.06f..0.94f,
            valueText = "${(character.x * 100).toInt()}%",
            onValueChange = { onUpdateTransform(character.characterId, it, null, null, null, null, null) },
        )
        StudioSlider(
            label = "Position Y",
            value = character.y,
            range = 0.34f..0.92f,
            valueText = "${(character.y * 100).toInt()}%",
            onValueChange = { onUpdateTransform(character.characterId, null, it, null, null, null, null) },
        )
        StudioSlider(
            label = "Scale",
            value = character.scale,
            range = 0.45f..2.0f,
            valueText = String.format("%.2fx", character.scale),
            onValueChange = { onUpdateTransform(character.characterId, null, null, it, null, null, null) },
        )
        StudioSlider(
            label = "Rotation",
            value = character.rotationDeg,
            range = -180f..180f,
            valueText = "${character.rotationDeg.toInt()}°",
            onValueChange = { onUpdateTransform(character.characterId, null, null, null, it, null, null) },
        )

        // Head & Eye Direction Controls
        SectionLabel("Eyes & Head Direction")
        StudioSlider(
            label = "Head Tilt",
            value = character.bodyPose.headTilt,
            range = -35f..35f,
            valueText = "${character.bodyPose.headTilt.toInt()}°",
            accent = RigCyan,
            onValueChange = { v -> onUpdatePose(character.characterId) { it.copy(headTilt = v) } },
        )
        StudioSlider(
            label = "Eye Look Left / Right",
            value = character.eyeLookX,
            range = -1f..1f,
            valueText = String.format("%.1f", character.eyeLookX),
            accent = RigCyan,
            onValueChange = { onUpdateGaze(character.characterId, it, null, "") },
        )
        StudioSlider(
            label = "Eye Look Up / Down",
            value = character.eyeLookY,
            range = -1f..1f,
            valueText = String.format("%.1f", character.eyeLookY),
            accent = RigCyan,
            onValueChange = { onUpdateGaze(character.characterId, null, it, "") },
        )

        // Auto Look-At Character
        val others = allCharacters.filter { it.characterId != character.characterId }
        if (others.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = character.lookAtCharacterId.isBlank(),
                    onClick = { onUpdateGaze(character.characterId, null, null, "") },
                    label = { Text("Free Gaze", style = MaterialTheme.typography.labelSmall) },
                )
                others.forEach { other ->
                    FilterChip(
                        selected = character.lookAtCharacterId == other.characterId,
                        onClick = { onUpdateGaze(character.characterId, null, null, other.characterId) },
                        label = { Text("Look at ${other.name}", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        // Body-Part Articulated Rig Sliders
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("Independent Body-Part Rig")
            TextButton(onClick = { onResetPose(character.characterId) }) {
                Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Pose", style = MaterialTheme.typography.labelSmall)
            }
        }

        StudioSlider(
            label = "Torso Lean",
            value = character.bodyPose.torsoLean,
            range = -25f..25f,
            valueText = "${character.bodyPose.torsoLean.toInt()}°",
            onValueChange = { v -> onUpdatePose(character.characterId) { it.copy(torsoLean = v) } },
        )
        StudioSlider(
            label = "Left Arm",
            value = character.bodyPose.leftArmAngle,
            range = -150f..150f,
            valueText = "${character.bodyPose.leftArmAngle.toInt()}°",
            onValueChange = { v -> onUpdatePose(character.characterId) { it.copy(leftArmAngle = v) } },
        )
        StudioSlider(
            label = "Right Arm",
            value = character.bodyPose.rightArmAngle,
            range = -150f..150f,
            valueText = "${character.bodyPose.rightArmAngle.toInt()}°",
            onValueChange = { v -> onUpdatePose(character.characterId) { it.copy(rightArmAngle = v) } },
        )
        StudioSlider(
            label = "Left Elbow",
            value = character.bodyPose.leftElbowAngle,
            range = -110f..110f,
            valueText = "${character.bodyPose.leftElbowAngle.toInt()}°",
            onValueChange = { v -> onUpdatePose(character.characterId) { it.copy(leftElbowAngle = v) } },
        )
        StudioSlider(
            label = "Right Elbow",
            value = character.bodyPose.rightElbowAngle,
            range = -110f..110f,
            valueText = "${character.bodyPose.rightElbowAngle.toInt()}°",
            onValueChange = { v -> onUpdatePose(character.characterId) { it.copy(rightElbowAngle = v) } },
        )
        StudioSlider(
            label = "Left Leg",
            value = character.bodyPose.leftLegAngle,
            range = -60f..60f,
            valueText = "${character.bodyPose.leftLegAngle.toInt()}°",
            onValueChange = { v -> onUpdatePose(character.characterId) { it.copy(leftLegAngle = v) } },
        )
        StudioSlider(
            label = "Right Leg",
            value = character.bodyPose.rightLegAngle,
            range = -60f..60f,
            valueText = "${character.bodyPose.rightLegAngle.toInt()}°",
            onValueChange = { v -> onUpdatePose(character.characterId) { it.copy(rightLegAngle = v) } },
        )

        // Character Entrance & Exit Timing + Styles
        SectionLabel("Entrance & Exit Timing")
        StudioSlider(
            label = "Entrance Time",
            value = character.entranceMs.toFloat(),
            range = 0f..(sceneDurationMs - 500L).coerceAtLeast(500L).toFloat(),
            valueText = String.format("%.1fs", character.entranceMs / 1000f),
            accent = VoiceCoral,
            onValueChange = { onUpdateEntranceExit(character.characterId, it.toLong(), null, null, null) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TransitionStyle.entries.forEach { style ->
                FilterChip(
                    selected = character.entranceStyle == style,
                    onClick = { onUpdateEntranceExit(character.characterId, null, null, style, null) },
                    label = { Text("In: ${style.label}", style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        StudioSlider(
            label = "Exit Time",
            value = character.exitMs.toFloat(),
            range = (character.entranceMs + 500L).coerceAtMost(sceneDurationMs).toFloat()..sceneDurationMs.toFloat(),
            valueText = String.format("%.1fs", character.exitMs / 1000f),
            accent = VoiceCoral,
            onValueChange = { onUpdateEntranceExit(character.characterId, null, it.toLong(), null, null) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TransitionStyle.entries.forEach { style ->
                FilterChip(
                    selected = character.exitStyle == style,
                    onClick = { onUpdateEntranceExit(character.characterId, null, null, null, style) },
                    label = { Text("Out: ${style.label}", style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        // Keyframes list
        if (character.keyframes.isNotEmpty()) {
            SectionLabel("Pinned Keyframes (${character.keyframes.size})")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                character.keyframes.forEach { kf ->
                    Surface(
                        color = StudioSurfaceContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(1.dp, CinemaGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { onSeek(kf.timeMs) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Rounded.Diamond, null, tint = CinemaGold, modifier = Modifier.size(12.dp))
                            Text(
                                text = String.format("%.1fs", kf.timeMs / 1000f),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete keyframe",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onDeleteKeyframe(character.characterId, kf.keyframeId) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun StudioSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    accent: Color = CinemaGold,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = valueText, style = MaterialTheme.typography.labelSmall, color = accent)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.height(28.dp),
        )
    }
}

@Composable
fun EmptySelectionPrompt(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
