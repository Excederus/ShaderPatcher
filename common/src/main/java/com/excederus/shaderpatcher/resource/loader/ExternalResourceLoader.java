package com.excederus.shaderpatcher.resource.loader;

import com.excederus.shaderpatcher.resource.model.RawResource;
import com.excederus.shaderpatcher.resource.model.ResourceCategory;
import com.excederus.shaderpatcher.resource.model.ResourceSource;
import com.excederus.shaderpatcher.resource.model.ResourceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.excederus.shaderpatcher.Constants.*;

public class ExternalResourceLoader {

    private final Path gameDir;

    public ExternalResourceLoader(Path gameDir) {
        this.gameDir = gameDir;
    }

    public List<RawResource> loadExternalResources(ResourceType targetType) {

        List<RawResource> resources = new ArrayList<>();

        Path configDir = gameDir.resolve("config").resolve("shaderpatcher");
        String targetTypeString = switch (targetType) {
            case PATCH -> "patches";
            case MAPPING -> "mappings";
            case TRANSFORM -> "transforms";
        };
        List<Path> externalResourcePaths = listExternalResources(configDir.resolve(targetTypeString));

        if (externalResourcePaths.isEmpty())
            return resources;

        for (Path externalResourcePath : externalResourcePaths) {
            try {
                String filename = externalResourcePath.getFileName().toString();
                String[] filenameSplit = filename.split("\\.");
                if (filenameSplit.length != 3) {
                    LOG.warn("Invalid resource filename: {}", filename);
                    continue;
                }

                String extension = filenameSplit[2];
                if (!extension.equals("yaml") && !extension.equals("yml")) {
                    LOG.warn("Invalid resource extension: {}", filename);
                    continue;
                }

                ResourceCategory category = switch (filenameSplit[0]) {
                    case "block" -> ResourceCategory.BLOCK;
                    case "item" -> ResourceCategory.ITEM;
                    case "entity" -> ResourceCategory.ENTITY;
                    default -> null;
                };
                if (category == null) {
                    LOG.warn("Invalid resource category: {}", filename);
                    continue;
                }

                String modid = filenameSplit[1];
                if (targetType == ResourceType.PATCH && modid.equals("_default")) {
                    LOG.warn("Default patches are not allowed: {}", externalResourcePath);
                    continue;
                }

                byte[] data = Files.readAllBytes(externalResourcePath);

                resources.add(new RawResource(
                        modid,
                        category,
                        ResourceSource.EXTERNAL,
                        data
                ));
            } catch (IOException e) {
                LOG.warn("Failed to read contents of {}", externalResourcePath, e);
            }
        }

        if (resources.isEmpty())
            LOG.info("No external {} loaded", targetType);

        return resources;
    }

    private List<Path> listExternalResources(Path path) {

        List<Path> externalResourcePaths = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(path)) {
            paths.filter(Files::isRegularFile).forEach(externalResourcePaths::add);
        } catch (IOException e) {
            LOG.warn("Failed to list resources for: {}", path, e);
            return externalResourcePaths;
        }

        return externalResourcePaths;
    }
}
