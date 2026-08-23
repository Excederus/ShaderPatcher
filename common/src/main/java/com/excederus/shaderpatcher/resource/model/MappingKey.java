package com.excederus.shaderpatcher.resource.model;

import org.jetbrains.annotations.Nullable;

public record MappingKey(
        String category,
        String concept,
        @Nullable String variant
) {
}
