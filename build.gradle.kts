plugins {
    application
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.allaymc"
version = "2.0.0"
description = "A tool that can encrypt & decrypt Minecraft: Bedrock Edition resource pack"

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
    maven("https://www.jitpack.io/")
}

dependencies {
    // Utils
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-io:commons-io:2.15.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")

    // UI
    implementation("com.github.steos:jnafilechooser:1.1.2")
    implementation("com.formdev:flatlaf:3.6")
    implementation("com.intellij:forms_rt:7.0.3")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

application {
    mainClass.set("org.allaymc.encryptmypack.EncryptMyPack")
}

tasks.shadowJar {
    archiveClassifier = "shaded"
}