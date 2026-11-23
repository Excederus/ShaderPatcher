package com.excederus.shaderpatcher.patching;

import com.excederus.shaderpatcher.resource.ResourceParser;
import com.excederus.shaderpatcher.resource.model.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PatchEngineTest {

    @Test
    void appliesSimplePatchViaDefaultMappingFallback() {
        Map<Category, PropertyDocument> properties = new HashMap<>();
        properties.put(Category.BLOCK, PropertyDocument.parse(List.of("waving.sapling=minecraft:oak_sapling")));

        Resource patch = resource(Category.BLOCK, "testmod", entry("waving", "sapling", null, "testmod:cool_sapling"));
        Resource mapping = resource(Category.BLOCK, "_default", entry("waving", "sapling", null, "minecraft:oak_sapling"));
        Resource transform = resource(Category.BLOCK, "_default", Map.of());

        int applied = PatchEngine.patch("test-shaderpack", properties, List.of(patch), List.of(mapping), List.of(transform));

        assertEquals(1, applied);
        assertEquals(
                List.of("minecraft:oak_sapling", "testmod:cool_sapling"),
                properties.get(Category.BLOCK).find("waving.sapling").get().values()
        );
    }

    @Test
    void skipsPatchWhenShaderAlreadyReferencesModAnywhere() {
        Map<Category, PropertyDocument> properties = new HashMap<>();
        properties.put(Category.BLOCK, PropertyDocument.parse(List.of("waving.sapling=minecraft:oak_sapling")));
        // The mod is already referenced in item.properties, a different category than the patch targets.
        properties.put(Category.ITEM, PropertyDocument.parse(List.of("item.1=testmod:cool_item")));

        Resource patch = resource(Category.BLOCK, "testmod", entry("waving", "sapling", null, "testmod:cool_sapling"));
        Resource mapping = resource(Category.BLOCK, "_default", entry("waving", "sapling", null, "minecraft:oak_sapling"));
        Resource transform = resource(Category.BLOCK, "_default", Map.of());

        int applied = PatchEngine.patch("test-shaderpack", properties, List.of(patch), List.of(mapping), List.of(transform));

        assertEquals(0, applied);
        assertEquals(List.of("minecraft:oak_sapling"), properties.get(Category.BLOCK).find("waving.sapling").get().values());
    }

    @Test
    void skipsPatchWhenNoApplicableMappingOrTransformExists() {
        Map<Category, PropertyDocument> properties = new HashMap<>();
        properties.put(Category.BLOCK, PropertyDocument.parse(List.of("waving.sapling=minecraft:oak_sapling")));

        Resource patch = resource(Category.BLOCK, "testmod", entry("waving", "sapling", null, "testmod:cool_sapling"));

        int applied = PatchEngine.patch("test-shaderpack", properties, List.of(patch), List.of(), List.of());

        assertEquals(0, applied);
        assertEquals(List.of("minecraft:oak_sapling"), properties.get(Category.BLOCK).find("waving.sapling").get().values());
    }

    @Test
    void realCobblemonPatchMatchesOnlyTheCorrectHalfVariant() throws IOException {
        Map<Category, PropertyDocument> properties = new HashMap<>();
        properties.put(Category.BLOCK, PropertyDocument.parse(List.of(
                "waving.tall.flower=minecraft:lilac:half=lower minecraft:lilac:half=upper"
        )));

        Resource patch = parseFixture("/assets/shaderpatcher/shaderpatcher/patches/block.cobblemon.yaml", new ResourceId(Category.BLOCK, "cobblemon"), Kind.PATCH);
        Resource mapping = parseFixture("/assets/shaderpatcher/shaderpatcher/mappings/block._default.yaml", new ResourceId(Category.BLOCK, "_default"), Kind.MAPPING);
        Resource transform = parseFixture("/assets/shaderpatcher/shaderpatcher/transforms/block._default.yaml", new ResourceId(Category.BLOCK, "_default"), Kind.TRANSFORM);

        int applied = PatchEngine.patch("test-shaderpack", properties, List.of(patch), List.of(mapping), List.of(transform));
        assertEquals(1, applied);

        List<String> values = properties.get(Category.BLOCK).find("waving.tall.flower").get().values();

        List<String> expectedLower = List.of(
                "cobblemon:lobaila:half=lower", "cobblemon:mistleaf:half=lower", "cobblemon:lucibell:half=lower",
                "cobblemon:hearty_grains:half=lower", "cobblemon:voltaconia:half=lower", "cobblemon:verdroot:half=lower"
        );
        List<String> expectedUpper = List.of(
                "cobblemon:lobaila:half=upper", "cobblemon:mistleaf:half=upper", "cobblemon:lucibell:half=upper",
                "cobblemon:hearty_grains:half=upper", "cobblemon:voltaconia:half=upper", "cobblemon:verdroot:half=upper"
        );

        assertEquals(2 + expectedLower.size() + expectedUpper.size(), values.size());
        assertTrue(values.containsAll(expectedLower));
        assertTrue(values.containsAll(expectedUpper));
        // half=lower values must appear before half=upper values, matching the order of the original half=lower/half=upper entries.
        assertTrue(values.indexOf(expectedLower.get(expectedLower.size() - 1)) < values.indexOf(expectedUpper.get(0)));
    }

    @Test
    void matchesExistingValuesWhoseBlockstatePropertiesAreInADifferentOrder() {
        Map<Category, PropertyDocument> properties = new HashMap<>();
        properties.put(Category.BLOCK, PropertyDocument.parse(List.of(
                // The shaderpack lists "lit" before "candles", the opposite of the order the transform below generates.
                "emissive.candle=minecraft:candle:lit=false:candles=1"
        )));

        Resource patch = resource(Category.BLOCK, "testmod", entry("emissive", "candle", null, "testmod:candle"));
        Resource mapping = resource(Category.BLOCK, "_default", entry("emissive", "candle", null, "minecraft:candle"));
        Resource transform = resource(Category.BLOCK, "_default", entry("emissive", "candle", null, ":candles=1:lit=false", ":candles=1:lit=true"));

        int applied = PatchEngine.patch("test-shaderpack", properties, List.of(patch), List.of(mapping), List.of(transform));

        assertEquals(1, applied);
        assertEquals(
                List.of("minecraft:candle:lit=false:candles=1", "testmod:candle:candles=1:lit=false"),
                properties.get(Category.BLOCK).find("emissive.candle").get().values()
        );
    }

    private Resource resource(Category category, String modid, Map<MappingKey, List<String>> contents) {
        return new Resource(new ResourceId(category, modid), Source.BUNDLED, contents);
    }

    private Map<MappingKey, List<String>> entry(String type, String concept, String variant, String... values) {
        return Map.of(new MappingKey(type, concept, variant), List.of(values));
    }

    private Resource parseFixture(String path, ResourceId id, Kind kind) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "missing fixture: " + path);
            return ResourceParser.parse(id, Source.BUNDLED, kind, stream.readAllBytes());
        }
    }
}
