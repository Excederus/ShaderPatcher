package com.excederus.shaderpatcher.resource.model;

import java.util.Map;

public record PropertyFile(
        Map<Identifier, Content> contents
) {
}
