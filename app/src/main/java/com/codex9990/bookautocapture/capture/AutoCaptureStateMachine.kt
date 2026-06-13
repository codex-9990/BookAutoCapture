package com.codex9990.bookautocapture.capture

enum class CaptureState {
    IDLE,
    WAITING_FOR_PAGE_TURN,
    PAGE_MOVING,
    WAITING_FOR_STABLE,
    CAPTURING,
    COOLDOWN
}

enum class Sensitivity {
    LOW,
    MEDIUM,
    HIGH
}

data class MotionThresholds(
    val moveThreshold: Double,
    val stableThreshold: Double
)

fun Sensitivity.motionThresholds(): MotionThresholds {
    return when (this) {
        Sensitivity.LOW -> MotionThresholds(moveThreshold = 18.0, stableThreshold = 4.5)
        Sensitivity.MEDIUM -> MotionThresholds(moveThreshold = 12.0, stableThreshold = 3.5)
        Sensitivity.HIGH -> MotionThresholds(moveThreshold = 8.0, stableThreshold = 3.0)
    }
}

data class CaptureSettings(
    val stableDurationMs: Long = 1_000L,
    val minCaptureIntervalMs: Long = 2_000L,
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val blurCheckEnabled: Boolean = true,
    val darknessCheckEnabled: Boolean = true
)

data class FrameMetrics(
    val timestampMs: Long,
    val difference: Double,
    val averageLuma: Double,
    val edgeScore: Double
)

enum class BlockReason {
    NONE,
    TOO_DARK,
    TOO_BLURRY,
    COOLDOWN
}

data class CaptureDecision(
    val state: CaptureState,
    val shouldCapture: Boolean = false,
    val blockReason: BlockReason = BlockReason.NONE
)

class AutoCaptureStateMachine(
    var settings: CaptureSettings = CaptureSettings()
) {
    var state: CaptureState = CaptureState.IDLE
        private set

    private var stableSinceMs: Long? = null
    private var lastCaptureAtMs: Long = Long.MIN_VALUE / 2

    fun start(nowMs: Long) {
        state = CaptureState.WAITING_FOR_PAGE_TURN
        stableSinceMs = null
        lastCaptureAtMs = nowMs - settings.minCaptureIntervalMs
    }

    fun stop() {
        state = CaptureState.IDLE
        stableSinceMs = null
    }

    fun onFrame(metrics: FrameMetrics): CaptureDecision {
        val thresholds = settings.sensitivity.motionThresholds()

        return when (state) {
            CaptureState.IDLE -> CaptureDecision(CaptureState.IDLE)
            CaptureState.WAITING_FOR_PAGE_TURN -> {
                if (metrics.difference >= thresholds.moveThreshold) {
                    state = CaptureState.PAGE_MOVING
                }
                CaptureDecision(state)
            }
            CaptureState.PAGE_MOVING -> {
                if (metrics.difference < thresholds.stableThreshold) {
                    state = CaptureState.WAITING_FOR_STABLE
                    stableSinceMs = metrics.timestampMs
                }
                CaptureDecision(state)
            }
            CaptureState.WAITING_FOR_STABLE -> handleStableCandidate(metrics, thresholds)
            CaptureState.CAPTURING -> CaptureDecision(CaptureState.CAPTURING)
            CaptureState.COOLDOWN -> handleCooldown(metrics)
        }
    }

    fun markCaptureCompleted(nowMs: Long) {
        lastCaptureAtMs = nowMs
        stableSinceMs = null
        state = CaptureState.COOLDOWN
    }

    fun markCaptureFailed() {
        stableSinceMs = null
        state = CaptureState.WAITING_FOR_PAGE_TURN
    }

    private fun handleStableCandidate(
        metrics: FrameMetrics,
        thresholds: MotionThresholds
    ): CaptureDecision {
        if (metrics.difference >= thresholds.moveThreshold) {
            state = CaptureState.PAGE_MOVING
            stableSinceMs = null
            return CaptureDecision(state)
        }

        if (metrics.difference >= thresholds.stableThreshold) {
            stableSinceMs = null
            return CaptureDecision(state)
        }

        val startedAt = stableSinceMs ?: metrics.timestampMs.also { stableSinceMs = it }
        if (metrics.timestampMs - startedAt < settings.stableDurationMs) {
            return CaptureDecision(state)
        }

        val blockReason = captureBlockReason(metrics)
        if (blockReason != BlockReason.NONE) {
            return CaptureDecision(state = state, blockReason = blockReason)
        }

        state = CaptureState.CAPTURING
        stableSinceMs = null
        return CaptureDecision(state = state, shouldCapture = true)
    }

    private fun handleCooldown(metrics: FrameMetrics): CaptureDecision {
        if (metrics.timestampMs - lastCaptureAtMs < settings.minCaptureIntervalMs) {
            return CaptureDecision(CaptureState.COOLDOWN, blockReason = BlockReason.COOLDOWN)
        }

        state = CaptureState.WAITING_FOR_PAGE_TURN
        return onFrame(metrics)
    }

    private fun captureBlockReason(metrics: FrameMetrics): BlockReason {
        if (settings.darknessCheckEnabled && metrics.averageLuma < CaptureQualityThresholds.TOO_DARK_LUMA) {
            return BlockReason.TOO_DARK
        }
        if (settings.blurCheckEnabled && metrics.edgeScore < CaptureQualityThresholds.TOO_BLURRY_EDGE) {
            return BlockReason.TOO_BLURRY
        }
        return BlockReason.NONE
    }
}
