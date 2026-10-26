import com.android.build.api.variant.impl.VariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// `keystore-release/keystore.properties` porte le chemin du `.jks` réel et ses mots de passe :
// jamais versionné (`.gitignore`, docs/publication.md). Absent (clone frais, conteneur cloud,
// CI sans les secrets), la variante release compile un APK non signé plutôt que de faire
// échouer toute la configuration Gradle. Gabarit : `keystore-release/keystore.properties.example`.
val proprietesRelease = file("keystore-release/keystore.properties")
val signatureReleaseDisponible = proprietesRelease.exists()

android {
    namespace = "fr.jrrocks.carnetderecette"
    compileSdk = 35

    signingConfigs {
        // Clé de debug fixe, versionnée (procédé de Popote) : une signature stable d'un
        // conteneur cloud à l'autre pour les builds locaux et les tests. Ce dépôt étant
        // public, cette clé l'est aussi : son SHA-1 n'est **jamais** déclaré au client OAuth
        // Google (docs/google-cloud.md). Seuls les APK signés de la clé release se connectent
        // au Drive. Alias et mots de passe sont ceux d'AGP par défaut.
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (signatureReleaseDisponible) {
            create("release") {
                val proprietes = Properties().apply { load(FileInputStream(proprietesRelease)) }
                storeFile = file("keystore-release/${proprietes.getProperty("storeFile")}")
                keyAlias = proprietes.getProperty("keyAlias")
                // La CI passe les mots de passe en variables d'environnement plutôt que dans le
                // fichier : `Properties.load` traite `\` comme un échappement et retire les
                // espaces de tête, ce qui corromprait un mot de passe généré (seconde relecture
                // du lot 1). Le fichier reste le repli pour une compilation sur un ordinateur.
                storePassword = System.getenv("SIGNATURE_STORE_PASSWORD") ?: proprietes.getProperty("storePassword")
                keyPassword = System.getenv("SIGNATURE_KEY_PASSWORD") ?: proprietes.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        // Définitif dès la première version distribuée : Android identifie l'app par lui, et
        // le client OAuth Android de Google Cloud aussi (docs/google-cloud.md).
        applicationId = "fr.jrrocks.carnetderecette"
        minSdk = 26
        targetSdk = 35
        // `versionCode` décide de la mise à jour en ligne (`maj/MiseAJour.kt`) : la CI le
        // recopie dans le nom de l'APK publié et l'app compare le sien à celui-là. L'oublier
        // en publiant, c'est publier une version que personne n'installera.
        versionCode = 2
        versionName = "0.2-correspondance"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signatureReleaseDisponible) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")
}

androidComponents {
    // Les tests s'appuient sur `ui-test-manifest`, fourni en debug seulement.
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enableUnitTest = false
    }
    // `Carnet-de-recette.apk`, ou `Carnet-de-recette-unsigned.apk` sans clé release : c'est sur
    // ce suffixe que `.github/workflows/version.yml` refuse de publier un APK non signé (AGP ne
    // l'ajoute plus une fois `outputFileName` fixé). `outputFileName` n'est exposé que par
    // l'implémentation interne : c'est le procédé documenté par AGP, repris de Popote.
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { sortie ->
            (sortie as? VariantOutputImpl)?.outputFileName?.set(
                if (signatureReleaseDisponible) "Carnet-de-recette.apk" else "Carnet-de-recette-unsigned.apk",
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // L'état de l'accueil vit dans un ViewModel : il survit à une rotation (cf. AccueilViewModel).
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    // Réponses de Drive et de GitHub lues en `JsonObject`, sans plugin de sérialisation.
    implementation(libs.kotlinx.serialization.json)
    // Autorisation Google (`Identity.getAuthorizationClient`), moteur `google-drive-appdata`.
    implementation(libs.play.services.auth)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
