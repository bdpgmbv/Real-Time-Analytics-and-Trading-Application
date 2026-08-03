import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    // java-library, not java: modules like contracts expose generated types to
    // consumers, which needs the `api` configuration.
    `java-library`
    id("rtat.quality-conventions")
}

val libs = the<LibrariesForLibs>()

group = "vyshaliprabananthlal"
version = "0.1.0-SNAPSHOT"

base {
    // Short prefix, not the full project name: the 27-character form would push
    // Kubernetes resource names past the 63-character RFC 1123 limit.
    archivesName = "rtat-${project.name}"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -parameters keeps parameter names for Jackson and Spring binding.
    // -serial is excluded because records implementing Serializable warn about a
    // missing serialVersionUID, which records do not need.
    options.compilerArgs.addAll(
        listOf("-Xlint:all,-serial,-processing", "-Werror", "-parameters")
    )
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// Lock resolved versions so a build today and a rebuild in six months produce the
// same bytes. Only runtime classpaths: locking every configuration makes routine
// dependency edits painful for no reproducibility gain.
configurations.matching { it.name.contains("ompileClasspath") || it.name.contains("untimeClasspath") }
    .configureEach {
        resolutionStrategy.activateDependencyLocking()
    }
