import java.util.Properties

plugins {
    id("java")
    kotlin("jvm") version "2.4.0"
    id("org.bezsahara.simpleindy")
    application
}

application {
    mainClass = "org.bezsahara.simpleindy.Main"
}

simpleIndy {
    enabled.set(true)
}

val rootProperties = Properties()
file("../gradle.properties").inputStream().use(rootProperties::load)
val simpleIndyVersion: String = rootProperties.getProperty("simpleIndyVersion")
    ?: error("simpleIndyVersion is missing from ../gradle.properties")

group = "org.bezsahara"
version = simpleIndyVersion

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.bezsahara:annotations:$simpleIndyVersion")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
