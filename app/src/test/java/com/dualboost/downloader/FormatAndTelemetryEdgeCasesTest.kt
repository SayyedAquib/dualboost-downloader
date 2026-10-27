package com.dualboost.downloader

import com.dualboost.downloader.data.ChunkSnapshot
import com.dualboost.downloader.data.ChunkStatus
import com.dualboost.downloader.data.DownloadChunk
import com.dualboost.downloader.data.NetworkInterfaceType
import com.dualboost.downloader.data.SpeedTestPhase
import com.dualboost.downloader.data.SpeedTestState
import com.dualboost.downloader.data.formatEta
import com.dualboost.downloader.data.formatSpeed
import com.dualboost.downloader.data.formatSpeedComponents
import org.junit.Assert.*
import org.junit.Test

class FormatAndTelemetryEdgeCasesTest {

    // --- formatEta Tests ---

    @Test
    fun testFormatEta_SubSecondAndZero() {
        assertEquals("< 1s", formatEta(0L))
        assertEquals("< 1s", formatEta(-10L))
    }

    @Test
    fun testFormatEta_SecondsOnly() {
        assertEquals("1s", formatEta(1L))
        assertEquals("45s", formatEta(45L))
        assertEquals("59s", formatEta(59L))
    }

    @Test
    fun testFormatEta_MinutesAndSeconds() {
        // Exact minutes (remainingSecs == 0)
        assertEquals("1m", formatEta(60L))
        assertEquals("5m", formatEta(300L))

        // Minutes with seconds
        assertEquals("1m 1s", formatEta(61L))
        assertEquals("1m 20s", formatEta(80L))
        assertEquals("59m 59s", formatEta(3599L))
    }

    @Test
    fun testFormatEta_HoursAndMinutes() {
        // Exact hours
        assertEquals("1h 0m", formatEta(3600L))
        assertEquals("2h 0m", formatEta(7200L))

        // Hours with minutes
        assertEquals("1h 1m", formatEta(3661L))
        assertEquals("2h 30m", formatEta(9000L))
        assertEquals("24h 0m", formatEta(86400L))
        assertEquals("25h 1m", formatEta(90060L))
    }

    // --- formatSpeed Tests ---

    @Test
    fun testFormatSpeed_ZeroAndNegative() {
        assertEquals("0.0 MB/s", formatSpeed(0.0))
        assertEquals("0.0 MB/s", formatSpeed(-5.0))
    }

    @Test
    fun testFormatSpeed_SubMegabyteUsesKbps() {
        // Speeds below 1.0 MB/s adapt to KB/s
        assertEquals("512 KB/s", formatSpeed(0.5))
        assertEquals("256 KB/s", formatSpeed(0.25))
        assertEquals("768 KB/s", formatSpeed(0.75))
        assertEquals("1023 KB/s", formatSpeed(0.999))
    }

    @Test
    fun testFormatSpeed_MegabyteAndAbove() {
        assertEquals("1.0 MB/s", formatSpeed(1.0))
        assertEquals("15.7 MB/s", formatSpeed(15.67))
        assertEquals("100.0 MB/s", formatSpeed(100.0))
        assertEquals("125.5 MB/s", formatSpeed(125.48))
    }

    // --- formatSpeedComponents Tests ---

    @Test
    fun testFormatSpeedComponents_ZeroAndNegative() {
        val (num, unit) = formatSpeedComponents(0f)
        assertEquals("0.0", num)
        assertEquals("MB/s", unit)

        val (negNum, negUnit) = formatSpeedComponents(-1f)
        assertEquals("0.0", negNum)
        assertEquals("MB/s", negUnit)
    }

    @Test
    fun testFormatSpeedComponents_SubMegabyte() {
        val (num, unit) = formatSpeedComponents(0.5f)
        assertEquals("512", num)
        assertEquals("KB/s", unit)

        val (num2, unit2) = formatSpeedComponents(0.25f)
        assertEquals("256", num2)
        assertEquals("KB/s", unit2)
    }

