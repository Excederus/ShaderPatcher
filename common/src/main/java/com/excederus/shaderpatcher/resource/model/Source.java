package com.excederus.shaderpatcher.resource.model;

/** Ordered by override priority: a higher ordinal wins when the same {@link ResourceId} is defined twice. */
public enum Source {
    BUNDLED,
    INTERNAL,
    EXTERNAL
}
