package org.bezsahara.simpleindy.cli;

import org.bezsahara.simpleindy.transform.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandLineParser {
    private CommandLineParser() {
    }

    public static CliOptions parse(String[] args) throws CommandLineException {
        var inputs = new ArrayList<Path>();
        var classpath = new ArrayList<Path>();
        Path outputJar = null;
        boolean inPlaceRequested = false;
        boolean verify = false;
        boolean help = false;
        Log.Level logLevel = Log.Level.INFO;

        for (int i = 0; i < args.length; i++) {
            var arg = args[i];

            if ("--".equals(arg)) {
                for (int j = i + 1; j < args.length; j++) {
                    inputs.add(Path.of(args[j]));
                }
                break;
            }

            if ("--help".equals(arg) || "-h".equals(arg)) {
                help = true;
                continue;
            }

            if ("--in-place".equals(arg)) {
                inPlaceRequested = true;
                continue;
            }

            if ("--verify".equals(arg)) {
                verify = true;
                continue;
            }

            if ("--output-jar".equals(arg)) {
                outputJar = readValue(args, ++i, arg);
                continue;
            }

            if (arg.startsWith("--output-jar=")) {
                outputJar = Path.of(nonEmptyValue(arg.substring("--output-jar=".length()), "--output-jar"));
                continue;
            }

            if ("--log-level".equals(arg)) {
                logLevel = parseLogLevel(readValue(args, ++i, arg).toString());
                continue;
            }

            if (arg.startsWith("--log-level=")) {
                logLevel = parseLogLevel(nonEmptyValue(arg.substring("--log-level=".length()), "--log-level"));
                continue;
            }

            if ("--classpath".equals(arg) || "-cp".equals(arg)) {
                addClasspath(classpath, readValue(args, ++i, arg).toString());
                continue;
            }

            if (arg.startsWith("--classpath=")) {
                addClasspath(classpath, nonEmptyValue(arg.substring("--classpath=".length()), "--classpath"));
                continue;
            }

            if (arg.startsWith("-")) {
                throw new CommandLineException("Unknown option '" + arg + "'. Use --help to see supported options.");
            }

            inputs.add(Path.of(arg));
        }

        var normalizedInputs = normalizeInputs(inputs);
        var normalizedClasspath = normalizeClasspath(classpath);
        Path normalizedOutputJar = outputJar == null ? null : outputJar.toAbsolutePath().normalize();
        var outputMode = normalizedOutputJar == null ? OutputMode.IN_PLACE : OutputMode.SINGLE_JAR_TO_FILE;

        if (!help) {
            validate(normalizedInputs, normalizedOutputJar, inPlaceRequested);
        }

        return new CliOptions(
                List.copyOf(normalizedInputs),
                outputMode,
                normalizedOutputJar,
                verify,
                logLevel,
                List.copyOf(normalizedClasspath),
                help
        );
    }

    public static String usage() {
        return """
                Usage:
                  simpleindy [options] --in-place <input>...
                  simpleindy [options] --output-jar <output.jar> <input.jar>

                Inputs:
                  A .class file, a .jar file, or a directory scanned recursively for .class and .jar files.

                Options:
                      --in-place           Rewrite input .class/.jar files in place.
                      --output-jar <jar>   Write one direct input jar to this exact output jar path.
                      --verify             Resolve and validate bootstrap owners and method descriptors.
                  -cp, --classpath <path>  Extra verification lookup path. Uses the platform path separator.
                      --log-level <level>  error, warn, info, debug, or trace. Default: info.
                  -h, --help               Show this help.
                """;
    }

    private static Path readValue(String[] args, int index, String option) throws CommandLineException {
        if (index >= args.length) {
            throw new CommandLineException("Missing value for " + option + ".");
        }
        return Path.of(nonEmptyValue(args[index], option));
    }

    private static String nonEmptyValue(String value, String option) throws CommandLineException {
        if (value == null || value.isBlank()) {
            throw new CommandLineException("Missing value for " + option + ".");
        }
        return value;
    }

    private static void addClasspath(List<Path> classpath, String value) {
        for (var part : value.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!part.isBlank()) {
                classpath.add(Path.of(part));
            }
        }
    }

    private static Log.Level parseLogLevel(String value) throws CommandLineException {
        try {
            return Log.Level.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new CommandLineException(exception.getMessage());
        }
    }

    private static List<Path> normalizeInputs(List<Path> inputs) {
        var normalized = new ArrayList<Path>(inputs.size());
        for (var input : inputs) {
            normalized.add(input.toAbsolutePath().normalize());
        }
        return normalized;
    }

    private static List<Path> normalizeClasspath(List<Path> classpath) throws CommandLineException {
        var normalized = new ArrayList<Path>(classpath.size());
        for (var path : classpath) {
            var absolute = path.toAbsolutePath().normalize();
            if (!Files.exists(absolute)) {
                throw new CommandLineException("Classpath entry does not exist: " + absolute);
            }
            normalized.add(absolute);
        }
        return normalized;
    }

    private static void validate(List<Path> inputs, Path outputJar, boolean inPlaceRequested) throws CommandLineException {
        if (inputs.isEmpty()) {
            throw new CommandLineException("No inputs were provided.");
        }

        if (inPlaceRequested && outputJar != null) {
            throw new CommandLineException("Choose exactly one output mode: --in-place or --output-jar <file.jar>.");
        }

        if (!inPlaceRequested && outputJar == null) {
            throw new CommandLineException("Choose an output mode: --in-place or --output-jar <file.jar>.");
        }

        for (var input : inputs) {
            if (!Files.exists(input)) {
                throw new CommandLineException("Input does not exist: " + input);
            }

            if (Files.isRegularFile(input) && !isSupportedInputFile(input)) {
                throw new CommandLineException(
                        "Unsupported input file: " + input + ". Expected .class or .jar."
                );
            }

            if (!Files.isRegularFile(input) && !Files.isDirectory(input)) {
                throw new CommandLineException("Input is not a regular file or directory: " + input);
            }
        }

        if (outputJar != null) {
            if (inputs.size() != 1) {
                throw new CommandLineException("--output-jar accepts exactly one input jar.");
            }

            var input = inputs.get(0);
            if (!Files.isRegularFile(input) || !isJar(input)) {
                throw new CommandLineException("--output-jar input must be one direct .jar file: " + input);
            }

            if (!isJar(outputJar)) {
                throw new CommandLineException("--output-jar must include the output file name and end with .jar: "
                        + outputJar);
            }

            if (Files.isDirectory(outputJar)) {
                throw new CommandLineException("--output-jar must be a file path, not a directory: " + outputJar);
            }

            if (input.equals(outputJar)) {
                throw new CommandLineException("--output-jar must be different from the input jar. Use --in-place instead.");
            }

            var parent = outputJar.getParent();
            if (parent != null && Files.exists(parent) && !Files.isDirectory(parent)) {
                throw new CommandLineException("--output-jar parent is not a directory: " + parent);
            }
        }
    }

    private static boolean isSupportedInputFile(Path input) {
        return isClass(input) || isJar(input);
    }

    private static boolean isClass(Path input) {
        return input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".class");
    }

    private static boolean isJar(Path input) {
        return input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }
}
