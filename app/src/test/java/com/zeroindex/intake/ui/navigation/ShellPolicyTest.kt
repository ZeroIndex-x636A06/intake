package com.zeroindex.intake.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShellPolicyTest {
    @Test
    fun `the pill and the avatar are on the four tab roots only`() {
        listOf(Route.Today, Route.Nutrition, Route.Train, Route.Progress).forEach {
            assertThat(ShellPolicy.pillVisible(it)).isTrue()
            assertThat(ShellPolicy.avatarVisible(it)).isTrue()
            assertThat(ShellPolicy.reservesPillSpace(it)).isTrue()
        }
    }

    @Test
    fun `pushed screens, sheets and full-screen tasks hide the pill`() {
        listOf(
            Route.You, Route.Settings, Route.SettingsGroup("water"), Route.Allergens, Route.Drinks, Route.SwapSheet("LUNCH"),
            Route.WeeklyCheckIn, Route.ProgressPhotos, Route.PhotoCompare, Route.MealPlans, Route.Programs,
            Route.ActiveWorkout("s1"), Route.CookMode("r1"), Route.Onboarding, Route.Profile, Route.YourPlan,
        ).forEach {
            assertThat(ShellPolicy.pillVisible(it)).isFalse()
            assertThat(ShellPolicy.avatarVisible(it)).isFalse()
            assertThat(ShellPolicy.reservesPillSpace(it)).isFalse()
        }
        assertThat(ShellPolicy.pillVisible(null)).isFalse()
    }

    @Test
    fun `the avatar opens You from every tab and there is no search icon yet`() {
        assertThat(ShellPolicy.avatarTarget()).isEqualTo(Route.You)
        TopLevelDestination.entries.forEach { assertThat(ShellPolicy.searchVisible(it.route)).isFalse() }
    }

    @Test
    fun `system back on a root other than Today goes to Today and on Today leaves`() {
        assertThat(ShellPolicy.backFromRoot(TopLevelDestination.TODAY)).isEqualTo(RootBackAction.EXIT)
        listOf(TopLevelDestination.NUTRITION, TopLevelDestination.TRAIN, TopLevelDestination.PROGRESS).forEach {
            assertThat(ShellPolicy.backFromRoot(it)).isEqualTo(RootBackAction.GO_TO_TODAY)
        }
    }
}
