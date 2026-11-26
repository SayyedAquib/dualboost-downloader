package com.dualboost.downloader

import com.dualboost.downloader.service.NotificationThrottler
import org.junit.Assert.*
import org.junit.Test

class NotificationThrottlerTest {

    @Test
    fun testThrottling_SuppressesHighFrequencyUpdatesWithinWindow() {
        val throttler = NotificationThrottler(throttleIntervalMs = 1000L)
        val startTime = 10_000L

        // First update at t=10_000ms should dispatch immediately
        assertTrue("First update should dispatch", throttler.shouldDispatch(currentTimeMs = startTime))
        assertEquals(1, throttler.notificationsDispatched)

        // 50 rapid updates across the next 500ms (t=10_010 to t=10_500)
        for (i in 1..50) {
            val intermediateTime = startTime + (i * 10)
            val dispatched = throttler.shouldDispatch(currentTimeMs = intermediateTime)
            assertFalse("Intermediate update within 1000ms window must be throttled", dispatched)
        }
        assertEquals("No additional notifications should be dispatched during 500ms flurry", 1, throttler.notificationsDispatched)

        // Next update at t=11_001ms (1001ms after first) should dispatch
        val nextWindowTime = startTime + 1001L
        assertTrue("Update after throttle interval should dispatch", throttler.shouldDispatch(currentTimeMs = nextWindowTime))
        assertEquals(2, throttler.notificationsDispatched)
    }

    @Test
    fun testTerminalEvent_DispatchesImmediatelyWithoutThrottling() {
        val throttler = NotificationThrottler(throttleIntervalMs = 1000L)

        // Progress update
        assertTrue(throttler.shouldDispatch(currentTimeMs = 1000L))
        assertEquals(1, throttler.notificationsDispatched)

        // Immediate terminal/lifecycle event (Completed / Paused / Failed) at t=1010ms (10ms later)
        val terminalDispatched = throttler.shouldDispatch(currentTimeMs = 1010L, isTerminalOrLifecycle = true)
        assertTrue("Terminal event must dispatch immediately", terminalDispatched)
        assertEquals(2, throttler.notificationsDispatched)

        // Reset restores clean initial state
        throttler.reset()
        assertEquals(0, throttler.notificationsDispatched)
        assertTrue(throttler.shouldDispatch(currentTimeMs = 1020L))
    }
}

