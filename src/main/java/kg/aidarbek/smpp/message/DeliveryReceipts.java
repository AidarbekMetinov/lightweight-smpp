package kg.aidarbek.smpp.message;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Explicit bounded receipt text interpretation; no PDU classification or request correlation. */
public final class DeliveryReceipts {
    private static final List<String> ORDER =
            List.of("id", "sub", "dlvrd", "submit date", "done date", "stat", "err", "text");
    private static final Pattern LABEL =
            Pattern.compile("(?:^| +)([A-Za-z][A-Za-z0-9_]*(?: date)?):", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBEDDED_LABEL =
            Pattern.compile(" +[A-Za-z][A-Za-z0-9_]*(?: date)?:", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]*(?: date)?");

    private DeliveryReceipts() {}

    /**
     * Parses at most 4096 UTF-16 units and 32 fields using the selected profile.
     *
     * @param text original externally decoded payload
     * @param format explicit grammar/missing-field policy
     * @return raw text and fields
     */
    public static DeliveryReceipt parse(String text, ReceiptFormat format) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(format, "format");
        if (text.length() > 4096) throw new IllegalArgumentException("Receipt exceeds 4096 UTF-16 units");
        if (text.isEmpty() && format == ReceiptFormat.FLEXIBLE) return new DeliveryReceipt(text, Map.of());
        var matcher = LABEL.matcher(text);
        Map<String, String> fields = new LinkedHashMap<>();
        if (!matcher.find() || matcher.start() != 0)
            throw new IllegalArgumentException("Receipt must begin with a field label");
        String label = normalize(matcher.group(1), format);
        int valueStart = matcher.end();
        while (true) {
            if (label.equals("text") || !matcher.find()) {
                put(fields, label, text.substring(valueStart));
                break;
            }
            put(fields, label, text.substring(valueStart, matcher.start()));
            label = normalize(matcher.group(1), format);
            valueStart = matcher.end();
        }
        validate(fields, format);
        return new DeliveryReceipt(text, fields);
    }

    /**
     * Builds canonical field order from explicit raw values; never invents missing values.
     * Non-text values with edge spaces or embedded field labels are rejected, without trimming.
     * Text is emitted last and preserves its complete content.
     *
     * @param fields caller field values
     * @param format explicit profile
     * @return bounded receipt text
     * @throws IllegalArgumentException for an ambiguous field or a profile/allocation-bound violation
     */
    public static String build(Map<String, String> fields, ReceiptFormat format) {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(format, "format");
        Map<String, String> copied = new LinkedHashMap<>();
        int outputLength = 0;
        for (var entry : fields.entrySet()) {
            String rawLabel = Objects.requireNonNull(entry.getKey(), "label");
            if (rawLabel.length() > 32) throw new IllegalArgumentException("Receipt field bound exceeded");
            String label = normalize(rawLabel, format);
            String value = Objects.requireNonNull(entry.getValue(), "value");
            long nextLength = (long) outputLength + label.length() + value.length() + (copied.isEmpty() ? 1 : 2);
            if (nextLength > 4096) throw new IllegalArgumentException("Receipt exceeds 4096 UTF-16 units");
            outputLength = (int) nextLength;
            if (!label.equals("text") && (value.startsWith(" ") || value.endsWith(" ")))
                throw new IllegalArgumentException("Canonical non-text fields cannot have edge spaces");
            put(copied, label, value);
        }
        validate(copied, format);
        StringBuilder text = new StringBuilder();
        for (String label : ORDER) {
            if (!label.equals("text") && copied.containsKey(label)) append(text, label, copied.get(label));
        }
        for (var entry : copied.entrySet())
            if (!ORDER.contains(entry.getKey())) append(text, entry.getKey(), entry.getValue());
        if (copied.containsKey("text")) append(text, "text", copied.get("text"));
        return text.toString();
    }

    private static String normalize(String label, ReceiptFormat format) {
        return format == ReceiptFormat.FLEXIBLE ? label.toLowerCase(Locale.ROOT) : label;
    }

    private static void append(StringBuilder target, String label, String value) {
        if ((long) target.length() + label.length() + value.length() + (target.isEmpty() ? 1 : 2) > 4096)
            throw new IllegalArgumentException("Receipt exceeds 4096 UTF-16 units");
        if (!target.isEmpty()) target.append(' ');
        target.append(label).append(':').append(value);
    }

    private static void put(Map<String, String> fields, String label, String value) {
        if (fields.size() == 32 || label.length() > 32)
            throw new IllegalArgumentException("Receipt field bound exceeded");
        if (fields.putIfAbsent(label, value) != null) throw new IllegalArgumentException("Duplicate receipt field");
    }

    private static void validate(Map<String, String> fields, ReceiptFormat format) {
        for (var entry : fields.entrySet()) {
            String label = entry.getKey();
            String value = entry.getValue();
            if (!KEY.matcher(label).matches() || label.length() > 32)
                throw new IllegalArgumentException("Invalid receipt label");
            if (!label.equals("text") && EMBEDDED_LABEL.matcher(value).find())
                throw new IllegalArgumentException("A non-text value contains an ambiguous field label");
        }
        if (format == ReceiptFormat.FLEXIBLE) return;
        if (fields.size() != ORDER.size() || !fields.keySet().containsAll(ORDER))
            throw new IllegalArgumentException("Example receipt requires its eight explicit fields");
        for (String label : ORDER) Objects.requireNonNull(fields.get(label), "receipt field value");
        for (String label : List.of("sub", "dlvrd", "err")) digits(fields.get(label), 3);
        for (String label : List.of("submit date", "done date")) digits(fields.get(label), 10);
        String id = fields.get("id");
        if (id.isEmpty() || id.length() > 64 || id.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException(
                    "Example receipt ID must be a nonempty opaque token of at most 64 units");
        if (fields.get("stat").length() != 7 || fields.get("text").length() > 20)
            throw new IllegalArgumentException("Example state/text length is invalid");
    }

    private static void digits(String value, int length) {
        if (value.length() != length || value.chars().anyMatch(character -> character < '0' || character > '9'))
            throw new IllegalArgumentException("Example numeric field has an invalid width or digit");
    }
}
