package org.bezsahara.simpleindy.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

abstract class SimpleIndyExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    val verify: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    val logLevel: Property<SimpleIndyLogLevel> =
        objects.property(SimpleIndyLogLevel::class.java).convention(SimpleIndyLogLevel.INFO)

    val sourceSetNames: SetProperty<String> =
        objects.setProperty(String::class.java).convention(setOf("main"))

    val useDefaultClassOutputs: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    val classOutputs: ConfigurableFileCollection = objects.fileCollection()

    val verificationClasspath: ConfigurableFileCollection = objects.fileCollection()
}
