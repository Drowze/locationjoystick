package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RealLocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the bug where starting a new walk-to while an ephemeral replay
 * (built via "add next point") was active would not cancel the replay, causing the
 * previous route to continue instead of starting the new walk.
 *
 * Scenario: walkTo(target1) → addEphemeralWaypoint ×5 → walkTo(target2)
 * Expected: ephemeral replay cancelled, new walk to target2 starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapControllerWalkCancellationTest {
    private val walkProfile = SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.4)

    @Before
    fun setUp() {
        mockkObject(MockLocationIntentBuilder)
        every { MockLocationIntentBuilder.startEphemeralReplay(any(), any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.appendWaypoint(any(), any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.cancelRouteReplay(any()) } returns mockk(relaxed = true)
        every { MockLocationIntentBuilder.updatePosition(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(MockLocationIntentBuilder)
    }

    @Test
    fun `walkTo cancels active ephemeral replay and starts new walk`() =
        runTest(UnconfinedTestDispatcher()) {
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    // resolveRoute with followRoads=false returns a straight-line pair — mirror
                    // the real implementation so addWaypoint produces a valid waypoint list.
                    coEvery { it.resolveRoute(any(), any(), any(), any(), any()) } answers {
                        listOf(secondArg<LatLng>(), thirdArg<LatLng>())
                    }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val locationRepository = harness.locationRepository
            val ephemeralController = harness.ephemeralController
            val mapController = harness.mapController

            // Step 1: start walk-to (not via roads)
            val start = LatLng(48.8566, 2.3522)
            val target1 = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(start)
            mapController.walkTo(target1)

            assertEquals(target1, locationRepository.walkTarget.value)
            assertEquals(MockMode.WALK_TO, locationRepository.currentMode.value)

            // Step 2: tap "add next point" 5 times — transitions walk → ephemeral replay
            val nextPoints = (1..5).map { i -> LatLng(48.9000 + i * 0.01, 2.3522) }
            for (point in nextPoints) {
                mapController.addEphemeralWaypoint(point)
            }
            assertTrue(
                "Ephemeral waypoints should be populated after 5 add-next-point taps",
                ephemeralController.pendingWaypoints.value.isNotEmpty(),
            )
            assertTrue(
                "walkMode should be EphemeralReplay",
                mapController.sharedState.value.walkMode is WalkMode.EphemeralReplay,
            )

            // Step 3: pick a new destination and tap "Walk here"
            val target2 = LatLng(48.8700, 2.3000)
            mapController.walkTo(target2)

            // Step 4: ephemeral replay must be fully cancelled; new walk to target2 must be active
            assertTrue(
                "Ephemeral waypoints must be cleared when new walk-to starts",
                ephemeralController.pendingWaypoints.value.isEmpty(),
            )
            assertEquals(
                "Walk target must be the new destination",
                target2,
                locationRepository.walkTarget.value,
            )
            assertEquals(
                "Mode must be WALK_TO",
                MockMode.WALK_TO,
                locationRepository.currentMode.value,
            )

            val walkMode = mapController.sharedState.value.walkMode
            assertTrue(
                "walkMode must be the new Walking state after walkTo(target2), not left over " +
                    "from the cancelled ephemeral replay",
                walkMode is WalkMode.Walking && walkMode.target == target2,
            )
        }

    // Regression for issue #99: a teleport from a screen that bypasses MapController (Favorites,
    // Routes, Capture jump) only cancels via WalkCoordinator. A walk-via-roads whose routing
    // result lands afterwards must not start walking from the teleport spot toward the old target.
    @Test
    fun `teleport while walk-via-roads is routing drops the late route`() =
        runTest(UnconfinedTestDispatcher()) {
            val routeResult = CompletableDeferred<Result<List<LatLng>>>()
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers { routeResult.await() }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val start = LatLng(48.8566, 2.3522)
            val targetA = LatLng(48.9000, 2.3522)
            val teleportB = LatLng(48.8000, 2.3000)
            harness.locationRepository.setPositionInternal(start)

            harness.mapController.walkViaRoads(targetA)
            // What TeleportUseCase.execute(resetMovement = true) does before the jump.
            harness.walkCoordinator.cancel()
            harness.locationRepository.setPositionInternal(teleportB)
            routeResult.complete(Result.success(listOf(start, targetA)))

            assertNull("Late route must not revive the walk", harness.locationRepository.walkTarget.value)
            assertNotEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
            assertFalse(harness.mapController.sharedState.value.walkMode is WalkMode.Walking)
            assertEquals(teleportB, harness.locationRepository.currentPosition.value)
        }

    @Test
    fun `teleport while walk-via-roads is routing drops the straight-line fallback`() =
        runTest(UnconfinedTestDispatcher()) {
            val routeResult = CompletableDeferred<Result<List<LatLng>>>()
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers { routeResult.await() }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            harness.locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))

            harness.mapController.walkViaRoads(LatLng(48.9000, 2.3522))
            harness.walkCoordinator.cancel()
            routeResult.complete(Result.failure(IllegalStateException("routing down")))

            assertNull(harness.locationRepository.walkTarget.value)
            assertNotEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
            assertFalse(harness.mapController.sharedState.value.walkMode is WalkMode.Walking)
        }

    // Issue #96: moving the joystick cancels a walk still waiting on its road route, and stops
    // any route/roam session through the same path a teleport uses.
    @Test
    fun `joystick takeover drops a pending walk-via-roads and stops route and roam`() =
        runTest(UnconfinedTestDispatcher()) {
            val routeResult = CompletableDeferred<Result<List<LatLng>>>()
            val osrmClient =
                mockk<OsrmClient>(relaxed = true).also {
                    coEvery { it.getRoute(any(), any()) } coAnswers { routeResult.await() }
                }
            val harness = buildHarness(backgroundScope, osrmClient)
            val start = LatLng(48.8566, 2.3522)
            harness.locationRepository.setPositionInternal(start)

            harness.mapController.walkViaRoads(LatLng(48.9000, 2.3522))
            harness.mapController.stopAutomatedMovement()
            routeResult.complete(Result.success(listOf(start, LatLng(48.9000, 2.3522))))

            assertNull(harness.locationRepository.walkTarget.value)
            assertNotEquals(MockMode.WALK_TO, harness.locationRepository.currentMode.value)
            assertEquals(WalkMode.Idle, harness.mapController.sharedState.value.walkMode)
            assertEquals(start, harness.locationRepository.currentPosition.value)
            coVerify(exactly = 1) { harness.teleportUseCase.stopAutomatedMovement() }
        }

    private class Harness(
        val locationRepository: LocationRepository,
        val walkCoordinator: WalkCoordinator,
        val ephemeralController: EphemeralReplayController,
        val mapController: MapController,
        val teleportUseCase: TeleportUseCase,
    )

    private fun buildHarness(
        scope: CoroutineScope,
        osrmClient: OsrmClient,
    ): Harness {
        val locationRepository = LocationRepository()
        val settingsRepository =
            mockk<SettingsRepository>(relaxed = true) {
                every { getActiveSpeedProfile() } returns flowOf(walkProfile)
                every { getRoutesSortMode() } returns flowOf(SavedItemSortMode.NEWEST_FIRST)
                every { getFavoritesSortMode() } returns flowOf(SavedItemSortMode.NEWEST_FIRST)
                every { getSpeedUnit() } returns flowOf(SpeedUnit.KMH)
                every { getRecentSearches() } returns flowOf(emptyList())
                every { getRoamingDefaults() } returns flowOf(RoamingDefaults())
                every { getSettingsSnapshot() } returns emptyFlow()
                every { getRememberLastLocation() } returns flowOf(false)
            }
        val walkToEngine = WalkToEngine(settingsRepository, locationRepository)
        val walkCoordinator = WalkCoordinator(locationRepository, walkToEngine)
        val routingErrorReporter = RoutingErrorReporter(mockk<android.content.Context>(relaxed = true))
        val ephemeralController =
            EphemeralReplayController(
                locationRepository,
                settingsRepository,
                walkCoordinator,
                osrmClient,
                routingErrorReporter,
            )

        val context = mockk<Context>(relaxed = true)
        val isRoaming = MutableStateFlow(false)
        val isRoamingPaused = MutableStateFlow(false)
        val roamingRepository =
            mockk<RoamingRepository>(relaxed = true) {
                every { this@mockk.isRoaming } returns isRoaming
                every { this@mockk.isRoamingPaused } returns isRoamingPaused
            }
        val routeRepository = mockk<RouteRepository>(relaxed = true) { every { getRoutes() } returns emptyFlow() }
        val favoriteRepository =
            mockk<FavoriteRepository>(relaxed = true) { every { getFavorites() } returns flowOf(emptyList()) }
        val teleportUseCase =
            mockk<TeleportUseCase>(relaxed = true) { every { cooldownsFor(any()) } returns emptyFlow() }
        val startRouteReplayUseCase = mockk<StartRouteReplayUseCase>(relaxed = true)

        val mapController =
            MapController(
                context = context,
                locationRepository = locationRepository,
                routeRepository = routeRepository,
                favoriteRepository = favoriteRepository,
                settingsRepository = settingsRepository,
                roamingRepository = roamingRepository,
                walkCoordinator = walkCoordinator,
                teleportUseCase = teleportUseCase,
                realLocationRepository =
                    mockk<RealLocationRepository> {
                        every { lastKnownRealPosition() } returns null
                        every { hasFinePermission() } returns false
                    },
                startRouteReplayUseCase = startRouteReplayUseCase,
                ephemeralReplayController = ephemeralController,
                osrmClient = osrmClient,
                routingErrorReporter = routingErrorReporter,
                appScope = scope,
            )
        return Harness(locationRepository, walkCoordinator, ephemeralController, mapController, teleportUseCase)
    }
}
