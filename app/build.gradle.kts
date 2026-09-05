import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class RequireReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val signingConfigured: Property<Boolean>

    @get:Input
    abstract val allowUnsigned: Property<Boolean>

    @TaskAction
    fun verifySigning() {
        check(signingConfigured.get() || allowUnsigned.get()) {
            "Release signing is not configured. Create the ignored keystore.properties file, " +
                "or use -Pboatspark.allowUnsignedRelease=true only to inspect an unsigned build."
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = releaseSigningKeys.all {
    !keystoreProperties.getProperty(it).isNullOrBlank()
}

fun configuredValue(name: String, fallback: String): String =
    localProperties.getProperty(name, fallback)

android {
    namespace = "com.boatspark.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.boatspark.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${configuredValue("boatspark.apiBaseUrl", "https://boatspark.example.com/")}\"",
        )
        buildConfigField(
            "String",
            "OIDC_ISSUER",
            "\"${configuredValue("boatspark.oidcIssuer", "https://id.example.com/")}\"",
        )
        buildConfigField(
            "String",
            "OIDC_CLIENT_ID",
            "\"${configuredValue("boatspark.oidcClientId", "replace-with-pocket-id-client-id")}\"",
        )
        buildConfigField(
            "String",
            "OSM_TILE_URL",
            "\"${configuredValue("boatspark.osmTileUrl", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")}\"",
        )
        buildConfigField(
            "String",
            "SEAMARK_TILE_URL",
            "\"${configuredValue("boatspark.seamarkTileUrl", "https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png")}\"",
        )

        manifestPlaceholders["appAuthRedirectScheme"] = "com.boatspark.app"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.appauth)
    implementation(libs.maplibre.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

room {
    schemaDirectory("$projectDir/schemas")
}

val requireReleaseSigning = tasks.register<RequireReleaseSigningTask>("requireReleaseSigning") {
    signingConfigured.set(hasReleaseSigning)
    allowUnsigned.set(
        providers.gradleProperty("boatspark.allowUnsignedRelease")
            .map(String::toBoolean)
            .orElse(false),
    )
}

tasks.matching { it.name == "packageRelease" }.configureEach {
    dependsOn(requireReleaseSigning)
}
