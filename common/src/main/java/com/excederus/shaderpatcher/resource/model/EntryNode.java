package com.excederus.shaderpatcher.resource.model;

import java.util.List;

public final class EntryNode implements PropertyNode {

    private final String identifier;
    private final List<String> lines;

    public EntryNode(String identifier, List<String> lines) {
        this.identifier = identifier;
        this.lines = lines;
    }

    public String identifier() {
        return identifier;
    }

    public List<String> lines() {
        return lines;
    }
}
