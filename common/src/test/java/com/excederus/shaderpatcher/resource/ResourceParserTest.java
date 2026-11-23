package com.excederus.shaderpatcher.resource;

import com.excederus.shaderpatcher.resource.model.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceParserTest {

    @Test
    void parsesBundledPatchWithNormalizationAndVariants() throws IOException {
        Resource resource = parseFixture(
                "/assets/shaderpatcher/shaderpatcher/patches/block.cobblemon.yaml",
                new ResourceId(Category.BLOCK, "cobblemon"), Source.BUNDLED, Kind.PATCH
        );

        assertEquals(
                List.of("cobblemon:pep_up_flower", "cobblemon:pomohana"),
                resource.contents().get(new MappingKey("waving", "flower", "small"))
        );
        assertEquals(
                List.of("cobblemon:galarica_nut_bush"),
                resource.contents().get(new MappingKey("waving", "bush", null))
        );
        assertEquals(
                List.of("cobblemon:wistensu"),
                resource.contents().get(new MappingKey("waving", "hanging", "bottom"))
        );
    }

    @Test
    void parsesBundledMappingWithDefaultNamespaceAndVariants() throws IOException {
        Resource resource = parseFixture(
                "/assets/shaderpatcher/shaderpatcher/mappings/block._default.yaml",
                new ResourceId(Category.BLOCK, "_default"), Source.BUNDLED, Kind.MAPPING
        );

        assertEquals(
                List.of("minecraft:oak_leaves"),
                resource.contents().get(new MappingKey("waving", "leaves", null))
        );
        assertEquals(
                List.of("minecraft:grass"),
                resource.contents().get(new MappingKey("waving", "grass", "_default"))
        );
        assertEquals(
                List.of("minecraft:tall_grass"),
                resource.contents().get(new MappingKey("waving", "grass", "tall"))
        );
    }

    @Test
    void parsesBundledTransformWithoutNormalizationAndWithRangeExpansion() throws IOException {
        Resource resource = parseFixture(
                "/assets/shaderpatcher/shaderpatcher/transforms/block._default.yaml",
                new ResourceId(Category.BLOCK, "_default"), Source.BUNDLED, Kind.TRANSFORM
        );

        assertEquals(
                List.of("", ":half=lower", ":half=upper"),
                resource.contents().get(new MappingKey("waving", "flower", "tall"))
        );

        List<String> moisture = resource.contents().get(new MappingKey("SSS", "farmland", null));
        assertEquals(9, moisture.size());
        assertEquals("", moisture.get(0));
        assertEquals(":moisture=0", moisture.get(1));
        assertEquals(":moisture=7", moisture.get(8));
    }

    @Test
    void resolveOverridesPrefersHigherPrioritySource() {
        ResourceId id = new ResourceId(Category.BLOCK, "cobblemon");

        Resource bundled = new Resource(id, Source.BUNDLED, java.util.Map.of(new MappingKey("waving", "sapling", null), List.of("cobblemon:old")));
        Resource external = new Resource(id, Source.EXTERNAL, java.util.Map.of(new MappingKey("waving", "sapling", null), List.of("cobblemon:new")));

        List<Resource> resolved = ResourceParser.resolveOverrides(List.of(bundled, external));

        assertEquals(1, resolved.size());
        assertEquals(Source.EXTERNAL, resolved.get(0).source());
    }

    @Test
    void normalizeValueOnlyPrefixesBareIdentifiers() {
        assertEquals("minecraft:stone", ResourceParser.normalizeValue("stone", null));
        assertEquals("cobblemon:stone", ResourceParser.normalizeValue("stone", "cobblemon"));
        assertEquals("minecraft:stone:half=upper", ResourceParser.normalizeValue("minecraft:stone:half=upper", null));
    }

    private Resource parseFixture(String path, ResourceId id, Source source, Kind kind) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "missing fixture: " + path);
            return ResourceParser.parse(id, source, kind, stream.readAllBytes());
        }
    }
}
