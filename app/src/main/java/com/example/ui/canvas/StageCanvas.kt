package com.example.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.EvaluatedCharacterState
import com.example.engine.EvaluatedFrame
import com.example.engine.EvaluatedInteractionVisual
import com.example.engine.EvaluatedPropState
import com.example.engine.MouthShape
import com.example.model.BackdropType
import com.example.model.CharacterAccessory
import com.example.model.CharacterAppearance
import com.example.model.FacialExpression
import com.example.model.HairStyle
import com.example.model.PropType
import com.example.model.parseHexColor
import com.example.ui.theme.CinemaGold
import com.example.ui.theme.RigCyan
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.VoiceCoral
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun StageCanvas(
    frame: EvaluatedFrame,
    backdrop: BackdropType,
    selectedCharacterId: String?,
    selectedPropId: String?,
    onSelectCharacter: (String?) -> Unit,
    onSelectProp: (String?) -> Unit,
    onDragCharacter: (String, Float, Float) -> Unit,
    onDragProp: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestFrame by rememberUpdatedState(frame)
    val latestOnSelectChar by rememberUpdatedState(onSelectCharacter)
    val latestOnSelectProp by rememberUpdatedState(onSelectProp)
    val latestOnDragChar by rememberUpdatedState(onDragCharacter)
    val latestOnDragProp by rememberUpdatedState(onDragProp)

    var draggingCharId by remember { mutableStateOf<String?>(null) }
    var draggingPropId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(StudioObsidian)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .testTag("stage_viewport_canvas")
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        // 1. Stage Backdrop Art
        when (backdrop) {
            BackdropType.PARK -> {
                Image(
                    painter = painterResource(id = R.drawable.img_stage_park_1795066749357),
                    contentDescription = "Park Plaza stage backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            BackdropType.CAFE -> {
                Image(
                    painter = painterResource(id = R.drawable.img_stage_cafe_1795066750001),
                    contentDescription = "Cozy Cafe stage backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            BackdropType.STUDIO, BackdropType.NIGHT_SKY -> {
                ProceduralBackdropCanvas(backdrop = backdrop, modifier = Modifier.fillMaxSize())
            }
        }

        // 2. Interactive Multi-Character & Props Canvas (Hardware-Accelerated)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val hitChar = findHitCharacter(latestFrame.characters, offset, w, h)
                        if (hitChar != null) {
                            latestOnSelectChar(hitChar.character.characterId)
                            return@detectTapGestures
                        }
                        val hitProp = findHitProp(latestFrame.props, offset, w, h)
                        if (hitProp != null) {
                            latestOnSelectProp(hitProp.prop.propId)
                            return@detectTapGestures
                        }
                        latestOnSelectChar(null)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val hitChar = findHitCharacter(latestFrame.characters, startOffset, w, h)
                            if (hitChar != null) {
                                latestOnSelectChar(hitChar.character.characterId)
                                draggingCharId = if (!hitChar.character.locked) hitChar.character.characterId else null
                                draggingPropId = null
                            } else {
                                val hitProp = findHitProp(latestFrame.props, startOffset, w, h)
                                if (hitProp != null) {
                                    latestOnSelectProp(hitProp.prop.propId)
                                    draggingPropId = hitProp.prop.propId
                                    draggingCharId = null
                                } else {
                                    draggingCharId = null
                                    draggingPropId = null
                                }
                            }
                        },
                        onDragEnd = {
                            draggingCharId = null
                            draggingPropId = null
                        },
                        onDragCancel = {
                            draggingCharId = null
                            draggingPropId = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val dx = dragAmount.x / w
                            val dy = dragAmount.y / h
                            val cid = draggingCharId
                            val pid = draggingPropId
                            if (cid != null) {
                                latestOnDragChar(cid, dx, dy)
                            } else if (pid != null) {
                                latestOnDragProp(pid, dx, dy)
                            }
                        },
                    )
                }
        ) {
            val baseUnit = size.height * 0.24f

            // Background unheld furniture/props (e.g. Chair) rendered behind characters
            frame.props
                .filter { it.holderId.isBlank() && !it.inTransit && it.prop.type == PropType.CHAIR }
                .forEach { propState ->
                    drawPropItem(
                        propState = propState,
                        isSelected = propState.prop.propId == selectedPropId,
                        baseUnit = baseUnit,
                    )
                }

            // Active character-to-character interaction arcs
            frame.activeInteractions.forEach { inter ->
                drawInteractionArc(inter)
            }

            // Independent articulated characters in strict DepthPlane + LayerOrder Z-order
            frame.characters.forEach { charState ->
                drawCharacterRig(
                    state = charState,
                    isSelected = charState.character.characterId == selectedCharacterId,
                    baseUnit = baseUnit,
                )
            }

            // Foreground / held / mid-pass props
            frame.props
                .filter { it.holderId.isNotBlank() || it.inTransit || it.prop.type != PropType.CHAIR }
                .forEach { propState ->
                    drawPropItem(
                        propState = propState,
                        isSelected = propState.prop.propId == selectedPropId,
                        baseUnit = baseUnit,
                    )
                }
        }

        // 3. Crisp Floating Speech Bubbles & Emotion Badges above active characters
        frame.characters.forEach { charState ->
            if (charState.activeDialogue.isNotBlank() || charState.emotionBadge.isNotBlank()) {
                val anchorX = (charState.x * widthPx).roundToInt()
                val anchorY = ((charState.y - 0.30f * charState.scale) * heightPx).roundToInt()
                val accent = parseHexColor(charState.character.appearance.accentColorHex, CinemaGold)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (anchorX - 96).coerceIn(8, (widthPx - 200).roundToInt().coerceAtLeast(8)),
                                y = (anchorY - 38).coerceIn(8, (heightPx - 64).roundToInt().coerceAtLeast(8)),
                            )
                        }
                        .widthIn(min = 64.dp, max = 188.dp)
                ) {
                    if (charState.activeDialogue.isNotBlank()) {
                        Surface(
                            color = Color(0xF2121722),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier.border(1.2.dp, accent, RoundedCornerShape(12.dp)),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
                                Text(
                                    text = charState.character.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = charState.activeDialogue,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 13.sp,
                                )
                            }
                        }
                    } else if (charState.emotionBadge.isNotBlank()) {
                        Surface(
                            color = accent.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(
                                text = charState.emotionBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioObsidian,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        // 4. Compact Stage HUD Overlay (Active Character Name, Depth Plane, Lock badge)
        val selectedState = frame.characters.firstOrNull { it.character.characterId == selectedCharacterId }
        if (selectedState != null) {
            Surface(
                color = Color(0xC80E121B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .border(
                        1.dp,
                        parseHexColor(selectedState.character.appearance.accentColorHex).copy(alpha = 0.6f),
                        RoundedCornerShape(10.dp),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(selectedState.character.appearance.accentColorHex))
                    )
                    Text(
                        text = "${selectedState.character.name} · ${selectedState.activeAction.label} · ${selectedState.character.depthPlane.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                    if (selectedState.character.groupId.isNotBlank()) {
                        Text(
                            text = "[${selectedState.character.groupId}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = RigCyan,
                        )
                    }
                    if (selectedState.character.locked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Character locked",
                            tint = CinemaGold,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        // Cast count badge
        Surface(
            color = Color(0xB80E121B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Text(
                text = "${frame.characters.size} Cast · ${frame.props.size} Props",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ProceduralBackdropCanvas(
    backdrop: BackdropType,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.66f

        if (backdrop == BackdropType.STUDIO) {
            // Cinema Soundstage Cyc Wall with warm & cyan spotlights
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF141B29), Color(0xFF1E293B), Color(0xFF121824))
                )
            )
            // Soft radial spotlights
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CinemaGold.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.30f, h * 0.25f),
                    radius = w * 0.42f,
                ),
                center = Offset(w * 0.30f, h * 0.25f),
                radius = w * 0.42f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(RigCyan.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * 0.72f, h * 0.28f),
                    radius = w * 0.38f,
                ),
                center = Offset(w * 0.72f, h * 0.28f),
                radius = w * 0.38f,
            )
            // Stage floor
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF232F42), Color(0xFF141B27)),
                    startY = horizonY,
                    endY = h,
                ),
                topLeft = Offset(0f, horizonY),
                size = Size(w, h - horizonY),
            )
            // Subtle perspective floor grid
            val gridColor = Color(0xFF394A66).copy(alpha = 0.45f)
            drawLine(gridColor, Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 1.5f)
            for (i in -4..4) {
                val topX = w * 0.5f + i * (w * 0.11f)
                val botX = w * 0.5f + i * (w * 0.22f)
                drawLine(gridColor, Offset(topX, horizonY), Offset(botX, h), strokeWidth = 1f)
            }
            for (row in 1..3) {
                val y = horizonY + (h - horizonY) * (row / 4f)
                drawLine(gridColor.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
        } else {
            // Night Rooftop Skyline
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0E1C), Color(0xFF192240), Color(0xFF2B2448))
                )
            )
            // Moon glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55FFF4C2), Color.Transparent),
                    center = Offset(w * 0.78f, h * 0.22f),
                    radius = h * 0.26f,
                ),
                center = Offset(w * 0.78f, h * 0.22f),
                radius = h * 0.26f,
            )
            drawCircle(Color(0xFFFFF4C2), radius = h * 0.075f, center = Offset(w * 0.78f, h * 0.22f))
            // Distant buildings silhouette
            val buildingColor = Color(0xFF11182C)
            val buildings = listOf(
                Triple(0.04f, 0.14f, 0.28f),
                Triple(0.17f, 0.12f, 0.40f),
                Triple(0.30f, 0.16f, 0.22f),
                Triple(0.49f, 0.15f, 0.34f),
                Triple(0.67f, 0.12f, 0.25f),
                Triple(0.81f, 0.16f, 0.38f),
            )
            buildings.forEach { (bx, bw, bh) ->
                val rectTop = horizonY - h * bh
                drawRect(
                    color = buildingColor,
                    topLeft = Offset(w * bx, rectTop),
                    size = Size(w * bw, h * bh),
                )
                // Warm lit windows
                for (wy in 0..2) {
                    for (wx in 0..1) {
                        drawRect(
                            color = CinemaGold.copy(alpha = 0.45f),
                            topLeft = Offset(w * bx + 12f + wx * 16f, rectTop + 14f + wy * 16f),
                            size = Size(7f, 9f),
                        )
                    }
                }
            }
            // Rooftop deck floor
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF282C3E), Color(0xFF161926)),
                    startY = horizonY,
                    endY = h,
                ),
                topLeft = Offset(0f, horizonY),
                size = Size(w, h - horizonY),
            )
            drawLine(CinemaGold.copy(alpha = 0.35f), Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 2f)
        }
    }
}

