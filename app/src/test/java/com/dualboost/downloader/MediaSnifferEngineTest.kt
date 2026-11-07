package com.dualboost.downloader

import com.dualboost.downloader.sniffer.DetectedMediaItem
import com.dualboost.downloader.sniffer.MediaSnifferEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MediaSnifferEngineTest {

    private lateinit var sniffer: MediaSnifferEngine

    @Before
    fun setUp() {
        sniffer = MediaSnifferEngine()
    }

    @Test
    fun testInspectUrl_DetectsDirectVideoExtensions_MkvMp4Webm() {
        val mkvUrl = "https://moviesleech.media/downloads/Mirzapur.The.Movie.2026.4K-2160p.x265-MoviesLeech.mkv?auth=xyz123"
        val item1 = sniffer.inspectRequest(mkvUrl)

        assertNotNull("MKV file should be detected", item1)
        assertEquals("mkv", item1?.extension)
        assertEquals("Mirzapur.The.Movie.2026.4K-2160p.x265-MoviesLeech.mkv", item1?.fileName)
        assertEquals("https://moviesleech.media/downloads/Mirzapur.The.Movie.2026.4K-2160p.x265-MoviesLeech.mkv?auth=xyz123", item1?.url)

        val mp4Url = "https://cdn.example.com/videos/Avatar.2024.1080p.mp4"
        val item2 = sniffer.inspectRequest(mp4Url)
        assertNotNull("MP4 file should be detected", item2)
        assertEquals("mp4", item2?.extension)
        assertEquals("Avatar.2024.1080p.mp4", item2?.fileName)

        val webmUrl = "https://cdn.example.com/trailer.webm"
        val item3 = sniffer.inspectRequest(webmUrl)
        assertNotNull("WebM file should be detected", item3)
        assertEquals("webm", item3?.extension)
    }

    @Test
    fun testInspectHeaders_DetectsVideoMimeTypes_AndContentDisposition() {
        // Obfuscated / dynamic URL without extension in path
        val dynamicUrl = "https://fastdl.example/api/stream?file_id=88319"
        val headers = mapOf(
            "Content-Type" to "video/mp4",
            "Content-Disposition" to "attachment; filename=\"Special_Episode_01.mp4\"",
            "Content-Length" to "1572864000" // ~1.5 GB
        )

        val item = sniffer.inspectRequest(dynamicUrl, headers = headers, mimeType = "video/mp4")
        assertNotNull("Should detect video stream via MIME and Content-Disposition", item)
        assertEquals("mp4", item?.extension)
        assertEquals("Special_Episode_01.mp4", item?.fileName)
        assertEquals(1572864000L, item?.contentLength)
    }

    @Test
    fun testInspectHlsStreams_DetectsM3u8Playlists() {
        val hlsUrl = "https://stream.server.net/live/master.m3u8?token=secure"
        val item = sniffer.inspectRequest(hlsUrl)

        assertNotNull("HLS m3u8 playlist should be detected", item)
        assertEquals("m3u8", item?.extension)
        assertEquals("master.m3u8", item?.fileName)
    }

    @Test
    fun testAdwareUrlFiltering_IgnoresAdNetworksAndTrackingDomains() {
        val adUrls = listOf(
            "https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub-1234",
            "https://creative.adsterra.com/track?imp=9938",
            "https://popads.net/serve/popunder.js",
            "https://propellerads.com/sdk/v1/ad.mp4",
            "https://betting-promo-1xbet.com/banner.mp4"
        )

        for (url in adUrls) {
            val item = sniffer.inspectRequest(url)
            assertNull("Ad network URL '$url' must be filtered out", item)
        }
    }

    @Test
    fun testStaticAssetFiltering_IgnoresImagesStylesheetsScriptsAndFavicons() {
        val assets = listOf(
            "https://moviesleech.media/assets/style.css",
            "https://moviesleech.media/assets/script.js",
            "https://moviesleech.media/images/poster.jpg",
            "https://moviesleech.media/images/thumbnail.png",
            "https://moviesleech.media/favicon.ico",
            "https://moviesleech.media/fonts/roboto.woff2"
        )

        for (url in assets) {
            val item = sniffer.inspectRequest(url)
            assertNull("Static asset '$url' must not be detected as media", item)
        }
    }

    @Test
    fun testDeduplication_IgnoresRepeatedRequestsForSameMedia() {
        val videoUrl = "https://cdn.example.com/movie.mp4"

        // First inspection registers it
        val item1 = sniffer.inspectRequest(videoUrl)
        assertNotNull(item1)
        assertEquals(1, sniffer.detectedMedia.value.size)

        // Second inspection for same URL is deduplicated
        val item2 = sniffer.inspectRequest(videoUrl)
        assertNull("Repeated request for same video URL must return null", item2)
        assertEquals(1, sniffer.detectedMedia.value.size)

        // Different video adds to list
        val item3 = sniffer.inspectRequest("https://cdn.example.com/movie2.mp4")
        assertNotNull(item3)
        assertEquals(2, sniffer.detectedMedia.value.size)

        // Clear wipes the detected list
        sniffer.clear()
        assertEquals(0, sniffer.detectedMedia.value.size)
    }

    @Test
    fun testCleanTitleExtraction_DecodesPercentEncoding() {
        val encodedUrl = "https://cdn.example.com/Spider-Man%20No%20Way%20Home%20(2021)%20[1080p].mkv"
        val item = sniffer.inspectRequest(encodedUrl)

        assertNotNull(item)
        assertEquals("Spider-Man No Way Home (2021) [1080p].mkv", item?.fileName)
    }
}
