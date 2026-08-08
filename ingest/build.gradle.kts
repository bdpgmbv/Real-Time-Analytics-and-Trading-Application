plugins {
    alias(libs.plugins.spring.boot)
}

description = "Reads the Kafka topics and writes what arrives into Postgres."

dependencies {
    implementation(platform(libs.spring.boot.bom))

    implementation(project(":platform"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.micrometer.prometheus)
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation(libs.jackson.databind)
    runtimeOnly(libs.postgresql)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":platform")))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.named<ProcessResources>("processTestResources") {
    from(rootProject.file("db")) { into("db") }
}
