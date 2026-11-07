package com.dualboost.downloader

import com.dualboost.downloader.social.SocialMediaDetector
import com.dualboost.downloader.social.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SocialMediaDetectorTest {

    @Test
    fun detectsYouTubeUrls_standardShortsAndShortlinks() {
        assertEquals(SocialPlatform.YOUTUBE, SocialMediaDetector.detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(SocialPlatform.YOUTUBE, SocialMediaDetector.detectPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(SocialPlatform.YOUTUBE, SocialMediaDetector.detectPlatform("https://m.youtube.com/shorts/abcdef123"))
        assertEquals(SocialPlatform.YOUTUBE, SocialMediaDetector.detectPlatform("https://youtube.com/live/abcdef123?si=tracking123"))
    }

    @Test
    fun detectsInstagramUrls_reelsPostsAndShares() {
        assertEquals(SocialPlatform.INSTAGRAM, SocialMediaDetector.detectPlatform("https://www.instagram.com/reel/C8xyz123/?igsh=abcdef"))
        assertEquals(SocialPlatform.INSTAGRAM, SocialMediaDetector.detectPlatform("https://instagram.com/p/C8xyz123/"))
        assertEquals(SocialPlatform.INSTAGRAM, SocialMediaDetector.detectPlatform("https://www.instagram.com/tv/C8xyz123/"))
        assertEquals(SocialPlatform.INSTAGRAM, SocialMediaDetector.detectPlatform("https://instagr.am/reel/C8xyz123/"))
    }

    @Test
    fun detectsTikTokUrls_standardAndShortlinks() {
        assertEquals(SocialPlatform.TIKTOK, SocialMediaDetector.detectPlatform("https://www.tiktok.com/@user/video/1234567890123456789"))
        assertEquals(SocialPlatform.TIKTOK, SocialMediaDetector.detectPlatform("https://vm.tiktok.com/ZM8abc123/"))
        assertEquals(SocialPlatform.TIKTOK, SocialMediaDetector.detectPlatform("https://m.tiktok.com/v/123456789.html"))
    }

    @Test
    fun detectsTwitterAndXUrls() {
        assertEquals(SocialPlatform.TWITTER, SocialMediaDetector.detectPlatform("https://twitter.com/user/status/1234567890123456789"))
        assertEquals(SocialPlatform.TWITTER, SocialMediaDetector.detectPlatform("https://x.com/user/status/1234567890123456789?s=20"))
    }

    @Test
    fun detectsRedditUrls() {
        assertEquals(SocialPlatform.REDDIT, SocialMediaDetector.detectPlatform("https://www.reddit.com/r/videos/comments/123456/cool_video/"))
        assertEquals(SocialPlatform.REDDIT, SocialMediaDetector.detectPlatform("https://v.redd.it/1234567890"))
    }

    @Test
    fun detectsFacebookUrls() {
        assertEquals(SocialPlatform.FACEBOOK, SocialMediaDetector.detectPlatform("https://www.facebook.com/reel/123456789012345"))
        assertEquals(SocialPlatform.FACEBOOK, SocialMediaDetector.detectPlatform("https://fb.watch/abc123xyz/"))
        assertEquals(SocialPlatform.FACEBOOK, SocialMediaDetector.detectPlatform("https://facebook.com/watch/?v=1234567890"))
    }

    @Test
    fun detectsPinterestSoundCloudAndVimeo() {
        assertEquals(SocialPlatform.PINTEREST, SocialMediaDetector.detectPlatform("https://pin.it/12345"))
        assertEquals(SocialPlatform.PINTEREST, SocialMediaDetector.detectPlatform("https://www.pinterest.com/pin/1234567890/"))
        assertEquals(SocialPlatform.SOUNDCLOUD, SocialMediaDetector.detectPlatform("https://soundcloud.com/artist/track-name"))
        assertEquals(SocialPlatform.VIMEO, SocialMediaDetector.detectPlatform("https://vimeo.com/123456789"))
    }

    @Test
    fun nonSocialUrls_returnNull() {
        assertNull(SocialMediaDetector.detectPlatform("https://example.com/files/archive.zip"))
        assertNull(SocialMediaDetector.detectPlatform("https://speed.hetzner.de/100MB.bin"))
        assertNull(SocialMediaDetector.detectPlatform("https://archive.org/download/test/test.mp4"))
        assertNull(SocialMediaDetector.detectPlatform("not a url at all"))
    }

    @Test
    fun cleanTrackingParams_stripsTelemetryAndPreservesCoreMediaId() {
        val dirtyYouTube = "https://youtu.be/dQw4w9WgXcQ?si=tracking_xyz&feature=share"
        val cleanYouTube = SocialMediaDetector.cleanUrl(dirtyYouTube)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", cleanYouTube)

        val dirtyInstagram = "https://www.instagram.com/reel/C8xyz123/?igsh=NTc4MTIwNjQ2YQ==&utm_source=ig_web_copy_link"
        val cleanInstagram = SocialMediaDetector.cleanUrl(dirtyInstagram)
        assertEquals("https://www.instagram.com/reel/C8xyz123/", cleanInstagram)
    }
}
