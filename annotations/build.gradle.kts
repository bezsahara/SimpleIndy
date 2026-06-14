import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID

plugins {
    `java-library`
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val annotationsArtifactId = "annotations"
val centralStagingRepository = layout.buildDirectory.dir("central-staging")
val centralBundleFile = layout.buildDirectory.file("simpleindy-annotations-central-bundle-${project.version}.zip")

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = annotationsArtifactId
            version = project.version.toString()

            pom {
                name.set("SimpleIndy Annotations")
                description.set("Annotations and runtime bootstrap adapter used by SimpleIndy invokedynamic transformation.")
                url.set("https://github.com/bezsahara/SimpleIndy")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                    }
                }
                developers {
                    developer {
                        id.set("bezsahara")
                        name.set("Hlib")
                        email.set("bezsahara888@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/bezsahara/SimpleIndy.git")
                    developerConnection.set("scm:git:ssh://github.com/bezsahara/SimpleIndy.git")
                    url.set("https://github.com/bezsahara/SimpleIndy")
                }
            }
        }
    }

    repositories {
        maven {
            name = "centralStaging"
            url = uri(centralStagingRepository)
        }
    }
}

fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

fun findSigningFile(name: String): File? {
    val skPath = env("SK_P") ?: return null
    return file("$skPath\\bez\\$name").takeIf { it.isFile }
}

fun signingKeyText(): String? {
    return providers.gradleProperty("signingKey").orNull
        ?: env("SIGNING_KEY")
        ?: findSigningFile("d1_SECRET.asc")?.readText()
}

fun signingPasswordText(): String? {
    return providers.gradleProperty("signingPassword").orNull
        ?: env("SIGNING_PASSWORD")
        ?: env("PASS_PHRASE")
        ?: findSigningFile("pass_phrase.txt")?.readText()?.trim()
}

val configuredSigningKey = signingKeyText()
val configuredSigningPassword = signingPasswordText()

if (!configuredSigningKey.isNullOrBlank()) {
    signing {
        useInMemoryPgpKeys(configuredSigningKey, configuredSigningPassword.orEmpty())
        sign(publishing.publications)
    }
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

fun File.isChecksumFile(): Boolean {
    val lower = name.lowercase(Locale.ROOT)
    return lower.endsWith(".md5") ||
        lower.endsWith(".sha1") ||
        lower.endsWith(".sha256") ||
        lower.endsWith(".sha512")
}

fun File.shouldHaveCentralChecksum(): Boolean {
    return isFile && !isChecksumFile() && !name.endsWith(".asc", ignoreCase = true)
}

fun File.digest(algorithm: String): String {
    val md = MessageDigest.getInstance(algorithm)
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            md.update(buffer, 0, read)
        }
    }
    return md.digest().joinToString("") { byte -> "%02x".format(byte) }
}

val generateAnnotationsCentralChecksums = tasks.register("generateAnnotationsCentralChecksums") {
    group = "publishing"
    description = "Ensures every annotations Central artifact has .md5 and .sha1 checksum files."
    dependsOn("publishMavenJavaPublicationToCentralStagingRepository")

    doLast {
        centralStagingRepository.get().asFile
            .walkTopDown()
            .filter { it.shouldHaveCentralChecksum() }
            .forEach { artifact ->
                artifact.resolveSibling("${artifact.name}.md5").writeText(artifact.digest("MD5"))
                artifact.resolveSibling("${artifact.name}.sha1").writeText(artifact.digest("SHA-1"))
            }
    }
}

val createAnnotationsCentralBundle = tasks.register<Zip>("createAnnotationsCentralBundle") {
    group = "publishing"
    description = "Creates a Maven Central Portal upload bundle for SimpleIndy annotations."
    dependsOn(generateAnnotationsCentralChecksums)
    archiveFileName.set(centralBundleFile.map { it.asFile.name })
    destinationDirectory.set(layout.buildDirectory)

    from(centralStagingRepository) {
        include("org/**")
        exclude("**/maven-metadata.xml*")
        exclude("**/*.asc.md5")
        exclude("**/*.asc.sha1")
        exclude("**/*.sha256")
        exclude("**/*.sha512")
    }
}

fun centralBearerToken(): String {
    providers.gradleProperty("centralPortalBearer").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    env("CENTRAL_PORTAL_BEARER")?.let { return it }

    val username = providers.gradleProperty("centralPortalUsername").orNull
        ?: env("CENTRAL_PORTAL_USERNAME")
    val password = providers.gradleProperty("centralPortalPassword").orNull
        ?: env("CENTRAL_PORTAL_PASSWORD")

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        throw GradleException(
            "Central Portal credentials are missing. Set centralPortalUsername/centralPortalPassword, " +
                "CENTRAL_PORTAL_USERNAME/CENTRAL_PORTAL_PASSWORD, centralPortalBearer, or CENTRAL_PORTAL_BEARER."
        )
    }

    return Base64.getEncoder()
        .encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
}

fun encodeQuery(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
}

fun uploadEndpoint(): URI {
    val deploymentName = providers.gradleProperty("centralDeploymentName").orNull
        ?: "SimpleIndy annotations ${project.version}"
    val publishingType = providers.gradleProperty("centralPublishingType").orNull
        ?: env("CENTRAL_PUBLISHING_TYPE")
        ?: "USER_MANAGED"
    return URI.create(
        "https://central.sonatype.com/api/v1/publisher/upload" +
            "?name=${encodeQuery(deploymentName)}" +
            "&publishingType=${encodeQuery(publishingType)}"
    )
}

fun multipartBodyFile(bundle: File, boundary: String): File {
    val body = layout.buildDirectory.file("tmp/central-upload/${UUID.randomUUID()}.multipart").get().asFile
    body.parentFile.mkdirs()

    Files.newOutputStream(body.toPath()).use { output ->
        fun writeUtf8(text: String) {
            output.write(text.toByteArray(StandardCharsets.UTF_8))
        }

        writeUtf8("--$boundary\r\n")
        writeUtf8("Content-Disposition: form-data; name=\"bundle\"; filename=\"${bundle.name}\"\r\n")
        writeUtf8("Content-Type: application/octet-stream\r\n\r\n")
        Files.copy(bundle.toPath(), output)
        writeUtf8("\r\n--$boundary--\r\n")
    }

    return body
}

tasks.register("uploadAnnotationsToMavenCentral") {
    group = "publishing"
    description = "Uploads the SimpleIndy annotations Central bundle to the Maven Central Portal Publisher API."
    dependsOn(createAnnotationsCentralBundle)

    doLast {
        val bundle = centralBundleFile.get().asFile
        if (!bundle.isFile) {
            throw GradleException("Central bundle does not exist: ${bundle.absolutePath}")
        }

        val boundary = "----SimpleIndyAnnotationsCentral${UUID.randomUUID()}"
        val multipartBody = multipartBodyFile(bundle, boundary)
        val request = HttpRequest.newBuilder(uploadEndpoint())
            .header("Authorization", "Bearer ${centralBearerToken()}")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofFile(multipartBody.toPath()))
            .build()

        val response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

        val deploymentIdFile = layout.buildDirectory.file("central-upload-deployment-id.txt").get().asFile
        deploymentIdFile.writeText(response.body())

        logger.lifecycle("Central upload HTTP ${response.statusCode()}")
        logger.lifecycle("Central upload response written to ${deploymentIdFile.absolutePath}")

        if (response.statusCode() !in 200..299) {
            throw GradleException("Central upload failed with HTTP ${response.statusCode()}: ${response.body()}")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
