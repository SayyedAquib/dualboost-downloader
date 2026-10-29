package com.dualboost.downloader

import com.dualboost.downloader.security.CrashVault
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class CrashVaultTest {

    private lateinit var tempDir: File
    private lateinit var dummyContext: DummyTestContext

    @Before
    fun setUp() {
        tempDir = File("build/test-crash-vault-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        dummyContext = DummyTestContext(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testRecordCrash_WritesSanitizedCrashDump() {
        val vault = CrashVault(dummyContext, defaultHandler = null)
        val testException = RuntimeException("Fatal engine crash occurred at https://secret.cdn.com/auth?token=supersecret123")

        val crashFile = vault.recordCrash(Thread.currentThread(), testException)
        assertTrue("Crash file must exist on disk", crashFile.exists())

        val logs = vault.getCrashLogs()
        assertNotNull("Crash logs should not be null", logs)
        assertTrue(logs!!.contains("CRASH VAULT DUMP"))
        assertTrue(logs.contains("RuntimeException"))
        assertTrue(logs.contains("Thread:"))
        // Sensitive token must be sanitized
        assertFalse("Secret token must be sanitized from dump", logs.contains("token=supersecret123"))

        assertTrue("clearCrashLogs should delete the file", vault.clearCrashLogs())
        assertNull("Crash logs should be null after clearing", vault.getCrashLogs())
    }

    @Test
    fun testSanitizeCrashLog_RedactsUrlsWithParameters() {
        val rawTrace = "Error downloading https://edge.worker.dev/file.bin?api_key=XYZ987 at Engine.kt:42"
        val sanitized = CrashVault.sanitizeCrashLog(rawTrace)

        assertFalse(sanitized.contains("api_key=XYZ987"))
        assertTrue(sanitized.contains("https://edge.worker.dev/[REDACTED]"))
    }
}

