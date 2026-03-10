plugins {
    kotlin("jvm") version "2.1.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.folivo:trixnity-client:4.15.0")
    implementation("net.folivo:trixnity-client-repository-exposed:4.15.0")
    implementation("net.folivo:trixnity-client-media-okio:4.15.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("io.ktor:ktor-client-java:3.1.2")
    implementation("io.github.oshai:kotlin-logging:7.0.6")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("phase1.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
