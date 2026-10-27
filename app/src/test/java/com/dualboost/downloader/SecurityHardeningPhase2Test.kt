package com.dualboost.downloader

import com.dualboost.downloader.data.SlicerPreferences
import org.junit.Assert.*
import org.junit.Test

class SecurityHardeningPhase2Test {

    @Test
    fun testWorkerScript_EnforcesAntiSsrfRedirectManualAndIpValidation() {
        val script = SlicerPreferences.WORKER_SCRIPT_SOURCE

        // SEC-AUDIT M-2: Must forbid following redirects to prevent SSRF bypass
        assertTrue("Worker script must set redirect: 'manual'", script.contains("redirect: 'manual'"))
        assertTrue("Worker script must reject redirects with security alert", script.contains("Upstream redirects prohibited"))

        // SEC-AUDIT M-2: Must check IPv4-mapped IPv6 literals
        assertTrue("Worker script must check IPv4-mapped IPv6 addresses", script.contains("::ffff:"))
        assertTrue("Worker script must inspect Carrier-Grade NAT and metadata IPs", script.contains("Carrier-Grade NAT"))
        assertTrue("Worker script must block Benchmark network IPs", script.contains("Benchmark network"))

        // SEC-AUDIT M-2: DNS rebinding check must fail closed
        assertTrue("Worker script must fail closed on DNS resolution failure", script.contains("Fail-closed: DNS resolution"))
    }

    @Test
    fun testWorkerScript_RefusesNonRangeOriginToPreventAmplification() {
        val script = SlicerPreferences.WORKER_SCRIPT_SOURCE

        // SEC-AUDIT M-6: Must NOT do unbounded skipping of bytes from non-range origins
        assertFalse("Worker script must not contain O(n^2) skipping loop", script.contains("bytesSkipped < rangeStart"))
        assertTrue("Worker script must reject non-range upstream with HTTP 501", script.contains("status: 501"))
        assertTrue("Worker script must set X-Slicer-Error header", script.contains("X-Slicer-Error"))
    }

    @Test
    fun testWorkerScript_HealthCheckDoesNotLeakSecretState() {
        val script = SlicerPreferences.WORKER_SCRIPT_SOURCE

        // SEC-AUDIT M-1: Health endpoint should not disclose whether SECRET_KEY is configured
        assertFalse("Health check must not disclose secured state", script.contains("secured: isSecured"))
        assertTrue("Health check must declare hardened version", script.contains("1.2.1-hardened"))
    }
}

