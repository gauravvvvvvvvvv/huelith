import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.lifecycle.runtime.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "cc.gvrv.huelith"
    compileSdk = 36
    defaultConfig {
        applicationId = "cc.gvrv.huelith"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.3"
        ndk { abiFilters.add("arm64-v8a") }
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        getByName("main").jniLibs.srcDirs("src/androidMain/jniLibs")
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Never ship a debug APK that crashes with UnsatisfiedLinkError on the first edit.
// Building this target always requires the ARM64 Rust engine produced by build-native.
val checkNativeEngine by tasks.registering {
    group = "verification"
    description = "Checks that the bundled offline upstream Rust engine exists"
    doLast {
        val so = file("src/androidMain/jniLibs/arm64-v8a/libhuelith_mobile_ffi.so")
        check(so.isFile && so.length() > 64 * 1024) {
            "Missing ARM64 Rust library: $so. Run scripts/build-native.ps1 or scripts/build-native.sh before Gradle assembleDebug."
        }
    }
}
tasks.configureEach {
    if (name == "preBuild") dependsOn(checkNativeEngine)
}
