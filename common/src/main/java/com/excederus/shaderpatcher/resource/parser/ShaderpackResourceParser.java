package com.excederus.shaderpatcher.resource.parser;

import com.excederus.shaderpatcher.resource.model.*;
import com.excederus.shaderpatcher.util.Helpers;
import com.excederus.shaderpatcher.util.Helpers.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static com.excederus.shaderpatcher.Constants.*;
import static com.excederus.shaderpatcher.util.Helpers.*;

public class ShaderpackResourceParser {

    private final RawResourceBundle rawResourceBundle;

    public ShaderpackResourceParser(RawResourceBundle rawResourceBundle) {
        this.rawResourceBundle = rawResourceBundle;
    }

    public List<Shaderpack> parseShaderpacks() {

        List<Shaderpack> shaderpacks = new ArrayList<>();

        for (RawShaderpack rawShaderpack : rawResourceBundle.shaderpacks()) {
            String name = getBaseName(rawShaderpack.path());

            PropertyFile block = parsePropertyFile(rawShaderpack.blockData());
            PropertyFile item = parsePropertyFile(rawShaderpack.itemData());
            PropertyFile entity = parsePropertyFile(rawShaderpack.entityData());
            if (block == null && item == null && entity == null)
                continue;

            shaderpacks.add(new Shaderpack(
                    name,
                    rawShaderpack.path(),
                    rawShaderpack.type(),
                    block,
                    item,
                    entity
            ));
        }

        if (shaderpacks.isEmpty())
            LOG.error("Failed to parse shaderpacks");

        return shaderpacks;
    }

    private String getBaseName(Path path) {

        String filename = path.getFileName().toString();

        if (filename.endsWith(".zip"))
            return filename.substring(0, filename.lastIndexOf("."));

        return filename;
    }

    private PropertyFile parsePropertyFile(byte[] data) {

        Map<Identifier, Content> propertyFile = new HashMap<>();

        if (data == null)
            return null;

        String text = new String(data, StandardCharsets.UTF_8);

        List<String> lines = buildLogicalLines(text);
        if (lines.isEmpty())
            return null;

        for (String line : lines) {
            int separatorIndex = line.indexOf("=");

            if (separatorIndex < 0) {
                LOG.warn("Invalid property file line: {}", line);
                continue;
            }

            String identifier = line.substring(0, separatorIndex).trim();
            String valuePart = line.substring(separatorIndex + 1).trim();
            List<String> values;

            if (valuePart.isEmpty()) {
                values = List.of();
            }
            else {
                values = Arrays.stream(valuePart.split("\\s+")).map(value -> normalizeValue(value, null)).toList();
            }

            propertyFile.merge(
                    new Identifier(identifier),
                    new Content(values), Helpers::mergeContent
            );
        }

        return new PropertyFile(propertyFile);
    }

    private List<String> buildLogicalLines(String text) {

        List<String> lines = new ArrayList<>();
        StringBuilder builder = new StringBuilder();

        for (String line : text.split("\\R")) {
            line = line.trim();

            if (line.isEmpty())
                continue;

            if (line.startsWith("#"))
                continue;

            boolean continued = line.endsWith("\\");
            if (continued)
                line = line.substring(0, line.length() - 1).trim();

            builder.append(line);

            if (continued) {
                builder.append(" ");
            }
            else {
                lines.add(builder.toString());
                builder.setLength(0);
            }
        }

        if (builder.length() > 0)
            lines.add(builder.toString());

        return lines;
    }
}
