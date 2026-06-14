package org.bezsahara.simpleindy.transform;

final class ClassUnit {
    private final PhysicalInput owner;
    private final String location;
    private final String jarEntryName;
    private byte[] bytes;
    private boolean changed;

    ClassUnit(PhysicalInput owner, byte[] bytes, String location, String jarEntryName) {
        this.owner = owner;
        this.bytes = bytes;
        this.location = location;
        this.jarEntryName = jarEntryName;
    }

    PhysicalInput owner() {
        return owner;
    }

    byte[] bytes() {
        return bytes;
    }

    void replaceBytes(byte[] bytes) {
        this.bytes = bytes;
        this.changed = true;
    }

    boolean changed() {
        return changed;
    }

    String location() {
        return location;
    }

    String jarEntryName() {
        return jarEntryName;
    }
}
