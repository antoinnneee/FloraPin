// Build script racine — déclare les plugins partagés en `apply false`.
// Chaque module applique ce dont il a besoin via son propre build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Compagnon Windows (module :desktop). Déclarés ici car le plugin
    // Kotlin arrive déjà sur le classpath via AGP : sa version ne peut
    // plus être négociée depuis un sous-module.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
