rootProject.name = "secretk"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven ("https://s01.oss.sonatype.org/content/repositories/releases/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    val versions = java.util.Properties().apply {
        load(File("${rootProject.projectDir}/versions.properties").reader())
    }
    plugins {
        // See https://jmfayard.github.io/refreshVersions
        id("de.fayard.refreshVersions") version versions["version.refreshVersions"] as String

        kotlin("multiplatform") version versions["version.kotlin"] as String apply false
        kotlin("android") version versions["version.kotlin"] as String apply false
        kotlin("plugin.serialization") version versions["version.kotlin"] as String apply false
        id("io.github.luca992.multiplatform-swiftpackage") version versions["version.multiplatformSwiftPackage"] as String apply false
        id("com.vanniktech.maven.publish") version versions["version.gradleMavenPublishPlugin"] as String apply false
        id("org.jetbrains.compose") version "1.2.0-beta02-dev795" apply false
        id("com.android.application").version("7.2.2") apply false


    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
        maven {
            name = "luca992/KHash GitHub Packages"
            url = uri("https://maven.pkg.github.com/luca992/KHash")
            credentials {
                username = "token"
                // see: https://github.community/t/download-from-github-package-registry-without-authentication/14407/44
                password =
                    "\u0067\u0068\u0070\u005f\u0072\u0067\u0070\u0054\u0069\u006c\u004c\u0042\u0038\u006c\u0066\u0057\u0045\u0056\u0031\u0053\u004a\u006d\u0061\u006b\u004a\u004c\u005a\u0063\u0038\u004d\u0074\u0038\u0045\u0066\u0031\u0059\u0053\u0058\u0039\u0055"
            }
        }
    }
}


plugins {
    id("de.fayard.refreshVersions")
    kotlin("android") apply false
    id("com.android.application") apply false
    id("org.jetbrains.compose") apply false

}


refreshVersions { // Optional: configure the plugin
    // ...
}

include(":secretk")
include(":sample")
