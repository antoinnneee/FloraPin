import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Clé MapTiler lue depuis local.properties (non commité) ou une variable
// d'environnement (utile en CI). Vide par défaut : la carte affiche alors un
// message expliquant qu'il faut configurer la clé.
val maptilerApiKey: String = run {
    val props = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { props.load(it) }
    }
    props.getProperty("MAPTILER_API_KEY")
        ?: System.getenv("MAPTILER_API_KEY")
        ?: ""
}

// URL de base de l'API. Surchargée par local.properties / env si fournie ;
// sinon valeur par défaut adaptée au type de build (voir buildTypes).
val apiBaseUrlOverride: String? = run {
    val props = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { props.load(it) }
    }
    props.getProperty("API_BASE_URL") ?: System.getenv("API_BASE_URL")
}

// URL de la politique de confidentialité (lien in-app, exigence Play Store).
// Surchargeable, sinon la page de la vitrine FloraPin (NODE-77).
val privacyPolicyUrl: String = run {
    val props = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { props.load(it) }
    }
    props.getProperty("PRIVACY_POLICY_URL")
        ?: System.getenv("PRIVACY_POLICY_URL")
        ?: "https://florapin.pattounecorp.ovh/politique-de-confidentialite"
}

// Secrets de signature release, lus depuis keystore.properties (gitignoré) ou
// des variables d'environnement (CI). Absents en dev/POC : le build release
// reste alors non signé (la config de signature n'est tout simplement pas posée).
val keystoreProps: Properties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun keystoreProp(key: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(key)
val releaseStoreFile: String? = keystoreProp("RELEASE_STORE_FILE")
val hasReleaseSigning: Boolean =
    releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.florapin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.florapin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.11.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MAPTILER_API_KEY", "\"$maptilerApiKey\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = keystoreProp("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProp("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 = hôte depuis l'émulateur Android.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${apiBaseUrlOverride ?: "http://10.0.2.2:3000/api/v1/"}\"",
            )
            // Crashlytics désactivé en debug : pas de collecte runtime, et on
            // n'upload pas le mapping (build plus rapide). Voir la meta-data
            // `firebase_crashlytics_collection_enabled` du manifeste.
            resValue("bool", "crashlytics_collection_enabled", "false")
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Symboles de débogage natifs (libs tierces avec code natif, ex.
            // SQLite/MapLibre) inclus dans le bundle pour un déobfuscation
            // correcte des plantages natifs côté Play Console.
            ndk {
                debugSymbolLevel = "FULL"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Crashlytics actif en release : collecte runtime + upload du mapping
            // R8 pour désobfusquer les stacktraces.
            resValue("bool", "crashlytics_collection_enabled", "true")
            // Signé seulement si un keystore est fourni (sinon build non signé).
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${apiBaseUrlOverride ?: "https://florapin.pattounecorp.ovh/api/v1/"}\"",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.text.google.fonts)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Coil (affichage de la photo capturée)
    implementation(libs.coil.compose)

    // Localisation (FusedLocationProvider)
    implementation(libs.play.services.location)

    // Carte (MapLibre GL)
    implementation(libs.maplibre.android)

    // Réseau (Retrofit + OkHttp + Moshi)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    // Stockage sécurisé des tokens
    implementation(libs.androidx.security.crypto)

    // Synchronisation en arrière-plan
    implementation(libs.androidx.work.runtime.ktx)

    // Push (Firebase Cloud Messaging) — versions alignées par le BOM.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // Crashlytics (reporting de crashs) — version alignée par le BOM.
    implementation(libs.firebase.crashlytics)

    // Room (persistance locale)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
