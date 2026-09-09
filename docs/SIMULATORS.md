# Client and server simulators

## Required outcome

Deliver independently runnable SMPP client and server simulators to exercise
both endpoints under functional, stress, and sustained-load scenarios. Support
SMPP 3.4 and 5.0 according to the library's implemented feature inventory.
Step 13 supplies the first usable pair in the separate `simulator` application.
Submission, delivery and bidirectional `data_sm` traffic run through the public
endpoint API. Step 14 adds query/cancel/replace/multi traffic and finite alert/
outbind checks. Step 15 adds explicit encoding, SAR and receipt content fixtures.
Step 16 adds 5.0 broadcast submission, query and cancellation. The broader
scenarios below remain the staged target for Steps 17–18; this pair does not establish production capacity or independent-peer
interoperability.

[Workload criteria](WORKLOADS.md) now define the Step 1 provisional profiles,
measurement environment, and acceptance/accounting rules. Production workload
requirements remain open. [API contracts](API.md) define the public behavior the
simulators exercise, and [the test plan](TEST_PLAN.md) identifies their first
deterministic verification scenarios.

The [Step 13 review](reviews/0012-simulators.md) records actual tests and selected
fresh measurements. Extend scenarios as library features arrive, and complete
heavy-load measurement in Step 18.
Both tools follow the same [TDD](TDD.md) and per-type [SOLID review](SOLID.md)
requirements as the library.

## Run the tools

Build the launchers once, then run each mode in its own terminal:

```shell
./gradlew :simulator:installDist --console=plain
simulator/build/install/simulator/bin/simulator --help
```

The following commands use the demo credentials `sim`/`sim` and SMPP 3.4.
Supply `SMPP_SYSTEM_ID` and `SMPP_PASSWORD` in each process environment to change
them; credential values are absent from reports. `--revision` is required and
is explicitly recorded as caller-supplied provenance. A dirty source revision
can be written as a full Git SHA followed by `+` and a SHA-256 source fingerprint.
The report also computes SHA-256 identities of the actual simulator, library and
histogram executable code.

```shell
simulator/build/install/simulator/bin/simulator server --revision="$(git rev-parse HEAD)" --report=build/runs/smoke-server --duration=PT10S --drain=PT1S
```

```shell
simulator/build/install/simulator/bin/simulator client --revision="$(git rev-parse HEAD)" --report=build/runs/smoke-client --count=100 --rates=10 --duration=PT10S --drain=PT1S
```

Reports require a fresh directory; an existing `report.json` or `resources.csv`
is never overwritten. The server prints `READY port=...` after opening its
listener; `--port=0` selects an ephemeral server port. The client requires a
nonzero peer port. Both default to `127.0.0.1:2775`. The installed launchers use
Java with `-Xms256m -Xmx512m`; normal Gradle caching remains enabled. Launching
the executable always performs a fresh run.

| Input | Implemented meaning |
| --- | --- |
| `--version=3.4` or `5.0`; `--bind=tx`, `rx`, `trx` | Requested/advertised profile and bind role. Incompatible originating operations fail before endpoint allocation. |
| `--operation=submit`, `deliver`, `data`, `query`, `cancel`, `replace`, `multi`, `broadcast`, `query-broadcast`, `cancel-broadcast`, `none` | Client submission/management/multiple-submission, server delivery, profile-permitted data in either direction, or receive-only. Broadcast variants require a 5.0 client in TX/TRX mode with raw content. Both peers register the applicable typed handlers. |
| `--connections=1`; `--window=32` | Fixed bound cohort and per-session request window. A run does not replace disconnected sessions or retry requests. |
| `--connect-interval=PT0S` | Spacing between explicit client connection attempts. Configure the same value on the server when waiting for a slowly arriving cohort. |
| `--model=arrival`; `--rates=10,100,10` | Aggregate independent arrivals across the cohort, in equal-duration rate steps. The last step includes any duration remainder. |
| `--model=concurrency` | Refill only the finite originating window. Omit `--rates`; no independent offered rate is claimed. |
| `--count=100`; `--duration=PT10S` | Measurement count cap and scheduling interval. Warmup uses a separate cohort, the first arrival rate, and its own drain. |
| `--warmup=PT0S`; `--drain=PT30S`; `--timeout=PT2S` | Explicit warmup, bounded completion observation and request deadline. Durations use ISO-8601. |
| `--payload=160`; `--seed=1` | Deterministic raw octets, with size 0–65,535. Short messages above 254 octets move to `message_payload`; `data_sm` always uses that TLV. Raw data declares coding 4 (8-bit binary). |
| `--source=1000`; `--destination=2000` | Non-NUL ASCII traffic addresses, 1–20 characters. |
| `--reject=5`; `--delay=10`; `--stall=1` | Disjoint receiver decision percentages. They sum to at most 100; the rest accept normally. |
| `--delay-duration=PT0.2S`; `--reject-status=0x58` | Delay duration and numeric nonzero rejection status. |
| `--disconnect-after=100` | Close each receiving connection on that application-observed request count. Zero disables it. |
| `--expect-failures=true` | Permit unsuccessful but accounted outcomes. It never excuses aborted/unfinished work, content errors or incomplete cleanup; it does not verify a statistical fault mix. |
| `--minimum-rate-ratio=0.99`; `--p99-ms=100` | Optional successful measurement-completion ratio and schedule-to-observation p99 gates. Defaults of zero disable these performance gates. |

