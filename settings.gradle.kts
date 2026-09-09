plugins {
    // Lets `./gradlew` auto-provision the JDK 17 toolchain the build targets,
    // so contributors don't have to install it by hand.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "claude-usage-monitor"
