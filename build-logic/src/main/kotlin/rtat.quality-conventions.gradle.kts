import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    java
    jacoco
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
    id("com.github.spotbugs")
    id("org.cyclonedx.bom")
}

val libs = the<LibrariesForLibs>()

dependencies {
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
    // JSpecify supplies @Nullable/@NonNull. compileOnly: annotations are not needed
    // at runtime and should not appear on a consumer's classpath.
    compileOnly(libs.jspecify)
    testCompileOnly(libs.jspecify)
}

spotless {
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        // Generated protobuf sources are not ours to format.
        targetExclude("**/build/generated/**")
    }
    kotlinGradle {
        ktlint()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        // Generated code is excluded wholesale rather than annotated.
        excludedPaths = ".*/build/generated/.*"

        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "vyshaliprabananthlal")
        // Treat a missing @Nullable as a bug, not a warning: a null slipping into a
        // valuation chain surfaces as a wrong number, not an exception.
        option("NullAway:UnannotatedSubPackages", "vyshaliprabananthlal.contract.v1")
    }
}

spotbugs {
    effort = Effort.MAX
    // LOW would drown the build in style noise; MEDIUM catches real defects.
    reportLevel = Confidence.MEDIUM
    excludeFilter = rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml").asFile
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("xml") { required = false }
}

// SpotBugs on test sources finds little of value and slows every build.
tasks.named("spotbugsTest") {
    enabled = false
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude("**/contract/v1/**") }
        })
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude("**/contract/v1/**") }
        })
    )
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

// Reproducible archives: identical sources must produce byte-identical jars, or
// "the same build" cannot be verified across machines.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
