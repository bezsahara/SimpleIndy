package org.bezsahara.simpleindy.transform;

import java.util.List;

public final class TransformException extends Exception {
    public TransformException(String message) {
        super(message);
    }

    public static TransformException fromProblems(String title, List<String> problems) {
        var builder = new StringBuilder(title)
                .append(" (")
                .append(problems.size())
                .append(problems.size() == 1 ? " problem" : " problems")
                .append("):");
        for (var problem : problems) {
            builder.append(System.lineSeparator()).append("  - ").append(problem);
        }
        return new TransformException(builder.toString());
    }
}
