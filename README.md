# SimpleIndy

SimpleIndy lets you write normal Java call sites and replace selected method calls with JVM `invokedynamic` instructions during the build.

You mark a static method as an indy stub, call it like a normal method, and the Gradle plugin rewrites calls to that method after compilation. Your source code stays ordinary Java; the compiled bytecode gets the `invokedynamic`.

## Gradle Usage

Apply the plugin and add the annotations dependency:

```kotlin
plugins {
    id("java")
    id("org.bezsahara.simpleindy") version "0.1.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bezsahara:annotations:0.1.1")
}
```

For local development builds, publish SimpleIndy first:

```powershell
.\gradlew.bat publishSimpleIndyToMavenLocal
```

Then use `mavenLocal()` in the consuming build:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

## Java Example

Create a bootstrap class by implementing `SimpleBootstrap`:

```java
package example;

import org.bezsahara.simpleindy.annotations.SimpleBootstrap;
import org.bezsahara.simpleindy.annotations.SimpleIndy;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Main {
    public static void main(String[] args) {
        System.out.println(answer());
    }

    static class AnswerBootstrap implements SimpleBootstrap {
        @Override
        public CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type, Object[] args) {
            return new ConstantCallSite(MethodHandles.constant(int.class, 42));
        }
    }

    @SimpleIndy(AnswerBootstrap.class)
    public static int answer() {
        throw new AssertionError("Replaced by SimpleIndy");
    }
}
```

In source code, `answer()` is just a static method call. After `compileJava`, SimpleIndy rewrites calls to `answer()` into `invokedynamic` instructions whose bootstrap is handled by `AnswerBootstrap`.

The annotated method is a stub. Its body is not meant to run after transformation.

## What The Plugin Does

By default, the plugin transforms the selected source set compile outputs in place:

- Java output, for example `build/classes/java/main`
- Kotlin JVM output when Kotlin JVM compile tasks are present, for example `build/classes/kotlin/main`

This is intentionally done on the compiler output directory so Gradle `run`, `test`, `jar`, and IntelliJ IDEA run configurations all see the same transformed bytecode.

Default configuration:

```kotlin
simpleIndy {
    enabled.set(true)
    verify.set(false)
    logLevel.set(SimpleIndyLogLevel.INFO)
    sourceSetNames.set(setOf("main"))
    useDefaultClassOutputs.set(true)
}
```

## Plugin Options

```kotlin
simpleIndy {
    enabled.set(true)
}
```

Turns transformation on or off. Default: `true`.

```kotlin
simpleIndy {
    verify.set(true)
}
```

Enables stricter verification for bootstrap owners and descriptors. Default: `false`.

```kotlin
simpleIndy {
    logLevel.set(SimpleIndyLogLevel.DEBUG)
}
```

Controls transformer logging. Supported levels: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. Default: `INFO`.

```kotlin
simpleIndy {
    sourceSetNames.set(setOf("main", "test"))
}
```

Chooses which source sets get the default Java/Kotlin output transforms. Default: `main`.

```kotlin
simpleIndy {
    verificationClasspath.from(configurations.runtimeClasspath)
}
```

Adds extra lookup locations used by verification.

```kotlin
simpleIndy {
    classOutputs.from(tasks.named("compileKotlin"))
}
```

Adds another bytecode-producing task to transform. This is mainly for custom compile or bytecode generation tasks. Default Java and Kotlin JVM outputs are already handled when `useDefaultClassOutputs` is `true`.

```kotlin
simpleIndy {
    useDefaultClassOutputs.set(false)
    classOutputs.from(tasks.named("myBytecodeTask"))
}
```

Disables automatic Java/Kotlin output handling and uses only explicitly supplied output tasks.

## Kotlin

SimpleIndy works on JVM bytecode, so Kotlin JVM output can be transformed too. The Gradle plugin detects normal Kotlin JVM compile tasks by name, such as `compileKotlin`, when the Kotlin JVM plugin is applied.

Example Kotlin stub:

```kotlin
import org.bezsahara.simpleindy.annotations.SimpleBootstrap
import org.bezsahara.simpleindy.annotations.SimpleIndy
import java.lang.invoke.CallSite
import java.lang.invoke.ConstantCallSite
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class AnswerBootstrap : SimpleBootstrap {
    override fun bootstrap(
        lookup: MethodHandles.Lookup,
        name: String,
        type: MethodType,
        args: Array<out Any?>,
    ): CallSite {
        return ConstantCallSite(MethodHandles.constant(Int::class.java, 42))
    }
}

@SimpleIndy(AnswerBootstrap::class)
fun answer(): Int = error("Replaced by SimpleIndy")
```

## Annotations

### `@SimpleIndy`

`@SimpleIndy` is the easy mode.

```java
@SimpleIndy(MyBootstrap.class)
public static int value() {
    throw new AssertionError("Replaced by SimpleIndy");
}
```

It uses SimpleIndy's built-in bootstrap adapter:

```text
org.bezsahara.simpleindy.annotations.impl.BootstrapForSimpleIndy.bootstrap
```

The transformer emits the class from `@SimpleIndy(...)` as the static bootstrap argument. The adapter creates an instance of that class and calls its `SimpleBootstrap.bootstrap(...)` method. If `name` is empty, the invokedynamic name is the annotated method name.

```java
@SimpleIndy(value = MyBootstrap.class, name = "customName")
public static int value() {
    throw new AssertionError("Replaced by SimpleIndy");
}
```

By default, the bootstrap implementation class needs a no-argument constructor. You can also annotate the bootstrap class with `@InitWithStatic("methodName")` to create the `SimpleBootstrap` instance through a static factory method.

### `@CustomIndy`

`@CustomIndy` gives direct control over the bootstrap method handle.

```java
@CustomIndy(
    owner = "example/MyBootstrap",
    bootstrap = "bootstrap",
    bootstrapDescriptor = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
    ownerIsInterface = false
)
public static int value() {
    throw new AssertionError("Replaced by SimpleIndy");
}
```

Use this when you want to provide the exact bootstrap owner, method name, descriptor, and interface flag yourself.

## Rules

- Annotated methods must currently be static.
- Calls to annotated methods are replaced across the transformed input set.
- Duplicate class names in the transformed input set are treated as an error.
- Multi-release jars are currently rejected.

## Standalone CLI

The Gradle plugin is the normal path. The transformer can also be run directly against `.class` files, directories, or jars.

In-place transform:

```powershell
java -jar transformer.jar --in-place build\classes\java\main
```

Transform one jar to a separate output jar:

```powershell
java -jar transformer.jar --output-jar build\libs\app-transformed.jar build\libs\app.jar
```

Useful options:

```text
--verify
--classpath <path>
--log-level error|warn|info|debug|trace
--help
```

`--output-jar` accepts exactly one direct input jar and the output path must include the final jar file name.

## Inspecting Output

Use `javap` to verify that call sites were rewritten:

```powershell
javap -classpath build\classes\java\main -c example.Main
```

You should see `invokedynamic` where the original static call was used.
