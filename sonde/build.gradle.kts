// La sonde hebdomadaire (cadrage § 9) : un petit programme JVM qui lit les pages publiques
// avec le même lecteur que l'app (module core) et dit si l'accès tient. Lancé chaque jeudi par
// .github/workflows/sonde.yml, depuis son script de démarrage (installDist) : `gradlew run`
// remplacerait son code de sortie 2 (« on ne conclut rien ») par 1 (« accès rompu »).
// À la main : ./gradlew :sonde:installDist && sonde/build/install/sonde/bin/sonde
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

application {
    mainClass.set("fr.jrrocks.carnetderecette.sonde.SondeKt")
    // La sortie part dans l'issue d'alerte : en UTF-8, accents compris, quel que soit le système.
    // Pour le script de démarrage comme pour `gradlew run`.
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

dependencies {
    implementation(project(":core"))
}
