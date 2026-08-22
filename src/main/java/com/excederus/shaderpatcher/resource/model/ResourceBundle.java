package com.excederus.shaderpatcher.resource.model;

import java.util.List;

public record ResourceBundle(
        List<Shaderpack> shaderpacks,
        List<Resource> patches,
        List<Resource> mappings,
        List<Resource> transforms
) {
}
