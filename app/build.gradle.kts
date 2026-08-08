kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.petro.smsapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.petro.smsapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "3.6.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
}

// Room اسکیمای دیتابیس رو (برای مایگریشن‌های بعدی) اینجا export می‌کنه 
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // SavedState
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation("androidx.core:core-ktx:1.13.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("sh.calvin.reorderable:reorderable:2.2.0")

    // Room - جایگزین همه‌ی *Store های SharedPreferences قبلی 
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore (Preferences) - جایگزین AppSettings و رمز بخش خصوصی 
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coil - برای نمایش عکسِ پروفایلِ مخاطبین (لیست مکالمات و صفحه‌ی چت) از content:// URI 
    implementation("io.coil-kt:coil-compose:2.6.0")
}
