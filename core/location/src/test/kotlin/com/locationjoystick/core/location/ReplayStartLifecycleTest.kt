package com.locationjoystick.core.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayStartLifecycleTest {
    @Test
    fun `begin supersedes and wakes the previous session`() {
        val l = ReplayStartLifecycle()
        val first = l.begin()
        first.paused.value = true
        val second = l.begin()
        assertFalse(first.paused.value)
        assertFalse(l.isCurrent(first.generation))
        assertTrue(l.isCurrent(second.generation))
    }

    @Test
    fun `abort clears current and unpauses`() {
        val l = ReplayStartLifecycle()
        val s = l.begin()
        s.paused.value = true
        l.abort()
        assertNull(l.current)
        assertFalse(s.paused.value)
        assertFalse(l.isCurrent(s.generation))
    }

    @Test
    fun `runWhenResumed waits for unpause then runs once`() =
        runTest {
            val l = ReplayStartLifecycle()
            val s = l.begin()
            s.paused.value = true
            var runs = 0
            launch { l.runWhenResumed(s) { runs++ } }
            advanceUntilIdle()
            assertEquals(0, runs)
            s.paused.value = false
            advanceUntilIdle()
            assertEquals(1, runs)
        }

    @Test
    fun `awaitResume throws when superseded while waiting`() =
        runTest {
            val l = ReplayStartLifecycle()
            val s = l.begin()
            s.paused.value = true
            var thrown: Throwable? = null
            launch {
                try {
                    l.awaitResume(s)
                } catch (e: CancellationException) {
                    thrown = e
                }
            }
            advanceUntilIdle()
            l.begin()
            advanceUntilIdle()
            assertTrue(thrown is CancellationException)
        }

    @Test
    fun `isStarting is true until the engine starts`() {
        val l = ReplayStartLifecycle()
        assertFalse(l.isStarting())
        val s = l.begin()
        assertTrue(l.isStarting(true))
        assertFalse(l.isStarting(false))
        s.engineStarted = true
        assertFalse(l.isStarting(true))
    }
}
