import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class VerifyReleaseSigning : DefaultTask() {
    @get:Input abstract val keystorePath: Property<String>
    @get:Input abstract val storePassword: Property<String>
    @get:Input abstract val keyAlias: Property<String>
    @get:Input abstract val keyPassword: Property<String>

    @TaskAction
    fun verify() {
        val required = mapOf(
            "RELEASE_KEYSTORE_PATH" to keystorePath.get(),
            "KEYSTORE_PASSWORD" to storePassword.get(),
            "KEY_ALIAS" to keyAlias.get(),
            "KEY_PASSWORD" to keyPassword.get()
        )
        val missing = required.filterValues { it.isBlank() }.keys
        check(missing.isEmpty()) {
            "Release signing is not configured. Set ${required.keys.joinToString()} for a non-debug release key."
        }
        check(File(keystorePath.get()).isFile) {
            "Release signing keystore was not found at RELEASE_KEYSTORE_PATH."
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

android {
    namespace = "com.matedroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.manohar.tesmano"
        minSdk = 28
        targetSdk = 36
        versionCode = 178293972
        versionName = "0.1.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Embed git commit short SHA in BuildConfig for debugging
        val gitSha = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "UPSTREAM_BASELINE_SHA", "\"9333a14521e232147df4f47e72cccff68531ad10\"")
        buildConfigField("String", "PRODUCT_NAME", "\"TesMano\"")
    }

    signingConfigs {
        create("release") {
            val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!releaseKeystorePath.isNullOrBlank()) {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Disable dependency metadata for F-Droid compatibility
    // This block is encrypted with Google's key and unreadable by anyone else
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // Treat hardcoded text as an error to enforce localization
        error += "HardcodedText"
        error += "SetTextI18n"

        // Fail the build on errors
        abortOnError = true

        // Generate reports
        htmlReport = true
        xmlReport = true
    }

    testOptions {
        unitTests.all {
            it.jvmArgs("-Xmx1024m")
        }
    }
}

val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH")
val releaseStorePassword = providers.environmentVariable("KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD")

val verifyReleaseSigning by tasks.registering(VerifyReleaseSigning::class) {
    keystorePath.set(releaseKeystorePath.orElse(""))
    storePassword.set(releaseStorePassword.orElse(""))
    keyAlias.set(releaseKeyAlias.orElse(""))
    keyPassword.set(releaseKeyPassword.orElse(""))
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(verifyReleaseSigning)
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // DataStore
    implementation(libs.datastore.preferences)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Glance (home screen widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Maps
    implementation(libs.osmdroid)
    implementation(libs.androidx.window)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.glance.appwidget.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
