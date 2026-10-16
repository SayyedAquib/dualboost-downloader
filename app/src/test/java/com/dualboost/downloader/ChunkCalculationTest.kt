package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadChunk
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.min

class ChunkCalculationTest {

    private val chunkSize = 4 * 1024 * 1024L // 4 MB

    private fun generateChunks(totalLength: Long): List<DownloadChunk> {
        val chunks = mutableListOf<DownloadChunk>()
        var offset = 0L
        var index = 0
        while (offset < totalLength) {
            val end = min(offset + chunkSize - 1, totalLength - 1)
            chunks.add(DownloadChunk(index++, offset, end))
            offset = end + 1
        }
        return chunks
    }

    @Test
    fun testSmallFilePartition() {
        val fileSize = 1024L // 1 KB
        val chunks = generateChunks(fileSize)

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startByte)
        assertEquals(1023L, chunks[0].endByte)
        assertEquals(1024L, chunks[0].totalBytes)
    }

    @Test
    fun testExactMultiplePartition() {
        val fileSize = 8 * 1024 * 1024L // 8 MB (Exactly 2 chunks)
        val chunks = generateChunks(fileSize)

        assertEquals(2, chunks.size)
        assertEquals(0L, chunks[0].startByte)
        assertEquals(4 * 1024 * 1024L - 1, chunks[0].endByte)

        assertEquals(4 * 1024 * 1024L, chunks[1].startByte)
        assertEquals(8 * 1024 * 1024L - 1, chunks[1].endByte)

        val totalSum = chunks.sumOf { it.totalBytes }
        assertEquals(fileSize, totalSum)
    }

    @Test
    fun testOddFileSizePartition_NoGapsOrOverlaps() {
        val fileSize = 15_728_647L // Non-aligned size (~15 MB)
        val chunks = generateChunks(fileSize)

        assertEquals(4, chunks.size)

        var expectedNextStart = 0L
        for (chunk in chunks) {
            assertEquals("Chunk ${chunk.index} has gap or overlap", expectedNextStart, chunk.startByte)
            assertTrue(chunk.endByte >= chunk.startByte)
            expectedNextStart = chunk.endByte + 1
        }

        assertEquals("Last chunk end does not align with file size", fileSize, expectedNextStart)
        val totalSum = chunks.sumOf { it.totalBytes }
        assertEquals(fileSize, totalSum)
    }

    @Test
    fun testOneByteFile() {
        val fileSize = 1L
        val chunks = generateChunks(fileSize)

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startByte)
        assertEquals(0L, chunks[0].endByte)
        assertEquals(1L, chunks[0].totalBytes)
    }
}

