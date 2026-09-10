# Simulator workload criteria

Step 1 baseline, 9 September 2026. Production connection counts, throughput, and
latency requirements have not been supplied. The numerical profiles below are
**provisional development targets**, chosen to make future measurements concrete.
They are not measured capacity, production guarantees, or user-approved SLAs.
Update them when actual workload requirements are available.

Use [the simulator design](SIMULATORS.md) for measurement semantics and
[the API contracts](API.md) for admission, timeouts, and lifecycle behavior.
Step 13 supplies runnable simulators and the fresh local measurements below.
The remaining heavy-load profiles continue in Step 18.

## Initial measurement environment

The current workstation was inspected read-only during Step 1:

| Attribute | Observed value |
| --- | --- |
| CPU | Intel Core i5-11400F, 12 logical CPUs visible |
| Physical memory visible to Linux | Approximately 31.2 GiB |
| Swap configured | 8 GiB |
| Runtime | OpenJDK 21.0.12, build 21.0.12+8 |
| Kernel/architecture | Linux 7.0.0-31-generic, x86_64 |

This is a development baseline. Record the current environment again for each
run. Run generator and target as separate JVMs, initially on this workstation.
Use separate hosts when needed to distinguish network, generator, and target
limits. A local loopback result is not a production network result.

Start each process with a 256 MiB initial heap and 512 MiB maximum heap. Use the
JDK's default collector and record its selected configuration. Set an initial
per-process RSS target of 1 GiB and report native memory separately from heap.
Larger explicit profiles may revise the budget; do not silently enlarge it after
a failed run. No system or JVM settings are changed by this document.

## Shared profile settings

Unless a row overrides them, use TRX binding, a request window of 32 per session
and originating direction, an outbound byte limit of 1 MiB per session, and a
maximum PDU size of 1 MiB. Bound active inbound application work to 32 requests
per session and 256 globally, with a separately bounded control-response path.
Choose and report the exact executor implementation during transport work.

Use 2-second request deadlines, 5-second connection deadlines, 10-second bind
deadlines, a 30-second drain bound, and deterministic seed `1`. Warmup and
measurement counts are separate. The initial payload is 160 raw octets with
explicit binary data coding. Receipt generation is disabled unless selected.

Rates below mean aggregate **originating SMPP requests per second across all
sessions**, excluding responses, binds, and keepalives. Report those additional
PDUs separately. A submission and a generated delivery are two requests; one
must not disappear from accounting because it was triggered by the other.

Run submission-only and delivery-only variants to target the server and client
respectively. The bidirectional variant divides the configured aggregate rate
equally between the two directions. All rows run separately under 3.4 and 5.0;
add TX/RX variants only where the operation inventory permits their direction.

## Initial scenario profiles

| ID | Purpose | Sessions | Offered load | Warmup / measurement | Acceptance category |
| --- | --- | --- | --- | --- | --- |
| `W-SMOKE` | Verify end-to-end accounting and lifecycle | 1 | 10/s; 100 measurement requests | 0 / 10 s | Functional; no performance claim |
| `W-BASE` | Establish a repeatable low-load baseline | 10 | 1,000/s constant arrival rate | 30 s / 120 s | Healthy target |
| `W-TARGET` | Initial substantial-load goal | 100 | 10,000/s constant arrival rate | 30 s / 300 s | Provisional healthy target |
| `W-RAMP` | Locate saturation and recovery | 100 | 1k, 5k, 10k, 20k/s, then 1k/s | 30 s / 60 s at each level | Characterization; report first unmet level |
| `W-WINDOW` | Measure fixed-concurrency behavior | 100 | Windows 1, 8, 32; no claimed offered rate | 30 s / 120 s each | Characterization |
| `W-BURST` | Exercise bounded overload | 100 | 1k/s baseline; three 10 s bursts at 20k/s, 30 s apart | 30 s / 180 s | Recovery and accounting |
| `W-CONNECTIONS` | Connection footprint and cleanup | 1,000 | Ramp 25 binds/s, then 1k requests/s total | Ramp / 300 s | Connection/resource target |
| `W-CHURN` | Repeated binding and closure | Up to 100 active | Replace 10 sessions/s; 1k requests/s total | 30 s / 300 s | Recovery and resource target |
| `W-FAULT` | Predictable slow/error paths | 100 | 1k/s with a recorded fault mix | 30 s / 300 s | Expected-outcome target |
| `W-SOAK` | Long-term resource stability | 100 | 5k/s constant arrival rate | 60 s / 60 min | Provisional healthy/resource target |

