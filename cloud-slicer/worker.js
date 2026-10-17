/**
 * DualBoost Cloud Slicer - Cloudflare Worker (Hardened Production Build)
 * Slices non-range web streams into HTTP 206 Partial Content byte ranges
 * for high-speed dual-network (Wi-Fi + 5G) download aggregation.
 *
 * Security Enhancements:
 * - Anti-SSRF: Protocol & IP range validation (blocks localhost, private RFC 1918, link-local, cloud metadata).
 * - Fail-Closed Authentication: Rejects requests if SECRET_KEY is unconfigured to eliminate open proxy relay abuse.
 * - Constant-Time Key Verification: Cryptographic timing-safe token equality check.
 * - DoS Prevention: Enforces 16 MB maximum slice length ceiling per request.
 *
 * Runs on Cloudflare's free tier (100,000 requests/day, zero cost).
 */

/**
 * Constant-time string comparison to prevent timing side-channel attacks on secret keys.
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

/**
 * Validates target URL against SSRF (Server-Side Request Forgery) attacks.
 * Only permits valid public HTTP and HTTPS endpoints.
 */
function validateTargetUrl(rawUrl) {
  let parsed;
  try {
    parsed = new URL(rawUrl);
  } catch (_) {
    return { valid: false, error: 'Invalid URL syntax' };
  }

  // 1. Enforce strict HTTP/HTTPS protocol
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return { valid: false, error: 'Prohibited protocol: Only HTTP and HTTPS are permitted' };
  }

  const hostname = parsed.hostname.toLowerCase();

  // 2. Reject localhost and cloud metadata hostnames
  const blockedHostnames = [
    'localhost',
    'metadata.google.internal',
    'instance-data',
    '169.254.169.254'
  ];
  if (blockedHostnames.includes(hostname)) {
    return { valid: false, error: 'Access to internal or metadata host is strictly prohibited' };
  }

  // 3. Block private / loopback / link-local IP addresses (IPv4 & IPv6)
  const ipv4Match = hostname.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4Match) {
    const o1 = parseInt(ipv4Match[1], 10);
    const o2 = parseInt(ipv4Match[2], 10);

    // Loopback (127.0.0.0/8)
    if (o1 === 127) return { valid: false, error: 'Loopback IP addresses prohibited' };
    // RFC 1918 Private (10.0.0.0/8)
    if (o1 === 10) return { valid: false, error: 'Private RFC 1918 IP addresses prohibited' };
    // RFC 1918 Private (172.16.0.0/12: 172.16.x.x - 172.31.x.x)
    if (o1 === 172 && o2 >= 16 && o2 <= 31) return { valid: false, error: 'Private RFC 1918 IP addresses prohibited' };
    // RFC 1918 Private (192.168.0.0/16)
    if (o1 === 192 && o2 === 168) return { valid: false, error: 'Private RFC 1918 IP addresses prohibited' };
    // Link-local / Cloud metadata (169.254.0.0/16)
    if (o1 === 169 && o2 === 254) return { valid: false, error: 'Link-local and cloud metadata IPs prohibited' };
    // Broadcast / Zero (0.0.0.0/8)
    if (o1 === 0) return { valid: false, error: 'Invalid target IP address' };
  }

  // Check IPv6 loopback / private
  if (hostname === '[::1]' || hostname.startsWith('[fe80:') || hostname.startsWith('[fc00:') || hostname.startsWith('[fd00:')) {
    return { valid: false, error: 'Internal IPv6 addresses prohibited' };
  }

  return { valid: true, parsed };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 1. Health check endpoint for in-app "Test Worker" button
    if (url.pathname === '/health') {
      const isSecured = Boolean(env?.SECRET_KEY && env.SECRET_KEY.trim() !== '');
      return new Response(JSON.stringify({
        status: 'ok',
        service: 'DualBoost Cloud Slicer',
        version: '1.1.0-hardened',
        secured: isSecured,
        timestamp: Date.now()
      }), {
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        }
      });
    }

    // 2. Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
          'Access-Control-Allow-Headers': 'Range, X-Slicer-Key, Content-Type'
        }
      });
    }

    // 3. Extract and validate target URL parameter
    const targetUrl = url.searchParams.get('url');
    if (!targetUrl) {
      return new Response('DualBoost Cloud Slicer: Missing target URL parameter (?url=https://...)', {
        status: 400,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    // 4. Anti-SSRF validation
    const ssrfCheck = validateTargetUrl(targetUrl);
    if (!ssrfCheck.valid) {
      return new Response(`DualBoost Cloud Slicer Security Alert: ${ssrfCheck.error}`, {
        status: 403,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    // 5. Fail-Closed Authentication Check: Prevent open proxy relay abuse
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

    const clientKey = request.headers.get('X-Slicer-Key') || url.searchParams.get('key');
    if (!clientKey || !timingSafeEqual(clientKey, configuredKey)) {
      return new Response('Unauthorized: Invalid or missing Slicer Key', {
        status: 401,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    const rangeHeader = request.headers.get('Range');

    // 6. If client did not request a Range, fetch origin directly with Desktop headers
    if (!rangeHeader || !rangeHeader.startsWith('bytes=')) {
      const response = await fetch(targetUrl, {
        method: request.method,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
        }
      });
      const newHeaders = new Headers(response.headers);
      newHeaders.set('Access-Control-Allow-Origin', '*');
      newHeaders.set('Accept-Ranges', 'bytes');
      return new Response(response.body, {
        status: response.status,
        headers: newHeaders
      });
    }

    // 7. Parse requested byte range (e.g., bytes=0-4194303)
    const rangeParts = rangeHeader.replace('bytes=', '').split('-');
    const rangeStart = parseInt(rangeParts[0], 10) || 0;
    const rangeEndRequested = rangeParts[1] ? parseInt(rangeParts[1], 10) : null;

    // 8. First, attempt to forward Range header to origin (in case origin supports ranges)
    const forwardResponse = await fetch(targetUrl, {
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Range': rangeHeader
      }
    });

    // If origin natively honored Range with 206, pass it through directly!
    if (forwardResponse.status === 206) {
      const newHeaders = new Headers(forwardResponse.headers);
      newHeaders.set('Access-Control-Allow-Origin', '*');
      newHeaders.set('Accept-Ranges', 'bytes');
      return new Response(forwardResponse.body, {
        status: 206,
        headers: newHeaders
      });
    }

    // 9. Origin did NOT honor ranges (returned 200 OK or ignores ranges).
    // Perform on-the-fly stream slicing with DoS bounds protection!
    const totalLengthHeader = forwardResponse.headers.get('Content-Length');
    const totalLength = totalLengthHeader ? parseInt(totalLengthHeader, 10) : -1;

    // Security: Cap single slice size to 16 MB maximum to prevent unbounded edge streaming DoS
    const MAX_SLICE_SIZE = 16 * 1024 * 1024;
    const requestedSlice = (rangeEndRequested !== null && rangeEndRequested >= rangeStart)
      ? (rangeEndRequested - rangeStart + 1)
      : (totalLength > 0 ? (totalLength - rangeStart) : 4 * 1024 * 1024);
    const boundedSlice = Math.min(Math.max(1, requestedSlice), MAX_SLICE_SIZE);
    const rangeEnd = rangeStart + boundedSlice - 1;

    const sliceLength = boundedSlice;
    const bodyReader = forwardResponse.body.getReader();

    // Stream transformation that skips rangeStart bytes and takes sliceLength bytes
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

          // Still skipping leading bytes?
          if (bytesSkipped < rangeStart) {
            const needToSkip = rangeStart - bytesSkipped;
            if (chunkSize <= needToSkip) {
              bytesSkipped += chunkSize;
              continue;
            } else {
              // Chunk covers the boundary
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
            // Already past rangeStart, emit bytes up to sliceLength
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
    responseHeaders.set('Content-Range', `bytes ${rangeStart}-${rangeEnd}/${totalLength > 0 ? totalLength : '*'}`);
    responseHeaders.set('Content-Length', sliceLength.toString());
    responseHeaders.set('Access-Control-Allow-Origin', '*');

    return new Response(stream, {
      status: 206,
      statusText: 'Partial Content',
      headers: responseHeaders
    });
  }
};
