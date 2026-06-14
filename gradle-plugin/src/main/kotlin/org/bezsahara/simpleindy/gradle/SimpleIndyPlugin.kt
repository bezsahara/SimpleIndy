package org.bezsahara.simpleindy.gradle

import org.bezsahara.simpleindy.cli.CliOptions
import org.bezsahara.simpleindy.cli.OutputMode
import org.bezsahara.simpleindy.transform.IndyTransformer
import org.bezsahara.simpleindy.transform.Log
import org.bezsahara.simpleindy.transform.TransformException
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import java.io.File
import java.io.IOException
import java.nio.file.Path

class SimpleIndyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<SimpleIndyExtension>("simpleIndy")
        val defaultTaskSelections = mutableMapOf<String, Provider<Boolean>>()

        project.plugins.withType(JavaPlugin::class.java) {
            val sourceSets = project.extensions.getByType<SourceSetContainer>()
            sourceSets.configureEach {
                configureSourceSet(project, extension, this, defaultTaskSelections)
            }
            configureManualClassOutputs(project, extension, defaultTaskSelections)
        }
    }

    private fun configureSourceSet(
        project: Project,
        extension: SimpleIndyExtension,
        sourceSet: SourceSet,
        defaultTaskSelections: MutableMap<String, Provider<Boolean>>,
    ) {
        configureJavaCompileOutput(project, extension, sourceSet, defaultTaskSelections)
        configureKotlinCompileOutput(project, extension, sourceSet, defaultTaskSelections)
    }

    private fun configureJavaCompileOutput(
        project: Project,
        extension: SimpleIndyExtension,
        sourceSet: SourceSet,
        defaultTaskSelections: MutableMap<String, Provider<Boolean>>,
    ) {
        val compileTask = project.tasks.named<JavaCompile>(sourceSet.compileJavaTaskName)
        val selectedForTransform = project.providers.provider {
            defaultClassOutputSelected(extension, sourceSet)
        }

        compileTask.configure {
            defaultTaskSelections[path] = selectedForTransform
            configureTransformAction(
                project,
                extension,
                this,
                selectedForTransform,
                "Java classes for source set '${sourceSet.name}'",
                { listOf(destinationDirectory.get().asFile) },
                { sourceSet.compileClasspath.files + extension.verificationClasspath.files },
            )
        }
    }

    private fun configureKotlinCompileOutput(
        project: Project,
        extension: SimpleIndyExtension,
        sourceSet: SourceSet,
        defaultTaskSelections: MutableMap<String, Provider<Boolean>>,
    ) {
        val kotlinCompileTaskName = sourceSet.getCompileTaskName("kotlin")
        val selectedForTransform = project.providers.provider {
            defaultClassOutputSelected(extension, sourceSet)
        }

        project.tasks.matching { it.name == kotlinCompileTaskName }.configureEach {
            defaultTaskSelections[path] = selectedForTransform
            configureTransformAction(
                project,
                extension,
                this,
                selectedForTransform,
                "Kotlin classes for source set '${sourceSet.name}'",
                { outputs.files.files.toList() },
                { sourceSet.compileClasspath.files + extension.verificationClasspath.files },
            )
        }
    }

    private fun configureManualClassOutputs(
        project: Project,
        extension: SimpleIndyExtension,
        defaultTaskSelections: Map<String, Provider<Boolean>>,
    ) {
        project.afterEvaluate {
            val selectedForTransform = project.providers.provider {
                extension.enabled.get()
            }
            for (producerTask in extension.classOutputs.buildDependencies.getDependencies(null)) {
                if (defaultTaskSelections[producerTask.path]?.get() == true) {
                    continue
                }

                configureTransformAction(
                    project,
                    extension,
                    producerTask,
                    selectedForTransform,
                    "class outputs from task '${producerTask.path}'",
                    { producerTask.outputs.files.files.toList() },
                    { extension.verificationClasspath.files },
                )
            }
        }
    }

    private fun configureTransformAction(
        project: Project,
        extension: SimpleIndyExtension,
        task: Task,
        selectedForTransform: Provider<Boolean>,
        inputDescription: String,
        inputFiles: () -> Iterable<File>,
        classpathFiles: () -> Iterable<File>,
    ) {
        task.inputs.property("simpleIndy.selectedForTransform", selectedForTransform)
        task.inputs.property("simpleIndy.verify", extension.verify)
        task.inputs.property("simpleIndy.logLevel", extension.logLevel)
        task.inputs.files(project.provider { classpathFiles().toList() })
            .withPropertyName("simpleIndy.verificationClasspath")
            .withNormalizer(ClasspathNormalizer::class.java)

        task.doLast("simpleIndyTransform") {
            if (!selectedForTransform.get()) {
                return@doLast
            }

            runTransformer(project, extension, inputDescription, inputFiles(), classpathFiles())
        }
    }

    private fun defaultClassOutputSelected(extension: SimpleIndyExtension, sourceSet: SourceSet): Boolean {
        return extension.enabled.get()
                && extension.useDefaultClassOutputs.get()
                && extension.sourceSetNames.get().contains(sourceSet.name)
    }

    private fun runTransformer(
        project: Project,
        extension: SimpleIndyExtension,
        inputDescription: String,
        inputFiles: Iterable<File>,
        classpathFiles: Iterable<File>,
    ) {
        val inputs = normalizeTransformInputs(inputFiles)
        if (inputs.isEmpty()) {
            project.logger.info("SimpleIndy skipped $inputDescription: no class, jar, or class-directory outputs exist.")
            return
        }

        val level = extension.logLevel.get().toTransformerLevel()
        val transformerLog = Log(level) { logLevel, message ->
            writeGradleLog(project, logLevel, message)
        }

        val options = CliOptions(
            inputs,
            OutputMode.IN_PLACE,
            null,
            extension.verify.get(),
            level,
            normalizeClasspath(classpathFiles),
            false,
        )

        try {
            IndyTransformer(transformerLog).run(options)
        } catch (exception: TransformException) {
            throw GradleException(exception.message ?: "SimpleIndy transformation failed.", exception)
        } catch (exception: IOException) {
            throw GradleException("SimpleIndy failed to transform $inputDescription: ${exception.message}", exception)
        }
    }

    private fun normalizeTransformInputs(files: Iterable<File>): List<Path> {
        return files.asSequence()
            .filter { it.exists() && (it.isDirectory || it.name.endsWith(".class") || it.name.endsWith(".jar")) }
            .map { it.toPath().toAbsolutePath().normalize() }
            .distinct()
            .toList()
    }

    private fun normalizeClasspath(files: Iterable<File>): List<Path> {
        return files.asSequence()
            .filter { it.exists() }
            .map { it.toPath().toAbsolutePath().normalize() }
            .distinct()
            .toList()
    }

    private fun writeGradleLog(project: Project, level: Log.Level, message: String) {
        when (level) {
            Log.Level.ERROR -> project.logger.error(message)
            Log.Level.WARN -> project.logger.warn(message)
            Log.Level.INFO -> project.logger.lifecycle(message)
            Log.Level.DEBUG -> project.logger.info(message)
            Log.Level.TRACE -> project.logger.debug(message)
        }
    }
}
