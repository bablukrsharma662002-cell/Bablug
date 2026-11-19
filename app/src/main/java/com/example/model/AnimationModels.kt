package com.example.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.UUID

fun newUid(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(10)}"

fun parseHexColor(hex: String, fallback: Color = Color(0xFFFFB020)): Color =
    try {
        val cleaned = hex.trim().removePrefix("#")
        val parsed = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color(0xFF000000L or parsed)
            8 -> Color(parsed)
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }

enum class HairStyle(val label: String) {
    SPIKY("Spiky"),
    BOB("Bob"),
    CURLY("Curly"),
    BUN("Top Bun"),
    CAP("Studio Cap"),
    SLEEK("Sleek");

    companion object {
        fun fromRaw(raw: String?): HairStyle =
            entries.firstOrNull { it.name == raw } ?: BOB
    }
}

enum class CharacterAccessory(val label: String) {
    NONE("None"),
    GLASSES("Glasses"),
    SCARF("Scarf"),
    CROWN("Crown"),
    HEADPHONES("Headphones");

    companion object {
        fun fromRaw(raw: String?): CharacterAccessory =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class CharacterActionType(val label: String, val colorHex: String) {
    IDLE("Idle", "#475569"),
    WALK("Walk", "#0EA5E9"),
    RUN("Run", "#0284C7"),
    STOP("Stop", "#64748B"),
    TALK("Talk", "#FB7185"),
    LISTEN("Listen", "#22D3EE"),
    SIT("Sit", "#6366F1"),
    STAND("Stand", "#8B5CF6"),
    EAT("Eat", "#F59E0B"),
    FIGHT("Fight", "#EF4444"),
    DANCE("Dance", "#EC4899"),
    HAND_GESTURE("Hand Gesture", "#F97316"),
    WAVE("Wave", "#10B981"),
    REACT("React", "#EAB308"),
    POINT("Point", "#14B8A6"),
    HANDSHAKE("Handshake", "#34D399"),
    LOOK_AROUND("Look Around", "#60A5FA"),
    PICK_UP("Pick Up", "#A855F7"),
    HOLD_PROP("Hold Prop", "#84CC16"),
    HAND_OVER("Hand Over", "#F43F5E");

    companion object {
        fun fromRaw(raw: String?): CharacterActionType =
            entries.firstOrNull { it.name == raw } ?: IDLE
    }
}

enum class FacialExpression(val label: String) {
    NEUTRAL("Neutral"),
    HAPPY("Happy"),
    EXCITED("Excited"),
    SURPRISED("Surprised"),
    ANGRY("Angry"),
    SAD("Sad"),
    THINKING("Thinking"),
    LAUGHING("Laughing"),
    FOCUSED("Focused");

    companion object {
        fun fromRaw(raw: String?): FacialExpression =
            entries.firstOrNull { it.name == raw } ?: NEUTRAL
    }
}

enum class DepthPlane(val label: String, val zBase: Int, val depthScale: Float) {
    BACKGROUND("Background", 0, 0.84f),
    MIDGROUND("Midground", 100, 1.0f),
    FOREGROUND("Foreground", 200, 1.16f);

    companion object {
        fun fromRaw(raw: String?): DepthPlane =
            entries.firstOrNull { it.name == raw } ?: MIDGROUND
    }
}

enum class TransitionStyle(val label: String) {
    NONE("Cut"),
    FADE("Fade"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    POP("Pop");

    companion object {
        fun fromRaw(raw: String?): TransitionStyle =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class PropType(val label: String) {
    COFFEE_CUP("Coffee Cup"),
    APPLE("Apple"),
    GIFT_BOX("Gift Box"),
    BRIEFCASE("Briefcase"),
    MICROPHONE("Microphone"),
    PHONE("Phone"),
    CHAIR("Chair"),
    BOOK("Book"),
    BOUQUET("Bouquet");

    companion object {
        fun fromRaw(raw: String?): PropType =
            entries.firstOrNull { it.name == raw } ?: COFFEE_CUP
    }
}

enum class InteractionType(val label: String, val usesProp: Boolean = false) {
    CONVERSATION("Conversation"),
    HAND_GESTURE("Hand Gestures"),
    HANDSHAKE("Handshake"),
    GREETING("Greeting"),
    POINTING("Pointing"),
    PASS_OBJECT("Pass Object", usesProp = true),
    GIVE_OBJECT("Give Object", usesProp = true),
    RECEIVE_OBJECT("Receive Object", usesProp = true),
    WALK_TOGETHER("Walk Together"),
    SIT_TOGETHER("Sit Together"),
    REACTION("Reaction"),
    EMOTIONAL_INTERACTION("Emotional Interaction");

    companion object {
        fun fromRaw(raw: String?): InteractionType =
            entries.firstOrNull { it.name == raw } ?: CONVERSATION
    }
}

enum class BackdropType(val label: String) {
    PARK("Park Plaza"),
    CAFE("Cozy Cafe"),
    STUDIO("Cinema Stage"),
    NIGHT_SKY("Night Rooftop");

    companion object {
        fun fromRaw(raw: String?): BackdropType =
            entries.firstOrNull { it.name == raw } ?: PARK
    }
}

data class CharacterAppearance(
    val skinColorHex: String = "#F4C29F",
    val hairColorHex: String = "#2B1E3E",
    val outfitColorHex: String = "#FFB020",
    val pantsColorHex: String = "#24344D",
    val accentColorHex: String = "#FFB020",
    val hairStyle: HairStyle = HairStyle.BOB,
    val accessory: CharacterAccessory = CharacterAccessory.NONE,
)

data class BodyPose(
    val headTilt: Float = 0f,         // degrees -35..35
    val torsoLean: Float = 0f,        // degrees -25..25
    val leftArmAngle: Float = 18f,    // degrees -160..160
    val rightArmAngle: Float = -18f,  // degrees -160..160
    val leftElbowAngle: Float = 10f,  // degrees -110..110
    val rightElbowAngle: Float = -10f,// degrees -110..110
    val leftLegAngle: Float = 4f,     // degrees -60..60
    val rightLegAngle: Float = -4f,   // degrees -60..60
)

data class ActionClip(
    val clipId: String = newUid("clip"),
    val action: CharacterActionType = CharacterActionType.IDLE,
    val startMs: Long = 0L,
    val durationMs: Long = 2500L,
    val expression: FacialExpression = FacialExpression.NEUTRAL,
    val dialogueText: String = "",
    val lipSyncEnabled: Boolean = true,
) {
    val endMs: Long get() = startMs + durationMs
}

data class CharacterKeyframe(
    val keyframeId: String = newUid("kf"),
    val timeMs: Long = 0L,
    val x: Float = 0.5f,
    val y: Float = 0.72f,
    val scale: Float = 1.0f,
    val rotationDeg: Float = 0f,
    val facingRight: Boolean = true,
    val expression: FacialExpression = FacialExpression.NEUTRAL,
    val eyeLookX: Float = 0f,
    val eyeLookY: Float = 0f,
    val pose: BodyPose = BodyPose(),
)

data class SceneCharacter(
    val characterId: String = newUid("char"),
    val name: String = "Character",
    val appearance: CharacterAppearance = CharacterAppearance(),
    val x: Float = 0.5f,               // normalized 0.08..0.92
    val y: Float = 0.72f,              // normalized 0.35..0.92
    val scale: Float = 1.0f,           // 0.45..2.0
    val rotationDeg: Float = 0f,       // -180..180
    val facingRight: Boolean = true,
    val depthPlane: DepthPlane = DepthPlane.MIDGROUND,
    val layerOrder: Int = 0,
    val locked: Boolean = false,
    val visible: Boolean = true,
    val groupId: String = "",
    val expression: FacialExpression = FacialExpression.HAPPY,
    val eyeLookX: Float = 0f,          // -1..1
    val eyeLookY: Float = 0f,          // -1..1
    val lookAtCharacterId: String = "",
    val bodyPose: BodyPose = BodyPose(),
    val voicePitch: Float = 1.0f,      // 0.5..2.0
    val voiceRate: Float = 1.0f,       // 0.5..2.0
    val lipSyncIntensity: Float = 1.0f,// 0.0..1.5
    val entranceMs: Long = 0L,
    val exitMs: Long = 12000L,
    val entranceStyle: TransitionStyle = TransitionStyle.NONE,
    val exitStyle: TransitionStyle = TransitionStyle.NONE,
    val actionClips: List<ActionClip> = emptyList(),
    val keyframes: List<CharacterKeyframe> = emptyList(),
)

data class SceneProp(
    val propId: String = newUid("prop"),
    val name: String = "Coffee Cup",
    val type: PropType = PropType.COFFEE_CUP,
    val x: Float = 0.5f,
    val y: Float = 0.78f,
    val scale: Float = 1.0f,
    val holderCharacterId: String = "",
    val visible: Boolean = true,
)

data class CharacterInteraction(
    val interactionId: String = newUid("int"),
    val type: InteractionType = InteractionType.CONVERSATION,
    val sourceCharacterId: String = "",
    val targetCharacterId: String = "",
    val propId: String = "",
    val startMs: Long = 2000L,
    val durationMs: Long = 3000L,
    val emotionLabel: String = "Friendly",
) {
    val endMs: Long get() = startMs + durationMs
}

@Keep
@IgnoreExtraProperties
data class CharacterTemplate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "Character",
    val skinColorHex: String = "#F4C29F",
    val hairColorHex: String = "#2B1E3E",
    val outfitColorHex: String = "#FFB020",
    val pantsColorHex: String = "#24344D",
    val accentColorHex: String = "#FFB020",
    val hairStyle: String = HairStyle.BOB.name,
    val accessory: String = CharacterAccessory.NONE.name,
    val voicePitch: Double = 1.0,
    val voiceRate: Double = 1.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun toAppearance(): CharacterAppearance = CharacterAppearance(
        skinColorHex = skinColorHex,
        hairColorHex = hairColorHex,
        outfitColorHex = outfitColorHex,
        pantsColorHex = pantsColorHex,
        accentColorHex = accentColorHex,
        hairStyle = HairStyle.fromRaw(hairStyle),
        accessory = CharacterAccessory.fromRaw(accessory),
    )
}

data class AnimationScene(
    val id: String = "",
    val userId: String = "",
    val title: String = "Park Meetup",
    val backdrop: BackdropType = BackdropType.PARK,
    val durationMs: Long = 12000L,
    val characters: List<SceneCharacter> = emptyList(),
    val props: List<SceneProp> = emptyList(),
    val interactions: List<CharacterInteraction> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

object StarterContent {
    val builtInLibrary: List<CharacterTemplate> = listOf(
        CharacterTemplate(
            id = "preset_mia",
            name = "Mia",
            skinColorHex = "#F4C29F",
            hairColorHex = "#2B1E3E",
            outfitColorHex = "#FFB020",
            pantsColorHex = "#24344D",
            accentColorHex = "#FFB020",
            hairStyle = HairStyle.BOB.name,
            accessory = CharacterAccessory.GLASSES.name,
            voicePitch = 1.18,
            voiceRate = 1.02,
        ),
        CharacterTemplate(
            id = "preset_leo",
            name = "Leo",
            skinColorHex = "#E0A378",
            hairColorHex = "#3B2418",
            outfitColorHex = "#0EA5E9",
            pantsColorHex = "#1E293B",
            accentColorHex = "#22D3EE",
            hairStyle = HairStyle.SPIKY.name,
            accessory = CharacterAccessory.HEADPHONES.name,
            voicePitch = 0.88,
            voiceRate = 0.96,
        ),
        CharacterTemplate(
            id = "preset_kai",
            name = "Kai",
            skinColorHex = "#C48257",
            hairColorHex = "#1E1B24",
            outfitColorHex = "#10B981",
            pantsColorHex = "#1E293B",
            accentColorHex = "#34D399",
            hairStyle = HairStyle.CAP.name,
            accessory = CharacterAccessory.SCARF.name,
            voicePitch = 1.04,
            voiceRate = 1.05,
        ),
        CharacterTemplate(
            id = "preset_zara",
            name = "Zara",
            skinColorHex = "#8D5524",
            hairColorHex = "#1E1B24",
            outfitColorHex = "#EC4899",
            pantsColorHex = "#312E81",
            accentColorHex = "#FB7185",
            hairStyle = HairStyle.CURLY.name,
            accessory = CharacterAccessory.CROWN.name,
            voicePitch = 1.24,
            voiceRate = 1.0,
        ),
        CharacterTemplate(
            id = "preset_nino",
            name = "Chef Nino",
            skinColorHex = "#FCE0C8",
            hairColorHex = "#8C4A27",
            outfitColorHex = "#F97316",
            pantsColorHex = "#1E293B",
            accentColorHex = "#FB923C",
            hairStyle = HairStyle.BUN.name,
            accessory = CharacterAccessory.SCARF.name,
            voicePitch = 0.92,
            voiceRate = 1.08,
        ),
        CharacterTemplate(
            id = "preset_aria",
            name = "Aria",
            skinColorHex = "#FCE0C8",
            hairColorHex = "#9333EA",
            outfitColorHex = "#8B5CF6",
            pantsColorHex = "#1E1B24",
            accentColorHex = "#A78BFA",
            hairStyle = HairStyle.SLEEK.name,
            accessory = CharacterAccessory.HEADPHONES.name,
            voicePitch = 1.15,
            voiceRate = 0.98,
        ),
    )

    fun createShowcaseScene(userId: String = ""): AnimationScene {
        val miaId = "char_mia_1"
        val leoId = "char_leo_2"
        val kaiId = "char_kai_3"
        val cupId = "prop_coffee_1"
        val appleId = "prop_apple_2"
        val chairId = "prop_chair_3"

        val mia = SceneCharacter(
            characterId = miaId,
            name = "Mia",
            appearance = builtInLibrary[0].toAppearance(),
            x = 0.18f,
            y = 0.74f,
            scale = 1.0f,
            facingRight = true,
            depthPlane = DepthPlane.FOREGROUND,
            layerOrder = 2,
            expression = FacialExpression.HAPPY,
            lookAtCharacterId = leoId,
            voicePitch = 1.18f,
            voiceRate = 1.02f,
            entranceMs = 0L,
            exitMs = 12000L,
            entranceStyle = TransitionStyle.SLIDE_LEFT,
            exitStyle = TransitionStyle.NONE,
            actionClips = listOf(
                ActionClip(
                    clipId = "mia_walk",
                    action = CharacterActionType.WALK,
                    startMs = 0L,
                    durationMs = 2600L,
                    expression = FacialExpression.HAPPY,
                ),
                ActionClip(
                    clipId = "mia_stop",
                    action = CharacterActionType.STOP,
                    startMs = 2600L,
                    durationMs = 900L,
                    expression = FacialExpression.EXCITED,
                ),
                ActionClip(
                    clipId = "mia_talk",
                    action = CharacterActionType.TALK,
                    startMs = 3500L,
                    durationMs = 4500L,
                    expression = FacialExpression.HAPPY,
                    dialogueText = "Hey Leo! I brought your coffee for our scene rehearsal!",
                    lipSyncEnabled = true,
                ),
                ActionClip(
                    clipId = "mia_gesture",
                    action = CharacterActionType.HAND_GESTURE,
                    startMs = 8000L,
                    durationMs = 4000L,
                    expression = FacialExpression.EXCITED,
                    dialogueText = "Let's block the finale together!",
                    lipSyncEnabled = true,
                ),
            ),
            keyframes = listOf(
                CharacterKeyframe(
                    keyframeId = "mia_kf_0",
                    timeMs = 0L,
                    x = 0.14f,
                    y = 0.75f,
                    scale = 1.0f,
                    facingRight = true,
                    expression = FacialExpression.HAPPY,
                ),
                CharacterKeyframe(
                    keyframeId = "mia_kf_1",
                    timeMs = 2600L,
                    x = 0.34f,
                    y = 0.75f,
                    scale = 1.02f,
                    facingRight = true,
                    expression = FacialExpression.EXCITED,
                ),
                CharacterKeyframe(
                    keyframeId = "mia_kf_2",
                    timeMs = 8000L,
                    x = 0.36f,
                    y = 0.75f,
                    scale = 1.02f,
                    facingRight = true,
                    expression = FacialExpression.EXCITED,
                ),
            ),
        )

        val leo = SceneCharacter(
            characterId = leoId,
            name = "Leo",
            appearance = builtInLibrary[1].toAppearance(),
            x = 0.56f,
            y = 0.73f,
            scale = 1.0f,
            facingRight = false,
            depthPlane = DepthPlane.MIDGROUND,
            layerOrder = 1,
            expression = FacialExpression.NEUTRAL,
            lookAtCharacterId = miaId,
            voicePitch = 0.88f,
            voiceRate = 0.96f,
            entranceMs = 0L,
            exitMs = 12000L,
            actionClips = listOf(
                ActionClip(
                    clipId = "leo_sit",
                    action = CharacterActionType.SIT,
                    startMs = 0L,
                    durationMs = 3500L,
                    expression = FacialExpression.NEUTRAL,
                ),
                ActionClip(
                    clipId = "leo_listen",
                    action = CharacterActionType.LISTEN,
                    startMs = 3500L,
                    durationMs = 3000L,
                    expression = FacialExpression.THINKING,
                ),
                ActionClip(
                    clipId = "leo_react",
                    action = CharacterActionType.REACT,
                    startMs = 6500L,
                    durationMs = 2500L,
                    expression = FacialExpression.SURPRISED,
                    dialogueText = "Whoa, thanks Mia! That smells amazing!",
                    lipSyncEnabled = true,
                ),
                ActionClip(
                    clipId = "leo_stand",
                    action = CharacterActionType.STAND,
                    startMs = 9000L,
                    durationMs = 3000L,
                    expression = FacialExpression.LAUGHING,
                    dialogueText = "I'm ready, let's roll!",
                    lipSyncEnabled = true,
                ),
            ),
            keyframes = listOf(
                CharacterKeyframe(
                    keyframeId = "leo_kf_0",
                    timeMs = 0L,
                    x = 0.56f,
                    y = 0.74f,
                    scale = 1.0f,
                    facingRight = false,
                    expression = FacialExpression.NEUTRAL,
                ),
                CharacterKeyframe(
                    keyframeId = "leo_kf_1",
                    timeMs = 9000L,
                    x = 0.54f,
                    y = 0.72f,
                    scale = 1.02f,
                    facingRight = false,
                    expression = FacialExpression.LAUGHING,
                ),
            ),
        )

        val kai = SceneCharacter(
            characterId = kaiId,
            name = "Kai",
            appearance = builtInLibrary[2].toAppearance(),
            x = 0.86f,
            y = 0.65f,
            scale = 0.94f,
            facingRight = false,
            depthPlane = DepthPlane.BACKGROUND,
            layerOrder = 0,
            expression = FacialExpression.HAPPY,
            voicePitch = 1.04f,
            voiceRate = 1.05f,
            entranceMs = 400L,
            exitMs = 12000L,
            entranceStyle = TransitionStyle.FADE,
            actionClips = listOf(
                ActionClip(
                    clipId = "kai_walk",
                    action = CharacterActionType.WALK,
                    startMs = 400L,
                    durationMs = 3600L,
                    expression = FacialExpression.HAPPY,
                ),
                ActionClip(
                    clipId = "kai_eat",
                    action = CharacterActionType.EAT,
                    startMs = 4000L,
                    durationMs = 4000L,
                    expression = FacialExpression.HAPPY,
                ),
                ActionClip(
                    clipId = "kai_look",
                    action = CharacterActionType.LOOK_AROUND,
                    startMs = 8000L,
                    durationMs = 4000L,
                    expression = FacialExpression.THINKING,
                ),
            ),
            keyframes = listOf(
                CharacterKeyframe(
                    keyframeId = "kai_kf_0",
                    timeMs = 400L,
                    x = 0.88f,
                    y = 0.66f,
                    scale = 0.94f,
                    facingRight = false,
                    expression = FacialExpression.HAPPY,
                ),
                CharacterKeyframe(
                    keyframeId = "kai_kf_1",
                    timeMs = 4000L,
                    x = 0.76f,
                    y = 0.66f,
                    scale = 0.94f,
                    facingRight = false,
                    expression = FacialExpression.HAPPY,
                ),
            ),
        )

        val props = listOf(
            SceneProp(
                propId = cupId,
                name = "Latte Cup",
                type = PropType.COFFEE_CUP,
                x = 0.34f,
                y = 0.72f,
                scale = 1.0f,
                holderCharacterId = miaId,
            ),
            SceneProp(
                propId = appleId,
                name = "Crisp Apple",
                type = PropType.APPLE,
                x = 0.76f,
                y = 0.66f,
                scale = 0.95f,
                holderCharacterId = kaiId,
            ),
            SceneProp(
                propId = chairId,
                name = "Park Bench Chair",
                type = PropType.CHAIR,
                x = 0.56f,
                y = 0.79f,
                scale = 1.0f,
                holderCharacterId = "",
            ),
        )

        val interactions = listOf(
            CharacterInteraction(
                interactionId = "int_greet",
                type = InteractionType.GREETING,
                sourceCharacterId = miaId,
                targetCharacterId = leoId,
                startMs = 2600L,
                durationMs = 1200L,
                emotionLabel = "Wave",
            ),
            CharacterInteraction(
                interactionId = "int_convo",
                type = InteractionType.CONVERSATION,
                sourceCharacterId = miaId,
                targetCharacterId = leoId,
                startMs = 3500L,
                durationMs = 3000L,
                emotionLabel = "Chat",
            ),
            CharacterInteraction(
                interactionId = "int_pass_cup",
                type = InteractionType.PASS_OBJECT,
                sourceCharacterId = miaId,
                targetCharacterId = leoId,
                propId = cupId,
                startMs = 5800L,
                durationMs = 1700L,
                emotionLabel = "Handover",
            ),
            CharacterInteraction(
                interactionId = "int_handshake",
                type = InteractionType.HANDSHAKE,
                sourceCharacterId = miaId,
                targetCharacterId = leoId,
                startMs = 9500L,
                durationMs = 2000L,
                emotionLabel = "Deal!",
            ),
        )

        return AnimationScene(
            id = "",
            userId = userId,
            title = "Park Meetup",
            backdrop = BackdropType.PARK,
            durationMs = 12000L,
            characters = listOf(kai, leo, mia),
            props = props,
            interactions = interactions,
        )
    }
}
