import com.github.jk1.license.render.JsonReportRenderer

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
    id("com.github.jk1.dependency-license-report") version "2.9"
}

licenseReport {
    outputDir = file("${projectDir}/src/main/assets").absolutePath
    renderers = arrayOf(JsonReportRenderer())
    excludeGroups = listOf("com.example").toTypedArray() // Exclude your own modules
}


android {
    namespace = "com.example.pelletswaterheater"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pelletswaterheater.new"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    // Ensure test tasks use JVM 17
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    implementation("androidx.compose.ui:ui-tooling-preview-android:1.7.0") //  koinViewModel() or get()


    implementation("com.github.albfernandez:juniversalchardet:2.4.0")


    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")

    testImplementation("org.mockito:mockito-core:5.12.0") // Added for Log mocking
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1") // Added for Kotlin-friendly mocking
    //testImplementation("org.robolectric:robolectric:4.12.2") // Added for Android classes in unit tests

    testImplementation("io.insert-koin:koin-test:3.5.6")
    implementation("io.insert-koin:koin-test:3.5.6")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
}

tasks.withType<Test> {
    useJUnitPlatform() // Enables JUnit 5 test discovery
}