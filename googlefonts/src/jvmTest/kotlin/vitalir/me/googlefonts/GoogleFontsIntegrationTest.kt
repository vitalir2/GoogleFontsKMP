package vitalir.me.googlefonts

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.LoadedFont
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end test of the JVM load path: directory resolution, font download, disk cache and
 * offline behavior, using a deterministic in-memory [FontHttpClient].
 */
class GoogleFontsIntegrationTest {

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

    private lateinit var http: FakeHttpClient
    private lateinit var cacheDir: String

    private val directoryXml = """
        <font_directory version='28'>
        	<families>
        		<family name='TestFont'>
        			<font weight='400' italic='0.0' styleName='Regular' url='//font/font.ttf'/>
        			<font weight='700' italic='0.0' styleName='Bold' url='//font/font-bold.ttf'/>
        		</family>
        	</families>
        </font_directory>
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        http = FakeHttpClient()
        http.responses["http://dir/directory.xml"] = directoryXml.toByteArray()
        http.responses["https://font/font.ttf"] = "fake-font-bytes".toByteArray()
        http.responses["https://font/font-bold.ttf"] = "fake-font-bold-bytes".toByteArray()
        GoogleFonts.httpClient = http
        FontDirectoryProvider.directoryUrl = "http://dir/directory.xml"
        runBlocking { FontDirectoryProvider.clear() }
        cacheDir = Files.createTempDirectory("googlefonts-test").toString()
        FontDiskCache.cacheDirOverride = cacheDir
    }

    @AfterTest
    fun tearDown() {
        runBlocking { FontFetcher.awaitIdle() } // let background prefetches finish first
        GoogleFonts.httpClient = null
        FontDirectoryProvider.directoryUrl = FontDirectoryProvider.DEFAULT_DIRECTORY_URL
        runBlocking {
            FontDirectoryProvider.clear()
            FontMemoryCache.clear()
        }
        FontDiskCache.cacheDirOverride = null
        FontFetcher.reset()
    }

    @Test
    fun loadsAndCachesFont() = runBlocking<Unit> {
        val font = GoogleFont("TestFont").load()
        assertNotNull(font)
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

    @Test
    fun toFontFamilyReturnsFamilyWithMatchingFace() = runBlocking<Unit> {
        val family = GoogleFont("TestFont").toFontFamily(FontWeight.Bold)
        assertTrue(family is FontListFontFamily)
        val font = (family as FontListFontFamily).fonts.single()
        assertEquals(FontWeight.Bold, font.weight)
        assertEquals(FontStyle.Normal, font.style)
    }

    @Test
    fun toFontFamilyMultiFaceLoadsAllWeights() = runBlocking<Unit> {
        val family = GoogleFont("TestFont").toFontFamily(FontWeight.Normal, FontWeight.Bold)
        assertTrue(family is FontListFontFamily)
        assertEquals(2, (family as FontListFontFamily).fonts.size)
    }

    @Test
    fun variationSettingsArePassedToFont() = runBlocking<Unit> {
        val variation = FontVariation.Settings(FontVariation.weight(500))
        val font = GoogleFont("TestFont").load(variationSettings = variation)
        val loaded = font as LoadedFont
        assertEquals(variation, loaded.variationSettings)
    }

    @Test
    fun fontFactoryReturnsLoadedFontFromCache() = runBlocking<Unit> {
        GoogleFont("TestFont").load(weight = FontWeight.Bold)
        val font = Font(googleFont = GoogleFont("TestFont"), weight = FontWeight.Bold)
        assertTrue(font is LoadedFont)
        assertEquals("fake-font-bold-bytes", (font as LoadedFont).data.decodeToString())
    }

    @Test
    fun fontFactoryDefaultProviderWorks() = runBlocking<Unit> {
        GoogleFont("TestFont").load()
        val font = Font(googleFont = GoogleFont("TestFont"))
        assertTrue(font is LoadedFont)
        assertEquals("fake-font-bytes", (font as LoadedFont).data.decodeToString())
    }

    @Test
    fun warmUpFillsDiskCache() = runBlocking<Unit> {
        GoogleFont("TestFont").warmUp()
        val bytes = FontFetcher.fetch(GoogleFont("TestFont"), FontWeight.Normal, FontStyle.Normal)
        assertEquals("fake-font-bytes", bytes.decodeToString())
        assertTrue(File(cacheDir, fontFileKey("TestFont", 400, false)).exists())
    }

    @Test
    fun concurrentLoadsDeduplicate() = runBlocking<Unit> {
        val fonts = (1..4).map { async { GoogleFont("TestFont").load() } }
        fonts.awaitAll()
        // directory (1) + font file (1) — concurrent loads share one download each
        assertEquals(2, http.requestCount)
    }
}