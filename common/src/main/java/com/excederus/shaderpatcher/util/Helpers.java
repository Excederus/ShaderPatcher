package com.excederus.shaderpatcher.util;

import com.excederus.shaderpatcher.resource.model.Content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.excederus.shaderpatcher.Constants.LOG;

public class Helpers {

    public static String normalizeValue(String value, String modid) {

        String namespace = modid == null ? "minecraft:" : modid + ":";

        String[] parts = value.split(":");

        long identifierParts = Arrays.stream(parts).filter(part -> !part.contains("=")).count();

        if (identifierParts == 1)
            return namespace + value;

        return value;
    }

    public static Content mergeContent(Content existing, Content incoming) {

        List<String> merged = new ArrayList<>(existing.values());

        merged.addAll(incoming.values());

        return new Content(merged);
    }

    public static void recursiveDelete(Path workingDir) {

        try (Stream<Path> stream = Files.walk(workingDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    LOG.warn("Failed to delete {} in working directory: {}", path, workingDir, e);
                }
            });
        } catch (IOException e) {
            LOG.warn("Failed to walk working directory: {}", workingDir, e);
        }
    }
}
