package com.excederus.shaderpatcher.resource;

import com.excederus.shaderpatcher.HostEnvironment.ModSource;
import com.excederus.shaderpatcher.resource.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDiscoveryTest {

    private static final String OWN_MOD_ID = "shaderpatcher";

    @TempDir
    Path tempDir;

    @Test
    void discoversBundledInternalAndExternalPatchesAndFiltersToInstalledMods() throws IOException {

        Path ownJar = zip("shaderpatcher.jar", Map.of(
                "assets/shaderpatcher/shaderpatcher/patches/block.cobblemon.yaml", "waving:\n  sapling: cobblemon_sapling\n",
                "assets/shaderpatcher/shaderpatcher/patches/block.uninstalledmod.yaml", "waving:\n  sapling: ghost_sapling\n",
                "assets/shaderpatcher/shaderpatcher/patches/block._default.yaml", "waving:\n  sapling: nope\n",
                "assets/shaderpatcher/shaderpatcher/mappings/block._default.yaml", "waving:\n  sapling: oak_sapling\n"
        ));

        Path cobblemonJar = zip("cobblemon.jar", Map.of(
                "assets/cobblemon/shaderpatcher/patches/block.cobblemon.yaml", "waving:\n  bush: galarica_nut_bush\n"
        ));

        // A mod shipping a patch for a different modid than its own must be ignored.
        Path impostorJar = zip("impostor.jar", Map.of(
                "assets/impostor/shaderpatcher/patches/block.cobblemon.yaml", "waving:\n  bush: should_be_ignored\n"
        ));

        List<ModSource> mods = List.of(
                new ModSource(OWN_MOD_ID, "1.0", ownJar),
                new ModSource("cobblemon", "1.0", cobblemonJar),
                new ModSource("impostor", "1.0", impostorJar)
        );

        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir.resolve("patches"));
        Files.writeString(configDir.resolve("patches").resolve("block.externalmod.yaml"), "waving:\n  sapling: external_sapling\n");

        List<Resource> patches = ResourceParser.resolveOverrides(ResourceDiscovery.discover(Kind.PATCH, OWN_MOD_ID, mods, configDir));

        // uninstalledmod and _default must be dropped, impostor's mismatched file must be dropped,
        // externalmod isn't installed so it must be dropped too even though it's EXTERNAL.
        // The bundled cobblemon patch and cobblemon's own internal patch share a ResourceId, so override
        // resolution must collapse them down to just the higher-priority (INTERNAL) one.
        assertEquals(1, patches.size());
        Resource cobblemonPatch = patches.get(0);
        assertEquals(new ResourceId(Category.BLOCK, "cobblemon"), cobblemonPatch.id());
        assertEquals(Source.INTERNAL, cobblemonPatch.source());
        assertEquals(List.of("cobblemon:galarica_nut_bush"), cobblemonPatch.contents().get(new MappingKey("waving", "bush", null)));
    }

    @Test
    void internalPatchFromModsOwnJarIsDiscoveredWithCorrectSource() throws IOException {

        Path cobblemonJar = zip("cobblemon.jar", Map.of(
                "assets/cobblemon/shaderpatcher/patches/block.cobblemon.yaml", "waving:\n  bush: galarica_nut_bush\n"
        ));

        List<ModSource> mods = List.of(
                new ModSource(OWN_MOD_ID, "1.0", tempDir.resolve("does-not-exist.jar")),
                new ModSource("cobblemon", "1.0", cobblemonJar)
        );

        List<Resource> patches = ResourceDiscovery.discover(Kind.PATCH, OWN_MOD_ID, mods, tempDir.resolve("config"));

        assertEquals(1, patches.size());
        assertEquals(Source.INTERNAL, patches.get(0).source());
    }

    @Test
    void defaultMappingIsAllowedFromBundledAndExternalButNotInternal() throws IOException {

        Path ownJar = zip("shaderpatcher.jar", Map.of(
                "assets/shaderpatcher/shaderpatcher/mappings/block._default.yaml", "waving:\n  sapling: oak_sapling\n"
        ));

        Path cobblemonJar = zip("cobblemon.jar", Map.of(
                // A third-party mod may not define a _default mapping, only one for its own modid.
                "assets/cobblemon/shaderpatcher/mappings/block._default.yaml", "waving:\n  sapling: should_be_ignored\n"
        ));

        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir.resolve("mappings"));
        Files.writeString(configDir.resolve("mappings").resolve("block._default.yaml"), "waving:\n  sapling: overridden_sapling\n");

        List<ModSource> mods = List.of(
                new ModSource(OWN_MOD_ID, "1.0", ownJar),
                new ModSource("cobblemon", "1.0", cobblemonJar)
        );

        List<Resource> mappings = ResourceDiscovery.discover(Kind.MAPPING, OWN_MOD_ID, mods, configDir);

        assertEquals(2, mappings.size());
        assertTrue(mappings.stream().anyMatch(r -> r.source() == Source.BUNDLED));
        assertTrue(mappings.stream().anyMatch(r -> r.source() == Source.EXTERNAL));
        assertTrue(mappings.stream().noneMatch(r -> r.source() == Source.INTERNAL));
    }

    @Test
    void mappingsAndTransformsAreNotFilteredByInstalledMods() throws IOException {

        Path ownJar = zip("shaderpatcher.jar", Map.of(
                "assets/shaderpatcher/shaderpatcher/mappings/block.uninstalledmod.yaml", "waving:\n  sapling: whatever\n"
        ));

        List<ModSource> mods = List.of(new ModSource(OWN_MOD_ID, "1.0", ownJar));

        List<Resource> mappings = ResourceDiscovery.discover(Kind.MAPPING, OWN_MOD_ID, mods, tempDir.resolve("config"));

        assertEquals(1, mappings.size());
    }

    @Test
    void discoversInternalResourcesFromAFolderModSourceAsWellAsAZip() throws IOException {

        Path modFolder = tempDir.resolve("cobblemon-mod");
        Path patchesDir = modFolder.resolve("assets/cobblemon/shaderpatcher/patches");
        Files.createDirectories(patchesDir);
        Files.writeString(patchesDir.resolve("block.cobblemon.yaml"), "waving:\n  bush: galarica_nut_bush\n");

        List<ModSource> mods = List.of(
                new ModSource(OWN_MOD_ID, "1.0", tempDir.resolve("does-not-exist.jar")),
                new ModSource("cobblemon", "1.0", modFolder)
        );

        List<Resource> patches = ResourceDiscovery.discover(Kind.PATCH, OWN_MOD_ID, mods, tempDir.resolve("config"));

        assertEquals(1, patches.size());
        assertEquals(Source.INTERNAL, patches.get(0).source());
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path path = tempDir.resolve(name);

        try (OutputStream fileOut = Files.newOutputStream(path); ZipOutputStream zip = new ZipOutputStream(fileOut)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }

        return path;
    }
}
