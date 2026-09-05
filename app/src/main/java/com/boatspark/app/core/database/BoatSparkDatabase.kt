package com.boatspark.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, BoatEntity::class, BoatStatusEntity::class, BoatAlertsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BoatSparkDatabase : RoomDatabase() {
    abstract fun dao(): BoatSparkDao
}
