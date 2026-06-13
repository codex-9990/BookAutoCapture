package com.codex9990.bookautocapture.capture

enum class QualityLevel {
    GOOD,
    CHECK,
    BLOCKED
}

enum class QualitySignal {
    GOOD,
    CHECK,
    BLOCKED
}

enum class QualityIssue {
    TOO_DARK,
    LOW_LIGHT,
    TOO_BLURRY,
    LOW_SHARPNESS,
    MOVING
}

data class QualityAssessment(
    val level: QualityLevel,
    val brightness: QualitySignal,
    val sharpness: QualitySignal,
    val stability: QualitySignal,
    val issues: List<QualityIssue>
)

object CaptureQualityThresholds {
    const val TOO_DARK_LUMA = 45.0
    const val LOW_LIGHT_LUMA = 65.0
    const val TOO_BLURRY_EDGE = 2.0
    const val LOW_SHARPNESS_EDGE = 4.0
}

object QualityAssessor {
    fun assess(
        metrics: FrameMetrics,
        settings: CaptureSettings
    ): QualityAssessment {
        val issues = mutableListOf<QualityIssue>()
        val brightness = assessBrightness(metrics, settings, issues)
        val sharpness = assessSharpness(metrics, settings, issues)
        val stability = assessStability(metrics, settings, issues)
        val level = when {
            listOf(brightness, sharpness, stability).any { it == QualitySignal.BLOCKED } -> {
                QualityLevel.BLOCKED
            }
            issues.isNotEmpty() -> QualityLevel.CHECK
            else -> QualityLevel.GOOD
        }

        return QualityAssessment(
            level = level,
            brightness = brightness,
            sharpness = sharpness,
            stability = stability,
            issues = issues
        )
    }

    private fun assessBrightness(
        metrics: FrameMetrics,
        settings: CaptureSettings,
        issues: MutableList<QualityIssue>
    ): QualitySignal {
        if (!settings.darknessCheckEnabled) return QualitySignal.GOOD

        return when {
            metrics.averageLuma < CaptureQualityThresholds.TOO_DARK_LUMA -> {
                issues += QualityIssue.TOO_DARK
                QualitySignal.BLOCKED
            }
            metrics.averageLuma < CaptureQualityThresholds.LOW_LIGHT_LUMA -> {
                issues += QualityIssue.LOW_LIGHT
                QualitySignal.CHECK
            }
            else -> QualitySignal.GOOD
        }
    }

    private fun assessSharpness(
        metrics: FrameMetrics,
        settings: CaptureSettings,
        issues: MutableList<QualityIssue>
    ): QualitySignal {
        if (!settings.blurCheckEnabled) return QualitySignal.GOOD

        return when {
            metrics.edgeScore < CaptureQualityThresholds.TOO_BLURRY_EDGE -> {
                issues += QualityIssue.TOO_BLURRY
                QualitySignal.BLOCKED
            }
            metrics.edgeScore < CaptureQualityThresholds.LOW_SHARPNESS_EDGE -> {
                issues += QualityIssue.LOW_SHARPNESS
                QualitySignal.CHECK
            }
            else -> QualitySignal.GOOD
        }
    }

    private fun assessStability(
        metrics: FrameMetrics,
        settings: CaptureSettings,
        issues: MutableList<QualityIssue>
    ): QualitySignal {
        val thresholds = settings.sensitivity.motionThresholds()
        return if (metrics.difference >= thresholds.stableThreshold) {
            issues += QualityIssue.MOVING
            QualitySignal.CHECK
        } else {
            QualitySignal.GOOD
        }
    }
}
