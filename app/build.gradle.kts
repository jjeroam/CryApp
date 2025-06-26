plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "com.example.babycry"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.babycry"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        mlModelBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        pickFirsts += listOf(
            "lib/**/libtensorflowlite_jni.so",
            "lib/**/libtask_audio_jni.so"
        )

        packagingOptions {
            jniLibs {
                useLegacyPackaging = true
            }
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }

        packaging {
            jniLibs {
                useLegacyPackaging = true
            }
        }
    }

    dependencies {
        // AndroidX + Material
        implementation(libs.appcompat)
        implementation(libs.material)
        implementation(libs.constraintlayout)
        implementation(libs.activity)
        implementation("androidx.core:core-ktx:1.10.1")


        // Lifecycle & ViewModel
        implementation(libs.lifecycle.runtime.ktx)
        implementation(libs.lifecycle.livedata.ktx)
        implementation(libs.lifecycle.viewmodel.ktx)

        // Navigation
        implementation(libs.navigation.fragment)
        implementation(libs.navigation.ui)

        // Compose
        implementation(platform(libs.compose.bom))
        implementation(libs.ui)
        implementation(libs.ui.graphics)
        implementation(libs.ui.tooling.preview)
        implementation(libs.material3)
        implementation(libs.activity.compose)

        // TensorFlow Lite (Pinned Versions)
        implementation("org.tensorflow:tensorflow-lite:2.13.0")
        implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
        implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.4")
        implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")

//        implementation("com.github.JorenSix:TarsosDSP:2.4")


        // Charting
        implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

        // SQLite
        implementation("androidx.sqlite:sqlite:2.1.0")

        // JSON/GSON
        implementation("com.google.code.gson:gson:2.8.8")

        // Retrofit
        implementation("com.squareup.retrofit2:retrofit:2.9.0")
        implementation("com.squareup.retrofit2:converter-gson:2.9.0")

        // GIF Support
        implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.23")


        // Annotation
        implementation(libs.annotation)

        // Testing
        testImplementation(libs.junit)
        androidTestImplementation(libs.ext.junit)
        androidTestImplementation(libs.espresso.core)
        androidTestImplementation(platform(libs.compose.bom))
        androidTestImplementation(libs.ui.test.junit4)
        debugImplementation(libs.ui.tooling)
        debugImplementation(libs.ui.test.manifest)
    }
}
dependencies {
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.material)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
