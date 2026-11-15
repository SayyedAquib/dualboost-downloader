package com.dualboost.downloader.data

class ConcurrencyConfig(
    wifiWorkers: Int = DEFAULT_WIFI_WORKERS,
    cellularWorkers: Int = DEFAULT_CELLULAR_WORKERS,
    val isAutoScaleEnabled: Boolean = false
) {
    val wifiWorkers: Int = wifiWorkers.coerceIn(MIN_WORKERS, MAX_WORKERS)
    val cellularWorkers: Int = cellularWorkers.coerceIn(MIN_WORKERS, MAX_WORKERS)

    val totalWorkers: Int
        get() = this.wifiWorkers + this.cellularWorkers

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConcurrencyConfig) return false
        return wifiWorkers == other.wifiWorkers &&
                cellularWorkers == other.cellularWorkers &&
                isAutoScaleEnabled == other.isAutoScaleEnabled
    }

    override fun hashCode(): Int {
        var result = wifiWorkers
        result = 31 * result + cellularWorkers
        result = 31 * result + isAutoScaleEnabled.hashCode()
        return result
    }

    override fun toString(): String {
        return "ConcurrencyConfig(wifiWorkers=$wifiWorkers, cellularWorkers=$cellularWorkers, isAutoScaleEnabled=$isAutoScaleEnabled)"
    }

    fun copy(
        wifiWorkers: Int = this.wifiWorkers,
        cellularWorkers: Int = this.cellularWorkers,
        isAutoScaleEnabled: Boolean = this.isAutoScaleEnabled
    ): ConcurrencyConfig = ConcurrencyConfig(wifiWorkers, cellularWorkers, isAutoScaleEnabled)

    companion object {
        const val MIN_WORKERS = 1
        const val MAX_WORKERS = 8
        const val DEFAULT_WIFI_WORKERS = 2
        const val DEFAULT_CELLULAR_WORKERS = 2
        val DEFAULT = ConcurrencyConfig(DEFAULT_WIFI_WORKERS, DEFAULT_CELLULAR_WORKERS, false)
    }
}
