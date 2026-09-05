package com.boatspark.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.core.model.Boat
import com.boatspark.app.ui.screens.BoatListScreen
import com.boatspark.app.ui.screens.SignInScreen
import com.boatspark.app.ui.theme.BoatSparkTheme
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
}

