package com.excederus.shaderpatcher.resource.model;

import java.util.Map;

public record Resource(
        ResourceKey resourceKey,
        ResourceSource source,
        Map<MappingKey, Content> contents
) {
}
