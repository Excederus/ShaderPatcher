package com.excederus.shaderpatcher.resource.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDocumentTest {

    @Test
    void roundTripPreservesCommentsBlankLinesAndContinuations() {
        List<String> original = List.of(
                "# a comment",
                "",
                "block.1=minecraft:stone minecraft:dirt",
                "waving.flower.small=minecraft:dandelion \\",
                " minecraft:poppy \\",
                " minecraft:blue_orchid",
                "# trailing comment"
        );

        PropertyDocument document = PropertyDocument.parse(original);

        assertEquals(original, document.render());
    }

    @Test
    void appendValuesAddsContinuationLine() {
        PropertyDocument document = PropertyDocument.parse(List.of("block.1=minecraft:stone"));

        Optional<Line> line = document.find("block.1");
        assertTrue(line.isPresent());

        line.get().appendValues(List.of("modid:foo", "modid:bar"));

        assertEquals(
                List.of("block.1=minecraft:stone \\", " modid:foo modid:bar"),
                document.render()
        );
    }

    @Test
    void appendValuesOnAlreadyContinuedLineAddsNewLineWithoutDuplicatingBackslash() {
        PropertyDocument document = PropertyDocument.parse(List.of(
                "block.1=minecraft:stone \\",
                " minecraft:dirt"
        ));

        document.find("block.1").get().appendValues(List.of("modid:foo"));

        assertEquals(
                List.of("block.1=minecraft:stone \\", " minecraft:dirt \\", " modid:foo"),
                document.render()
        );
    }

    @Test
    void findReturnsEmptyForMissingIdentifier() {
        PropertyDocument document = PropertyDocument.parse(List.of("block.1=minecraft:stone"));

        assertTrue(document.find("block.2").isEmpty());
    }

    @Test
    void malformedLineIsKeptVerbatim() {
        List<String> original = List.of("not a valid line without equals");

        PropertyDocument document = PropertyDocument.parse(original);

        assertEquals(original, document.render());
    }

    @Test
    void preprocessorDirectivesInsideAContinuationDoNotSplitOrCorruptTheEntry() {
        // Reduced version of the real-world Bliss shader bug report: an #ifdef/#else/#endif sits
        // inside a backslash-continued value, including a duplicate "block.56=" assignment for the
        // other branch, and neither #else nor #endif repeats the trailing backslash of the line before it.
        List<String> original = List.of(
                "#ifdef BOES_EARTH_BLOCKSTATES",
                "block.56=minecraft:azalea_leaves minecraft:oak_leaves \\",
                "#else",
                "block.56=minecraft:azalea_leaves \\",
                "#endif",
                " beachparty:palm_leaves \\",
                " westerosblocks:palm_leaves"
        );

        PropertyDocument document = PropertyDocument.parse(original);

        // Round trip without any patching must reproduce the original file exactly, directives and all.
        assertEquals(original, document.render());

        // There must be exactly one "block.56" entry (not two, split at #else), so matching/appending works correctly.
        assertEquals(1, document.lines().stream().filter(Line::isEntry).filter(l -> l.identifier().equals("block.56")).count());

        document.find("block.56").get().appendValues(List.of("cobblemon:apricorn_leaves"));

        // The new value must land once, cleanly after the real final content - never interleaved with a directive.
        assertEquals(
                List.of(
                        "#ifdef BOES_EARTH_BLOCKSTATES",
                        "block.56=minecraft:azalea_leaves minecraft:oak_leaves \\",
                        "#else",
                        "block.56=minecraft:azalea_leaves \\",
                        "#endif",
                        " beachparty:palm_leaves \\",
                        " westerosblocks:palm_leaves \\",
                        " cobblemon:apricorn_leaves"
                ),
                document.render()
        );
    }

    @Test
    void danglingContinuationAtEndOfFileIsNotLost() {
        List<String> original = List.of(
                "block.1=minecraft:stone \\",
                " minecraft:dirt \\"
        );

        PropertyDocument document = PropertyDocument.parse(original);

        assertEquals(original, document.render());
        assertTrue(document.find("block.1").isPresent());
    }
}
