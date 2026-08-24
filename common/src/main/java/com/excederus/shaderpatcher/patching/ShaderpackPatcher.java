package com.excederus.shaderpatcher.patching;

import com.excederus.shaderpatcher.resource.model.*;
import com.excederus.shaderpatcher.resource.model.ResourceBundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.excederus.shaderpatcher.Constants.*;
import static com.excederus.shaderpatcher.util.Helpers.*;

public class ShaderpackPatcher {

    public boolean patchShaderpacks(Path workingDir, ResourceBundle resourceBundle) {

        List<Path> workingPaths;

        try (Stream<Path> stream = Files.list(workingDir)) {
            workingPaths = stream.filter(Files::isDirectory).filter(path -> !path.equals(workingDir)).toList();
        } catch (IOException e) {
            LOG.error("Failed to list extracted shaderpacks in {}", workingDir, e);
            return false;
        }

        int shaderpacksPatched = 0;

        for (Shaderpack shaderpack : resourceBundle.shaderpacks()) {
            Path workingPath = null;

            for (Path path : workingPaths) {
                if (shaderpack.name().equals(path.getFileName().toString()))
                    workingPath = path;
            }

            if (workingPath == null) {
                LOG.warn("Failed to find extracted shaderpack for {}", shaderpack.name());
                continue;
            }

            int patchesApplied = 0;

            Path blockPath = workingPath.resolve("shaders").resolve("block.properties");
            Path itemPath = workingPath.resolve("shaders").resolve("item.properties");
            Path entityPath = workingPath.resolve("shaders").resolve("entity.properties");

            PropertyDocument blockFile = parseDocument(blockPath);
            PropertyDocument itemFile = parseDocument(itemPath);
            PropertyDocument entityFile = parseDocument(entityPath);

            for (Resource patch : resourceBundle.patches()) {
                boolean hasSupport = false;

                Pattern pattern = Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(patch.resourceKey().modid()) + ":[a-z0-9_]+" + "(?::[a-z0-9_]+=[a-z0-9_]+)*");

                if (blockFile != null)
                    hasSupport |= hasModSupport(blockFile, pattern);
                if (itemFile != null)
                    hasSupport |= hasModSupport(itemFile, pattern);
                if (entityFile != null)
                    hasSupport |= hasModSupport(entityFile, pattern);

                if (hasSupport) {
                    LOG.info("Skipping {}.{} patch for {} because shader already provides support", patch.resourceKey().category(), patch.resourceKey().modid(), shaderpack.name());
                    continue;
                }

                Resource mapping = getApplicableResource(patch.resourceKey(), resourceBundle.mappings());
                if (mapping == null) {
                    LOG.warn("No mapping found for {} {} patch", patch.source(), patch.resourceKey());
                    continue;
                }

                Resource transform = getApplicableResource(patch.resourceKey(), resourceBundle.transforms());
                if (transform == null && patch.resourceKey().category().equals(ResourceCategory.BLOCK)) {
                    LOG.warn("No transform found for {} {} patch", patch.source(), patch.resourceKey());
                    continue;
                } else if (transform != null) {
                    patch = applyTransform(patch, transform);
                    mapping = applyTransform(mapping, transform);
                }

                switch (patch.resourceKey().category()) {
                    case BLOCK -> patchFile(blockFile, shaderpack.block(), patch, mapping);
                    case ITEM -> patchFile(itemFile, shaderpack.item(), patch, mapping);
                    case ENTITY -> patchFile(entityFile, shaderpack.entity(), patch, mapping);
                }

                patchesApplied++;
            }

            if (patchesApplied == 0) {
                LOG.warn("No patches processed for {}", shaderpack.name());
                // Delete workingDir copy of shaderpack so it doesn't get imported as a patched version when nothing was patched
                recursiveDelete(workingPath);
                continue;
            }

            if (blockFile != null)
                writeDocument(blockPath, blockFile);
            if (itemFile != null)
                writeDocument(itemPath, itemFile);
            if (entityFile != null)
                writeDocument(entityPath, entityFile);

            LOG.info("Applied {} patches to {}", patchesApplied, shaderpack.name());
            shaderpacksPatched++;
        }

        if (shaderpacksPatched == 0) {
            LOG.error("Failed to patch any shader");
            return false;
        }

