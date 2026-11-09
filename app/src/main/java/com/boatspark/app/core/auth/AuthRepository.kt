package com.boatspark.app.core.auth

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import java.util.concurrent.atomic.AtomicLong
import com.boatspark.app.core.config.AppConfig
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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

/** Lets data code tie API results to the session that requested them. */
interface SessionAuthority {
    fun currentGeneration(): Long

    /** Forces the next request in [generation] to refresh its access token first. */
    fun invalidateAccessToken(generation: Long)

    /** Ends the session, but only if it is still [generation]. */
    fun markSessionExpired(generation: Long)
}

class AuthRepository(
    private val config: AppConfig,
    private val authorizationService: AuthorizationService,
    private val store: EncryptedAuthStateStore,
) : SessionAuthority {
    private val authState = AtomicReference(loadState())
    private val sessionGeneration = AtomicLong()

    // Serialises session changes with credential writes so a late token callback
    // cannot persist an old session after sign-out.
    private val sessionLock = Any()
    private val mutableState = MutableStateFlow(
        if (authState.get().isAuthorized) AuthenticationState.SignedIn
        else AuthenticationState.SignedOut,
    )
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    suspend fun createAuthorizationIntent(): Intent {
        config.configurationError()?.let { throw IllegalStateException(it) }
        val serviceConfiguration = discoverConfiguration(config.oidcIssuer.toUri())
        val request = AuthorizationRequest.Builder(
            serviceConfiguration,
            config.oidcClientId,
            ResponseTypeValues.CODE,
            config.redirectUri,
        )
            .setScopes("openid", "email", "profile", "groups")
            .apply { if (store.requiresLoginPrompt()) setPrompt(AuthorizationRequest.Prompt.LOGIN) }
            .build()
        return authorizationService.getAuthorizationRequestIntent(request)
    }

    suspend fun handleAuthorizationResult(data: Intent): Result<Unit> = runCatching {
        val generation = sessionGeneration.get()
        val response = AuthorizationResponse.fromIntent(data)
        val authorizationError = AuthorizationException.fromIntent(data)
        if (authorizationError.isUserCancellation()) {
            throw SignInCancelledException()
        }
        if (response == null) {
            throw authorizationError ?: IllegalStateException("Pocket ID did not return an authorization response")
        }
        val state = AuthState(response, authorizationError)
        val tokenResponse = exchangeCode(response)
        state.update(tokenResponse, null)
        synchronized(sessionLock) {
            if (sessionGeneration.get() != generation) {
                throw AuthenticationRequiredException()
            }
            store.write(state.jsonSerializeString())
            store.setRequiresLoginPrompt(false)
            authState.set(state)
            mutableState.value = AuthenticationState.SignedIn
        }
    }.onFailure {
        if (it !is SignInCancelledException) {
            mutableState.value = AuthenticationState.Error(it.message ?: "Sign-in failed")
        }
    }

    suspend fun freshAccessToken(): String = suspendCancellableCoroutine { continuation ->
        val state = authState.get()
        val generation = sessionGeneration.get()
        if (!state.isAuthorized) {
            continuation.resumeWithException(AuthenticationRequiredException())
            return@suspendCancellableCoroutine
        }
        val previousAccessToken = state.accessToken
        val previousRefreshToken = state.refreshToken
        state.performActionWithFreshTokens(authorizationService) { accessToken, _, exception ->
            // AppAuth may call back on the main thread, so nothing here may throw.
            val outcome = runCatching {
                if (exception != null || accessToken == null) {
                    if (!state.isAuthorized) {
                        endSession(generation, state, "Your session expired. Sign in again.")
                    }
                    throw exception ?: AuthenticationRequiredException()
                }
                synchronized(sessionLock) {
                    if (sessionGeneration.get() != generation || authState.get() !== state) {
                        throw AuthenticationRequiredException()
                    }
                    if (accessToken != previousAccessToken || state.refreshToken != previousRefreshToken) {
                        store.write(state.jsonSerializeString())
                    }
                }
                accessToken
            }
            if (continuation.isActive) {
                outcome.fold(continuation::resume, continuation::resumeWithException)
            }
        }
    }

    override fun currentGeneration(): Long = sessionGeneration.get()

    override fun invalidateAccessToken(generation: Long) {
        val state = authState.get()
        if (sessionGeneration.get() == generation) {
            state.needsTokenRefresh = true
        }
    }

    override fun markSessionExpired(generation: Long) {
        endSession(generation, authState.get(), "Your session expired. Sign in again.")
    }

    fun clear() {
        synchronized(sessionLock) {
            sessionGeneration.incrementAndGet()
            authState.set(AuthState())
            runCatching { store.clear() }
            // An explicit sign-out should let the user pick an account next time.
            runCatching { store.setRequiresLoginPrompt(true) }
            mutableState.value = AuthenticationState.SignedOut
        }
    }

    private fun endSession(generation: Long, state: AuthState, message: String) {
        synchronized(sessionLock) {
            if (authState.get() !== state ||
                !sessionGeneration.compareAndSet(generation, generation + 1)
            ) {
                return
            }
            authState.set(AuthState())
            runCatching { store.clear() }
            mutableState.value = AuthenticationState.Error(message)
        }
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

    private fun loadState(): AuthState = try {
        store.read()?.let(AuthState::jsonDeserialize) ?: AuthState()
    } catch (_: Exception) {
        runCatching { store.clear() }
        AuthState()
    }
}

class AuthenticationRequiredException : Exception("Authentication is required")

class SignInCancelledException : Exception(MESSAGE) {
    companion object {
        const val MESSAGE = "Sign-in was cancelled"
    }
}

private fun AuthorizationException?.isUserCancellation(): Boolean =
    this != null &&
        type == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.type &&
        code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code
