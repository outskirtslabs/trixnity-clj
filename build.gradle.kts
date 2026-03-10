plugins {
    kotlin("jvm") version "2.3.10"
}

repositories {
    mavenCentral()
}

val clojureRuntimeClasspath = run {
    val process = ProcessBuilder("clojure", "-Spath")
        .directory(projectDir)
        .redirectErrorStream(true)
        .start()
    val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
    check(process.waitFor() == 0) {
        "clojure -Spath failed:\n$stdout"
    }

    val projectClasspathEntries = setOf(
        file("src/clj").canonicalFile,
        file("build/classes/kotlin/main").canonicalFile,
        file("build/resources/main").canonicalFile,
    )

    files(
        stdout.trim()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map(::file)
            .filter { it.exists() }
            .filter { it.canonicalFile !in projectClasspathEntries }
    )
}

dependencies {
    implementation("de.connect2x.trixnity:trixnity-client-jvm:5.2.0")
    implementation("de.connect2x.trixnity:trixnity-client-media-okio-jvm:5.2.0")
    implementation("de.connect2x.trixnity:trixnity-client-cryptodriver-vodozemac-jvm:5.2.0")

    implementation("org.clojure:clojure:1.12.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    runtimeOnly(clojureRuntimeClasspath)

    testImplementation(kotlin("test"))
    testImplementation("de.connect2x.trixnity:trixnity-test-utils-jvm:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

kotlin {
    jvmToolchain(25)
    sourceSets {
        getByName("main").kotlin.srcDir("src/kotlin")
        getByName("main").resources.srcDir("src/clj")
        getByName("test").kotlin.srcDir("src/kotlinTest")
        getByName("test").kotlin.srcDir(
            "extra/trixnity/trixnity/trixnity-client/client-repository-test/src/commonMain/kotlin"
        )
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
