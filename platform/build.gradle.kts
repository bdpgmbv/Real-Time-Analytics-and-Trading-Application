plugins {
    `java-library`
}

description = "Things every service needs before it starts: secrets, and refusing to run without them."

dependencies {
    implementation(platform(libs.spring.boot.bom))

    api("org.springframework.boot:spring-boot")

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
