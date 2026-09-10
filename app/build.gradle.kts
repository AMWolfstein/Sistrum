plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.hilt)
}

val releaseSigningPropertyNames = listOf(
    "SISTRUM_KEYSTORE_PATH",
    "SISTRUM_KEYSTORE_PASSWORD",
    "SISTRUM_KEY_ALIAS",
    "SISTRUM_KEY_PASSWORD"
)

android {
    namespace = "com.amwolfstein.sistrum"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.amwolfstein.sistrum"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            // Read lazily/optionally here so builds that don't need release
            // signing (e.g. assembleDebug on a machine without a release
            // keystore) never fail at configuration time. Absence is only
            // treated as an error if a release-signing task actually ends up
            // in the task graph - see the gradle.taskGraph.whenReady block
            // below.
            providers.gradleProperty("SISTRUM_KEYSTORE_PATH").orNull?.let { storeFile = file(it) }
            storePassword = providers.gradleProperty("SISTRUM_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.gradleProperty("SISTRUM_KEY_ALIAS").orNull
            keyPassword = providers.gradleProperty("SISTRUM_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    //standard material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    //m3expressive
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.compose.animation.core)

    //playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    //room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    //hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    //workmanager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    //metadata reading
    implementation(libs.taglib)
    implementation(libs.jaudiotagger)

    //networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)

    //plugins
    implementation(libs.coil.compose)
    implementation(libs.material.kolor)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.reorderable)
    implementation(libs.androidx.datastore.preferences)

    //testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Only enforce SISTRUM_* signing properties when a task that actually builds
// a signed release (assembleRelease, bundleRelease, ...) is in the requested
// task graph - debug builds must work for anyone who clones this repo
// without a release keystore.
val releaseSigningTaskRegex = Regex("^(assemble|bundle|package).*Release", RegexOption.IGNORE_CASE)

gradle.taskGraph.whenReady {
    val needsReleaseSigning = allTasks.any { releaseSigningTaskRegex.matches(it.name) }
    if (needsReleaseSigning) {
        val missing = releaseSigningPropertyNames.filter { providers.gradleProperty(it).orNull == null }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Cannot build a signed release: missing Gradle propert${if (missing.size == 1) "y" else "ies"} " +
                missing.joinToString(", ") + ". Set ${if (missing.size == 1) "it" else "them"} in your " +
                "machine-level ~/.gradle/gradle.properties (never in this project)."
            )
        }
    }
}