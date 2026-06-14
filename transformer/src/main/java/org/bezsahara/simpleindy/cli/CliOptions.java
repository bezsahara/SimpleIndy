package org.bezsahara.simpleindy.cli;

import org.bezsahara.simpleindy.transform.Log;

import java.nio.file.Path;
import java.util.List;

public record CliOptions(
        List<Path> inputs,
        OutputMode outputMode,
        Path outputJar,
        boolean verify,
        Log.Level logLevel,
        List<Path> classpath,
        boolean help
) {
}
