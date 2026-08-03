plugins {
    id("rtat.java-conventions")
}

description = "Architecture rules enforced as tests across every module."

dependencies {
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":common"))
    testImplementation(project(":contracts"))
}

tasks.named("jacocoTestCoverageVerification") {
    enabled = false
}
