package vitalir.me.googlefonts.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import vitalir.me.googlefonts.GoogleFontException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorFontHttpClientTest {

    @Test
    fun returnsResponseBody() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond("font-data".encodeToByteArray(), HttpStatusCode.OK)
                }
            }
        }
        val http = KtorFontHttpClient(client)
        assertEquals("font-data", http.get("https://example.com/font.ttf").decodeToString())
    }

    @Test
    fun throwsOnErrorStatus() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond("", HttpStatusCode.NotFound) }
            }
        }
        val http = KtorFontHttpClient(client)
        assertFailsWith<GoogleFontException> {
            http.get("https://example.com/font.ttf")
        }
    }
}