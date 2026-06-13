package com.codex9990.bookautocapture.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityAssessorTest {
    @Test
    fun goodFrameIsReadyToCapture() {
        val assessment = QualityAssessor.assess(
            metrics = frame(difference = 1.0, averageLuma = 120.0, edgeScore = 12.0),
            settings = settings()
        )

        assertEquals(QualityLevel.GOOD, assessment.level)
        assertEquals(QualitySignal.GOOD, assessment.brightness)
        assertEquals(QualitySignal.GOOD, assessment.sharpness)
        assertEquals(QualitySignal.GOOD, assessment.stability)
        assertTrue(assessment.issues.isEmpty())
    }

    @Test
    fun lowLightIsCaution() {
        val assessment = QualityAssessor.assess(
            metrics = frame(difference = 1.0, averageLuma = 55.0, edgeScore = 12.0),
            settings = settings()
        )

        assertEquals(QualityLevel.CHECK, assessment.level)
        assertEquals(QualitySignal.CHECK, assessment.brightness)
        assertTrue(assessment.issues.contains(QualityIssue.LOW_LIGHT))
    }

    @Test
    fun tooDarkBlocksCapture() {
        val assessment = QualityAssessor.assess(
            metrics = frame(difference = 1.0, averageLuma = 30.0, edgeScore = 12.0),
            settings = settings()
        )

        assertEquals(QualityLevel.BLOCKED, assessment.level)
        assertEquals(QualitySignal.BLOCKED, assessment.brightness)
        assertTrue(assessment.issues.contains(QualityIssue.TOO_DARK))
    }

    @Test
    fun lowSharpnessIsCaution() {
        val assessment = QualityAssessor.assess(
            metrics = frame(difference = 1.0, averageLuma = 120.0, edgeScore = 3.0),
            settings = settings()
        )

        assertEquals(QualityLevel.CHECK, assessment.level)
        assertEquals(QualitySignal.CHECK, assessment.sharpness)
        assertTrue(assessment.issues.contains(QualityIssue.LOW_SHARPNESS))
    }

    @Test
    fun tooBlurryBlocksCapture() {
        val assessment = QualityAssessor.assess(
            metrics = frame(difference = 1.0, averageLuma = 120.0, edgeScore = 1.0),
            settings = settings()
        )

        assertEquals(QualityLevel.BLOCKED, assessment.level)
        assertEquals(QualitySignal.BLOCKED, assessment.sharpness)
        assertTrue(assessment.issues.contains(QualityIssue.TOO_BLURRY))
    }

    @Test
    fun movingFrameIsCautionUntilStable() {
        val assessment = QualityAssessor.assess(
            metrics = frame(difference = 5.0, averageLuma = 120.0, edgeScore = 12.0),
            settings = settings()
        )

        assertEquals(QualityLevel.CHECK, assessment.level)
        assertEquals(QualitySignal.CHECK, assessment.stability)
        assertTrue(assessment.issues.contains(QualityIssue.MOVING))
    }

    @Test
    fun disabledChecksDoNotReportDarknessOrBlurIssues() {
        val assessment = QualityAssessor.assess(
            metrics = frame(difference = 1.0, averageLuma = 30.0, edgeScore = 1.0),
            settings = settings(
                blurCheckEnabled = false,
                darknessCheckEnabled = false
            )
        )

        assertEquals(QualityLevel.GOOD, assessment.level)
        assertFalse(assessment.issues.contains(QualityIssue.TOO_DARK))
        assertFalse(assessment.issues.contains(QualityIssue.TOO_BLURRY))
    }

    private fun settings(
        blurCheckEnabled: Boolean = true,
        darknessCheckEnabled: Boolean = true
    ): CaptureSettings {
        return CaptureSettings(
            stableDurationMs = 1_000L,
            minCaptureIntervalMs = 2_000L,
            sensitivity = Sensitivity.MEDIUM,
            blurCheckEnabled = blurCheckEnabled,
            darknessCheckEnabled = darknessCheckEnabled
        )
    }

    private fun frame(
        difference: Double,
        averageLuma: Double,
        edgeScore: Double
    ): FrameMetrics {
        return FrameMetrics(
            timestampMs = 0L,
            difference = difference,
            averageLuma = averageLuma,
            edgeScore = edgeScore
        )
    }
}
