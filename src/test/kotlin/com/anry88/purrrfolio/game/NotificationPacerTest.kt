package com.anry88.purrrfolio.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NotificationPacerTest {
    @Test
    fun `fast sends including consecutive batches cannot burst`() {
        var now = 0L
        val waits = mutableListOf<Long>()
        val pacer = NotificationPacer(250, { now }, { millis -> waits.add(millis); now += millis * 1_000_000 })
        pacer.awaitTurn()
        assertThat(waits).isEmpty()
        repeat(3) { pacer.attemptFinished(); pacer.awaitTurn() }
        assertThat(waits).containsExactly(250, 250, 250)
    }

    @Test
    fun `slow requests are followed by a full quiet interval before the next attempt`() {
        var now = 0L
        val waits = mutableListOf<Long>()
        val pacer = NotificationPacer(250, { now }, { millis -> waits.add(millis); now += millis * 1_000_000 })
        pacer.awaitTurn()
        now += 100_000_000
        pacer.attemptFinished()
        pacer.awaitTurn()
        now += 500_000_000
        pacer.attemptFinished()
        pacer.awaitTurn()
        assertThat(waits).containsExactly(250, 250)
    }

    @Test
    fun `early wakeups cannot bypass the minimum interval`() {
        var now = 0L
        var earlyWakeup = true
        val waits = mutableListOf<Long>()
        val pacer = NotificationPacer(250, { now }, { millis ->
            waits.add(millis)
            now += (if (earlyWakeup) 100 else millis) * 1_000_000
            earlyWakeup = false
        })
        pacer.awaitTurn()
        pacer.attemptFinished()
        pacer.awaitTurn()
        assertThat(waits).containsExactly(250, 150)
    }

    @Test
    fun `slow database work before a request does not permit the next request to burst`() {
        var now = 0L
        val waits = mutableListOf<Long>()
        val pacer = NotificationPacer(250, { now }, { millis -> waits.add(millis); now += millis * 1_000_000 })
        pacer.awaitTurn()
        now += 500_000_000
        pacer.attemptFinished()
        pacer.awaitTurn()
        assertThat(waits).containsExactly(250)
    }

    @Test
    fun `interrupted wait does not reserve another send`() {
        var now = 0L
        var interrupted = true
        val waits = mutableListOf<Long>()
        val pacer = NotificationPacer(250, { now }, { millis ->
            if (interrupted) throw InterruptedException()
            waits.add(millis)
            now += millis * 1_000_000
        })
        pacer.awaitTurn()
        pacer.attemptFinished()
        assertThrows<InterruptedException> { pacer.awaitTurn() }
        interrupted = false
        pacer.awaitTurn()
        assertThat(waits).containsExactly(250)
    }
}
