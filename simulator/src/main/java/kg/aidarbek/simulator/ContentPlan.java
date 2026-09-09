package kg.aidarbek.simulator;

/** A deterministic traffic variant and its receive-side content check. Implementations must be bounded. */
interface ContentPlan {
    TrafficContent next(long index);

    void validate(TrafficContent content);

    default int incompleteAssemblies() {
        return 0;
    }
}
