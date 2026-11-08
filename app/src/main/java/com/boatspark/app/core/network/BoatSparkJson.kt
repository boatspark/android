package com.boatspark.app.core.network

import kotlinx.serialization.json.Json

// Ignore unknown keys so new server fields don't break older app versions.
val BoatSparkJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
