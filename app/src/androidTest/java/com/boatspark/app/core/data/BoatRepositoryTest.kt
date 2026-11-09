package com.boatspark.app.core.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.boatspark.app.core.auth.AuthenticationRequiredException
import com.boatspark.app.core.auth.SessionAuthority
import com.boatspark.app.core.database.BoatSparkDatabase
import com.boatspark.app.core.model.BoatAlerts
import com.boatspark.app.core.model.BoatHistory
import com.boatspark.app.core.model.BoatList
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.UserProfile
import com.boatspark.app.core.network.ApiFailure
import com.boatspark.app.core.network.BoatSparkApi
import com.boatspark.app.core.network.BoatSparkJson
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BoatRepositoryTest {
    private lateinit var database: BoatSparkDatabase
    private val api = FakeApi()
    private val session = FakeSession()
    private lateinit var repository: BoatRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BoatSparkDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BoatRepository(api, database.dao(), BoatSparkJson, session)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transient401RefreshesTokenAndRetriesWithoutEndingSession() = runTest {
        api.statusFailures += http(401)

        repository.refreshBoat(BOAT_ID)

        assertEquals(1, session.invalidations)
        assertEquals(emptyList(), session.expiredGenerations)
        assertNotNull(repository.status(BOAT_ID).first())
    }

    @Test
    fun repeated401EndsOnlyTheRequestingSession() = runTest {
        api.statusFailures += http(401)
        api.statusFailures += http(401)

        assertFailsWith<ApiFailure.Authentication> { repository.refreshBoat(BOAT_ID) }

        assertEquals(listOf(0L), session.expiredGenerations)
    }

    @Test
    fun resultsFromAnEndedSessionAreNotSaved() = runTest {
        api.onStatus = { session.generation++ }

        assertFailsWith<AuthenticationRequiredException> { repository.refreshBoat(BOAT_ID) }

        assertNull(repository.status(BOAT_ID).first())
    }

    @Test
    fun alertsFailureStillSavesStatus() = runTest {
        api.alertsFailure = http(500)

        assertFailsWith<ApiFailure.Server> { repository.refreshBoat(BOAT_ID) }

        assertNotNull(repository.status(BOAT_ID).first())
        assertNull(repository.alerts(BOAT_ID).first())
    }

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private class FakeSession : SessionAuthority {
        var generation = 0L
        var invalidations = 0
        val expiredGenerations = mutableListOf<Long>()

        override fun currentGeneration() = generation
        override fun invalidateAccessToken(generation: Long) {
            invalidations++
        }
        override fun markSessionExpired(generation: Long) {
            expiredGenerations += generation
        }
    }

    private class FakeApi : BoatSparkApi {
        val statusFailures = ArrayDeque<Exception>()
        var alertsFailure: Exception? = null
        var onStatus: () -> Unit = {}

        override suspend fun currentUser() = UserProfile(1, "a@example.com", true)
        override suspend fun boats() = BoatList(emptyList())
        override suspend fun status(boatId: Long): BoatStatus {
            statusFailures.removeFirstOrNull()?.let { throw it }
            onStatus()
            return BoatStatus(boatId, "Test Boat")
        }
        override suspend fun alerts(boatId: Long, days: Int): BoatAlerts {
            alertsFailure?.let { throw it }
            return BoatAlerts(boatId, "Test Boat", emptyList())
        }
        override suspend fun history(boatId: Long): BoatHistory = error("unused")
    }

    private companion object {
        const val BOAT_ID = 7L
    }
}
