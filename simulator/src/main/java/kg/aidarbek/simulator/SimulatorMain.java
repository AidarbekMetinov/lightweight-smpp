package kg.aidarbek.simulator;

/** Independent client/server development workload launcher. */
public final class SimulatorMain {
    private SimulatorMain() {}
    /**
     * Runs one independent workload and exits nonzero on invalid inputs, failed criteria or incomplete cleanup.
     * @param arguments mode followed by explicit {@code --name=value} options, or {@code --help}
     */
    public static void main(String[] arguments) {
        int status;
        try {
            if (arguments.length == 1 && arguments[0].equals("--help")) {
                System.out.println(
                        "Usage: simulator client|server --revision=<full Git SHA> [--name=value ...]\n"
                                + "Operations: client submit/data/none; server deliver/data/none. Versions: 3.4, 5.0; bind: tx/rx/trx.\n"
                                + "Load: --model=arrival|concurrency --rates=10,100,10 --count=100 --duration=PT10S --warmup=PT0S --drain=PT30S\n"
                                + "Bounds: --connections=1 --window=32 --payload=160 --timeout=PT2S --connect-interval=PT0S\n"
                                + "Peer: --host=127.0.0.1 --port=2775 --source=1000 --destination=2000\n"
                                + "Faults: --seed=1 --reject=0 --delay=0 --stall=0 --delay-duration=PT0.2S --reject-status=0x58 --disconnect-after=0\n"
                                + "Reports: --report=<fresh directory> --run-id=<name> --expect-failures=false --minimum-rate-ratio=0 --p99-ms=0\n"
                                + "Credentials: SMPP_SYSTEM_ID and SMPP_PASSWORD environment variables (demo defaults sim/sim).\n"
                                + "Each process runs independently. Server prints READY after binding its listener. Durations use ISO-8601.");
                return;
            }
            var config = SimulatorArguments.parse(arguments);
            status = SimulatorRun.execute(
                    config,
                    System.getenv().getOrDefault("SMPP_SYSTEM_ID", "sim"),
                    System.getenv().getOrDefault("SMPP_PASSWORD", "sim"),
                    System.out::println);
        } catch (IllegalArgumentException invalid) {
            System.err.println("Invalid simulator configuration. Use --help and docs/SIMULATORS.md.");
            status = 2;
        } catch (Exception failure) {
            System.err.println(
                    "Simulator could not complete: " + failure.getClass().getSimpleName());
            status = 1;
        }
        if (status != 0) System.exit(status);
    }
}
