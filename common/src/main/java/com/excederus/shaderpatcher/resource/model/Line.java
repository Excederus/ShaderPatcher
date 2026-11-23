package com.excederus.shaderpatcher.resource.model;

import com.excederus.shaderpatcher.resource.ResourceParser;

import java.util.Arrays;
import java.util.List;

/**
 * One logical line of a shaderpack properties file.
 * {@code identifier == null} marks a passthrough line (comment, blank, or malformed line)
 * that is kept verbatim; otherwise {@code rawLines} holds the {@code identifier=value} line
 * plus any {@code \}-continued follow-up lines.
 */
public record Line(String identifier, List<String> rawLines) {

    public boolean isEntry() {
        return identifier != null;
    }

    /** The whitespace-separated, namespace-normalized value tokens currently assigned to this identifier. */
    public List<String> values() {
        if (!isEntry())
            return List.of();

        StringBuilder joined = new StringBuilder();
        for (String raw : rawLines) {
            String trimmed = raw.trim();
            if (trimmed.endsWith("\\"))
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            joined.append(trimmed).append(' ');
        }

        String text = joined.toString().trim();
        int separatorIndex = text.indexOf('=');
        String valuePart = separatorIndex >= 0 ? text.substring(separatorIndex + 1).trim() : "";

        if (valuePart.isEmpty())
            return List.of();

        return Arrays.stream(valuePart.split("\\s+")).map(value -> ResourceParser.normalizeValue(value, null)).toList();
    }

    public void appendValues(List<String> values) {
        if (values.isEmpty())
            return;

        int lastIndex = rawLines.size() - 1;
        String lastLine = rawLines.get(lastIndex);

        if (!lastLine.trim().endsWith("\\"))
            rawLines.set(lastIndex, lastLine + " \\");

        rawLines.add(" " + String.join(" ", values));
    }
}
