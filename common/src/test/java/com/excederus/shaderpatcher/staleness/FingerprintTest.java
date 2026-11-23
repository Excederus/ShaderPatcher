package com.excederus.shaderpatcher.staleness;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FingerprintTest {

    @Test
    void encodeDecodeRoundTrips() {
        Fingerprint fingerprint = new Fingerprint("abc123", "def456");

        Optional<Fingerprint> decoded = Fingerprint.decode(fingerprint.encode());

        assertTrue(decoded.isPresent());
        assertEquals(fingerprint, decoded.get());
    }

    @Test
    void decodeRejectsNullMissingAndMalformedComments() {
        assertTrue(Fingerprint.decode(null).isEmpty());
        assertTrue(Fingerprint.decode("").isEmpty());
        assertTrue(Fingerprint.decode("some other comment").isEmpty());
        assertTrue(Fingerprint.decode("shaderpatcher:1:base=;patches=").isEmpty());
        assertTrue(Fingerprint.decode("shaderpatcher:1:base=abc123").isEmpty());
    }

    @Test
    void shouldRegenerateWhenCommentMissingOrMalformed() {
        assertTrue(Fingerprint.shouldRegenerate(null, "base", "patches"));
        assertTrue(Fingerprint.shouldRegenerate("not a fingerprint", "base", "patches"));
    }

    @Test
    void shouldRegenerateWhenEitherHashChanges() {
        String comment = new Fingerprint("base1", "patches1").encode();

        assertFalse(Fingerprint.shouldRegenerate(comment, "base1", "patches1"));
        assertTrue(Fingerprint.shouldRegenerate(comment, "base2", "patches1"));
        assertTrue(Fingerprint.shouldRegenerate(comment, "base1", "patches2"));
        assertTrue(Fingerprint.shouldRegenerate(comment, "base2", "patches2"));
    }
}
