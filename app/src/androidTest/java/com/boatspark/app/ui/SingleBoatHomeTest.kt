package com.boatspark.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SingleBoatHomeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private var boatIds by mutableStateOf(listOf<Long>())

    private fun setContent() {
        composeRule.setContent {
            navController = rememberNavController()
            SingleBoatHome(navController, boatIds)
            NavHost(navController, startDestination = ROUTE_BOATS) {
                composable(ROUTE_BOATS) { Text("Boats") }
                composable(ROUTE_STATUS) { Text("Status") }
                composable("profile") { Text("Profile") }
            }
        }
    }

    private val currentRoute get() = navController.currentDestination?.route

    @Test
    fun singleBoatSkipsListAndListReturnsWhenAnotherBoatAppears() {
        setContent()
        composeRule.waitForIdle()
        assertEquals(ROUTE_BOATS, currentRoute)

        boatIds = listOf(7)
        composeRule.waitForIdle()
        assertEquals(ROUTE_STATUS, currentRoute)
        assertEquals("7", navController.currentBackStackEntry?.arguments?.getString("boatId"))
        assertNull(navController.previousBackStackEntry)

        boatIds = listOf(7, 8)
        composeRule.waitForIdle()
        assertEquals(ROUTE_BOATS, currentRoute)
        assertNull(navController.previousBackStackEntry)
    }

    @Test
    fun anotherBoatDoesNotInterruptProfileButRestoresListOnReturn() {
        boatIds = listOf(7)
        setContent()
        composeRule.waitForIdle()
        composeRule.runOnUiThread { navController.navigate("profile") }
        composeRule.waitForIdle()

        boatIds = listOf(7, 8)
        composeRule.waitForIdle()
        assertEquals("profile", currentRoute)

        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()
        assertEquals(ROUTE_BOATS, currentRoute)
    }
}
