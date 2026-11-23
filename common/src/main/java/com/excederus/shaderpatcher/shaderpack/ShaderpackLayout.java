package com.excederus.shaderpatcher.shaderpack;

import com.excederus.shaderpatcher.resource.model.Category;

/** The on-disk conventions shared by the scanner and the writer: where patched output goes, and how a properties entry is recognized. */
final class ShaderpackLayout {

    private ShaderpackLayout() {}

    private static final String OUTPUT_SUFFIX = " + shaderpatcher.zip";

    static String outputFileName(String name) {
        return name + OUTPUT_SUFFIX;
    }

    static boolean isOutputFile(String filename) {
        return filename.endsWith(OUTPUT_SUFFIX);
    }

    /** A properties entry is identified purely by its filename and immediate parent folder, regardless of nesting depth. */
    static Category categoryOfEntry(String path) {

        String[] segments = path.split("/");
        if (segments.length < 2 || !segments[segments.length - 2].equals("shaders"))
            return null;

        return switch (segments[segments.length - 1]) {
            case "block.properties" -> Category.BLOCK;
            case "item.properties" -> Category.ITEM;
            case "entity.properties" -> Category.ENTITY;
            default -> null;
        };
    }
}
