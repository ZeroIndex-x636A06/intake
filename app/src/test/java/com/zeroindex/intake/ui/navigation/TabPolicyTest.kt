package com.zeroindex.intake.ui.navigation

import com.zeroindex.intake.ui.motion.isMotionReduced
import com.zeroindex.intake.ui.screens.hub.defaultMealSlot
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.screens.more.ScriptTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabPolicyTest {

    @Test fun `choosing another tab switches and restores that tab's stack`() {
        assertEquals(TabAction.SWITCH, TabPolicy.actionFor("today", "nutrition", currentIsAtRoot = true))
        // Even from deep inside a tab: the stack is saved, not popped.
        assertEquals(TabAction.SWITCH, TabPolicy.actionFor("nutrition", "train", currentIsAtRoot = false))
    }

    @Test fun `choosing the current tab below its root pops to the root`() {
        assertEquals(TabAction.POP_TO_ROOT, TabPolicy.actionFor("nutrition", "nutrition", currentIsAtRoot = false))
    }

    @Test fun `choosing the current tab at its root does nothing`() {
        assertEquals(TabAction.NONE, TabPolicy.actionFor("nutrition", "nutrition", currentIsAtRoot = true))
    }

    @Test fun `no current tab counts as a switch`() {
        assertEquals(TabAction.SWITCH, TabPolicy.actionFor(null, "today", currentIsAtRoot = false))
    }

    @Test fun `fade through is only for moving between two known tabs`() {
        assertTrue(TabPolicy.isTabSwitch("today", "nutrition"))
        assertFalse(TabPolicy.isTabSwitch("nutrition", "nutrition"))
        assertFalse(TabPolicy.isTabSwitch(null, "nutrition"))
        assertFalse(TabPolicy.isTabSwitch("nutrition", null))
    }

    @Test fun `the pill has the four destinations in order`() {
        assertEquals(
            listOf("TODAY", "NUTRITION", "TRAIN", "PROGRESS"),
            TopLevelDestination.entries.map { it.name },
        )
        assertEquals(
            listOf(Route.Today, Route.Nutrition, Route.Train, Route.Progress),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test fun `only an animator scale of zero reduces motion`() {
        assertTrue(isMotionReduced(0f))
        assertFalse(isMotionReduced(1f))
        assertFalse(isMotionReduced(0.5f))
        assertFalse(isMotionReduced(10f))
    }

    @Test fun `script topics keep food and training docs apart`() {
        assertEquals(ScriptTopic.FOOD, ScriptTopic.of("meal-plan"))
        assertEquals(ScriptTopic.FOOD, ScriptTopic.of("examples/meal-plan-sample"))
        assertEquals(ScriptTopic.TRAIN, ScriptTopic.of("program"))
        assertEquals(ScriptTopic.TRAIN, ScriptTopic.of("progress-rules"))
        assertEquals(ScriptTopic.ALL, ScriptTopic.of("README"))
        assertEquals(ScriptTopic.ALL, ScriptTopic.of("errors"))
        assertEquals("meal-plan", ScriptTopic.FOOD.firstPage)
        assertEquals("program", ScriptTopic.TRAIN.firstPage)
        assertEquals("README", ScriptTopic.ALL.firstPage)
        assertTrue(ScriptTopic.FOOD.pages.intersect(ScriptTopic.TRAIN.pages.toSet()).isEmpty())
    }

    @Test fun `log food starts in a sensible meal slot`() {
        assertEquals(MealSlot.BREAKFAST, defaultMealSlot(8))
        assertEquals(MealSlot.LUNCH, defaultMealSlot(12))
        assertEquals(MealSlot.DINNER, defaultMealSlot(19))
        assertEquals(MealSlot.SNACK, defaultMealSlot(23))
        assertEquals(MealSlot.SNACK, defaultMealSlot(2))
    }
}
