package com.locationjoystick.core.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * Owns the start generation counter, the per-start pause/engine-started state and the lock that
 * keeps them atomic with [ReplayOrchestrator]'s mode changes — extracted from the orchestrator,
 * mirroring [FollowerCatchUpCoordinator]: state ownership lives in one small class. Engine and
 * repository side effects stay in the orchestrator.
 */
internal class ReplayStartLifecycle {
    /** Pause survives planning and the approach walk without cancelling their saved progress. */
    class Session(
        val generation: Int,
    ) {
        val paused = MutableStateFlow(false)

        // Written under the lifecycle lock, read without it.
        @Volatile var engineStarted = false
    }

    private val generation = AtomicInteger(0)

    @PublishedApi internal val lock = Any()

    @Volatile var current: Session? = null
        private set

    inline fun <T> locked(block: () -> T): T = synchronized(lock, block)

    fun begin(): Session =
        locked {
            val previous = current
            Session(generation.incrementAndGet()).also {
                current = it
                previous?.paused?.value = false
            }
        }

    /** Invalidates the current session and wakes its paused callbacks so they discard themselves. */
    fun abort() {
        locked {
            generation.incrementAndGet()
            val previous = current
            current = null
            previous?.paused?.value = false
        }
    }

    fun clear() = locked { current = null }

    fun isCurrent(generation: Int): Boolean = generation == this.generation.get()

    /** True while a session exists whose engine has not started (planning / approach walk). */
    fun isStarting(engineJobActive: Boolean = true): Boolean = current?.let { !it.engineStarted && engineJobActive } ?: false

    suspend fun awaitResume(session: Session) {
        session.paused.first { !it }
        coroutineContext.ensureActive()
        if (!isCurrent(session.generation)) throw CancellationException("Replay replaced")
    }

    suspend fun runWhenResumed(
        session: Session,
        action: () -> Unit,
    ) {
        while (true) {
            awaitResume(session)
            val started =
                locked {
                    if (session !== current || session.paused.value) {
                        false
                    } else {
                        action()
                        true
                    }
                }
            if (started) return
        }
    }
}
