import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-library`
    id("rtat.quality-conventions")
}

val libs = the<LibrariesForLibs>()

group = "vyshaliprabananthlal"
version = "0.1.0-SNAPSHOT"

base {
    archivesName = "rtat-${project.name}"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

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

configurations.matching { it.name.contains("ompileClasspath") || it.name.contains("untimeClasspath") }
    .configureEach {
        resolutionStrategy.activateDependencyLocking()
    }
