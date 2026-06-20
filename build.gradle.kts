// Top-level build.gradle.kts — provides repositories and a clean task
// Restores a valid Kotlin DSL file (previously the CI workflow YAML was accidentally placed here).

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Provide a clean task to match common Android project structure
import org.gradle.api.tasks.Delete

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
