package kg.aidarbek.smpp.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ReviewCoverageTest {
    private static final SourceType CURRENT =
            new SourceType("src/main/java/example/Value.java", "example.Value", "a".repeat(64));

    @Test
    void rejectsAMissingTypeReview() {
        assertEquals(
                List.of("Missing review for src/main/java/example/Value.java :: example.Value"),
                new ReviewCoverage().violations(List.of(CURRENT), List.of()));
    }

    @Test
    void acceptsACompleteCurrentReview() {
        assertEquals(List.of(), new ReviewCoverage().violations(List.of(CURRENT), List.of(review(CURRENT))));
    }

    @Test
    void identifiesStaleEvidenceByTheExpectedHash() {
        SourceType old = new SourceType(CURRENT.source(), CURRENT.identity(), "b".repeat(64));

        assertEquals(
                List.of("Stale review for src/main/java/example/Value.java :: example.Value; expected SHA-256 "
                        + "a".repeat(64)),
                new ReviewCoverage().violations(List.of(CURRENT), List.of(review(old))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"S", "O", "L", "I", "D"})
    void rejectsFailingPrinciplesEvenBesidePassingCurrentEvidence(String principle) {
        ReviewEvidence passing = review(CURRENT);
        var principles = new HashMap<>(passing.principles());
        principles.put(principle, new PrincipleReview("fail", "The reviewed obligation remains violated."));
        var failing = new ReviewEvidence(CURRENT, passing.responsibility(), passing.consumers(), principles, "none");

        var failures = new ReviewCoverage().violations(List.of(CURRENT), List.of(passing, failing));

        assertEquals(1, failures.size());
        assertTrue(failures.getFirst().contains("Unresolved " + principle + " review"));
    }

    @Test
    void rejectsRemainingFindingsEvenWhenEveryVerdictPasses() {
        ReviewEvidence passing = review(CURRENT);
        var failing = new ReviewEvidence(
                CURRENT,
                passing.responsibility(),
                passing.consumers(),
                passing.principles(),
                "Mutable storage escapes");

        assertEquals(
                List.of(
                        "Remaining findings for src/main/java/example/Value.java :: example.Value: Mutable storage escapes"),
                new ReviewCoverage().violations(List.of(CURRENT), List.of(passing, failing)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.Value.Nested", "example.Renamed"})
    void newlyAddedOrRenamedTypesNeedTheirOwnEvidence(String identity) {
        SourceType changed = new SourceType(CURRENT.source(), identity, CURRENT.sha256());

        assertEquals(
                List.of("Missing review for " + CURRENT.source() + " :: " + identity),
                new ReviewCoverage().violations(List.of(CURRENT, changed), List.of(review(CURRENT))));
    }

    @Test
    void aMatchingTypeFromAnotherSourceRootCannotSupplyCoverage() {
        SourceType elsewhere = new SourceType("src/test/java/example/Value.java", CURRENT.identity(), CURRENT.sha256());

        assertEquals(
                List.of("Missing review for src/main/java/example/Value.java :: example.Value"),
                new ReviewCoverage().violations(List.of(CURRENT), List.of(review(elsewhere))));
    }

    @Test
    void historicalEvidenceAndHistoricalFindingsDoNotReplaceCurrentEvidence() {
        SourceType old = new SourceType(CURRENT.source(), CURRENT.identity(), "b".repeat(64));
        ReviewEvidence oldPassing = review(old);
        var oldFailing = new ReviewEvidence(
                old,
                oldPassing.responsibility(),
                oldPassing.consumers(),
                oldPassing.principles(),
                "Old mutable storage");

        assertEquals(
                List.of(), new ReviewCoverage().violations(List.of(CURRENT), List.of(oldFailing, review(CURRENT))));
    }

    private static ReviewEvidence review(SourceType type) {
        return new ReviewEvidence(
                type,
                "Stores one integer value for the example API.",
                "Callers use integer value access and equality.",
                Map.of(
                        "S", new PrincipleReview("pass", "Only the integer invariant can drive changes."),
                        "O", new PrincipleReview("pass", "No variable operation is required for this value."),
                        "L", new PrincipleReview("pass", "Integer equality and hash code preserve Object contracts."),
                        "I", new PrincipleReview("not applicable", "No custom interface is exposed to callers."),
                        "D", new PrincipleReview("pass", "Primitive values do not depend on infrastructure.")),
                "none");
    }
}
