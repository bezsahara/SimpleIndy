package org.bezsahara.simpleindy.transform;

import org.bezsahara.simpleindy.cli.CliOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class InputScanner {
    private final Log log;

    InputScanner(Log log) {
        this.log = log;
    }

    InputIndex scan(CliOptions options) throws IOException, TransformException {
        var physicalInputs = new LinkedHashMap<Path, PhysicalInput>();
        var classUnits = new ArrayList<ClassUnit>();
        var problems = new ArrayList<String>();

        for (var input : options.inputs()) {
            if (Files.isDirectory(input)) {
                scanDirectory(input, physicalInputs, classUnits, problems);
                continue;
            }

            if (isJar(input)) {
                scanJar(input, physicalInputs, classUnits, problems);
            } else if (isClass(input)) {
                scanClassFile(input, physicalInputs, classUnits, problems);
            }
        }

        if (!problems.isEmpty()) {
            throw TransformException.fromProblems("Could not scan transformer inputs", problems);
        }

        log.info(
                "Scanned " + physicalInputs.size() + " physical input"
                        + (physicalInputs.size() == 1 ? "" : "s")
                        + " containing " + classUnits.size() + " class"
                        + (classUnits.size() == 1 ? "" : "es") + "."
        );
        return new InputIndex(physicalInputs, classUnits);
    }

    private void scanDirectory(
            Path root,
            LinkedHashMap<Path, PhysicalInput> physicalInputs,
            List<ClassUnit> classUnits,
            List<String> problems
    ) throws IOException {
        var files = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.naturalOrder());

        for (var file : files) {
            if (isJar(file)) {
                scanJar(file, physicalInputs, classUnits, problems);
            } else if (isClass(file)) {
                scanClassFile(file, physicalInputs, classUnits, problems);
            }
        }
    }

    private void scanClassFile(
            Path file,
            LinkedHashMap<Path, PhysicalInput> physicalInputs,
            List<ClassUnit> classUnits,
            List<String> problems
    ) {
        try {
            var bytes = Files.readAllBytes(file);
            var input = new ClassFileInput(file, bytes);
            addPhysicalInput(input, physicalInputs, classUnits, problems);
            log.trace("Queued class " + file);
        } catch (IOException exception) {
            problems.add(file + ": failed to read class file: " + exception.getMessage());
        }
    }

    private void scanJar(
            Path file,
            LinkedHashMap<Path, PhysicalInput> physicalInputs,
            List<ClassUnit> classUnits,
            List<String> problems
    ) {
        try (var jar = new JarFile(file.toFile())) {
            if (isMultiRelease(jar)) {
                problems.add(file + ": multi-release jars are not supported yet. "
                        + "The manifest has Multi-Release: true.");
                return;
            }

            var input = new JarFileInput(file);
            var enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                var entry = enumeration.nextElement();
                var entryName = entry.getName();

                if (JarFileInput.isSignatureEntry(entryName)) {
                    input.markSigned();
                }

                if (!entry.isDirectory() && entryName.endsWith(".class")) {
                    try (var entryInput = jar.getInputStream(entry)) {
                        input.addClass(entryName, entryInput.readAllBytes());
                    }
                }
            }

            addPhysicalInput(input, physicalInputs, classUnits, problems);
            log.trace("Queued jar " + file);
        } catch (IOException exception) {
            problems.add(file + ": failed to read jar: " + exception.getMessage());
        }
    }

    private static void addPhysicalInput(
            PhysicalInput input,
            LinkedHashMap<Path, PhysicalInput> physicalInputs,
            List<ClassUnit> classUnits,
            List<String> problems
    ) {
        if (physicalInputs.containsKey(input.path())) {
            problems.add("Input file was selected more than once: " + input.path());
            return;
        }
        physicalInputs.put(input.path(), input);
        classUnits.addAll(input.classes());
    }

    private static boolean isClass(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".class");
    }

    private static boolean isJar(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static boolean isMultiRelease(JarFile jar) throws IOException {
        var manifest = jar.getManifest();
        if (manifest == null) {
            return false;
        }

        var value = manifest.getMainAttributes().getValue("Multi-Release");
        return value != null && "true".equals(value.toLowerCase(Locale.ROOT));
    }
}
