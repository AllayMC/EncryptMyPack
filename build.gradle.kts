plugins {
    id("java")
    application
    // Shadow jar plugin
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.graalvm.buildtools.native") version "0.10.6"
}

group = "org.allaymc"
version = "1.0.0"

application {
    mainClass.set("org.allaymc.encryptmypack.EncryptMyPack")
}

repositories {
    mavenCentral()
    maven("https://www.jitpack.io/")
}

dependencies {
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-io:commons-io:2.15.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
    implementation("com.github.steos:jnafilechooser:1.1.2")
    implementation("com.formdev:flatlaf:3.6")
    implementation("com.intellij:forms_rt:7.0.3")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    binaries.all {
        resources.autodetect()
    }
}

tasks.runShadow {
    val runningDir = File("run")
    runningDir.mkdirs()
    workingDir = runningDir
}