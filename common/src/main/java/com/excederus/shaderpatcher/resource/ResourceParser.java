package com.excederus.shaderpatcher.resource;

import com.excederus.shaderpatcher.resource.model.*;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a patch/mapping/transform YAML resource directly into a {@link Resource}, and resolves overrides. */
public final class ResourceParser {

    private ResourceParser() {}

    private static final Pattern RANGE_PATTERN = Pattern.compile("(.*=)(\\d+)~(\\d+)$");

    public static Resource parse(ResourceId id, Source source, Kind kind, byte[] data) {

        Map<String, Object> yaml = parseYaml(data);

        Map<MappingKey, List<String>> contents = new HashMap<>();
        if (yaml != null)
            collectTypes(yaml, kind, normalizationNamespace(id, kind), contents);

        return new Resource(id, source, contents);
    }

    public static List<Resource> resolveOverrides(List<Resource> resources) {

        Map<ResourceId, Resource> resolved = new LinkedHashMap<>();

        for (Resource resource : resources) {
            Resource existing = resolved.get(resource.id());

            if (existing == null) {
                resolved.put(resource.id(), resource);
                continue;
            }

            if (resource.source().ordinal() > existing.source().ordinal())
                resolved.put(resource.id(), resource);
        }

        return List.copyOf(resolved.values());
    }

    private static String normalizationNamespace(ResourceId id, Kind kind) {
        return kind == Kind.PATCH ? id.modid() : null;
    }

    private static boolean shouldNormalize(Kind kind) {
        return kind == Kind.PATCH || kind == Kind.MAPPING;
    }

    private static void collectTypes(Map<String, Object> yaml, Kind kind, String namespace, Map<MappingKey, List<String>> out) {

        for (var typeEntry : yaml.entrySet()) {
            String type = typeEntry.getKey();
            Object conceptsObj = typeEntry.getValue();

            if (!(conceptsObj instanceof Map<?, ?> concepts))
                continue;

            for (var conceptEntry : concepts.entrySet()) {
                String concept = conceptEntry.getKey().toString();
                Object values = conceptEntry.getValue();

                if (values == null)
                    continue;

                if (values instanceof Map<?, ?> variantMap) {
                    for (var variantEntry : variantMap.entrySet()) {
                        String variant = variantEntry.getKey().toString();
                        Object variantValues = variantEntry.getValue();

                        if (variantValues == null || variantValues instanceof Map<?, ?>)
                            continue;

                        merge(out, new MappingKey(type, concept, variant), toValueList(variantValues, kind, namespace));
                    }
                }
                else {
                    merge(out, new MappingKey(type, concept, null), toValueList(values, kind, namespace));
                }
            }
        }
    }

    private static List<String> toValueList(Object values, Kind kind, String namespace) {

        List<String> raw = values instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of(values.toString());

        boolean normalize = shouldNormalize(kind);

        List<String> result = new ArrayList<>();
        for (String value : raw) {
            String normalized = normalize ? normalizeValue(value, namespace) : value;
            result.addAll(expandRanges(normalized));
        }

        return result;
    }

    private static void merge(Map<MappingKey, List<String>> out, MappingKey key, List<String> values) {
        out.merge(key, values, (existing, incoming) -> {
            List<String> merged = new ArrayList<>(existing);
            merged.addAll(incoming);
            return merged;
        });
    }

    private static List<String> expandRanges(String value) {

        Matcher matcher = RANGE_PATTERN.matcher(value);

        if (!matcher.matches())
            return List.of(value);

        String prefix = matcher.group(1);
        int start = Integer.parseInt(matcher.group(2));
        int end = Integer.parseInt(matcher.group(3));

        List<String> expanded = new ArrayList<>();
        for (int i = start; i <= end; i++)
            expanded.add(prefix + i);

        return expanded;
    }

    public static String normalizeValue(String value, String modid) {

        String namespace = modid == null ? "minecraft:" : modid + ":";

        String[] parts = value.split(":");
        long identifierParts = Arrays.stream(parts).filter(part -> !part.contains("=")).count();

        if (identifierParts == 1)
            return namespace + value;

        return value;
    }

    private static Map<String, Object> parseYaml(byte[] data) {

        Object obj = new Yaml().load(new ByteArrayInputStream(data));

        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> parsed = new HashMap<>();
            for (var entry : map.entrySet())
                parsed.put(entry.getKey().toString(), entry.getValue());
            return parsed;
        }

        return null;
    }
}
