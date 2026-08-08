plugins {
    `java-library`
}

description = "Keeps a running exposure total per fund and currency, from the price stream."

dependencies {
    compileOnly(libs.flink.streaming.java)
    compileOnly(libs.flink.clients)
    implementation(libs.flink.connector.kafka)
    compileOnly(libs.flink.connector.base)
    testImplementation(libs.flink.connector.base)
    implementation(libs.postgresql)
    implementation(libs.jackson.databind)

    testImplementation(libs.flink.streaming.java)
    testImplementation(libs.flink.clients)
    testImplementation(libs.flink.test.utils)
    testImplementation(testFixtures(project(":platform")))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-Xlint:-try")
}

tasks.named<ProcessResources>("processTestResources") {
    from(rootProject.file("db")) { into("db") }
}

val provedByRunningTheJob = listOf("vyshaliprabananthlal/jobs/exposure/ExposureJob*")

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(provedByRunningTheJob) } }),
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(provedByRunningTheJob) } }),
    )
}
