package com.boatspark.app

import android.app.Application

class BoatSparkApplication : Application() {
    val container by lazy { AppContainer(this) }
}
