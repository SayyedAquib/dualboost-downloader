/**
 * DualBoost Cloud Slicer - Cloudflare Worker
 * Slices non-range web streams into HTTP 206 Partial Content byte ranges
 * for high-speed dual-network (Wi-Fi + 5G) download aggregation.
 *
 * Runs on Cloudflare's free tier (100,000 requests/day, zero cost).
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 1. Health check endpoint for in-app "Test Worker" button
    if (url.pathname === '/health') {
      return new Response(JSON.stringify({
        status: 'ok',
        service: 'DualBoost Cloud Slicer',
        version: '1.0.0',
        timestamp: Date.now()
      }), {
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*'
        }
      });
    }

    // 2. Extract parameters
    const targetUrl = url.searchParams.get('url');
    if (!targetUrl) {
      return new Response('DualBoost Cloud Slicer: Missing target URL parameter (?url=https://...)', {
        status: 400,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    // 3. Security check: validate secret key if configured in Worker environment or query
    const clientKey = request.headers.get('X-Slicer-Key') || url.searchParams.get('key');
    const configuredKey = env?.SECRET_KEY;
    if (configuredKey && clientKey !== configuredKey) {
      return new Response('Unauthorized: Invalid or missing Slicer Key', {
        status: 401,
        headers: { 'Access-Control-Allow-Origin': '*' }
      });
    }

    // 4. Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
          'Access-Control-Allow-Headers': 'Range, X-Slicer-Key, Content-Type'
        }
      });
    }

    const rangeHeader = request.headers.get('Range');

    // 5. If client did not request a Range, fetch origin directly
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

    // 6. Parse requested byte range (e.g., bytes=0-4194303)
    const rangeParts = rangeHeader.replace('bytes=', '').split('-');
    const rangeStart = parseInt(rangeParts[0], 10) || 0;
    const rangeEndRequested = rangeParts[1] ? parseInt(rangeParts[1], 10) : null;

    // 7. First, attempt to forward Range header to origin (in case origin supports ranges)
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

    // 8. Origin did NOT honor ranges (returned 200 OK or ignores ranges).
    // Perform on-the-fly stream slicing!
    const totalLengthHeader = forwardResponse.headers.get('Content-Length');
    const totalLength = totalLengthHeader ? parseInt(totalLengthHeader, 10) : -1;
    const rangeEnd = (rangeEndRequested !== null && rangeEndRequested >= rangeStart)
      ? rangeEndRequested
      : (totalLength > 0 ? totalLength - 1 : rangeStart + 4 * 1024 * 1024 - 1);

    const sliceLength = (rangeEnd - rangeStart) + 1;
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

