package com.excederus.shaderpatcher.resource;

import com.excederus.shaderpatcher.HostEnvironment.ModSource;
import com.excederus.shaderpatcher.Log;
import com.excederus.shaderpatcher.resource.model.Category;
import com.excederus.shaderpatcher.resource.model.Kind;
import com.excederus.shaderpatcher.resource.model.Resource;
import com.excederus.shaderpatcher.resource.model.ResourceId;
import com.excederus.shaderpatcher.resource.model.Source;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds patch/mapping/transform YAML resources from the three sources ShaderPatcher reads from:
 * its own jar (bundled), other mods' own jars (internal), and the modpack author's config folder (external).
 * Patches are additionally filtered down to modids that are actually installed.
 */
public final class ResourceDiscovery {

    private static final Logger LOG = Log.of(ResourceDiscovery.class);

    private ResourceDiscovery() {}

    public static List<Resource> discover(Kind kind, String ownModId, List<ModSource> mods, Path configDir) {

        List<Resource> resources = new ArrayList<>();

        for (ModSource mod : mods) {
            boolean isOwn = mod.modId().equals(ownModId);
            String context = (isOwn ? "bundled " : "internal ") + kind + " resource from " + mod.modId() + " (" + mod.path() + ")";

            for (NamedBytes file : scanModAssets(mod.path(), mod.modId(), kind)) {
                ResourceId id = parseFilename(file.filename(), kind, context);
                if (id == null)
                    continue;

                // Mods may only provide patches/mappings/transforms for themselves, never for another modid.
                if (!isOwn && !id.modid().equals(mod.modId())) {
                    LOG.warn("Skipping {} ({}): mods may only provide resources for themselves, not for '{}'", file.filename(), context, id.modid());
                    continue;
                }

                resources.add(ResourceParser.parse(id, isOwn ? Source.BUNDLED : Source.INTERNAL, kind, file.data()));
            }
        }

        for (NamedBytes file : scanExternal(configDir, kind)) {
            ResourceId id = parseFilename(file.filename(), kind, "external " + kind + " resource in " + configDir);
            if (id == null)
                continue;

            resources.add(ResourceParser.parse(id, Source.EXTERNAL, kind, file.data()));
        }

        if (kind == Kind.PATCH) {
            Set<String> installedModIds = mods.stream().map(ModSource::modId).collect(Collectors.toSet());

            int beforeFilter = resources.size();
            resources.removeIf(resource -> !installedModIds.contains(resource.id().modid()));

            if (resources.isEmpty()) {
                if (beforeFilter > 0)
                    LOG.info("No patches applied: none of the {} mod(s) with available patches are installed", beforeFilter);
            }
            else {
                long applicableMods = resources.stream().map(Resource::id).distinct().count();
                LOG.info("Loaded {} patch(es) applicable to the current install", applicableMods);
            }
        }

        return resources;
    }

    private static ResourceId parseFilename(String filename, Kind kind, String context) {

        String[] parts = filename.split("\\.");
        if (parts.length != 3) {
            LOG.warn("Skipping {} ({}): expected a '<category>.<modid>.yaml' filename", filename, context);
            return null;
        }

        String extension = parts[2];
        if (!extension.equals("yaml") && !extension.equals("yml")) {
            LOG.warn("Skipping {} ({}): unsupported extension '{}', expected 'yaml' or 'yml'", filename, context, extension);
            return null;
        }

        Category category = switch (parts[0]) {
            case "block" -> Category.BLOCK;
            case "item" -> Category.ITEM;
            case "entity" -> Category.ENTITY;
            default -> null;
        };
        if (category == null) {
            LOG.warn("Skipping {} ({}): unknown category '{}', expected 'block', 'item' or 'entity'", filename, context, parts[0]);
            return null;
        }

        String modid = parts[1];

        // A "default patch" is meaningless: patches are inherently per-mod, so _default is reserved for mappings/transforms.
        if (kind == Kind.PATCH && modid.equals("_default")) {
            LOG.warn("Skipping {} ({}): '_default' patches are not allowed, patches must target a specific modid", filename, context);
            return null;
        }

        return new ResourceId(category, modid);
    }

    private static List<NamedBytes> scanModAssets(Path modPath, String ownerModId, Kind kind) {

        if (modPath == null)
            return List.of();

        String prefix = "assets/" + ownerModId + "/shaderpatcher/" + folderName(kind) + "/";

        if (Files.isRegularFile(modPath))
            return scanZip(modPath, prefix);

        if (Files.isDirectory(modPath))
            return scanFolder(modPath, prefix);

        return List.of();
    }

    private static List<NamedBytes> scanZip(Path zipPath, String prefix) {

        List<NamedBytes> results = new ArrayList<>();

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (entry.isDirectory() || !name.startsWith(prefix))
                    continue;

                String rest = name.substring(prefix.length());
                if (rest.isEmpty() || rest.contains("/"))
                    continue;

                try (InputStream stream = zip.getInputStream(entry)) {
                    results.add(new NamedBytes(rest, stream.readAllBytes()));
                }
            }
        } catch (FileNotFoundException e) {
            // Some mod sources (e.g. nested jar-in-jar module paths) don't resolve to a directly openable
            // zip even though they're reported as a regular file; there's simply nothing to scan there.
            return List.of();
        } catch (IOException e) {
            LOG.warn("Failed to read {}: {}", zipPath, e.toString());
            return List.of();
        }

        return results;
    }

    private static List<NamedBytes> scanFolder(Path modDir, String prefix) {

        Path dir = modDir;
        for (String segment : prefix.split("/"))
            if (!segment.isEmpty())
                dir = dir.resolve(segment);

        if (!Files.isDirectory(dir))
            return List.of();

        List<NamedBytes> results = new ArrayList<>();

        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                results.add(new NamedBytes(file.getFileName().toString(), Files.readAllBytes(file)));
        } catch (IOException e) {
            LOG.warn("Failed to read {}: {}", dir, e.toString());
            return List.of();
        }

        return results;
    }

    private static List<NamedBytes> scanExternal(Path configDir, Kind kind) {

        Path dir = configDir.resolve(folderName(kind));
        if (!Files.isDirectory(dir))
            return List.of();

        List<NamedBytes> results = new ArrayList<>();

        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                results.add(new NamedBytes(file.getFileName().toString(), Files.readAllBytes(file)));
        } catch (IOException e) {
            LOG.warn("Failed to read {}: {}", dir, e.toString());
            return List.of();
        }

        return results;
    }

    private static String folderName(Kind kind) {
        return switch (kind) {
            case PATCH -> "patches";
            case MAPPING -> "mappings";
            case TRANSFORM -> "transforms";
        };
    }

    private record NamedBytes(String filename, byte[] data) {}
}
