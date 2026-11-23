package com.excederus.shaderpatcher.resource.model;

/** Identifies one whole YAML resource, e.g. {@code block.cobblemon.yaml} -> {@code ResourceId(BLOCK, "cobblemon")}. */
public record ResourceId(Category category, String modid) {
}
