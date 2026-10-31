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

interface ThermalStatusProvider {
    val thermalStatus: StateFlow<Int>
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

