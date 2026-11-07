package com.dualboost.downloader.security

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * High-performance, on-device security engine that detects disguised APKs,
 * executables, fake MIME types, malicious domains, and magic byte mismatches.
 */
class ThreatDetectionEngine {

    companion object {
        private val EXECUTABLE_EXTENSIONS = setOf(
            "apk", "exe", "bat", "cmd", "vbs", "vbe", "js", "jse",
            "wsf", "wsh", "ps1", "sh", "dex", "scr", "pif", "dll", "jar", "msi"
        )

        private val MALICIOUS_MIME_TYPES = setOf(
            "application/vnd.android.package-archive",
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-executable",
            "application/x-bat",
            "application/x-sh"
        )

        private val KNOWN_MALICIOUS_DOMAINS = setOf(
            "apk-serve.net",
            "track-install.apk-serve.net",
            "malicious-ads.com",
            "ad-redirector.net",
            "install-update.xyz",
            "virus-alert.org",
            "betting-install.com"
        )

        private val MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "m4v", "mp3", "aac", "wav", "flac"
        )
    }

    /**
     * Inspects the URL, response headers, and file names prior to fetching chunk payloads.
     */
    fun inspectUrlAndHeaders(
        url: String,
        headers: Map<String, String> = emptyMap(),
        userReportedName: String? = null
    ): ThreatAssessment {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT) ?: ""

        // 1. Check known malicious domains
        if (KNOWN_MALICIOUS_DOMAINS.any { host == it || host.endsWith(".$it") }) {
            return ThreatAssessment(
                isThreat = true,
                threatType = ThreatType.KNOWN_MALICIOUS_DOMAIN,
                severity = ThreatSeverity.HIGH,
                description = "Domain '$host' is identified on security threat intelligence lists.",
                originalTarget = userReportedName,
                disguisedTarget = url
            )
        }

        // 2. Parse candidate filenames from Content-Disposition, URL path, query params, or user-reported name
        val contentDisposition = headers["Content-Disposition"] ?: headers["content-disposition"]
        val candidates = collectCandidateFilenames(uri, contentDisposition, userReportedName)

        // 3. Check for double extension trap across candidates (e.g. .mkv.apk or .mp4.apk)
        for (candidate in candidates) {
            if (hasDoubleExtensionTrap(candidate)) {
                val extension = candidate.substringAfterLast('.')
                return ThreatAssessment(
                    isThreat = true,
                    threatType = ThreatType.DOUBLE_EXTENSION,
                    severity = ThreatSeverity.CRITICAL,
                    description = "File uses a deceptive double extension ending in '.$extension' disguised as media.",
                    originalTarget = userReportedName,
                    disguisedTarget = candidate
                )
            }
        }

        // 4. Check for disguised executable extensions across candidates
        for (candidate in candidates) {
            val fileExtension = candidate.substringAfterLast('.', "")
            if (EXECUTABLE_EXTENSIONS.contains(fileExtension)) {
                return ThreatAssessment(
                    isThreat = true,
                    threatType = ThreatType.DISGUISED_EXECUTABLE,
                    severity = ThreatSeverity.CRITICAL,
                    description = "The target file is an executable package ('.$fileExtension') which may execute malicious code.",
                    originalTarget = userReportedName,
                    disguisedTarget = candidate
                )
            }
        }

        // 5. Check Content-Type / MIME type for APK or executable payloads
        val contentType = (headers["Content-Type"] ?: headers["content-type"])
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)

        if (contentType != null && MALICIOUS_MIME_TYPES.contains(contentType)) {
            return ThreatAssessment(
                isThreat = true,
                threatType = ThreatType.MALICIOUS_MIME,
                severity = ThreatSeverity.CRITICAL,
                description = "Server returned application installer MIME type '$contentType' instead of safe media.",
                originalTarget = userReportedName,
                disguisedTarget = candidates.firstOrNull() ?: userReportedName ?: url
            )
        }

        return ThreatAssessment.clean()
    }

    /**
     * Inspects raw magic bytes (first 4 to 16 bytes of payload) to detect executable payloads disguised as media.
     */
    fun inspectMagicBytes(
        bytes: ByteArray,
        reportedExtension: String
    ): ThreatAssessment {
        if (bytes.size < 4) return ThreatAssessment.clean()
        val ext = reportedExtension.lowercase(Locale.ROOT).removePrefix(".")

        // If reported as media file (video/audio)
        if (MEDIA_EXTENSIONS.contains(ext)) {
            // Check for PK (ZIP / APK / JAR): 0x50, 0x4B, 0x03, 0x04
            if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
            ) {
                return ThreatAssessment(
                    isThreat = true,
                    threatType = ThreatType.MAGIC_BYTE_MISMATCH,
                    severity = ThreatSeverity.CRITICAL,
                    description = "File is disguised as '.$ext' but contains Android APK / ZIP package headers (PK..).",
                    originalTarget = ext,
                    disguisedTarget = "application/vnd.android.package-archive"
                )
            }

            // Check for MZ (DOS / Windows Executable): 0x4D, 0x5A
            if (bytes[0] == 0x4D.toByte() && bytes[1] == 0x5A.toByte()) {
                return ThreatAssessment(
                    isThreat = true,
                    threatType = ThreatType.MAGIC_BYTE_MISMATCH,
                    severity = ThreatSeverity.CRITICAL,
                    description = "File is disguised as '.$ext' but contains Windows executable binary signatures (MZ..).",
                    originalTarget = ext,
                    disguisedTarget = "application/x-msdownload"
                )
            }

            // Check for ELF (Linux / Android native binary): 0x7F, 'E', 'L', 'F'
            if (bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte() &&
                bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
            ) {
                return ThreatAssessment(
                    isThreat = true,
                    threatType = ThreatType.MAGIC_BYTE_MISMATCH,
                    severity = ThreatSeverity.CRITICAL,
                    description = "File is disguised as '.$ext' but contains ELF native binary executable signatures.",
                    originalTarget = ext,
                    disguisedTarget = "application/x-executable"
                )
            }
        }

        return ThreatAssessment.clean()
    }

    /**
     * Computes SHA-256 fingerprint of a local file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hasDoubleExtensionTrap(filename: String): Boolean {
        val parts = filename.split('.')
        if (parts.size >= 3) {
            val lastExt = parts.last()
            val secondLastExt = parts[parts.size - 2]
            if (EXECUTABLE_EXTENSIONS.contains(lastExt) && MEDIA_EXTENSIONS.contains(secondLastExt)) {
                return true
            }
        }
        return false
    }

    private fun extractFilenameFromContentDisposition(cd: String?): String? {
        if (cd.isNullOrBlank()) return null
        val match = Regex("""filename\*?=['"]?(?:UTF-8'')?([^'";]+)['"]?""", RegexOption.IGNORE_CASE).find(cd)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun collectCandidateFilenames(
        uri: URI?,
        contentDisposition: String?,
        userReportedName: String?
    ): List<String> {
        val list = mutableListOf<String>()
        extractFilenameFromContentDisposition(contentDisposition)?.let { list.add(it) }
        userReportedName?.let { list.add(it) }

        val pathSegment = uri?.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        if (pathSegment != null) list.add(pathSegment)

        val query = uri?.query
        if (!query.isNullOrBlank()) {
            val queryParams = query.split('&')
            for (param in queryParams) {
                val pair = param.split('=', limit = 2)
                if (pair.size == 2 && (pair[0].equals("file", ignoreCase = true) || pair[0].equals("filename", ignoreCase = true))) {
                    list.add(pair[1])
                }
            }
        }
        return list.map { it.lowercase(Locale.ROOT) }
    }
}
