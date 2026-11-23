package com.excederus.shaderpatcher.resource.model;

import java.util.List;
import java.util.Map;

public record Resource(ResourceId id, Source source, Map<MappingKey, List<String>> contents) {
}
