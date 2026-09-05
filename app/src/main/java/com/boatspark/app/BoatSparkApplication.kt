package com.boatspark.app

import android.app.Application
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class BoatSparkApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .addNetworkInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "BoatSpark/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                            )
                            .build(),
                    )
                }
                .build(),
        )
    }
}
