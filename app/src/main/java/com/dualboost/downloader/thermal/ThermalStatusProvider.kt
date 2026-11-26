package com.dualboost.downloader.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThermalStatus {
    const val NONE = 0
    const val LIGHT = 1
    const val MODERATE = 2
    const val SEVERE = 3
    const val CRITICAL = 4
    const val EMERGENCY = 5
    const val SHUTDOWN = 6
}

/**
 * Explicit typed thermal severity states matching Android [PowerManager] thermal statuses.
 */
enum class ThermalLevel(
    val code: Int,
    val displayName: String
) {
    NONE(ThermalStatus.NONE, "Normal"),
    LIGHT(ThermalStatus.LIGHT, "Light Warmth"),
    MODERATE(ThermalStatus.MODERATE, "Moderate Heat"),
    SEVERE(ThermalStatus.SEVERE, "Severe Heat"),
    CRITICAL(ThermalStatus.CRITICAL, "Critical Heat"),
    EMERGENCY(ThermalStatus.EMERGENCY, "Emergency Heat"),
    SHUTDOWN(ThermalStatus.SHUTDOWN, "Thermal Shutdown");

    val isCoolForAutoScale: Boolean
        get() = this == NONE || this == LIGHT

    val isSevereOrHigher: Boolean
        get() = code >= ThermalStatus.SEVERE

    companion object {
        fun fromCode(code: Int): ThermalLevel {
            return entries.firstOrNull { it.code == code }
                ?: if (code > ThermalStatus.SHUTDOWN) SHUTDOWN else NONE
        }
    }
}

/**
 * Explicit thermal throttling policy governing worker scheduling across Wi-Fi and Cellular radios.
 */
enum class ThermalThrottlePolicy(val description: String) {
    UNRESTRICTED("Full dual-channel concurrency and auto-scale enabled"),
    CAP_CELLULAR_SINGLE("Moderate heat: Cellular throttled to 1 stream"),
    OFFLOAD_CELLULAR_TO_WIFI("Severe heat: Cellular paused and offloaded 100% to Wi-Fi"),
    EMERGENCY_SINGLE_CELLULAR("Severe heat without Wi-Fi: Cellular paced to 1 stream");

    fun allowsCellularWorker(workerIndex: Int): Boolean {
        return when (this) {
            UNRESTRICTED -> true
            CAP_CELLULAR_SINGLE, EMERGENCY_SINGLE_CELLULAR -> workerIndex == 0
            OFFLOAD_CELLULAR_TO_WIFI -> false
        }
    }

    val allowsAutoScale: Boolean
        get() = this == UNRESTRICTED

    companion object {
        fun resolve(level: ThermalLevel, isWifiAvailable: Boolean): ThermalThrottlePolicy {
            return when {
                level.isSevereOrHigher -> {
                    if (isWifiAvailable) OFFLOAD_CELLULAR_TO_WIFI else EMERGENCY_SINGLE_CELLULAR
                }
                level == ThermalLevel.MODERATE -> CAP_CELLULAR_SINGLE
                else -> UNRESTRICTED
            }
        }
    }
}

interface ThermalStatusProvider {
    val thermalStatus: StateFlow<Int>
    val currentLevel: ThermalLevel
        get() = ThermalLevel.fromCode(thermalStatus.value)
    fun startListening() {}
    fun stopListening() {}
}

class SystemThermalStatusProvider(private val context: Context) : ThermalStatusProvider {
    private val _thermalStatus = MutableStateFlow(ThermalStatus.NONE)
    override val thermalStatus: StateFlow<Int> = _thermalStatus.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    override fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                _thermalStatus.value = powerManager.currentThermalStatus
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    _thermalStatus.value = status
                }
                thermalListener = listener
                powerManager.addThermalStatusListener(context.mainExecutor, listener)
            } catch (_: Exception) {}
        }
    }

    override fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { listener ->
                try {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    powerManager?.removeThermalStatusListener(listener)
                } catch (_: Exception) {}
                thermalListener = null
            }
        }
    }
}
