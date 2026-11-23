package com.excederus.shaderpatcher.staleness;

import java.util.Optional;

/**
 * The two hashes stored in a patched shaderpack's zip comment: one for the source shaderpack,
 * one for the resolved patch collection that was applied to it. Either changing means the
 * patched output is stale and must be regenerated.
 */
public record Fingerprint(String baseHash, String patchesHash) {

    private static final String PREFIX = "shaderpatcher:1:";

    public String encode() {
        return PREFIX + "base=" + baseHash + ";patches=" + patchesHash;
    }

    public static Optional<Fingerprint> decode(String comment) {

        if (comment == null || !comment.startsWith(PREFIX))
            return Optional.empty();

        String base = null;
        String patches = null;

        for (String part : comment.substring(PREFIX.length()).split(";")) {
            if (part.startsWith("base="))
                base = part.substring("base=".length());
            else if (part.startsWith("patches="))
                patches = part.substring("patches=".length());
        }

        if (base == null || base.isEmpty() || patches == null || patches.isEmpty())
            return Optional.empty();

        return Optional.of(new Fingerprint(base, patches));
    }

    /** A missing or malformed comment is treated as stale, same as a mismatched hash. */
    public static boolean shouldRegenerate(String existingComment, String newBaseHash, String newPatchesHash) {
        return decode(existingComment)
                .map(fingerprint -> !fingerprint.baseHash().equals(newBaseHash) || !fingerprint.patchesHash().equals(newPatchesHash))
                .orElse(true);
    }
}
