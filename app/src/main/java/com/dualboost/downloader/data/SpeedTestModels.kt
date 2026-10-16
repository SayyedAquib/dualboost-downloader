package com.dualboost.downloader.data

enum class SpeedTestPhase {
    IDLE,
    TESTING_WIFI,
    TESTING_CELLULAR,
    TESTING_DUALBOOST,
    COMPLETED,
    CANCELLED,
    FAILED
}

sealed class SpeedTestState {
    data object Idle : SpeedTestState()

    data class Running(
        val phase: SpeedTestPhase,
        val currentSpeedMBps: Float,
        val overallProgress: Float, // 0.0f to 1.0f
        val wifiSpeedMBps: Float = 0f,
        val cellularSpeedMBps: Float = 0f
    ) : SpeedTestState() {
        val currentSpeedMbps: Float get() = currentSpeedMBps
        val wifiSpeedMbps: Float get() = wifiSpeedMBps
        val cellularSpeedMbps: Float get() = cellularSpeedMBps
    }

    data class Completed(
        val wifiSpeedMBps: Float,
        val cellularSpeedMBps: Float,
        val dualBoostSpeedMBps: Float,
        val boostGainPercent: Int
    ) : SpeedTestState() {
        val wifiSpeedMbps: Float get() = wifiSpeedMBps
        val cellularSpeedMbps: Float get() = cellularSpeedMBps
        val dualBoostSpeedMbps: Float get() = dualBoostSpeedMBps
    }

    data class Error(val message: String) : SpeedTestState()
}

