import org.gradle.api.JavaVersion
import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Загрузка и инкремент версии вынесены на уровень скрипта для стабильности
val versionPropsFile = file("${project.rootDir}/version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    FileInputStream(versionPropsFile).use { versionProps.load(it) }
} else {
    versionProps.setProperty("build_number", "100")
    versionProps.setProperty("major_version", "1")
    versionProps.setProperty("minor_version", "2")
    versionProps.setProperty("patch_version", "0")
}

val buildNumber: Int = versionProps.getProperty("build_number", "100").toInt()
val major: String = versionProps.getProperty("major_version", "1")
val minor: String = versionProps.getProperty("minor_version", "1")
val patch: String = versionProps.getProperty("patch_version", "0")

val taskNames = project.gradle.startParameter.taskNames
val isBuilding = taskNames.any { it.contains("assemble") || it.contains("bundle") || it.contains("install") }
val finalBuildNumber = if (isBuilding) {
    val next = buildNumber + 1
    versionProps.setProperty("build_number", next.toString())
    FileOutputStream(versionPropsFile).use { versionProps.store(it, null) }
    next
} else {
    buildNumber
}

android {
    namespace = "com.rosseti.cardata"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rosseti.cardata"
        minSdk = 30
        targetSdk = 37

        versionCode = finalBuildNumber
        versionName = "$major.$minor.$patch.$finalBuildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        viewBinding = true
        resValues = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        dex {
            useLegacyPackaging = false
        }
    }
    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
    buildToolsVersion = "36.1.0"
}

dependencies {
    implementation(libs.androidx.navigation.runtime.ktx)
	implementation(libs.firebase.config)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.material)
    implementation(libs.play.services.location)
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    
    // Accompanist
    implementation("com.google.accompanist:accompanist-pager:0.36.0")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.36.0")
    
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.core.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
