package kg.aidarbek.smpp.message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable parsed text plus raw field values; no inferred dates, delivery states or ID conversions. */
public final class DeliveryReceipt {
    private final String rawText;
    private final Map<String, String> fields;

    DeliveryReceipt(String rawText, Map<String, String> fields) {
        this.rawText = rawText;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /**
     * Returns the exact supplied text, including spelling and spacing.
     * @return raw text
     */
    public String rawText() {
        return rawText;
    }
    /**
     * Returns ordered raw values keyed by canonical lowercase labels.
     * @return immutable fields
     */
    public Map<String, String> fields() {
        return fields;
    }
    /**
     * Returns a raw value without inventing absent fields.
     * @param label canonical label
     * @return optional raw value
     */
    public Optional<String> field(String label) {
        return Optional.ofNullable(fields.get(label));
    }

    @Override
    public String toString() {
        return "DeliveryReceipt[fieldCount=" + fields.size() + ", text=redacted]";
    }
}
