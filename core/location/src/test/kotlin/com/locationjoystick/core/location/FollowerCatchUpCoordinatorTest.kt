package com.locationjoystick.core.location

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [FollowerCatchUpCoordinator]:
 * - stateful compareAndSet gating in [FollowerCatchUpCoordinator.handleLeaderActiveUpdate]
 *   (the pure decision table itself is covered by [LocationLoopActionTest]'s
 *   `computeFollowerActiveAction` cases; this test covers the gate that gives each transition
 *   exactly one BOOTSTRAP/PAUSE per active/inactive streak)
 * - teleport sequence tracking and snap-to-leader decision logic
 */
class FollowerCatchUpCoordinatorTest {
    @Test
    fun `bootstraps once then no-ops while the leader stays active`() {
        val coordinator = FollowerCatchUpCoordinator()

        val first = coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)
        val second = coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.RUNNING)

        assertEquals(FollowerActiveAction.BOOTSTRAP, first)
        assertEquals(FollowerActiveAction.NO_OP, second)
    }

    @Test
    fun `pauses once then no-ops while the leader stays inactive`() {
        val coordinator = FollowerCatchUpCoordinator()
        coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        val first = coordinator.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING)
        val second = coordinator.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.IDLE)

        assertEquals(FollowerActiveAction.PAUSE, first)
        assertEquals(FollowerActiveAction.NO_OP, second)
    }

    @Test
    fun `bootstraps again after a pause-resume cycle`() {
        val coordinator = FollowerCatchUpCoordinator()
        coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)
        coordinator.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING)

        val action = coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        assertEquals(FollowerActiveAction.BOOTSTRAP, action)
    }

    @Test
    fun `clear allows an immediate re-bootstrap`() {
        val coordinator = FollowerCatchUpCoordinator()
        coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        coordinator.clear()
        val action = coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        assertEquals(FollowerActiveAction.BOOTSTRAP, action)
    }

    @Test
    fun `clearTarget drops the target but keeps the bootstrap gate`() {
        val coordinator = FollowerCatchUpCoordinator()
        coordinator.setTarget(LatLng(1.0, 2.0), leaderBearing = 0f)
        coordinator.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)

        coordinator.clearTarget()

        assertNull(coordinator.currentTarget())
        assertEquals(
            FollowerActiveAction.PAUSE,
            coordinator.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING),
        )
    }

    @Test
    fun `follower present before and follower joining after leader start both re-bootstrap at the new position`() {
        val p1 = LatLng(1.0, 1.0)
        val p2 = LatLng(5.0, 5.0)
        val x = FollowerCatchUpCoordinator()
        val y = FollowerCatchUpCoordinator()
        // X saw the leader's first active update; Y joined afterwards and its first update is also at P1.
        listOf(x, y).forEach {
            it.setTarget(p1, 0f)
            assertEquals(FollowerActiveAction.BOOTSTRAP, it.handleLeaderActiveUpdate(true, MockLocationState.IDLE))
        }
        // Leader stops: inactive update pauses both and drops the stale target.
        listOf(x, y).forEach {
            assertEquals(FollowerActiveAction.PAUSE, it.handleLeaderActiveUpdate(false, MockLocationState.RUNNING))
            it.clearTarget()
            assertNull(it.currentTarget())
        }
        // Leader moves and starts again at P2.
        listOf(x, y).forEach {
            it.setTarget(p2, 0f)
            assertEquals(FollowerActiveAction.BOOTSTRAP, it.handleLeaderActiveUpdate(true, MockLocationState.IDLE))
            assertEquals(p2, it.currentTarget())
        }
    }

    @Test
    fun `first observed seq is only a baseline`() {
        val c = FollowerCatchUpCoordinator()
        assertFalse(c.observeTeleportSeq(5L))
        assertFalse(c.observeTeleportSeq(5L))
    }

    @Test
    fun `changed seq in either direction is a teleport`() {
        val c = FollowerCatchUpCoordinator()
        c.observeTeleportSeq(1L)
        assertTrue(c.observeTeleportSeq(2L))
        assertTrue(c.observeTeleportSeq(0L))
    }

    @Test
    fun `clear re-baselines the seq`() {
        val c = FollowerCatchUpCoordinator()
        c.observeTeleportSeq(1L)
        c.clear()
        assertFalse(c.observeTeleportSeq(9L))
    }

    @Test
    fun `pausedByLeader is consumed once after a pause`() {
        val c = FollowerCatchUpCoordinator()
        assertFalse(c.consumePausedByLeader())
        c.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)
        c.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING)
        assertTrue(c.consumePausedByLeader())
        assertFalse(c.consumePausedByLeader())
    }

    @Test
    fun `clear resets pausedByLeader`() {
        val c = FollowerCatchUpCoordinator()
        c.handleLeaderActiveUpdate(leaderActive = true, currentState = MockLocationState.IDLE)
        c.handleLeaderActiveUpdate(leaderActive = false, currentState = MockLocationState.RUNNING)
        c.clear()
        assertFalse(c.consumePausedByLeader())
    }

    @Test
    fun `snap only when leader teleported and following and teleport not hidden`() {
        assertTrue(shouldSnapToLeader(true, true, false))
        assertFalse(shouldSnapToLeader(false, true, false))
        assertFalse(shouldSnapToLeader(true, false, false))
        assertFalse(shouldSnapToLeader(true, true, true))
    }
}
