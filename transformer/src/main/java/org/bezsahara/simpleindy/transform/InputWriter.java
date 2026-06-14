package org.bezsahara.simpleindy.transform;

import org.bezsahara.simpleindy.cli.CliOptions;
import org.bezsahara.simpleindy.cli.OutputMode;

import java.io.IOException;

final class InputWriter {
    private final Log log;

    InputWriter(Log log) {
        this.log = log;
    }

    void write(InputIndex inputIndex, CliOptions options) throws IOException, TransformException {
        if (options.outputMode() == OutputMode.SINGLE_JAR_TO_FILE) {
            inputIndex.onlyJarInput().writeTo(options.outputJar(), log);
            log.debug("Wrote transformed jar " + options.outputJar());
            return;
        }

        for (var input : inputIndex.physicalInputs().values()) {
            input.writeInPlace(log);
        }
    }
}
