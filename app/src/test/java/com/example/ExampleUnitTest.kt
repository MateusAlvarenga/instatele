package com.example

import android.net.Uri
import com.example.data.local.ArchiverSettings
import com.example.data.model.MediaType
import com.example.data.model.ScannedMediaFile
import com.example.data.model.TELEGRAM_BOT_API_MAX_FILE_SIZE
import com.example.data.model.UploadMode
import com.example.data.model.UserSessionInfo
import com.example.util.FileUtils
import com.example.util.InstagramParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertFalse(normalFile.isOversizedForStandardBot)
        assertTrue(oversizedFile.isOversizedForStandardBot)

        val list = listOf(normalFile, oversizedFile)
        val (valid, oversized) = list.partition { !it.isOversizedForStandardBot }
        assertEquals(1, valid.size)
        assertEquals(1, oversized.size)
        assertEquals("video1.mp4", valid[0].name)
        assertEquals("video2.mp4", oversized[0].name)
    }

    @Test
    fun testUserModeSettingsReadiness() {
        val botSettings = ArchiverSettings(
            uploadMode = UploadMode.BOT,
            botToken = "123:ABC",
            uploadDestination = "@my_channel"
        )
        assertTrue(botSettings.isReadyForUpload)

        val userSettingsIncomplete = ArchiverSettings(
            uploadMode = UploadMode.USER_ACCOUNT,
            isUserSessionValid = false,
            userUploadDestination = "me"
        )
        assertFalse(userSettingsIncomplete.isReadyForUpload)

        val userSettingsReady = ArchiverSettings(
            uploadMode = UploadMode.USER_ACCOUNT,
            isUserSessionValid = true,
            userSessionString = "valid_session_string",
            userUploadDestination = "me"
        )
        assertTrue(userSettingsReady.isReadyForUpload)
        assertEquals("Mensagens Salvas (me)", userSettingsReady.activeDestination)
    }

    @Test
    fun testAlbumSizePartitioningForBotMode() {
        // Create 3 files of 20 MB each (sum = 60 MB > 48 MB)
        val files = (1..3).map { i ->
            ScannedMediaFile(
                id = "$i",
                name = "vid$i.mp4",
                uri = Uri.parse("content://test/vid$i.mp4"),
                sizeBytes = 20L * 1024L * 1024L,
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO,
                instagramUsername = "test_user"
            )
        }

        val albumLimit = 10
        val maxAlbumBytes = 48L * 1024L * 1024L

        val groups = mutableListOf<List<ScannedMediaFile>>()
        var curGroup = mutableListOf<ScannedMediaFile>()
        var curGroupBytes = 0L

        for (vf in files) {
            val willExceedCount = curGroup.size >= albumLimit
            val willExceedBytes = (curGroupBytes + vf.sizeBytes > maxAlbumBytes) && curGroup.isNotEmpty()
            if (willExceedCount || willExceedBytes) {
                groups.add(curGroup)
                curGroup = mutableListOf()
                curGroupBytes = 0L
            }
            curGroup.add(vf)
            curGroupBytes += vf.sizeBytes
        }
        if (curGroup.isNotEmpty()) groups.add(curGroup)

        // Group 1 will have 2 files (40 MB <= 48 MB)
        // Group 2 will have 1 file (20 MB <= 48 MB)
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].size)
        assertEquals(1, groups[1].size)
    }

    @Test
    fun testInventedCodeOrShortStringIsRejected() {
        val client = com.example.data.telegram.TelegramUserClient()
        // User invented code test: "12345"
        val resultInvented = client.importStringSession("12345", "Test")
        assertTrue(resultInvented is com.example.data.telegram.UserSignInResult.Failure)

        val resultRandom = client.importStringSession("codigo_inventado_random_123", "Test")
        assertTrue(resultRandom is com.example.data.telegram.UserSignInResult.Failure)
    }

    @Test
    fun testValidStringSessionImport() {
        val client = com.example.data.telegram.TelegramUserClient()
        // Generate valid-length dummy MTProto session (263 bytes: DC 2 + 4 bytes IP + 2 bytes Port + 256 bytes key)
        val validBytes = ByteArray(263) { 0 }
        validBytes[0] = 2 // DC 2
        val base64Valid = android.util.Base64.encodeToString(validBytes, android.util.Base64.NO_WRAP)

        val result = client.importStringSession(base64Valid, "Minha Conta Principal")
        assertTrue(result is com.example.data.telegram.UserSignInResult.Success)
        val session = (result as com.example.data.telegram.UserSignInResult.Success).session
        assertTrue(session.isValid)
        assertEquals(2, session.dcId)
        assertEquals("Minha Conta Principal", session.firstName)
    }

    @Test
    fun testTelethonStringSessionWithOnePrefixAndQuotes() {
        val client = com.example.data.telegram.TelegramUserClient()
        // Telethon generates: '1' + urlsafe_b64encode(struct.pack('>B4sH256s', dc_id, ip, port, auth_key))
        val telethonPayload = ByteArray(263) { 0 }
        telethonPayload[0] = 4 // DC 4
        telethonPayload[1] = 149.toByte() // IP
        val base64UrlSafe = android.util.Base64.encodeToString(
            telethonPayload,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val telethonString = "1$base64UrlSafe"

        // Test with raw Telethon string
        val res1 = client.importStringSession(telethonString)
        assertTrue(res1 is com.example.data.telegram.UserSignInResult.Success)
        val s1 = (res1 as com.example.data.telegram.UserSignInResult.Success).session
        assertEquals(4, s1.dcId)

        // Test with quotes around it (like copied from terminal: "1BAA...")
        val quoted = "\"$telethonString\""
        val res2 = client.importStringSession(quoted)
        assertTrue(res2 is com.example.data.telegram.UserSignInResult.Success)

        // Test with python variable assignment (session = "1BAA...")
        val pythonSnippet = "session = '$telethonString'"
        val res3 = client.importStringSession(pythonSnippet)
        assertTrue(res3 is com.example.data.telegram.UserSignInResult.Success)
    }
}
