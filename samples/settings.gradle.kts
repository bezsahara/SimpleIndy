pluginManagement {
    val rootProperties = java.util.Properties()
    val rootPropertiesFile = file("../gradle.properties")
    if (rootPropertiesFile.isFile) {
        rootPropertiesFile.inputStream().use(rootProperties::load)
    }
    val simpleIndyVersion = rootProperties.getProperty("simpleIndyVersion")
        ?: error("simpleIndyVersion is missing from ../gradle.properties")

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("org.bezsahara.simpleindy") version simpleIndyVersion
    }
}

rootProject.name = "samples"
