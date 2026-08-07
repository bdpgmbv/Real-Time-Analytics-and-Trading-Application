plugins {
    application
}

description = "Reads the Kafka topics and writes what arrives into Postgres."

dependencies {
    implementation(libs.postgresql)
    implementation(libs.kafka.clients)
    implementation(libs.jackson.databind)
}

application {
    mainClass = "vyshaliprabananthlal.ingest.PositionReceiver"
}

val receivers =
    mapOf(
        "receivePositions" to "PositionReceiver",
        "receivePrices" to "PriceReceiver",
        "receiveRates" to "RateReceiver",
    )

receivers.forEach { (taskName, className) ->
    tasks.register<JavaExec>(taskName) {
        group = "rtat ingest"
        description = "Reads a Kafka topic and writes the changes into Postgres."
        mainClass = "vyshaliprabananthlal.ingest.$className"
        classpath = sourceSets["main"].runtimeClasspath
    }
}
