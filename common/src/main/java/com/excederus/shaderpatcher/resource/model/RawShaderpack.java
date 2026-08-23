package com.excederus.shaderpatcher.resource.model;

import java.nio.file.Path;

public record RawShaderpack(
        Path path,
        ShaderpackType type,
        byte[] blockData,
        byte[] itemData,
        byte[] entityData
) {
}
