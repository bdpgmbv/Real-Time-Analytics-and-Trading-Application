// The root build deliberately holds no shared configuration. Cross-module concerns
// live in build-logic convention plugins, which each module applies explicitly.
//
// These are declared here only to load them into the root classloader scope. Plugins
// that register a shared build service - Spotless and SpotBugs both do - otherwise
// get a separate class per sibling project and fail when the service is handed
// across. `apply false` means the root project itself gets none of them.
plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.cyclonedx) apply false
}

tasks.register("checkAll") {
    group = "verification"
    description = "Builds and tests every module."
    // Container projects such as :testing and :services group modules but carry no
    // build file, and therefore no check task.
    dependsOn(
        subprojects
            .filter { it.buildFile.exists() }
            .map { "${it.path}:check" }
    )
}
