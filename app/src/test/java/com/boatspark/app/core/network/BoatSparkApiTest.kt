package com.boatspark.app.core.network

import com.boatspark.app.core.auth.AuthenticationRequiredException
import com.boatspark.app.core.model.BoatStatus
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class BoatSparkApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BoatSparkApi
    private val json = Json { explicitNulls = false }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BoatSparkApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `status request uses expected endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "boat_id": 7,
                  "boat_name": "Test Boat",
                  "published_at": null,
                  "firmware_version": null,
                  "gps": null,
                  "gps_no_fix": false,
                  "last_gps": null,
                  "gpio": null,
                  "system": null,
                  "beacons": [],
                  "missing_beacons": []
                }
                """.trimIndent(),
            ),
        )

        val response = api.status(7)

        assertEquals(7, response.boatId)
        assertEquals("/api/v1/boats/7/status", server.takeRequest().path)
    }

    @Test
    fun `API detail is preserved for forbidden response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"detail":"Access denied"}"""),
        )

        val failure = assertFailsWith<ApiFailure.Authorization> {
            apiCall<BoatStatus>(json) { api.status(8) }
        }

        assertEquals("Access denied", failure.message)
    }

    @Test
    fun `malformed payload is an invalid response`() = runTest {
        server.enqueue(MockResponse().setBody("""{"boat_id":"not-a-number"}"""))

        assertFailsWith<ApiFailure.InvalidResponse> {
            apiCall<BoatStatus>(json) { api.status(9) }
        }
    }

    @Test
    fun `missing access token surfaces the auth failure without reaching the server`() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val authenticatedApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .dispatcher(
                        Dispatcher(
                            Executors.newSingleThreadExecutor { runnable ->
                                Thread(runnable).apply {
                                    setUncaughtExceptionHandler { _, error -> uncaught += error }
                                }
                            },
                        ),
                    )
                    .addInterceptor(AccessTokenInterceptor { throw AuthenticationRequiredException() })
                    .build(),
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BoatSparkApi::class.java)

        assertFailsWith<AuthenticationRequiredException> {
            apiCall<BoatStatus>(json) { authenticatedApi.status(10) }
        }
        assertEquals(0, server.requestCount)
        assertEquals(emptyList(), uncaught)
    }
}

