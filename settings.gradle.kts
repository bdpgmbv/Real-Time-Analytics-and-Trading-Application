pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "realtimeanalyticsandtrading"

// Modules are added one per build step rather than declared up front, so the
// build stays green at every commit.
include("common")
include("contracts")
