package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.AnimationEvaluator
import com.example.model.CharacterActionType
import com.example.model.StarterContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StageCraft", appName)
    }

    @Test
    fun `multi character showcase evaluates simultaneous actions and prop handover`() {
        val scene = StarterContent.createShowcaseScene("test_uid")

        // At 4.5s: Mia talks, Leo listens, Kai eats simultaneously
        val midFrame = AnimationEvaluator.evaluateFrame(scene, 4500L)
        assertEquals(3, midFrame.characters.size)
        val mia = midFrame.characters.first { it.character.name == "Mia" }
        val leo = midFrame.characters.first { it.character.name == "Leo" }
        val kai = midFrame.characters.first { it.character.name == "Kai" }
        assertEquals(CharacterActionType.TALK, mia.activeAction)
        assertEquals(CharacterActionType.LISTEN, leo.activeAction)
        assertEquals(CharacterActionType.EAT, kai.activeAction)
        assertTrue(mia.activeDialogue.isNotBlank())

        // At 6.6s: Coffee cup is in transit from Mia to Leo
        val passFrame = AnimationEvaluator.evaluateFrame(scene, 6600L)
        val cup = passFrame.props.first { it.prop.propId == "prop_coffee_1" }
        assertTrue(cup.inTransit)

        // At 8.5s: Coffee cup ownership transferred to Leo
        val postPassFrame = AnimationEvaluator.evaluateFrame(scene, 8500L)
        val postCup = postPassFrame.props.first { it.prop.propId == "prop_coffee_1" }
        assertEquals(leo.character.characterId, postCup.holderId)
    }
}
