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
import com.boatspark.app.core.model.BoatStatus
import com.boatspark.app.core.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val isRefreshing: Boolean = false,
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

    private val mutableUiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            authentication.collect { state ->
                when (state) {
                    AuthenticationState.SignedIn -> {
                        container.refreshScheduler.schedule()
                        refreshNow()
                    }
                    AuthenticationState.SignedOut,
                    is AuthenticationState.Error,
                    -> {
                        container.refreshScheduler.cancel()
                        selectedBoatId.value = null
                        container.boatRepository.clear()
                        mutableUiState.value = MainUiState()
                    }
                }
            }
        }
    }

    suspend fun createSignInIntent(): Intent = container.authRepository.createAuthorizationIntent()

    fun completeSignIn(data: Intent) {
        viewModelScope.launch {
            mutableUiState.value = MainUiState(isRefreshing = true)
            val result = container.authRepository.handleAuthorizationResult(data)
            mutableUiState.value = MainUiState(message = result.exceptionOrNull()?.message)
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
        viewModelScope.launch { refreshNow() }
    }

    suspend fun pollWhileForeground() {
        while (viewModelScope.isActive) {
            if (authentication.value == AuthenticationState.SignedIn) {
                refreshNow()
            }
            delay(60_000)
        }
    }

    fun logout() {
        container.authRepository.clear()
    }

    fun showMessage(message: String) {
        mutableUiState.value = mutableUiState.value.copy(message = message)
    }

    fun consumeMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null)
    }

    private suspend fun refreshNow() {
        if (authentication.value != AuthenticationState.SignedIn || mutableUiState.value.isRefreshing) {
            return
        }
        mutableUiState.value = mutableUiState.value.copy(isRefreshing = true, message = null)
        try {
            val boatId = selectedBoatId.value
            if (boatId == null) {
                container.boatRepository.refreshUserAndBoats()
            } else {
                container.boatRepository.refreshBoat(boatId)
            }
            mutableUiState.value = mutableUiState.value.copy(isRefreshing = false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: AuthenticationRequiredException) {
            mutableUiState.value = MainUiState()
        } catch (error: Exception) {
            mutableUiState.value = MainUiState(message = error.message ?: "Refresh failed")
        }
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
