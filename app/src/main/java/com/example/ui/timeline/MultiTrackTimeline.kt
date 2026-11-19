package com.example.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ActionClip
import com.example.model.AnimationScene
import com.example.model.CharacterActionType
import com.example.model.CharacterInteraction
import com.example.model.SceneCharacter
import com.example.model.parseHexColor
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.RigCyan
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceContainer
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.theme.VoiceCoral
import kotlin.math.roundToLong

private const val DP_PER_SECOND = 68f

@Composable
fun MultiTrackTimeline(
    scene: AnimationScene,
    selectedCharacterId: String?,
    playheadMs: Long,
    isPlaying: Boolean,
    isLooping: Boolean,
    playbackSpeed: Float,
    voiceEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onToggleLoop: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleVoice: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectCharacter: (String) -> Unit,
    onAddKeyframe: (String) -> Unit,
    onQuickAddAction: (String) -> Unit,
    onEditClip: (String, ActionClip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = (scene.durationMs / 1000f).coerceAtLeast(2f)
    val trackWidthDp: Dp = (totalSeconds * DP_PER_SECOND).dp
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val pxPerMs = with(density) { (DP_PER_SECOND.dp.toPx()) / 1000f }

    val latestDurationMs by rememberUpdatedState(scene.durationMs)
    val latestOnSeek by rememberUpdatedState(onSeek)

    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .testTag("multi_character_timeline"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Transport Controls Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioSurfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = onRewind,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("timeline_rewind_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.rewind_scene),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onTogglePlay,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CinemaGold,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("timeline_play_button"),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.pause_scene else R.string.play_scene
                            ),
                        )
                    }

                    IconButton(
                        onClick = onToggleLoop,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("timeline_loop_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = stringResource(R.string.toggle_loop),
                            tint = if (isLooping) CinemaGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    IconButton(
                        onClick = onToggleVoice,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("timeline_voice_button"),
                    ) {
                        Icon(
                            imageVector = if (voiceEnabled) {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            },
                            contentDescription = stringResource(R.string.toggle_voice),
                            tint = if (voiceEnabled) VoiceCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                // Timecode + Speed + Pin Keyframe
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = StudioObsidian,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onCycleSpeed() },
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            style = TimecodeTextStyle,
                            color = RigCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }

                    Surface(
                        color = StudioObsidian,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "${formatTimecode(playheadMs)} / ${formatTimecode(scene.durationMs)}",
                            style = TimecodeTextStyle,
                            color = CinemaGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    if (selectedCharacterId != null) {
                        Surface(
                            color = CinemaGold.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .border(1.dp, CinemaGold.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .clickable { onAddKeyframe(selectedCharacterId) }
                                .testTag("pin_keyframe_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Diamond,
                                    contentDescription = stringResource(R.string.add_keyframe),
                                    tint = CinemaGold,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "Key",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CinemaGold,
                                )
                            }
                        }
                    }
                }
            }

            // Ruler Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(StudioObsidian),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "TRACKS (${scene.characters.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(horizontalScroll)
                        .pointerInput(pxPerMs) {
                            detectTapGestures { offset ->
                                val ms = (offset.x / pxPerMs).roundToLong().coerceIn(0L, latestDurationMs)
                                latestOnSeek(ms)
                            }
                        }
                        .pointerInput(pxPerMs) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val ms = (change.position.x / pxPerMs).roundToLong().coerceIn(0L, latestDurationMs)
                                latestOnSeek(ms)
                            }
                        }
                ) {
                    TimelineRulerCanvas(
                        durationMs = scene.durationMs,
                        playheadMs = playheadMs,
                        widthDp = trackWidthDp,
                    )
                }
            }

            // Independent Track per Character + Dedicated Interactions Track
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = scene.characters.sortedByDescending { it.layerOrder },
                    key = { it.characterId },
                ) { character ->
                    CharacterTimelineTrackRow(
                        character = character,
                        isSelected = character.characterId == selectedCharacterId,
                        durationMs = scene.durationMs,
                        playheadMs = playheadMs,
                        trackWidthDp = trackWidthDp,
                        scrollState = horizontalScroll,
                        pxPerMs = pxPerMs,
                        onSelect = { onSelectCharacter(character.characterId) },
                        onSeek = onSeek,
                        onQuickAddAction = { onQuickAddAction(character.characterId) },
                        onEditClip = { clip -> onEditClip(character.characterId, clip) },
                    )
                }

                if (scene.interactions.isNotEmpty()) {
                    item(key = "interactions_track") {
                        InteractionsTimelineTrackRow(
                            interactions = scene.interactions,
                            characters = scene.characters,
                            durationMs = scene.durationMs,
                            playheadMs = playheadMs,
                            trackWidthDp = trackWidthDp,
                            scrollState = horizontalScroll,
                            onSeek = onSeek,
                            pxPerMs = pxPerMs,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRulerCanvas(
    durationMs: Long,
    playheadMs: Long,
    widthDp: Dp,
) {
    Canvas(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
    ) {
        val totalSec = (durationMs / 1000f).coerceAtLeast(1f)
        val pxPerSec = size.width / totalSec
        for (sec in 0..totalSec.toInt()) {
            val x = sec * pxPerSec
            drawLine(
                color = Color(0xFF475569),
                start = Offset(x, size.height * 0.35f),
                end = Offset(x, size.height),
                strokeWidth = 1.6f,
            )
            if (sec < totalSec.toInt()) {
                val halfX = x + pxPerSec * 0.5f
                drawLine(
                    color = Color(0xFF334155),
                    start = Offset(halfX, size.height * 0.65f),
                    end = Offset(halfX, size.height),
                    strokeWidth = 1f,
                )
            }
        }
        val playX = (playheadMs.toFloat() / durationMs.coerceAtLeast(1L)) * size.width
        val tri = Path().apply {
            moveTo(playX - 6f, 2f)
            lineTo(playX + 6f, 2f)
            lineTo(playX, size.height - 2f)
            close()
        }
        drawPath(tri, color = CinemaGold)
    }
}

@Composable
private fun CharacterTimelineTrackRow(
    character: SceneCharacter,
    isSelected: Boolean,
    durationMs: Long,
    playheadMs: Long,
    trackWidthDp: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onSeek: (Long) -> Unit,
    onQuickAddAction: () -> Unit,
    onEditClip: (ActionClip) -> Unit,
) {
    val accent = parseHexColor(character.appearance.accentColorHex, CinemaGold)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestOnSeek by rememberUpdatedState(onSeek)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(
                if (isSelected) StudioSurfaceHigh else StudioSurfaceContainer.copy(alpha = 0.65f)
            )
            .clickable { onSelect() }
            .testTag("timeline_track_${character.characterId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Track Header (Name + Accent Dot + Lock/Visibility + Quick +Action)
        Row(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "${character.actionClips.size}a·${character.keyframes.size}k",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                    )
                    if (character.locked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = CinemaGold,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                    if (!character.visible) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Hidden",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .clickable { onQuickAddAction() },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add action clip",
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Scrollable Track Lane
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .pointerInput(pxPerMs) {
                    detectTapGestures { offset ->
                        val ms = (offset.x / pxPerMs).roundToLong().coerceIn(0L, latestDurationMs)
                        latestOnSeek(ms)
                        onSelect()
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .width(trackWidthDp)
                    .fillMaxHeight()
                    .background(StudioObsidian.copy(alpha = 0.55f))
            ) {
                // Entrance/Exit active lifespan span background
                val inDp = ((character.entranceMs / 1000f) * DP_PER_SECOND).dp
                val spanDp = (((character.exitMs - character.entranceMs).coerceAtLeast(300L) / 1000f) * DP_PER_SECOND).dp
                Box(
                    modifier = Modifier
                        .offset(x = inDp)
                        .width(spanDp)
                        .fillMaxHeight()
                        .background(accent.copy(alpha = 0.06f))
                )

                // Action Clips
                character.actionClips.forEach { clip ->
                    val startDp = ((clip.startMs / 1000f) * DP_PER_SECOND).dp
                    val widthDp = ((clip.durationMs / 1000f) * DP_PER_SECOND).dp.coerceAtLeast(26.dp)
                    val clipColor = parseHexColor(clip.action.colorHex, accent)
                    val hasVoice = clip.dialogueText.isNotBlank()

                    Box(
                        modifier = Modifier
                            .offset(x = startDp, y = 4.dp)
                            .width(widthDp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(clipColor.copy(alpha = 0.84f))
                            .border(
                                width = if (hasVoice) 1.2.dp else 0.7.dp,
                                color = if (hasVoice) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable {
                                onSelect()
                                onEditClip(clip)
                            }
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = if (hasVoice) "${clip.action.label} · ♫" else clip.action.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                        )
                    }
                }

                // Keyframe Diamonds along bottom edge
                character.keyframes.forEach { kf ->
                    val kfX = ((kf.timeMs / 1000f) * DP_PER_SECOND).dp
                    Icon(
                        imageVector = Icons.Rounded.Diamond,
                        contentDescription = "Keyframe",
                        tint = CinemaGold,
                        modifier = Modifier
                            .offset(x = (kfX - 5.dp).coerceAtLeast(0.dp), y = 29.dp)
                            .size(11.dp)
                            .clickable { onSeek(kf.timeMs) },
                    )
                }

                // Vertical Playhead Needle
                val playheadDp = ((playheadMs / 1000f) * DP_PER_SECOND).dp
                Box(
                    modifier = Modifier
                        .offset(x = playheadDp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(CinemaGold)
                )
            }
        }
    }
}

@Composable
private fun InteractionsTimelineTrackRow(
    interactions: List<CharacterInteraction>,
    characters: List<SceneCharacter>,
    durationMs: Long,
    playheadMs: Long,
    trackWidthDp: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onSeek: (Long) -> Unit,
    pxPerMs: Float,
) {
    val nameMap = characters.associate { it.characterId to it.name }
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestOnSeek by rememberUpdatedState(onSeek)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(StudioSurfaceContainer.copy(alpha = 0.45f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = RigCyan,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "Interact",
                style = MaterialTheme.typography.labelSmall,
                color = RigCyan,
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .pointerInput(pxPerMs) {
                    detectTapGestures { offset ->
                        val ms = (offset.x / pxPerMs).roundToLong().coerceIn(0L, latestDurationMs)
                        latestOnSeek(ms)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .width(trackWidthDp)
                    .fillMaxHeight()
                    .background(StudioObsidian.copy(alpha = 0.45f))
            ) {
                interactions.forEach { inter ->
                    val startDp = ((inter.startMs / 1000f) * DP_PER_SECOND).dp
                    val widthDp = ((inter.durationMs / 1000f) * DP_PER_SECOND).dp.coerceAtLeast(36.dp)
                    val src = nameMap[inter.sourceCharacterId]?.take(4) ?: "A"
                    val dst = nameMap[inter.targetCharacterId]?.take(4) ?: "B"
                    Box(
                        modifier = Modifier
                            .offset(x = startDp, y = 4.dp)
                            .width(widthDp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(RigCyan.copy(alpha = 0.28f))
                            .border(1.dp, RigCyan.copy(alpha = 0.85f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "${inter.type.label} ($src↔$dst)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 9.sp,
                        )
                    }
                }

                val playheadDp = ((playheadMs / 1000f) * DP_PER_SECOND).dp
                Box(
                    modifier = Modifier
                        .offset(x = playheadDp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(CinemaGold)
                )
            }
        }
    }
}

fun formatTimecode(ms: Long): String {
    val totalTenths = (ms / 100L).coerceAtLeast(0L)
    val secs = totalTenths / 10L
    val tenths = totalTenths % 10L
    val mins = secs / 60L
    val remSecs = secs % 60L
    return if (mins > 0) {
        String.format("%d:%02d.%d", mins, remSecs, tenths)
    } else {
        String.format("0:%02d.%d", remSecs, tenths)
    }
}
