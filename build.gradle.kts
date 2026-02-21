val tdlightVersion = "3.4.4+td.1.8.52"

plugins {
    kotlin("jvm") version "2.1.10"
}

group = "com.vygovskiy"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
    maven("https://mvn.mchv.eu/repository/mchv/")
}

dependencies {
    implementation(platform("it.tdlight:tdlight-java-bom:$tdlightVersion"))
    implementation(group = "it.tdlight", name = "tdlight-java")
    implementation(group = "it.tdlight", name = "tdlight-natives", classifier = "linux_amd64_gnu_ssl3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("ch.qos.logback:logback-classic:1.5.20")
    implementation("io.github.oshai:kotlin-logging:7.0.13")
}

kotlin {
    jvmToolchain(21)
}