These are required configurable scenarios, not instructions to run a long test
on every commit. Deliver `W-SMOKE`, `W-BASE`, and a short ramp with the first
simulators, then add the remaining cases as their dependencies are implemented.

Add payload variants of 0, 32, and 160 raw octets and a 4 KiB `message_payload`
where permitted. Add an explicit receipt run with one synthetic receipt per
successful submission, and a 50/50 bidirectional run. Measure TLS on/off separately
when TLS is available. Give broadcast, multiple-destination, and other command
mixes distinct scenario names and report their true PDU/message counts.

## Healthy-run criteria

For `W-BASE`, the provisional `W-TARGET`, and `W-SOAK`, use these initial goals:

- Achieve at least 99% of the configured originating-request rate during the
  measurement interval, with no unreported arrivals or generator backlog.
- Have zero unexpected local rejections, protocol rejections, transport failures,
  timeouts, duplicate terminal completions, or unfinished measurement-cohort
  requests after drain in a fault-free run.
- Keep p99 schedule-to-terminal-result latency at or below 100 ms. Record API
  request latency separately. Report p50, p95, p99.9, maximum, and sample counts;
  mark extreme percentiles as unsuitable for comparison when samples are sparse.
- Stay within configured request, byte, callback, connection, and memory bounds.
  Return active session and pending-request counters to zero after shutdown.

Treat inability to meet a provisional throughput or latency goal as a measurement
to investigate. Do not weaken correctness checks or claim the target was achieved.
The production release performance requirement remains open until its workload
and hardware are established.

### Required 99% planned-success floor

The user additionally requires at least 99% success. For a healthy originating
`W-BASE`, `W-TARGET` or `W-SOAK` report, both of these must hold:

- Eventual measurement-cohort `SUCCESS` outcomes / all planned measurement
  requests is at least 0.99. Skips and rejections stay in the denominator.
- Successful completions within the measurement interval / its observed duration
  reaches at least 99% of the configured offered rate.

Only skipped arrivals may consume the 1% budget. Unexpected rejected or admitted
failures, incomplete phases, unfinished requests, invalid accounting, incomplete
cleanup, and violated resource or latency bounds still fail the assessment.
Warmup has its own complete accounting. A receiving-only role has no originating
success denominator and must still pass the pair's receive/cleanup checks.

The helper records this policy as `healthyPlannedSuccess`, separately from the
existing strict zero-skip verdict and exit code. Shortened or rate-overridden
runs keep their diagnostic label. See [report interpretation](LOAD_TESTING.md)
and [the implementation review](reviews/0020-load-efficiency.md) for the exact
scope, test evidence and current qualification record.

## Fault and overload criteria

For the first `W-FAULT` mix, assign mutually exclusive outcomes by deterministic
request identity: 5% throttled, 10% delayed by 200 ms, 1% with no response, and 84%
ordinary success. Run disconnect-after-100-requests, slow reader, duplicate/late
response, malformed frame, and stalled handler as separate named variants before
combining them. This makes expected outcomes explainable.

Some faults require the tooling-owned low-level peer because production APIs
correctly prevent invalid PDUs or eventually time out a handler. Record which
adapter generated the fault. Correlate planned injections with attempted,
actually injected, and observed outcomes so admission rejection does not count
as a fault successfully delivered to the peer.

Under intentional overload, local rejections and timeouts may be expected.
The run passes its correctness criteria only if every attempted request has an
accounted outcome, configured bounds hold, shutdown works, and unaffected sessions
retain control-traffic progress. Measure recovery for 30 seconds after returning
to baseline load; the provisional goal is baseline throughput and p99 within twice
the pre-fault value. Report ongoing injected delays separately.

For connection and soak runs, sample heap, RSS/native memory, descriptors,
threads, queues, and active sessions. Compare the final idle state with the
post-warmup idle baseline after drain. An RSS increase alone does not prove a
leak; retained pending objects, continuing growth, or unreleased owned resources
need investigation. Do not force garbage collection during latency measurement.

## Accounting and valid comparisons

Reconcile disjoint categories for the measurement cohort:

```text
planned arrivals = generator-skipped arrivals + attempted requests
attempted requests = local admission/validation rejections + admitted requests
admitted requests = successful peer responses + negative peer responses
                  + local failures + timeouts + cancellations + unfinished
```

