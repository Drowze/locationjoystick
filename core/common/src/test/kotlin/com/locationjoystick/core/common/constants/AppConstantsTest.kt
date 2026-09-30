package com.locationjoystick.core.common.constants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConstantsTest {
    @Test
    fun `DEFAULT_REPLAY_SPEED_MS equals WALK_SPEED_MPS`() {
        assertEquals(
            AppConstants.ProfileConstants.WALK_SPEED_MPS,
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS,
            0.0001,
        )
    }

    @Test
    fun `DEFAULT_REPLAY_SPEED_MS is within valid speed range`() {
        assertTrue(
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS >= AppConstants.ProfileConstants.MIN_SPEED_MS,
        )
        assertTrue(
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS <= AppConstants.ProfileConstants.MAX_SPEED_MS,
        )
    }

    @Test
    fun `speed profile ordering is walk less than run less than bike less than drive`() {
        assertTrue(
            AppConstants.ProfileConstants.WALK_SPEED_MPS < AppConstants.ProfileConstants.RUN_SPEED_MPS,
        )
        assertTrue(
            AppConstants.ProfileConstants.RUN_SPEED_MPS < AppConstants.ProfileConstants.BIKE_SPEED_MPS,
        )
        assertTrue(
            AppConstants.ProfileConstants.BIKE_SPEED_MPS < AppConstants.ProfileConstants.DRIVE_SPEED_MPS,
        )
    }

    @Test
    fun `planting roam default speed is bike`() {
        assertEquals(
            AppConstants.ProfileConstants.PROFILE_ID_BIKE,
            AppConstants.RoamingConstants.PLANTING_DEFAULT_SPEED_PROFILE_ID,
        )
    }

    @Test
    fun `planting default radius is within clamp range`() {
        assertTrue(
            AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS >=
                AppConstants.RouteConstants.ROUTE_MIN_RADIUS_METERS,
        )
        assertTrue(
            AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS <=
                AppConstants.RouteConstants.PLANTING_MAX_RADIUS_METERS,
        )
    }

    @Test
    fun `roaming and route planting share the same max radius and chord constants`() {
        assertEquals(
            AppConstants.PlantingConstants.MAX_RADIUS_METERS,
            AppConstants.RoamingConstants.PLANTING_MAX_RADIUS_METERS,
            0.0,
        )
        assertEquals(
            AppConstants.PlantingConstants.MAX_RADIUS_METERS,
            AppConstants.RouteConstants.PLANTING_MAX_RADIUS_METERS,
            0.0,
        )
        assertEquals(
            AppConstants.PlantingConstants.CHORD_METERS,
            AppConstants.RoamingConstants.PLANTING_CHORD_METERS,
            0.0,
        )
        assertEquals(
            AppConstants.PlantingConstants.CHORD_METERS,
            AppConstants.RouteConstants.PLANTING_CHORD_METERS,
            0.0,
        )
    }

    @Test
    fun `default roaming radius is within the allowed range`() {
        assertTrue(
            AppConstants.RoamingConstants.DEFAULT_RADIUS_METERS in
                AppConstants.RoamingConstants.ROAMING_MIN_RADIUS_METERS..AppConstants.RoamingConstants.RADIUS_MAX_METERS,
        )
    }

    @Test
    fun `paste temp route id is reserved and not a hot route prefix`() {
        assertTrue(!AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID.startsWith("hot_route_"))
    }

    @Test
    fun `teleport between default delay is within min and max`() {
        val min = AppConstants.RouteConstants.TELEPORT_BETWEEN_MIN_DELAY_SECONDS
        val max = AppConstants.RouteConstants.TELEPORT_BETWEEN_MAX_DELAY_SECONDS
        val default = AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS
        assertTrue(default in min..max)
    }

    @Test
    fun `whats new json is packed as an APK asset named after the version`() {
        assertEquals("0.20.16.json", AppConstants.WhatsNewConstants.assetFileName("0.20.16"))
        assertEquals("0.20.16.json", AppConstants.WhatsNewConstants.assetFileName("0.20.16-alpha1"))
    }
}
