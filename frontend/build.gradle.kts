@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.benManes.versions)
}

group = "com.inframap"
version = "0.1.0-SNAPSHOT"

compose.resources {
    publicResClass = true
    packageOfResClass = "com.inframap.frontend.generated.resources"
    generateResClass = always
}

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
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)

                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.json)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.koin.test)
                implementation(libs.turbine)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        val wasmJsTest by getting

        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

val ktlintCli: Configuration by configurations.creating

dependencies {
    ktlintCli(libs.ktlint.cli)
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

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return !isStable
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.inframap.frontend.MainKt",
                    "com.inframap.frontend.ui.splash.SplashScreenKt*",
                    "com.inframap.frontend.ui.login.LoginScreenKt*",
                    "com.inframap.frontend.ui.onboarding.OnboardingScreenKt*",
                    "com.inframap.frontend.ui.app.InfraMapAppKt*",
                    "com.inframap.frontend.ui.app.MainScaffoldKt*",
                    "com.inframap.frontend.ui.app.PlaceholderScreenKt*",
                    "com.inframap.frontend.ui.dashboard.DashboardScreenKt*",
                    "com.inframap.frontend.ui.devices.*ScreenKt*",
                    "com.inframap.frontend.ui.staging.*ScreenKt*",
                    "com.inframap.frontend.ui.subnets.*ScreenKt*",
                    "com.inframap.frontend.ui.discovery.*ScreenKt*",
                    "com.inframap.frontend.ui.topology.*ScreenKt*",
                    "com.inframap.frontend.ui.inventory.*",
                    "com.inframap.frontend.ui.topology.CanvasToolbarKt*",
                    "com.inframap.frontend.ui.topology.DeviceInspectorSheetKt*",
                    "com.inframap.frontend.ui.topology.TopologyCanvasKt*",

                    "com.inframap.frontend.ui.topology.components.*",
                    "com.inframap.frontend.ui.command.*",
                    "com.inframap.frontend.ui.app.NavItem",
                    "com.inframap.frontend.ui.app.ComposableSingletons*",
                )
                packages(
                    "com.inframap.frontend.data.dto",
                    "com.inframap.frontend.ui.app",
                    "com.inframap.frontend.generated",
                )
                classes(
                    "com.inframap.frontend.designsystem.*",
                    "com.inframap.frontend.data.api.SuccessEnvelope",
                    "com.inframap.frontend.data.api.ErrorEnvelope",
                    "com.inframap.frontend.data.api.Meta",
                    "com.inframap.frontend.data.api.ErrorBody",
                    "com.inframap.frontend.data.api.FieldError",
                )
                annotatedBy("kotlinx.serialization.Serializable")
                inheritedFrom("kotlinx.serialization.KSerializer")
            }
        }

        verify {
            rule {
                minBound(85)
            }
        }
    }
}
