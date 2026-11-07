package com.dualboost.downloader

import com.dualboost.downloader.data.ChunkStatus
import com.dualboost.downloader.data.DownloadChunk
import com.dualboost.downloader.data.NetworkInterfaceType
import com.dualboost.downloader.engine.DualDownloaderEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class SequentialChunkingTest {

    private lateinit var tempDir: File
    private lateinit var engine: DualDownloaderEngine

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "dualboost_seq_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val dummyContext = DummyTestContext(tempDir)
        val mockNet = AsymmetricWorkStealingTest.MockNetworkProvider()
        engine = DualDownloaderEngine(dummyContext, mockNet)
    }

    @Test
    fun testIsVideoFile_IdentifiesCommonContainers() {
        // Common video extensions
        assertTrue("mkv must be identified as video", engine.isVideoFile("movie.mkv"))
        assertTrue("mp4 must be identified as video", engine.isVideoFile("trailer.mp4"))
        assertTrue("webm must be identified as video", engine.isVideoFile("stream.webm"))
        assertTrue("mov must be identified as video", engine.isVideoFile("clip.mov"))
        assertTrue("avi must be identified as video", engine.isVideoFile("video.avi"))
        assertTrue("ts must be identified as video", engine.isVideoFile("segment.ts"))
        assertTrue("m4v must be identified as video", engine.isVideoFile("episode.m4v"))
        assertTrue("flv must be identified as video", engine.isVideoFile("flash.flv"))

        // Case insensitivity & URL query parameters
        assertTrue("Uppercase MKV must be identified", engine.isVideoFile("MIRZAPUR.MKV"))
        assertTrue("MP4 with query parameters", engine.isVideoFile("https://cdn.example.com/video.mp4?auth=token123"))
        assertTrue("Complex encoded name", engine.isVideoFile("Mirzapur.The.Movie.2026.4K-2160p.x265-MoviesLeech.mkv"))

        // Non-video files
        assertFalse("zip must not be identified as video", engine.isVideoFile("archive.zip"))
        assertFalse("iso must not be identified as video", engine.isVideoFile("ubuntu.iso"))
        assertFalse("bin must not be identified as video", engine.isVideoFile("firmware.bin"))
        assertFalse("pdf must not be identified as video", engine.isVideoFile("document.pdf"))
        assertFalse("apk must not be identified as video", engine.isVideoFile("app-release.apk"))
    }

    @Test
    fun testCalculateContiguousPlayableBytes_FromByteZero() {
        val chunk0 = DownloadChunk(0, 0, 4_194_303, ChunkStatus.COMPLETED)
        val chunk1 = DownloadChunk(1, 4_194_304, 8_388_607, ChunkStatus.COMPLETED)
        val chunk2 = DownloadChunk(2, 8_388_608, 12_582_911, ChunkStatus.COMPLETED)
        val chunk3 = DownloadChunk(3, 12_582_912, 16_777_215, ChunkStatus.PENDING)
        val chunk4 = DownloadChunk(4, 16_777_216, 20_971_519, ChunkStatus.COMPLETED)

        // 1. Chunks 0, 1, 2 contiguous: should be 12 MB
        val list1 = listOf(chunk0, chunk1, chunk2, chunk3, chunk4)
        assertEquals(12_582_912L, engine.calculateContiguousPlayableBytes(list1))

        // 2. If chunk 0 is not completed, contiguous playable bytes must be 0
        val pendingChunk0 = DownloadChunk(0, 0, 4_194_303, ChunkStatus.PENDING)
        val list2 = listOf(pendingChunk0, chunk1, chunk2)
        assertEquals(0L, engine.calculateContiguousPlayableBytes(list2))

        // 3. All chunks completed
        val completedChunk3 = DownloadChunk(3, 12_582_912, 16_777_215, ChunkStatus.COMPLETED)
        val list3 = listOf(chunk0, chunk1, chunk2, completedChunk3, chunk4)
        assertEquals(20_971_520L, engine.calculateContiguousPlayableBytes(list3))
    }

    @Test
    fun testClaimSequentialPendingChunks_PrioritizesHeadAndTrailerForMp4() {
        val totalChunks = 10
        val chunkSize = 4_194_304L
        val chunks: MutableList<DownloadChunk> = (0 until totalChunks).map { idx ->
            DownloadChunk(
                index = idx,
                startByte = idx * chunkSize,
                endByte = (idx + 1) * chunkSize - 1,
                status = ChunkStatus.PENDING
            )
        }.toMutableList()

        // 1. First claim for MP4: Must claim chunk 0 (Header)
        val claim1 = engine.claimSequentialPendingChunks(
            pendingChunks = chunks,
            maxBatchCount = 1,
            interfaceType = NetworkInterfaceType.WIFI,
            isSequentialMode = true,
            isMp4 = true
        )
        assertEquals(1, claim1.size)
        assertEquals(0, claim1[0].index)
        assertEquals(ChunkStatus.DOWNLOADING, chunks[0].status)

        // 2. Second claim for MP4 while chunk 0 is in progress:
        // Must prioritize EOF Trailer (chunk 9) so the `moov` atom is fetched upfront
        val claim2 = engine.claimSequentialPendingChunks(
            pendingChunks = chunks,
            maxBatchCount = 1,
            interfaceType = NetworkInterfaceType.CELLULAR,
            isSequentialMode = true,
            isMp4 = true
        )
        assertEquals(1, claim2.size)
        assertEquals(9, claim2[0].index)
        assertEquals(ChunkStatus.DOWNLOADING, chunks[9].status)

        // 3. Third claim: Once trailer (chunk 9) is claimed, claims progress linearly from head forward (chunk 1)
        val claim3 = engine.claimSequentialPendingChunks(
            pendingChunks = chunks,
            maxBatchCount = 2,
            interfaceType = NetworkInterfaceType.WIFI,
            isSequentialMode = true,
            isMp4 = true
        )
        assertEquals(2, claim3.size)
        assertEquals(1, claim3[0].index)
        assertEquals(2, claim3[1].index)
    }

    @Test
    fun testClaimSequentialPendingChunks_LinearProgressionForMkv() {
        val totalChunks = 8
        val chunkSize = 4_194_304L
        val chunks: MutableList<DownloadChunk> = (0 until totalChunks).map { idx ->
            DownloadChunk(
                index = idx,
                startByte = idx * chunkSize,
                endByte = (idx + 1) * chunkSize - 1,
                status = ChunkStatus.PENDING
            )
        }.toMutableList()

        // For MKV (isMp4 = false): Chunks progress strictly 0, 1, 2...
        val claim1 = engine.claimSequentialPendingChunks(
            pendingChunks = chunks,
            maxBatchCount = 2,
            interfaceType = NetworkInterfaceType.WIFI,
            isSequentialMode = true,
            isMp4 = false
        )
        assertEquals(2, claim1.size)
        assertEquals(0, claim1[0].index)
        assertEquals(1, claim1[1].index)

        // Sibling interface takes next contiguous chunk (2)
        val claim2 = engine.claimSequentialPendingChunks(
            pendingChunks = chunks,
            maxBatchCount = 1,
            interfaceType = NetworkInterfaceType.CELLULAR,
            isSequentialMode = true,
            isMp4 = false
        )
        assertEquals(1, claim2.size)
        assertEquals(2, claim2[0].index)
    }

    @Test
    fun testPlayableThreshold_TriggersAt24MBForMkv() {
        val chunkSize = 4_194_304L // 4 MB
        val totalChunks = 10
        val chunks = (0 until totalChunks).map { idx ->
            DownloadChunk(
                index = idx,
                startByte = idx * chunkSize,
                endByte = (idx + 1) * chunkSize - 1,
                status = ChunkStatus.PENDING
            )
        }

        // Initially 0 bytes -> not playable
        assertFalse(engine.isDownloadPlayable(chunks, totalBytes = totalChunks * chunkSize, isMp4 = false))

        // Complete 5 chunks (20 MB < 24 MB threshold) -> not playable yet
        for (i in 0 until 5) chunks[i].status = ChunkStatus.COMPLETED
        assertFalse(engine.isDownloadPlayable(chunks, totalBytes = totalChunks * chunkSize, isMp4 = false))

        // Complete 6th chunk (24 MB >= 24 MB threshold) -> PLAYABLE!
        chunks[5].status = ChunkStatus.COMPLETED
        assertTrue(engine.isDownloadPlayable(chunks, totalBytes = totalChunks * chunkSize, isMp4 = false))
    }

    @Test
    fun testPlayableThreshold_RequiresMoovAtomForMp4() {
        val chunkSize = 4_194_304L // 4 MB
        val totalChunks = 10
        val chunks = (0 until totalChunks).map { idx ->
            DownloadChunk(
                index = idx,
                startByte = idx * chunkSize,
                endByte = (idx + 1) * chunkSize - 1,
                status = ChunkStatus.PENDING
            )
        }

        // Complete 6 contiguous chunks at head (24 MB)
        for (i in 0 until 6) chunks[i].status = ChunkStatus.COMPLETED

        // For MP4, last chunk (chunk 9) is still PENDING -> not playable yet!
        assertFalse(engine.isDownloadPlayable(chunks, totalBytes = totalChunks * chunkSize, isMp4 = true))

        // Complete EOF trailer chunk (chunk 9) -> PLAYABLE!
        chunks[9].status = ChunkStatus.COMPLETED
        assertTrue(engine.isDownloadPlayable(chunks, totalBytes = totalChunks * chunkSize, isMp4 = true))
    }
}