private fun findHitCharacter(
    characters: List<EvaluatedCharacterState>,
    tap: Offset,
    widthPx: Float,
    heightPx: Float,
): EvaluatedCharacterState? {
    // Check front-to-back (reverse Z-order)
    val baseUnit = heightPx * 0.24f
    for (state in characters.asReversed()) {
        val cx = state.x * widthPx
        val footY = state.y * heightPx
        val charHeight = baseUnit * 1.45f * state.scale
        val halfW = baseUnit * 0.52f * state.scale
        if (tap.x in (cx - halfW)..(cx + halfW) && tap.y in (footY - charHeight)..(footY + 24f)) {
            return state
        }
    }
    return null
}

private fun findHitProp(
    props: List<EvaluatedPropState>,
    tap: Offset,
    widthPx: Float,
    heightPx: Float,
): EvaluatedPropState? {
    for (prop in props.asReversed()) {
        val px = prop.x * widthPx
        val py = prop.y * heightPx
        if (hypot(tap.x - px, tap.y - py) <= 44f) {
            return prop
        }
    }
    return null
}

private fun DrawScope.drawInteractionArc(inter: EvaluatedInteractionVisual) {
    val start = Offset(inter.sourceX * size.width, inter.sourceY * size.height)
    val end = Offset(inter.targetX * size.width, inter.targetY * size.height)
    val mid = Offset((start.x + end.x) * 0.5f, minOf(start.y, end.y) - size.height * 0.09f)

    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(mid.x, mid.y, end.x, end.y)
    }
    drawPath(
        path = path,
        color = RigCyan.copy(alpha = 0.78f),
        style = Stroke(
            width = 3f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), inter.progress * 40f),
        ),
    )
    // Glowing pulse dot travelling along the interaction arc
    val t = inter.progress
    val inv = 1f - t
    val px = inv * inv * start.x + 2 * inv * t * mid.x + t * t * end.x
    val py = inv * inv * start.y + 2 * inv * t * mid.y + t * t * end.y
    drawCircle(color = CinemaGold, radius = 6f, center = Offset(px, py))
}

