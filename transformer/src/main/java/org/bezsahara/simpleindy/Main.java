package org.bezsahara.simpleindy;

import org.bezsahara.simpleindy.cli.CommandLineException;
import org.bezsahara.simpleindy.cli.CommandLineParser;
import org.bezsahara.simpleindy.transform.IndyTransformer;
import org.bezsahara.simpleindy.transform.Log;
import org.bezsahara.simpleindy.transform.TransformException;

import java.io.IOException;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            var options = CommandLineParser.parse(args);
            if (options.help()) {
                System.out.print(CommandLineParser.usage());
                return;
            }

            new IndyTransformer(new Log(options.logLevel())).run(options);
        } catch (CommandLineException exception) {
            System.err.println("error: " + exception.getMessage());
            System.err.println();
            System.err.print(CommandLineParser.usage());
            System.exit(2);
        } catch (TransformException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (IOException exception) {
            System.err.println("I/O failure while transforming files: " + exception.getMessage());
            System.exit(1);
        } catch (RuntimeException exception) {
            System.err.println("Unexpected transformer failure: " + exception.getMessage());
            System.exit(1);
        }
    }
}
