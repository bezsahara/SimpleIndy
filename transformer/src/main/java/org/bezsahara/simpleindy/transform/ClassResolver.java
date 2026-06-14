package org.bezsahara.simpleindy.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;

final class ClassResolver {
    private final Map<String, ClassInfo> inputClasses = new HashMap<>();
    private final Map<String, Optional<ClassInfo>> resolvedCache = new HashMap<>();
    private final List<Path> classpath;
    private final Log log;

    ClassResolver(List<ClassUnit> classUnits, List<Path> classpath, Log log)
            throws TransformException {
        this.classpath = classpath;
        this.log = log;

        var problems = new ArrayList<String>();
        for (var unit : classUnits) {
            try {
                var info = readInfo(unit.bytes(), unit.location());
                var previous = inputClasses.putIfAbsent(info.internalName(), info);
                if (previous != null) {
                    problems.add(
                            "Duplicate class " + info.internalName()
                                    + " found in both " + previous.location()
                                    + " and " + info.location()
                                    + ". SimpleIndy treats all provided inputs as one transformation universe, "
                                    + "so duplicate class identities are ambiguous."
                    );
                }
            } catch (RuntimeException exception) {
                problems.add(unit.location() + ": failed to read class header: " + exception.getMessage());
            }
        }

        if (!problems.isEmpty()) {
            throw TransformException.fromProblems("Could not inspect input classes", problems);
        }
    }

    Optional<ClassInfo> resolve(String internalName) {
        var inputInfo = inputClasses.get(internalName);
        if (inputInfo != null) {
            return Optional.of(inputInfo);
        }

        var cached = resolvedCache.get(internalName);
        if (cached != null) {
            return cached;
        }

        var resolved = findOutsideInputs(internalName);
        resolvedCache.put(internalName, resolved);
        return resolved;
    }

    boolean isAssignableTo(String internalName, String expectedInternalName) {
        return isAssignableTo(internalName, expectedInternalName, new HashSet<>());
    }

    private boolean isAssignableTo(String internalName, String expectedInternalName, Set<String> seen) {
        if (internalName.equals(expectedInternalName)) {
            return true;
        }
        if (!seen.add(internalName)) {
            return false;
        }

        var info = resolve(internalName);
        if (info.isEmpty()) {
            return false;
        }

        for (var iface : info.get().interfaces()) {
            if (isAssignableTo(iface, expectedInternalName, seen)) {
                return true;
            }
        }

        var superName = info.get().superName();
        return superName != null && isAssignableTo(superName, expectedInternalName, seen);
    }

    private Optional<ClassInfo> findOutsideInputs(String internalName) {
        var classFile = internalName + ".class";

        for (var root : classpath) {
            try {
                if (Files.isDirectory(root)) {
                    var path = root.resolve(classFile);
                    if (Files.isRegularFile(path)) {
                        log.trace("Resolved " + internalName + " from classpath directory " + root);
                        return Optional.of(readInfo(Files.readAllBytes(path), path.toString()));
                    }
                    continue;
                }

                if (Files.isRegularFile(root) && root.getFileName().toString().endsWith(".jar")) {
                    try (var jar = new JarFile(root.toFile())) {
                        var entry = jar.getJarEntry(classFile);
                        if (entry != null && !entry.isDirectory()) {
                            try (var input = jar.getInputStream(entry)) {
                                log.trace("Resolved " + internalName + " from classpath jar " + root);
                                return Optional.of(readInfo(input.readAllBytes(), root + "!" + classFile));
                            }
                        }
                    }
                }
            } catch (IOException | RuntimeException exception) {
                log.warn(
                        "Could not inspect classpath entry " + root + " while resolving "
                                + internalName + ": " + exception.getMessage()
                );
            }
        }

        try (var input = resourceStream(classFile)) {
            if (input != null) {
                log.trace("Resolved " + internalName + " from transformer runtime classpath");
                return Optional.of(readInfo(input.readAllBytes(), "runtime classpath:" + classFile));
            }
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not inspect runtime classpath class " + internalName + ": " + exception.getMessage());
        }

        return Optional.empty();
    }

    private static InputStream resourceStream(String classFile) {
        var contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            var input = contextLoader.getResourceAsStream(classFile);
            if (input != null) {
                return input;
            }
        }
        return ClassLoader.getSystemResourceAsStream(classFile);
    }

    private static ClassInfo readInfo(byte[] bytes, String location) {
        var reader = new ClassReader(bytes);
        var node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        var methods = new ArrayList<ClassInfo.MethodInfo>(node.methods.size());
        for (var method : node.methods) {
            methods.add(new ClassInfo.MethodInfo(method.name, method.desc, method.access));
        }

        return new ClassInfo(
                node.name,
                node.access,
                node.superName,
                List.copyOf(node.interfaces),
                List.copyOf(methods),
                location
        );
    }
}
