package com.excederus.shaderpatcher.staleness;

import com.excederus.shaderpatcher.resource.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HashingTest {

    @TempDir
    Path tempDir;

    @Test
    void sha256MatchesKnownTestVectors() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Hashing.sha256(new byte[0])
        );
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Hashing.sha256("abc".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void hashFileIsDeterministicAndContentSensitive() throws IOException {
        Path file = tempDir.resolve("pack.zip");
        Files.writeString(file, "hello");

        String first = Hashing.hashFile(file);
        String second = Hashing.hashFile(file);
        assertEquals(first, second);

        Files.writeString(file, "hello!");
        assertNotEquals(first, Hashing.hashFile(file));
    }

    @Test
    void hashFolderIsIndependentOfFilesystemIterationOrderButSensitiveToContent() throws IOException {
        Path folderA = tempDir.resolve("a");
        Files.createDirectories(folderA.resolve("shaders"));
        Files.writeString(folderA.resolve("shaders").resolve("block.properties"), "block.1=minecraft:stone");
        Files.writeString(folderA.resolve("shaders").resolve("item.properties"), "item.1=minecraft:stick");

        Path folderB = tempDir.resolve("b");
        Files.createDirectories(folderB.resolve("shaders"));
        // Write in the opposite order to prove the hash doesn't depend on write/iteration order.
        Files.writeString(folderB.resolve("shaders").resolve("item.properties"), "item.1=minecraft:stick");
        Files.writeString(folderB.resolve("shaders").resolve("block.properties"), "block.1=minecraft:stone");

        assertEquals(Hashing.hashFolder(folderA), Hashing.hashFolder(folderB));

        Files.writeString(folderB.resolve("shaders").resolve("block.properties"), "block.1=minecraft:dirt");
        assertNotEquals(Hashing.hashFolder(folderA), Hashing.hashFolder(folderB));
    }

    @Test
    void patchCollectionHashIsIndependentOfListAndMapOrdering() {
        Resource patchA = resource(Category.BLOCK, "cobblemon", Source.BUNDLED,
                entry("waving", "sapling", null, "cobblemon:a"),
                entry("waving", "bush", null, "cobblemon:b"));
        Resource patchB = resource(Category.BLOCK, "quark", Source.INTERNAL,
                entry("waving", "flower", "tall", "quark:c"));

        String first = Hashing.patchCollectionHash("2.0", List.of(patchA, patchB), List.of(), List.of());
        String second = Hashing.patchCollectionHash("2.0", List.of(patchB, patchA), List.of(), List.of());

        assertEquals(first, second);
    }

    @Test
    void patchCollectionHashChangesWhenContentOrVersionChanges() {
        Resource patch = resource(Category.BLOCK, "cobblemon", Source.BUNDLED, entry("waving", "sapling", null, "cobblemon:a"));

        String base = Hashing.patchCollectionHash("2.0", List.of(patch), List.of(), List.of());

        Resource changedPatch = resource(Category.BLOCK, "cobblemon", Source.BUNDLED, entry("waving", "sapling", null, "cobblemon:b"));
        assertNotEquals(base, Hashing.patchCollectionHash("2.0", List.of(changedPatch), List.of(), List.of()));

        assertNotEquals(base, Hashing.patchCollectionHash("2.1", List.of(patch), List.of(), List.of()));
    }

    private Resource resource(Category category, String modid, Source source, Map<MappingKey, List<String>> contents) {
        return new Resource(new ResourceId(category, modid), source, contents);
    }

    @SafeVarargs
    private Resource resource(Category category, String modid, Source source, Map<MappingKey, List<String>>... entries) {
        Map<MappingKey, List<String>> merged = new java.util.HashMap<>();
        for (var entry : entries)
            merged.putAll(entry);
        return new Resource(new ResourceId(category, modid), source, merged);
    }

    private Map<MappingKey, List<String>> entry(String type, String concept, String variant, String... values) {
        return Map.of(new MappingKey(type, concept, variant), List.of(values));
    }
}
