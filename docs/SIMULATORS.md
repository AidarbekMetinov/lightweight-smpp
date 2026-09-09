# Client and server simulators

## Required outcome

Deliver independently runnable SMPP client and server simulators to exercise
both endpoints under functional, stress, and sustained-load scenarios. Support
SMPP 3.4 and 5.0 according to the library's implemented feature inventory.
This document is a design plan; neither simulator exists yet.

Build an initial usable pair in [roadmap Step 13](ROADMAP.md), extend its scenarios
as library features arrive, and complete heavy-load measurement in Step 18.
Both tools follow the same [TDD](TDD.md) and per-type [SOLID review](SOLID.md)
requirements as the library.

## Packaging and responsibilities

Keep the library as a small consumable JAR. Add a separate Gradle application
subproject for simulator tooling when it is needed. The proposed application
has independently launchable `client` and `server` modes; each runs in its own
process and can target another implementation. A single simulator distribution
can contain both modes without imposing its dependencies on library users.

Use public library APIs for normal scenarios. Keep CLI parsing, immutable run
configuration, workload scheduling, server response policies, measurement, and
report output as cohesive components. Share a component only where the two
modes have the same contract. Introduce no production-library dependency on the
simulator.

Some malformed-wire scenarios cannot be expressed through a correctly validating
public API. Isolate those in a low-level test peer or fault adapter owned by the
tooling. Do not weaken normal PDU validation to generate invalid traffic.

## Endpoint behavior

| Client simulator | Server simulator |
| --- | --- |
| Open configurable numbers of connections and bind in selected modes. | Accept configured connection counts, versions, and bind modes. |
| Generate submissions and other supported client operations. | Apply deterministic accept, reject, throttle, delay, or no-response policies. |
| Receive deliveries and return configurable acknowledgements. | Generate deliveries and synthetic receipts with controllable rates and delays. |
| Exercise slow consumers, missed acknowledgements, and reconnects. | Exercise bind rejection, pending requests, disconnects, and slow reads. |
| Measure request outcomes and receipt correlation separately. | Measure inbound processing and outbound delivery outcomes separately. |

Synthetic receipts describe simulator behavior. Keep successful submission,
delivery acknowledgement, and reported delivery status as distinct events.
Do not treat a simulator acknowledgement as evidence of handset delivery.

Required configuration includes peer/listen address, protocol version, bind mode,
connection count and ramp, request-window size, payload size and encoding mix,
operation mix, rate or concurrency model, message count/duration, warmup, drain
deadline, request timeouts, deterministic seed, fault policy, and report path.
Add TLS configuration when the transport supports it. Keep secrets out of reports.

Document whether rates and limits apply globally or per connection. Give generated
traffic stable run and message identifiers so duplicates and missing outcomes can
be traced without keeping every message body in memory. Local smoke examples use
loopback addresses; external targets are explicitly configured.

## Workload models and bounds

Support two clearly named modes. An **arrival-rate mode** schedules messages
independently of earlier completions. A **fixed-concurrency mode** refills a
configured number of in-flight requests as responses arrive. The distinction
follows the open/closed workload model: arrival rate and concurrency answer
different capacity questions.[^1]

For SMPP, retain the configured request window in both modes. Arrival scheduling
must not bypass that limit. When an arrival cannot be admitted, record the cause
and apply a declared bounded queue, reject, or abort policy. Never allocate an
unbounded backlog just to maintain a nominal offered rate.

If the generator cannot keep up, record missed schedule slots and scheduling lag.
Do not silently lower the offered load or issue an uncontrolled catch-up burst.
Keep planned arrivals, admitted requests, actual writes, completed requests, and
successful protocol outcomes separately visible. Report partial or overloaded
runs as such.

Start with constant rates and controlled ramps. Add bursts, repeated ramp/hold
cycles, connection churn, and sustained soak scenarios as distinct configurations.
Reuse the same seed and configuration for comparisons; thread execution order
need not be identical for a run to have a reproducible input workload.

## Scenario coverage

| Scenario | Behavior to observe |
| --- | --- |
| Small smoke run | Bind, submit, delivery/receipt, acknowledgement, and graceful close for both profiles. |
| Steady load and ramp | Achieved throughput, growing latency, window occupancy, and the first saturated resource. |
| Bursts | Admission bounds, queue growth, rejected work, and return to normal operation. |
| Many connections | Bind throughput, fairness, per-connection memory, connection limits, and cleanup. |
| Bidirectional traffic | Simultaneous submission and delivery with control traffic still progressing. |
| Slow consumer or delayed handler | Backpressure, callback bounds, response deadlines, and isolation between connections. |
| Throttling and rejection | Status accounting and application policy without silently retrying ambiguous submissions. |
| Duplicate, late, or missing responses | Exactly one request completion, timeout handling, and released capacity. |
| Disconnect and connection churn | Pending outcomes, reconnect policy, descriptor/thread cleanup, and recovery. |
| Fragmented or invalid frames | Bounded parsing, correct failure handling, and continued service for other sessions. |
| Payload and receipt mix | Encoding boundaries, segmentation costs, raw payload correctness, and receipt correlation. |
| SMPP 5.0 additions | Broadcast and other declared capabilities under valid profiles and unsupported-profile handling. |
| TLS and keepalives | Handshake cost, encrypted steady load, idle peers, and shutdown under traffic. |
| Soak | Heap/native-memory trends, GC behavior, resource retention, and accounting over a long interval. |

