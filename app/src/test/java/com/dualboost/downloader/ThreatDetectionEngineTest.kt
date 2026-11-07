package com.dualboost.downloader

import com.dualboost.downloader.security.ThreatAssessment
import com.dualboost.downloader.security.ThreatDetectionEngine
import com.dualboost.downloader.security.ThreatSeverity
import com.dualboost.downloader.security.ThreatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThreatDetectionEngineTest {

    private lateinit var engine: ThreatDetectionEngine

    @Before
    fun setUp() {
        engine = ThreatDetectionEngine()
    }

    @Test
    fun detectsDisguisedApkDoubleExtension() {
        val url = "https://moviesleech.media/downloads/Mirzapur.The.Movie.2026.4K.mkv.apk"
        val assessment = engine.inspectUrlAndHeaders(
            url = url,
            headers = emptyMap(),
            userReportedName = "Mirzapur.The.Movie.2026.4K.mkv"
        )

        assertTrue("Expected threat to be detected for double extension", assessment.isThreat)
        assertEquals(ThreatType.DOUBLE_EXTENSION, assessment.threatType)
        assertEquals(ThreatSeverity.CRITICAL, assessment.severity)
        assertTrue(assessment.description.contains("apk", ignoreCase = true))
    }

    @Test
    fun detectsMaliciousMimeType() {
        val url = "https://files.streamserver.xyz/v/mirzapur_stream.mkv"
        val headers = mapOf(
            "Content-Type" to "application/vnd.android.package-archive"
        )
        val assessment = engine.inspectUrlAndHeaders(
            url = url,
            headers = headers,
            userReportedName = "mirzapur_stream.mkv"
        )

        assertTrue("Expected threat for APK MIME type disguised as media", assessment.isThreat)
        assertEquals(ThreatType.MALICIOUS_MIME, assessment.threatType)
        assertEquals(ThreatSeverity.CRITICAL, assessment.severity)
    }

    @Test
    fun detectsDisguisedExecutableExtensions() {
        val url = "https://files.freemovies.club/get?file=setup_codec.exe"
        val assessment = engine.inspectUrlAndHeaders(
            url = url,
            headers = emptyMap(),
            userReportedName = "setup_codec.exe"
        )

        assertTrue("Expected threat for executable file extension", assessment.isThreat)
        assertEquals(ThreatType.DISGUISED_EXECUTABLE, assessment.threatType)
    }

    @Test
    fun detectsMagicBytesMismatch_apkDisguisedAsMp4() {
        // ZIP/APK magic bytes: 0x50, 0x4B, 0x03, 0x04 ("PK..")
        val apkMagicBytes = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte(), 0x00, 0x00)
        val assessment = engine.inspectMagicBytes(
            bytes = apkMagicBytes,
            reportedExtension = "mp4"
        )

        assertTrue("Expected threat for APK magic bytes masquerading as MP4", assessment.isThreat)
        assertEquals(ThreatType.MAGIC_BYTE_MISMATCH, assessment.threatType)
        assertEquals(ThreatSeverity.CRITICAL, assessment.severity)
    }

    @Test
    fun detectsMagicBytesMismatch_exeDisguisedAsMkv() {
        // DOS/Windows executable magic bytes: 0x4D, 0x5A ("MZ")
        val exeMagicBytes = byteArrayOf(0x4D.toByte(), 0x5A.toByte(), 0x90.toByte(), 0x00.toByte())
        val assessment = engine.inspectMagicBytes(
            bytes = exeMagicBytes,
            reportedExtension = "mkv"
        )

        assertTrue("Expected threat for EXE magic bytes masquerading as MKV", assessment.isThreat)
        assertEquals(ThreatType.MAGIC_BYTE_MISMATCH, assessment.threatType)
    }

    @Test
    fun passesGenuineMp4AndMkvFiles() {
        // Genuine MP4 header starts with ftyp at byte offset 4: 00 00 00 18 66 74 79 70 ("....ftyp")
        val genuineMp4 = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D)
        val mp4Assessment = engine.inspectMagicBytes(
            bytes = genuineMp4,
            reportedExtension = "mp4"
        )
        assertFalse("Genuine MP4 should pass clean", mp4Assessment.isThreat)

        // Genuine MKV header starts with 0x1A, 0x45, 0xDF, 0xA3 (EBML ID)
        val genuineMkv = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x00)
        val mkvAssessment = engine.inspectMagicBytes(
            bytes = genuineMkv,
            reportedExtension = "mkv"
        )
        assertFalse("Genuine MKV should pass clean", mkvAssessment.isThreat)
    }

    @Test
    fun detectsKnownAdwareAndMalwareDomains() {
        val url = "https://track-install.apk-serve.net/download/payload"
        val assessment = engine.inspectUrlAndHeaders(
            url = url,
            headers = emptyMap(),
            userReportedName = "video.mp4"
        )

        assertTrue("Known adware domain must be flagged", assessment.isThreat)
        assertEquals(ThreatType.KNOWN_MALICIOUS_DOMAIN, assessment.threatType)
    }
}
