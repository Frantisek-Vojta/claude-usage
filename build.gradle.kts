import java.io.File

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType").get(),
            providers.gradleProperty("platformVersion").get(),
        )
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    // `./gradlew signPlugin` / `publishPlugin` read these from the environment.
    // Point *_FILE at your PEM files (easiest locally), or paste the PEM contents
    // into CERTIFICATE_CHAIN / PRIVATE_KEY (handy for CI secrets).
    signing {
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
        providers.environmentVariable("CERTIFICATE_CHAIN_FILE").orNull
            ?.let { certificateChainFile = layout.file(provider { File(it) }) }
            ?: run { certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN") }
        providers.environmentVariable("PRIVATE_KEY_FILE").orNull
            ?.let { privateKeyFile = layout.file(provider { File(it) }) }
            ?: run { privateKey = providers.environmentVariable("PRIVATE_KEY") }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    buildSearchableOptions { enabled = false }
}