Write progress is a separate dimension: not started, started, and locally
completed. A started write can still fail with uncertain peer acceptance.
Do not count the same local rejection both before and after admission. Count
peer-generated receipts separately from the original request's terminal result.

Record schedule lag, skipped arrivals, generator CPU/memory, target CPU/memory,
and phase durations. If the generator misses arrivals or is itself saturated,
the requested target load was not established: label that comparison invalid for
target-capacity claims, while retaining the useful diagnostic data.

For healthy comparisons, run the same profile at least three times with identical
declared inputs and record the spread. Preserve per-run reports; aggregate
histogram counts only with matching units/ranges and populations. Do not average
percentiles or compare a warm local result with an unreported cold remote run.

Reports must include the exact revision, configuration, seed, environment,
achieved rates, all outcome counts, timing populations, histogram settings, and
resource observations. Threshold failure or incomplete execution gives a nonzero
exit status. Distinguish an expected fault outcome from an unexpected failure.
All load runs execute freshly and stay outside the default quick build checks.

## Step 13 fresh local measurements

Twelve run pairs executed freshly on 9 September 2026 as separate installed
client/server JVMs on loopback. The workstation still reports Java
21.0.12+8-1-24.04-Ubuntu, Linux 7.0.0-31-generic amd64 and 12 logical processors.
Both JVMs used `-Xms256m -Xmx512m`, the default collector, binary DCS 4, 160-byte
payloads, seed 1, TRX binding, window 32 and a 2-second request timeout.
The host was also running development builds; these are diagnostic local
measurements without CPU isolation or a production-capacity claim.

- `smoke34` / `smoke50`: W-SMOKE, one connection, 10/s, 100 requests, no warmup,
  10-second measurement and 2-second drain.
- `base34` / `base50`: W-BASE, ten connections, 1,000/s, 30-second warmup,
  120-second measurement, 120,000 planned requests and 30-second drain.
- `repeat1`–`repeat3` and `repeat50-1`–`repeat50-3`: three identical short inputs
  per profile, two connections, 100/s, 1-second warmup, 3-second measurement,
  300 planned requests and 2-second drain. These are not shortened W-BASE claims.
- `ramp34` / `ramp50`: ten connections, 100/500/1,000/100 per second in four
  3-second steps, 3-second warmup, 5,100 planned requests and 3-second drain.
  These are short initial ramps, not the larger W-RAMP profile above.

Client operations were `submit`; receivers were `none`. The server retained
service through the configured drain. Baseline/repeat/ramp senders enabled
`--minimum-rate-ratio=0.99 --p99-ms=100` and kept `--expect-failures=false`.
Smoke runs enabled correctness checks without performance gates. No retry,
catch-up queue or automatic reconnect was used.

The rate below counts successful completions observed during the actual measured
phase; eventual successes also include drain. Latencies are schedule-to-terminal
observation in milliseconds. Peak RSS is the Linux process high-water mark in
MiB. Exit columns show client/server; 1 means failed criteria.

| Run | Planned | Eventual success | Skipped | Successful/s in phase | p50 / p95 / p99 ms | Peak RSS client/server MiB | Exit client/server |
| --- | ---: | ---: | ---: | ---: | --- | --- | --- |
| smoke34 | 100 | 100 | 0 | 10.000 | 1.412 / 4.263 / 7.119 | 71.1 / 70.1 | 0 / 0 |
| smoke50 | 100 | 100 | 0 | 10.000 | 1.415 / 4.183 / 6.819 | 71.5 / 70.8 | 0 / 0 |
| base34 | 120000 | 119764 | 236 | 998.024 | 1.055 / 1.074 / 2.069 | 243.8 / 246.6 | 1 / 0 |
| base50 | 120000 | 119955 | 45 | 999.616 | 1.054 / 1.063 / 1.071 | 236.5 / 247.8 | 1 / 0 |
| repeat1 | 300 | 300 | 0 | 99.998 | 1.242 / 1.399 / 2.479 | 76.6 / 77.7 | 0 / 0 |
| repeat2 | 300 | 300 | 0 | 99.998 | 1.293 / 2.469 / 2.635 | 75.5 / 77.4 | 0 / 0 |
| repeat3 | 300 | 300 | 0 | 99.998 | 1.232 / 1.385 / 1.634 | 75.9 / 77.6 | 0 / 0 |
| ramp34 | 5100 | 5100 | 0 | 424.998 | 1.064 / 1.229 / 1.299 | 128.8 / 124.4 | 0 / 0 |
| repeat50-1 | 300 | 300 | 0 | 99.998 | 1.226 / 1.414 / 2.589 | 75.7 / 77.5 | 0 / 0 |
| repeat50-2 | 300 | 300 | 0 | 99.998 | 1.230 / 1.366 / 1.507 | 75.8 / 78.3 | 0 / 0 |
| repeat50-3 | 300 | 300 | 0 | 99.998 | 1.237 / 1.392 / 1.500 | 77.7 / 79.8 | 0 / 0 |
| ramp50 | 5100 | 5083 | 17 | 423.581 | 1.059 / 2.071 / 5.323 | 124.8 / 123.6 | 1 / 0 |