        LOG.info("Patched {} shaderpacks", shaderpacksPatched);
        return true;
    }

    private PropertyDocument parseDocument(Path path) {

        List<PropertyNode> nodes = new ArrayList<>();

        if (Files.notExists(path))
            return null;

        try {
            List<String> lines = Files.readAllLines(path);

            List<String> currentLines = new ArrayList<>();
            String currentIdentifier = null;

            for (String line : lines) {
                if (currentIdentifier != null) {
                    currentLines.add(line);

                    if (!line.trim().endsWith("\\")) {
                        nodes.add(new EntryNode(
                                currentIdentifier,
                                new ArrayList<>(currentLines)
                        ));

                        currentLines.clear();
                        currentIdentifier = null;
                    }

                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    nodes.add(new RawNode(line));
                    continue;
                }

                int separatorIndex = line.indexOf("=");
                if (separatorIndex < 0) {
                    nodes.add(new RawNode(line));
                    continue;
                }

                currentIdentifier = line.substring(0, separatorIndex).trim();
                currentLines.add(line);

                if (!trimmed.endsWith("\\")) {
                    nodes.add(new EntryNode(
                            currentIdentifier,
                            new ArrayList<>(currentLines)
                    ));

                    currentLines.clear();
                    currentIdentifier = null;
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to read lines in {}", path, e);
            return null;
        }

        return new PropertyDocument(nodes);
    }

    private boolean hasModSupport(PropertyDocument document, Pattern pattern) {

        for (PropertyNode node : document.nodes()) {
            if (!(node instanceof EntryNode entryNode))
                continue;

            for (String line : entryNode.lines()) {
                Matcher matcher = pattern.matcher(line);

                if (matcher.find())
                    return true;
            }
        }

        return false;
    }

    private Resource getApplicableResource(ResourceKey key, List<Resource> resources) {

        Resource defaultResource = null;

        for (Resource resource : resources) {
            if (resource.resourceKey().equals(key))
                return resource;
            if (resource.resourceKey().equals(new ResourceKey(key.category(), "_default")))
                defaultResource = resource;
        }

        return defaultResource;
    }

    private Resource applyTransform(Resource resource, Resource transform) {

        Map<MappingKey, Content> contents = new HashMap<>();

        for (var entry : resource.contents().entrySet()) {
            MappingKey key = entry.getKey();
            Content content = entry.getValue();

            Content transformContent = transform.contents().get(key);

            if (transformContent == null) {
                var fallbackKey = new MappingKey(
                        key.category(),
                        key.concept(),
                        null
                );

                transformContent = transform.contents().get(fallbackKey);
            }

            if (transformContent == null) {
                contents.put(key, content);
                continue;
            }

            List<String> transformedValues = new ArrayList<>();

            for (String value : content.values()) {
                for (String transformValue : transformContent.values()) {
                    transformedValues.add(value + transformValue);
                }
            }

            contents.put(key, new Content(transformedValues));
        }

        return new Resource(
                resource.resourceKey(),
                resource.source(),
                contents
        );
    }

    private void patchFile(PropertyDocument patchTarget, PropertyFile mappingHelper, Resource patch, Resource mapping) {

        if (patchTarget == null || mappingHelper == null)
            return;

        for (var mappingHelperEntry : mappingHelper.contents().entrySet()) {
            String mappingHelperIdentifier = mappingHelperEntry.getKey().identifier();
            List<String> mappingHelperValues = mappingHelperEntry.getValue().values();

            for (String mappingHelperValue : mappingHelperValues) {
                List<String> matchedPatchValues = new ArrayList<>();

                for (var patchEntry : patch.contents().entrySet()) {
                    MappingKey patchKey = patchEntry.getKey();
                    Content mappingContent = mapping.contents().get(patchKey);

                    if (mappingContent == null) {
                        var fallbackKey = new MappingKey(
                                patchKey.category(),
                                patchKey.concept(),
                                "_default"
                        );

                        mappingContent = mapping.contents().get(fallbackKey);
                    }

                    if (mappingContent == null)
                        continue;

                    List<String> patchValues = patchEntry.getValue().values();
                    List<String> mappingTargets = mappingContent.values();

                    for (String mappingTarget : mappingTargets) {
                        if (!mappingHelperValue.equals(mappingTarget))
                            continue;

                        if (mappingTargets.size() > 1) {
                            for (String patchValue : patchValues) {
                                if (getProperties(mappingTarget).equals(getProperties(patchValue)))
                                    matchedPatchValues.add(patchValue);
                            }
                        } else {
                            matchedPatchValues.addAll(patchValues);
                        }
                    }
                }

                if (matchedPatchValues.isEmpty())
                    continue;

                for (PropertyNode node : patchTarget.nodes()) {
                    if (!(node instanceof EntryNode entryNode))
                        continue;

                    if (!entryNode.identifier().equals(mappingHelperIdentifier))
                        continue;

                    appendPatch(entryNode, matchedPatchValues);
                }
            }
        }
    }

    private void writeDocument(Path path, PropertyDocument document) {

        List<String> output = new ArrayList<>();

        for (PropertyNode node : document.nodes()) {
            if (node instanceof RawNode rawNode)
                output.add(rawNode.line());
            else if (node instanceof EntryNode entryNode)
                output.addAll(entryNode.lines());
        }

        try {
            Files.write(path, output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to write properties file {}", path, e);
        }
    }

    private Set<String> getProperties(String value) {

        String[] parts = value.split(":");

        return new HashSet<>(Arrays.asList(parts).subList(2, parts.length));
    }

    private void appendPatch(EntryNode entryNode, List<String> values) {

        if (values.isEmpty())
            return;

        List<String> lines = entryNode.lines();

        int lastIndex = lines.size() - 1;
        String lastLine = lines.get(lastIndex);

        if (!lastLine.trim().endsWith("\\"))
            lines.set(lastIndex, lastLine + " \\");

        String joinedValues = String.join(" ", values);

        lines.add(" " + joinedValues);
    }
}
