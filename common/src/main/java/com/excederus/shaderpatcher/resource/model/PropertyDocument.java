package com.excederus.shaderpatcher.resource.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record PropertyDocument(List<Line> lines) {

    public Optional<Line> find(String identifier) {
        return lines.stream().filter(Line::isEntry).filter(line -> line.identifier().equals(identifier)).findFirst();
    }

    public List<String> render() {
        List<String> output = new ArrayList<>();

        for (Line line : lines)
            output.addAll(line.rawLines());

        return output;
    }

    public static PropertyDocument parse(List<String> rawLines) {

        List<Line> lines = new ArrayList<>();

        List<String> currentLines = new ArrayList<>();
        String currentIdentifier = null;

        for (String line : rawLines) {
            if (currentIdentifier != null) {
                currentLines.add(line);

                // Preprocessor directives (#ifdef/#else/#elif/#endif) and stray comments never end a
                // continuation on their own - shaders like Bliss rely on a backslash-joined value
                // continuing straight through an #else/#endif, often without even repeating the
                // backslash on the directive line itself.
                if (isDirectiveOrComment(line))
                    continue;

                if (!line.trim().endsWith("\\")) {
                    lines.add(new Line(currentIdentifier, new ArrayList<>(currentLines)));
                    currentLines.clear();
                    currentIdentifier = null;
                }

                continue;
            }

            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                lines.add(new Line(null, new ArrayList<>(List.of(line))));
                continue;
            }

            int separatorIndex = line.indexOf("=");
            if (separatorIndex < 0) {
                lines.add(new Line(null, new ArrayList<>(List.of(line))));
                continue;
            }

            currentIdentifier = line.substring(0, separatorIndex).trim();
            currentLines.add(line);

            if (!trimmed.endsWith("\\")) {
                lines.add(new Line(currentIdentifier, new ArrayList<>(currentLines)));
                currentLines.clear();
                currentIdentifier = null;
            }
        }

        // A dangling continuation at EOF (missing its closing non-continued line) is still real content.
        if (currentIdentifier != null)
            lines.add(new Line(currentIdentifier, new ArrayList<>(currentLines)));

        return new PropertyDocument(lines);
    }

    private static boolean isDirectiveOrComment(String line) {
        return line.trim().startsWith("#");
    }
}
