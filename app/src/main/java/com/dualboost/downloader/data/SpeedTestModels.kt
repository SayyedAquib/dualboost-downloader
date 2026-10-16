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
        val currentSpeedMbps: Float,
        val overallProgress: Float, // 0.0f to 1.0f
        val wifiSpeedMbps: Float = 0f,
        val cellularSpeedMbps: Float = 0f
    ) : SpeedTestState()

    data class Completed(
        val wifiSpeedMbps: Float,
        val cellularSpeedMbps: Float,
        val dualBoostSpeedMbps: Float,
        val boostGainPercent: Int
    ) : SpeedTestState()

    data class Error(val message: String) : SpeedTestState()
}

