package com.boatspark.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.HistoryState
import com.boatspark.app.core.model.BeaconHistory
import com.boatspark.app.core.model.BeaconHistorySample
import com.boatspark.app.core.model.BeaconHistoryValues
import com.boatspark.app.core.model.BeaconReading
import com.boatspark.app.core.model.BoatHistory
import com.boatspark.app.core.model.LastBeaconReading
import com.boatspark.app.core.model.Boat
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.settings.StatusSection
import com.boatspark.app.core.settings.TemperatureUnit
import com.boatspark.app.ui.screens.BoatListScreen
import com.boatspark.app.ui.screens.BoatStatusScreen
import com.boatspark.app.ui.screens.SensorDetailScreen
import com.boatspark.app.ui.screens.SettingsScreen
import com.boatspark.app.ui.screens.SignInScreen
import com.boatspark.app.ui.theme.BoatSparkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BoatScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signInExplainsBrowserFlow() {
        composeRule.setContent {
            BoatSparkTheme {
                SignInScreen(isLoading = false, configurationError = null, onSignIn = {})
            }
        }

        composeRule.onNodeWithText("Sign in with Pocket ID").assertIsDisplayed()
        composeRule.onNodeWithText("Sign-in opens securely in your browser.").assertIsDisplayed()
    }

    @Test
    fun boatListShowsBoatAndDeviceIdentifier() {
        composeRule.setContent {
            BoatSparkTheme {
                BoatListScreen(
                    boats = listOf(
                        CachedValue(
                            Boat(1, "Sea Breeze", "DEVICE00001", "2026-01-15T10:30:00Z"),
                            refreshedAt = 1_769_000_000_000,
                        ),
                    ),
                    onBoatSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Sea Breeze").assertIsDisplayed()
        composeRule.onNodeWithText("DEVICE00001").assertIsDisplayed()
    }

    @Test
    fun statusShowsBeaconsThatShareAnId() {
        val beacon = BeaconReading(3, "Cabin", -65, 2250, 5500, 95, 3000)
        composeRule.setContent {
            BoatSparkTheme {
                BoatStatusScreen(
                    cachedStatus = CachedValue(
                        BoatStatus(
                            boatId = 1,
                            boatName = "Sea Breeze",
                            beacons = listOf(beacon, beacon),
                        ),
                        refreshedAt = 1_769_000_000_000,
                    ),
                    osmTileUrl = "",
                    seamarkTileUrl = "",
                    temperatureUnit = TemperatureUnit.Fahrenheit,
                    expandedSections = emptySet(),
                    onSectionExpandedChange = { _, _ -> },
                    onAlerts = {},
                    onSensor = {},
                )
            }
        }

        assertEquals(2, composeRule.onAllNodesWithText("Cabin").fetchSemanticsNodes().size)
    }

    @Test
    fun statusShowsBeaconTemperatureInSelectedUnit() {
        val beacon = BeaconReading(3, "Cabin", -65, 2250, 5500, 95, 3000)
        var unit by mutableStateOf(TemperatureUnit.Fahrenheit)
        composeRule.setContent {
            BoatSparkTheme {
                BoatStatusScreen(
                    cachedStatus = CachedValue(
                        BoatStatus(boatId = 1, boatName = "Sea Breeze", beacons = listOf(beacon)),
                        refreshedAt = 1_769_000_000_000,
                    ),
                    osmTileUrl = "",
                    seamarkTileUrl = "",
                    temperatureUnit = unit,
                    expandedSections = emptySet(),
                    onSectionExpandedChange = { _, _ -> },
                    onAlerts = {},
                    onSensor = {},
                )
            }
        }

        composeRule.onNodeWithText("72.5 °F").assertIsDisplayed()
        composeRule.onNodeWithText("55% humidity").assertIsDisplayed()
        unit = TemperatureUnit.Celsius
        composeRule.onNodeWithText("22.5 °C").assertIsDisplayed()
    }

    @Test
    fun statusSectionsStartCollapsedAndToggle() {
        var expanded by mutableStateOf(emptySet<StatusSection>())
        composeRule.setContent {
            BoatSparkTheme {
                BoatStatusScreen(
                    cachedStatus = CachedValue(
                        BoatStatus(boatId = 1, boatName = "Sea Breeze"),
                        refreshedAt = 1_769_000_000_000,
                    ),
                    osmTileUrl = "",
                    seamarkTileUrl = "",
                    temperatureUnit = TemperatureUnit.Fahrenheit,
                    expandedSections = expanded,
                    onSectionExpandedChange = { section, isExpanded ->
                        expanded = if (isExpanded) expanded + section else expanded - section
                    },
                    onAlerts = {},
                    onSensor = {},
                )
            }
        }

        composeRule.onNodeWithText("No GPS data is available.").assertDoesNotExist()
        composeRule.onNodeWithText("Location").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf(StatusSection.Location), expanded)
        composeRule.onNodeWithText("No GPS data is available.").assertIsDisplayed()
        composeRule.onNodeWithText("Location").performClick()
        composeRule.waitForIdle()
        assertEquals(emptySet<StatusSection>(), expanded)
    }

    @Test
    fun sensorTileShowsSummaryAndOpensSensor() {
        val beacon = BeaconReading(3, "Cabin", -65, 2250, 5500, 95, 3000)
        val stale = BeaconReading(4, "Locker", -80, 1000, 4000, 20, 2600)
        var opened: Long? = null
        composeRule.setContent {
            BoatSparkTheme {
                BoatStatusScreen(
                    cachedStatus = CachedValue(
                        BoatStatus(
                            boatId = 1,
                            boatName = "Sea Breeze",
                            beacons = listOf(beacon),
                            missingBeacons = listOf(LastBeaconReading(stale, "2026-02-01T12:00:00Z")),
                        ),
                        refreshedAt = 1_769_000_000_000,
                    ),
                    osmTileUrl = "",
                    seamarkTileUrl = "",
                    temperatureUnit = TemperatureUnit.Fahrenheit,
                    expandedSections = emptySet(),
                    onSectionExpandedChange = { _, _ -> },
                    onAlerts = {},
                    onSensor = { opened = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Battery 95%").assertIsDisplayed()
        composeRule.onNodeWithText("-65 dBm", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Last seen", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cabin").performClick()
        composeRule.waitForIdle()
        assertEquals(3L, opened)
        composeRule.onNodeWithText("Locker").performClick()
        composeRule.waitForIdle()
        assertEquals(4L, opened)
    }

    @Test
    fun sensorDetailShowsAllValuesAndHistory() {
        val beacon = BeaconReading(3, "Cabin", -65, 2250, 5500, 95, 3000)
        val history = BoatHistory(
            boatId = 1,
            windowStart = "2026-02-01T00:00:00Z",
            windowEnd = "2026-02-03T00:00:00Z",
            beacons = listOf(
                BeaconHistory(
                    3,
                    "Cabin",
                    listOf(
                        BeaconHistorySample("2026-02-01T00:05:00Z", BeaconHistoryValues(2000)),
                        BeaconHistorySample("2026-02-01T00:20:00Z", BeaconHistoryValues(2100)),
                    ),
                ),
            ),
        )
        var state by mutableStateOf<HistoryState?>(HistoryState.Loading)
        composeRule.setContent {
            BoatSparkTheme {
                SensorDetailScreen(
                    cachedStatus = CachedValue(
                        BoatStatus(boatId = 1, boatName = "Sea Breeze", beacons = listOf(beacon)),
                        refreshedAt = 1_769_000_000_000,
                    ),
                    beaconId = 3,
                    history = state,
                    temperatureUnit = TemperatureUnit.Fahrenheit,
                )
            }
        }

        composeRule.onNodeWithText("72.5 °F").assertIsDisplayed()
        composeRule.onNodeWithText("55%").assertIsDisplayed()
        composeRule.onNodeWithText("95% (3.00 V)").assertIsDisplayed()
        composeRule.onNodeWithText("-65 dBm").assertIsDisplayed()
        state = HistoryState.Failed("Offline")
        composeRule.onNodeWithText("History unavailable: Offline").assertIsDisplayed()
        state = HistoryState.Loaded(history)
        composeRule.onNodeWithContentDescription("Temperature history chart").assertIsDisplayed()
        state = HistoryState.Loaded(history.copy(beacons = emptyList()))
        composeRule.onNodeWithText("No temperature readings in the last 48 hours.").assertIsDisplayed()
    }

    @Test
    fun settingsSelectsTemperatureUnit() {
        var unit by mutableStateOf(TemperatureUnit.Fahrenheit)
        composeRule.setContent {
            BoatSparkTheme {
                SettingsScreen(temperatureUnit = unit, onTemperatureUnitChange = { unit = it })
            }
        }

        composeRule.onNodeWithText("°C").performClick()
        composeRule.waitForIdle()
        assertEquals(TemperatureUnit.Celsius, unit)
        composeRule.onNodeWithText("°F").performClick()
        composeRule.waitForIdle()
        assertEquals(TemperatureUnit.Fahrenheit, unit)
    }
}
