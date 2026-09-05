package com.boatspark.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserEntity(
    @PrimaryKey val id: Long,
    val email: String,
    val isActive: Boolean,
    val refreshedAt: Long,
)

@Entity(tableName = "boats")
data class BoatEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val deviceIdentifier: String,
    val createdAt: String,
    val refreshedAt: Long,
)

@Entity(tableName = "boat_status")
data class BoatStatusEntity(
    @PrimaryKey val boatId: Long,
    val payload: String,
    val refreshedAt: Long,
)

@Entity(tableName = "boat_alerts")
data class BoatAlertsEntity(
    @PrimaryKey val boatId: Long,
    val payload: String,
    val refreshedAt: Long,
)

