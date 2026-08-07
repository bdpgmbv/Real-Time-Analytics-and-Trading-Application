plugins {
    alias(libs.plugins.spring.boot)
}

description = "Sends continuously-changing data to Kafka."

dependencies {
    implementation(platform(libs.spring.boot.bom))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.micrometer.prometheus)
    runtimeOnly(libs.postgresql)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

val provedByTheLiveRunNotByUnitTests =
    listOf(
        "vyshaliprabananthlal/stream/send/**",
        "vyshaliprabananthlal/stream/StreamApplication*",
    )

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(provedByTheLiveRunNotByUnitTests) } }),
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(provedByTheLiveRunNotByUnitTests) } }),
    )
}
