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
                                + "Operations: client submit/data/query/cancel/replace/multi/broadcast/query-broadcast/cancel-broadcast/none; server deliver/data/none. Versions: 3.4, 5.0; bind: tx/rx/trx. Broadcast operations require 5.0 TX/TRX and raw content.\n"
                                + "Load: --model=arrival|concurrency --rates=10,100,10 --holds=PT3S,PT4S,PT3S --count=100 --duration=PT10S --warmup=PT0S --drain=PT30S\n"
                                + "Pacing: --scenario=custom --spin=PT0.0001S --sample=PT1S; arrivals skipped by a late generator are counted without catch-up bursts.\n"
                                + "Bounds: --connections=1 --window=32 --payload=160 --timeout=PT2S --connect-interval=PT0S\n"
                                + "Content: --content=raw|gsm7|ucs2|sar|receipt|receipt-flexible|receipt-tlv (see docs/SIMULATORS.md for size/operation rules).\n"
                                + "Peer: --host=127.0.0.1 --port=2775 --source=1000 --destination=2000\n"
                                + "Faults: --seed=1 --reject=0 --delay=0 --stall=0 --delay-duration=PT0.2S --reject-status=0x58 --disconnect-after=0\n"
                                + "Long scenarios: --churn-rate=0 --churn-count=0 --consumer-delay=PT0S --verify-fault-mix=false --fault-tolerance=0.05\n"
                                + "Reports: --report=<fresh directory> --run-id=<name> --expect-failures=false --minimum-rate-ratio=0 --p99-ms=0\n"
                                + "Resource criteria: --max-rss-mib=0 --max-heap-mib=0 --max-fd-growth=-1 --max-thread-growth=-1 (zero memory / -1 growth disables).\n"
                                + "Lifecycle: --keepalive-idle=PT30S --keepalive-timeout=PT5S; optional client --reconnect-attempts=3 --reconnect-delay=PT1S (cannot combine with churn).\n"
                                + "TLS: --tls=on --tls-key-store=<PKCS12> --tls-trust-store=<PKCS12> --tls-peer-name=<certificate identity> --tls-handshake-timeout=PT5S --tls-client-certificate=false\n"
                                + "TLS secrets: --tls-key-password-env=<name> --tls-trust-password-env=<name>; defaults SMPP_TLS_KEYSTORE_PASSWORD/SMPP_TLS_TRUSTSTORE_PASSWORD.\n"
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
