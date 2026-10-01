package com.example

import com.example.data.model.MediaType
import com.example.data.model.ScannedMediaFile
import com.example.util.FileUtils
import com.example.util.InstagramParser
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun testExtractUsernameFromStandardInstagramFilename() {
        val fileName = "eumariaemanuellyoficial_3944008708912508440.mp4"
        val username = InstagramParser.extractUsername(fileName)
        assertEquals("eumariaemanuellyoficial", username)
    }

    @Test
    fun testExtractUsernameWithPrefixAndSuffix() {
        val file1 = "@user.name_20231201_UTC.jpg"
        assertEquals("user.name", InstagramParser.extractUsername(file1))

        val file2 = "profile_photo-123456.png"
        assertEquals("profile_photo", InstagramParser.extractUsername(file2))
    }

    @Test
    fun testBuildCaptionContainsUsernameAndLot() {
        val caption = InstagramParser.buildCaption(
            username = "eumariaemanuellyoficial",
            fileName = "eumariaemanuellyoficial_3944008708912508440.mp4",
            lotNumber = 1,
            totalLots = 3,
            groupItemRange = "1-10/100",
            includeLink = true
        )

        assertTrue(caption.contains("@eumariaemanuellyoficial"))
        assertTrue(caption.contains("instagram.com/eumariaemanuellyoficial"))
        assertTrue(caption.contains("1/3"))
        assertTrue(caption.contains("1-10/100"))
        assertTrue(caption.contains("eumariaemanuellyoficial_3944008708912508440.mp4"))
    }

    @Test
    fun testBuildLotHeaderMessage() {
        val header = InstagramParser.buildLotHeaderMessage(
            username = "eumariaemanuellyoficial",
            lotNumber = 1,
            totalLots = 2,
            itemCountInLot = 100,
            totalUserFiles = 145,
            lotTotalSizeBytes = 500_000_000L
        )

        assertTrue(header.contains("@eumariaemanuellyoficial"))
        assertTrue(header.contains("1 de 2"))
        assertTrue(header.contains("100"))
    }

    @Test
    fun testOversizedFileDetection() {
        val normalFile = ScannedMediaFile(
            id = "1",
            name = "video1.mp4",
            uri = Uri.parse("content://test/video1.mp4"),
            sizeBytes = 25L * 1024L * 1024L, // 25 MB
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            instagramUsername = "eumariaemanuellyoficial"
        )
        val oversizedFile = ScannedMediaFile(
            id = "2",
            name = "video2.mp4",
            uri = Uri.parse("content://test/video2.mp4"),
            sizeBytes = 75L * 1024L * 1024L, // 75 MB
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            instagramUsername = "eumariaemanuellyoficial"
        )

        org.junit.Assert.assertFalse(normalFile.isOversizedForStandardBot)
        assertTrue(oversizedFile.isOversizedForStandardBot)

        val list = listOf(normalFile, oversizedFile)
        val (valid, oversized) = list.partition { !it.isOversizedForStandardBot }
        assertEquals(1, valid.size)
        assertEquals(1, oversized.size)
        assertEquals("video1.mp4", valid[0].name)
        assertEquals("video2.mp4", oversized[0].name)
    }
}
