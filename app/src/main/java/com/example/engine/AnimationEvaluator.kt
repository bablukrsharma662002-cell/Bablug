package com.example.engine

import com.example.model.ActionClip
import com.example.model.AnimationScene
import com.example.model.BodyPose
import com.example.model.CharacterActionType
import com.example.model.CharacterInteraction
import com.example.model.FacialExpression
import com.example.model.InteractionType
import com.example.model.SceneCharacter
import com.example.model.SceneProp
import com.example.model.TransitionStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class MouthShape {
    CLOSED,
    SMILE,
    AH,
    OH,
    EE,
    WIDE,
}

data class EvaluatedCharacterState(
    val character: SceneCharacter,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotationDeg: Float,
    val facingRight: Boolean,
    val alpha: Float,
    val expression: FacialExpression,
    val eyeLookX: Float,
    val eyeLookY: Float,
    val blinkClosed: Boolean,
    val mouthShape: MouthShape,
    val activeDialogue: String,
    val activeAction: CharacterActionType,
    val pose: BodyPose,
    val sitFactor: Float,       // 0 = standing, 1 = seated
    val bobOffsetY: Float,      // normalized subtle vertical bounce
    val handAnchorX: Float,     // normalized stage X of primary hand
    val handAnchorY: Float,     // normalized stage Y of primary hand
    val emotionBadge: String,
)

data class EvaluatedPropState(
    val prop: SceneProp,
    val x: Float,
    val y: Float,
    val scale: Float,
    val inTransit: Boolean,
    val holderId: String,
)

data class EvaluatedInteractionVisual(
    val interaction: CharacterInteraction,
    val sourceX: Float,
    val sourceY: Float,
    val targetX: Float,
    val targetY: Float,
    val progress: Float,
)

data class EvaluatedFrame(
    val timeMs: Long,
    val characters: List<EvaluatedCharacterState>,
    val props: List<EvaluatedPropState>,
    val activeInteractions: List<EvaluatedInteractionVisual>,
)

object AnimationEvaluator {

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun smoothStep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun lerpPose(a: BodyPose, b: BodyPose, t: Float): BodyPose = BodyPose(
        headTilt = lerp(a.headTilt, b.headTilt, t),
        torsoLean = lerp(a.torsoLean, b.torsoLean, t),
        leftArmAngle = lerp(a.leftArmAngle, b.leftArmAngle, t),
        rightArmAngle = lerp(a.rightArmAngle, b.rightArmAngle, t),
        leftElbowAngle = lerp(a.leftElbowAngle, b.leftElbowAngle, t),
        rightElbowAngle = lerp(a.rightElbowAngle, b.rightElbowAngle, t),
        leftLegAngle = lerp(a.leftLegAngle, b.leftLegAngle, t),
        rightLegAngle = lerp(a.rightLegAngle, b.rightLegAngle, t),
    )

    private fun addPose(base: BodyPose, delta: BodyPose): BodyPose = BodyPose(
        headTilt = (base.headTilt + delta.headTilt).coerceIn(-40f, 40f),
        torsoLean = (base.torsoLean + delta.torsoLean).coerceIn(-30f, 30f),
        leftArmAngle = (base.leftArmAngle + delta.leftArmAngle).coerceIn(-170f, 170f),
        rightArmAngle = (base.rightArmAngle + delta.rightArmAngle).coerceIn(-170f, 170f),
        leftElbowAngle = (base.leftElbowAngle + delta.leftElbowAngle).coerceIn(-120f, 120f),
        rightElbowAngle = (base.rightElbowAngle + delta.rightElbowAngle).coerceIn(-120f, 120f),
        leftLegAngle = (base.leftLegAngle + delta.leftLegAngle).coerceIn(-75f, 75f),
        rightLegAngle = (base.rightLegAngle + delta.rightLegAngle).coerceIn(-75f, 75f),
    )

