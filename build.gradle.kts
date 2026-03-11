import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    }
}

apply(plugin = "org.jetbrains.kotlin.jvm")

repositories {
    mavenLocal()
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
    add("implementation", "de.connect2x.trixnity:trixnity-client-jvm:5.2.0")
    add("implementation", "de.connect2x.trixnity:trixnity-client-media-okio-jvm:5.2.0")
    add("implementation", "de.connect2x.trixnity:trixnity-client-cryptodriver-vodozemac-jvm:5.2.0")
    add("implementation", "io.ktor:ktor-client-mock-jvm:3.4.1")

    add("implementation", "org.clojure:clojure:1.12.4")
    add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    add("runtimeOnly", clojureRuntimeClasspath)

    add("testImplementation", "org.jetbrains.kotlin:kotlin-test:2.3.10")
    add("testImplementation", "de.connect2x.trixnity:trixnity-test-utils-jvm:5.2.0")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

configure<KotlinJvmProjectExtension> {
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

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
