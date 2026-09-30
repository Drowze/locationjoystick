package com.locationjoystick.core.routing

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.distanceTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class RouteInterpolatorTest {
    private lateinit var interpolator: RouteInterpolator

    @Before
    fun setUp() {
        interpolator = RouteInterpolator()
    }

    // advancePosition — bearing direction

    @Test
    fun `advancePosition direction by bearing`() {
        // Table: bearing -> expected sign of latitude and longitude change
        data class Row(
            val bearing: Double,
            val distance: Double,
            val startLat: Double,
            val name: String,
            val expectLatSign: Int, // +1, 0, or -1
            val expectLonSign: Int, // +1, 0, or -1
            val latTol: Double = 1e-3,
            val lonTol: Double = 1e-3,
        )

        val rows =
            listOf(
                // 0° = North: latitude increases, longitude unchanged
                Row(bearing = 0.0, distance = 100.0, startLat = 0.0, name = "0° North", expectLatSign = 1, expectLonSign = 0),
                // 90° = East: longitude increases, latitude unchanged
                Row(bearing = 90.0, distance = 100.0, startLat = 0.0, name = "90° East", expectLatSign = 0, expectLonSign = 1),
                // 180° = South: latitude decreases, longitude unchanged
                Row(bearing = 180.0, distance = 100.0, startLat = 1.0, name = "180° South", expectLatSign = -1, expectLonSign = 0),
                // 270° = West: longitude decreases, latitude unchanged
                Row(bearing = 270.0, distance = 1000.0, startLat = 0.0, name = "270° West", expectLatSign = 0, expectLonSign = -1),
                // 45° = Northeast: both increase equally (at equator)
                Row(
                    bearing = 45.0,
                    distance = 1000.0,
                    startLat = 0.0,
                    name = "45° Northeast",
                    expectLatSign = 1,
                    expectLonSign = 1,
                    latTol = 0.0001,
                    lonTol = 0.0001,
                ),
            )

        for (row in rows) {
            val from = LatLng(row.startLat, 0.0)
            val result = interpolator.advancePosition(from, row.bearing, row.distance)
            val dLat = result.latitude - from.latitude
            val dLon = result.longitude - from.longitude

            when {
                row.expectLatSign > 0 -> assertTrue("${row.name}: latitude should increase", result.latitude > from.latitude)
                row.expectLatSign < 0 -> assertTrue("${row.name}: latitude should decrease", result.latitude < from.latitude)
                row.expectLatSign == 0 -> assertEquals("${row.name}: latitude unchanged", from.latitude, result.latitude, row.latTol)
            }

            when {
                row.expectLonSign > 0 -> assertTrue("${row.name}: longitude should increase", result.longitude > from.longitude)
                row.expectLonSign < 0 -> assertTrue("${row.name}: longitude should decrease", result.longitude < from.longitude)
                row.expectLonSign == 0 -> assertEquals("${row.name}: longitude unchanged", from.longitude, result.longitude, row.lonTol)
            }

            // 45° special case: lat and lon should be roughly equal
            if (row.bearing == 45.0) {
                assertEquals("${row.name}: lat ≈ lon within 1%", result.latitude, result.longitude, result.latitude * 0.01)
            }
        }
    }

    @Test
    fun `advancePosition 100m north moves approx 100m`() {
        val from = LatLng(0.0, 0.0)
        val result = interpolator.advancePosition(from, 0.0, 100.0)
        // 100m / 6371000 * (180/π) ≈ 0.0008994°
        assertEquals(0.0008994, result.latitude, 0.00001)
    }

    @Test
    fun `advancePosition distance traveled is proportional to delta`() {
        val from = LatLng(0.0, 0.0)
        val advance100 = interpolator.advancePosition(from, 0.0, 100.0)
        val advance200 = interpolator.advancePosition(from, 0.0, 200.0)
        assertTrue("200m should go further north than 100m", advance200.latitude > advance100.latitude)
        // ~2x distance should give ~2x latitude change (linear approximation at equator)
        assertEquals(advance100.latitude * 2, advance200.latitude, advance100.latitude * 0.1)
    }

    @Test
    fun `advancePosition half-earth distance wraps correctly`() {
        val from = LatLng(0.0, 0.0)
        val result = interpolator.advancePosition(from, 0.0, 20_000_000.0) // half circumference
        // After going half way around earth north, should be near equator on opposite side
        assertTrue("should be near equator after half-earth distance", abs(result.latitude) < 5.0)
    }

    // interpolateAlongRoute — guard conditions

    @Test
    fun `interpolateAlongRoute guard conditions`() {
        // Table: waypoints, index -> all expect reachedEnd
        data class Row(
            val waypoints: List<LatLng>,
            val index: Int,
            val name: String,
            val assertNextIndex: Boolean = false,
        )

        val rows =
            listOf(
                Row(emptyList(), 0, "empty list"),
                Row(listOf(LatLng(1.0, 0.0)), 0, "single waypoint"),
                Row(listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0)), 99, "index past waypoints"),
                Row(listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0)), 2, "index equal to size", assertNextIndex = true),
            )

        val pos = LatLng(0.0, 0.0)
        for (row in rows) {
            val result = interpolator.interpolateAlongRoute(row.waypoints, pos, row.index, 1.4, 1000)
            assertTrue("${row.name}: should reach end", result.reachedEnd)
            if (row.assertNextIndex) {
                assertEquals("${row.name}: nextWaypointIndex should equal size", row.waypoints.size, result.nextWaypointIndex)
            }
        }
    }

    @Test
    fun `interpolateAlongRoute with index 0 advances toward first waypoint`() {
        val start = LatLng(0.0, 0.0)
        val wp0 = LatLng(0.0, 0.0)
        val wp1 = LatLng(0.01, 0.0)
        val waypoints = listOf(wp0, wp1)

        // When current position is at wp0 and index is 0, it targets wp0
        // Since distance to target is 0, it should snap and advance index
        val result = interpolator.interpolateAlongRoute(waypoints, start, 0, 1.4, 1000)
        assertFalse(result.reachedEnd)
        assertEquals(1, result.nextWaypointIndex)
    }

    // interpolateAlongRoute — normal advance

    @Test
    fun `interpolateAlongRoute advance distance and speed`() {
        // Table: (speedMs, deltaMs) with fixed waypoints
        // Start (0,0), far target (5.0, 0.0), index 1
        data class Row(
            val speedMs: Double,
            val deltaMs: Long,
            val name: String,
        )

        val start = LatLng(0.0, 0.0)
        val farTarget = LatLng(5.0, 0.0) // very far
        val waypoints = listOf(start, farTarget)

        val rows =
            listOf(
                Row(1.0, 1000, "speed 1.0 m/s, 1000ms"),
                Row(3.0, 1000, "speed 3.0 m/s, 1000ms"),
                Row(1.0, 500, "speed 1.0 m/s, 500ms"),
                Row(1.4, AppConstants.LocationConstants.UPDATE_INTERVAL_MS, "speed 1.4 m/s, standard interval"),
            )

        for (row in rows) {
            val result = interpolator.interpolateAlongRoute(waypoints, start, 1, row.speedMs, row.deltaMs)
            assertFalse("${row.name}: should not reach end", result.reachedEnd)
            assertEquals("${row.name}: index stays 1", 1, result.nextWaypointIndex)
            assertEquals("${row.name}: longitude 0 within 1e-4", 0.0, result.position.longitude, 1e-4)

            // Assert distance traveled matches speed * deltaMs / 1000 within 1%
            val expectedDistance = row.speedMs * (row.deltaMs / 1000.0)
            val actualDistance = start.distanceTo(result.position)
            assertEquals("${row.name}: distance ≈ speed × time", expectedDistance, actualDistance, expectedDistance * 0.01)
        }
    }

    @Test
    fun `interpolateAlongRoute preserves longitude when moving north`() {
        val start = LatLng(0.0, 10.0)
        val north = LatLng(0.1, 10.0)
        val waypoints = listOf(start, north)
        val result = interpolator.interpolateAlongRoute(waypoints, start, 1, 1.4, 1000)
        assertEquals(10.0, result.position.longitude, 0.0001)
        assertTrue("should move north", result.position.latitude > start.latitude)
    }

    @Test
    fun `interpolateAlongRoute zero speed and zero time do not advance`() {
        // Table: (speed, deltaTime) both expect no advance
        data class Row(
            val speedMs: Double,
            val deltaMs: Long,
            val name: String,
        )

        val start = LatLng(0.0, 0.0)
        val target = LatLng(0.01, 0.0)
        val waypoints = listOf(start, target)

        val rows =
            listOf(
                Row(0.0, 1000, "zero speed"),
                Row(1.4, 0, "zero deltaTime"),
            )

        for (row in rows) {
            val result = interpolator.interpolateAlongRoute(waypoints, start, 1, row.speedMs, row.deltaMs)
            assertFalse("${row.name}: should not reach end", result.reachedEnd)
            assertEquals("${row.name}: latitude unchanged", start.latitude, result.position.latitude, 1e-5)
            assertEquals("${row.name}: index stays 1", 1, result.nextWaypointIndex)
        }
    }

    // interpolateAlongRoute — snap to waypoint (WAYPOINT_SNAP_THRESHOLD_METERS ≈ 1.0m)

    @Test
    fun `interpolateAlongRoute snap with next waypoint`() {
        // Table: different distances from target, speed 0 or 5.0 m/s
        // Waypoints: [start (0,0), target, next (0.1,0)], index 1
        data class Row(
            val targetLat: Double,
            val speedMs: Double,
            val expectedIndex: Int,
            val name: String,
            val assertPosition: Boolean = false,
        )

        val start = LatLng(0.0, 0.0)
        val next = LatLng(0.1, 0.0)

        val rows =
            listOf(
                // Within snap threshold (≈1.0m): snap to target and advance index
                Row(0.000004, 0.0, 2, "0.44m (0.000004°) → snap to target, speed 0"),
                Row(0.000008, 0.0, 2, "0.89m (0.000008°) → snap to target, speed 0", assertPosition = true),
                // Outside snap threshold (≈1.1m+): do not snap
                Row(0.000010, 0.0, 1, "1.11m (0.000010°) → no snap, speed 0"),
                Row(0.000020, 0.0, 1, "2.22m (0.000020°) → no snap, speed 0"),
                // With speed: snap overriding only threshold, still advances if within it
                Row(0.00001, 5.0, 2, "≈1.1m with speed 5.0 m/s → snap due to overshoot"),
            )

        for (row in rows) {
            val target = LatLng(row.targetLat, 0.0)
            val waypoints = listOf(start, target, next)
            val result = interpolator.interpolateAlongRoute(waypoints, start, 1, row.speedMs, 1000)

            assertEquals("${row.name}: nextWaypointIndex", row.expectedIndex, result.nextWaypointIndex)
            assertFalse("${row.name}: should not reach end", result.reachedEnd)

            if (row.expectedIndex == 2 && row.speedMs == 0.0) {
                assertEquals("${row.name}: position snapped to target lat", target.latitude, result.position.latitude, 1e-9)
            }
            if (row.expectedIndex == 1 && row.speedMs == 0.0) {
                assertEquals("${row.name}: position at start", start.latitude, result.position.latitude, 1e-9)
            }
        }
    }

    @Test
    fun `interpolateAlongRoute snapping sets position to target waypoint`() {
        val start = LatLng(0.0, 0.0)
        val target = LatLng(0.000005, 0.000005) // within snap threshold
        val waypoints = listOf(start, target)

        val result = interpolator.interpolateAlongRoute(waypoints, start, 1, 1.4, 1000)
        assertEquals(target.latitude, result.position.latitude, 0.000001)
        assertEquals(target.longitude, result.position.longitude, 0.000001)
    }

    @Test
    fun `interpolateAlongRoute snap to last waypoint`() {
        // Table: different last waypoint distances and speeds
        // All expect reachedEnd
        data class Row(
            val lastLat: Double,
            val speedMs: Double,
            val name: String,
            val assertLatitude: Boolean = false,
        )

        val start = LatLng(0.0, 0.0)

        val rows =
            listOf(
                Row(0.000008, 1.4, "0.89m with speed 1.4 m/s"),
                Row(0.000001, 100.0, "0.11m with speed 100.0 m/s", assertLatitude = true),
                Row(0.001, 500.0, "111m with speed 500.0 m/s"),
            )

        for (row in rows) {
            val last = LatLng(row.lastLat, 0.0)
            val waypoints = listOf(start, last)
            val result = interpolator.interpolateAlongRoute(waypoints, start, 1, row.speedMs, 1000)

            assertTrue("${row.name}: should reach end", result.reachedEnd)

            if (row.assertLatitude) {
                assertEquals("${row.name}: position at last lat", last.latitude, result.position.latitude, 1e-5)
            }
        }
    }

    // interpolateAlongRoute — carry-forward

    @Test
    fun `interpolateAlongRoute carries leftover distance past waypoint into next segment`() {
        // Waypoints spaced ~111m apart (1 degree lat ≈ 111km, so 0.001 deg ≈ 111m)
        val wp0 = LatLng(0.0, 0.0)
        val wp1 = LatLng(0.001, 0.0) // ~111m north
        val wp2 = LatLng(0.002, 0.0) // another ~111m north
        val waypoints = listOf(wp0, wp1, wp2)

        // Speed high enough to overshoot wp1 but not reach wp2 in one tick: 200m/s for 1s = 200m,
        // and wp0->wp1 + wp1->wp2 is ~222m total.
        val result =
            interpolator.interpolateAlongRoute(
                waypoints = waypoints,
                currentPosition = wp0,
                currentWaypointIndex = 1,
                speedMs = 200.0,
                deltaTimeMs = 1000L,
            )

        // Should advance index to 2 (targeting wp2) and the full 200m budget is consumed:
        // 111m to reach wp1, then the ~89m leftover carried past it toward wp2.
        assertEquals(2, result.nextWaypointIndex)
        assertFalse(result.reachedEnd)
        val distanceToWp1 = wp0.distanceTo(wp1)
        val leftover = 200.0 - distanceToWp1
        val bearingToWp2 = calculateBearing(wp1.latitude, wp1.longitude, wp2.latitude, wp2.longitude)
        val expected = interpolator.advancePosition(wp1, bearingToWp2, leftover)
        assertEquals(expected.latitude, result.position.latitude, 1e-9)
        assertTrue("leftover should carry position past wp1", result.position.latitude > wp1.latitude)
    }

    @Test
    fun `interpolateAlongRoute budget spanning three waypoints consumes full distance in one call`() {
        // Four waypoints ~111m apart each; budget covers the first two segments fully
        // plus part of the third, all within a single interpolateAlongRoute call.
        val wp0 = LatLng(0.0, 0.0)
        val wp1 = LatLng(0.001, 0.0)
        val wp2 = LatLng(0.002, 0.0)
        val wp3 = LatLng(0.003, 0.0)
        val waypoints = listOf(wp0, wp1, wp2, wp3)

        // 300m/s for 1s = 300m budget; wp0->wp1->wp2 is ~222m, leaving ~78m carried into wp2->wp3.
        val result =
            interpolator.interpolateAlongRoute(
                waypoints = waypoints,
                currentPosition = wp0,
                currentWaypointIndex = 1,
                speedMs = 300.0,
                deltaTimeMs = 1000L,
            )

        // Full budget crossed two full segments — index lands past wp1 and wp2, targeting wp3.
        assertEquals(3, result.nextWaypointIndex)
        assertFalse(result.reachedEnd)
        val leftover = 300.0 - wp0.distanceTo(wp1) - wp1.distanceTo(wp2)
        val bearingToWp3 = calculateBearing(wp2.latitude, wp2.longitude, wp3.latitude, wp3.longitude)
        val expected = interpolator.advancePosition(wp2, bearingToWp3, leftover)
        assertEquals(expected.latitude, result.position.latitude, 1e-9)
        assertTrue("leftover should carry position past wp2", result.position.latitude > wp2.latitude)
    }

    @Test
    fun `interpolateAlongRoute carry-forward moves position into next segment after overshooting waypoint`() {
        // 4 waypoints so nextIndex+1 < size is true, enabling carry-forward
        val wp0 = LatLng(0.0, 0.0)
        val wp1 = LatLng(0.001, 0.0) // ~111m north
        val wp2 = LatLng(0.002, 0.0) // another ~111m north
        val wp3 = LatLng(0.003, 0.0)
        val waypoints = listOf(wp0, wp1, wp2, wp3)

        // 200 m/s × 1s = 200m; wp1 is ~111m away → ~89m leftover carries into wp1→wp2
        val result =
            interpolator.interpolateAlongRoute(
                waypoints = waypoints,
                currentPosition = wp0,
                currentWaypointIndex = 1,
                speedMs = 200.0,
                deltaTimeMs = 1000L,
            )

        assertFalse(result.reachedEnd)
        assertEquals("Index should advance to wp2", 2, result.nextWaypointIndex)
        assertTrue(
            "Carry-forward: position should be north of wp1, not stuck at it",
            result.position.latitude > wp1.latitude,
        )
        assertTrue(
            "Carry-forward: position should not overshoot wp2",
            result.position.latitude <= wp2.latitude,
        )
    }

    @Test
    fun `interpolateAlongRoute carry-forward keeps consuming budget across a short intermediate segment`() {
        // Extreme overshoot: speed high enough that leftover after wp1 exceeds the
        // short wp1->wp2 segment too — the fix keeps consuming budget into wp2->wp3
        // instead of dropping the remainder at wp2 (the old, capped behavior).
        val wp0 = LatLng(0.0, 0.0)
        val wp1 = LatLng(0.001, 0.0) // ~111m
        val wp2 = LatLng(0.0011, 0.0) // only ~11m from wp1
        val wp3 = LatLng(0.01, 0.0)
        val waypoints = listOf(wp0, wp1, wp2, wp3)

        val result =
            interpolator.interpolateAlongRoute(
                waypoints = waypoints,
                currentPosition = wp0,
                currentWaypointIndex = 1,
                speedMs = 1000.0,
                deltaTimeMs = 1000L,
            )

        assertFalse(result.reachedEnd)
        // Full 1000m budget crosses wp1 (~111m) and wp2 (~11m more), landing in the wp2->wp3 leg.
        assertEquals("Index should advance past wp2 to wp3", 3, result.nextWaypointIndex)
        assertTrue(
            "Leftover budget should carry position past wp2, not cap there",
            result.position.latitude > wp2.latitude,
        )
        assertTrue(
            "Should not overshoot wp3 since the full budget is less than the whole route",
            result.position.latitude < wp3.latitude,
        )
    }

    @Test
    fun `interpolateAlongRoute carry-forward happens even when the next waypoint is the last one`() {
        // 3 waypoints: reaching wp1 still has budget left over, and wp2 is the last
        // waypoint — the fix carries the leftover into wp1->wp2 instead of dropping it
        // (the old bug required a waypoint *after* the next one to carry at all).
        val wp0 = LatLng(0.0, 0.0)
        val wp1 = LatLng(0.001, 0.0) // ~111m
        val wp2 = LatLng(0.002, 0.0)
        val waypoints = listOf(wp0, wp1, wp2)

        val result =
            interpolator.interpolateAlongRoute(
                waypoints = waypoints,
                currentPosition = wp0,
                currentWaypointIndex = 1,
                speedMs = 200.0,
                deltaTimeMs = 1000L,
            )

        assertFalse(result.reachedEnd)
        assertEquals(2, result.nextWaypointIndex)
        // Position carries past wp1 toward wp2 instead of snapping exactly to wp1.
        assertTrue(result.position.latitude > wp1.latitude)
        assertTrue(result.position.latitude < wp2.latitude)
    }

    // interpolateAlongRoute — precision

    @Test
    fun `cumulative distance after N ticks matches N times speed times deltaT`() {
        // Long north-running segment — no snapping occurs during the test
        val wp0 = LatLng(0.0, 0.0)
        val wp1 = LatLng(10.0, 0.0) // ~1111 km
        val waypoints = listOf(wp0, wp1)

        val speedMs = 5.0
        val deltaTimeMs = 1000L
        val ticks = 100
        val expectedTotal = speedMs * (deltaTimeMs / 1000.0) * ticks // 500m

        var pos = wp0
        var idx = 1
        var cumulativeDistance = 0.0
        repeat(ticks) {
            val prev = pos
            val result = interpolator.interpolateAlongRoute(waypoints, pos, idx, speedMs, deltaTimeMs)
            assertFalse("Should not reach end during test", result.reachedEnd)
            cumulativeDistance += prev.distanceTo(result.position)
            pos = result.position
            idx = result.nextWaypointIndex
        }

        // Allow 1% tolerance for floating-point / Haversine approximation
        assertEquals(expectedTotal, cumulativeDistance, expectedTotal * 0.01)
    }
}
