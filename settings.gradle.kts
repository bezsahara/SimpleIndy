pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("org.bezsahara.simpleindy") version providers.gradleProperty("simpleIndyVersion").get()
    }
}

rootProject.name = "SimpleIndy"
include("annotations")
include("transformer")
include("gradle-plugin")
