package com.excederus.shaderpatcher.resource.parser;

import com.excederus.shaderpatcher.resource.model.*;
import com.excederus.shaderpatcher.util.Helpers;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.excederus.shaderpatcher.Constants.*;
import static com.excederus.shaderpatcher.util.Helpers.*;

public class GeneralResourceParser {

    private final RawResourceBundle rawResourceBundle;

    public GeneralResourceParser(RawResourceBundle rawResourceBundle) {
        this.rawResourceBundle = rawResourceBundle;
    }

    private static final Pattern RANGE_PATTERN = Pattern.compile("(.*=)(\\d+)~(\\d+)$");

    public List<Resource> parseResources(ResourceType type) {

        List<RawResource> rawResources = new ArrayList<>();

        switch (type) {
            case PATCH -> rawResources = rawResourceBundle.patches();
            case MAPPING -> rawResources = rawResourceBundle.mappings();
            case TRANSFORM -> rawResources = rawResourceBundle.transforms();
        }

        List<Resource> resources = new ArrayList<>();

        for (RawResource rawResource : rawResources) {
            Map<MappingKey, Content> contents = new HashMap<>(parseResourceContents(type, rawResource.modid(), rawResource.data()));

            resources.add(new Resource(
                    new ResourceKey(rawResource.category(), rawResource.modid()),
                    rawResource.source(),
                    contents
            ));
        }

        if (resources.isEmpty()) {
            LOG.error("Failed to parse {}", type);
            return resources;
        }

        resources = resolveOverrides(resources);

        return resources;
    }

    private Map<MappingKey, Content> parseResourceContents(ResourceType resourceType, String modid, byte[] data) {

        Map<MappingKey, Content> contents = new HashMap<>();

        if (data == null)
            return contents;

        Map<String, Object> yaml = parseYaml(data);

        if (yaml == null)
            return contents;

        boolean shouldNormalize = resourceType == ResourceType.PATCH || resourceType == ResourceType.MAPPING;
        String normalizationNamespace = shouldNormalize ? getNormalizationNamespace(modid, resourceType) : null;

        for (var typeEntry : yaml.entrySet()) {
            var type = typeEntry.getKey().toString();
            var conceptsObj = typeEntry.getValue();

            if (!(conceptsObj instanceof Map<?, ?> concepts)) {
                LOG.warn("Invalid concept structure for type {}", type);
                continue;
            }

            for (var conceptEntry : concepts.entrySet()) {
                var concept = conceptEntry.getKey().toString();
                var values = conceptEntry.getValue();

                if (values == null) {
                    LOG.warn("No values/variants for concept {}:{}", type, concept);
                    continue;
                }

                if (values instanceof String value) {
                    List<String> parsed = expandRanges(shouldNormalize ? normalizeValue(value, normalizationNamespace) : value);

                    contents.merge(
                            new MappingKey(type, concept, null),
                            new Content(parsed), Helpers::mergeContent
                    );
                }
                else if (values instanceof List<?> valuesList) {
                    List<String> parsed = valuesList.stream().map(Object::toString).map(v -> shouldNormalize ? normalizeValue(v, normalizationNamespace) : v).flatMap(v -> expandRanges(v).stream()).toList();

                    contents.merge(
                            new MappingKey(type, concept, null),
                            new Content(parsed), Helpers::mergeContent
                    );
                }
                else if (values instanceof Map<?, ?> variantMap) {
                    for (var variantEntry : variantMap.entrySet()) {
                        var variant = variantEntry.getKey().toString();
                        var variantValues = variantEntry.getValue();

                        if (variantValues == null) {
                            LOG.warn("No values for variant {}:{}:{}", type, concept, variant);
                            continue;
                        }

                        if (variantValues instanceof Map<?, ?>) {
                            LOG.warn("Invalid values structure for variant {}:{}:{}", type, concept, variant);
                            continue;
                        }

                        if (variantValues instanceof String value) {
                            List<String> parsed = expandRanges(shouldNormalize ? normalizeValue(value, normalizationNamespace) : value);

                            contents.merge(
                                    new MappingKey(type, concept, variant),
                                    new Content(parsed), Helpers::mergeContent
                            );
                        }
                        else if (variantValues instanceof List<?> valuesList) {
                            List<String> parsed = valuesList.stream().map(Object::toString).map(v -> shouldNormalize ? normalizeValue(v, normalizationNamespace) : v).flatMap(v -> expandRanges(v).stream()).toList();

                            contents.merge(
                                    new MappingKey(type, concept, variant),
                                    new Content(parsed), Helpers::mergeContent
                            );
                        }
                        else
                            LOG.warn("Invalid value type {}:{}:{}:{}", type, concept, variant, variantValues);
                    }
                }
                else
                    LOG.warn("Invalid value type {}:{}:{}", type, concept, values);
            }
        }

        return contents;
    }

    private Map<String, Object> parseYaml(byte[] data) {

        LoadSettings settings = LoadSettings.builder().build();
        Load load = new Load(settings);

        try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            Object obj = load.loadFromInputStream(stream);

            if (obj instanceof Map<?, ?> map) {
                Map<String, Object> parsed = new HashMap<>();

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    parsed.put(
                            entry.getKey().toString(),
                            entry.getValue()
                    );
                }

                return parsed;
            }

            LOG.warn("Invalid YAML structure");
            return null;
        } catch (IOException e) {
            LOG.warn("Failed to parse YAML", e);
        }

        return null;
    }

    private List<String> expandRanges(String value) {

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

    private String getNormalizationNamespace(String modid, ResourceType resourceType) {

        if (resourceType == ResourceType.PATCH)
            return modid;

        return null;
    }

    private List<Resource> resolveOverrides(List<Resource> resources) {

        Map<ResourceKey, Resource> resolved = new HashMap<>();

        for (Resource resource : resources) {
            Resource existing = resolved.get(resource.resourceKey());

            if (existing == null) {
                resolved.put(resource.resourceKey(), resource);
                continue;
            }

            int incomingPriority = getPriority(resource.source());
            int existingPriority = getPriority(existing.source());

            if (incomingPriority > existingPriority) {
                LOG.info("Overwriting {} {} with {} {}", existing.source(), existing.resourceKey(), resource.source(), resource.resourceKey());
                resolved.put(resource.resourceKey(), resource);
            }
            else if (incomingPriority == existingPriority)
                LOG.warn("Ignoring duplicate {} resource for {}", resource.source(), resource.resourceKey());
        }

        return resolved.values().stream().toList();
    }

    private int getPriority(ResourceSource source) {
        return switch (source) {
            case BUNDLED -> 0;
            case INTERNAL -> 1;
            case EXTERNAL -> 2;
        };
    }
}
