package com.zeroindex.intake.data.db

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenCatalog
import org.junit.Test

class AllergenSeedTest {
    @Test fun `seed ids are unique and deterministic`() {
        val ids = AllergenCatalog.ALL.flatMap { a -> a.keywords.map { AllergenSeed.keywordId(a.key, it.term) } }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids.size).isEqualTo(AllergenSeed.keywordCount)
        assertThat(AllergenSeed.keywordId("dairy", "sodium caseinate")).isEqualTo("seed-dairy-sodium-caseinate")
        val defs = AllergenCatalog.ALL.map { AllergenSeed.definitionId(it.key) }
        assertThat(defs).containsNoDuplicates()
    }

    @Test fun `seed ids are distinguishable from a user keyword`() {
        assertThat(AllergenSeed.keywordId("tree nuts", "pine nut")).startsWith("seed-")
    }
}
