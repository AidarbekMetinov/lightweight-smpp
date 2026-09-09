package kg.aidarbek.smpp.endpoint;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Single registration boundary for implemented paired endpoint operations. */
final class OperationCatalog {
    private static final List<Operation<?, ?>> OPERATIONS = Stream.of(
                    MessageOperations.all(), CommonOperations.all(), BroadcastOperations.all())
            .flatMap(List::stream)
            .toList();

    private OperationCatalog() {}

    static List<Operation<?, ?>> all() {
        return OPERATIONS;
    }

    static Optional<Operation<?, ?>> find(long commandId) {
        return all().stream()
                .filter(operation -> operation.commandId() == commandId)
                .findFirst();
    }
}
