package com.zeroindex.intake.data.health

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HealthAvailabilityRulesTest {
    @Test fun `an update is only asked for when Health Connect is installed`() {
        assertThat(HealthAvailabilityRules.resolve(HealthAvailability.UPDATE_REQUIRED, providerInstalled = false))
            .isEqualTo(HealthAvailability.NOT_INSTALLED)
        assertThat(HealthAvailabilityRules.resolve(HealthAvailability.UPDATE_REQUIRED, providerInstalled = true))
            .isEqualTo(HealthAvailability.UPDATE_REQUIRED)
    }

    @Test fun `other answers pass through`() {
        HealthAvailability.entries.filter { it != HealthAvailability.UPDATE_REQUIRED }.forEach {
            assertThat(HealthAvailabilityRules.resolve(it, providerInstalled = false)).isEqualTo(it)
            assertThat(HealthAvailabilityRules.resolve(it, providerInstalled = true)).isEqualTo(it)
        }
    }
}
