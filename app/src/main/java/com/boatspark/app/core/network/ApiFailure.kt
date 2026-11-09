package com.boatspark.app.core.network

import com.boatspark.app.core.model.ApiError
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationException
import retrofit2.HttpException

sealed class ApiFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Authentication(message: String) : ApiFailure(message)
    class Authorization(message: String) : ApiFailure(message)
    class Validation(message: String) : ApiFailure(message)
    class Connectivity(cause: IOException) : ApiFailure("Unable to reach BoatSpark", cause)
    class InvalidResponse(cause: Throwable) : ApiFailure("BoatSpark returned invalid data", cause)
    class Server(message: String) : ApiFailure(message)
}

suspend fun <T> apiCall(json: Json, block: suspend () -> T): T = try {
    block()
} catch (error: HttpException) {
    val detail = runCatching {
        json.decodeFromString<ApiError>(error.response()?.errorBody()?.string().orEmpty()).detail
    }.getOrNull() ?: "BoatSpark request failed (${error.code()})"
    throw when (error.code()) {
        401 -> ApiFailure.Authentication(detail)
        403 -> ApiFailure.Authorization(detail)
        422 -> ApiFailure.Validation(detail)
        in 500..599 -> ApiFailure.Server(detail)
        else -> ApiFailure.Server(detail)
    }
} catch (error: AccessTokenUnavailableException) {
    val cause = error.cause
    // A token refresh that failed for network reasons is retryable, not an auth failure.
    throw if (cause is AuthorizationException && cause.type == AuthorizationException.TYPE_GENERAL_ERROR) {
        ApiFailure.Connectivity(error)
    } else {
        cause
    }
} catch (error: IOException) {
    throw ApiFailure.Connectivity(error)
} catch (error: SerializationException) {
    throw ApiFailure.InvalidResponse(error)
}

