package org.bezsahara.simpleindy.transform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class ClassFileInput implements PhysicalInput {
    private final Path path;
    private final ClassUnit classUnit;

    ClassFileInput(Path path, byte[] bytes) {
        this.path = path;
        this.classUnit = new ClassUnit(this, bytes, path.toString(), null);
    }

    ClassUnit classUnit() {
        return classUnit;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public List<ClassUnit> classes() {
        return List.of(classUnit);
    }

    @Override
    public boolean hasChangedClasses() {
        return classUnit.changed();
    }

    @Override
    public void writeInPlace(Log log) throws IOException {
        if (!classUnit.changed()) {
            return;
        }

        FileWrites.writeBytesAtomically(path, classUnit.bytes());
        log.debug("Wrote class file " + path);
    }
}
