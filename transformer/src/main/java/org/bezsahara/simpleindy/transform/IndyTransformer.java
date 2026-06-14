package org.bezsahara.simpleindy.transform;

import org.bezsahara.simpleindy.cli.CliOptions;

import java.io.IOException;

public final class IndyTransformer {
    private final Log log;

    public IndyTransformer(Log log) {
        this.log = log;
    }

    public void run(CliOptions options) throws IOException, TransformException {
        log.info("Scanning inputs.");
        var input = new InputScanner(log).scan(options);

        log.info("Indexing classes for annotation discovery and verification.");
        var resolver = new ClassResolver(input.classes(), options.classpath(), log);

        var targetScan = new TargetScanner(log).scan(input.classes(), resolver, options.verify());
        if (targetScan.targets().isEmpty()) {
            log.warn("No @SimpleIndy or @CustomIndy targets were found.");
        }

        var rewriteResult = new ClassRewriter(log).rewrite(input, targetScan.targets());

        log.info("Writing transformed files.");
        new InputWriter(log).write(input, options);

        log.info(
                "Done. Targets: " + targetScan.targets().size()
                        + ", rewritten call sites: " + rewriteResult.replacedCallSites()
                        + "."
        );
    }
}
