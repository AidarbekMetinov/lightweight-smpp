package kg.aidarbek.smpp.review;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ReviewDocumentParser {
    private static final List<String> PRINCIPLES = List.of("S", "O", "L", "I", "D");
    private static final Set<String> FIELDS =
            Set.of("source", "type", "sha256", "responsibility", "consumers", "S", "O", "L", "I", "D", "findings");

    List<ReviewEvidence> parse(String markdown, String document) {
        var result = new ArrayList<ReviewEvidence>();
        Map<String, String> fields = null;
        String ignoredFence = null;
        int lineNumber = 0;
        for (String rawLine : markdown.lines().toList()) {
            lineNumber++;
            String line = rawLine.strip();
            String location = document + ":" + lineNumber;
            String fence = fencePrefix(line);
            if (ignoredFence != null) {
                if (fence.length() >= ignoredFence.length()
                        && fence.charAt(0) == ignoredFence.charAt(0)
                        && line.substring(fence.length()).isEmpty()) {
                    ignoredFence = null;
                }
                continue;
            }
            if (line.equals("```solid-review")) {
                if (fields != null) {
                    throw malformed(location, "nested evidence block");
                }
                fields = new HashMap<>();
            } else if (line.startsWith("```solid-review")) {
                throw malformed(location, "expected an exact ```solid-review opening fence");
            } else if (line.equals("```") && fields != null) {
                result.add(evidence(fields, location));
                fields = null;
            } else if (fields != null) {
                int separator = line.indexOf(':');
                if (separator < 1) {
                    throw malformed(location, "expected key: value");
                }
                String key = line.substring(0, separator);
                String value = line.substring(separator + 1).strip();
                if (!FIELDS.contains(key) || value.isEmpty() || fields.putIfAbsent(key, value) != null) {
                    throw malformed(location, "unknown, empty or duplicate field: " + key);
                }
            } else if (!fence.isEmpty()) {
                if (line.substring(fence.length()).strip().startsWith("solid-review")) {
                    throw malformed(location, "expected an exact ```solid-review opening fence");
                }
                ignoredFence = fence;
            }
        }
        if (fields != null) {
            throw malformed(document, "unclosed evidence block");
        }
        return List.copyOf(result);
    }

    private static ReviewEvidence evidence(Map<String, String> fields, String location) {
        if (!fields.keySet().equals(FIELDS)) {
            throw malformed(location, "missing required evidence fields");
        }
        String source = fields.get("source");
        if (source.contains("\\") || source.contains(":") || !source.endsWith(".java")) {
            throw malformed(location, "source must be a canonical repository-relative Java path");
        }
        for (String component : source.split("/", -1)) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")) {
                throw malformed(location, "source must be a canonical repository-relative Java path");
            }
        }
        if (!fields.get("sha256").matches("[0-9a-f]{64}")) {
            throw malformed(location, "sha256 must contain 64 lowercase hexadecimal digits");
        }
        if (fields.get("type").chars().anyMatch(Character::isWhitespace)) {
            throw malformed(location, "type identity must not contain whitespace");
        }
        var principles = new HashMap<String, PrincipleReview>();
        for (String principle : PRINCIPLES) {
            String[] assessment = fields.get(principle).split(" \\| ", 2);
            if (assessment.length != 2
                    || !Set.of("pass", "fail", "not applicable").contains(assessment[0])
                    || assessment[1].isBlank()) {
                throw malformed(location, principle + " requires pass, fail or not applicable | concrete reasoning");
            }
            principles.put(principle, new PrincipleReview(assessment[0], assessment[1]));
        }
        return new ReviewEvidence(
                new SourceType(fields.get("source"), fields.get("type"), fields.get("sha256")),
                fields.get("responsibility"),
                fields.get("consumers"),
                principles,
                fields.get("findings"));
    }

    private static IllegalArgumentException malformed(String location, String reason) {
        return new IllegalArgumentException(location + ": malformed SOLID review: " + reason);
    }

    private static String fencePrefix(String line) {
        if (!line.startsWith("```") && !line.startsWith("~~~")) {
            return "";
        }
        int length = 3;
        while (length < line.length() && line.charAt(length) == line.charAt(0)) {
            length++;
        }
        return line.substring(0, length);
    }
}
