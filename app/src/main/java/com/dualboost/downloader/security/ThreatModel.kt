package com.dualboost.downloader.security

/**
 * Severity level of an identified security threat.
 */
enum class ThreatSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Categorization of malware, trojan, or deceptive payload vectors.
 */
enum class ThreatType {
    DOUBLE_EXTENSION,
    MALICIOUS_MIME,
    DISGUISED_EXECUTABLE,
    MAGIC_BYTE_MISMATCH,
    KNOWN_MALICIOUS_DOMAIN,
    CLOUD_FLAGGED_MALWARE
}

/**
 * Result assessment of threat inspection before or during download.
 */
data class ThreatAssessment(
    val isThreat: Boolean,
    val threatType: ThreatType? = null,
    val severity: ThreatSeverity = ThreatSeverity.LOW,
    val description: String = "",
    val originalTarget: String? = null,
    val disguisedTarget: String? = null
) {
    companion object {
        fun clean(): ThreatAssessment = ThreatAssessment(isThreat = false)
    }
}
