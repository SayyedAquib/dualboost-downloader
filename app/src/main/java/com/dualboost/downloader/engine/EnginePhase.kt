package com.dualboost.downloader.engine

/**
 * Explicit lifecycle phases for [DualDownloaderEngine].
 * Replaces implicit state combinations across loose AtomicBooleans (isPaused, isDiscarding, isExecuting)
 * and enforces deterministic phase transitions.
 */
enum class EnginePhase {
    /** Engine is idle and ready to accept new downloads. */
    IDLE,

    /** Active download pipeline is executing (probing, chunking, or streaming). */
    EXECUTING,

    /** Transfer has been paused; in-flight network calls have been cancelled and state is preserved. */
    PAUSED,

    /** Transfer is being discarded; in-flight calls cancelled and partial disk files are being purged. */
    DISCARDING;

    val isActive: Boolean get() = this == EXECUTING
    val isInterrupted: Boolean get() = this == PAUSED || this == DISCARDING

    fun canTransitionTo(next: EnginePhase): Boolean {
        if (this == next) return true
        return when (this) {
            IDLE -> next == EXECUTING || next == PAUSED || next == DISCARDING
            EXECUTING -> next == IDLE || next == PAUSED || next == DISCARDING
            PAUSED -> next == EXECUTING || next == DISCARDING || next == IDLE
            DISCARDING -> next == IDLE
        }
    }
}
