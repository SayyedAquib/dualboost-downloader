package com.dualboost.downloader.engine

import com.dualboost.downloader.data.NetworkInterfaceType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Explicit state model for the DualBoost network interface circuit breaker.
 */
enum class CircuitBreakerState {
    /** Interface is healthy, errors are below the trip threshold. */
    HEALTHY,

    /** Interface has tripped due to consecutive failures, traffic diverts to peer interface. */
    TRIPPED,

    /** Both interfaces have tripped; coordinated reconnection & exponential backoff is active. */
    RECOVERING,

    /** Retries exhausted (>3 recovery cycles or chunk retries exceeded); escalated to auto-pause. */
    EXHAUSTED
}

/**
 * Manages per-interface failure tracking, failover diversion, and coordinated recovery
 * without loose integer counters.
 */
class DualInterfaceCircuitBreaker(
    val failureThreshold: Int = 2,
    val maxRecoveryCycles: Int = 3
) {
    private val wifiFailures = AtomicInteger(0)
    private val cellularFailures = AtomicInteger(0)
    private val chunkFailures = ConcurrentHashMap<Int, AtomicInteger>()
    private val recoveryCycles = AtomicInteger(0)
    private val isExhausted = AtomicBoolean(false)

    fun isExhausted(): Boolean = isExhausted.get()

    fun recordSuccess(interfaceType: NetworkInterfaceType) {
        when (interfaceType) {
            NetworkInterfaceType.WIFI -> wifiFailures.set(0)
            NetworkInterfaceType.CELLULAR -> cellularFailures.set(0)
            NetworkInterfaceType.DEFAULT -> {
                wifiFailures.set(0)
                cellularFailures.set(0)
            }
        }
    }

    fun recordFailure(interfaceType: NetworkInterfaceType): CircuitBreakerState {
        val failures = when (interfaceType) {
            NetworkInterfaceType.WIFI -> wifiFailures.incrementAndGet()
            NetworkInterfaceType.CELLULAR -> cellularFailures.incrementAndGet()
            NetworkInterfaceType.DEFAULT -> wifiFailures.incrementAndGet()
        }
        return if (failures >= failureThreshold) CircuitBreakerState.TRIPPED else CircuitBreakerState.HEALTHY
    }

    fun recordChunkFailure(chunkIndex: Int, maxRetries: Int): Boolean {
        val attempts = chunkFailures.computeIfAbsent(chunkIndex) { AtomicInteger(0) }.incrementAndGet()
        if (attempts >= maxRetries) {
            isExhausted.set(true)
            return true
        }
        return false
    }

    fun evaluateState(
        interfaceType: NetworkInterfaceType,
        isOtherHealthyAndAvailable: Boolean
    ): CircuitBreakerState {
        if (isExhausted.get()) return CircuitBreakerState.EXHAUSTED

        val isTripped = when (interfaceType) {
            NetworkInterfaceType.WIFI -> wifiFailures.get() >= failureThreshold
            NetworkInterfaceType.CELLULAR -> cellularFailures.get() >= failureThreshold
            NetworkInterfaceType.DEFAULT -> wifiFailures.get() >= failureThreshold
        }

        if (!isTripped) return CircuitBreakerState.HEALTHY
        if (isOtherHealthyAndAvailable) return CircuitBreakerState.TRIPPED
        return CircuitBreakerState.RECOVERING
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
            cycle
        } else {
            isExhausted.set(true)
            null
        }
    }

    fun calculateRecoveryBackoffMs(): Long {
        val cycle = recoveryCycles.get().coerceAtLeast(1)
        return kotlin.math.min(1000L * (1L shl (cycle - 1)), 4000L)
    }

    fun markExhausted() {
        isExhausted.set(true)
    }

    fun reset() {
        wifiFailures.set(0)
        cellularFailures.set(0)
        chunkFailures.clear()
        recoveryCycles.set(0)
        isExhausted.set(false)
    }
}
