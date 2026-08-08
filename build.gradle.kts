import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.cyclonedx) apply false
}

subprojects {
    if (!buildFile.exists()) {
        return@subprojects
    }

    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.ltgt.errorprone")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "org.cyclonedx.bom")

    group = "vyshaliprabananthlal"
    version = "0.1.0-SNAPSHOT"

    configure<BasePluginExtension> {
        archivesName = "rtat-${project.name}"
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        add("errorprone", rootProject.libs.errorprone.core)
        add("errorprone", rootProject.libs.nullaway)

        add("compileOnly", rootProject.libs.jspecify)
        add("testCompileOnly", rootProject.libs.jspecify)

        add("testImplementation", platform(rootProject.libs.junit.bom))
        add("testImplementation", rootProject.libs.junit.jupiter)
        add("testImplementation", rootProject.libs.assertj.core)
        add("testRuntimeOnly", rootProject.libs.junit.platform.launcher)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror", "-parameters"))

        options.errorprone {
            disableWarningsInGeneratedCode = true
            excludedPaths = ".*/build/generated/.*"
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "vyshaliprabananthlal")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("rtat.module", project.name)
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }

    configurations
        .matching { it.name.contains("ompileClasspath") || it.name.contains("untimeClasspath") }
        .configureEach {
            resolutionStrategy.activateDependencyLocking()
        }

    configure<SpotlessExtension> {
        java {
            palantirJavaFormat()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
            targetExclude("**/build/generated/**")
        }
        kotlinGradle {
            ktlint()
        }
    }

    configure<SpotBugsExtension> {
        effort = Effort.MAX
        reportLevel = Confidence.MEDIUM
        excludeFilter = rootProject.layout.projectDirectory.file("spotbugs-exclude.xml").asFile
    }

    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") { required = true }
        reports.create("xml") { required = false }
    }

    tasks.named("spotbugsTest") {
        enabled = false
    }

    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required = true
            html.required = true
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("test"))
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "0.80".toBigDecimal()
                }
            }
            rule {
                limit {
                    counter = "BRANCH"
                    minimum = "0.70".toBigDecimal()
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

tasks.register("everyModuleHasTests") {
    group = "verification"
    description = "Fails a module that has code but no tests, which coverage alone lets through."

    val modules =
        subprojects
            .filter { it.buildFile.exists() }
            .associate { it.path to Pair(it.file("src/main/java"), it.file("src/test/java")) }

    doLast {
        val withoutTests =
            modules.filter { (_, folders) ->
                val (main, test) = folders
                main.exists() && !(test.exists() && test.walkTopDown().any { it.name.endsWith("Test.java") })
            }

        if (withoutTests.isNotEmpty()) {
            throw GradleException(
                "these modules have code but no tests, so their coverage gate means nothing: " +
                    withoutTests.keys.joinToString(", "),
            )
        }
    }
}

tasks.register("checkAll") {
    group = "verification"
    description = "Builds and tests every module."

    dependsOn("everyModuleHasTests")
    dependsOn(
        subprojects
            .filter { it.buildFile.exists() }
            .map { "${it.path}:check" }
    )
}
