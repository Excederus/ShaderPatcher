package com.excederus.shaderpatcher.shaderpack;

import com.excederus.shaderpatcher.resource.model.ShaderpackFormat;

import java.nio.file.Path;

/** A candidate shaderpack found in the {@code shaderpacks} folder, not yet patched. */
public record Shaderpack(String name, Path path, ShaderpackFormat format) {
}
