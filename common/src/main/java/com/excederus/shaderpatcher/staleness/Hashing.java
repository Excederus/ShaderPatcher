package com.excederus.shaderpatcher.staleness;

import com.excederus.shaderpatcher.resource.model.MappingKey;
import com.excederus.shaderpatcher.resource.model.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** SHA-256 hashing for staleness detection: one hash for the source shaderpack, one for the resolved patch collection. */
public final class Hashing {

    private Hashing() {}

    public static String sha256(byte[] data) {
        return HexFormat.of().formatHex(newDigest().digest(data));
    }

    public static String sha256(InputStream stream) throws IOException {
        MessageDigest digest = newDigest();

        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1)
            digest.update(buffer, 0, read);

        return HexFormat.of().formatHex(digest.digest());
    }

    /** Hash of a single shaderpack zip file's raw bytes. */
    public static String hashFile(Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            return sha256(stream);
        }
    }

    /** Deterministic hash of a shaderpack folder's contents, independent of filesystem iteration order. */
    public static String hashFolder(Path folder) throws IOException {

        List<Path> files;
        try (Stream<Path> stream = Files.walk(folder)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }

        MessageDigest digest = newDigest();

        for (Path file : files) {
            String relative = folder.relativize(file).toString().replace('\\', '/');
            digest.update(relative.getBytes(StandardCharsets.UTF_8));

            try (InputStream stream = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1)
                    digest.update(buffer, 0, read);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Deterministic hash of every patch/mapping/transform resource actually applicable to the current install,
     * salted with the ShaderPatcher version so a ShaderPatcher update also triggers regeneration.
     */
    public static String patchCollectionHash(String modVersion, List<Resource> patches, List<Resource> mappings, List<Resource> transforms) {

        StringBuilder canonical = new StringBuilder();
        canonical.append("v=").append(modVersion).append('\n');

        appendCanonical(canonical, "PATCH", patches);
        appendCanonical(canonical, "MAPPING", mappings);
        appendCanonical(canonical, "TRANSFORM", transforms);

        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendCanonical(StringBuilder canonical, String kindLabel, List<Resource> resources) {

        List<Resource> sorted = resources.stream()
                .sorted(Comparator.<Resource, String>comparing(r -> r.id().category().name()).thenComparing(r -> r.id().modid()))
                .toList();

        for (Resource resource : sorted) {
            canonical.append(kindLabel).append(':').append(resource.id().category()).append(':').append(resource.id().modid())
                    .append(':').append(resource.source()).append('\n');

            resource.contents().keySet().stream()
                    .sorted(Comparator.comparing(MappingKey::type).thenComparing(MappingKey::concept).thenComparing(k -> k.variant() == null ? "" : k.variant()))
                    .forEach(key -> canonical.append("  ").append(key.type()).append('.').append(key.concept()).append('.').append(key.variant())
                            .append('=').append(String.join(",", resource.contents().get(key))).append('\n'));
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