/**
 * Draws an articulated, expressive 2D cartoon rig for an independent SceneCharacter.
 */
fun DrawScope.drawCharacterRig(
    state: EvaluatedCharacterState,
    isSelected: Boolean,
    baseUnit: Float,
) {
    val cx = state.x * size.width
    val footY = (state.y + state.bobOffsetY) * size.height
    val u = baseUnit * state.scale
    val alpha = state.alpha.coerceIn(0f, 1f)
    val dir = if (state.facingRight) 1f else -1f

    val skin = parseHexColor(state.character.appearance.skinColorHex).copy(alpha = alpha)
    val hair = parseHexColor(state.character.appearance.hairColorHex).copy(alpha = alpha)
    val outfit = parseHexColor(state.character.appearance.outfitColorHex).copy(alpha = alpha)
    val pants = parseHexColor(state.character.appearance.pantsColorHex).copy(alpha = alpha)
    val accent = parseHexColor(state.character.appearance.accentColorHex).copy(alpha = alpha)
    val outline = Color(0xFF0E131D).copy(alpha = alpha * 0.9f)

    // Grounded soft contact shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.32f * alpha),
        topLeft = Offset(cx - u * 0.34f, footY - u * 0.05f),
        size = Size(u * 0.68f, u * 0.14f),
    )

    // Selection reticle & group indicator ring
    if (isSelected) {
        drawOval(
            color = CinemaGold.copy(alpha = 0.85f),
            topLeft = Offset(cx - u * 0.44f, footY - u * 0.09f),
            size = Size(u * 0.88f, u * 0.22f),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
        )
        drawRoundRect(
            color = CinemaGold.copy(alpha = 0.55f),
            topLeft = Offset(cx - u * 0.48f, footY - u * 1.48f),
            size = Size(u * 0.96f, u * 1.54f),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
        )
    }

    withTransform({
        rotate(degrees = state.rotationDeg, pivot = Offset(cx, footY - u * 0.65f))
    }) {
        val hipY = footY - u * (0.48f - state.sitFactor * 0.14f)
        val torsoH = u * 0.44f
        val torsoW = u * 0.34f
        val leanRad = state.pose.torsoLean * dir * (PI.toFloat() / 180f)
        val shoulderCenter = Offset(
            x = cx + sin(leanRad) * torsoH,
            y = hipY - cos(leanRad) * torsoH,
        )

        val limbStroke = (u * 0.115f).coerceAtLeast(4f)
        val shoeColor = Color(0xFF1F2937).copy(alpha = alpha)

        // Back leg
        drawArticulatedLimb(
            origin = Offset(cx - dir * u * 0.09f, hipY),
            upperLen = u * 0.25f,
            lowerLen = u * 0.24f,
            upperAngleDeg = state.pose.leftLegAngle * dir,
            lowerAngleDeg = if (state.sitFactor > 0.5f) 78f * dir else 6f * dir,
            strokeWidth = limbStroke,
            color = pants.copy(alpha = alpha * 0.85f),
            endCapColor = shoeColor,
            endCapRadius = u * 0.075f,
        )

        // Back arm
        drawArticulatedLimb(
            origin = Offset(shoulderCenter.x - dir * u * 0.13f, shoulderCenter.y + u * 0.04f),
            upperLen = u * 0.22f,
            lowerLen = u * 0.20f,
            upperAngleDeg = state.pose.leftArmAngle * dir,
            lowerAngleDeg = state.pose.leftElbowAngle * dir,
            strokeWidth = limbStroke * 0.88f,
            color = outfit.copy(alpha = alpha * 0.86f),
            endCapColor = skin,
            endCapRadius = u * 0.062f,
        )

        // Front leg
        drawArticulatedLimb(
            origin = Offset(cx + dir * u * 0.09f, hipY),
            upperLen = u * 0.25f,
            lowerLen = u * 0.24f,
            upperAngleDeg = state.pose.rightLegAngle * dir,
            lowerAngleDeg = if (state.sitFactor > 0.5f) 80f * dir else 4f * dir,
            strokeWidth = limbStroke,
            color = pants,
            endCapColor = shoeColor,
            endCapRadius = u * 0.078f,
        )

        // Torso capsule
        rotate(degrees = state.pose.torsoLean * dir, pivot = Offset(cx, hipY)) {
            drawRoundRect(
                color = outfit,
                topLeft = Offset(cx - torsoW * 0.5f, hipY - torsoH),
                size = Size(torsoW, torsoH + u * 0.05f),
                cornerRadius = CornerRadius(u * 0.14f, u * 0.14f),
            )
            // Collar / center stripe accent
            drawRoundRect(
                color = accent.copy(alpha = alpha * 0.35f),
                topLeft = Offset(cx - torsoW * 0.16f, hipY - torsoH + u * 0.06f),
                size = Size(torsoW * 0.32f, torsoH * 0.72f),
                cornerRadius = CornerRadius(u * 0.08f, u * 0.08f),
            )
            drawRoundRect(
                color = outline,
                topLeft = Offset(cx - torsoW * 0.5f, hipY - torsoH),
                size = Size(torsoW, torsoH + u * 0.05f),
                cornerRadius = CornerRadius(u * 0.14f, u * 0.14f),
                style = Stroke(width = 2f),
            )

            // Scarf accessory around neck
            if (state.character.appearance.accessory == CharacterAccessory.SCARF) {
                drawRoundRect(
                    color = VoiceCoral.copy(alpha = alpha),
                    topLeft = Offset(cx - torsoW * 0.48f, hipY - torsoH - u * 0.04f),
                    size = Size(torsoW * 0.96f, u * 0.11f),
                    cornerRadius = CornerRadius(u * 0.05f, u * 0.05f),
                )
            }
        }

        // Head + Hair + Facial Expressions + Eyes + Lip-Sync Mouth + Accessories
        val headRadius = u * 0.25f
        val headCenter = Offset(shoulderCenter.x, shoulderCenter.y - headRadius * 1.05f)
        rotate(degrees = state.pose.headTilt * dir, pivot = Offset(shoulderCenter.x, shoulderCenter.y)) {
            drawCharacterHead(
                center = headCenter,
                radius = headRadius,
                appearance = state.character.appearance,
                skin = skin,
                hair = hair,
                outline = outline,
                alpha = alpha,
                facingRight = state.facingRight,
                expression = state.expression,
                eyeLookX = state.eyeLookX,
                eyeLookY = state.eyeLookY,
                blinkClosed = state.blinkClosed,
                mouthShape = state.mouthShape,
            )
        }

        // Front arm (drawn over torso so gestures & holding props look crisp!)
        drawArticulatedLimb(
            origin = Offset(shoulderCenter.x + dir * u * 0.13f, shoulderCenter.y + u * 0.04f),
            upperLen = u * 0.22f,
            lowerLen = u * 0.20f,
            upperAngleDeg = state.pose.rightArmAngle * dir,
            lowerAngleDeg = state.pose.rightElbowAngle * dir,
            strokeWidth = limbStroke * 0.9f,
            color = outfit,
            endCapColor = skin,
            endCapRadius = u * 0.066f,
        )
    }
}

