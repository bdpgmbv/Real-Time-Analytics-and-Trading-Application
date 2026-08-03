import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    java
    `jvm-test-suite`
}

val libs = the<LibrariesForLibs>()

// Integration tests need Docker and take seconds to minutes. Keeping them out of
// `test` means `./gradlew test` stays fast and runs anywhere, while `check` still
// runs everything.
testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(platform(libs.junit.bom))
                implementation(libs.assertj.core)
                implementation(platform(libs.testcontainers.bom))
                implementation(libs.testcontainers.junit)
                implementation(libs.awaitility)
            }

            targets.all {
                testTask.configure {
                    shouldRunAfter(tasks.named("test"))
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}
