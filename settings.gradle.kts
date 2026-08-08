pluginManagement {
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


include("ingest")
include("calculate")
include("platform")
include("api")
include("jobs:exposure")
include("gateway")
