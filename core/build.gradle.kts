// Kotlin pur, sans Android : le modèle et, plus tard, le planificateur (cadrage § 7).
// Ses tests tournent en JVM simple, sans Robolectric ni SDK.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Cible Java 17 comme l'app, sans exiger un JDK 17 précis sur la machine (la CI a le 17,
// un conteneur cloud peut avoir le 21) : une « toolchain » irait en télécharger un.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    testImplementation(libs.junit)
}
