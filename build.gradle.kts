// Top-level build file. Plugin versions are managed in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Keep the resolved dependency graph reproducible on clean machines and in CI.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
