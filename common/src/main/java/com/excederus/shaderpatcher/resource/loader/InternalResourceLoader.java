package com.excederus.shaderpatcher.resource.loader;

import com.excederus.shaderpatcher.resource.model.*;

import java.util.ArrayList;
import java.util.List;

import static com.excederus.shaderpatcher.Constants.*;

public class InternalResourceLoader {

    private final List<InternalResource> internalResources;

    public InternalResourceLoader(List<InternalResource> internalResources) {
        this.internalResources = internalResources;
    }

    public List<RawResource> loadInternalResources(ResourceType targetType) {

        List<RawResource> resources = new ArrayList<>();

        for (InternalResource internalResource : internalResources) {
            ResourceType type = internalResource.type();
            if (type != targetType)
                continue;

            String namespace = internalResource.namespace();
            String modid = internalResource.modid();

            if (!namespace.equals(MODID)) {
                if (modid.equals("_default")) {
                    LOG.warn("Mods are not allowed to provide default files: {}", internalResource.namespace() + ":" + internalResource.path());
                    continue;
                }
                else if (!namespace.equals(modid)) {
                    LOG.warn("Mods are only allowed to add support for themselves: {}", internalResource.namespace() + ":" + internalResource.path());
                    continue;
                }
            }

            ResourceCategory category = internalResource.category();
            ResourceSource source = internalResource.source();
            byte[] data = internalResource.data();

            resources.add(new RawResource(
                    modid,
                    category,
                    source,
                    data
            ));
        }

        if (resources.isEmpty()) {
            LOG.warn("No internal {} loaded", targetType);
        }

        return resources;
    }
}
