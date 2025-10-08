plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Firebase için gerekli olan Google Services eklentisi eklendi
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ismetguler.sehirtahmin"
    // compileSdk, projenizin hangi Android API seviyesinde derlendiğini belirtir.
    // Genellikle en son stabil versiyonu kullanmak en iyisidir.
    compileSdk = 35 // Android 15 (API 35) olarak güncellendi

    defaultConfig {
        applicationId = "com.ismetguler.sehirtahmin"
        // minSdk, uygulamanızın çalışabileceği en düşük Android API seviyesidir.
        minSdk = 24 // Android 7.0 (Nougat)
        // targetSdk, uygulamanızın test edildiği API seviyesidir.
        // Genellikle compileSdk ile aynı olması önerilir.
        targetSdk = 35 // Android 15 (API 35) olarak güncellendi
        versionCode = 12
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true // View Binding'i etkinleştir
    }
    viewBinding {
        enable = true
    }
}

// Bu blok, modül bağımlılıklarının nereden bulunacağını belirtir.
// settings.gradle.kts'te tanımlı olsa da, bazen burada da belirtmek sorunları çözebilir.
// Bu repositories bloğu, settings.gradle.kts'teki yapılandırma nedeniyle kaldırıldı.
/*
repositories {
    google() // Google'ın Maven deposu
    mavenCentral() // Maven Central deposu
}
*/

dependencies {
    implementation("com.google.android.gms:play-services-ads:24.5.0")
    implementation("com.google.android.material:material:1.12.0")
// En son sürümü kullan
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation ("com.google.code.gson:gson:2.13.1")
    // AndroidX Core kütüphaneleri - En son kararlı sürümleri kullanın
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    // ConstraintLayout - Güncel stabil versiyon
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.google.maps.android:android-maps-utils:3.14.0")
// Coğrafi hesaplamalar için
// BU SATIR OLMALI!
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    // Firebase BOM (Bill of Materials) eklendi. Bu, tüm Firebase kütüphanelerinin uyumlu versiyonlarını yönetir.
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    // Firebase Firestore kütüphanesi eklendi (Liderlik tablosu için)
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Firebase Authentication kütüphanesi eklendi (Anonim kullanıcı girişi için)
    implementation("com.google.firebase:firebase-auth-ktx")

    // Google Sign-In kütüphanesi tekrar eklendi (Google hesaplarıyla giriş için
    implementation("androidx.recyclerview:recyclerview:1.4.0")
// Veya en güncel versiyon
    implementation("androidx.cardview:cardview:1.0.0")
// Veya en güncel versiyon
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.cardview:cardview:1.0.0")
// En güncel versiyonu kullanabilirsin
    // Test bağımlılıkları
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
