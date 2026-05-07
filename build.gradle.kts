import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("dev.detekt") version "2.0.0-alpha.3"
}

group = "com.forketyfork"
version = providers.gradleProperty("pluginVersion").get()

fun latestChangelog(): String {
    val changelog = file("CHANGELOG.md")
    if (!changelog.exists()) return "Initial version"
    val lines = changelog.readLines()
    val start = lines.indexOfFirst { it.matches(Regex("""^## \[\d+.*""")) }
    if (start == -1) return "Initial version"
    val end = lines.drop(start + 1).indexOfFirst { it.startsWith("## [") }
    val section = if (end == -1) lines.drop(start + 1) else lines.subList(start + 1, start + 1 + end)
    return section.joinToString("\n").trim().ifEmpty { "Initial version" }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.opentest4j)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit4)

    intellijPlatform {
        intellijIdea("2026.1")

        // Add plugin dependencies for compilation here:

        composeUI()
        bundledModule("intellij.platform.compose.markdown")
        bundledModule("intellij.platform.jewel.markdown.core")
        bundledModule("intellij.platform.jewel.markdown.ideLafBridgeStyling")
        bundledModule("intellij.platform.jewel.markdown.extensions.autolink")
        bundledModule("intellij.platform.jewel.markdown.extensions.gfmAlerts")
        bundledModule("intellij.platform.jewel.markdown.extensions.gfmTables")
        bundledModule("intellij.platform.jewel.markdown.extensions.gfmStrikethrough")
        bundledPlugin("com.intellij.mcpServer")

    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = latestChangelog()
    }

    caching {
        ides {
            enabled.set(true)
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

detekt {
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    parallel = true
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
    basePath.set(projectDir)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget.set("21")

    reports {
        sarif.required.set(true)
        markdown.required.set(true)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("21")
}
