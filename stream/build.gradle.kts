plugins {
    application
}

description = "Sends continuously-changing data to Kafka."

dependencies {
    implementation(libs.postgresql)
    implementation(libs.kafka.clients)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "vyshaliprabananthlal.stream.send.CurrencyRateSender"
}

val streams =
    mapOf(
        "sendCurrencyRates" to "CurrencyRateSender",
        "sendPrices" to "PriceSender",
        "sendPositions" to "PositionSender",
        "sendTrades" to "TradeSender",
        "sendHedgeFills" to "HedgeFillSender",
    )

streams.forEach { (taskName, className) ->
    tasks.register<JavaExec>(taskName) {
        group = "rtat stream"
        description = "Streams $className to Kafka."
        mainClass = "vyshaliprabananthlal.stream.send.$className"
        classpath = sourceSets["main"].runtimeClasspath
    }
}

val provedByTheLiveRunNotByUnitTests =
    listOf(
        "vyshaliprabananthlal/stream/send/**",
        "vyshaliprabananthlal/stream/plumbing/Database*",
        "vyshaliprabananthlal/stream/plumbing/Kafka*",
        "vyshaliprabananthlal/stream/plumbing/Rows*",
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
