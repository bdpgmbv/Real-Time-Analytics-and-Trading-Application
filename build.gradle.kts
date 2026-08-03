plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.cyclonedx) apply false
}

tasks.register("checkAll") {
    group = "verification"
    description = "Builds and tests every module."

    dependsOn(
        subprojects
            .filter { it.buildFile.exists() }
            .map { "${it.path}:check" }
    )
}
