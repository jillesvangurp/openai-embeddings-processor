
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.51.0"
}

refreshVersions {
}

rootProject.name = "openai-embeddings-processor"
