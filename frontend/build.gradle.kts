@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.inframap"
version = "0.1.0-SNAPSHOT"

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "inframap.js"
            }
        }
        binaries.executable()
    }

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }

        val wasmJsMain by getting

        val wasmJsTest by getting

        val jvmMain by getting

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }
}

compose.resources {
    generateResClass = never
}

val ktlintVersion = "1.5.0"

val ktlintCli: Configuration by configurations.creating

dependencies {
    ktlintCli("com.pinterest.ktlint:ktlint-cli:$ktlintVersion")
}

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Check Kotlin code style with ktlint"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        "src/**/*.kt",
        "--editorconfig=${projectDir}/.editorconfig",
    )
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Fix Kotlin code style with ktlint"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        "-F",
        "src/**/*.kt",
        "--editorconfig=${projectDir}/.editorconfig",
    )
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("detekt.yml"))
    source.setFrom(
        files(
            "src/commonMain/kotlin",
            "src/wasmJsMain/kotlin",
            "src/jvmMain/kotlin",
        ),
    )
}

kover {
    reports {
        filters {
            excludes {
                classes("com.inframap.frontend.MainKt")
            }
        }

        verify {
            rule {
                minBound(85)
            }
        }
    }
}
