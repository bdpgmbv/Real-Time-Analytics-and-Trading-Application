plugins {
    id("rtat.java-conventions")
}

description = "Architecture rules enforced as tests across every module."

dependencies {
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":common"))
    testImplementation(project(":contracts"))
}

// This module has no production classes of its own, so a coverage threshold here
// measures nothing.
tasks.named("jacocoTestCoverageVerification") {
    enabled = false
}