Add each scenario when its underlying feature exists. The complete simulator
suite is part of the delivery scope, with support status recorded explicitly.

## Measurement contract

Use monotonic timestamps for elapsed durations within one process. Distinguish
schedule-to-admission delay, API-invocation-to-terminal-result latency, and
schedule-to-terminal-result latency. Define any write-to-response metric using
an observable write milestone; avoid pretending socket acceptance proves peer
receipt. Cross-host one-way timings need explicit clock-error treatment.

Record success, protocol rejection, local admission rejection, timeout, transport
failure, cancellation, and unfinished work at shutdown. Keep timeout and failure
counts beside latency distributions; a fast success-only percentile can conceal
many failed requests. State the sample population for every histogram.

Record p50, p95, p99, p99.9, maximum, sample count, units, histogram range, and
precision. HdrHistogram is a candidate simulator-only dependency because it
supports configurable range/precision and interval recording. Its documentation
also explains how stalled request generation can omit bad latency samples.[^2]
Verify version compatibility before adding it.

For arrival-rate runs, measure from the intended schedule as well as actual API
invocation and report missed arrivals explicitly. This helps expose coordinated
omission: a generator that stops producing requests during a stall can otherwise
make that stall look less significant. If histogram correction is offered, label
its model and inferred samples separately. Do not apply correction twice or use
it to hide admission failures.

Report CPU, heap and native/process memory, allocation/GC observations, threads,
open connections, pending requests, queue high-water marks, and network traffic
where the environment exposes them. Separate generator measurements from target
measurements. JDK Flight Recorder can help diagnose CPU, allocation, locks, and
socket delays; record its settings and assess its measurement overhead.[^3]

## Reproducible execution and reports

Use distinct warmup, measurement, and bounded drain phases. Define a measurement
cohort by its intended schedule times and follow those requests through drain.
Report completions during the measurement interval separately from final cohort
outcomes, so late completions do not distort throughput. Stop scheduling first,
then settle outstanding work or record it as unfinished before closing resources.

Produce a concise console summary plus machine-readable JSON and time-series
CSV output. Keep histogram data suitable for later aggregation and standalone
charts. Merge compatible histogram counts to calculate combined percentiles;
do not average per-connection or per-process percentiles.

Each report should include the run identifier, exact library/simulator revision,
configuration and seed, profile/operation mix, JDK/JVM settings, OS, CPU and memory,
host placement, network/TLS settings, phase durations, metrics, and pass/fail
criteria. Report violated criteria and incomplete runs through a nonzero exit
status. Define thresholds from the agreed workload before interpreting a result.

First compare separate processes on one host, then move the generator and target
to separate hosts when needed. Monitor both sides and check generator headroom.
If a single generator saturates, use additional independent processes with
distinct run partitions and compatible aggregation. Distributed orchestration
is a later addition only if simple processes cannot meet the workload.

Use the two in-house simulators for repeatable stress. Also run the library client
against an independent server and its server against an independent client.
Agreement between two endpoints sharing the same codec can preserve the same bug.
Keep performance evidence separate from protocol conformance evidence.

## TDD, SOLID, and build behavior

Test configuration, schedules, bounded admission, counters, known latency samples,
histogram aggregation, fault policies, thresholds, and shutdown using controlled
time and small deterministic fixtures. Observe a relevant failure before adding
each behavior. Use small real-process checks for the CLI and networking boundary.

Heavy-load runs evaluate capacity on specified hardware. Avoid making ordinary
unit tests depend on a machine-specific TPS or p99 threshold. Turn any behavioral
bug discovered under load into a deterministic failing regression test where
possible before fixing it; preserve a bounded integration reproduction when
the defect requires real I/O or concurrency.

Review every created or changed simulator type and test helper against all five
SOLID principles. A scheduler should not format reports, and a response policy
should not parse CLI arguments. Keep the set of extension interfaces driven by
real scenarios and use contract tests for shared abstractions.

Cache simulator compilation, dependencies, and deterministic verification through
Gradle's normal mechanisms. Actual load runs must always execute when requested:
do not reuse task outputs or mark previous performance results as a new run.
Keep heavy scenarios outside the default quick `check` lifecycle. Store generated
artifacts under ignored build output directories and document selected findings
under `docs/`.

## Sources

[^1]: Gatling. [Workload models](https://docs.gatling.io/testing-concepts/workload-models/), open and closed load definitions, accessed 9 September 2026. This informs the load model; Gatling itself is not a selected project dependency.
[^2]: HdrHistogram contributors. [HdrHistogram README](https://github.com/HdrHistogram/HdrHistogram/blob/master/README.md), range, precision, interval recording, and corrected/raw samples, accessed 9 September 2026.
[^3]: Oracle. [Troubleshoot Performance Issues Using Flight Recorder](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshoot-performance-issues-using-jfr.html), Java 21 documentation, accessed 9 September 2026.
