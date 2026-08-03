import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    java
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
