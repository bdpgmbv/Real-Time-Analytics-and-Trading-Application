plugins {
    id("rtat.java-conventions")
    alias(libs.plugins.protobuf)
}

description = "Protobuf contracts: the shared vocabulary carried on the event bus."

dependencies {
    api(libs.protobuf.java)
    api(project(":common"))
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs = listOf("-Xlint:none", "-parameters")
}
