plugins {
    kotlin("multiplatform") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

group = "io.eqoty"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        withJava()
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
        binaries.executable()
    }
    macosX64()
    macosArm64().apply{
        compilations.getByName("main") {    // NL
            cinterops {
                val libaes_siv by creating {
                    defFile(project.file("src/nativeInterop/cinterop/libaes_siv.def"))
                    includeDirs.allHeaders(project.file("${project.rootDir}/nativelibs/libaes_siv/"))
                }
            }
            val buildFolderName = Target.MacosArm64.buildFolderName
            val releaseFolderName = Target.MacosArm64.releaseFolderName
            kotlinOptions.freeCompilerArgs = listOf(
                "-include-binary", "${project.rootDir}/nativelibs/libaes_siv_build/$buildFolderName/$releaseFolderName/libaes_siv.a",
                "-include-binary", "$projectDir/nativelibs/darwinopenssl/macosx/lib/libcrypto.a"
            )
        }
    }
    iosX64()
    iosArm64()
//    linuxX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:_")
                implementation("io.ktor:ktor-client-json:_")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:_")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:_")
                implementation("io.ktor:ktor-serialization-kotlinx-json:_")
                implementation("io.ktor:ktor-client-content-negotiation:_")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:_")
                implementation("com.squareup.okio:okio:_")
                implementation("com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings:_")
                implementation("com.ionspin.kotlin:bignum:_")
                implementation("com.ionspin.kotlin:bignum-serialization-kotlinx:_")
                implementation("cash.z.ecc.android:kotlin-bip39:1.0.2-SNAPSHOT")
                implementation("co.touchlab:kermit:_")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:_")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.slf4j:slf4j-simple:_")
                implementation("io.ktor:ktor-client-okhttp:_")
                implementation("org.cryptomator:siv-mode:_")
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:_")
                implementation(npm("path-browserify", "1.0.1"))
                implementation(npm("crypto-browserify", "3.12.0"))
                implementation(npm("buffer", "6.0.3"))
                implementation(npm("stream-browserify", "3.0.0"))
                implementation(npm("os-browserify", "0.3.0"))
                implementation(npm("miscreant", "0.3.2"))
                implementation(npm("libsodium-wrappers-sumo", "0.7.10"))
            }
        }
        val jsTest by getting
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating


        val darwinMain by creating {
            dependsOn(nativeMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:_")
            }
        }

        val iosMain by creating {
            dependsOn(darwinMain)
            dependencies {
            }
        }
        val macosX64Main by getting {
            dependsOn(darwinMain)
        }
        val macosArm64Main by getting {
            dependsOn(darwinMain)
        }
//        val linuxX64Main by getting {
//            dependsOn(desktopMain)
//        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosX64Main by getting {
            dependsOn(iosMain)
        }
    }
}

enum class Target(
    val taskSuffix: String,
    val buildFolderName: String,
    val releaseFolderName: String
) {
    MacosArm64("MacosArm64", "MAC_ARM64", "Release"),
    MacosX64("MacosX64", "MAC", "Release"),
    IosArm64("IosArm64", "OS64", "Release-iphoneos")
}

fun makeLibAesSiv(target: Target): Task =
    target.run {
        task<Exec>("makeLibAesSiv$taskSuffix") {
            workingDir = File("./nativelibs")
            commandLine("./make-libaes_siv.sh", buildFolderName)
        }.apply {
            onlyIf {
                !file("./nativelibs/libaes_siv_build/$buildFolderName/$releaseFolderName/libaes_siv.a").exists()
            }
        }
    }


tasks.findByName("cinteropLibaes_sivMacosArm64")!!.dependsOn(makeLibAesSiv(Target.MacosArm64))

tasks.clean {
    doFirst {
        val libAesSivBuild = File("./nativelibs/libaes_siv_build")
        libAesSivBuild.deleteRecursively()
    }
}