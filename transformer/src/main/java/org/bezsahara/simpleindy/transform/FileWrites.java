package org.bezsahara.simpleindy.transform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class FileWrites {
    private FileWrites() {
    }

    static void writeBytesAtomically(Path target, byte[] bytes) throws IOException {
        var temp = temporarySiblingOf(target);
        try {
            Files.write(temp, bytes);
            moveReplacing(temp, target);
        } catch (IOException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }
    }

    static Path temporarySiblingOf(Path target) throws IOException {
        var directory = target.getParent();
        if (directory == null) {
            directory = target.toAbsolutePath().normalize().getParent();
        }
        if (directory == null) {
            directory = Path.of(".").toAbsolutePath().normalize();
        }
        Files.createDirectories(directory);
        return Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
    }

    static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
