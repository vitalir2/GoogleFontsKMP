package vitalir.me.googlefonts

import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Error-path tests for the fetch pipeline: exception wrapping, body validation, corrupted cache
 * files, and the stale-directory offline fallback.
 */
class FetchPipelineTest {

    private class FakeHttpClient : FontHttpClient {
        var offline = false
        var requestCount = 0
        val responses = mutableMapOf<String, ByteArray>()

        override suspend fun get(url: String): ByteArray {
            requestCount++
            if (offline) throw GoogleFontException("offline")
            return responses[url] ?: throw GoogleFontException("no response for $url")
        }
    }

    /** A client that violates the [FontHttpClient] contract by throwing a raw non-library exception. */
    private class MisbehavingHttpClient : FontHttpClient {
        override suspend fun get(url: String): ByteArray = throw IllegalStateException("boom")
    }

    private val directoryXml = """
        <font_directory version='28'>
        	<families>
        		<family name='TestFont'>
        			<font weight='400' italic='0.0' styleName='Regular' url='//font/font.ttf'/>
        		</family>
        	</families>
        </font_directory>
    """.trimIndent()

    private fun ttf(payload: String): ByteArray =
        byteArrayOf(0x00, 0x01, 0x00, 0x00) + payload.encodeToByteArray()

    @BeforeTest
    fun setUp() {
        FontDirectoryProvider.directoryUrl = "http://dir/directory.xml"
        runBlocking { FontDirectoryProvider.clear() }
        FontDiskCache.cacheDirOverride = "build/googlefonts-test-${currentTimeMillis()}"
    }

    @AfterTest
    fun tearDown() {
        runBlocking {
            FontDirectoryProvider.clear()
            FontMemoryCache.clear()
        }
        GoogleFonts.httpClient = null
        FontDirectoryProvider.directoryUrl = FontDirectoryProvider.DEFAULT_DIRECTORY_URL
        FontDiskCache.cacheDirOverride = null
        FontFetcher.reset()
    }

    @Test
    fun nonGoogleFontExceptionIsWrapped() = runBlocking<Unit> {
        GoogleFonts.httpClient = MisbehavingHttpClient()
        val result = runCatching { GoogleFont("TestFont").load() }
        val error = result.exceptionOrNull()
        assertTrue(error is GoogleFontException, "expected GoogleFontException, got $error")
        // Walk to the root cause: coroutines stack-trace recovery may insert a copy of the
        // exception when it crosses a coroutine boundary.
        val rootCause = generateSequence(error as Throwable) { it.cause }.last()
        assertTrue(rootCause is IllegalStateException, "unexpected root cause: $rootCause")
    }

    @Test
    fun nonFontBodyIsRejectedAndNotCached() = runBlocking<Unit> {
        val http = FakeHttpClient()
        http.responses["http://dir/directory.xml"] = directoryXml.encodeToByteArray()
        http.responses["https://font/font.ttf"] = "<html>captive portal</html>".encodeToByteArray()
        GoogleFonts.httpClient = http
        val result = runCatching { GoogleFont("TestFont").load() }
        assertTrue(result.exceptionOrNull() is GoogleFontException)
        // The invalid body must not have been persisted to the disk cache.
        assertEquals(null, FontDiskCache.get(fontFileKey("TestFont", 400, false, bestEffort = true)))
    }

    @Test
    fun staleDirectoryIsUsedWhenOffline() = runBlocking<Unit> {
        val http = FakeHttpClient()
        http.offline = true
        GoogleFonts.httpClient = http
        // Seed a stale (older than TTL) directory on disk, in an isolated scratch directory.
        FontDiskCache.cacheDirOverride = "build/googlefonts-test-stale-${currentTimeMillis()}"
        val staleTime = currentTimeMillis() - 8.days.inWholeMilliseconds
        FontDiskCache.put("directory.xml", directoryXml.encodeToByteArray())
        FontDiskCache.put("directory.xml.time", staleTime.toString().encodeToByteArray())
        val directory = FontDirectoryProvider.get()
        assertEquals(1, directory.families.size)
        assertEquals("TestFont", directory.families.single().name)
    }
}
