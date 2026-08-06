plugins {
    id("rtat.java-conventions")
    application
}

description = "Downloads real 13F filings from SEC EDGAR."

application {
    mainClass = "vyshaliprabananthlal.sim.edgar.DownloadOne"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