For delivery-only traffic, set the server operation to `deliver` and the client
to `none`, with RX or TRX binding. Set both operations to `data` for bidirectional
data traffic; configure half the intended combined rate in each process. A
receive-only process keeps serving through its warmup, measurement and drain
durations after the required cohort arrives. An originating process also serves
incoming requests, then closes after its own measured cohort drains. Give the
receiving peer enough lifetime for the sender's warmup and drain.

Limits are checked before networking: at most 4,096 connections, 65,536 combined
window slots, 64 rate steps, one billion measurement requests, and an estimated
128 MiB outstanding payload/frame budget. Each rate step and nonzero arrival
warmup lasts at least 1 ms. Measurement and warmup are bounded to one day each;
request and drain durations are bounded to one hour each. These guards bound
tool inputs; they do not promise a particular heap or native-memory footprint.

The endpoint uses a 1 MiB maximum frame, a finite per-session byte budget of at
least 1 MiB, at most 64 active handler invocations, a bounded handler queue and
bounded replies. Delayed/stalled decisions and retained content streams also
have explicit finite limits. A saturated generator emits only the newest due
arrival and records the skipped ones, without a catch-up burst or backlog.

Fault selection hashes the configured seed with the incoming per-connection
sequence number. The same sequence on different connections selects the same
decision. Percentages describe hash buckets, not an exact percentage of a small
sample. A stalled decision withholds application completion until tool cleanup;
the library's handler deadline still applies and can generate a negative reply.
Delay release is polled by the tool owner, so scheduling stalls can extend it.
The simulator never weakens protocol validation to generate malformed wire data.

## Explicit content fixtures

The [broadcast fixture](BROADCAST.md) sends one originating PDU with two named
area descriptors and up to 65,535 raw payload bytes. Query responses supply two
synthetic area results; no persistent CBC, geographic expansion or radio-network
delivery is simulated. Originating PDU counts do not represent handset counts.
The same bounded reply/fault controller handles these requests. Point-to-point
SAR and receipt content flags do not apply to broadcast bodies.

`--content=raw` remains the default binary DCS4 workload. Both peers can select
`gsm7`, `ucs2`, `sar`, `receipt`, `receipt-flexible` or `receipt-tlv`. Select the
same content, size and seed at both ends. [The helper guide](MESSAGE_HELPERS.md)
defines exact byte/field bounds and supported operations:

| Variant | Payload option and behavior |
| --- | --- |
| `gsm7` | Exact encoded octets using the explicitly agreed unpacked GSM convention at coding 0; no septet packing. |
| `ucs2` | Exact even encoded octets, coding 8; unsupported surrogate/non-BMP characters are rejected by the helper. |
| `sar` | One logical 161–39015-octet GSM fixture, split at 153 encoded octets into at most 255 parts. Each connection receives its own part sequence and validates isolated bounded assembly. |
| `receipt` | Final receipt text-field length 0–20, plus canonical example metadata. |
| `receipt-flexible` | Final text-field length 0–2000, with provider fields and deliberately missing optional fields. |
| `receipt-tlv` | Payload must be 0; ID/state/network-error TLVs carry the fixture. |

