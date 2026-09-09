package kg.aidarbek.smpp.codec;

/** A structurally malformed or truncated field within an already bounded input block. */
public final class FieldCodecException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a field failure. Diagnostic messages must exclude credentials and payload contents.
     *
     * @param message structural failure description
     */
    public FieldCodecException(String message) {
        super(message);
    }
}
