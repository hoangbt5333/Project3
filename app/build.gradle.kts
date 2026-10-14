plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.testcamera"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.testcamera"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    implementation("com.github.jiangdongguo.AndroidUSBCamera:libausbc:3.2.7")
    implementation("com.github.jiangdongguo.AndroidUSBCamera:libnative:3.2.7")
    implementation("com.github.jiangdongguo.AndroidUSBCamera:libuvc:3.2.7")

    // Google ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.7")
}

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.gyf.immersionbar:immersionbar"))
            .using(module("com.geyifeng.immersionbar:immersionbar:3.2.2"))
        substitute(module("com.zlc.glide:webpdecoder"))
            .using(module("com.github.zjupure:webpdecoder:2.7.4.16.0"))
    }
}
