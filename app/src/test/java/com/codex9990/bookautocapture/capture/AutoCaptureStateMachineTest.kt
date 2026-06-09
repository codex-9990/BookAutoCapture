package com.codex9990.bookautocapture.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureStateMachineTest {
    @Test
    fun pageTurnThenStableDurationRequestsCapture() {
        val machine = AutoCaptureStateMachine(testSettings())

        machine.start(0L)
        machine.onFrame(frame(0L, difference = 20.0))
        machine.onFrame(frame(100L, difference = 2.0))

        val decision = machine.onFrame(frame(1_100L, difference = 2.0))

        assertTrue(decision.shouldCapture)
        assertEquals(CaptureState.CAPTURING, decision.state)
    }

    @Test
    fun stableDurationMustBeSatisfiedBeforeCapture() {
        val machine = AutoCaptureStateMachine(testSettings())

        machine.start(0L)
        machine.onFrame(frame(0L, difference = 20.0))
        machine.onFrame(frame(100L, difference = 2.0))

        val decision = machine.onFrame(frame(1_099L, difference = 2.0))

        assertFalse(decision.shouldCapture)
        assertEquals(CaptureState.WAITING_FOR_STABLE, decision.state)
    }

    @Test
    fun cooldownBlocksCapture() {
        val machine = AutoCaptureStateMachine(testSettings())

        machine.start(0L)
        machine.onFrame(frame(0L, difference = 20.0))
        machine.onFrame(frame(100L, difference = 2.0))
        val captureDecision = machine.onFrame(frame(1_100L, difference = 2.0))
        assertTrue(captureDecision.shouldCapture)

        machine.markCaptureCompleted(1_100L)
        val cooldownDecision = machine.onFrame(frame(1_500L, difference = 20.0))

        assertFalse(cooldownDecision.shouldCapture)
        assertEquals(CaptureState.COOLDOWN, cooldownDecision.state)
        assertEquals(BlockReason.COOLDOWN, cooldownDecision.blockReason)
    }

    @Test
    fun darknessBlocksCaptureWhenEnabled() {
        val machine = AutoCaptureStateMachine(testSettings())

        machine.start(0L)
        machine.onFrame(frame(0L, difference = 20.0))
        machine.onFrame(frame(100L, difference = 2.0, averageLuma = 25.0))

        val decision = machine.onFrame(frame(1_100L, difference = 2.0, averageLuma = 25.0))

        assertFalse(decision.shouldCapture)
        assertEquals(BlockReason.TOO_DARK, decision.blockReason)
    }

    @Test
    fun blurBlocksCaptureWhenEnabled() {
        val machine = AutoCaptureStateMachine(testSettings())

        machine.start(0L)
        machine.onFrame(frame(0L, difference = 20.0))
        machine.onFrame(frame(100L, difference = 2.0, edgeScore = 0.5))

        val decision = machine.onFrame(frame(1_100L, difference = 2.0, edgeScore = 0.5))

        assertFalse(decision.shouldCapture)
        assertEquals(BlockReason.TOO_BLURRY, decision.blockReason)
    }

    private fun testSettings(): CaptureSettings {
        return CaptureSettings(
            stableDurationMs = 1_000L,
            minCaptureIntervalMs = 2_000L,
            sensitivity = Sensitivity.MEDIUM,
            blurCheckEnabled = true,
            darknessCheckEnabled = true
        )
    }

    private fun frame(
        timestampMs: Long,
        difference: Double,
        averageLuma: Double = 120.0,
        edgeScore: Double = 12.0
    ): FrameMetrics {
        return FrameMetrics(
            timestampMs = timestampMs,
            difference = difference,
            averageLuma = averageLuma,
            edgeScore = edgeScore
        )
    }
}

