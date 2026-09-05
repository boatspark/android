package com.boatspark.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BoatSparkDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun observeUser(): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    @Query("SELECT * FROM boats ORDER BY name COLLATE NOCASE")
    fun observeBoats(): Flow<List<BoatEntity>>

    @Query("SELECT * FROM boats ORDER BY name COLLATE NOCASE")
    suspend fun getBoats(): List<BoatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoats(boats: List<BoatEntity>)

    @Query("DELETE FROM boats")
    suspend fun deleteBoats()

    @Transaction
    suspend fun replaceBoats(boats: List<BoatEntity>) {
        deleteBoats()
        insertBoats(boats)
    }

    @Query("SELECT * FROM boat_status WHERE boatId = :boatId")
    fun observeStatus(boatId: Long): Flow<BoatStatusEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStatus(status: BoatStatusEntity)

    @Query("SELECT * FROM boat_alerts WHERE boatId = :boatId")
    fun observeAlerts(boatId: Long): Flow<BoatAlertsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlerts(alerts: BoatAlertsEntity)

    @Query("DELETE FROM user_profile")
    suspend fun deleteUser()

    @Query("DELETE FROM boat_status")
    suspend fun deleteStatuses()

    @Query("DELETE FROM boat_alerts")
    suspend fun deleteAlerts()

    @Transaction
    suspend fun clearUserData() {
        deleteAlerts()
        deleteStatuses()
        deleteBoats()
        deleteUser()
    }
}

