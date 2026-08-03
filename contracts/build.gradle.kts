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

// protoc-generated sources do not survive -Xlint:all -Werror, and we cannot fix
// warnings in code we do not write. This module compiles permissively; every other
// module keeps the strict flags from the convention plugin.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs = listOf("-Xlint:none", "-parameters")
}
