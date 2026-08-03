plugins {
    `kotlin-dsl`
}

// Pin the convention-plugin build to 21 as well. Without this it follows the Gradle
// launcher JVM, which makes the build depend on whatever JDK happens to be on PATH.
kotlin {
    jvmToolchain(21)
}

dependencies {
    // Gradle does not generate version-catalog accessors for precompiled script
    // plugins. Putting the generated accessor classes on the classpath lets the
    // convention plugins reference `libs`, so every version stays in one file.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    // Plugins applied from inside precompiled script plugins must be on this
    // classpath, not just declared in the catalog.
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.spotbugs.gradle.plugin)
    implementation(libs.cyclonedx.gradle.plugin)
}
