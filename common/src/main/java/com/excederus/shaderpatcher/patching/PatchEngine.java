package com.excederus.shaderpatcher.patching;

import com.excederus.shaderpatcher.Log;
import com.excederus.shaderpatcher.resource.model.*;
import org.slf4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies resolved patch resources to an in-memory shaderpack's properties documents.
 * Pure in-memory logic: no filesystem access, no zip handling.
 */
public final class PatchEngine {

    private static final Logger LOG = Log.of(PatchEngine.class);

    private PatchEngine() {}

    /** @return how many patches were applied. Mutates the given {@code properties} documents in place. */
    public static int patch(String shaderpackName, Map<Category, PropertyDocument> properties, List<Resource> patches, List<Resource> mappings, List<Resource> transforms) {

        int patchesApplied = 0;
        List<String> skippedBuiltIn = new ArrayList<>();

        for (Resource patch : patches) {
            String label = label(patch.id());

            PropertyDocument target = properties.get(patch.id().category());
            if (target == null)
                continue;

            if (hasModSupport(properties.values(), patch.id().modid())) {
                skippedBuiltIn.add(label);
                continue;
            }

            Resource mapping = getApplicableResource(patch.id(), mappings);
            if (mapping == null) {
                LOG.warn("No mapping found for {} patch on {}: this patch will not be applied", label, shaderpackName);
                continue;
            }

            Resource transform = getApplicableResource(patch.id(), transforms);
            if (transform == null) {
                LOG.warn("No transform found for {} patch on {}: this patch will not be applied", label, shaderpackName);
                continue;
            }

            Resource transformedPatch = applyTransform(patch, transform);
            Resource transformedMapping = applyTransform(mapping, transform);

            patchFile(target, transformedPatch, transformedMapping);
            patchesApplied++;
        }

        if (!skippedBuiltIn.isEmpty())
            LOG.info("Skipped {} patch(es) for {} because the shader already provides support: {}", skippedBuiltIn.size(), shaderpackName, String.join(", ", skippedBuiltIn));

        return patchesApplied;
    }

    private static String label(ResourceId id) {
        return id.category().name().toLowerCase() + "." + id.modid();
    }

    private static boolean hasModSupport(Collection<PropertyDocument> documents, String modid) {

        Pattern pattern = Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(modid) + ":[a-z0-9_]+(?::[a-z0-9_]+=[a-z0-9_]+)*");

        for (PropertyDocument document : documents) {
            for (Line line : document.lines()) {
                if (!line.isEntry())
                    continue;

                for (String rawLine : line.rawLines()) {
                    Matcher matcher = pattern.matcher(rawLine);
                    if (matcher.find())
                        return true;
                }
            }
        }

        return false;
    }

    private static Resource getApplicableResource(ResourceId id, List<Resource> resources) {

        Resource defaultResource = null;

        for (Resource resource : resources) {
            if (resource.id().equals(id))
                return resource;
            if (resource.id().equals(new ResourceId(id.category(), "_default")))
                defaultResource = resource;
        }

        return defaultResource;
    }

    private static Resource applyTransform(Resource resource, Resource transform) {

        Map<MappingKey, List<String>> contents = new HashMap<>();

        for (var entry : resource.contents().entrySet()) {
            MappingKey key = entry.getKey();
            List<String> values = entry.getValue();

            List<String> transformValues = transform.contents().get(key);
            if (transformValues == null)
                transformValues = transform.contents().get(new MappingKey(key.type(), key.concept(), null));

            if (transformValues == null) {
                contents.put(key, values);
                continue;
            }

            List<String> transformed = new ArrayList<>();
            for (String value : values)
                for (String suffix : transformValues)
                    transformed.add(value + suffix);

            contents.put(key, transformed);
        }

        return new Resource(resource.id(), resource.source(), contents);
    }

    private static void patchFile(PropertyDocument target, Resource patch, Resource mapping) {

        for (Line line : target.lines()) {
            if (!line.isEntry())
                continue;

            List<String> matchedPatchValues = new ArrayList<>();

            for (String existingValue : line.values()) {
                for (var patchEntry : patch.contents().entrySet()) {
                    MappingKey patchKey = patchEntry.getKey();

                    List<String> mappingTargets = mapping.contents().get(patchKey);
                    if (mappingTargets == null)
                        mappingTargets = mapping.contents().get(new MappingKey(patchKey.type(), patchKey.concept(), "_default"));
                    if (mappingTargets == null)
                        continue;

                    List<String> patchValues = patchEntry.getValue();

                    for (String mappingTarget : mappingTargets) {
                        if (!sameValue(existingValue, mappingTarget))
                            continue;

                        if (mappingTargets.size() > 1) {
                            for (String patchValue : patchValues)
                                if (propertiesOf(mappingTarget).equals(propertiesOf(patchValue)))
                                    matchedPatchValues.add(patchValue);
                        }
                        else {
                            matchedPatchValues.addAll(patchValues);
                        }
                    }
                }
            }

            if (!matchedPatchValues.isEmpty())
                line.appendValues(matchedPatchValues);
        }
    }

    private static Set<String> propertiesOf(String value) {
        String[] parts = value.split(":");
        return new HashSet<>(Arrays.asList(parts).subList(Math.min(2, parts.length), parts.length));
    }

    /**
     * Same namespace:id and the same set of blockstate properties, ignoring the order those properties
     * are written in. A shaderpack is free to list e.g. {@code candle:candles=1:lit=false} as
     * {@code candle:lit=false:candles=1} - only the identifier and the property set need to match, not
     * the exact order a transform happened to generate them in.
     */
    private static boolean sameValue(String a, String b) {

        String[] partsA = a.split(":");
        String[] partsB = b.split(":");

        if (partsA.length < 2 || partsB.length < 2)
            return a.equals(b);

        return partsA[0].equals(partsB[0]) && partsA[1].equals(partsB[1]) && propertiesOf(a).equals(propertiesOf(b));
    }
}
