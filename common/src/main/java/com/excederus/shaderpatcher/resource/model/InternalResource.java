package com.excederus.shaderpatcher.resource.model;

public record InternalResource(
        String path,
        String namespace,
        String modid,
        ResourceType type,
        ResourceCategory category,
        ResourceSource source,
        byte[] data
) {
}
