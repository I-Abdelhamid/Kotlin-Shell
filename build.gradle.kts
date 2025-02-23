plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1" // Use latest version
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // JUnit 5 dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.0")

    // Mockito dependencies
    testImplementation("org.mockito:mockito-core:4.0.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")

    // Kotlin test support
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.5.31")
}

tasks.test {
    useJUnitPlatform()
    dependsOn("jar") // Ensure the JAR is built before running tests
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

application {
    mainClass.set("MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
