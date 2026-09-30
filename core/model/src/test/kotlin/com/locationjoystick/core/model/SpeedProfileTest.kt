package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedProfileTest {
    @Test
    fun `defaultProfiles map id to speed in meters per second`() {
        val expected =
            mapOf(
                "slow_walk" to 0.3,
                "walk" to 2.0 / 3.6,
                "run" to 8.0 / 3.6,
                "bike" to 15.0 / 3.6,
                "drive" to 15.0,
            )
        val actual = SpeedProfile.defaultProfiles().associate { it.id to it.speedMetersPerSecond }

        assertEquals(expected.keys, actual.keys)
        for ((id, speed) in expected) {
            assertEquals("$id speed", speed, actual.getValue(id), 0.001)
        }
    }
}
