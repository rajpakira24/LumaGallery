package com.webstudio.lumagallery.data.ai

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AiProxyClientTest {
    private lateinit var server: MockWebServer
    private val fakeIntegrity = object : IntegrityTokenProvider {
        override suspend fun token(requestHash: String) = "FAKE_TOKEN"
    }

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client() = AiProxyClient(
        functionUrl = server.url("/").toString(),
        integrity = fakeIntegrity,
        http = OkHttpClient(),
        encodeJpeg = { _, _ -> byteArrayOf(1, 2, 3) },
        decode = { _ -> STUB },
    )

    @Test fun describe_returns_text() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"text":"a cat"}""").setResponseCode(200))
        assertEquals("a cat", client().describe(STUB))
    }

    @Test fun edit_returns_bitmap() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"image_b64":"AQID"}""").setResponseCode(200))
        assertEquals(STUB, client().editImage("upscale", STUB, null, null))
    }

    @Test fun http_429_throws_quota() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"quota"}"""))
        assertThrows(AiProxyClient.QuotaException::class.java) {
            runBlocking { client().describe(STUB) }
        }
    }

    @Test fun http_401_throws_attestation() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"attestation_failed"}"""))
        assertThrows(AiProxyClient.AttestationException::class.java) {
            runBlocking { client().describe(STUB) }
        }
    }

    companion object {
        private val STUB: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
