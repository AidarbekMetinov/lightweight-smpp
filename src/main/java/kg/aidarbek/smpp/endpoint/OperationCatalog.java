package kg.aidarbek.smpp.endpoint;

import java.util.List;
import java.util.Optional;

/** Single registration boundary for implemented paired endpoint operations. */
final class OperationCatalog {
    private OperationCatalog() {}

    static List<Operation<?, ?>> all() {
        return MessageOperations.all();
    }

    static Optional<Operation<?, ?>> find(long commandId) {
        return all().stream()
                .filter(operation -> operation.commandId() == commandId)
                .findFirst();
    }
}