private fun DrawScope.drawArticulatedLimb(
    origin: Offset,
    upperLen: Float,
    lowerLen: Float,
    upperAngleDeg: Float,
    lowerAngleDeg: Float,
    strokeWidth: Float,
    color: Color,
    endCapColor: Color,
    endCapRadius: Float,
) {
    val rad1 = (upperAngleDeg + 90f) * (PI.toFloat() / 180f)
    val joint = Offset(
        x = origin.x + cos(rad1) * upperLen,
        y = origin.y + sin(rad1) * upperLen,
    )
    val rad2 = (upperAngleDeg + lowerAngleDeg + 90f) * (PI.toFloat() / 180f)
    val end = Offset(
        x = joint.x + cos(rad2) * lowerLen,
        y = joint.y + sin(rad2) * lowerLen,
    )
    drawLine(color = color, start = origin, end = joint, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = joint, end = end, strokeWidth = strokeWidth * 0.92f, cap = StrokeCap.Round)
    drawCircle(color = endCapColor, radius = endCapRadius, center = end)
}

private fun DrawScope.drawCharacterHead(
    center: Offset,
    radius: Float,
    appearance: CharacterAppearance,
    skin: Color,
    hair: Color,
    outline: Color,
    alpha: Float,
    facingRight: Boolean,
    expression: FacialExpression,
    eyeLookX: Float,
    eyeLookY: Float,
    blinkClosed: Boolean,
    mouthShape: MouthShape,
) {
    val dir = if (facingRight) 1f else -1f

    // Back hair volume for Bob / Curly / Bun / Sleek
    when (appearance.hairStyle) {
        HairStyle.BOB -> {
            drawRoundRect(
                color = hair,
                topLeft = Offset(center.x - radius * 1.15f, center.y - radius * 0.85f),
                size = Size(radius * 2.3f, radius * 1.85f),
                cornerRadius = CornerRadius(radius * 0.9f, radius * 0.9f),
            )
        }

        HairStyle.CURLY -> {
            for (i in 0..6) {
                val angle = (-195f + i * 35f) * (PI.toFloat() / 180f)
                drawCircle(
                    color = hair,
                    radius = radius * 0.45f,
                    center = Offset(
                        center.x + cos(angle) * radius * 0.92f,
                        center.y + sin(angle) * radius * 0.92f,
                    ),
                )
            }
        }

        HairStyle.BUN -> {
            drawCircle(
                color = hair,
                radius = radius * 0.48f,
                center = Offset(center.x - dir * radius * 0.25f, center.y - radius * 1.18f),
            )
        }

        HairStyle.SLEEK -> {
            drawRoundRect(
                color = hair,
                topLeft = Offset(center.x - radius * 1.10f, center.y - radius * 0.95f),
                size = Size(radius * 2.2f, radius * 2.05f),
                cornerRadius = CornerRadius(radius * 0.7f, radius * 0.7f),
            )
        }

        else -> Unit
    }

    // Face circle
    drawCircle(color = skin, radius = radius, center = center)
    drawCircle(color = outline, radius = radius, center = center, style = Stroke(width = 2f))

    // Cheek blush for happy/excited/laughing
    if (expression in listOf(FacialExpression.HAPPY, FacialExpression.EXCITED, FacialExpression.LAUGHING)) {
        val blushColor = VoiceCoral.copy(alpha = 0.28f * alpha)
        drawOval(
            color = blushColor,
            topLeft = Offset(center.x + dir * radius * 0.05f - radius * 0.45f, center.y + radius * 0.14f),
            size = Size(radius * 0.28f, radius * 0.16f),
        )
        drawOval(
            color = blushColor,
            topLeft = Offset(center.x + dir * radius * 0.05f + radius * 0.26f, center.y + radius * 0.14f),
            size = Size(radius * 0.28f, radius * 0.16f),
        )
    }

    // Eyes with independent eyeLookX / eyeLookY direction and automatic blink
    val faceOffsetX = dir * radius * 0.12f
    val leftEyeCenter = Offset(center.x + faceOffsetX - radius * 0.30f, center.y - radius * 0.06f)
    val rightEyeCenter = Offset(center.x + faceOffsetX + radius * 0.30f, center.y - radius * 0.06f)
    val eyeRadius = radius * 0.16f

    listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
        if (blinkClosed) {
            drawLine(
                color = outline,
                start = Offset(eyePos.x - eyeRadius, eyePos.y),
                end = Offset(eyePos.x + eyeRadius, eyePos.y),
                strokeWidth = 2.6f,
                cap = StrokeCap.Round,
            )
        } else {
            drawCircle(color = Color.White.copy(alpha = alpha), radius = eyeRadius, center = eyePos)
            val pupilOffset = Offset(
                x = eyeLookX.coerceIn(-1f, 1f) * eyeRadius * 0.42f,
                y = eyeLookY.coerceIn(-1f, 1f) * eyeRadius * 0.36f,
            )
            val pupilR = if (expression == FacialExpression.SURPRISED) eyeRadius * 0.44f else eyeRadius * 0.56f
            drawCircle(color = Color(0xFF0F172A).copy(alpha = alpha), radius = pupilR, center = eyePos + pupilOffset)
            // Specular highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * alpha),
                radius = pupilR * 0.30f,
                center = eyePos + pupilOffset + Offset(-pupilR * 0.25f, -pupilR * 0.25f),
            )
        }
    }

    // Expressive Eyebrows
    val browY = center.y - radius * 0.30f
    val (leftSlope, rightSlope, browLift) = when (expression) {
        FacialExpression.ANGRY -> Triple(0.11f, -0.11f, 0.05f)
        FacialExpression.SAD -> Triple(-0.10f, 0.10f, -0.02f)
        FacialExpression.SURPRISED, FacialExpression.EXCITED -> Triple(0f, 0f, -0.10f)
        FacialExpression.THINKING -> Triple(-0.08f, 0.04f, -0.06f)
        FacialExpression.FOCUSED -> Triple(0.06f, -0.06f, 0.02f)
        else -> Triple(0f, 0f, 0f)
    }
    drawLine(
        color = hair,
        start = Offset(leftEyeCenter.x - eyeRadius, browY + radius * (browLift - leftSlope)),
        end = Offset(leftEyeCenter.x + eyeRadius, browY + radius * (browLift + leftSlope)),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = hair,
        start = Offset(rightEyeCenter.x - eyeRadius, browY + radius * (browLift - rightSlope)),
        end = Offset(rightEyeCenter.x + eyeRadius, browY + radius * (browLift + rightSlope)),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )

    // Lip-Sync & Expression Mouth
    val mouthCenter = Offset(center.x + faceOffsetX, center.y + radius * 0.36f)
    val mouthDark = Color(0xFF4C0D1D).copy(alpha = alpha)
    when (mouthShape) {
        MouthShape.CLOSED -> {
            val curve = when (expression) {
                FacialExpression.HAPPY, FacialExpression.EXCITED -> radius * 0.14f
                FacialExpression.SAD, FacialExpression.ANGRY -> -radius * 0.12f
                else -> 0f
            }
            val p = Path().apply {
                moveTo(mouthCenter.x - radius * 0.22f, mouthCenter.y)
                quadraticTo(mouthCenter.x, mouthCenter.y + curve, mouthCenter.x + radius * 0.22f, mouthCenter.y)
            }
            drawPath(p, color = outline, style = Stroke(width = 2.8f, cap = StrokeCap.Round))
        }

        MouthShape.SMILE -> {
            val p = Path().apply {
                moveTo(mouthCenter.x - radius * 0.26f, mouthCenter.y - radius * 0.03f)
                quadraticTo(
                    mouthCenter.x,
                    mouthCenter.y + radius * 0.24f,
                    mouthCenter.x + radius * 0.26f,
                    mouthCenter.y - radius * 0.03f,
                )
            }
            drawPath(p, color = outline, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }

        MouthShape.AH -> {
            drawOval(
                color = mouthDark,
                topLeft = Offset(mouthCenter.x - radius * 0.18f, mouthCenter.y - radius * 0.10f),
                size = Size(radius * 0.36f, radius * 0.26f),
            )
            drawOval(
                color = VoiceCoral.copy(alpha = 0.7f * alpha),
                topLeft = Offset(mouthCenter.x - radius * 0.10f, mouthCenter.y + radius * 0.03f),
                size = Size(radius * 0.20f, radius * 0.10f),
            )
        }

        MouthShape.OH -> {
            drawCircle(color = mouthDark, radius = radius * 0.14f, center = mouthCenter)
        }

        MouthShape.EE -> {
            drawRoundRect(
                color = Color.White.copy(alpha = alpha),
                topLeft = Offset(mouthCenter.x - radius * 0.24f, mouthCenter.y - radius * 0.07f),
                size = Size(radius * 0.48f, radius * 0.15f),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawRoundRect(
                color = outline,
                topLeft = Offset(mouthCenter.x - radius * 0.24f, mouthCenter.y - radius * 0.07f),
                size = Size(radius * 0.48f, radius * 0.15f),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 2f),
            )
        }

        MouthShape.WIDE -> {
            drawOval(
                color = mouthDark,
                topLeft = Offset(mouthCenter.x - radius * 0.25f, mouthCenter.y - radius * 0.08f),
                size = Size(radius * 0.50f, radius * 0.26f),
            )
            drawRect(
                color = Color.White.copy(alpha = alpha),
                topLeft = Offset(mouthCenter.x - radius * 0.18f, mouthCenter.y - radius * 0.06f),
                size = Size(radius * 0.36f, radius * 0.08f),
            )
        }
    }

    // Front Hair Bangs / Cap
    when (appearance.hairStyle) {
        HairStyle.SPIKY -> {
            val p = Path().apply {
                moveTo(center.x - radius * 1.05f, center.y - radius * 0.35f)
                lineTo(center.x - radius * 0.75f, center.y - radius * 1.25f)
                lineTo(center.x - radius * 0.25f, center.y - radius * 0.90f)
                lineTo(center.x + radius * 0.15f, center.y - radius * 1.38f)
                lineTo(center.x + radius * 0.55f, center.y - radius * 0.88f)
                lineTo(center.x + radius * 0.95f, center.y - radius * 1.15f)
                lineTo(center.x + radius * 1.02f, center.y - radius * 0.35f)
                close()
            }
            drawPath(p, color = hair)
        }

        HairStyle.CAP -> {
            drawArc(
                color = parseHexColor(appearance.outfitColorHex).copy(alpha = alpha),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - radius * 1.05f, center.y - radius * 1.12f),
                size = Size(radius * 2.10f, radius * 1.25f),
            )
            drawRoundRect(
                color = hair,
                topLeft = Offset(
                    if (facingRight) center.x - radius * 0.2f else center.x - radius * 1.45f,
                    center.y - radius * 0.56f,
                ),
                size = Size(radius * 1.65f, radius * 0.18f),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }

        else -> {
            drawArc(
                color = hair,
                startAngle = 185f,
                sweepAngle = 170f,
                useCenter = true,
                topLeft = Offset(center.x - radius * 1.04f, center.y - radius * 1.08f),
                size = Size(radius * 2.08f, radius * 1.12f),
            )
        }
    }

    // Wearable Accessories
    when (appearance.accessory) {
        CharacterAccessory.GLASSES -> {
            val frameColor = Color(0xFF111827).copy(alpha = alpha)
            drawCircle(frameColor, radius = eyeRadius * 1.45f, center = leftEyeCenter, style = Stroke(width = 2.6f))
            drawCircle(frameColor, radius = eyeRadius * 1.45f, center = rightEyeCenter, style = Stroke(width = 2.6f))
            drawLine(
                frameColor,
                start = Offset(leftEyeCenter.x + eyeRadius * 1.45f, leftEyeCenter.y),
                end = Offset(rightEyeCenter.x - eyeRadius * 1.45f, rightEyeCenter.y),
                strokeWidth = 2.4f,
            )
        }

        CharacterAccessory.HEADPHONES -> {
            drawArc(
                color = RigCyan.copy(alpha = alpha),
                startAngle = 185f,
                sweepAngle = 170f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 1.15f, center.y - radius * 1.15f),
                size = Size(radius * 2.3f, radius * 2.2f),
                style = Stroke(width = 4.5f, cap = StrokeCap.Round),
            )
            drawRoundRect(
                color = Color(0xFF0F172A).copy(alpha = alpha),
                topLeft = Offset(center.x - radius * 1.24f, center.y - radius * 0.25f),
                size = Size(radius * 0.26f, radius * 0.52f),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawRoundRect(
                color = Color(0xFF0F172A).copy(alpha = alpha),
                topLeft = Offset(center.x + radius * 0.98f, center.y - radius * 0.25f),
                size = Size(radius * 0.26f, radius * 0.52f),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }

        CharacterAccessory.CROWN -> {
            val crownPath = Path().apply {
                val topY = center.y - radius * 1.02f
                moveTo(center.x - radius * 0.48f, topY)
                lineTo(center.x - radius * 0.56f, topY - radius * 0.44f)
                lineTo(center.x - radius * 0.20f, topY - radius * 0.20f)
                lineTo(center.x, topY - radius * 0.52f)
                lineTo(center.x + radius * 0.20f, topY - radius * 0.20f)
                lineTo(center.x + radius * 0.56f, topY - radius * 0.44f)
                lineTo(center.x + radius * 0.48f, topY)
                close()
            }
            drawPath(crownPath, color = CinemaGold.copy(alpha = alpha))
            drawPath(crownPath, color = outline, style = Stroke(width = 1.8f))
        }

        else -> Unit
    }
}

private fun DrawScope.drawPropItem(
    propState: EvaluatedPropState,
    isSelected: Boolean,
    baseUnit: Float,
) {
    val px = propState.x * size.width
    val py = propState.y * size.height
    val s = baseUnit * 0.28f * propState.scale

    if (isSelected || propState.inTransit) {
        drawCircle(
            color = if (propState.inTransit) RigCyan.copy(alpha = 0.6f) else CinemaGold.copy(alpha = 0.65f),
            radius = s * 1.15f,
            center = Offset(px, py),
            style = Stroke(width = 2.4f),
        )
    }

    when (propState.prop.type) {
        PropType.COFFEE_CUP -> {
            drawRoundRect(
                color = Color(0xFFF8FAFC),
                topLeft = Offset(px - s * 0.45f, py - s * 0.55f),
                size = Size(s * 0.90f, s * 1.1f),
                cornerRadius = CornerRadius(5f, 5f),
            )
            drawRect(
                color = Color(0xFFD97706),
                topLeft = Offset(px - s * 0.45f, py - s * 0.15f),
                size = Size(s * 0.90f, s * 0.36f),
            )
            drawRoundRect(
                color = Color(0xFF334155),
                topLeft = Offset(px - s * 0.52f, py - s * 0.68f),
                size = Size(s * 1.04f, s * 0.20f),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        PropType.APPLE -> {
            drawCircle(color = Color(0xFFEF4444), radius = s * 0.52f, center = Offset(px, py))
            drawLine(
                color = Color(0xFF78350F),
                start = Offset(px, py - s * 0.45f),
                end = Offset(px + s * 0.08f, py - s * 0.78f),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
            drawOval(
                color = Color(0xFF22C55E),
                topLeft = Offset(px + s * 0.05f, py - s * 0.80f),
                size = Size(s * 0.36f, s * 0.18f),
            )
        }

        PropType.GIFT_BOX -> {
            drawRoundRect(
                color = VoiceCoral,
                topLeft = Offset(px - s * 0.60f, py - s * 0.55f),
                size = Size(s * 1.20f, s * 1.10f),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawRect(color = CinemaGold, topLeft = Offset(px - s * 0.12f, py - s * 0.55f), size = Size(s * 0.24f, s * 1.10f))
            drawRect(color = CinemaGold, topLeft = Offset(px - s * 0.60f, py - s * 0.10f), size = Size(s * 1.20f, s * 0.22f))
        }

        PropType.BRIEFCASE -> {
            drawRoundRect(
                color = Color(0xFF78350F),
                topLeft = Offset(px - s * 0.72f, py - s * 0.45f),
                size = Size(s * 1.44f, s * 0.96f),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawRect(color = CinemaGold, topLeft = Offset(px - s * 0.14f, py - s * 0.05f), size = Size(s * 0.28f, s * 0.18f))
        }

        PropType.MICROPHONE -> {
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(px, py + s * 0.65f),
                end = Offset(px, py - s * 0.20f),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
            drawCircle(color = Color(0xFFCBD5E1), radius = s * 0.36f, center = Offset(px, py - s * 0.45f))
        }

        PropType.PHONE -> {
            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(px - s * 0.32f, py - s * 0.58f),
                size = Size(s * 0.64f, s * 1.16f),
                cornerRadius = CornerRadius(6f, 6f),
            )
            drawRect(
                color = RigCyan.copy(alpha = 0.75f),
                topLeft = Offset(px - s * 0.24f, py - s * 0.45f),
                size = Size(s * 0.48f, s * 0.86f),
            )
        }

        PropType.CHAIR -> {
            val wood = Color(0xFF92400E)
            // Backrest + seat + legs
            drawRoundRect(wood, Offset(px - s * 0.95f, py - s * 1.55f), Size(s * 0.26f, s * 1.55f), CornerRadius(4f, 4f))
            drawRoundRect(wood, Offset(px - s * 0.95f, py - s * 0.35f), Size(s * 1.90f, s * 0.26f), CornerRadius(4f, 4f))
            drawRect(wood, Offset(px - s * 0.85f, py - s * 0.10f), Size(s * 0.20f, s * 0.95f))
            drawRect(wood, Offset(px + s * 0.65f, py - s * 0.10f), Size(s * 0.20f, s * 0.95f))
        }

        PropType.BOOK -> {
            drawRoundRect(
                color = Color(0xFF2563EB),
                topLeft = Offset(px - s * 0.58f, py - s * 0.45f),
                size = Size(s * 1.16f, s * 0.90f),
                cornerRadius = CornerRadius(5f, 5f),
            )
            drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(px - s * 0.45f, py - s * 0.36f), size = Size(s * 0.90f, s * 0.12f))
        }

        PropType.BOUQUET -> {
            drawLine(Color(0xFF16A34A), Offset(px, py + s * 0.65f), Offset(px, py - s * 0.25f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawCircle(VoiceCoral, s * 0.30f, Offset(px - s * 0.24f, py - s * 0.32f))
            drawCircle(CinemaGold, s * 0.30f, Offset(px + s * 0.24f, py - s * 0.32f))
            drawCircle(Color(0xFFC084FC), s * 0.32f, Offset(px, py - s * 0.56f))
        }
    }
}
