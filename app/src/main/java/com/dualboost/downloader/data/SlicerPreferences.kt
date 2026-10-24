package com.dualboost.downloader.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object SlicerPreferences {

    private const val TAG = "SlicerPreferences"
    private const val PREFS_NAME = "dualboost_settings_encrypted"
    private const val LEGACY_PREFS_NAME = "dualboost_settings"
    private const val KEY_SLICER_URL = "cloud_slicer_url"
    private const val KEY_SLICER_SECRET = "cloud_slicer_secret"

    // SEC-AUDIT C-1: Android Keystore-backed AES-256 encrypted credential storage
    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            // One-time migration from legacy plaintext SharedPreferences
            migrateLegacyPrefs(context, encPrefs)
            encPrefs
        } catch (e: Exception) {
            // Fallback to standard SharedPreferences if Keystore is broken (rare)
            Log.e(TAG, "EncryptedSharedPreferences init failed, falling back: ${e.message}")
            context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun migrateLegacyPrefs(context: Context, encPrefs: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyUrl = legacy.getString(KEY_SLICER_URL, null)
        val legacySecret = legacy.getString(KEY_SLICER_SECRET, null)
        if (legacyUrl != null || legacySecret != null) {
            encPrefs.edit().apply {
                if (legacyUrl != null && encPrefs.getString(KEY_SLICER_URL, null) == null) {
                    putString(KEY_SLICER_URL, legacyUrl)
                }
                if (legacySecret != null && encPrefs.getString(KEY_SLICER_SECRET, null) == null) {
                    putString(KEY_SLICER_SECRET, legacySecret)
                }
            }.apply()
            // Wipe legacy plaintext credentials permanently
            legacy.edit().clear().apply()
        }
    }

    val DEFAULT_SLICER_URL: String
        get() = try {
            com.dualboost.downloader.BuildConfig.DEFAULT_SLICER_URL.trim()
        } catch (_: Throwable) {
            ""
        }

    val DEFAULT_SLICER_SECRET: String
        get() = try {
            com.dualboost.downloader.BuildConfig.DEFAULT_SLICER_SECRET.trim()
        } catch (_: Throwable) {
            ""
        }

    fun getSlicerUrl(context: Context): String {
        val stored = getPrefs(context).getString(KEY_SLICER_URL, null)
        if (stored != null) return stored.trim()
        return DEFAULT_SLICER_URL
    }

    fun setSlicerUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_SLICER_URL, url.trim()).apply()
    }

    fun getSlicerSecret(context: Context): String {
        val stored = getPrefs(context).getString(KEY_SLICER_SECRET, null)
        if (stored != null) return stored.trim()
        return DEFAULT_SLICER_SECRET
    }

    fun setSlicerSecret(context: Context, secret: String) {
        getPrefs(context).edit().putString(KEY_SLICER_SECRET, secret.trim()).apply()
    }

    fun buildSlicedUrl(slicerBaseUrl: String, targetUrl: String, @Suppress("UNUSED_PARAMETER") secretKey: String? = null): String {
        val cleanBase = slicerBaseUrl.trim().trimEnd('/')
        val encodedUrl = URLEncoder.encode(targetUrl.trim(), StandardCharsets.UTF_8.name())
        val endpoint = if (cleanBase.endsWith("/slice")) cleanBase else "$cleanBase/slice"
        return "$endpoint?url=$encodedUrl"
    }

    const val WORKER_SCRIPT_SOURCE = """/**
 * DualBoost Cloud Slicer - Cloudflare Worker (Hardened Production Build)
 * Slices non-range web streams into HTTP 206 Partial Content byte ranges
 * for high-speed dual-network (Wi-Fi + 5G) download aggregation.
 *
 * Security: Anti-SSRF, DNS Rebinding Defense, Fail-Closed Header Auth, 16MB DoS Bounds.
 */

function timingSafeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const enc = new TextEncoder();
  const bufA = enc.encode(a);
  const bufB = enc.encode(b);
  if (bufA.byteLength !== bufB.byteLength) return false;
  let diff = 0;
  for (let i = 0; i < bufA.byteLength; i++) {
    diff |= bufA[i] ^ bufB[i];
  }
  return diff === 0;
}

function isPrivateIpv4(o1, o2) {
  if (o1 === 127) return { isPrivate: true, reason: 'Loopback IP addresses prohibited' };
  if (o1 === 10) return { isPrivate: true, reason: 'Private RFC 1918 IP addresses prohibited' };
  if (o1 === 172 && o2 >= 16 && o2 <= 31) return { isPrivate: true, reason: 'Private RFC 1918 IP addresses prohibited' };
  if (o1 === 192 && o2 === 168) return { isPrivate: true, reason: 'Private RFC 1918 IP addresses prohibited' };
  if (o1 === 169 && o2 === 254) return { isPrivate: true, reason: 'Link-local and cloud metadata IPs prohibited' };
  if (o1 === 100 && o2 >= 64 && o2 <= 127) return { isPrivate: true, reason: 'Carrier-Grade NAT IPs prohibited' };
  if (o1 === 0) return { isPrivate: true, reason: 'Invalid target IP address' };
  return { isPrivate: false };
}

function validateTargetUrl(rawUrl) {
  let parsed;
  try {
    parsed = new URL(rawUrl);
  } catch (_) {
    return { valid: false, error: 'Invalid URL syntax' };
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return { valid: false, error: 'Prohibited protocol: Only HTTP and HTTPS are permitted' };
  }

  const port = parsed.port;
  if (port && port !== '80' && port !== '443') {
    return { valid: false, error: 'Prohibited port (' + port + '): Only standard web ports (80, 443) are permitted' };
  }

  const hostname = parsed.hostname.toLowerCase();
  const blockedHostnames = ['localhost', 'metadata.google.internal', 'instance-data', '169.254.169.254'];
  if (blockedHostnames.includes(hostname)) {
    return { valid: false, error: 'Access to internal or metadata host is strictly prohibited' };
  }

  const blockedDynamicDns = ['nip.io', 'sslip.io', 'xip.io', 'localtest.me', 'vcap.me', 'lvh.me'];
  for (const service of blockedDynamicDns) {
    if (hostname === service || hostname.endsWith('.' + service)) {
      return { valid: false, error: 'Dynamic DNS service (' + service + ') is prohibited' };
    }
  }

  const blockedTlds = ['.local', '.internal', '.lan', '.home', '.corp', '.intra'];
  for (const tld of blockedTlds) {
    if (hostname.endsWith(tld)) {
      return { valid: false, error: 'Internal TLD (' + tld + ') is prohibited' };
    }
  }

  const ipv4Match = hostname.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4Match) {
    const o1 = parseInt(ipv4Match[1], 10);
    const o2 = parseInt(ipv4Match[2], 10);
    const check = isPrivateIpv4(o1, o2);
    if (check.isPrivate) {
      return { valid: false, error: check.reason };
    }
  }

  if (hostname === '[::1]' || hostname.startsWith('[fe80:') || hostname.startsWith('[fc00:') || hostname.startsWith('[fd00:')) {
    return { valid: false, error: 'Internal IPv6 addresses prohibited' };
  }

  return { valid: true, parsed };
}

async function verifyDnsResolution(hostname) {
  if (/^(\d{1,3}\.){3}\d{1,3}$/.test(hostname) || hostname.startsWith('[')) {
    return { valid: true };
  }
  try {
    const [resA, resAAAA] = await Promise.all([
      fetch('https://cloudflare-dns.com/dns-query?name=' + encodeURIComponent(hostname) + '&type=A', { headers: { 'Accept': 'application/dns-json' } }),
      fetch('https://cloudflare-dns.com/dns-query?name=' + encodeURIComponent(hostname) + '&type=AAAA', { headers: { 'Accept': 'application/dns-json' } })
    ]);

    if (resA.ok) {
      const data = await resA.json();
      if (data.Answer && Array.isArray(data.Answer)) {
        for (const ans of data.Answer) {
          if (ans.type === 1 && ans.data) {
            const parts = ans.data.split('.').map(Number);
            if (parts.length === 4) {
              const check = isPrivateIpv4(parts[0], parts[1]);
              if (check.isPrivate) {
                return { valid: false, error: 'DNS Rebinding: Hostname resolves to prohibited private IPv4 (' + ans.data + ')' };
              }
            }
          }
        }
      }
    }

    if (resAAAA.ok) {
      const data = await resAAAA.json();
      if (data.Answer && Array.isArray(data.Answer)) {
        for (const ans of data.Answer) {
          if (ans.type === 28 && ans.data) {
            const ip6 = ans.data.toLowerCase().trim();
            if (
              ip6 === '::1' ||
              ip6 === '0:0:0:0:0:0:0:1' ||
              ip6.startsWith('fe80:') ||
              ip6.startsWith('fc00:') ||
              ip6.startsWith('fd00:') ||
              ip6.startsWith('::ffff:127.') ||
              ip6.startsWith('::ffff:10.') ||
              ip6.startsWith('::ffff:192.168.')
            ) {
              return { valid: false, error: 'DNS Rebinding: Hostname resolves to prohibited private IPv6 (' + ans.data + ')' };
            }
          }
        }
      }
    }
  } catch (_) {}
  return { valid: true };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === '/health') {
      const isSecured = Boolean(env?.SECRET_KEY && env.SECRET_KEY.trim() !== '');
      return new Response(JSON.stringify({
        status: 'ok',
        service: 'DualBoost Cloud Slicer',
        version: '1.2.0-hardened',
        secured: isSecured,
        timestamp: Date.now()
      }), {
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
          'Access-Control-Allow-Headers': 'Range, X-Slicer-Key, Content-Type'
        }
      });
    }

    const targetUrl = url.searchParams.get('url');
    if (!targetUrl) {
      return new Response('DualBoost Cloud Slicer: Missing target URL parameter (?url=https://...)', {
        status: 400,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    const ssrfCheck = validateTargetUrl(targetUrl);
    if (!ssrfCheck.valid) {
      return new Response(`DualBoost Cloud Slicer Security Alert: ${'$'}{ssrfCheck.error}`, {
        status: 403,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    const dnsCheck = await verifyDnsResolution(ssrfCheck.parsed.hostname);
    if (!dnsCheck.valid) {
      return new Response(`DualBoost Cloud Slicer Security Alert: ${'$'}{dnsCheck.error}`, {
        status: 403,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    const resolvedHostname = ssrfCheck.parsed.hostname;

    const configuredKey = env?.SECRET_KEY;
    if (!configuredKey || configuredKey.trim() === '') {
      return new Response(JSON.stringify({
        error: 'Slicer Configuration Error',
        message: 'SECRET_KEY is not configured in Cloudflare Worker environment variables. Set SECRET_KEY in Cloudflare Dashboard to enable secure slicing.'
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    const clientKey = request.headers.get('X-Slicer-Key');
    if (!clientKey || !timingSafeEqual(clientKey, configuredKey)) {
      return new Response('Unauthorized: Missing or invalid X-Slicer-Key header', {
        status: 401,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    const rangeHeader = request.headers.get('Range');
    if (!rangeHeader || !rangeHeader.startsWith('bytes=')) {
      const response = await fetch(targetUrl, {
        method: request.method,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
        },
        cf: {
          resolveOverride: resolvedHostname
        }
      });
      const newHeaders = new Headers(response.headers);
      newHeaders.set('Access-Control-Allow-Origin', '*');
      newHeaders.set('Accept-Ranges', 'bytes');
      return new Response(response.body, { status: response.status, headers: newHeaders });
    }

    const rangeParts = rangeHeader.replace('bytes=', '').split('-');
    const rangeStart = parseInt(rangeParts[0], 10) || 0;
    const rangeEndRequested = rangeParts[1] ? parseInt(rangeParts[1], 10) : null;

    const forwardResponse = await fetch(targetUrl, {
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Range': rangeHeader
      },
      cf: {
        resolveOverride: resolvedHostname
      }
    });

    if (forwardResponse.status === 206) {
      const newHeaders = new Headers(forwardResponse.headers);
      newHeaders.set('Access-Control-Allow-Origin', '*');
      newHeaders.set('Accept-Ranges', 'bytes');
      return new Response(forwardResponse.body, { status: 206, headers: newHeaders });
    }

    const totalLengthHeader = forwardResponse.headers.get('Content-Length');
    const totalLength = totalLengthHeader ? parseInt(totalLengthHeader, 10) : -1;

    const MAX_SLICE_SIZE = 16 * 1024 * 1024;
    const requestedSlice = (rangeEndRequested !== null && rangeEndRequested >= rangeStart)
      ? (rangeEndRequested - rangeStart + 1)
      : (totalLength > 0 ? (totalLength - rangeStart) : 4 * 1024 * 1024);
    const boundedSlice = Math.min(Math.max(1, requestedSlice), MAX_SLICE_SIZE);
    const rangeEnd = rangeStart + boundedSlice - 1;
    const sliceLength = boundedSlice;

    const bodyReader = forwardResponse.body.getReader();
    let bytesSkipped = 0;
    let bytesYielded = 0;

    const stream = new ReadableStream({
      async pull(controller) {
        while (bytesYielded < sliceLength) {
          const { done, value } = await bodyReader.read();
          if (done) {
            controller.close();
            return;
          }
          const chunk = value;
          const chunkSize = chunk.length;
          if (bytesSkipped < rangeStart) {
            const needToSkip = rangeStart - bytesSkipped;
            if (chunkSize <= needToSkip) {
              bytesSkipped += chunkSize;
              continue;
            } else {
              const sliceStart = needToSkip;
              bytesSkipped = rangeStart;
              const remainingToTake = sliceLength - bytesYielded;
              const availableInChunk = chunkSize - sliceStart;
              const take = Math.min(availableInChunk, remainingToTake);
              const subChunk = chunk.subarray(sliceStart, sliceStart + take);
              controller.enqueue(subChunk);
              bytesYielded += take;
              if (bytesYielded >= sliceLength) {
                controller.close();
                try { await bodyReader.cancel(); } catch (_) {}
                return;
              }
            }
          } else {
            const remainingToTake = sliceLength - bytesYielded;
            const take = Math.min(chunkSize, remainingToTake);
            const subChunk = chunk.subarray(0, take);
            controller.enqueue(subChunk);
            bytesYielded += take;
            if (bytesYielded >= sliceLength) {
              controller.close();
              try { await bodyReader.cancel(); } catch (_) {}
              return;
            }
          }
        }
        controller.close();
      },
      async cancel() {
        try { await bodyReader.cancel(); } catch (_) {}
      }
    });

    const responseHeaders = new Headers();
    responseHeaders.set('Content-Type', forwardResponse.headers.get('Content-Type') || 'application/octet-stream');
    responseHeaders.set('Accept-Ranges', 'bytes');
    responseHeaders.set('Content-Range', `bytes ${'$'}{rangeStart}-${'$'}{rangeEnd}/${'$'}{totalLength > 0 ? totalLength : '*'}`);
    responseHeaders.set('Content-Length', sliceLength.toString());
    responseHeaders.set('Access-Control-Allow-Origin', '*');

    return new Response(stream, { status: 206, statusText: 'Partial Content', headers: responseHeaders });
  }
};"""
}
