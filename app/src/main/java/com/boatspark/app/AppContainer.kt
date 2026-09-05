package com.boatspark.app

import android.content.Context
import androidx.room.Room
import com.boatspark.app.core.auth.AuthRepository
import com.boatspark.app.core.auth.EncryptedAuthStateStore
import com.boatspark.app.core.config.AppConfig
import com.boatspark.app.core.data.BoatRepository
import com.boatspark.app.core.data.RefreshScheduler
import com.boatspark.app.core.database.BoatSparkDatabase
import com.boatspark.app.core.network.AccessTokenInterceptor
import com.boatspark.app.core.network.BoatSparkApi
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AppContainer(context: Context) {
    val config = AppConfig()
    val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }
    val authRepository = AuthRepository(
        config,
        AuthorizationService(context),
        EncryptedAuthStateStore(context),
    )
    private val database = Room.databaseBuilder(
        context,
        BoatSparkDatabase::class.java,
        "boatspark.db",
    ).build()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AccessTokenInterceptor(authRepository))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .build()
    private val api = Retrofit.Builder()
        .baseUrl(config.apiBaseUrl)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BoatSparkApi::class.java)

    val boatRepository = BoatRepository(
        api,
        database.dao(),
        json,
        authRepository::markSessionExpired,
    )
    val refreshScheduler = RefreshScheduler(context)
}

