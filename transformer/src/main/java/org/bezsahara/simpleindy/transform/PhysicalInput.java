package org.bezsahara.simpleindy.transform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

interface PhysicalInput {
    Path path();

    List<ClassUnit> classes();

    boolean hasChangedClasses();

    void writeInPlace(Log log) throws IOException;
}
