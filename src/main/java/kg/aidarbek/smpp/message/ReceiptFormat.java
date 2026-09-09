package kg.aidarbek.smpp.message;

/** Explicit library parsing profiles for vendor-specific textual delivery receipts. */
public enum ReceiptFormat {
    /** Complete lowercase example fields, three-digit counts/error and ten-digit raw dates. */
    EXAMPLE,
    /** Optional fields, case-insensitive labels and retained unknown provider fields/raw values. */
    FLEXIBLE
}
