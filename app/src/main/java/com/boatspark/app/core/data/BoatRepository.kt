package com.boatspark.app.core.data

import com.boatspark.app.core.auth.AuthenticationRequiredException
import com.boatspark.app.core.auth.SessionAuthority
import com.boatspark.app.core.database.BoatAlertsEntity
import com.boatspark.app.core.database.BoatEntity
import com.boatspark.app.core.database.BoatSparkDao
import com.boatspark.app.core.database.BoatStatusEntity
import com.boatspark.app.core.database.UserEntity
import com.boatspark.app.core.model.Boat
import com.boatspark.app.core.model.BoatAlerts
import com.boatspark.app.core.model.BoatHistory
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.UserProfile
import com.boatspark.app.core.network.ApiFailure
import com.boatspark.app.core.network.BoatSparkApi
import com.boatspark.app.core.network.apiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

data class CachedValue<T>(
    val value: T,
    val refreshedAt: Long,
)

class BoatRepository(
    private val api: BoatSparkApi,
    private val dao: BoatSparkDao,
    private val json: Json,
    private val session: SessionAuthority,
) {
    private val refreshMutex = Mutex()

    val user: Flow<CachedValue<UserProfile>?> = dao.observeUser().map { entity ->
        entity?.let {
            CachedValue(UserProfile(it.id, it.email, it.isActive), it.refreshedAt)
        }
    }

    val boats: Flow<List<CachedValue<Boat>>> = dao.observeBoats().map { entities ->
        entities.map {
            CachedValue(
                Boat(it.id, it.name, it.deviceIdentifier, it.createdAt),
                it.refreshedAt,
            )
        }
    }

    fun status(boatId: Long): Flow<CachedValue<BoatStatus>?> =
        dao.observeStatus(boatId).map { entity ->
            entity?.let { cached -> decodeCached<BoatStatus>(cached.payload)?.let { CachedValue(it, cached.refreshedAt) } }
        }.flowOn(Dispatchers.Default)

    fun alerts(boatId: Long): Flow<CachedValue<BoatAlerts>?> =
        dao.observeAlerts(boatId).map { entity ->
            entity?.let { cached -> decodeCached<BoatAlerts>(cached.payload)?.let { CachedValue(it, cached.refreshedAt) } }
        }.flowOn(Dispatchers.Default)

    /** A payload cached by an older app version may no longer decode; treat it as not cached. */
    private inline fun <reified T> decodeCached(payload: String): T? = try {
        json.decodeFromString<T>(payload)
    } catch (_: IllegalArgumentException) {
        null
    }

    suspend fun refreshUserAndBoats() = refreshMutex.withLock {
        authenticatedCall { generation ->
            val now = System.currentTimeMillis()
            val profile = apiCall(json, api::currentUser)
            val remoteBoats = apiCall(json, api::boats).boats
            requireSession(generation)
            dao.saveUser(UserEntity(profile.id, profile.email, profile.isActive, now))
            dao.replaceBoats(
                remoteBoats.map {
                    BoatEntity(it.id, it.name, it.deviceIdentifier, it.createdAt, now)
                },
            )
        }
    }

    suspend fun refreshBoat(boatId: Long) = refreshMutex.withLock {
        authenticatedCall { generation ->
            val now = System.currentTimeMillis()
            val status = apiCall(json) { api.status(boatId) }
            requireSession(generation)
            dao.saveStatus(BoatStatusEntity(boatId, json.encodeToString(status), now))
            val alerts = apiCall(json) { api.alerts(boatId) }
            requireSession(generation)
            dao.saveAlerts(BoatAlertsEntity(boatId, json.encodeToString(alerts), System.currentTimeMillis()))
        }
    }

    /** History is fetched on demand and not cached offline. */
    suspend fun history(boatId: Long): BoatHistory = authenticatedCall {
        apiCall(json) { api.history(boatId) }
    }

    /** Refreshes every boat, continuing past failures so one bad boat cannot starve the rest. */
    suspend fun refreshAll() {
        refreshUserAndBoats()
        var firstFailure: Exception? = null
        for (boat in dao.getBoats()) {
            try {
                refreshBoat(boat.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthenticationRequiredException) {
                throw error
            } catch (error: ApiFailure.Authentication) {
                throw error
            } catch (error: Exception) {
                if (firstFailure == null) firstFailure = error
            }
        }
        firstFailure?.let { throw it }
    }

    suspend fun clear() = refreshMutex.withLock {
        dao.clearUserData()
    }

    /**
     * Runs [block] for the current session. A 401 first forces a token refresh and
     * retries once, so a transient rejection does not end an otherwise valid session.
     */
    private suspend fun <T> authenticatedCall(block: suspend (generation: Long) -> T): T {
        val generation = session.currentGeneration()
        return try {
            block(generation)
        } catch (_: ApiFailure.Authentication) {
            session.invalidateAccessToken(generation)
            try {
                block(generation)
            } catch (error: ApiFailure.Authentication) {
                session.markSessionExpired(generation)
                throw error
            }
        }
    }

    /** Drops results that arrive after the session that requested them has ended. */
    private fun requireSession(generation: Long) {
        if (session.currentGeneration() != generation) throw AuthenticationRequiredException()
    }
}
