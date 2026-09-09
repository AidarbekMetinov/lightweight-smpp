package kg.aidarbek.smpp.endpoint;

import java.util.Objects;

/** Immutable endpoint message policy and optional application handlers.
 * @param options explicit finite execution and reply bounds
 * @param handlers immutable optional service registrations */
public record ExchangeConfig(ExchangeOptions options, EndpointHandlers handlers) {
    /** Requires explicit immutable policy and registrations. */
    public ExchangeConfig {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(handlers, "handlers");
    }
    /** Preserves absent-handler negatives while enabling implemented sending capabilities.
     * @return default finite bounds and an empty handler registry */
    public static ExchangeConfig defaults() {
        return new ExchangeConfig(ExchangeOptions.defaults(), EndpointHandlers.empty());
    }
}
