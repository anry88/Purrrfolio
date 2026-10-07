package com.anry88.purrrfolio.game

import java.util.concurrent.TimeUnit

/** Spaces reminder attempts, including failures. Waiting happens before any player row is locked. */
class NotificationPacer(
    intervalMillis: Long,
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    private val intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis)
    private var nextAttemptAt: Long? = null

    init {
        require(intervalMillis > 0)
    }

    @Synchronized
    fun awaitTurn() {
        nextAttemptAt?.let { deadline ->
            while (true) {
                val remaining = deadline - nanoTime()
                if (remaining <= 0) break
                sleep((remaining + 999_999) / 1_000_000)
            }
        }
    }

    /** Start the quiet interval after the client returns, including failed/slow requests. */
    @Synchronized
    fun attemptFinished() {
        nextAttemptAt = nanoTime() + intervalNanos
    }
}
