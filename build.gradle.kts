plugins {
    id("java")
}

val simpleIndyVersion = providers.gradleProperty("simpleIndyVersion").get()

allprojects {
    group = "org.bezsahara"
    version = simpleIndyVersion
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("publishSimpleIndyToMavenLocal") {
    group = "publishing"
    description = "Publishes SimpleIndy modules needed by the Maven-local sample build."
    dependsOn(
        ":annotations:publishToMavenLocal",
        ":transformer:publishToMavenLocal",
        ":gradle-plugin:publishToMavenLocal",
    )
}
