plugins {
    `java-library`
}

description = "Works out currency exposure and what to hedge."

dependencies {
    implementation(platform(libs.spring.boot.bom))

    api(project(":platform"))

    api("org.springframework:spring-context")
    api("org.springframework:spring-jdbc")
    api("io.micrometer:micrometer-core")
    api("org.springframework:spring-context-support")
    api(libs.caffeine)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":platform")))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.named<ProcessResources>("processTestResources") {
    from(rootProject.file("db")) { into("db") }
}
