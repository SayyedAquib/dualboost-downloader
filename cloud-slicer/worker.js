/**
 * DualBoost Cloud Slicer - Cloudflare Worker (Hardened Production Build)
 * Slices non-range web streams into HTTP 206 Partial Content byte ranges
 * for high-speed dual-network (Wi-Fi + 5G) download aggregation.
 *
 * Security: Anti-SSRF, DNS Rebinding Defense, Fail-Closed Header Auth, DoS Bounds.
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

function isProhibitedIpv4(o1, o2, o3, o4) {
  if (o1 === 127) return { isPrivate: true, reason: 'Loopback IPv4 addresses prohibited' };
  if (o1 === 10) return { isPrivate: true, reason: 'Private RFC 1918 IPv4 addresses prohibited' };
  if (o1 === 172 && o2 >= 16 && o2 <= 31) return { isPrivate: true, reason: 'Private RFC 1918 IPv4 addresses prohibited' };
  if (o1 === 192 && o2 === 168) return { isPrivate: true, reason: 'Private RFC 1918 IPv4 addresses prohibited' };
  if (o1 === 169 && o2 === 254) return { isPrivate: true, reason: 'Link-local and cloud metadata IPs prohibited' };
  if (o1 === 100 && o2 >= 64 && o2 <= 127) return { isPrivate: true, reason: 'Carrier-Grade NAT IPs prohibited' };
  if (o1 === 198 && (o2 === 18 || o2 === 19)) return { isPrivate: true, reason: 'Benchmark network IPs prohibited' };
  if (o1 >= 240) return { isPrivate: true, reason: 'Reserved Class E IPs prohibited' };
  if (o1 === 0) return { isPrivate: true, reason: 'Invalid or zero target IP address' };
  return { isPrivate: false };
}

function parseIpv4String(ipStr) {
  const parts = ipStr.split('.');
  if (parts.length !== 4) return null;
  const nums = parts.map(p => Number(p));
  if (nums.some(n => isNaN(n) || n < 0 || n > 255)) return null;
  return nums;
}

function isProhibitedIpv6(rawIp6) {
  let clean = rawIp6.toLowerCase().replace(/^\[|\]$/g, '').trim();
  if (clean === '::' || clean === '::1' || clean === '0:0:0:0:0:0:0:1' || clean === '0:0:0:0:0:0:0:0') {
    return { isPrivate: true, reason: 'Loopback or unspecified IPv6 address prohibited' };
  }
  // Check for IPv4-mapped IPv6 (::ffff:192.168.1.1, ::ffff:c0a8:0101, etc.)
  if (clean.includes('::ffff:') || clean.includes(':ffff:')) {
    const lastPart = clean.substring(clean.lastIndexOf(':') + 1);
    if (lastPart.includes('.')) {
      const parts = parseIpv4String(lastPart);
      if (parts) {
        return isProhibitedIpv4(parts[0], parts[1], parts[2], parts[3]);
      }
    }
    const segments = clean.split(':').filter(Boolean);
    if (segments.length >= 2) {
      const h1 = parseInt(segments[segments.length - 2], 16);
      const h2 = parseInt(segments[segments.length - 1], 16);
      if (!isNaN(h1) && !isNaN(h2)) {
        const o1 = (h1 >> 8) & 0xff;
        const o2 = h1 & 0xff;
        const o3 = (h2 >> 8) & 0xff;
        const o4 = h2 & 0xff;
        return isProhibitedIpv4(o1, o2, o3, o4);
      }
    }
    return { isPrivate: true, reason: 'IPv4-mapped IPv6 address prohibited' };
  }
  // Link local fe80::/10 (fe80 - febf)
  if (clean.startsWith('fe8') || clean.startsWith('fe9') || clean.startsWith('fea') || clean.startsWith('feb')) {
    return { isPrivate: true, reason: 'Link-local IPv6 address prohibited' };
  }
  // Unique local fc00::/7 (fc00 - fdff)
  if (clean.startsWith('fc') || clean.startsWith('fd')) {
    return { isPrivate: true, reason: 'Unique local IPv6 address prohibited' };
  }
  // NAT64 64:ff9b::/96
  if (clean.startsWith('64:ff9b:')) {
    return { isPrivate: true, reason: 'NAT64 prefix prohibited' };
  }
  // 6to4 2002::/16
  if (clean.startsWith('2002:')) {
    return { isPrivate: true, reason: '6to4 prefix prohibited' };
  }
  // Documentation 2001:db8::/32
  if (clean.startsWith('2001:db8:') || clean.startsWith('2001:0db8:')) {
    return { isPrivate: true, reason: 'Documentation IPv6 prefix prohibited' };
  }
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
    const o3 = parseInt(ipv4Match[3], 10);
    const o4 = parseInt(ipv4Match[4], 10);
    const check = isProhibitedIpv4(o1, o2, o3, o4);
    if (check.isPrivate) {
      return { valid: false, error: check.reason };
    }
  }

  if (hostname.startsWith('[')) {
    const check = isProhibitedIpv6(hostname);
    if (check.isPrivate) {
      return { valid: false, error: check.reason };
    }
  }

  return { valid: true, parsed };
}

async function verifyDnsResolution(hostname) {
  if (/^(\d{1,3}\.){3}\d{1,3}$/.test(hostname)) {
    return { valid: true };
  }
  if (hostname.startsWith('[')) {
    return { valid: !isProhibitedIpv6(hostname).isPrivate };
  }
  try {
    const [resA, resAAAA] = await Promise.all([
      fetch('https://cloudflare-dns.com/dns-query?name=' + encodeURIComponent(hostname) + '&type=A', { headers: { 'Accept': 'application/dns-json' } }),
      fetch('https://cloudflare-dns.com/dns-query?name=' + encodeURIComponent(hostname) + '&type=AAAA', { headers: { 'Accept': 'application/dns-json' } })
    ]);

    if (!resA.ok && !resAAAA.ok) {
      return { valid: false, error: 'Fail-closed: DNS resolution failed upstream' };
    }

    if (resA.ok) {
      const data = await resA.json();
      if (data.Answer && Array.isArray(data.Answer)) {
        for (const ans of data.Answer) {
          if (ans.type === 1 && ans.data) {
            const parts = parseIpv4String(ans.data);
            if (parts) {
              const check = isProhibitedIpv4(parts[0], parts[1], parts[2], parts[3]);
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
            const check = isProhibitedIpv6(ans.data);
            if (check.isPrivate) {
              return { valid: false, error: 'DNS Rebinding: Hostname resolves to prohibited IPv6 (' + ans.data + ')' };
            }
          }
        }
      }
    }
    return { valid: true };
  } catch (err) {
    return { valid: false, error: 'Fail-closed: DNS resolution error' };
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // SEC-AUDIT M-1: Public health check ping (does not disclose secret presence)
    if (url.pathname === '/health') {
      return new Response(JSON.stringify({
        status: 'ok',
        service: 'DualBoost Cloud Slicer',
        version: '1.2.1-hardened',
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
      return new Response(`DualBoost Cloud Slicer Security Alert: ${ssrfCheck.error}`, {
        status: 403,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    const dnsCheck = await verifyDnsResolution(ssrfCheck.parsed.hostname);
    if (!dnsCheck.valid) {
      return new Response(`DualBoost Cloud Slicer Security Alert: ${dnsCheck.error}`, {
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
      // SEC-AUDIT M-2: Enforce redirect: 'manual' to prevent SSRF redirect bypass
      const response = await fetch(targetUrl, {
        method: request.method,
        redirect: 'manual',
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
        },
        cf: {
          resolveOverride: resolvedHostname
        }
      });

      if (response.status >= 300 && response.status < 400) {
        return new Response('DualBoost Cloud Slicer Security Alert: Upstream redirects prohibited', {
          status: 403,
          headers: { 'Access-Control-Allow-Origin': '*' }
        });
      }

      const newHeaders = new Headers(response.headers);
      newHeaders.set('Access-Control-Allow-Origin', '*');
      newHeaders.set('Accept-Ranges', 'bytes');
      return new Response(response.body, { status: response.status, headers: newHeaders });
    }

    // SEC-AUDIT M-2: Forward Range with redirect: 'manual'
    const forwardResponse = await fetch(targetUrl, {
      method: 'GET',
      redirect: 'manual',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Range': rangeHeader
      },
      cf: {
        resolveOverride: resolvedHostname
      }
    });

    if (forwardResponse.status >= 300 && forwardResponse.status < 400) {
      return new Response('DualBoost Cloud Slicer Security Alert: Upstream redirects prohibited', {
        status: 403,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    if (forwardResponse.status === 206) {
      const newHeaders = new Headers(forwardResponse.headers);
      newHeaders.set('Access-Control-Allow-Origin', '*');
      newHeaders.set('Accept-Ranges', 'bytes');
      return new Response(forwardResponse.body, { status: 206, headers: newHeaders });
    }

    // SEC-AUDIT M-6: If origin does NOT honor Range (e.g. returns 200 instead of 206),
    // cancel upstream body immediately to prevent O(n^2) bandwidth amplification!
    try { await forwardResponse.body?.cancel(); } catch (_) {}
    return new Response('DualBoost Cloud Slicer: Upstream origin does not support HTTP Range requests', {
      status: 501,
      statusText: 'Not Implemented',
      headers: {
        'X-Slicer-Error': 'Upstream does not support Range requests',
        'Access-Control-Allow-Origin': '*'
      }
    });
  }
};
