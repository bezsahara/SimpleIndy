package org.bezsahara.simpleindy.transform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

final class JarFileInput implements PhysicalInput {
    private final Path path;
    private final List<ClassUnit> classes = new ArrayList<>();
    private final Map<String, ClassUnit> classesByEntryName = new LinkedHashMap<>();
    private boolean signed;

    JarFileInput(Path path) {
        this.path = path;
    }

    void addClass(String entryName, byte[] bytes) {
        var unit = new ClassUnit(this, bytes, path + "!" + entryName, entryName);
        classes.add(unit);
        classesByEntryName.put(entryName, unit);
    }

    void markSigned() {
        signed = true;
    }

    boolean signed() {
        return signed;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public List<ClassUnit> classes() {
        return List.copyOf(classes);
    }

    @Override
    public boolean hasChangedClasses() {
        for (var unit : classes) {
            if (unit.changed()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeInPlace(Log log) throws IOException {
        if (!hasChangedClasses()) {
            return;
        }

        var temp = FileWrites.temporarySiblingOf(path);
        try {
            writeTo(temp, log);
            FileWrites.moveReplacing(temp, path);
            log.debug("Wrote jar " + path);
        } catch (IOException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }
    }

    void writeTo(Path target, Log log) throws IOException {
        if (signed && hasChangedClasses()) {
            log.warn(
                    "Rewriting classes inside signed jar " + path
                            + ". Existing META-INF signature files will probably no longer verify."
            );
        }

        var parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (var jar = new JarFile(path.toFile());
             var output = new JarOutputStream(Files.newOutputStream(target))) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var replacement = classesByEntryName.get(entry.getName());
                var bytes = bytesForEntry(jar, entry, replacement);
                var outEntry = copyEntry(entry, bytes);

                output.putNextEntry(outEntry);
                if (!entry.isDirectory()) {
                    output.write(bytes);
                }
                output.closeEntry();
            }
        }
    }

    private static byte[] bytesForEntry(JarFile jar, java.util.jar.JarEntry entry, ClassUnit replacement)
            throws IOException {
        if (replacement != null && replacement.changed()) {
            return replacement.bytes();
        }
        if (entry.isDirectory()) {
            return new byte[0];
        }
        try (var input = jar.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static java.util.jar.JarEntry copyEntry(java.util.jar.JarEntry source, byte[] bytes) {
        var target = new java.util.jar.JarEntry(source.getName());
        if (source.getTime() >= 0) {
            target.setTime(source.getTime());
        }
        target.setComment(source.getComment());
        if (source.getExtra() != null) {
            target.setExtra(source.getExtra());
        }

        if (source.getMethod() == ZipEntry.STORED) {
            target.setMethod(ZipEntry.STORED);
            target.setSize(bytes.length);
            target.setCompressedSize(bytes.length);
            target.setCrc(crc(bytes));
        }

        return target;
    }

    static boolean isSignatureEntry(String entryName) {
        var upper = entryName.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC"));
    }

    private static long crc(byte[] bytes) {
        var crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }
}
