package com.excederus.shaderpatcher.shaderpack;

import com.excederus.shaderpatcher.Log;
import com.excederus.shaderpatcher.resource.model.ShaderpackFormat;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds candidate shaderpacks (zips or folders containing a {@code shaders} properties file) in the {@code shaderpacks} folder. */
public final class ShaderpackScanner {

    private static final Logger LOG = Log.of(ShaderpackScanner.class);

    private ShaderpackScanner() {}

    public static List<Shaderpack> scan(Path shaderpacksDir) throws IOException {

        List<Shaderpack> shaderpacks = new ArrayList<>();

        if (!Files.isDirectory(shaderpacksDir))
            return shaderpacks;

        try (Stream<Path> entries = Files.list(shaderpacksDir)) {
            for (Path path : entries.sorted().toList()) {
                String filename = path.getFileName().toString();

                // Never treat our own patched output as a source shaderpack to patch again.
                if (ShaderpackLayout.isOutputFile(filename))
                    continue;

                if (Files.isRegularFile(path) && filename.endsWith(".zip")) {
                    if (hasProperties(path))
                        shaderpacks.add(new Shaderpack(filename.substring(0, filename.length() - ".zip".length()), path, ShaderpackFormat.ZIP));
                    else
                        LOG.info("Skipping {}: not a shaderpack (no block/item/entity.properties found under a 'shaders' folder)", filename);
                }
                else if (Files.isDirectory(path)) {
                    if (hasProperties(path))
                        shaderpacks.add(new Shaderpack(filename, path, ShaderpackFormat.FOLDER));
                    else
                        LOG.info("Skipping {}: not a shaderpack (no block/item/entity.properties found under a 'shaders' folder)", filename);
                }
            }
        }

        return shaderpacks;
    }

    private static boolean hasProperties(Path path) {
        try {
            return Files.isDirectory(path) ? folderHasProperties(path) : zipHasProperties(path);
        } catch (IOException e) {
            LOG.warn("Skipping {}: failed to inspect it as a shaderpack: {}", path.getFileName(), e.toString());
            return false;
        }
    }

    private static boolean zipHasProperties(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && ShaderpackLayout.categoryOfEntry(entry.getName()) != null)
                    return true;
            }
        }

        return false;
    }

    private static boolean folderHasProperties(Path path) throws IOException {
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Files::isRegularFile)
                    .anyMatch(file -> ShaderpackLayout.categoryOfEntry(path.relativize(file).toString().replace('\\', '/')) != null);
        }
    }
}
