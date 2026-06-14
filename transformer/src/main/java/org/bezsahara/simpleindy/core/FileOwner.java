package org.bezsahara.simpleindy.core;

import java.nio.file.Path;
import java.util.HashMap;

public class FileOwner {
    // name/path to its bytes
    public final HashMap<String, byte[]> files = new HashMap<>();
    public final Path originalPath;
    public FileOwner(Path originalPath) {
        this.originalPath = originalPath;
    }

    public final boolean isJar() {
        return originalPath.getFileName().toString().endsWith(".jar");
    }
}