    @Test
    fun testFormatSpeedComponents_MegabyteAndAbove() {
        val (num, unit) = formatSpeedComponents(1.0f)
        assertEquals("1.0", num)
        assertEquals("MB/s", unit)

        val (num2, unit2) = formatSpeedComponents(42.84f)
        assertEquals("42.8", num2)
        assertEquals("MB/s", unit2)
    }

    // --- DownloadChunk Model Tests ---

    @Test
    fun testDownloadChunk_TotalBytesCalculation() {
        // Single byte range 0-0
        val chunkSingle = DownloadChunk(index = 0, startByte = 0L, endByte = 0L)
        assertEquals(1L, chunkSingle.totalBytes)

        // 100-byte range 100-199
        val chunk100 = DownloadChunk(index = 1, startByte = 100L, endByte = 199L)
        assertEquals(100L, chunk100.totalBytes)

        // Standard 4 MB chunk: 0 to 4194303
        val chunk4MB = DownloadChunk(index = 2, startByte = 0L, endByte = 4194303L)
        assertEquals(4 * 1024 * 1024L, chunk4MB.totalBytes)
    }

    @Test
    fun testDownloadChunk_DefaultValues() {
        val chunk = DownloadChunk(index = 5, startByte = 1000L, endByte = 2000L)
        assertEquals(0L, chunk.downloadedBytes)
        assertEquals(ChunkStatus.PENDING, chunk.status)
        assertEquals(NetworkInterfaceType.DEFAULT, chunk.assignedNetwork)
    }

    // --- ChunkSnapshot Tests ---

    @Test
    fun testChunkSnapshot_PropertiesPreserved() {
        val snapshot = ChunkSnapshot(
            index = 3,
            progress = 0.75f,
            networkType = NetworkInterfaceType.WIFI,
            status = ChunkStatus.DOWNLOADING
        )
        assertEquals(3, snapshot.index)
        assertEquals(0.75f, snapshot.progress, 0.001f)
        assertEquals(NetworkInterfaceType.WIFI, snapshot.networkType)
        assertEquals(ChunkStatus.DOWNLOADING, snapshot.status)
    }

    // --- SpeedTestState.Completed & Boost Model Tests ---

    @Test
    fun testSpeedTestStateCompleted_BoostCalculationAndCompatibilityGetters() {
        val completed = SpeedTestState.Completed(
            wifiSpeedMBps = 15.5f,
            cellularSpeedMBps = 25.0f,
            dualBoostSpeedMBps = 40.5f,
            boostGainPercent = 62
        )

        assertEquals(15.5f, completed.wifiSpeedMBps, 0.01f)
        assertEquals(15.5f, completed.wifiSpeedMbps, 0.01f)

        assertEquals(25.0f, completed.cellularSpeedMBps, 0.01f)
        assertEquals(25.0f, completed.cellularSpeedMbps, 0.01f)

        assertEquals(40.5f, completed.dualBoostSpeedMBps, 0.01f)
        assertEquals(40.5f, completed.dualBoostSpeedMbps, 0.01f)

        assertEquals(62, completed.boostGainPercent)
    }

    @Test
    fun testSpeedTestStateRunning_ProgressAndPhase() {
        val running = SpeedTestState.Running(
            phase = SpeedTestPhase.TESTING_DUALBOOST,
            currentSpeedMBps = 32.4f,
            overallProgress = 0.85f,
            wifiSpeedMBps = 14.0f,
            cellularSpeedMBps = 18.4f
        )

        assertEquals(SpeedTestPhase.TESTING_DUALBOOST, running.phase)
        assertEquals(32.4f, running.currentSpeedMBps, 0.01f)
        assertEquals(32.4f, running.currentSpeedMbps, 0.01f)
        assertEquals(0.85f, running.overallProgress, 0.001f)
        assertEquals(14.0f, running.wifiSpeedMBps, 0.01f)
        assertEquals(18.4f, running.cellularSpeedMBps, 0.01f)
    }
}
