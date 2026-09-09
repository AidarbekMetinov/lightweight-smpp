# Step 13: client and server simulators

This step adds a separate Java 21 application using the public SMPP endpoint
API. It owns configuration, bounded schedules, receiver faults, aggregate
accounting, latency histograms, resource sampling and reports. The library keeps
its empty runtime dependency set. HdrHistogram 2.2.2 belongs only to the tool.

The [simulator guide](../SIMULATORS.md) defines the executable contract and
remaining heavy-load scope. [Per-type SOLID evidence](0012-simulator-types.md)
covers every simulator production/test type at its final formatted revision.
Step 12 production files copied into the isolated worktree were prerequisites,
not Step 13 library changes.

## Actual TDD cycles

All focused commands ran in the isolated Step 13 worktree using the Java 21
Gradle wrapper, `--console=plain` and
`-Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step13'`. Ordinary build and
configuration caching remained enabled. Behavioral red runs compiled and executed
the named assertions; source/test changes invalidated the relevant test task.

| Cycle / local logs | Observed red and subsequent green |
| --- | --- |
| `01-plan-red` → `02-plan-green` | Invalid/unbounded plans were accepted and caller mutation changed rates; finite validation and defensive copying passed. |
| `03-pacing-red` → `04-pacing-green` | Empty scheduling lost known planned arrivals; exact constant/ramp timing, late-slot accounting and signed clock wrap passed. |
| `05-fault-policy-red` → `06-fault-policy-green` | Known rejection buckets incorrectly accepted and invalid mixes were allowed; disjoint seeded decisions passed. |
| `07-arguments-red` → `08-arguments-green` | Valid configuration returned null and invalid inputs were accepted; strict CLI parsing and defaults passed. |
| `09-content-red` → `10-content-green` | Missing payloads and invalid bounds; deterministic raw content and immutable body bounds passed. |
| `11-metrics-red` → `12-metrics-green` | Planned/outcome counts and histogram populations were zero; disjoint accounting, percentile fixtures, overflow and status-cardinality bounds passed. |
| `13c-decisions-red` → `14-decisions-green` | Deferred work did not complete or consume its bound; due/stalled/cancelled decisions, cleanup and clock wrap passed. |
| `16-message-traffic-red` → `17-message-traffic-green` | Missing payload placement and capability selection; exact short-field/TLV placement and version/role preflight passed. |
| `18-runner-red` → `19-runner-green` | No requests or cohorts were recorded; bounded independent pacing, concurrency refill, skips, warmup and unfinished drain passed. |
| `20-criteria-red` → `21-criteria-green` | Failed, slow and unfinished runs incorrectly passed; explicit correctness and optional performance gates passed. |
| `22-reports-red` → `23-reports-green` | Missing/incorrect JSON and output files; escaping, numeric/bucket encoding, streamed CSV and overwrite refusal passed. |
| `24-receiver-red` → `25-receiver-green` | A real request received the absent-handler status 8 instead of configured throttle 88; registered receiver faults and counts passed. |
| `26-process-red` → `27-process-green` | The standalone server produced no READY event; independent Java client/server processes then exchanged and reported ten successes under each profile. |
| `29-architecture-red` → `30-architecture-green` | The permissive rule missed a simulator dependency on RequestWindow; the public-capability rule detected that real negative probe and accepted production sources. |
| `32-abort-accounting-red` → `33-abort-accounting-green` | Warmup/maintenance aborts threw away admitted work and sub-millisecond warmup passed preflight; partial Results preserve counts, abort reasons and measurementStarted, and invalid warmup is rejected early. |
| `34-unknown-resources-red` → `35-unknown-resources-green` | An unavailable descriptor peak was reported as zero; it now stays -1 until a real sample exists. |
| `36b-extraction-accounting-red` → `37b-extraction-accounting-green` | A raw SMPP 5.0 request with 65 unknown TLVs returned a negative response but receiver count stayed zero; counted supplier extraction now records received/invalid content. |
| `39-interrupted-start-red` → `40-interrupted-start-green` | Interrupted startup cleared the owner's interrupt; blocking startup/shutdown paths preserve it and cleanup remains possible. |
| `43-binary-coding-red` → `44-binary-coding-green` | Raw bytes declared SMSC-default coding 0 instead of the workload's binary coding; generated/validated raw content now uses data_coding 4, verified against both specification tables. |

