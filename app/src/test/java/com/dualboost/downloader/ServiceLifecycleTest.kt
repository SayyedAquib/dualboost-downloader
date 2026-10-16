package com.dualboost.downloader

import com.dualboost.downloader.service.DownloadForegroundService
import org.junit.Assert.*
import org.junit.Test

class ServiceLifecycleTest {

    @Test
    fun testServiceConstants_CorrectAndConsistent() {
        assertEquals("com.dualboost.ACTION_START_DOWNLOAD", DownloadForegroundService.ACTION_START_DOWNLOAD)
        assertEquals("com.dualboost.ACTION_CANCEL_DOWNLOAD", DownloadForegroundService.ACTION_CANCEL_DOWNLOAD)
        assertEquals("extra_download_url", DownloadForegroundService.EXTRA_URL)
        assertEquals("dualboost_download_channel", DownloadForegroundService.CHANNEL_ID)
        assertEquals(1001, DownloadForegroundService.NOTIFICATION_ID)
    }

    @Test
    fun testServiceActionNaming_FollowsReverseDomainConvention() {
        assertTrue(DownloadForegroundService.ACTION_START_DOWNLOAD.startsWith("com.dualboost."))
        assertTrue(DownloadForegroundService.ACTION_CANCEL_DOWNLOAD.startsWith("com.dualboost."))
        assertFalse(DownloadForegroundService.CHANNEL_ID.contains(" "))
    }

    @Test
    fun testThrottledNotificationLogic_LimitsToOncePerSecond() {
        var lastNotificationTime = 0L
        var notificationsDispatched = 0

        fun dispatchNotificationIfAllowed(currentTime: Long, isTerminal: Boolean): Boolean {
            if (isTerminal || currentTime - lastNotificationTime >= 1000L) {
                lastNotificationTime = currentTime
                notificationsDispatched++
                return true
            }
            return false
        }

        val startTime = 50_000L

        // Initial progress notification -> should dispatch
        assertTrue(dispatchNotificationIfAllowed(startTime, isTerminal = false))
        assertEquals(1, notificationsDispatched)

        // 100 rapid progress updates across next 900ms -> all throttled
        for (i in 1..100) {
            val intermediateTime = startTime + (i * 9)
            assertFalse(dispatchNotificationIfAllowed(intermediateTime, isTerminal = false))
        }
        assertEquals(1, notificationsDispatched)

        // Update at 1005ms -> should dispatch
        assertTrue(dispatchNotificationIfAllowed(startTime + 1005L, isTerminal = false))
        assertEquals(2, notificationsDispatched)

        // Immediate terminal event (Download Complete) 10ms later -> must bypass throttle and dispatch
        assertTrue(dispatchNotificationIfAllowed(startTime + 1015L, isTerminal = true))
        assertEquals(3, notificationsDispatched)
    }
}
