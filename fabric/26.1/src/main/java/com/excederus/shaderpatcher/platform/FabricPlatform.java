package com.excederus.shaderpatcher.platform;

import com.excederus.shaderpatcher.resource.model.InternalResource;
import com.excederus.shaderpatcher.resource.model.ResourceCategory;
import com.excederus.shaderpatcher.resource.model.ResourceSource;
import com.excederus.shaderpatcher.resource.model.ResourceType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.excederus.shaderpatcher.Constants.*;

public class FabricPlatform implements Platform {

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public List<InternalResource> getInternalResources() {

        List<InternalResource> resources = new ArrayList<>();

        Map<Identifier, Resource> internalResources = Minecraft.getInstance().getResourceManager().listResources(MODID, id -> id.getPath().endsWith(".yaml") || id.getPath().endsWith(".yml"));

        for (Map.Entry<Identifier, Resource> resource : internalResources.entrySet()) {

            try (InputStream stream = resource.getValue().open()) {

                String path = resource.getKey().getPath().replace("\\", "/");

                String[] split = path.split("/");
                String filename = split[2];
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

                String namespace = resource.getKey().getNamespace();
                String modid = filenameSplit[1];
                ResourceType type = switch (split[1]) {
                    case "patches" -> ResourceType.PATCH;
                    case "mappings" -> ResourceType.MAPPING;
                    case "transforms" -> ResourceType.TRANSFORM;
                    default -> null;
                };
                if (type == null) {
                    LOG.warn("Invalid resource folder: {}", split[1]);
                    continue;
                }

                ResourceCategory category = switch (filenameSplit[0]) {
                    case "block" -> ResourceCategory.BLOCK;
                    case "item" -> ResourceCategory.ITEM;
                    case "entities" -> ResourceCategory.ENTITY;
                    default -> null;
                };
                if (category == null) {
                    LOG.warn("Invalid resource category: {}", filename);
                    continue;
                }

                ResourceSource source = namespace.equals(MODID) ? ResourceSource.BUNDLED : ResourceSource.INTERNAL;
                byte[] data = stream.readAllBytes();

                resources.add(new InternalResource(
                        path,
                        namespace,
                        modid,
                        type,
                        category,
                        source,
                        data
                ));
            }
            catch (IOException e) { LOG.warn("Failed to load internal resource: {}", resource.getKey().getPath(), e); }
        }

        return resources;
    }
}
