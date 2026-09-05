package com.boatspark.app.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class BoatSparkDaoTest {
    private lateinit var database: BoatSparkDatabase
    private lateinit var dao: BoatSparkDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            BoatSparkDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacingBoatsRemovesOldSnapshot() = runTest {
        dao.replaceBoats(
            listOf(
                BoatEntity(1, "First", "DEVICE1", "2026-01-01T00:00:00Z", 1),
                BoatEntity(2, "Second", "DEVICE2", "2026-01-01T00:00:00Z", 1),
            ),
        )
        dao.replaceBoats(
            listOf(BoatEntity(2, "Renamed", "DEVICE2", "2026-01-01T00:00:00Z", 2)),
        )

        assertEquals(listOf("Renamed"), dao.getBoats().map { it.name })
    }

    @Test
    fun clearingUserDataRemovesEveryUserScopedTable() = runTest {
        dao.saveUser(UserEntity(1, "user@example.com", true, 1))
        dao.replaceBoats(listOf(BoatEntity(1, "Boat", "DEVICE1", "2026-01-01T00:00:00Z", 1)))
        dao.saveStatus(BoatStatusEntity(1, """{"boat_id":1}""", 1))
        dao.saveAlerts(BoatAlertsEntity(1, """{"boat_id":1}""", 1))

        dao.clearUserData()

        assertNull(dao.observeUser().first())
        assertEquals(emptyList(), dao.getBoats())
        assertNull(dao.observeStatus(1).first())
        assertNull(dao.observeAlerts(1).first())
    }
}

