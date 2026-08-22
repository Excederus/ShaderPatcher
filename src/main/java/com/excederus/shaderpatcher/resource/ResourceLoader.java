package com.excederus.shaderpatcher.resource;

import com.excederus.shaderpatcher.platform.Platform;
import com.excederus.shaderpatcher.resource.loader.ExternalResourceLoader;
import com.excederus.shaderpatcher.resource.loader.InternalResourceLoader;
import com.excederus.shaderpatcher.resource.loader.ShaderpackResourceLoader;
import com.excederus.shaderpatcher.resource.model.RawResource;
import com.excederus.shaderpatcher.resource.model.RawResourceBundle;
import com.excederus.shaderpatcher.resource.model.RawShaderpack;
import com.excederus.shaderpatcher.resource.model.ResourceType;

import java.util.ArrayList;
import java.util.List;

public class ResourceLoader {

    private final Platform platform;

    public ResourceLoader(Platform platform) {
        this.platform = platform;
    }

    public RawResourceBundle loadResources() {

        ShaderpackResourceLoader shaderpackResourceLoader = new ShaderpackResourceLoader(platform.getGameDir());
        InternalResourceLoader internalResourceLoader = new InternalResourceLoader(platform.getInternalResources());
        ExternalResourceLoader externalResourceLoader = new ExternalResourceLoader(platform.getGameDir());

        List<RawShaderpack> shaderpacks = new ArrayList<>();
        List<RawResource> patches = new ArrayList<>();
        List<RawResource> mappings = new ArrayList<>();
        List<RawResource> transforms = new ArrayList<>();

        // Load Shaderpacks
        shaderpacks.addAll(shaderpackResourceLoader.loadShaderpacks());
        if (shaderpacks.isEmpty())
            return null;

        // Load Patches
        patches.addAll(internalResourceLoader.loadInternalResources(ResourceType.PATCH));
        patches.addAll(externalResourceLoader.loadExternalResources(ResourceType.PATCH));
        if (patches.isEmpty())
            return null;

        // Load Mappings
        mappings.addAll(internalResourceLoader.loadInternalResources(ResourceType.MAPPING));
        mappings.addAll(externalResourceLoader.loadExternalResources(ResourceType.MAPPING));
        if (mappings.isEmpty())
            return null;

        // Load Transforms
        transforms.addAll(internalResourceLoader.loadInternalResources(ResourceType.TRANSFORM));
        transforms.addAll(externalResourceLoader.loadExternalResources(ResourceType.TRANSFORM));
        if (transforms.isEmpty())
            return null;

        return new RawResourceBundle(
                shaderpacks,
                patches,
                mappings,
                transforms
        );
    }
}
