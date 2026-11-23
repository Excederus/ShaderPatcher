package com.excederus.shaderpatcher.resource.model;

/** Identifies a leaf path inside a parsed YAML resource, e.g. {@code waving.flower.small}. */
public record MappingKey(String type, String concept, String variant) {
}
