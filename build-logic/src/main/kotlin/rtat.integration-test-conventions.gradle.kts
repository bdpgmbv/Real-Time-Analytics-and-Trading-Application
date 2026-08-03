import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    java
    `jvm-test-suite`
}

val libs = the<LibrariesForLibs>()

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(platform(libs.junit.bom))
                implementation(libs.assertj.core)
                implementation(platform(libs.testcontainers.bom))
                implementation(libs.testcontainers.core)
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

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.getByName("implementation"))
}

configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.getByName("runtimeOnly"))
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}
