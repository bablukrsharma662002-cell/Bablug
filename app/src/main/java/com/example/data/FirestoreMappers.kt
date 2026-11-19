package com.example.data

import com.example.model.ActionClip
import com.example.model.AnimationScene
import com.example.model.BackdropType
import com.example.model.BodyPose
import com.example.model.CharacterAccessory
import com.example.model.CharacterActionType
import com.example.model.CharacterAppearance
import com.example.model.CharacterInteraction
import com.example.model.CharacterKeyframe
import com.example.model.CharacterTemplate
import com.example.model.DepthPlane
import com.example.model.FacialExpression
import com.example.model.HairStyle
import com.example.model.InteractionType
import com.example.model.PropType
import com.example.model.SceneCharacter
import com.example.model.SceneProp
import com.example.model.TransitionStyle
import com.example.model.newUid
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

object FirestoreMappers {

    private fun Map<*, *>.numFloat(key: String, default: Float): Float =
        (this[key] as? Number)?.toFloat() ?: default

    private fun Map<*, *>.numLong(key: String, default: Long): Long =
        (this[key] as? Number)?.toLong() ?: default

    private fun Map<*, *>.numInt(key: String, default: Int): Int =
        (this[key] as? Number)?.toInt() ?: default

    private fun Map<*, *>.str(key: String, default: String): String =
        this[key] as? String ?: default

    private fun Map<*, *>.bool(key: String, default: Boolean): Boolean =
        this[key] as? Boolean ?: default

    // --- CharacterTemplate ---