    fun evaluateFrame(scene: AnimationScene, timeMs: Long): EvaluatedFrame {
        val activeInts = scene.interactions.filter { timeMs in it.startMs..it.endMs }
        val basePositions = HashMap<String, Pair<Float, Float>>(scene.characters.size)

        // Pass 1: compute interpolated base positions so look-at & interactions have accurate targets
        val sortedChars = scene.characters.sortedWith(
            compareBy<SceneCharacter> { it.depthPlane.zBase }.thenBy { it.layerOrder }
        )

        val prelimStates = sortedChars.map { c ->
            val interp = interpolateBase(c, timeMs)
            basePositions[c.characterId] = interp.x to interp.y
            c to interp
        }

        // Pass 2: evaluate full skeletal kinematics, lip-sync, eye gaze, and interaction modifiers
        val evaluatedChars = prelimStates.mapNotNull { (c, base) ->
            if (!c.visible) return@mapNotNull null

            val entranceDuration = 650L
            val exitDuration = 650L
            if (timeMs < c.entranceMs || timeMs > c.exitMs) {
                return@mapNotNull null
            }

            var alpha = 1f
            var xOffset = 0f
            var scaleMult = 1f

            if (c.entranceStyle != TransitionStyle.NONE && timeMs < c.entranceMs + entranceDuration) {
                val p = smoothStep((timeMs - c.entranceMs).toFloat() / entranceDuration)
                when (c.entranceStyle) {
                    TransitionStyle.FADE -> alpha *= p
                    TransitionStyle.SLIDE_LEFT -> xOffset -= (1f - p) * 0.25f
                    TransitionStyle.SLIDE_RIGHT -> xOffset += (1f - p) * 0.25f
                    TransitionStyle.POP -> scaleMult *= (0.25f + 0.75f * p)
                    TransitionStyle.NONE -> Unit
                }
            }
            if (c.exitStyle != TransitionStyle.NONE && timeMs > c.exitMs - exitDuration) {
                val p = smoothStep((c.exitMs - timeMs).toFloat() / exitDuration)
                when (c.exitStyle) {
                    TransitionStyle.FADE -> alpha *= p
                    TransitionStyle.SLIDE_LEFT -> xOffset -= (1f - p) * 0.25f
                    TransitionStyle.SLIDE_RIGHT -> xOffset += (1f - p) * 0.25f
                    TransitionStyle.POP -> scaleMult *= (0.25f + 0.75f * p)
                    TransitionStyle.NONE -> Unit
                }
            }

            val activeClip: ActionClip? = c.actionClips.lastOrNull {
                timeMs in it.startMs..it.endMs
            }

            var activeAction = activeClip?.action ?: CharacterActionType.IDLE
            var expression = activeClip?.expression ?: base.expression
            val dialogue = activeClip?.dialogueText.orEmpty()
            val lipSyncOn = (activeClip?.lipSyncEnabled ?: false) && dialogue.isNotBlank()

            var facingRight = base.facingRight
            var eyeX = base.eyeLookX
            var eyeY = base.eyeLookY
            var emotionBadge = ""
            var interactionPoseDelta = BodyPose()
            var sitFactor = if (activeAction == CharacterActionType.SIT) 1f else 0f

            // Look-at target gaze tracking
            if (c.lookAtCharacterId.isNotBlank()) {
                val targetPos = basePositions[c.lookAtCharacterId]
                if (targetPos != null) {
                    val dx = targetPos.first - base.x
                    val dy = targetPos.second - base.y
                    eyeX = (dx * 3.0f).coerceIn(-1f, 1f)
                    eyeY = (dy * 2.5f).coerceIn(-0.8f, 0.8f)
                }
            }

            // Apply active character-to-character interactions
            val sec = timeMs / 1000f
            for (inter in activeInts) {
                val isSource = inter.sourceCharacterId == c.characterId
                val isTarget = inter.targetCharacterId == c.characterId
                if (!isSource && !isTarget) continue

                val otherId = if (isSource) inter.targetCharacterId else inter.sourceCharacterId
                val otherPos = basePositions[otherId]
                if (otherPos != null) {
                    facingRight = otherPos.first >= base.x
                    eyeX = if (facingRight) 0.55f else -0.55f
                }

                when (inter.type) {
                    InteractionType.CONVERSATION -> {
                        if (isTarget && activeAction == CharacterActionType.IDLE) {
                            activeAction = CharacterActionType.LISTEN
                        }
                        if (isTarget) {
                            // Subtle listening nod
                            interactionPoseDelta = interactionPoseDelta.copy(
                                headTilt = sin(sec * 5.0f) * 8f
                            )
                        }
                    }

                    InteractionType.GREETING -> {
                        activeAction = CharacterActionType.WAVE
                        expression = FacialExpression.HAPPY
                        emotionBadge = "Hi!"
                    }

                    InteractionType.HANDSHAKE -> {
                        activeAction = CharacterActionType.HANDSHAKE
                        expression = FacialExpression.HAPPY
                        val shakeWave = sin(sec * 10f) * 14f
                        interactionPoseDelta = interactionPoseDelta.copy(
                            rightArmAngle = -72f + shakeWave,
                            rightElbowAngle = -18f,
                        )
                        if (isSource) emotionBadge = "Shake"
                    }

                    InteractionType.POINTING -> {
                        if (isSource) {
                            activeAction = CharacterActionType.POINT
                            interactionPoseDelta = interactionPoseDelta.copy(
                                rightArmAngle = -88f,
                                rightElbowAngle = -4f,
                            )
                        }
                    }

                    InteractionType.HAND_GESTURE -> {
                        if (isSource) activeAction = CharacterActionType.HAND_GESTURE
                    }

                    InteractionType.PASS_OBJECT,
                    InteractionType.GIVE_OBJECT,
                    InteractionType.RECEIVE_OBJECT -> {
                        val reach = sin(((timeMs - inter.startMs).toFloat() / inter.durationMs.coerceAtLeast(1L)) * PI.toFloat())
                        interactionPoseDelta = interactionPoseDelta.copy(
                            rightArmAngle = -70f * reach,
                            rightElbowAngle = -15f * reach,
                        )
                        if (isTarget && reach > 0.6f) {
                            expression = FacialExpression.HAPPY
                        }
                    }

                    InteractionType.WALK_TOGETHER -> {
                        activeAction = CharacterActionType.WALK
                        xOffset += sin(sec * 1.4f) * 0.02f
                    }

                    InteractionType.SIT_TOGETHER -> {
                        activeAction = CharacterActionType.SIT
                        sitFactor = 1f
                    }

                    InteractionType.REACTION -> {
                        if (isTarget) {
                            activeAction = CharacterActionType.REACT
                            expression = FacialExpression.SURPRISED
                            emotionBadge = "!"
                        }
                    }

                    InteractionType.EMOTIONAL_INTERACTION -> {
                        expression = FacialExpression.EXCITED
                        emotionBadge = inter.emotionLabel.ifBlank { "Spark" }
                        interactionPoseDelta = interactionPoseDelta.copy(
                            headTilt = sin(sec * 4f) * 10f
                        )
                    }
                }
            }

            // Procedural action kinematics layered with manual pose & interaction delta
            val (actionPose, bobY, actionSit) = proceduralActionPose(
                action = activeAction,
                timeSec = sec,
                charSeed = abs(c.characterId.hashCode() % 17),
            )
            val finalSit = maxOf(sitFactor, actionSit)
            val combinedPose = addPose(addPose(base.pose, actionPose), interactionPoseDelta)

            // Automatic natural blink cycle (independent phase per character)
            val blinkPhase = ((timeMs + abs(c.characterId.hashCode() % 2900)) % 3600L)
            val blinkClosed = blinkPhase in 3420L..3580L

            // Real-time phoneme-driven lip-sync mouth shape
            val mouthShape = computeLipSyncMouth(
                dialogue = dialogue,
                lipSyncActive = lipSyncOn,
                intensity = c.lipSyncIntensity,
                elapsedInClipMs = activeClip?.let { timeMs - it.startMs } ?: 0L,
                isTalkingAction = activeAction == CharacterActionType.TALK,
                isEatingAction = activeAction == CharacterActionType.EAT,
                expression = expression,
                timeSec = sec,
            )

            val finalX = (base.x + xOffset).coerceIn(0.05f, 0.95f)
            val finalY = base.y.coerceIn(0.32f, 0.94f)
            val finalScale = (base.scale * c.depthPlane.depthScale * scaleMult).coerceIn(0.35f, 2.4f)

            val dir = if (facingRight) 1f else -1f
            val handAnchorX = finalX + dir * (0.048f * finalScale)
            val handAnchorY = finalY - (0.075f * finalScale) + bobY

            EvaluatedCharacterState(
                character = c,
                x = finalX,
                y = finalY,
                scale = finalScale,
                rotationDeg = base.rotationDeg,
                facingRight = facingRight,
                alpha = alpha,
                expression = expression,
                eyeLookX = eyeX,
                eyeLookY = eyeY,
                blinkClosed = blinkClosed,
                mouthShape = mouthShape,
                activeDialogue = dialogue,
                activeAction = activeAction,
                pose = combinedPose,
                sitFactor = finalSit,
                bobOffsetY = bobY,
                handAnchorX = handAnchorX,
                handAnchorY = handAnchorY,
                emotionBadge = emotionBadge,
            )
        }

        val charMap = evaluatedChars.associateBy { it.character.characterId }

        // Pass 3: evaluate props including smooth handover arc transfers
        val evaluatedProps = scene.props.mapNotNull { prop ->
            if (!prop.visible) return@mapNotNull null

            val handoverInteractions = scene.interactions
                .filter {
                    it.propId == prop.propId &&
                        it.type in listOf(
                            InteractionType.PASS_OBJECT,
                            InteractionType.GIVE_OBJECT,
                            InteractionType.RECEIVE_OBJECT,
                        )
                }
                .sortedBy { it.startMs }

            var currentHolderId = prop.holderCharacterId
            var inTransit = false
            var px = prop.x
            var py = prop.y

            for (inter in handoverInteractions) {
                if (timeMs > inter.endMs) {
                    currentHolderId = inter.targetCharacterId
                } else if (timeMs in inter.startMs..inter.endMs) {
                    val src = charMap[inter.sourceCharacterId]
                    val dst = charMap[inter.targetCharacterId]
                    if (src != null && dst != null) {
                        val rawP = (timeMs - inter.startMs).toFloat() / inter.durationMs.coerceAtLeast(1L)
                        val p = smoothStep(rawP)
                        px = lerp(src.handAnchorX, dst.handAnchorX, p)
                        py = lerp(src.handAnchorY, dst.handAnchorY, p) - sin(p * PI.toFloat()) * 0.08f
                        inTransit = true
                        currentHolderId = if (p < 0.5f) inter.sourceCharacterId else inter.targetCharacterId
                    }
                    break
                }
            }

            if (!inTransit && currentHolderId.isNotBlank()) {
                val holder = charMap[currentHolderId]
                if (holder != null) {
                    px = holder.handAnchorX
                    py = holder.handAnchorY
                }
            }

            EvaluatedPropState(
                prop = prop,
                x = px,
                y = py,
                scale = prop.scale,
                inTransit = inTransit,
                holderId = currentHolderId,
            )
        }

        // Pass 4: evaluate active interaction overlays
        val interactionVisuals = activeInts.mapNotNull { inter ->
            val src = charMap[inter.sourceCharacterId] ?: return@mapNotNull null
            val dst = charMap[inter.targetCharacterId] ?: return@mapNotNull null
            val p = ((timeMs - inter.startMs).toFloat() / inter.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            EvaluatedInteractionVisual(
                interaction = inter,
                sourceX = src.x,
                sourceY = src.y - 0.14f * src.scale,
                targetX = dst.x,
                targetY = dst.y - 0.14f * dst.scale,
                progress = p,
            )
        }

        return EvaluatedFrame(
            timeMs = timeMs,
            characters = evaluatedChars,
            props = evaluatedProps,
            activeInteractions = interactionVisuals,
        )
    }

    private data class BaseInterpolated(
        val x: Float,
        val y: Float,
        val scale: Float,
        val rotationDeg: Float,
        val facingRight: Boolean,
        val expression: FacialExpression,
        val eyeLookX: Float,
        val eyeLookY: Float,
        val pose: BodyPose,
    )

    private fun interpolateBase(c: SceneCharacter, timeMs: Long): BaseInterpolated {
        val sortedKf = c.keyframes.sortedBy { it.timeMs }
        if (sortedKf.isEmpty()) {
            return BaseInterpolated(
                x = c.x,
                y = c.y,
                scale = c.scale,
                rotationDeg = c.rotationDeg,
                facingRight = c.facingRight,
                expression = c.expression,
                eyeLookX = c.eyeLookX,
                eyeLookY = c.eyeLookY,
                pose = c.bodyPose,
            )
        }
        val first = sortedKf.first()
        if (timeMs <= first.timeMs) {
            return BaseInterpolated(
                x = first.x,
                y = first.y,
                scale = first.scale,
                rotationDeg = first.rotationDeg,
                facingRight = first.facingRight,
                expression = first.expression,
                eyeLookX = first.eyeLookX,
                eyeLookY = first.eyeLookY,
                pose = addPose(c.bodyPose, first.pose),
            )
        }
        val last = sortedKf.last()
        if (timeMs >= last.timeMs) {
            return BaseInterpolated(
                x = last.x,
                y = last.y,
                scale = last.scale,
                rotationDeg = last.rotationDeg,
                facingRight = last.facingRight,
                expression = last.expression,
                eyeLookX = last.eyeLookX,
                eyeLookY = last.eyeLookY,
                pose = addPose(c.bodyPose, last.pose),
            )
        }
        var prev = first
        var next = last
        for (i in 0 until sortedKf.lastIndex) {
            if (timeMs in sortedKf[i].timeMs..sortedKf[i + 1].timeMs) {
                prev = sortedKf[i]
                next = sortedKf[i + 1]
                break
            }
        }
        val span = (next.timeMs - prev.timeMs).coerceAtLeast(1L).toFloat()
        val t = smoothStep((timeMs - prev.timeMs).toFloat() / span)
        return BaseInterpolated(
            x = lerp(prev.x, next.x, t),
            y = lerp(prev.y, next.y, t),
            scale = lerp(prev.scale, next.scale, t),
            rotationDeg = lerp(prev.rotationDeg, next.rotationDeg, t),
            facingRight = if (t < 0.5f) prev.facingRight else next.facingRight,
            expression = if (t < 0.5f) prev.expression else next.expression,
            eyeLookX = lerp(prev.eyeLookX, next.eyeLookX, t),
            eyeLookY = lerp(prev.eyeLookY, next.eyeLookY, t),
            pose = addPose(c.bodyPose, lerpPose(prev.pose, next.pose, t)),
        )
    }

    private fun proceduralActionPose(
        action: CharacterActionType,
        timeSec: Float,
        charSeed: Int,
    ): Triple<BodyPose, Float, Float> {
        val phase = timeSec + charSeed * 0.37f
        return when (action) {
            CharacterActionType.IDLE -> {
                val breath = sin(phase * 2.2f)
                Triple(
                    BodyPose(
                        headTilt = breath * 2.5f,
                        leftArmAngle = breath * 4f,
                        rightArmAngle = -breath * 4f,
                    ),
                    breath * 0.004f,
                    0f,
                )
            }

            CharacterActionType.WALK -> {
                val stride = sin(phase * 6.5f)
                val bounce = abs(cos(phase * 6.5f)) * -0.014f
                Triple(
                    BodyPose(
                        torsoLean = 6f,
                        leftArmAngle = stride * 34f,
                        rightArmAngle = -stride * 34f,
                        leftElbowAngle = 14f,
                        rightElbowAngle = -14f,
                        leftLegAngle = -stride * 30f,
                        rightLegAngle = stride * 30f,
                    ),
                    bounce,
                    0f,
                )
            }

            CharacterActionType.RUN -> {
                val stride = sin(phase * 11f)
                val bounce = abs(cos(phase * 11f)) * -0.024f
                Triple(
                    BodyPose(
                        torsoLean = 14f,
                        leftArmAngle = stride * 58f,
                        rightArmAngle = -stride * 58f,
                        leftElbowAngle = 45f,
                        rightElbowAngle = -45f,
                        leftLegAngle = -stride * 48f,
                        rightLegAngle = stride * 48f,
                    ),
                    bounce,
                    0f,
                )
            }

            CharacterActionType.STOP, CharacterActionType.STAND -> {
                Triple(
                    BodyPose(
                        headTilt = sin(phase * 1.8f) * 2f,
                        torsoLean = 0f,
                    ),
                    0f,
                    0f,
                )
            }

            CharacterActionType.TALK -> {
                val g1 = sin(phase * 5.2f)
                val g2 = cos(phase * 3.8f)
                Triple(
                    BodyPose(
                        headTilt = g2 * 7f,
                        leftArmAngle = 22f + g1 * 18f,
                        rightArmAngle = -52f + g2 * 22f,
                        leftElbowAngle = 28f,
                        rightElbowAngle = -42f + g1 * 18f,
                    ),
                    sin(phase * 4f) * 0.004f,
                    0f,
                )
            }

            CharacterActionType.LISTEN -> {
                val nod = sin(phase * 3.6f)
                Triple(
                    BodyPose(
                        headTilt = 5f + nod * 7f,
                        torsoLean = 4f,
                        leftArmAngle = 12f,
                        rightArmAngle = -12f,
                    ),
                    0f,
                    0f,
                )
            }

            CharacterActionType.SIT -> {
                val sway = sin(phase * 2.0f)
                Triple(
                    BodyPose(
                        headTilt = sway * 3f,
                        torsoLean = -3f,
                        leftArmAngle = 20f,
                        rightArmAngle = -24f,
                        leftElbowAngle = 36f,
                        rightElbowAngle = -36f,
                        leftLegAngle = -46f,
                        rightLegAngle = -42f,
                    ),
                    0.042f,
                    1f,
                )
            }

            CharacterActionType.EAT -> {
                val bite = (sin(phase * 4.5f) + 1f) * 0.5f
                Triple(
                    BodyPose(
                        headTilt = 6f * bite,
                        rightArmAngle = -35f - bite * 68f,
                        rightElbowAngle = -30f - bite * 55f,
                        leftArmAngle = 16f,
                    ),
                    0f,
                    0f,
                )
            }

            CharacterActionType.FIGHT -> {
                val punch = sin(phase * 9.5f)
                Triple(
                    BodyPose(
                        torsoLean = 12f,
                        leftArmAngle = -52f + punch * 38f,
                        rightArmAngle = -75f - punch * 48f,
                        leftElbowAngle = -55f,
                        rightElbowAngle = -35f,
                        leftLegAngle = -22f,
                        rightLegAngle = 22f,
                    ),
                    abs(sin(phase * 9.5f)) * -0.015f,
                    0f,
                )
            }

            CharacterActionType.DANCE -> {
                val beat = sin(phase * 7.5f)
                val groove = cos(phase * 3.75f)
                Triple(
                    BodyPose(
                        headTilt = beat * 12f,
                        torsoLean = groove * 10f,
                        leftArmAngle = -85f + beat * 45f,
                        rightArmAngle = 85f + beat * 45f,
                        leftElbowAngle = -35f * beat,
                        rightElbowAngle = 35f * beat,
                        leftLegAngle = beat * 22f,
                        rightLegAngle = -beat * 22f,
                    ),
                    abs(beat) * -0.022f,
                    0f,
                )
            }

            CharacterActionType.HAND_GESTURE -> {
                val sw = sin(phase * 5.8f)
                Triple(
                    BodyPose(
                        headTilt = sw * 6f,
                        leftArmAngle = -40f + sw * 28f,
                        rightArmAngle = -68f - sw * 26f,
                        leftElbowAngle = -45f,
                        rightElbowAngle = -40f,
                    ),
                    0f,
                    0f,
                )
            }

            CharacterActionType.WAVE -> {
                val wave = sin(phase * 9.0f)
                Triple(
                    BodyPose(
                        headTilt = 6f,
                        rightArmAngle = -132f + wave * 22f,
                        rightElbowAngle = -26f + wave * 18f,
                    ),
                    -0.005f,
                    0f,
                )
            }

            CharacterActionType.REACT -> {
                val jolt = sin(phase * 6.0f)
                Triple(
                    BodyPose(
                        headTilt = -8f + jolt * 6f,
                        torsoLean = -8f,
                        leftArmAngle = -95f,
                        rightArmAngle = -95f,
                        leftElbowAngle = -40f,
                        rightElbowAngle = -40f,
                    ),
                    -0.016f * abs(jolt),
                    0f,
                )
            }

            CharacterActionType.POINT -> {
                Triple(
                    BodyPose(
                        torsoLean = 5f,
                        rightArmAngle = -88f,
                        rightElbowAngle = -4f,
                    ),
                    0f,
                    0f,
                )
            }

            CharacterActionType.HANDSHAKE -> {
                val shake = sin(phase * 8.5f)
                Triple(
                    BodyPose(
                        torsoLean = 5f,
                        rightArmAngle = -68f + shake * 12f,
                        rightElbowAngle = -16f,
                    ),
                    0f,
                    0f,
                )
            }

            CharacterActionType.LOOK_AROUND -> {
                val pan = sin(phase * 2.4f)
                Triple(
                    BodyPose(
                        headTilt = pan * 14f,
                        torsoLean = pan * 5f,
                        leftArmAngle = 14f,
                        rightArmAngle = -28f,
                        rightElbowAngle = -45f,
                    ),
                    0f,
                    0f,
                )
            }

            CharacterActionType.PICK_UP -> {
                val bend = (sin(phase * 3.5f) + 1f) * 0.5f
                Triple(
                    BodyPose(
                        torsoLean = 18f * bend,
                        rightArmAngle = -22f + 42f * bend,
                        leftArmAngle = 15f,
                    ),
                    0.02f * bend,
                    0f,
                )
            }

            CharacterActionType.HOLD_PROP -> {
                Triple(
                    BodyPose(
                        rightArmAngle = -54f,
                        rightElbowAngle = -32f,
                    ),
                    0f,
                    0f,
                )
            }

            CharacterActionType.HAND_OVER -> {
                val extend = (sin(phase * 3.0f) + 1f) * 0.5f
                Triple(
                    BodyPose(
                        torsoLean = 7f * extend,
                        rightArmAngle = -78f * extend - 18f,
                        rightElbowAngle = -12f,
                    ),
                    0f,
                    0f,
                )
            }
        }
    }

    private fun computeLipSyncMouth(
        dialogue: String,
        lipSyncActive: Boolean,
        intensity: Float,
        elapsedInClipMs: Long,
        isTalkingAction: Boolean,
        isEatingAction: Boolean,
        expression: FacialExpression,
        timeSec: Float,
    ): MouthShape {
        if (isEatingAction) {
            return if ((timeSec * 5f).toInt() % 2 == 0) MouthShape.OH else MouthShape.CLOSED
        }
        if (lipSyncActive && intensity > 0.05f && dialogue.isNotBlank()) {
            // Map character stream at ~10 phonemes/sec to real mouth shapes
            val charIndex = ((elapsedInClipMs / 95L).toInt()) % dialogue.length
            val ch = dialogue[charIndex].lowercaseChar()
            return when (ch) {
                'a', 'h' -> MouthShape.AH
                'o', 'u', 'w' -> MouthShape.OH
                'e', 'i', 'y' -> MouthShape.EE
                ' ', '.', ',', '!', '?', 'm', 'b', 'p' -> MouthShape.CLOSED
                else -> if (intensity > 0.7f) MouthShape.WIDE else MouthShape.AH
            }
        }
        if (isTalkingAction) {
            val cycle = (timeSec * 8f).toInt() % 4
            return when (cycle) {
                0 -> MouthShape.AH
                1 -> MouthShape.EE
                2 -> MouthShape.OH
                else -> MouthShape.CLOSED
            }
        }
        return when (expression) {
            FacialExpression.HAPPY, FacialExpression.EXCITED -> MouthShape.SMILE
            FacialExpression.LAUGHING -> MouthShape.WIDE
            FacialExpression.SURPRISED -> MouthShape.OH
            else -> MouthShape.CLOSED
        }
    }
}
