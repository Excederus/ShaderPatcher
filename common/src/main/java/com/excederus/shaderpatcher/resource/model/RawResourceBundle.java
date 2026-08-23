package com.excederus.shaderpatcher.resource.model;

import java.util.List;

public record RawResourceBundle(
        List<RawShaderpack> shaderpacks,
        List<RawResource> patches,
        List<RawResource> mappings,
        List<RawResource> transforms
) {
}
