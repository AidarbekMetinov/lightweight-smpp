package kg.aidarbek.simulator;

/** Starts the complete simulator on the same owner kind as its standalone launcher. */
final class SimulatorTestOwner {
    private SimulatorTestOwner() {}

    static Thread start(Runnable action) {
        return Thread.ofPlatform().name("simulator-test-owner").start(action);
    }
}
