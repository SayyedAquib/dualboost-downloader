package com.dualboost.downloader.engine

import com.dualboost.downloader.data.NetworkInterfaceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Explicit state model for the DualBoost network interface circuit breaker.
 */
enum class CircuitBreakerState {
    /** Interface is healthy, errors are below the trip threshold. */
    HEALTHY,

    /** Interface has tripped due to consecutive failures, traffic diverts to peer interface. */
    TRIPPED,

    /** Cooldown elapsed after trip; allows a single trial request to verify if connection recovered. */
    HALF_OPEN,

    /** Both interfaces have tripped; coordinated reconnection & exponential backoff is active. */
    RECOVERING,

    /** Retries exhausted (>3 recovery cycles or chunk retries exceeded); escalated to auto-pause. */
    EXHAUSTED
}

/**
 * Explicit composite aggregate state across both physical interfaces.
 * Eliminates confusion between per-interface health and global failover/recovery modes.
 */
sealed class CircuitBreakerAggregateState {
    object BothHealthy : CircuitBreakerAggregateState()

    data class FailoverActive(
        val degradedInterface: NetworkInterfaceType,
        val degradedState: CircuitBreakerState,
        val activeInterface: NetworkInterfaceType
    ) : CircuitBreakerAggregateState()

    data class BothTripped(
        val recoveryCycle: Int,
        val maxRecoveryCycles: Int
    ) : CircuitBreakerAggregateState()

    object Exhausted : CircuitBreakerAggregateState()
}

/**
 * Observable snapshot of per-interface and composite circuit-breaker status for UI, logging, and AI diagnostics.
 */
data class CircuitBreakerSnapshot(
    val wifiState: CircuitBreakerState = CircuitBreakerState.HEALTHY,
    val cellularState: CircuitBreakerState = CircuitBreakerState.HEALTHY,
    val aggregateState: CircuitBreakerAggregateState = CircuitBreakerAggregateState.BothHealthy,
    val recoveryCycle: Int = 0
)

/**
 * Manages per-interface failure tracking, failover diversion, and coordinated recovery
 * without loose integer counters.
 */
