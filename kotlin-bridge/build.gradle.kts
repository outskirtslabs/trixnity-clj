plugins {
    kotlin("jvm") version "2.1.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.folivo:trixnity-client:4.22.7")
    implementation("net.folivo:trixnity-client-repository-exposed:4.22.7")
    implementation("net.folivo:trixnity-client-media-okio:4.22.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("src/generated/kotlin")
    }
}
