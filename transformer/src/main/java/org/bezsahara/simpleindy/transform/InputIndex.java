package org.bezsahara.simpleindy.transform;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class InputIndex {
    private final Map<Path, PhysicalInput> physicalInputs;
    private final List<ClassUnit> classes;

    InputIndex(Map<Path, PhysicalInput> physicalInputs, List<ClassUnit> classes) {
        this.physicalInputs = Map.copyOf(physicalInputs);
        this.classes = List.copyOf(classes);
    }

    Map<Path, PhysicalInput> physicalInputs() {
        return physicalInputs;
    }

    List<ClassUnit> classes() {
        return classes;
    }

    JarFileInput onlyJarInput() throws TransformException {
        if (physicalInputs.size() != 1) {
            throw new TransformException("Internal error: single-jar output mode had " + physicalInputs.size()
                    + " physical inputs.");
        }

        var input = physicalInputs.values().iterator().next();
        if (input instanceof JarFileInput jarInput) {
            return jarInput;
        }

        throw new TransformException("Internal error: single-jar output mode was given " + input.path()
                + ", which is not a jar input.");
    }
}
