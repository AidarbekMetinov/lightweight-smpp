package kg.aidarbek.smpp.review;

import java.util.Map;

record ReviewEvidence(
        SourceType type,
        String responsibility,
        String consumers,
        Map<String, PrincipleReview> principles,
        String findings) {
    ReviewEvidence {
        principles = Map.copyOf(principles);
    }
}
