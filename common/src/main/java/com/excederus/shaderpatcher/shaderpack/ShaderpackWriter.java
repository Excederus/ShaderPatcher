package com.excederus.shaderpatcher.shaderpack;

import com.excederus.shaderpatcher.Log;
import com.excederus.shaderpatcher.patching.PatchEngine;
import com.excederus.shaderpatcher.resource.model.*;
import com.excederus.shaderpatcher.staleness.Fingerprint;
import com.excederus.shaderpatcher.staleness.Hashing;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Patches a shaderpack's properties files and writes the result to a new zip, without ever
 * modifying the source shaderpack. Only regenerates when the source or the resolved patch
 * collection actually changed, tracked via two hashes stored in the output zip's comment.
 */
public final class ShaderpackWriter {

    private static final Logger LOG = Log.of(ShaderpackWriter.class);

    private ShaderpackWriter() {}

    public enum Outcome {
        REGENERATED,
        UP_TO_DATE,
        NO_APPLICABLE_PATCHES,
        IN_USE
    }

    public static Outcome write(Path source, ShaderpackFormat format, Path outputDir, String name, String modVersion,
                                 List<Resource> patches, List<Resource> mappings, List<Resource> transforms) throws IOException {

        Files.createDirectories(outputDir);

        Path outputPath = outputDir.resolve(ShaderpackLayout.outputFileName(name));
        boolean outputExisted = Files.exists(outputPath);

        String baseHash = format == ShaderpackFormat.ZIP ? Hashing.hashFile(source) : Hashing.hashFolder(source);
        String patchesHash = Hashing.patchCollectionHash(modVersion, patches, mappings, transforms);

        if (outputExisted && !Fingerprint.shouldRegenerate(readComment(outputPath), baseHash, patchesHash)) {
            LOG.info("{} is already up to date, skipping", name);
            return Outcome.UP_TO_DATE;
        }

        // Delete the stale output as early as possible, before doing any of the (re)patching work below.
        // If something else (e.g. Iris, with this shaderpack currently selected) has the file open, we
        // want to find out now and skip cheaply, rather than discover it only after redoing all the work.
        if (outputExisted && !tryDelete(outputPath, name))
            return Outcome.IN_USE;

        Map<Category, byte[]> existingProperties = readExistingProperties(source, format);

        Map<Category, PropertyDocument> properties = new EnumMap<>(Category.class);
        Map<Category, List<String>> renderedBeforePatching = new EnumMap<>(Category.class);
        for (var entry : existingProperties.entrySet()) {
            PropertyDocument document = PropertyDocument.parse(linesOf(entry.getValue()));
            properties.put(entry.getKey(), document);
            renderedBeforePatching.put(entry.getKey(), document.render());
        }

        int patchesApplied = PatchEngine.patch(name, properties, patches, mappings, transforms);

        if (patchesApplied == 0) {
            if (outputExisted)
                LOG.info("Deleted stale patched output for {} since no patches are applicable anymore", name);

            return Outcome.NO_APPLICABLE_PATCHES;
        }

        // Only categories whose content actually changed should be rewritten; everything else (including
        // properties files that exist but weren't touched by any patch) is copied through byte-for-byte.
        Map<Category, PropertyDocument> changedProperties = new EnumMap<>(Category.class);
        for (var entry : properties.entrySet())
            if (!entry.getValue().render().equals(renderedBeforePatching.get(entry.getKey())))
                changedProperties.put(entry.getKey(), entry.getValue());

        Fingerprint fingerprint = new Fingerprint(baseHash, patchesHash);
        Path partPath = outputDir.resolve(outputPath.getFileName() + ".part");

        try {
            writeOutput(source, format, changedProperties, fingerprint, partPath);
            Files.move(partPath, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileSystemException e) {
            LOG.warn("Could not write patched shaderpack for {}: the output is currently in use (e.g. selected in Iris) - deselect it or restart, then try again", name);
            return Outcome.IN_USE;
        } finally {
            Files.deleteIfExists(partPath);
        }

        LOG.info("{} patched with {} patch(es), wrote {}", name, patchesApplied, outputPath.getFileName());

        return Outcome.REGENERATED;
    }

    private static boolean tryDelete(Path path, String name) {
        try {
            Files.delete(path);
            return true;
        } catch (FileSystemException e) {
            LOG.warn("Could not regenerate {}: the existing patched output is currently in use (e.g. selected in Iris) - deselect it or restart, then try again", name);
            return false;
        } catch (IOException e) {
            LOG.warn("Failed to delete stale patched output for {}: {}", name, e.toString());
            return false;
        }
    }

    private static String readComment(Path zipPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            return zip.getComment();
        }
    }

    private static Map<Category, byte[]> readExistingProperties(Path source, ShaderpackFormat format) throws IOException {

        Map<Category, byte[]> result = new EnumMap<>(Category.class);

        if (format == ShaderpackFormat.ZIP) {
            try (ZipFile zip = new ZipFile(source.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory())
                        continue;

                    Category category = ShaderpackLayout.categoryOfEntry(entry.getName());
                    if (category == null)
                        continue;

                    try (InputStream stream = zip.getInputStream(entry)) {
                        result.put(category, stream.readAllBytes());
                    }
                }
            }
        }
        else {
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Category category = ShaderpackLayout.categoryOfEntry(relativize(source, file));
                    if (category == null)
                        continue;

                    result.put(category, Files.readAllBytes(file));
                }
            }
        }

        return result;
    }

    private static void writeOutput(Path source, ShaderpackFormat format, Map<Category, PropertyDocument> changedProperties,
                                      Fingerprint fingerprint, Path partPath) throws IOException {

        try (OutputStream fileOut = Files.newOutputStream(partPath); ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {

            if (format == ShaderpackFormat.ZIP) {
                try (ZipFile sourceZip = new ZipFile(source.toFile())) {
                    Enumeration<? extends ZipEntry> entries = sourceZip.entries();

                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();

                        if (entry.isDirectory()) {
                            zipOut.putNextEntry(new ZipEntry(entry.getName()));
                            zipOut.closeEntry();
                            continue;
                        }

                        byte[] data = readEntry(sourceZip, entry, changedProperties, ShaderpackLayout.categoryOfEntry(entry.getName()));

                        zipOut.putNextEntry(new ZipEntry(entry.getName()));
                        zipOut.write(data);
                        zipOut.closeEntry();
                    }
                }
            }
            else {
                List<Path> files;
                try (Stream<Path> stream = Files.walk(source)) {
                    files = stream.filter(Files::isRegularFile).sorted().toList();
                }

                for (Path file : files) {
                    String relative = relativize(source, file);
                    Category category = ShaderpackLayout.categoryOfEntry(relative);

                    byte[] data = category != null && changedProperties.containsKey(category)
                            ? render(changedProperties.get(category))
                            : Files.readAllBytes(file);

                    zipOut.putNextEntry(new ZipEntry(relative));
                    zipOut.write(data);
                    zipOut.closeEntry();
                }
            }

            zipOut.setComment(fingerprint.encode());
        }
    }

    private static byte[] readEntry(ZipFile sourceZip, ZipEntry entry, Map<Category, PropertyDocument> changedProperties, Category category) throws IOException {

        if (category != null && changedProperties.containsKey(category))
            return render(changedProperties.get(category));

        try (InputStream stream = sourceZip.getInputStream(entry)) {
            return stream.readAllBytes();
        }
    }

    private static String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static byte[] render(PropertyDocument document) {
        return String.join("\n", document.render()).getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> linesOf(byte[] data) throws IOException {

        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null)
                lines.add(line);
        }

        return lines;
    }
}
