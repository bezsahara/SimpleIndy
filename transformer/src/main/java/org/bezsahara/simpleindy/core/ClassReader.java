package org.bezsahara.simpleindy.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class ClassReader {
    public static FileOwner readFromPath(Path path) throws IOException {
        Objects.requireNonNull(path, "path");

        var fileOwner = new FileOwner(path);
        var map = fileOwner.files;

        try (Stream<Path> files = Files.walk(path)) {
            files.forEach(p -> {
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                        && p.getFileName().toString().endsWith(".class")) {
                    try {
                        map.put(p.toString(), Files.readAllBytes(p));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        return fileOwner;
    }

    public static FileOwner readFromJar(Path jarPath) throws IOException {
        Objects.requireNonNull(jarPath, "jarPath");

        var fileOwner = new FileOwner(jarPath);
        var map = fileOwner.files;

        try (var jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();

            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();

                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (var in = jar.getInputStream(entry)) {
                        map.put(entry.getRealName(), in.readAllBytes());
                    }
                }
            }
        }

        return fileOwner;
    }
}