The log prefix is `/tmp/step13-` and suffix `.log`. Cycles through process setup
normally used `:simulator:test --tests 'kg.aidarbek.simulator.*Test'`; later
focused commands selected the specific runner/configuration, criteria, resource,
exchange or architecture class. The interruption cycle selected the single
`SimulatorExchangeTest.interruptedStartupPreservesTheOwnersInterruptAndStillAllowsCleanup`
method.

`13-decisions-red` was a compiler warning about explicitly closing a resource
inside try-with-resources and is not behavioral red evidence. `13b` was interrupted
because the unfinished declaration exposed an unbounded test join; adding an
observable completion assertion yielded the actual `13c` red. The first `36`/`37`
extraction attempts were rejected by outgoing TLV extension validation before
reaching the receiver and are not the bug reproduction. The corrected `36b`
fixture uses a bounded raw TCP peer and leaves normal outgoing validation intact.

`15-prerequisite-green` established a compiling temporary Step 12 dependency.
`28-process-scenarios` added characterization of duplex data, rejection, delay,
stall, disconnect and preflight failures; it passed without an invented red.
`31-visibility-refactor` preserved those behaviors while making tool internals
package-private and keeping the CLI main class public; tool Javadoc had no
warnings. Code and tests subsequently used the final shared Step 12 production
snapshot, including its independently reviewed bounded reply-drain fixes.

## Review findings and corrections

An independent agent reviewed the completed scheduling, accounting, receiver and
reporting types. The partial-result, warmup preflight, unknown-resource and eager
content-extraction defects above received actual regression cycles. Receiver
metadata was also corrected to distinguish application-observed handlers from
wire frames rejected before handler invocation. The review identified the
per-stream SAR ordinal needed for the upcoming Step 15 adapter; that integration
belongs to Step 15 and its own tests/review.

The main interfaces have concrete consumers: ContentPlan supplies generated and
received content, TrafficOperation calls one library capability, and PendingCall
observes/cancels that library-owned request. None allocates protocol sequences,
duplicates the request window or sends protocol responses independently.
Application response decisions retain finite ownership until advanced or closed.
Latency/counter snapshots and report formatting remain separate responsibilities.

## Final verification and measurements

Separate `45-final-format` preceded `46-final-validation`, which freshly executed
all **40 simulator cases across 15 classes**, produced warning-free simulator
Javadoc/installed launchers and generated the final inventory. Every simulator
source hash was independently recomputed before the 65-type review was accepted.
The unchanged 559-case library suite belongs to the final Step 12 snapshot.

Main-checkout integration ran `./gradlew check build :simulator:installDist
:simulator:javadoc solidReviewInventory dependencies --configuration
runtimeClasspath --console=plain` successfully. Its 40-case simulator output
was restored from the matching shared cache; library tests and the 60 review-tool
cases were up to date. The current integrated inventory covers **293 identities**.
Runtime dependency inspection reports no library dependencies. All three library
JARs contain exact Apache LICENSE/NOTICE and only the expected production content.
The simulator distribution contains its tool JAR, the library and HdrHistogram
2.2.2, with no test/architecture/review dependency. HdrHistogram carries its own
`META-INF/LICENSE.txt`; simulator archives/distribution also preserve project
licensing. Output-root exclusions remain explicit and configuration-cache safe.

`47-fresh-measurements` and `48-fresh-measurements` launched twelve independent
client/server run pairs outside Gradle tests. [WORKLOADS.md](../WORKLOADS.md)
records exact inputs, executable/source identities, observed counts, percentiles,
resource observations, statuses and retained local reports. Both W-SMOKE runs and
all three short comparison runs per profile passed. The 3.4 short ramp passed;
the 5.0 short ramp and both full 1,000/s W-BASE runs failed strict criteria because
of recorded generator skips. Every admitted request completed successfully, both
receiver/sender cohorts reconciled and cleanup completed. These measurements do
not establish production capacity, independent interoperability or sustained
resource stability. No failed target was weakened to obtain a passing result.
