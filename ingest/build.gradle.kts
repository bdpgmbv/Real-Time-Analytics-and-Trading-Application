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

tasks.register<JavaExec>("receivePositions") {
    group = "rtat ingest"
    description = "Reads rtat.position and writes the changes into Postgres."
    mainClass = "vyshaliprabananthlal.ingest.PositionReceiver"
    classpath = sourceSets["main"].runtimeClasspath
}
