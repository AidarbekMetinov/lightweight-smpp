package kg.aidarbek.smpp.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class ReviewDocumentParserTest {
    private static final String CURRENT = """
            # Example review

            ```solid-review
            source: src/main/java/example/Value.java
            type: example.Value
            sha256: 461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1
            responsibility: Stores one integer value for callers of the example API.
            consumers: Example callers use the number accessor and value equality.
            S: pass | Only the integer value invariant can change this record.
            O: pass | The example has no supported variable behavior or extension requirement.
            L: pass | Generated equality and hash code preserve integer value semantics.
            I: not applicable | No custom interface; callers only need its integer accessor.
            D: pass | Uses a primitive value and has no infrastructure dependency.
            findings: none
            ```

            Historical narrative is allowed outside evidence blocks.
            """;

    @Test
    void readsOneCompleteReviewFromMarkdown() {
        var evidence = new ReviewDocumentParser().parse(CURRENT, "current.md");

        assertEquals(1, evidence.size());
        ReviewEvidence review = evidence.getFirst();
        assertEquals("src/main/java/example/Value.java", review.type().source());
        assertEquals("example.Value", review.type().identity());
        assertEquals(
                "461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1",
                review.type().sha256());
        assertEquals("Stores one integer value for callers of the example API.", review.responsibility());
        assertEquals("Example callers use the number accessor and value equality.", review.consumers());
        assertEquals(5, review.principles().size());
        assertEquals(
                new PrincipleReview("pass", "Only the integer value invariant can change this record."),
                review.principles().get("S"));
        assertEquals("not applicable", review.principles().get("I").verdict());
        assertEquals("none", review.findings());
    }

    @ParameterizedTest
    @MethodSource("malformedEvidence")
    void rejectsMalformedEvidenceWithItsDocumentName(String malformed) {
        var failure = assertThrows(
                IllegalArgumentException.class, () -> new ReviewDocumentParser().parse(malformed, "broken.md"));
        assertTrue(failure.getMessage().contains("broken.md"));
    }

    @Test
    void ignoresExamplesInsideOrdinaryFencedCodeAndReadsSeparateReviewBlocks() {
        String markdown = "````markdown\n" + CURRENT + "````\n" + CURRENT + CURRENT;

        assertEquals(
                2, new ReviewDocumentParser().parse(markdown, "examples.md").size());
    }

    @Test
    void rejectsAReviewTagSeparatedFromItsFenceByWhitespace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReviewDocumentParser()
                        .parse(CURRENT.replace("```solid-review", "``` solid-review"), "broken.md"));
    }

    private static Stream<String> malformedEvidence() {
        return Stream.of(
                CURRENT.replace("type: example.Value", "type: example.Value\ntype: example.Other"),
                CURRENT.replace("findings: none", "findings: none\nunknown: unexpected"),
                CURRENT.replace("type: example.Value\n", ""),
                CURRENT.replace("type: example.Value", "type: example.Value extra"),
                CURRENT.replace(
                        "sha256: 461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1", "sha256: old"),
                CURRENT.replace("S: pass | Only the integer value invariant can change this record.", "S: pass | "),
                CURRENT.replace("S: pass |", "S: maybe |"),
                CURRENT.replace("S: pass |", "S: pass"),
                CURRENT.replace(
                        "responsibility: Stores one integer value for callers of the example API.", "responsibility: "),
                CURRENT.replace("consumers: Example callers use the number accessor and value equality.\n", ""),
                CURRENT.replace("findings: none", "findings: "),
                CURRENT.replace("type: example.Value", "malformed line"),
                CURRENT.replace("src/main/java/example/Value.java", "../Value.java"),
                CURRENT.replace("src/main/java/example/Value.java", "/Value.java"),
                CURRENT.replace("src/main/java/example/Value.java", "C:\\Value.java"),
                CURRENT.replace("src/main/java/example/Value.java", "src/../Value.java"),
                CURRENT.replace("src/main/java/example/Value.java", "src//Value.java"),
                CURRENT.replace("src/main/java/example/Value.java", "src/Value.txt"),
                CURRENT.replace("```\n", ""),
                CURRENT.replace("```solid-review", "```solid-review extra"),
                CURRENT.replace("type: example.Value", "```solid-review\ntype: example.Value"));
    }
}
