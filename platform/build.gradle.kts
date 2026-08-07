plugins {
    `java-library`
    `java-test-fixtures`
}

description = "Things every service needs before it starts: secrets, and refusing to run without them."

dependencies {
    implementation(platform(libs.spring.boot.bom))

    testFixturesApi(platform(libs.spring.boot.bom))
    testFixturesApi(platform(libs.testcontainers.bom))
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.testcontainers.core)

    api("org.springframework.boot:spring-boot")

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
