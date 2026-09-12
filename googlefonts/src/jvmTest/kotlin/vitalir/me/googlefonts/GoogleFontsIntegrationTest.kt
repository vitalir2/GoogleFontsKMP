package vitalir.me.googlefonts

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end test of the JVM load path: directory resolution, font download, disk cache and
 * offline behavior, using a deterministic in-memory [FontHttpClient].
 */
class GoogleFontsIntegrationTest {

    private class FakeHttpClient : FontHttpClient {
        var offline = false
        val responses = mutableMapOf<String, ByteArray>()

        override suspend fun get(url: String): ByteArray {
            if (offline) throw GoogleFontException("offline")
            return responses[url] ?: throw GoogleFontException("no response for $url")
        }
    }

    private lateinit var http: FakeHttpClient
    private lateinit var cacheDir: String

    private val directoryXml = """
        <font_directory version='28'>
        	<families>
        		<family name='TestFont'>
        			<font weight='400' italic='0.0' styleName='Regular' url='//font/font.ttf'/>
        		</family>
        	</families>
        </font_directory>
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        http = FakeHttpClient()
        http.responses["http://dir/directory.xml"] = directoryXml.toByteArray()
        http.responses["https://font/font.ttf"] = "fake-font-bytes".toByteArray()
        GoogleFonts.httpClient = http
        FontDirectoryProvider.directoryUrl = "http://dir/directory.xml"
        cacheDir = Files.createTempDirectory("googlefonts-test").toString()
        FontDiskCache.cacheDirOverride = cacheDir
    }

    @AfterTest
    fun tearDown() {
        GoogleFonts.httpClient = null
        FontDirectoryProvider.directoryUrl = FontDirectoryProvider.DEFAULT_DIRECTORY_URL
        FontDiskCache.cacheDirOverride = null
        FontMemoryCache.clear()
    }

    @Test
    fun loadsAndCachesFont() = runBlocking<Unit> {
        val font = GoogleFont("TestFont").load()
        assertNotNull(font)
        assertTrue(GoogleFont("TestFont").isCached())
        val cachedFile = File(cacheDir, fontFileKey("TestFont", 400, false))
        assertTrue(cachedFile.exists(), "expected cached font file at $cachedFile")
    }

    @Test
    fun servesFromDiskCacheWithoutNetwork() = runBlocking<Unit> {
        GoogleFont("TestFont").load()
        FontMemoryCache.clear()
        http.offline = true // kill the network
        val font = GoogleFont("TestFont").load()
        assertNotNull(font)
    }

    @Test
    fun throwsWhenFontNotInDirectory() = runBlocking<Unit> {
        val result = runCatching { GoogleFont("MissingFont").load() }
        assertTrue(result.exceptionOrNull() is GoogleFontException)
    }
}