package org.bezsahara.simpleindy.gradle

import org.bezsahara.simpleindy.transform.Log

enum class SimpleIndyLogLevel {
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE;

    internal fun toTransformerLevel(): Log.Level = Log.Level.valueOf(name)
}