Text/SAR fixtures support submit/deliver/data/multi. Receipt fixtures are an
explicit MC/server-originated deliver/data scenario; receive-only clients validate
them. This tool policy does not restrict SMPP 5.0's additional ESME-originated
receipt message types. Helper fixtures do not support replacement because that
command lacks encoding/receipt fields; raw replacement retains its documented
assumption about the original message.

Invalid names, sizes, scenario pairs and originating roles fail before reports
or endpoints are created. Defaults are not silently truncated: for example,
`--content=receipt` also requires an explicit `--payload` at most 20. Raw payload
access remains available through the public library APIs. SAR repetitions reuse
one fixed fixture reference and retained duplicate history; they are not distinct
logical message counts. A partial received fixture makes the run fail at cleanup.
Automatic submission-to-receipt correlation remains later tooling work.

## Common-operation runs

Query, cancel and multi require an ESME TX/TRX connection. Replace requires TX
under 3.4 and TX/TRX under 5.0; configure `--bind=tx` on both peers for a common
profile example. `--operation=replace --version=3.4 --payload=255` is rejected
before reports or networking. Raw replacement assumes an existing binary DCS4
message because the command has no encoding field. Query/cancel use synthetic
IDs without message content; multi contains one SME and one distribution list.
The receiver reports deterministic decisions rather than real stored messages.

[Common-operation documentation](COMMON_OPERATIONS.md) includes separate finite
`CommonOperationSmoke alert|outbind 3.4|5.0` commands for one-way operations.
Their local-write, notification and authentication observations are not fabricated
paired responses or throughput measurements.

## Current reports and limitations

`report.json` contains exact non-secret inputs, executable fingerprints, JVM/OS
observations, warmup and measurement accounting, HDR distributions and buckets,
receiver decisions, resource snapshots, criteria and cleanup status.
`resources.csv` streams elapsed monotonic time, heap, RSS, process CPU, cumulative
GC time and descriptor observations. Linux-specific unavailable observations
use `-1`; thread snapshots count platform threads, not virtual threads. Heap and
descriptor peaks are sampled; Linux peak RSS is the process high-water reading.
No forced collection, allocation-rate measurement or network-byte measurement is
implied by these observations.

Sender accounting follows the [workload equations](WORKLOADS.md). Measurement
completions are separate from outcomes observed during drain. Warmup failure,
interrupted generation and other observed execution aborts preserve partial
counts, mark remaining calls unfinished, and report whether measurement began.
Exit codes are 0 for passed criteria, 1 for failed execution/criteria/cleanup,
and 2 for invalid configuration. A persistent output failure can prevent a report
file from being written and still produces a nonzero process exit.

Latency ends when the single tool owner observes the library's terminal snapshot;
polling and generator delay are included. Invocation latency also includes
payload generation in the operation adapter. Histograms include all admitted
terminal outcomes except unfinished requests, use microseconds rounded upward,
three significant digits and an explicit one-hour upper range. Overflow samples
are counted separately. Skipped arrivals receive no invented latency samples.
Request handles expose transmission certainty rather than a local write-complete
timestamp, so no write-to-response histogram is claimed.

Receiver counts describe application-observed handler requests before receiver
closure, including peer warmup/drain. They exclude frames rejected by decoding
or endpoint capacity before the handler runs. Raw content validation checks the
configured shape/length, not handset delivery or a retained history of message
identities. Full duplicate/receipt correlation, malformed-wire adapters, churn,
slow readers, distributed coordination and sustained heavy-load studies remain
later work. Two endpoints using this library are functional/performance test
peers; independent interoperability remains Step 19.

## Packaging and responsibilities

The library remains a small consumable JAR. The separate Gradle application
subproject contains simulator tooling. The application
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
precision. HdrHistogram 2.2.2 is pinned as a simulator-only dependency; it
supports configurable range/precision and interval recording. Its documentation
also explains how stalled request generation can omit bad latency samples.[^2]
The selected release builds and runs on the project's Java 21 baseline.

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
