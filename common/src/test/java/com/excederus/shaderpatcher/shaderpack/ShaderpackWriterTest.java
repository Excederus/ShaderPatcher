package com.excederus.shaderpatcher.shaderpack;

import com.excederus.shaderpatcher.resource.model.*;
import com.excederus.shaderpatcher.staleness.Fingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ShaderpackWriterTest {

    @TempDir
    Path tempDir;

    private static final String MOD_VERSION = "2.0";

    @Test
    void writesPatchedOutputNeverTouchesSourceAndSkipsOnSecondRunWhenUpToDate() throws IOException {

        Path source = zip("TestShader.zip", Map.of(
                "shaders/block.properties", "waving.sapling=minecraft:oak_sapling",
                "shaders/gbuffers_basic.fsh", "// not a properties file"
        ));
        byte[] sourceBytesBefore = Files.readAllBytes(source);
        FileTime sourceModifiedBefore = Files.getLastModifiedTime(source);

        Path outputDir = tempDir.resolve("out");
        List<Resource> patches = List.of(patchResource("testmod", "testmod:cool_sapling"));
        List<Resource> mappings = List.of(mappingResource());
        List<Resource> transforms = List.of(transformResource());

        ShaderpackWriter.Outcome first = ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION, patches, mappings, transforms);
        assertEquals(ShaderpackWriter.Outcome.REGENERATED, first);

        // The source shaderpack must never be modified.
        assertArrayEquals(sourceBytesBefore, Files.readAllBytes(source));
        assertEquals(sourceModifiedBefore, Files.getLastModifiedTime(source));

        Path output = outputDir.resolve("TestShader + shaderpatcher.zip");
        assertTrue(Files.exists(output));

        String blockProperties = readEntry(output, "shaders/block.properties");
        assertTrue(blockProperties.contains("minecraft:oak_sapling"));
        assertTrue(blockProperties.contains("testmod:cool_sapling"));

        // Non-properties entries must pass through byte-for-byte.
        assertEquals("// not a properties file", readEntry(output, "shaders/gbuffers_basic.fsh"));

        byte[] outputBytesAfterFirstRun = Files.readAllBytes(output);

        ShaderpackWriter.Outcome second = ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION, patches, mappings, transforms);
        assertEquals(ShaderpackWriter.Outcome.UP_TO_DATE, second);
        assertArrayEquals(outputBytesAfterFirstRun, Files.readAllBytes(output));
    }

    @Test
    void regeneratesWhenPatchCollectionChangesButBaseHashStaysTheSame() throws IOException {

        Path source = zip("TestShader.zip", Map.of("shaders/block.properties", "waving.sapling=minecraft:oak_sapling"));
        Path outputDir = tempDir.resolve("out");
        Path output = outputDir.resolve("TestShader + shaderpatcher.zip");

        ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION,
                List.of(patchResource("testmod", "testmod:cool_sapling")), List.of(mappingResource()), List.of(transformResource()));
        Fingerprint first = readFingerprint(output);

        ShaderpackWriter.Outcome outcome = ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION,
                List.of(patchResource("testmod", "testmod:even_cooler_sapling")), List.of(mappingResource()), List.of(transformResource()));
        Fingerprint second = readFingerprint(output);

        assertEquals(ShaderpackWriter.Outcome.REGENERATED, outcome);
        assertEquals(first.baseHash(), second.baseHash());
        assertNotEquals(first.patchesHash(), second.patchesHash());
        assertTrue(readEntry(output, "shaders/block.properties").contains("testmod:even_cooler_sapling"));
    }

    @Test
    void deletesStaleOutputWhenNoPatchesAreApplicableAnymore() throws IOException {

        Path source = zip("TestShader.zip", Map.of("shaders/block.properties", "waving.sapling=minecraft:oak_sapling"));
        Path outputDir = tempDir.resolve("out");
        Path output = outputDir.resolve("TestShader + shaderpatcher.zip");

        ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION,
                List.of(patchResource("testmod", "testmod:cool_sapling")), List.of(mappingResource()), List.of(transformResource()));
        assertTrue(Files.exists(output));

        ShaderpackWriter.Outcome outcome = ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION,
                List.of(), List.of(mappingResource()), List.of(transformResource()));

        assertEquals(ShaderpackWriter.Outcome.NO_APPLICABLE_PATCHES, outcome);
        assertFalse(Files.exists(output));
    }

    @Test
    void supportsAShaderRootNestedInsideTheZipAndAFolderFormatSource() throws IOException {

        Path nestedZip = zip("Nested.zip", Map.of("TestShader-1.0/shaders/block.properties", "waving.sapling=minecraft:oak_sapling"));
        Path outputDir = tempDir.resolve("out");

        ShaderpackWriter.Outcome zipOutcome = ShaderpackWriter.write(nestedZip, ShaderpackFormat.ZIP, outputDir, "Nested", MOD_VERSION,
                List.of(patchResource("testmod", "testmod:cool_sapling")), List.of(mappingResource()), List.of(transformResource()));
        assertEquals(ShaderpackWriter.Outcome.REGENERATED, zipOutcome);
        assertTrue(readEntry(outputDir.resolve("Nested + shaderpatcher.zip"), "TestShader-1.0/shaders/block.properties").contains("testmod:cool_sapling"));

        Path folderSource = tempDir.resolve("FolderShader");
        Files.createDirectories(folderSource.resolve("shaders"));
        Files.writeString(folderSource.resolve("shaders").resolve("block.properties"), "waving.sapling=minecraft:oak_sapling");

        ShaderpackWriter.Outcome folderOutcome = ShaderpackWriter.write(folderSource, ShaderpackFormat.FOLDER, outputDir, "FolderShader", MOD_VERSION,
                List.of(patchResource("testmod", "testmod:cool_sapling")), List.of(mappingResource()), List.of(transformResource()));
        assertEquals(ShaderpackWriter.Outcome.REGENERATED, folderOutcome);
        assertTrue(readEntry(outputDir.resolve("FolderShader + shaderpatcher.zip"), "shaders/block.properties").contains("testmod:cool_sapling"));
    }

    @Test
    void untouchedPropertiesFilesPassThroughByteForByteIncludingLineEndings() throws IOException {

        byte[] itemPropertiesWithCrlf = "item.1=minecraft:stick\r\nitem.2=minecraft:bow\r\n".getBytes(StandardCharsets.UTF_8);

        Path source = tempDir.resolve("TestShader.zip");
        try (OutputStream fileOut = Files.newOutputStream(source); ZipOutputStream zip = new ZipOutputStream(fileOut)) {
            zip.putNextEntry(new ZipEntry("shaders/block.properties"));
            zip.write("waving.sapling=minecraft:oak_sapling".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // No patch targets ITEM, so this file must come through completely untouched, CRLF and all.
            zip.putNextEntry(new ZipEntry("shaders/item.properties"));
            zip.write(itemPropertiesWithCrlf);
            zip.closeEntry();
        }

        Path outputDir = tempDir.resolve("out");
        ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION,
                List.of(patchResource("testmod", "testmod:cool_sapling")), List.of(mappingResource()), List.of(transformResource()));

        assertArrayEquals(itemPropertiesWithCrlf, readEntryBytes(outputDir.resolve("TestShader + shaderpatcher.zip"), "shaders/item.properties"));
    }

    @Test
    void reportsInUseAndLeavesTheExistingOutputAloneWhenItIsCurrentlyOpenElsewhere() throws IOException {

        Path source = zip("TestShader.zip", Map.of("shaders/block.properties", "waving.sapling=minecraft:oak_sapling"));
        Path outputDir = tempDir.resolve("out");
        Path output = outputDir.resolve("TestShader + shaderpatcher.zip");

        ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION,
                List.of(patchResource("testmod", "testmod:cool_sapling")), List.of(mappingResource()), List.of(transformResource()));
        byte[] outputBeforeLockedAttempt = Files.readAllBytes(output);

        // Simulate Iris (or anything else) having this shaderpack's patched output open, the way it would
        // if the shaderpack were currently selected, so a Windows delete/rename of the file fails.
        try (RandomAccessFile locked = new RandomAccessFile(output.toFile(), "rw")) {
            ShaderpackWriter.Outcome outcome = ShaderpackWriter.write(source, ShaderpackFormat.ZIP, outputDir, "TestShader", MOD_VERSION,
                    List.of(patchResource("testmod", "testmod:even_cooler_sapling")), List.of(mappingResource()), List.of(transformResource()));

            assertEquals(ShaderpackWriter.Outcome.IN_USE, outcome);
        }

        // The existing output must be left untouched, not partially overwritten or deleted, when it can't be replaced.
        assertArrayEquals(outputBeforeLockedAttempt, Files.readAllBytes(output));
    }

    private Resource patchResource(String modid, String value) {
        return new Resource(new ResourceId(Category.BLOCK, modid), Source.BUNDLED, Map.of(new MappingKey("waving", "sapling", null), List.of(value)));
    }

    private Resource mappingResource() {
        return new Resource(new ResourceId(Category.BLOCK, "_default"), Source.BUNDLED, Map.of(new MappingKey("waving", "sapling", null), List.of("minecraft:oak_sapling")));
    }

    private Resource transformResource() {
        return new Resource(new ResourceId(Category.BLOCK, "_default"), Source.BUNDLED, Map.of());
    }

    private Fingerprint readFingerprint(Path output) throws IOException {
        try (ZipFile zip = new ZipFile(output.toFile())) {
            return Fingerprint.decode(zip.getComment()).orElseThrow();
        }
    }

    private String readEntry(Path zipPath, String entryName) throws IOException {
        return new String(readEntryBytes(zipPath, entryName), StandardCharsets.UTF_8);
    }

    private byte[] readEntryBytes(Path zipPath, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            assertNotNull(entry, "missing entry: " + entryName);
            try (var stream = zip.getInputStream(entry)) {
                return stream.readAllBytes();
            }
        }
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
