package com.zeroindex.intake.ui.screens.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.AllergenStatus
import org.junit.Test

class MentionedAvoidedTest {

    @Test fun appliesOnlyToTheQuietVerdict() {
        assertThat(MentionedAvoided.applies(AllergenStatus.MENTIONED_AVOIDED)).isTrue()
        AllergenStatus.entries.filter { it != AllergenStatus.MENTIONED_AVOIDED }.forEach {
            assertThat(MentionedAvoided.applies(it)).isFalse()
        }
        assertThat(MentionedAvoided.applies(null)).isFalse()
    }

    @Test fun noteNamesTheTermsAndIsNotAlarming() {
        val note = MentionedAvoided.note(listOf("dairy", "milk", "dairy"))
        assertThat(note).contains("dairy, milk")
        assertThat(note).contains("not a warning")
        assertThat(note).doesNotContain("Check the labels before cooking")
    }

    @Test fun noteWorksWithoutTerms() {
        assertThat(MentionedAvoided.note(emptyList())).contains("something you avoid")
    }
}
