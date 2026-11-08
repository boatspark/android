package com.boatspark.app.core.config

import android.net.Uri
import androidx.core.net.toUri
import com.boatspark.app.BuildConfig

data class AppConfig(
    val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    val oidcIssuer: String = BuildConfig.OIDC_ISSUER,
    val oidcClientId: String = BuildConfig.OIDC_CLIENT_ID,
    val osmTileUrl: String = BuildConfig.OSM_TILE_URL,
    val seamarkTileUrl: String = BuildConfig.SEAMARK_TILE_URL,
) {
    val redirectUri: Uri = "com.boatspark.app:/oauth2redirect".toUri()

    fun configurationError(): String? = when {
        apiBaseUrl.contains("example.com") -> "Set boatspark.apiBaseUrl in local.properties"
        oidcIssuer.contains("example.com") -> "Set boatspark.oidcIssuer in local.properties"
        oidcClientId.startsWith("replace-") -> "Set boatspark.oidcClientId in local.properties"
        !apiBaseUrl.startsWith("https://") -> "BoatSpark API URL must use HTTPS"
        !oidcIssuer.startsWith("https://") -> "Pocket ID issuer URL must use HTTPS"
        else -> null
    }
}

