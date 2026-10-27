// La sonde hebdomadaire (cadrage § 9) : un petit programme JVM qui lit les pages publiques
// avec le même lecteur que l'app (module core) et dit si l'accès tient. Lancé chaque jeudi par
// .github/workflows/sonde.yml ; à la main : ./gradlew :sonde:run
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
}

dependencies {
    implementation(project(":core"))
}

// La sortie part dans l'issue d'alerte : en UTF-8, accents compris, quel que soit le système.
tasks.named<JavaExec>("run") {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}
