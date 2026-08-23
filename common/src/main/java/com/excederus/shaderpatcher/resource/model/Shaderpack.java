package com.excederus.shaderpatcher.resource.model;

import java.nio.file.Path;

public record Shaderpack(
        String name,
        Path path,
        ShaderpackType type,
        PropertyFile block,
        PropertyFile item,
        PropertyFile entity
) {
}
