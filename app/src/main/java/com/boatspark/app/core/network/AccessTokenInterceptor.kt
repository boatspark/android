package com.boatspark.app.core.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AccessTokenInterceptor(
    private val accessToken: suspend () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp rethrows non-IOExceptions from interceptors on its dispatcher
        // thread, crashing the app, so auth failures travel as an IOException.
        val token = try {
            runBlocking { accessToken() }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw AccessTokenUnavailableException(error)
        }
        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }
}

class AccessTokenUnavailableException(
    override val cause: Exception,
) : IOException(cause.message, cause)
