import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("com.gradleup.shadow") version "9.0.0"
}

group = "com.reburp"
version = "1.1.2"

repositories {
    mavenCentral()
}

dependencies {
    // Burp Montoya API - provided by Burp Suite at runtime
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.7")

    // Ktor HTTP server (Netty engine)
    implementation("io.ktor:ktor-server-core:3.1.3")
    implementation("io.ktor:ktor-server-netty:3.1.3")
    implementation("io.ktor:ktor-server-cors:3.1.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
    implementation("io.ktor:ktor-server-status-pages:3.1.3")

    // DoubleReceive - lets the monitoring intercept read the request body without consuming it
    implementation("io.ktor:ktor-server-double-receive:3.1.3")

    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // SLF4J (Ktor / Netty need a binding)
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(kotlin("test"))
    testImplementation("net.portswigger.burp.extensions:montoya-api:2026.7")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(listOf("-Xjsr305=strict"))
    }
}

tasks.jar {
    enabled = false
}

// The version is reported at startup and over /api/status. Generate it from the Gradle
// version rather than keeping a copy in the source, so the two cannot drift apart.
val generateVersionFile by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/version")
    val versionString = project.version.toString()
    inputs.property("version", versionString)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile.resolve("com/reburp/Version.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.reburp

            /** Generated from the Gradle version by the generateVersionFile task. Do not edit. */
            const val REBURP_VERSION: String = "$versionString"
            """.trimIndent() + "\n"
        )
    }
}

sourceSets.main {
    kotlin.srcDir(generateVersionFile)
}

tasks.shadowJar {
    archiveBaseName.set("reburp")
    archiveClassifier.set("")
    mergeServiceFiles()
    isZip64 = true
    configurations = listOf(project.configurations.runtimeClasspath.get())

    // Burp reloads an extension by watching the path it was loaded from, and the released
    // jar carries the version in its name, so every version bump silently left a locally
    // loaded extension pointing at a stale file. Drop an unversioned copy next to it and
    // load that one while developing: the path survives bumps, so reloading keeps working.
    // The release workflow globs "reburp-*.jar", which does not match "reburp.jar", so this
    // copy is never published as a release asset.
    doLast {
        val versioned = archiveFile.get().asFile
        versioned.copyTo(File(versioned.parentFile, "reburp.jar"), overwrite = true)
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}
