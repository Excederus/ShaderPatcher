package com.excederus.shaderpatcher.resource.model;

public record RawResource(
        String modid,
        ResourceCategory category,
        ResourceSource source,
        byte[] data
) {
}
