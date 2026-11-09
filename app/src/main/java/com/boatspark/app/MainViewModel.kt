package com.boatspark.app

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boatspark.app.core.auth.AuthenticationRequiredException
import com.boatspark.app.core.auth.AuthenticationState
import com.boatspark.app.core.data.CachedValue
import com.boatspark.app.core.model.Boat
import com.boatspark.app.core.model.BoatAlerts
import com.boatspark.app.core.model.BoatHistory
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.UserProfile
import com.boatspark.app.core.settings.SettingsRepository
import com.boatspark.app.core.settings.StatusSection
import com.boatspark.app.core.settings.TemperatureUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Loaded(val history: BoatHistory) : HistoryState
    data class Failed(val message: String) : HistoryState
}

data class MainUiState(
    val isRefreshing: Boolean = false,
    val isStartingSignIn: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val authentication = container.authRepository.state
    val profile: StateFlow<CachedValue<UserProfile>?> = container.boatRepository.user
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val boats: StateFlow<List<CachedValue<Boat>>> = container.boatRepository.boats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedBoatId = MutableStateFlow<Long?>(null)
    val status: StateFlow<CachedValue<BoatStatus>?> = selectedBoatId
        .flatMapLatest { id -> id?.let(container.boatRepository::status) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val alerts: StateFlow<CachedValue<BoatAlerts>?> = selectedBoatId
        .flatMapLatest { id -> id?.let(container.boatRepository::alerts) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val temperatureUnit: StateFlow<TemperatureUnit> = container.settingsRepository.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_TEMPERATURE_UNIT)

    val expandedSections: StateFlow<Set<StatusSection>> = container.settingsRepository.expandedSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Only touched on the main thread.
    private var historyBoatId: Long? = null
    private var historyViewers = 0
    private var historyJob: Job? = null
    private var historyLoadedAt = 0L
    private var refreshJob: Job? = null
    private var refreshTarget: Long? = null

    private val signInIntentChannel = Channel<Intent>(Channel.BUFFERED)
    val signInIntents: Flow<Intent> = signInIntentChannel.receiveAsFlow()

    private val mutableHistory = MutableStateFlow<HistoryState?>(null)
    val history: StateFlow<HistoryState?> = mutableHistory.asStateFlow()

    private val mutableUiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            authentication.collect { state ->
                when (state) {
                    AuthenticationState.SignedIn -> {
                        container.refreshScheduler.schedule()
                        // Launched, not awaited, so the collector never misses a later sign-out.
                        requestRefresh()
                    }
                    AuthenticationState.SignedOut,
                    is AuthenticationState.Error,
                    -> {
                        container.refreshScheduler.cancel()
                        refreshJob?.cancel()
                        historyJob?.cancel()
                        selectedBoatId.value = null
                        historyBoatId = null
                        historyLoadedAt = 0L
                        mutableHistory.value = null
                        mutableUiState.value = MainUiState()
                        container.boatRepository.clear()
                    }
                }
            }
        }
    }

    fun startSignIn() {
        if (mutableUiState.value.isStartingSignIn) return
        mutableUiState.update { it.copy(isStartingSignIn = true) }
        viewModelScope.launch {
            try {
                signInIntentChannel.send(container.authRepository.createAuthorizationIntent())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showMessage(error.message ?: "Unable to start sign-in")
            } finally {
                mutableUiState.update { it.copy(isStartingSignIn = false) }
            }
        }
    }

    fun completeSignIn(data: Intent) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isRefreshing = true, message = null) }
            // Never show a previous account's cache, even if its sign-out cleanup was skipped.
            container.boatRepository.clear()
            val result = container.authRepository.handleAuthorizationResult(data)
            mutableUiState.update {
                it.copy(
                    isRefreshing = refreshJob?.isActive == true,
                    message = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun selectBoat(boatId: Long) {
        if (selectedBoatId.value == boatId) return
        selectedBoatId.value = boatId
        refresh()
    }

    fun clearSelectedBoat() {
        selectedBoatId.value = null
    }

    fun refresh() {
        requestRefresh()
        if (historyViewers > 0) loadHistory()
    }

    /** Called by each history screen as it appears; balanced by [closeHistory]. */
    fun openHistory(boatId: Long) {
        historyViewers++
        if (historyBoatId != boatId) {
            historyJob?.cancel()
            historyBoatId = boatId
            historyLoadedAt = 0L
            mutableHistory.value = null
        }
        if (!isHistoryFresh()) loadHistory()
    }

    fun closeHistory() {
        historyViewers = (historyViewers - 1).coerceAtLeast(0)
        if (historyViewers == 0) historyJob?.cancel()
    }

    private fun isHistoryFresh(): Boolean =
        mutableHistory.value is HistoryState.Loaded &&
            System.currentTimeMillis() - historyLoadedAt < HISTORY_MAX_AGE_MS

    private fun loadHistory() {
        val boatId = historyBoatId ?: return
        // Cancelling the previous load means an older response can never overwrite a newer one.
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            if (mutableHistory.value !is HistoryState.Loaded) {
                mutableHistory.value = HistoryState.Loading
            }
            val result = try {
                HistoryState.Loaded(container.boatRepository.history(boatId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                HistoryState.Failed(error.message ?: "History unavailable")
            }
            if (historyBoatId != boatId) return@launch
            if (result is HistoryState.Loaded) {
                mutableHistory.value = result
                historyLoadedAt = System.currentTimeMillis()
            } else if (mutableHistory.value !is HistoryState.Loaded) {
                // Keep showing the previous graph if a background refresh fails.
                mutableHistory.value = result
            }
        }
    }

    fun setSectionExpanded(section: StatusSection, expanded: Boolean) {
        viewModelScope.launch { container.settingsRepository.setSectionExpanded(section, expanded) }
    }

    suspend fun pollWhileForeground() {
        while (true) {
            if (authentication.value == AuthenticationState.SignedIn) {
                // The refresh runs in viewModelScope, so leaving the foreground cannot cancel it midway.
                requestRefresh()
                if (historyViewers > 0 && !isHistoryFresh()) loadHistory()
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    fun logout() {
        container.authRepository.clear()
        // Only an explicit sign-out resets settings; session expiry keeps them.
        viewModelScope.launch { container.settingsRepository.reset() }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { container.settingsRepository.setTemperatureUnit(unit) }
    }

    fun showMessage(message: String) {
        mutableUiState.value = mutableUiState.value.copy(message = message)
    }

    fun consumeMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null)
    }

    /**
     * Starts a refresh of the selected boat, or of the boat list when none is selected.
     * A refresh already running for the same target is reused; one for a different target
     * is replaced so a newly selected boat is never left waiting for the next poll.
     */
    private fun requestRefresh() {
        if (authentication.value != AuthenticationState.SignedIn) return
        val target = selectedBoatId.value ?: ALL_BOATS
        val running = refreshJob
        if (running != null && running.isActive) {
            if (refreshTarget == target) return
            running.cancel()
        }
        refreshTarget = target
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            mutableUiState.update { it.copy(isRefreshing = true, message = null) }
            var message: String? = null
            try {
                if (target == ALL_BOATS) {
                    container.boatRepository.refreshUserAndBoats()
                } else {
                    container.boatRepository.refreshBoat(target)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: AuthenticationRequiredException) {
                // The session ended; the sign-out handler resets the UI.
            } catch (error: Exception) {
                message = error.message ?: "Refresh failed"
            } finally {
                // A replaced refresh must not clear the indicator of its replacement.
                if (refreshJob === job) {
                    mutableUiState.update { it.copy(isRefreshing = false, message = message ?: it.message) }
                }
            }
        }
        refreshJob = job
        job.start()
    }

    private companion object {
        const val ALL_BOATS = -1L
        const val POLL_INTERVAL_MS = 60_000L
        const val HISTORY_MAX_AGE_MS = 5 * 60_000L
    }
}

class MainViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(container) as T
    }
}
