package com.boatspark.app.core.auth

import android.content.Intent
import android.net.Uri
import java.util.concurrent.atomic.AtomicLong
import com.boatspark.app.core.config.AppConfig
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AuthenticationState {
    data object SignedOut : AuthenticationState
    data object SignedIn : AuthenticationState
    data class Error(val message: String) : AuthenticationState
}

class AuthRepository(
    private val config: AppConfig,
    private val authorizationService: AuthorizationService,
    private val store: EncryptedAuthStateStore,
) {
    private val authState = AtomicReference(loadState())
    private val sessionGeneration = AtomicLong()
    private val mutableState = MutableStateFlow(
        if (authState.get().isAuthorized) AuthenticationState.SignedIn
        else AuthenticationState.SignedOut,
    )
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    suspend fun createAuthorizationIntent(): Intent {
        config.configurationError()?.let { throw IllegalStateException(it) }
        val serviceConfiguration = discoverConfiguration(Uri.parse(config.oidcIssuer))
        val request = AuthorizationRequest.Builder(
            serviceConfiguration,
            config.oidcClientId,
            ResponseTypeValues.CODE,
            config.redirectUri,
        )
            .setScopes("openid", "email", "profile", "groups")
            .build()
        return authorizationService.getAuthorizationRequestIntent(request)
    }

    suspend fun handleAuthorizationResult(data: Intent): Result<Unit> = runCatching {
        val generation = sessionGeneration.get()
        val response = AuthorizationResponse.fromIntent(data)
        val authorizationError = AuthorizationException.fromIntent(data)
        if (response == null) {
            throw authorizationError ?: IllegalStateException("Pocket ID did not return an authorization response")
        }
        val state = AuthState(response, authorizationError)
        val tokenResponse = exchangeCode(response)
        state.update(tokenResponse, null)
        if (sessionGeneration.get() != generation) {
            throw AuthenticationRequiredException()
        }
        authState.set(state)
        persist(state)
        mutableState.value = AuthenticationState.SignedIn
    }.onFailure {
        mutableState.value = AuthenticationState.Error(it.message ?: "Sign-in failed")
    }

    suspend fun freshAccessToken(): String = suspendCancellableCoroutine { continuation ->
        val state = authState.get()
        val generation = sessionGeneration.get()
        if (!state.isAuthorized) {
            continuation.resumeWithException(AuthenticationRequiredException())
            return@suspendCancellableCoroutine
        }
        state.performActionWithFreshTokens(authorizationService) { accessToken, _, exception ->
            if (exception != null || accessToken == null) {
                if (!state.isAuthorized &&
                    authState.get() === state &&
                    sessionGeneration.compareAndSet(generation, generation + 1)
                ) {
                    authState.set(AuthState())
                    store.clear()
                    mutableState.value =
                        AuthenticationState.Error("Your session expired. Sign in again.")
                }
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        exception ?: AuthenticationRequiredException(),
                    )
                }
            } else if (sessionGeneration.get() != generation || authState.get() !== state) {
                if (continuation.isActive) {
                    continuation.resumeWithException(AuthenticationRequiredException())
                }
            } else {
                persist(state)
                if (continuation.isActive) {
                    continuation.resume(accessToken)
                }
            }
        }
    }

    fun markSessionExpired() {
        sessionGeneration.incrementAndGet()
        authState.set(AuthState())
        store.clear()
        mutableState.value = AuthenticationState.Error("Your session expired. Sign in again.")
    }

    fun clear() {
        sessionGeneration.incrementAndGet()
        authState.set(AuthState())
        store.clear()
        mutableState.value = AuthenticationState.SignedOut
    }

    private suspend fun discoverConfiguration(issuer: Uri): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { continuation ->
            AuthorizationServiceConfiguration.fetchFromIssuer(issuer) { configuration, exception ->
                if (configuration != null) continuation.resume(configuration)
                else continuation.resumeWithException(
                    exception ?: IllegalStateException("Pocket ID discovery failed"),
                )
            }
        }

    private suspend fun exchangeCode(response: AuthorizationResponse) =
        suspendCancellableCoroutine { continuation ->
            authorizationService.performTokenRequest(response.createTokenExchangeRequest()) {
                    tokenResponse,
                    exception,
                ->
                if (tokenResponse != null) continuation.resume(tokenResponse)
                else continuation.resumeWithException(
                    exception ?: IllegalStateException("Pocket ID token exchange failed"),
                )
            }
        }

    private fun persist(state: AuthState) {
        store.write(state.jsonSerializeString())
    }

    private fun loadState(): AuthState = try {
        store.read()?.let(AuthState::jsonDeserialize) ?: AuthState()
    } catch (_: Exception) {
        store.clear()
        AuthState()
    }
}

class AuthenticationRequiredException : Exception("Authentication is required")
