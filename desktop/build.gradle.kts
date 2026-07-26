import java.security.MessageDigest
import java.time.LocalDate
import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
}

// ── Configuration de build ────────────────────────────────────────────────
// Mêmes sources que le module Android (local.properties non commité, sinon
// variable d'environnement), afin qu'un poste déjà configuré pour l'app
// Android construise le compagnon sans réglage supplémentaire.
fun localProp(key: String): String? {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    return props.getProperty(key) ?: System.getenv(key)
}

// Le compagnon parle au backend de production par défaut : il n'a aucune
// utilité hors ligne face à un serveur local, contrairement à l'app Android.
val apiBaseUrl: String = localProp("API_BASE_URL")
    ?: "https://florapin.pattounecorp.ovh/api/v1/"
val maptilerApiKey: String = localProp("MAPTILER_API_KEY") ?: ""

// Sources partagées avec le module Android : du Kotlin pur (Moshi, Retrofit,
// OkHttp, Compose) sans aucune API Android. Les partager plutôt que les
// dupliquer garantit que les contrats d'API restent synchronisés avec le
// backend, et que la charte graphique ne diverge pas entre les deux clients.
val sharedSourceDirs = listOf(
    "../app/src/main/java/com/florapin/app/network/dto",
    "../app/src/main/java/com/florapin/app/network/api",
    "../app/src/main/java/com/florapin/app/network/auth",
    "../app/src/main/java/com/florapin/app/ui/theme",
)

// Exceptions : ces fichiers du même dossier dépendent du framework Android.
// Le compagnon fournit ses propres équivalents (stockage de jetons, thème et
// typographie desktop).
val androidOnlySources = listOf(
    "**/EncryptedTokenStore.kt",
    "**/Theme.kt",
    "**/Type.kt",
)

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.apply {
        sharedSourceDirs.forEach { srcDir(it) }
        androidOnlySources.forEach { exclude(it) }
    }
}

/**
 * Écrit la configuration dans les ressources plutôt que dans un BuildConfig
 * généré : l'utilisateur peut ainsi pointer une autre instance du backend en
 * éditant un fichier, sans reconstruire (voir DesktopConfig).
 */
val generateConfigProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/florapinConfig")
    // Valeurs recopiées dans des variables locales : les lire depuis le corps
    // de la tâche capturerait une référence au script de build, que le
    // configuration cache de Gradle ne sait pas sérialiser.
    val baseUrl = apiBaseUrl
    val mapKey = maptilerApiKey
    outputs.dir(outputDir)
    inputs.property("apiBaseUrl", baseUrl)
    inputs.property("maptilerApiKey", mapKey)
    doLast {
        val file = outputDir.get().file("florapin.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("# Généré par Gradle — ne pas éditer ici.")
                appendLine("# Surcharge utilisateur : %LOCALAPPDATA%/FloraPin/config.properties")
                appendLine("apiBaseUrl=$baseUrl")
                appendLine("maptilerApiKey=$mapKey")
            },
        )
    }
}

sourceSets["main"].resources.srcDir(generateConfigProperties)

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    // Compose Desktop poste ses frames sur l'EDT Swing : sans ce dispatcher,
    // Dispatchers.Main n'est pas résolu au runtime.
    implementation(libs.kotlinx.coroutines.swing)

    // Pile réseau identique à celle de l'app Android (contrats partagés).
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    testImplementation(libs.junit)
    // kotlin-test se règle sur le moteur configuré (JUnit 4 ici) et donne des
    // assertions plus lisibles que celles de JUnit.
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Version du compagnon, indépendante de celle de l'app mobile : les deux ne
// sont pas publiées ensemble. Reprise par l'archive et par le manifeste lu par
// la vitrine.
val companionVersion = "1.0.0"

compose.desktop {
    application {
        mainClass = "com.florapin.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "FloraPin"
            packageVersion = companionVersion
            description = "Compagnon Windows FloraPin — photos, albums et carte"
            vendor = "FloraPin"
            copyright = "© FloraPin"

            // jpackage n'embarque que les modules détectés statiquement ; la
            // réflexion d'OkHttp/Retrofit en fait manquer certains, ce qui ne
            // casse qu'à l'exécution du MSI. On embarque tout le JDK.
            includeAllModules = true

            windows {
                menu = true
                menuGroup = "FloraPin"
                shortcut = true
                dirChooser = true
                // UUID stable : sans lui, chaque build s'installe en parallèle
                // au lieu de mettre à jour l'installation existante.
                upgradeUuid = "6E2C9A31-4F8B-4B7D-9A0E-1C5D7F3B2A64"
            }
        }
    }
}

// ── Distribution par la vitrine ────────────────────────────────────────────
// L'installeur MSI exige WiX Toolset, absent des postes de développement. On
// publie donc une archive portable : décompresser, lancer FloraPin.exe. Elle
// embarque son propre runtime Java, d'où son poids — aucune installation de
// Java n'est requise sur le poste de l'utilisateur.

/** Dossier servi tel quel par Astro (landing/public → racine du site). */
val landingDownloads = rootProject.layout.projectDirectory.dir("landing/public/telechargements")

val windowsArchiveName = "FloraPin-$companionVersion-windows.zip"

val packageWindowsArchive by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Archive portable du compagnon, déposée dans la vitrine."
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set(windowsArchiveName)
    destinationDirectory.set(landingDownloads)
}

/**
 * Écrit le manifeste lu par la vitrine. Sans lui, la page n'aurait aucun moyen
 * d'annoncer une taille et une version exactes — or un bouton de
 * téléchargement qui ment sur le poids du fichier est une mauvaise surprise.
 */
val publishWindowsRelease by tasks.registering {
    group = "distribution"
    description = "Met à jour landing/src/windows-release.json depuis l'archive produite."
    dependsOn(packageWindowsArchive)

    val archive = landingDownloads.file(windowsArchiveName)
    val manifest = rootProject.layout.projectDirectory.file("landing/src/windows-release.json")
    val version = companionVersion
    val fileName = windowsArchiveName
    inputs.file(archive)
    outputs.file(manifest)

    doLast {
        val file = archive.asFile
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val builtAt = LocalDate.now().toString()
        manifest.asFile.writeText(
            """
            {
              "available": true,
              "version": "$version",
              "fileName": "$fileName",
              "sizeBytes": ${file.length()},
              "sha256": "$sha",
              "builtAt": "$builtAt"
            }

            """.trimIndent(),
        )
        logger.lifecycle("Archive Windows : ${file.length() / 1024 / 1024} Mo → ${manifest.asFile}")
    }
}
