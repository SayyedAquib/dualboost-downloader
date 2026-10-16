package com.dualboost.downloader

import org.junit.Assert.*
import org.junit.Test

class NotificationThrottlerTest {

    class TestThrottler(private val throttleIntervalMs: Long = 1000L) {
        var lastNotificationTime = 0L
        var notificationsDispatched = 0

        fun onProgressUpdate(currentTime: Long): Boolean {
            if (currentTime - lastNotificationTime >= throttleIntervalMs) {
                lastNotificationTime = currentTime
                notificationsDispatched++
                return true
            }
            return false
        }

        fun onTerminalEvent(): Boolean {
            notificationsDispatched++
            return true
        }
    }

    @Test
    fun testThrottling_SuppressesHighFrequencyUpdatesWithinWindow() {
        val throttler = TestThrottler(throttleIntervalMs = 1000L)
        val startTime = 10_000L

        // First update at t=10_000ms should dispatch
        assertTrue("First update should dispatch", throttler.onProgressUpdate(startTime))
        assertEquals(1, throttler.notificationsDispatched)

        // 50 rapid updates across the next 500ms (t=10_010 to t=10_500)
        for (i in 1..50) {
            val intermediateTime = startTime + (i * 10)
            val dispatched = throttler.onProgressUpdate(intermediateTime)
            assertFalse("Intermediate update within 1000ms window must be throttled", dispatched)
        }
        assertEquals("No additional notifications should be dispatched during 500ms flurry", 1, throttler.notificationsDispatched)

        // Next update at t=11_001ms (1001ms after first) should dispatch
        val nextWindowTime = startTime + 1001L
        assertTrue("Update after throttle interval should dispatch", throttler.onProgressUpdate(nextWindowTime))
        assertEquals(2, throttler.notificationsDispatched)
    }

    @Test
    fun testTerminalEvent_DispatchesImmediatelyWithoutThrottling() {
        val throttler = TestThrottler(throttleIntervalMs = 1000L)

        // Progress update
        throttler.onProgressUpdate(1000L)
        assertEquals(1, throttler.notificationsDispatched)

        // Immediate terminal event (Download Complete / Failed) at t=1010ms (10ms later)
        val terminalDispatched = throttler.onTerminalEvent()
        assertTrue("Terminal event must dispatch immediately", terminalDispatched)
        assertEquals(2, throttler.notificationsDispatched)
    }
}

