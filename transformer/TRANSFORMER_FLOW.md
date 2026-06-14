# Transformer Flow

This document follows the transformer in execution order.

## 1. CLI parsing

### `Main.main(String[] args)`

Entrypoint for the transformer.

Flow:
1. Calls `CommandLineParser.parse(args)`.
2. If `--help` was passed, prints usage and exits.
3. Creates `IndyTransformer`.
4. Runs the transformer.
5. Converts CLI, transform, and I/O failures into readable command-line errors.

### `CommandLineParser.parse(String[] args)`

Reads raw CLI arguments and creates `CliOptions`.

Important rules:
- `--in-place` rewrites provided files directly.
- `--output-jar <file.jar>` is the only out-of-place mode.
- `--output-jar` accepts exactly one direct input jar.
- `--output-jar` must include the final output jar filename.
- Generic output directory mirroring is not supported.

### `CommandLineParser.validate(...)`

Checks parsed arguments before transformation starts.

It rejects:
- no inputs
- both `--in-place` and `--output-jar`
- neither `--in-place` nor `--output-jar`
- unsupported direct files
- `--output-jar` with anything except one direct `.jar`
- output jar path that does not end with `.jar`
- output jar path equal to input jar path

### `CliOptions`

Parsed immutable CLI state.

Fields:
- `inputs`: normalized input paths
- `outputMode`: `IN_PLACE` or `SINGLE_JAR_TO_FILE`
- `outputJar`: exact output jar path for `SINGLE_JAR_TO_FILE`
- `verify`: whether strict bootstrap verification is enabled
- `logLevel`: selected log level
- `classpath`: extra class lookup paths for verification
- `help`: whether usage was requested

### `OutputMode`

Names the write mode.

Values:
- `IN_PLACE`: mutate changed input files directly
- `SINGLE_JAR_TO_FILE`: write one transformed input jar to one explicit output jar path

## 2. Top-level transform

### `IndyTransformer.run(CliOptions options)`

Coordinates the whole transformation.

Flow:
1. `InputScanner.scan(options)` reads physical inputs and class bytes.
2. `ClassResolver` indexes class metadata for verification.
3. `TargetScanner.scan(...)` finds annotated static methods.
4. `ClassRewriter.rewrite(...)` mutates changed `ClassUnit` byte arrays.
5. `InputWriter.write(...)` writes changed files.

## 3. Input scanning

### `InputScanner.scan(CliOptions options)`

Builds an `InputIndex`.

It scans every input path:
- direct `.class` file becomes `ClassFileInput`
- direct `.jar` file becomes `JarFileInput`
- directory is walked recursively; only `.class` and `.jar` files are indexed

It does not model non-class loose files. For jars, non-class entries are ignored during scanning and copied later during writing.

### `InputScanner.scanDirectory(...)`

Walks a directory recursively.

For each regular file:
- `.class` calls `scanClassFile`
- `.jar` calls `scanJar`
- anything else is ignored

### `InputScanner.scanClassFile(...)`

Reads one physical `.class` file into a `ClassFileInput`.

The `ClassFileInput` creates exactly one `ClassUnit`.

### `InputScanner.scanJar(...)`

Reads one physical `.jar` file into a `JarFileInput`.

Rules:
- rejects manifest `Multi-Release: true`
- reads only `.class` entries into `ClassUnit`s
- records signature-file presence for warning later
- does not store resource entries in memory

### `InputScanner.addPhysicalInput(...)`

Adds one physical input to the index.

It rejects the same physical file being selected more than once.

### `InputScanner.isMultiRelease(JarFile jar)`

Reads the jar manifest.

Returns `true` only when the manifest contains `Multi-Release: true`.

## 4. Input model

### `InputIndex`

Scanner result.

Fields:
- `physicalInputs`: map from physical path to `PhysicalInput`
- `classes`: flat list of all `ClassUnit`s

The flat class list is used by scanners and rewriters. The physical input map is used when writing files back.

### `ClassUnit`

One class worth of bytecode.

It is not necessarily a physical file.

Fields:
- `owner`: the `PhysicalInput` containing this class
- `bytes`: current class bytes
- `changed`: whether transformer replaced the bytes
- `location`: human-readable error/log location
- `jarEntryName`: jar entry name, or `null` for direct `.class` files

### `ClassUnit.replaceBytes(byte[] bytes)`

Replaces current class bytes and marks the unit as changed.

### `PhysicalInput`

Common interface for actual filesystem inputs.

Methods:
- `path()`: physical file path
- `classes()`: class units contained in this physical input
- `hasChangedClasses()`: whether any owned class changed
- `writeInPlace(Log log)`: rewrite this physical input directly

### `ClassFileInput`

Represents one physical `.class` file.

It owns one `ClassUnit`.

### `ClassFileInput.writeInPlace(Log log)`

