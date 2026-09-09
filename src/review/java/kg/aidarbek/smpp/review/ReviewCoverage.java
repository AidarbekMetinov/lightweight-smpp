package kg.aidarbek.smpp.review;

import java.util.ArrayList;
import java.util.List;

final class ReviewCoverage {
    List<String> violations(List<SourceType> inventory, List<ReviewEvidence> reviews) {
        var violations = new ArrayList<String>();
        for (SourceType type : inventory) {
            String description = type.source() + " :: " + type.identity();
            var matchingType = reviews.stream()
                    .filter(review -> review.type().source().equals(type.source())
                            && review.type().identity().equals(type.identity()))
                    .toList();
            if (matchingType.isEmpty()) {
                violations.add("Missing review for " + description);
            } else if (matchingType.stream()
                    .noneMatch(review -> review.type().sha256().equals(type.sha256()))) {
                violations.add("Stale review for " + description + "; expected SHA-256 " + type.sha256());
            } else {
                for (ReviewEvidence review : matchingType) {
                    if (!review.type().sha256().equals(type.sha256())) {
                        continue;
                    }
                    for (String principle : List.of("S", "O", "L", "I", "D")) {
                        PrincipleReview assessment = review.principles().get(principle);
                        if (assessment.verdict().equals("fail")) {
                            violations.add("Unresolved " + principle + " review for " + description + ": "
                                    + assessment.reasoning());
                        }
                    }
                    if (!review.findings().equals("none")) {
                        violations.add("Remaining findings for " + description + ": " + review.findings());
                    }
                }
            }
        }
        return List.copyOf(violations);
    }
}
