plugins {
    application
}

description = "Sends continuously-changing data to Kafka."

dependencies {
    implementation(libs.postgresql)
    implementation(libs.kafka.clients)
}

application {
    mainClass = "vyshaliprabananthlal.stream.CurrencyRateSender"
}

val streams =
    mapOf(
        "sendCurrencyRates" to "CurrencyRateSender",
        "sendPrices" to "PriceSender",
        "sendPositions" to "PositionSender",
        "sendTrades" to "TradeSender",
    )

streams.forEach { (taskName, className) ->
    tasks.register<JavaExec>(taskName) {
        group = "rtat stream"
        description = "Streams $className to Kafka."
        mainClass = "vyshaliprabananthlal.stream.$className"
        classpath = sourceSets["main"].runtimeClasspath
    }
}