class DualInterfaceCircuitBreaker(
    val failureThreshold: Int = 2,
    val maxRecoveryCycles: Int = 3,
    val cooldownBaseMs: Long = 5000L,
    val maxCooldownMs: Long = 30000L
) {
    private val wifiFailures = AtomicInteger(0)
    private val cellularFailures = AtomicInteger(0)
    private val chunkFailures = ConcurrentHashMap<Int, AtomicInteger>()
    private val recoveryCycles = AtomicInteger(0)
    private val isExhausted = AtomicBoolean(false)

    private val lastTripTimeWifi = AtomicLong(0L)
    private val lastTripTimeCellular = AtomicLong(0L)
    private val tripCountWifi = AtomicInteger(0)
    private val tripCountCellular = AtomicInteger(0)

    private val _snapshot = MutableStateFlow(CircuitBreakerSnapshot())
    val snapshot: StateFlow<CircuitBreakerSnapshot> = _snapshot.asStateFlow()

    fun isExhausted(): Boolean = isExhausted.get()

    fun recordSuccess(interfaceType: NetworkInterfaceType) {
        when (interfaceType) {
            NetworkInterfaceType.WIFI -> {
                wifiFailures.set(0)
                tripCountWifi.set(0)
            }
            NetworkInterfaceType.CELLULAR -> {
                cellularFailures.set(0)
                tripCountCellular.set(0)
            }
            NetworkInterfaceType.DEFAULT -> {
                wifiFailures.set(0)
                cellularFailures.set(0)
                tripCountWifi.set(0)
                tripCountCellular.set(0)
            }
        }
        publishSnapshot()
    }

    fun recordFailure(
        interfaceType: NetworkInterfaceType,
        currentTimeMs: Long = System.currentTimeMillis()
    ): CircuitBreakerState {
        val failures = when (interfaceType) {
            NetworkInterfaceType.WIFI -> {
                val f = wifiFailures.incrementAndGet()
                if (f >= failureThreshold) {
                    lastTripTimeWifi.set(currentTimeMs)
                    tripCountWifi.incrementAndGet()
                }
                f
            }
            NetworkInterfaceType.CELLULAR -> {
                val f = cellularFailures.incrementAndGet()
                if (f >= failureThreshold) {
                    lastTripTimeCellular.set(currentTimeMs)
                    tripCountCellular.incrementAndGet()
                }
                f
            }
            NetworkInterfaceType.DEFAULT -> {
                val f = wifiFailures.incrementAndGet()
                if (f >= failureThreshold) {
                    lastTripTimeWifi.set(currentTimeMs)
                    tripCountWifi.incrementAndGet()
                }
                f
            }
        }
        publishSnapshot(currentTimeMs)
        return if (failures >= failureThreshold) CircuitBreakerState.TRIPPED else CircuitBreakerState.HEALTHY
    }

    fun calculateCooldownMs(interfaceType: NetworkInterfaceType): Long {
        val count = when (interfaceType) {
            NetworkInterfaceType.WIFI -> tripCountWifi.get()
            NetworkInterfaceType.CELLULAR -> tripCountCellular.get()
            NetworkInterfaceType.DEFAULT -> tripCountWifi.get()
        }.coerceAtLeast(1)
        val factor = 1L shl (count - 1).coerceAtMost(5)
        return (cooldownBaseMs * factor).coerceAtMost(maxCooldownMs)
    }

    fun recordChunkFailure(chunkIndex: Int, maxRetries: Int): Boolean {
        val attempts = chunkFailures.computeIfAbsent(chunkIndex) { AtomicInteger(0) }.incrementAndGet()
        if (attempts >= maxRetries) {
            isExhausted.set(true)
            publishSnapshot()
            return true
        }
        return false
    }

    /**
     * Evaluates the isolated state of a single physical interface without mixing global RECOVERING logic.
     * Useful when checking whether a peer interface is healthy or in HALF_OPEN trial state.
     */
    fun evaluateInterfaceOnly(
        interfaceType: NetworkInterfaceType,
        currentTimeMs: Long = System.currentTimeMillis()
    ): CircuitBreakerState {
        if (isExhausted.get()) return CircuitBreakerState.EXHAUSTED

        val isTripped = when (interfaceType) {
            NetworkInterfaceType.WIFI -> wifiFailures.get() >= failureThreshold
            NetworkInterfaceType.CELLULAR -> cellularFailures.get() >= failureThreshold
            NetworkInterfaceType.DEFAULT -> wifiFailures.get() >= failureThreshold
        }
        if (!isTripped) return CircuitBreakerState.HEALTHY

        val lastTrip = when (interfaceType) {
            NetworkInterfaceType.WIFI -> lastTripTimeWifi.get()
            NetworkInterfaceType.CELLULAR -> lastTripTimeCellular.get()
            NetworkInterfaceType.DEFAULT -> lastTripTimeWifi.get()
        }
        val cooldown = calculateCooldownMs(interfaceType)
        return if (currentTimeMs - lastTrip >= cooldown) {
            CircuitBreakerState.HALF_OPEN
        } else {
            CircuitBreakerState.TRIPPED
        }
    }

    /**
     * Returns true if the given interface is currently capable of carrying traffic
     * (either HEALTHY or ready for a HALF_OPEN probe).
     */
    fun isInterfaceCapable(
        interfaceType: NetworkInterfaceType,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        val ownState = evaluateInterfaceOnly(interfaceType, currentTimeMs)
        return ownState == CircuitBreakerState.HEALTHY || ownState == CircuitBreakerState.HALF_OPEN
    }

    fun evaluateState(
        interfaceType: NetworkInterfaceType,
        isOtherHealthyAndAvailable: Boolean,
        currentTimeMs: Long = System.currentTimeMillis()
    ): CircuitBreakerState {
        if (isExhausted.get()) return CircuitBreakerState.EXHAUSTED

        val isolatedState = evaluateInterfaceOnly(interfaceType, currentTimeMs)
        if (isolatedState == CircuitBreakerState.HEALTHY || isolatedState == CircuitBreakerState.HALF_OPEN) {
            return isolatedState
        }

        return if (isOtherHealthyAndAvailable) {
            isolatedState // TRIPPED
        } else {
            CircuitBreakerState.RECOVERING
        }
    }

    /**
     * Executes coordinated recovery when both interfaces trip.
     * Returns the current recovery cycle attempt (1..3), or null if exhausted.
     */
    fun attemptCoordinatedRecovery(): Int? {
        if (isExhausted.get()) return null
        val cycle = recoveryCycles.incrementAndGet()
        return if (cycle <= maxRecoveryCycles) {
            wifiFailures.set(0)
            cellularFailures.set(0)
            publishSnapshot()
            cycle
        } else {
            isExhausted.set(true)
            publishSnapshot()
            null
        }
    }

    fun calculateRecoveryBackoffMs(): Long {
        val cycle = recoveryCycles.get().coerceAtLeast(1)
        return kotlin.math.min(1000L * (1L shl (cycle - 1)), 4000L)
    }

    fun markExhausted() {
        isExhausted.set(true)
        publishSnapshot()
    }

    fun reset() {
        wifiFailures.set(0)
        cellularFailures.set(0)
        chunkFailures.clear()
        recoveryCycles.set(0)
        tripCountWifi.set(0)
        tripCountCellular.set(0)
        lastTripTimeWifi.set(0L)
        lastTripTimeCellular.set(0L)
        isExhausted.set(false)
        publishSnapshot()
    }

    fun refreshSnapshot(
        isWifiAvailable: Boolean = true,
        isCellularAvailable: Boolean = true,
        currentTimeMs: Long = System.currentTimeMillis()
    ): CircuitBreakerSnapshot {
        val snap = computeSnapshot(isWifiAvailable, isCellularAvailable, currentTimeMs)
        _snapshot.value = snap
        return snap
    }

    private fun publishSnapshot(currentTimeMs: Long = System.currentTimeMillis()) {
        _snapshot.value = computeSnapshot(true, true, currentTimeMs)
    }

    private fun computeSnapshot(
        isWifiAvailable: Boolean,
        isCellularAvailable: Boolean,
        currentTimeMs: Long
    ): CircuitBreakerSnapshot {
        val cycle = recoveryCycles.get()
        if (isExhausted.get()) {
            return CircuitBreakerSnapshot(
                wifiState = CircuitBreakerState.EXHAUSTED,
                cellularState = CircuitBreakerState.EXHAUSTED,
                aggregateState = CircuitBreakerAggregateState.Exhausted,
                recoveryCycle = cycle
            )
        }

        val rawWifi = evaluateInterfaceOnly(NetworkInterfaceType.WIFI, currentTimeMs)
        val rawCell = evaluateInterfaceOnly(NetworkInterfaceType.CELLULAR, currentTimeMs)
        val wifiReady = isWifiAvailable && (rawWifi == CircuitBreakerState.HEALTHY || rawWifi == CircuitBreakerState.HALF_OPEN)
        val cellReady = isCellularAvailable && (rawCell == CircuitBreakerState.HEALTHY || rawCell == CircuitBreakerState.HALF_OPEN)

        val wifiEff = evaluateState(NetworkInterfaceType.WIFI, cellReady, currentTimeMs)
        val cellEff = evaluateState(NetworkInterfaceType.CELLULAR, wifiReady, currentTimeMs)

        val aggregate = when {
            rawWifi == CircuitBreakerState.HEALTHY && rawCell == CircuitBreakerState.HEALTHY ->
                CircuitBreakerAggregateState.BothHealthy
            rawWifi != CircuitBreakerState.HEALTHY && cellReady ->
                CircuitBreakerAggregateState.FailoverActive(NetworkInterfaceType.WIFI, wifiEff, NetworkInterfaceType.CELLULAR)
            rawCell != CircuitBreakerState.HEALTHY && wifiReady ->
                CircuitBreakerAggregateState.FailoverActive(NetworkInterfaceType.CELLULAR, cellEff, NetworkInterfaceType.WIFI)
            else ->
                CircuitBreakerAggregateState.BothTripped(cycle, maxRecoveryCycles)
        }

        return CircuitBreakerSnapshot(
            wifiState = wifiEff,
            cellularState = cellEff,
            aggregateState = aggregate,
            recoveryCycle = cycle
        )
    }
}