All twelve pairs reconciled planned/attempted/admitted/outcome equations and
receiver counts against sender warmup plus measurement admission. They had zero
local rejections, negative responses, timeouts, cancellations, local failures,
unfinished calls, pending calls after drain or invalid received content. Both
endpoints reported complete cleanup in every pair.

**Both full W-BASE runs failed.** Measurement skips were 236 under 3.4 and 45
under 5.0; warmup skipped another 9 and 44 respectively. The 5.0 short ramp also
failed with 17 skips. Successful rate and p99 targets alone cannot override the
zero-skip criterion. The generator did not establish every scheduled arrival,
so these results cannot establish target server capacity. All admitted requests
still reached positive responses. No criteria were relaxed or failed reports
replaced by passing reruns.

The three healthy short runs per profile all passed. Their p99 spreads were
1.634–2.635 ms under 3.4 and 1.500–2.589 ms under 5.0; these are separate
percentiles, not averaged values. With only 100 or 300 samples, extreme
percentiles such as p99.9 are unsuitable for comparison. Full per-run reports
retain maximum, counts, overflow, p99.9, API invocation latency, scheduling lag,
mergeable buckets and separate warmup populations. No sustained resource/leak
conclusion is made from these finite runs.

Local raw artifacts are retained under `build/runs/step13-20260909/`: each named
run has `client/report.json`, `server/report.json`, numeric resource CSVs and
process logs. `manifest.json` preserves exact argument vectors, process statuses,
source paths/hash method and report SHA-256 identities; it is intentionally
outside Git's source tree. Its SHA-256 is
`bc0bb1432cabf8f34db649d382c23a27723983c3d339e3bb837c12afc467389f`.

All measured binaries identify the development source as
`e79b9f4cb905056fe6d91ded563e2a19b48ce4d9+2d6959eb02761d23affd86c50c4537f5f5ec9cf7debec9f74cbf3c01705e9b1f`:
the baseline Git commit plus the manifest's explicit hash of final Step 12/13
production/build inputs. The simulator executable SHA-256 is
`6b6f54668ee686b3f6fb9922b038cbbf64976b181752393d265c9e1c36e597f2`;
the library is `4e1d5a0ed133be89d4087722f320659c12f7931ff86fcf1d5089f1478a7ff5aa`;
HdrHistogram 2.2.2 is
`22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8`.
Later commits/builds have their own fingerprints; these results belong to this
recorded snapshot. Build-cache hits did not replay any measurement process.

## Step 15 finite content checks

Fourteen additional fresh installed client/server pairs exercised GSM, UCS-2 and
SAR `submit_multi`, 4 KiB UCS-2 delivery, and example/flexible/TLV receipt variants
under both profiles. Each pair planned four requests and observed four successful
responses/four received requests, with zero invalid or incomplete content and
complete cleanup. SAR multi used two connections with two parts per fixture.
These are content/assembly checks, not additional performance comparisons.
The exact configurations, source/binary fingerprints and reports are retained
locally under `build/runs/step15-variants-20260909/`, with manifest SHA-256
`ccfac137fea11b28ba7f821962d22403482c4251042eaafee2914fda09cb52bf`.
See [integration evidence](reviews/0014-simulator-integration.md). The Step 13
performance measurements above remain tied to their original snapshot and keep
their recorded failed workload criteria.

## Decisions still awaiting real requirements

Production peak and sustained rates, maximum simultaneous sessions, acceptable
latency/error budgets, deployment hardware, WAN conditions, operation/receipt
mix, and soak duration remain open. The profiles above allow implementation and
local measurement to proceed without presenting assumptions as user requirements.
