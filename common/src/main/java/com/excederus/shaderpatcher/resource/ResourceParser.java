package com.excederus.shaderpatcher.resource;

import com.excederus.shaderpatcher.resource.model.*;
import com.excederus.shaderpatcher.resource.parser.GeneralResourceParser;
import com.excederus.shaderpatcher.resource.parser.ShaderpackResourceParser;

import java.util.ArrayList;
import java.util.List;

public class ResourceParser {

    public ResourceBundle parseResources(RawResourceBundle rawResourceBundle) {

        ShaderpackResourceParser shaderpackResourceParser = new ShaderpackResourceParser(rawResourceBundle);
        GeneralResourceParser generalResourceParser = new GeneralResourceParser(rawResourceBundle);

        // Parse Shaderpacks
        List<Shaderpack> shaderpacks = shaderpackResourceParser.parseShaderpacks();
        if (shaderpacks.isEmpty())
            return null;

        // Parse Patches
        List<Resource> patches = generalResourceParser.parseResources(ResourceType.PATCH);
        if (patches.isEmpty())
            return null;

        // Parse Mappings
        List<Resource> mappings = generalResourceParser.parseResources(ResourceType.MAPPING);
        if (mappings.isEmpty())
            return null;

        // Parse Transforms
        List<Resource> transforms = generalResourceParser.parseResources(ResourceType.TRANSFORM);
        if (transforms.isEmpty())
            return null;

        return new ResourceBundle(
                shaderpacks,
                patches,
                mappings,
                transforms
        );
    }
}
