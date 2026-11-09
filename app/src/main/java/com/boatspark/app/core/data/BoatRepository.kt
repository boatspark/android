package com.boatspark.app.core.data

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
import kotlinx.coroutines.flow.Flow
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
    private val onAuthenticationExpired: () -> Unit,
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
            entity?.let {
                CachedValue(json.decodeFromString<BoatStatus>(it.payload), it.refreshedAt)
            }
        }

    fun alerts(boatId: Long): Flow<CachedValue<BoatAlerts>?> =
        dao.observeAlerts(boatId).map { entity ->
            entity?.let {
                CachedValue(json.decodeFromString<BoatAlerts>(it.payload), it.refreshedAt)
            }
        }

    suspend fun refreshUserAndBoats() = refreshMutex.withLock {
        authenticatedCall {
            val now = System.currentTimeMillis()
            val profile = apiCall(json, api::currentUser)
            val remoteBoats = apiCall(json, api::boats).boats
            dao.saveUser(UserEntity(profile.id, profile.email, profile.isActive, now))
            dao.replaceBoats(
                remoteBoats.map {
                    BoatEntity(it.id, it.name, it.deviceIdentifier, it.createdAt, now)
                },
            )
        }
    }

    suspend fun refreshBoat(boatId: Long) = refreshMutex.withLock {
        authenticatedCall {
            val now = System.currentTimeMillis()
            val status = apiCall(json) { api.status(boatId) }
            val alerts = apiCall(json) { api.alerts(boatId) }
            dao.saveStatus(BoatStatusEntity(boatId, json.encodeToString(status), now))
            dao.saveAlerts(BoatAlertsEntity(boatId, json.encodeToString(alerts), now))
        }
    }

    /** History is fetched on demand and not cached offline. */
    suspend fun history(boatId: Long): BoatHistory = authenticatedCall {
        apiCall(json) { api.history(boatId) }
    }

    suspend fun refreshAll() {
        refreshUserAndBoats()
        dao.getBoats().forEach { refreshBoat(it.id) }
    }

    suspend fun clear() = refreshMutex.withLock {
        dao.clearUserData()
    }

    private suspend fun <T> authenticatedCall(block: suspend () -> T): T = try {
        block()
    } catch (error: ApiFailure.Authentication) {
        onAuthenticationExpired()
        throw error
    }
}