If its class changed, writes that class byte array back to the same path atomically.

### `JarFileInput`

Represents one physical `.jar` file.

It owns all class units read from `.class` entries in that jar.

Important fields:
- `classes`: all class units from the jar
- `classesByEntryName`: maps jar entry names to class units for writing
- `signed`: whether signature files were seen

### `JarFileInput.addClass(String entryName, byte[] bytes)`

Creates a `ClassUnit` for one class entry inside the jar.

### `JarFileInput.writeInPlace(Log log)`

If any class changed:
1. Creates a temporary sibling jar.
2. Calls `writeTo(temp, log)`.
3. Atomically replaces the original jar when possible.

### `JarFileInput.writeTo(Path target, Log log)`

Writes a transformed jar to a target path.

It reopens the original jar and iterates entries:
- changed class entry: writes the changed `ClassUnit` bytes
- unchanged class entry: copies original entry bytes
- resource/directory entry: copies original entry bytes/metadata

### `JarFileInput.bytesForEntry(...)`

Chooses bytes for one jar entry during writing.

Changed class entries come from `ClassUnit.bytes()`. Everything else is read from the original jar.

### `JarFileInput.copyEntry(...)`

Creates the output jar entry metadata from the original jar entry.

For stored entries, it recalculates size and CRC because changed bytes may have a different length.

### `JarFileInput.isSignatureEntry(String entryName)`

Detects jar signature-related entries under `META-INF`.

Used only to warn that rewriting signed jars will likely break signature verification.

## 5. Class metadata lookup

### `ClassResolver`

Indexes class metadata without loading classes.

Lookup order:
1. classes from current inputs
2. paths from `--classpath`
3. transformer runtime classpath

### `ClassResolver.resolve(String internalName)`

Returns class metadata for an internal JVM class name if it can be found.

### `ClassResolver.isAssignableTo(...)`

Checks inheritance/interface assignability using bytecode metadata.

Used for verification, such as confirming `SimpleIndy.value()` implements `SimpleBootstrap`.

## 6. Annotation discovery

### `TargetScanner.scan(...)`

Walks all `ClassUnit`s and finds static methods annotated with:
- `@SimpleIndy`
- `@CustomIndy`

It returns a map from `MethodKey` to `IndyMethodTarget`.

### `TargetScanner.scanMethod(...)`

Handles one method.

It rejects:
- both indy annotations on one method
- non-static annotated methods
- invalid annotation values

### `TargetScanner.readSimpleTarget(...)`

Builds an `IndyMethodTarget` for `@SimpleIndy`.

It always uses the project-provided simple bootstrap handle and passes the annotation class literal as the first bootstrap argument.

### `TargetScanner.readCustomTarget(...)`

Builds an `IndyMethodTarget` for `@CustomIndy`.

It uses the exact bootstrap owner, name, descriptor, and interface flag from the annotation or resolver.

### `TargetScanner.ownerIsInterface(...)`

Determines the bootstrap owner interface flag.

If annotation value was omitted, it resolves the owner class and derives the flag. If `--verify` is enabled, it also checks explicit values against resolved bytecode.

### `TargetScanner.validateCustomBootstrap(...)`

With `--verify`, confirms the bootstrap method exists and is static.

### `TargetScanner.validateBootstrapShape(...)`

With `--verify`, checks bootstrap descriptor shape.

Required prefix:
- `MethodHandles.Lookup`
- `String`
- `MethodType`

Return type must be `CallSite` or a subtype.

## 7. Rewrite

### `ClassRewriter.rewrite(InputIndex input, Map<MethodKey, IndyMethodTarget> targets)`

Walks every method instruction in every `ClassUnit`.

When it finds an `INVOKESTATIC` call matching an annotated target, it replaces that instruction with `invokedynamic`.

If a class changed, it writes new bytes back into the same `ClassUnit` with `replaceBytes`.

### `ClassRewriter.readClass(byte[] bytes)`

Parses class bytes into ASM tree form for rewriting.

### `ClassRewriter.opcodeName(int opcode)`

Formats invoke opcodes for readable error messages.

## 8. Writing

### `InputWriter.write(InputIndex inputIndex, CliOptions options)`

Writes changed bytes according to output mode.

For `IN_PLACE`:
- iterates physical inputs
- each input writes itself in place

For `SINGLE_JAR_TO_FILE`:
- gets the only jar input
- writes it to `CliOptions.outputJar`

### `FileWrites.writeBytesAtomically(Path target, byte[] bytes)`

Writes bytes through a temporary sibling file, then replaces the target.

Used for direct `.class` in-place writes.

### `FileWrites.temporarySiblingOf(Path target)`

Creates a temp file next to the target.

This keeps replacement on the same filesystem when possible.

### `FileWrites.moveReplacing(Path source, Path target)`

Attempts atomic replace first.

Falls back to normal replace if atomic move is unsupported.
