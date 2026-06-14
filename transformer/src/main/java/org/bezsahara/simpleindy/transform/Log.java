package org.bezsahara.simpleindy.transform;

import java.util.Locale;

public final class Log {
    private final Level level;
    private final Sink sink;

    public Log(Level level) {
        this(level, Log::writeToConsole);
    }

    public Log(Level level, Sink sink) {
        this.level = level;
        this.sink = sink;
    }

    public boolean enabled(Level requested) {
        return requested.priority <= level.priority;
    }

    public void error(String message) {
        write(Level.ERROR, message);
    }

    public void warn(String message) {
        write(Level.WARN, message);
    }

    public void info(String message) {
        write(Level.INFO, message);
    }

    public void debug(String message) {
        write(Level.DEBUG, message);
    }

    public void trace(String message) {
        write(Level.TRACE, message);
    }

    private void write(Level requested, String message) {
        if (enabled(requested)) {
            sink.write(requested, "[" + requested.label + "] " + message);
        }
    }

    private static void writeToConsole(Level level, String message) {
        var stream = level == Level.ERROR || level == Level.WARN ? System.err : System.out;
        stream.println(message);
    }

    @FunctionalInterface
    public interface Sink {
        void write(Level level, String message);
    }

    public enum Level {
        ERROR(0, "error"),
        WARN(1, "warn"),
        INFO(2, "info"),
        DEBUG(3, "debug"),
        TRACE(4, "trace");

        private final int priority;
        private final String label;

        Level(int priority, String label) {
            this.priority = priority;
            this.label = label;
        }

        public static Level parse(String raw) {
            var normalized = raw.toLowerCase(Locale.ROOT);
            for (var level : values()) {
                if (level.label.equals(normalized)) {
                    return level;
                }
            }
            throw new IllegalArgumentException(
                    "Invalid log level '" + raw + "'. Expected error, warn, info, debug, or trace."
            );
        }
    }
}
