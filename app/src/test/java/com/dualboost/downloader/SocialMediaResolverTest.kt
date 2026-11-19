package com.dualboost.downloader

import com.dualboost.downloader.social.MediaQuality
import com.dualboost.downloader.social.SocialMediaResolver
import com.dualboost.downloader.social.SocialPlatform
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SocialMediaResolverTest {

    private lateinit var mockServer1: MockWebServer
    private lateinit var mockServer2: MockWebServer
    private lateinit var okHttpClient: OkHttpClient

    private val sampleInvidiousJson = """
        {
          "title": "Rick Astley - Never Gonna Give You Up",
          "videoId": "dQw4w9WgXcQ",
          "lengthSeconds": 213,
          "videoThumbnails": [
            { "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" }
          ],
          "adaptiveFormats": [
            {
              "url": "https://rr4---sn-ab5sznzl.googlevideo.com/videoplayback?audio_128k",
              "type": "audio/mp4; codecs=\"mp4a.40.2\"",
              "bitrate": "131401",
              "container": "m4a"
            },
            {
              "url": "https://rr4---sn-ab5sznzl.googlevideo.com/videoplayback?video_720p",
              "type": "video/mp4; codecs=\"avc1.4d401f\"",
              "qualityLabel": "720p",
              "bitrate": "1500000",
              "container": "mp4"
            },
            {
              "url": "https://rr4---sn-ab5sznzl.googlevideo.com/videoplayback?video_1080p",
              "type": "video/mp4; codecs=\"avc1.640028\"",
              "qualityLabel": "1080p",
              "bitrate": "3500000",
              "container": "mp4"
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockServer1 = MockWebServer()
        mockServer1.start()

        mockServer2 = MockWebServer()
        mockServer2.start()

        okHttpClient = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        mockServer1.shutdown()
        mockServer2.shutdown()
    }

    @Test
    fun resolveYouTube_viaInvidious_returnsMaxQualityStreamAndMetadata() = runBlocking {
        mockServer1.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sampleInvidiousJson)
        )

        val resolver = SocialMediaResolver(
            client = okHttpClient,
            instances = emptyList(),
            youtubeInstances = listOf(mockServer1.url("/").toString())
        )

        val result = resolver.resolve(
            url = "https://youtu.be/dQw4w9WgXcQ?si=abcdef123456",
            quality = MediaQuality.MAX
        )

        assertTrue("Invidious resolution should succeed", result.isSuccess)
        val asset = result.getOrNull()
        assertNotNull(asset)
        assertEquals(SocialPlatform.YOUTUBE, asset!!.platform)
        assertEquals("https://rr4---sn-ab5sznzl.googlevideo.com/videoplayback?video_1080p", asset.streamUrl)
        assertEquals("Rick Astley - Never Gonna Give You Up_1080p.mp4", asset.suggestedFilename)
        assertEquals(213L, asset.durationSeconds)
        assertFalse(asset.isAudioOnly)

        val recordedRequest = mockServer1.takeRequest()
        assertEquals("/api/v1/videos/dQw4w9WgXcQ", recordedRequest.path)
    }

    @Test
    fun resolveYouTube_viaInvidious_720p_returns720pStream() = runBlocking {
        mockServer1.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sampleInvidiousJson)
        )

        val resolver = SocialMediaResolver(
            client = okHttpClient,
            instances = emptyList(),
            youtubeInstances = listOf(mockServer1.url("/").toString())
        )

        val result = resolver.resolve(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            quality = MediaQuality.HD_720
        )

        assertTrue(result.isSuccess)
        val asset = result.getOrNull()!!
        assertEquals("https://rr4---sn-ab5sznzl.googlevideo.com/videoplayback?video_720p", asset.streamUrl)
        assertEquals("Rick Astley - Never Gonna Give You Up_720p.mp4", asset.suggestedFilename)
    }

    @Test
    fun resolveYouTube_viaInvidious_audioOnly_returnsM4aAudioStream() = runBlocking {
        mockServer1.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sampleInvidiousJson)
        )

        val resolver = SocialMediaResolver(
            client = okHttpClient,
            instances = emptyList(),
            youtubeInstances = listOf(mockServer1.url("/").toString())
        )

        val result = resolver.resolve(
            url = "https://youtu.be/dQw4w9WgXcQ",
            quality = MediaQuality.AUDIO_ONLY
        )

        assertTrue(result.isSuccess)
        val asset = result.getOrNull()!!
        assertEquals("https://rr4---sn-ab5sznzl.googlevideo.com/videoplayback?audio_128k", asset.streamUrl)
        assertEquals("Rick Astley - Never Gonna Give You Up.m4a", asset.suggestedFilename)
        assertTrue(asset.isAudioOnly)
    }

    @Test
    fun resolveYouTube_fallbackToCobalt_whenInvidiousFails() = runBlocking {
        // Server 1 (Invidious) fails with 500
        mockServer1.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        // Server 2 (Cobalt instance) succeeds
        val cobaltJson = """
            {
              "status": "redirect",
              "url": "https://rr4---sn-ab5sznzl.googlevideo.com/videoplayback?expire=12345",
              "filename": "Rick_Astley_Never_Gonna_Give_You_Up.mp4"
            }
        """.trimIndent()

        mockServer2.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(cobaltJson)
        )

        val resolver = SocialMediaResolver(
            client = okHttpClient,
            instances = listOf(mockServer2.url("/").toString()),
            youtubeInstances = listOf(mockServer1.url("/").toString())
        )

        val result = resolver.resolve(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            quality = MediaQuality.MAX
        )

        assertTrue("Fallback to Cobalt should succeed", result.isSuccess)
        val asset = result.getOrNull()!!
        assertEquals(SocialPlatform.YOUTUBE, asset.platform)
        assertEquals("https://rr4---sn-ab5sznzl.googlevideo.com/videoplayback?expire=12345", asset.streamUrl)
        assertEquals("Rick_Astley_Never_Gonna_Give_You_Up.mp4", asset.suggestedFilename)
    }

    @Test
    fun resolveAudioOnly_requestsMp3FormatAndSetsAudioFlag() = runBlocking {
        val jsonResponse = """
            {
              "status": "stream",
              "url": "https://stream.audio.cdn.com/track.mp3",
              "filename": "lofi_beats.mp3"
            }
        """.trimIndent()

        mockServer1.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val resolver = SocialMediaResolver(
            client = okHttpClient,
            instances = listOf(mockServer1.url("/").toString()),
            youtubeInstances = emptyList()
        )

        val result = resolver.resolve(
            url = "https://soundcloud.com/artist/lofi-beats",
            quality = MediaQuality.AUDIO_ONLY
        )

        assertTrue(result.isSuccess)
        val asset = result.getOrNull()!!
        assertTrue(asset.isAudioOnly)
        assertEquals(SocialPlatform.SOUNDCLOUD, asset.platform)
        assertEquals("lofi_beats.mp3", asset.suggestedFilename)

        val recordedRequest = mockServer1.takeRequest()
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("downloadMode") && body.contains("audio"))
    }

    @Test
    fun multiInstanceFailover_whenPrimaryFails_retriesOnSecondaryInstance() = runBlocking {
        // Server 1 returns 429 Rate Limit
        mockServer1.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("Rate limited")
        )

        // Server 2 returns 200 Success
        val successJson = """
            {
              "status": "redirect",
              "url": "https://scontent.cdninstagram.com/reel_1080p.mp4",
              "filename": "instagram_reel_123.mp4"
            }
        """.trimIndent()

        mockServer2.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(successJson)
        )

        val resolver = SocialMediaResolver(
            client = okHttpClient,
            instances = listOf(
                mockServer1.url("/").toString(),
                mockServer2.url("/").toString()
            ),
            youtubeInstances = emptyList()
        )

        val result = resolver.resolve(
            url = "https://www.instagram.com/reel/C8xyz123/",
            quality = MediaQuality.MAX
        )

        assertTrue("Should succeed on backup server", result.isSuccess)
        val asset = result.getOrNull()!!
        assertEquals("https://scontent.cdninstagram.com/reel_1080p.mp4", asset.streamUrl)
        assertEquals(1, mockServer1.requestCount)
        assertEquals(1, mockServer2.requestCount)
    }

    @Test
    fun directInstagramHtmlScraper_extractsOgVideoWhenApiFails() = runBlocking {
        val instagramHtml = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta property="og:title" content="Viral Comedy Reel - Instagram" />
              <meta property="og:video" content="https://instagram.fdel.cdn.com/videos/v12345.mp4" />
              <meta property="og:image" content="https://instagram.fdel.cdn.com/thumbs/t12345.jpg" />
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val mockInstagramServer = MockWebServer()
        mockInstagramServer.start()
        mockInstagramServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody(instagramHtml)
        )

        val directUrl = mockInstagramServer.url("/reel/C8xyz123/").toString()

        val resolver = SocialMediaResolver(
            client = okHttpClient,
            instances = emptyList(),
            youtubeInstances = emptyList()
        )

        val result = resolver.scrapeInstagramDirectly(directUrl)
        mockInstagramServer.shutdown()

        assertTrue("Direct scraper should succeed", result.isSuccess)
        val asset = result.getOrNull()!!
        assertEquals(SocialPlatform.INSTAGRAM, asset.platform)
        assertEquals("https://instagram.fdel.cdn.com/videos/v12345.mp4", asset.streamUrl)
        assertTrue(asset.title.contains("Viral Comedy Reel"))
    }
}
