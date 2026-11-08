package com.boatspark.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boatspark.app.BuildConfig
import com.boatspark.app.MainViewModel
import com.boatspark.app.core.auth.AuthenticationState
import com.boatspark.app.ui.screens.AlertsScreen
import com.boatspark.app.ui.screens.BoatListScreen
import com.boatspark.app.ui.screens.BoatStatusScreen
import com.boatspark.app.ui.screens.ProfileScreen
import com.boatspark.app.ui.screens.SettingsScreen
import com.boatspark.app.ui.screens.SignInScreen

private const val ROUTE_BOATS = "boats"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_STATUS = "status/{boatId}"
private const val ROUTE_ALERTS = "alerts/{boatId}"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun BoatSparkApp(
    viewModel: MainViewModel,
    onSignIn: () -> Unit,
) {
    val authentication by viewModel.authentication.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (authentication) {
            AuthenticationState.SignedIn -> {
                AuthenticatedApp(viewModel, snackbarHostState)
            }
            AuthenticationState.SignedOut -> {
                SignInScreen(
                    isLoading = uiState.isRefreshing,
                    configurationError = null,
                    onSignIn = onSignIn,
                )
            }
            is AuthenticationState.Error -> {
                SignInScreen(
                    isLoading = uiState.isRefreshing,
                    configurationError = (authentication as AuthenticationState.Error).message,
                    onSignIn = onSignIn,
                )
            }
        }

        if (uiState.isRefreshing && authentication == AuthenticationState.SignedIn) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
            )
        }
        if (authentication != AuthenticationState.SignedIn) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedApp(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val navController = rememberNavController()
    val boats by viewModel.boats.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                navController = navController,
                onRefresh = viewModel::refresh,
                onProfile = { navController.navigate(ROUTE_PROFILE) },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_BOATS,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_BOATS) {
                LaunchedEffect(Unit) { viewModel.clearSelectedBoat() }
                BoatListScreen(
                    boats = boats,
                    onBoatSelected = { boatId ->
                        viewModel.selectBoat(boatId)
                        navController.navigate("status/$boatId")
                    },
                )
            }
            composable(ROUTE_STATUS) { entry ->
                val boatId = entry.arguments?.getString("boatId")?.toLongOrNull()
                LaunchedEffect(boatId) {
                    boatId?.let(viewModel::selectBoat)
                }
                BoatStatusScreen(
                    cachedStatus = status,
                    osmTileUrl = BuildConfig.OSM_TILE_URL,
                    seamarkTileUrl = BuildConfig.SEAMARK_TILE_URL,
                    temperatureUnit = temperatureUnit,
                    onAlerts = { boatId -> navController.navigate("alerts/$boatId") },
                )
            }
            composable(ROUTE_ALERTS) { entry ->
                val boatId = entry.arguments?.getString("boatId")?.toLongOrNull()
                LaunchedEffect(boatId) {
                    boatId?.let(viewModel::selectBoat)
                }
                AlertsScreen(cachedAlerts = alerts)
            }
            composable(ROUTE_PROFILE) {
                LaunchedEffect(Unit) { viewModel.clearSelectedBoat() }
                ProfileScreen(
                    profile = profile,
                    version = BuildConfig.VERSION_NAME,
                    onSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onLogout = viewModel::logout,
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    temperatureUnit = temperatureUnit,
                    onTemperatureUnitChange = viewModel::setTemperatureUnit,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    navController: NavHostController,
    onRefresh: () -> Unit,
    onProfile: () -> Unit,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val canGoBack = currentEntry?.destination?.route != ROUTE_BOATS
    TopAppBar(
        title = { Text("BoatSpark") },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onProfile) {
                Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
            }
        },
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(24.dp),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
