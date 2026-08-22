package com.excederus.shaderpatcher.resource.loader;

import com.excederus.shaderpatcher.resource.model.RawShaderpack;
import com.excederus.shaderpatcher.resource.model.ShaderpackType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.excederus.shaderpatcher.Constants.*;

public class ShaderpackResourceLoader {

    private final Path gameDir;

    public ShaderpackResourceLoader(Path gameDir) {
        this.gameDir = gameDir;
    }

    private static final List<String> PROPERTIES = List.of(
            "block.properties",
            "item.properties",
            "entity.properties"
    );

    public List<RawShaderpack> loadShaderpacks() {

        List<RawShaderpack> shaderpacks = new ArrayList<>();

        List<Path> shaderpackPaths = listShaderpacks(gameDir.resolve("shaderpacks"));

        if (shaderpackPaths.isEmpty()) return shaderpacks;

        for (Path shaderpackPath : shaderpackPaths) {
            if (isCandidate(shaderpackPath, shaderpackPaths)) {
                RawShaderpack shaderpack = null;

                if (isZip(shaderpackPath))
                    shaderpack = loadZip(shaderpackPath);
                else if (isFolder(shaderpackPath))
                    shaderpack = loadFolder(shaderpackPath);

                if (shaderpack == null)
                    continue;

                shaderpacks.add(shaderpack);
            }
        }

        if (shaderpacks.isEmpty())
            LOG.warn("No shaderpacks loaded");

        return shaderpacks;
    }

    private List<Path> listShaderpacks(Path gameDir) {

        List<Path> shaderpackPaths = new ArrayList<>();

        try (Stream<Path> files = Files.list(gameDir)) {
            files.forEach(shaderpackPaths::add);
        } catch (IOException e) {
            LOG.error("Failed to list shaderpacks");
            return shaderpackPaths;
        }

        return shaderpackPaths;
    }

    private RawShaderpack loadZip(Path path) {

        byte[] block = null;
        byte[] item = null;
        byte[] entity = null;

        try (ZipFile zip = new ZipFile(path.toFile())) {
            String root = findShaderRoot(zip);

            if (root == null) {
                LOG.warn("Skipped loading {} because failed to find root", path.getFileName());
                return null;
            }

            for (String file : PROPERTIES) {
                ZipEntry entry = zip.getEntry(root + file);

                if (entry == null) {
                    LOG.warn("Skipped {} for {} because it does not exist", file, zip.getName());
                    continue;
                }

                try (InputStream stream = zip.getInputStream(entry)) {
                    byte[] data = stream.readAllBytes();

                    switch (file) {
                        case "block.properties": block = data;
                        case "item.properties": item = data;
                        case "entity.properties": entity = data;
                    }
                } catch (IOException e) {
                    LOG.warn("Failed to stream contents of {} for {}", file, path.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to load zip: {}", path, e);
            return null;
        }

        if (block == null && item == null && entity == null) {
            LOG.warn("Skipped loading {} because failed to read any file", path.getFileName());
            return null;
        }

        return new RawShaderpack(
                path,
                ShaderpackType.ZIP,
                block,
                item,
                entity
        );
    }

    private RawShaderpack loadFolder(Path path) {

        byte[] block = null;
        byte[] item = null;
        byte[] entity = null;

        Path shaders = path.resolve("shaders");

        if (Files.notExists(shaders)) {
            LOG.warn("Skipped loading {} because failed to find root", path.getFileName());
            return null;
        }

        for (String file : PROPERTIES) {
            try {
                byte[] data = Files.readAllBytes(shaders.resolve(file));

                switch (file) {
                    case "block.properties": block = data;
                    case "item.properties": item = data;
                    case "entity.properties": entity = data;
                }
            } catch (IOException e) {
                LOG.warn("Failed to read contents of {} for {}", file, path.getFileName(), e);
            }
        }

        if (block == null && item == null && entity == null) {
            LOG.warn("Skipped loading {} because failed to read any file", path.getFileName());
            return null;
        }

        return new RawShaderpack(
                path,
                ShaderpackType.FOLDER,
                block,
                item,
                entity
        );
    }

    private boolean isCandidate(Path path, List<Path> comparisonPaths) {

        if (!isZip(path) && !isFolder(path)) {
            LOG.info("Skipped loading {} because not a shaderpack", path.getFileName());
            return false;
        }

        String filename = getBaseName(path);

        if (filename.contains(" + " + MODID + "-")) {
            LOG.info("Skipped loading {} because already patched", path.getFileName());
            return false;
        }

        for (Path comparisonPath : comparisonPaths) {

            if (comparisonPath.getFileName().toString().equals(path.getFileName().toString()))
                continue;

            if (!isZip(comparisonPath) && !isFolder(comparisonPath))
                continue;

            String comparisonFilename = getBaseName(comparisonPath);

            if (comparisonFilename.equals(filename + " + " + MODTAG)) {
                LOG.info("Skipped loading {} because up-to-date patched version exists ({})", filename, comparisonFilename);
                return false;
            }
        }

        return true;
    }

    private boolean isZip(Path path) {

        if (path.getFileName().toString().endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                String root = findShaderRoot(zip);

                if (root == null) {
                    LOG.warn("Failed to find root for {}", path.getFileName());
                    return false;
                }

                for (String file : PROPERTIES) {
                    ZipEntry entry = zip.getEntry(root + file);

                    if (entry != null)
                        return true;
                }
            } catch (IOException e) {
                LOG.warn("Failed to check if {} is a zip", path.getFileName());
            }
        }

        return false;
    }

    private boolean isFolder(Path path) {

        Path shaders = path.resolve("shaders");

        if (Files.notExists(shaders) || !Files.isDirectory(shaders))
            return false;

        for (String file : PROPERTIES) {
            if (Files.exists(shaders.resolve(file)))
                return true;
        }

        return false;
    }

    private String getBaseName(Path path) {

        String name = path.getFileName().toString();

        if (name.endsWith(".zip"))
            return name.substring(0, name.lastIndexOf(".zip"));

        return name;
    }

    private String findShaderRoot(ZipFile zip) {

        Enumeration<? extends ZipEntry> entries = zip.entries();

        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("shaders/"))
                return "shaders/";

            int index = name.indexOf("/shaders/");
            if (index >= 0)
                return name.substring(0, index + "/shaders/".length());
        }

        return null;
    }
}