    fun characterTemplateCreateMap(
        userId: String,
        template: CharacterTemplate,
    ): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to template.name.trim().ifBlank { "Character" }.take(60),
        "skinColorHex" to template.skinColorHex,
        "hairColorHex" to template.hairColorHex,
        "outfitColorHex" to template.outfitColorHex,
        "pantsColorHex" to template.pantsColorHex,
        "accentColorHex" to template.accentColorHex,
        "hairStyle" to HairStyle.fromRaw(template.hairStyle).name,
        "accessory" to CharacterAccessory.fromRaw(template.accessory).name,
        "voicePitch" to template.voicePitch.coerceIn(0.5, 2.0),
        "voiceRate" to template.voiceRate.coerceIn(0.5, 2.0),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun characterTemplateUpdateMap(template: CharacterTemplate): Map<String, Any> = mapOf(
        "name" to template.name.trim().ifBlank { "Character" }.take(60),
        "skinColorHex" to template.skinColorHex,
        "hairColorHex" to template.hairColorHex,
        "outfitColorHex" to template.outfitColorHex,
        "pantsColorHex" to template.pantsColorHex,
        "accentColorHex" to template.accentColorHex,
        "hairStyle" to HairStyle.fromRaw(template.hairStyle).name,
        "accessory" to CharacterAccessory.fromRaw(template.accessory).name,
        "voicePitch" to template.voicePitch.coerceIn(0.5, 2.0),
        "voiceRate" to template.voiceRate.coerceIn(0.5, 2.0),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun snapshotToCharacterTemplate(doc: DocumentSnapshot): CharacterTemplate {
        val data = doc.data ?: emptyMap<String, Any>()
        return CharacterTemplate(
            id = doc.id,
            userId = data.str("userId", ""),
            name = data.str("name", "Character"),
            skinColorHex = data.str("skinColorHex", "#F4C29F"),
            hairColorHex = data.str("hairColorHex", "#2B1E3E"),
            outfitColorHex = data.str("outfitColorHex", "#FFB020"),
            pantsColorHex = data.str("pantsColorHex", "#24344D"),
            accentColorHex = data.str("accentColorHex", "#FFB020"),
            hairStyle = HairStyle.fromRaw(data.str("hairStyle", HairStyle.BOB.name)).name,
            accessory = CharacterAccessory.fromRaw(data.str("accessory", CharacterAccessory.NONE.name)).name,
            voicePitch = (data["voicePitch"] as? Number)?.toDouble()?.coerceIn(0.5, 2.0) ?: 1.0,
            voiceRate = (data["voiceRate"] as? Number)?.toDouble()?.coerceIn(0.5, 2.0) ?: 1.0,
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        )
    }

    // --- AnimationScene ---

    fun sceneCreateMap(userId: String, scene: AnimationScene): Map<String, Any> = mapOf(
        "userId" to userId,
        "title" to scene.title.trim().ifBlank { "Untitled Scene" }.take(80),
        "backdrop" to scene.backdrop.name,
        "durationMs" to scene.durationMs.coerceIn(2000L, 60000L),
        "characters" to scene.characters.take(50).map { characterToMap(it) },
        "props" to scene.props.take(50).map { propToMap(it) },
        "interactions" to scene.interactions.take(100).map { interactionToMap(it) },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun sceneUpdateMap(scene: AnimationScene): Map<String, Any> = mapOf(
        "title" to scene.title.trim().ifBlank { "Untitled Scene" }.take(80),
        "backdrop" to scene.backdrop.name,
        "durationMs" to scene.durationMs.coerceIn(2000L, 60000L),
        "characters" to scene.characters.take(50).map { characterToMap(it) },
        "props" to scene.props.take(50).map { propToMap(it) },
        "interactions" to scene.interactions.take(100).map { interactionToMap(it) },
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun snapshotToScene(doc: DocumentSnapshot): AnimationScene {
        val data = doc.data ?: emptyMap<String, Any>()
        val rawChars = (data["characters"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        val rawProps = (data["props"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        val rawInts = (data["interactions"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

        return AnimationScene(
            id = doc.id,
            userId = data.str("userId", ""),
            title = data.str("title", "Untitled Scene"),
            backdrop = BackdropType.fromRaw(data.str("backdrop", BackdropType.PARK.name)),
            durationMs = data.numLong("durationMs", 12000L).coerceIn(2000L, 60000L),
            characters = rawChars.map { mapToCharacter(it) },
            props = rawProps.map { mapToProp(it) },
            interactions = rawInts.map { mapToInteraction(it) },
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        )
    }

    private fun poseToMap(pose: BodyPose): Map<String, Any> = mapOf(
        "headTilt" to pose.headTilt.toDouble(),
        "torsoLean" to pose.torsoLean.toDouble(),
        "leftArmAngle" to pose.leftArmAngle.toDouble(),
        "rightArmAngle" to pose.rightArmAngle.toDouble(),
        "leftElbowAngle" to pose.leftElbowAngle.toDouble(),
        "rightElbowAngle" to pose.rightElbowAngle.toDouble(),
        "leftLegAngle" to pose.leftLegAngle.toDouble(),
        "rightLegAngle" to pose.rightLegAngle.toDouble(),
    )

    private fun mapToPose(map: Map<*, *>?): BodyPose {
        if (map == null) return BodyPose()
        return BodyPose(
            headTilt = map.numFloat("headTilt", 0f),
            torsoLean = map.numFloat("torsoLean", 0f),
            leftArmAngle = map.numFloat("leftArmAngle", 18f),
            rightArmAngle = map.numFloat("rightArmAngle", -18f),
            leftElbowAngle = map.numFloat("leftElbowAngle", 10f),
            rightElbowAngle = map.numFloat("rightElbowAngle", -10f),
            leftLegAngle = map.numFloat("leftLegAngle", 4f),
            rightLegAngle = map.numFloat("rightLegAngle", -4f),
        )
    }

    private fun characterToMap(c: SceneCharacter): Map<String, Any> = mapOf(
        "characterId" to c.characterId,
        "name" to c.name.take(60),
        "skinColorHex" to c.appearance.skinColorHex,
        "hairColorHex" to c.appearance.hairColorHex,
        "outfitColorHex" to c.appearance.outfitColorHex,
        "pantsColorHex" to c.appearance.pantsColorHex,
        "accentColorHex" to c.appearance.accentColorHex,
        "hairStyle" to c.appearance.hairStyle.name,
        "accessory" to c.appearance.accessory.name,
        "x" to c.x.toDouble(),
        "y" to c.y.toDouble(),
        "scale" to c.scale.toDouble(),
        "rotationDeg" to c.rotationDeg.toDouble(),
        "facingRight" to c.facingRight,
        "depthPlane" to c.depthPlane.name,
        "layerOrder" to c.layerOrder,
        "locked" to c.locked,
        "visible" to c.visible,
        "groupId" to c.groupId,
        "expression" to c.expression.name,
        "eyeLookX" to c.eyeLookX.toDouble(),
        "eyeLookY" to c.eyeLookY.toDouble(),
        "lookAtCharacterId" to c.lookAtCharacterId,
        "bodyPose" to poseToMap(c.bodyPose),
        "voicePitch" to c.voicePitch.toDouble(),
        "voiceRate" to c.voiceRate.toDouble(),
        "lipSyncIntensity" to c.lipSyncIntensity.toDouble(),
        "entranceMs" to c.entranceMs,
        "exitMs" to c.exitMs,
        "entranceStyle" to c.entranceStyle.name,
        "exitStyle" to c.exitStyle.name,
        "actionClips" to c.actionClips.map { clip ->
            mapOf(
                "clipId" to clip.clipId,
                "action" to clip.action.name,
                "startMs" to clip.startMs,
                "durationMs" to clip.durationMs,
                "expression" to clip.expression.name,
                "dialogueText" to clip.dialogueText.take(240),
                "lipSyncEnabled" to clip.lipSyncEnabled,
            )
        },
        "keyframes" to c.keyframes.map { kf ->
            mapOf(
                "keyframeId" to kf.keyframeId,
                "timeMs" to kf.timeMs,
                "x" to kf.x.toDouble(),
                "y" to kf.y.toDouble(),
                "scale" to kf.scale.toDouble(),
                "rotationDeg" to kf.rotationDeg.toDouble(),
                "facingRight" to kf.facingRight,
                "expression" to kf.expression.name,
                "eyeLookX" to kf.eyeLookX.toDouble(),
                "eyeLookY" to kf.eyeLookY.toDouble(),
                "pose" to poseToMap(kf.pose),
            )
        },
    )

    private fun mapToCharacter(map: Map<*, *>): SceneCharacter {
        val clips = (map["actionClips"] as? List<*>)?.mapNotNull { it as? Map<*, *> }?.map { cm ->
            ActionClip(
                clipId = cm.str("clipId", newUid("clip")),
                action = CharacterActionType.fromRaw(cm.str("action", CharacterActionType.IDLE.name)),
                startMs = cm.numLong("startMs", 0L),
                durationMs = cm.numLong("durationMs", 2000L).coerceAtLeast(300L),
                expression = FacialExpression.fromRaw(cm.str("expression", FacialExpression.NEUTRAL.name)),
                dialogueText = cm.str("dialogueText", ""),
                lipSyncEnabled = cm.bool("lipSyncEnabled", true),
            )
        } ?: emptyList()

        val keyframes = (map["keyframes"] as? List<*>)?.mapNotNull { it as? Map<*, *> }?.map { km ->
            CharacterKeyframe(
                keyframeId = km.str("keyframeId", newUid("kf")),
                timeMs = km.numLong("timeMs", 0L),
                x = km.numFloat("x", 0.5f),
                y = km.numFloat("y", 0.72f),
                scale = km.numFloat("scale", 1.0f),
                rotationDeg = km.numFloat("rotationDeg", 0f),
                facingRight = km.bool("facingRight", true),
                expression = FacialExpression.fromRaw(km.str("expression", FacialExpression.NEUTRAL.name)),
                eyeLookX = km.numFloat("eyeLookX", 0f),
                eyeLookY = km.numFloat("eyeLookY", 0f),
                pose = mapToPose(km["pose"] as? Map<*, *>),
            )
        } ?: emptyList()

        return SceneCharacter(
            characterId = map.str("characterId", newUid("char")),
            name = map.str("name", "Character"),
            appearance = CharacterAppearance(
                skinColorHex = map.str("skinColorHex", "#F4C29F"),
                hairColorHex = map.str("hairColorHex", "#2B1E3E"),
                outfitColorHex = map.str("outfitColorHex", "#FFB020"),
                pantsColorHex = map.str("pantsColorHex", "#24344D"),
                accentColorHex = map.str("accentColorHex", "#FFB020"),
                hairStyle = HairStyle.fromRaw(map.str("hairStyle", HairStyle.BOB.name)),
                accessory = CharacterAccessory.fromRaw(map.str("accessory", CharacterAccessory.NONE.name)),
            ),
            x = map.numFloat("x", 0.5f),
            y = map.numFloat("y", 0.72f),
            scale = map.numFloat("scale", 1.0f),
            rotationDeg = map.numFloat("rotationDeg", 0f),
            facingRight = map.bool("facingRight", true),
            depthPlane = DepthPlane.fromRaw(map.str("depthPlane", DepthPlane.MIDGROUND.name)),
            layerOrder = map.numInt("layerOrder", 0),
            locked = map.bool("locked", false),
            visible = map.bool("visible", true),
            groupId = map.str("groupId", ""),
            expression = FacialExpression.fromRaw(map.str("expression", FacialExpression.HAPPY.name)),
            eyeLookX = map.numFloat("eyeLookX", 0f),
            eyeLookY = map.numFloat("eyeLookY", 0f),
            lookAtCharacterId = map.str("lookAtCharacterId", ""),
            bodyPose = mapToPose(map["bodyPose"] as? Map<*, *>),
            voicePitch = map.numFloat("voicePitch", 1.0f),
            voiceRate = map.numFloat("voiceRate", 1.0f),
            lipSyncIntensity = map.numFloat("lipSyncIntensity", 1.0f),
            entranceMs = map.numLong("entranceMs", 0L),
            exitMs = map.numLong("exitMs", 12000L),
            entranceStyle = TransitionStyle.fromRaw(map.str("entranceStyle", TransitionStyle.NONE.name)),
            exitStyle = TransitionStyle.fromRaw(map.str("exitStyle", TransitionStyle.NONE.name)),
            actionClips = clips,
            keyframes = keyframes,
        )
    }

    private fun propToMap(prop: SceneProp): Map<String, Any> = mapOf(
        "propId" to prop.propId,
        "name" to prop.name.take(60),
        "type" to prop.type.name,
        "x" to prop.x.toDouble(),
        "y" to prop.y.toDouble(),
        "scale" to prop.scale.toDouble(),
        "holderCharacterId" to prop.holderCharacterId,
        "visible" to prop.visible,
    )

    private fun mapToProp(map: Map<*, *>): SceneProp = SceneProp(
        propId = map.str("propId", newUid("prop")),
        name = map.str("name", "Prop"),
        type = PropType.fromRaw(map.str("type", PropType.COFFEE_CUP.name)),
        x = map.numFloat("x", 0.5f),
        y = map.numFloat("y", 0.78f),
        scale = map.numFloat("scale", 1.0f),
        holderCharacterId = map.str("holderCharacterId", ""),
        visible = map.bool("visible", true),
    )

    private fun interactionToMap(item: CharacterInteraction): Map<String, Any> = mapOf(
        "interactionId" to item.interactionId,
        "type" to item.type.name,
        "sourceCharacterId" to item.sourceCharacterId,
        "targetCharacterId" to item.targetCharacterId,
        "propId" to item.propId,
        "startMs" to item.startMs,
        "durationMs" to item.durationMs,
        "emotionLabel" to item.emotionLabel.take(40),
    )

    private fun mapToInteraction(map: Map<*, *>): CharacterInteraction = CharacterInteraction(
        interactionId = map.str("interactionId", newUid("int")),
        type = InteractionType.fromRaw(map.str("type", InteractionType.CONVERSATION.name)),
        sourceCharacterId = map.str("sourceCharacterId", ""),
        targetCharacterId = map.str("targetCharacterId", ""),
        propId = map.str("propId", ""),
        startMs = map.numLong("startMs", 0L),
        durationMs = map.numLong("durationMs", 2500L).coerceAtLeast(400L),
        emotionLabel = map.str("emotionLabel", ""),
    )
}
