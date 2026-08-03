import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("rtat.java-conventions")
    id("rtat.integration-test-conventions")
    id("org.springframework.boot")
}

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.cloud.bom))

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    "integrationTestImplementation"(platform(libs.spring.boot.bom))
    "integrationTestImplementation"(platform(libs.spring.cloud.bom))
    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "${project.name}.jar"
}
