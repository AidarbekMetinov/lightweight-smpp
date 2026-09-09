# Review: heavy-load scenarios and reporting

## Scope and source ownership

Development began in an isolated worktree at
`e0f0f9c049dd63c89cff6c78078adf9e6267acd9`. Final composition consumes the separately
reviewed Step 16/17 public APIs at `387aed9ef523c85fb3cfa0f8f245438759deb6fc`.
This report covers **70 identities in 42 owned Java files**, including every
changed record, enum, nested holder, anonymous PendingCall, test and architecture
fixture. The complete post-format path/type/SHA-256 inventory appears in the
blocks below. No Java build logic changed in this work.

Owned changes provide explicit holds and accurate finite pacing, interval
attribution, off-owner resource sampling, bounded churn, slow decisions, retired
stream accounting, fault-mixture checks, public request/reply ownership samples,
TLS/keepalive/reconnect composition, truthful throughput gates and fresh report
orchestration. The separate Python runner supplies finite preset campaigns,
count-weighted repeat aggregation and explicit recovery assessment. Library
runtime dependencies remain unchanged; HDR stays in the simulator tool.

Copied library sources, `BroadcastTraffic`/test, `LifecycleSettings`/test, the
Step 17 PKCS12 fixture and the unchanged Step 16 `SimulatorProcessTest` were
compilation/test prerequisites, not owned edits. Their exact paths and copied
hashes are recorded in `/tmp/step18-copied-prerequisites.json` and excluded from
the handoff patch. The Step 16 broadcast registry/help and the original standalone
broadcast process regression are preserved. The root integration additionally
runs the inherited three-case `LifecycleTlsTest`, absent from this isolated tree.
The separate raw-wire peer and its evidence belong to
[0017-fault-peer.md](0017-fault-peer.md); no Cloudhopper code or dependency was
added anywhere in this change.

## Contracts reviewed

Arrival scheduling retains finite original identities and emits at most the
newest due event per poll. Skips do not become a retry queue. Concurrency mode
retains its separate finite-window refill contract and has no independent offered
rate. Both modes retain aborted/warmup/unfinished accounting, with all terminal
outcomes attributed to the originating cohort. The 100us default final spin
allowance addresses a known park-oversleep fixture, without claiming zero skips
on an ordinary JVM or a shared host.

Connection replacement waits for physical retirement, resets only the fresh
generation's content ordinal and never resends a previous request. Receiver
plans are bounded by physical slots; retirement preserves incomplete SAR counts
before dropping a generation plan. Deferred handler decisions hold capacity
through completion callback return, release on cancellation only when the owner
advances, and retain separately reconcilable selected and physically deferred
populations. A selected application stall is subject to endpoint deadlines and
ordered reply ownership; it is not a promise of a missing wire response.

Sampling runs on one owned worker without queued ticks. A blocked sample does
not hold generation progress, but it remains an owned task and bounded stop
reports incomplete termination. Process snapshots retain unknown Linux values as
`-1`. Public SessionResources supplies request/reply reservations, not physical
transport queue occupancy. Closed last-generation facades can remain in fixed
slots; sampled independent fields/peaks are not an atomic current-connection
measurement. Final physical, request, reply, decision and stream ownership must
be zero. The report explicitly marks unavailable control/response write counts
and radio recipients instead of inferring them from API invocations.

The root's independent review identified the completion-fraction rate gate:
100% nominal-phase successes could pass even when a delayed owner exit made
actual observed throughput less than 99%. The regression now uses a truthful
one-second plan and 1.02-second observed phase. The corrected gate compares
actual successful/s against planned/configured offered/s and rejects zero,
empty or shortened healthy phases. Fixed concurrency rejects an arrival-rate
ratio option at configuration time.

`SimulatorArchitectureTest` continues to require public capability dependencies.
Its only new library allowance is the exact public `transport.TlsConfig` value.
The real production import failed before that allowance, then passed. Permanent
negative fixtures still reject both a second RequestWindow and concrete
TcpTransport ownership. The root library ArchitectureTest is unchanged here.

## Behavioral TDD evidence

Each behavioral change was exercised before its implementation/fix. Gradle runs
used the following command family in the isolated worktree, with `--tests` for
the named class below (some green runs also selected earlier related suites):

```sh
./gradlew :simulator:test --tests kg.aidarbek.simulator.<TestClass> \
  --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'
```

Python cycles used the actual discovery command:

```sh
python3 -m unittest discover -s simulator/scripts/tests -p test_load_runs.py -v
```

Logs are `/tmp/step18-<number>-<name>.log`. Both numbers in each row are actual
sequential red/green executions, not reconstructed expected failures.

| Red → green log numbers | Scenario and observed relevant red | Test selector / green result |
| --- | --- | --- |
| 01-pacer-red → 02-pacer-green | Known 50us park oversleep produced 10.5ms instead of 10ms | DeadlinePacerTest passed |
| 03-burst-holds-red → 04-burst-holds-green | Unequal 2/1/2-second holds planned25 instead of18 | ArrivalScheduleTest passed |
| 05-load-options-red → 06-load-options-green | Explicit heavy-load options were unknown | SimulatorArgumentsTest passed |
| 07-load-bounds-red → 08-load-bounds-green | 2ms final spin was accepted above the1ms bound | SimulatorArgumentsTest passed |
| 09-step-accounting-red → 10-step-accounting-green | Expected three original step cohorts, received none | TrafficRunnerTest passed |
| 11-step-completion-red → 12-step-completion-green | A late first-step success incorrectly counted as in-step completion | TrafficRunnerTest passed |
| 13-sampling-red → 14-sampling-green | A blocked sample prevented the traffic owner returning | SamplingLoopTest passed |
| 15-resource-baseline-red → 16-resource-baseline-green | Post-warmup baseline still showed initial100 instead of200 | ResourceSamplerTest passed |
| 17-phase-actions-red → 18-phase-actions-green | Measurement hooks never ran | TrafficRunnerTest passed |
| 19-resource-criteria-red → 20-resource-criteria-green | Missing enabled RSS observation was accepted | ResourceCriteriaTest passed |
| 21b-report-rates-red → 22-report-rates-green | Required performance map was null | RunReportTest passed |
| 23-report-settings-red → 24-report-settings-green | Duration settings could not serialize through the declared JSON schema | RunReportTest passed |
| 25-load-composition-red → 26-load-composition-green | Real role reports lacked the post-warmup baseline marker | LoadRunTest passed |
| 27-profile-script-red → 28-profile-script-green | Full soak connection definition absent | Python1 case passed |
| 29-campaign-options-red → 30-campaign-options-green | Bidirectional5k/s soak lacked the required2500/s per-JVM options | Python2 cases passed |
| 31-campaign-cleanup-red → 32-campaign-cleanup-green | Finite child timeout/cleanup observation was absent | Python3 cases passed; two actual waiting child processes were stopped |
| 33-histogram-merge-red → 34-histogram-merge-green | A weighted100-sample histogram merged as zero | Python4 cases passed |
| 35-histogram-overflow-red → 36-histogram-overflow-green | Valid overflow-only population was rejected | Python5 cases passed |
| 37-source-manifest-red → 38-source-manifest-green | Exact declared input path list was empty | Python6 cases passed |
| 39-report-aggregation-red → 40-report-aggregation-green | Two compatible repeats aggregated as zero | Python7 cases passed |
| 41-campaign-cli-red → 42-campaign-cli-green | Failed fresh child reports were reported as a successful campaign | Python8 cases passed |
| 46-receive-only-rate-red → 47-receive-only-rate-green | Receive-only offered rate was0.2 instead of null | RunReportTest passed |
| 51b-cooperative-stall-red → 52-cooperative-stall-green | A cancelled incoming owner did not cancel/release its stalled decision | DecisionQueueTest passed |
| 53b-slow-consumer-red → 54-slow-consumer-green | Ordinary reply completed immediately; pending decision expected1, actual0 | ReplyControllerTest passed with controlled wrapped clock and real requests |
| 55-in-phase-population-red → 56-in-phase-population-green | Three corrupt success/completion populations were accepted | Python8 cases passed |
| 57-churn-schedule-red → 58-churn-schedule-green | Expected slot actions0/1 never occurred | ConnectionChurnTest passed |
| 59-churn-endpoint-red → 60-churn-endpoint-green | Replacement retained the original UUID | ChurnEndpointTest passed with both real generations distinct |
| 61-retired-stream-red → 62-retired-stream-green | Physically terminated receive stream never retired | ReplyControllerTest passed, preserving retired and new incomplete SAR groups separately |
| 63-churn-composition-red → 64-churn-composition-green | Real run lacked replacement lifecycle report | LoadRunTest passed |
| 65-decision-accounting-red → 66-decision-accounting-green | Expected2/1/1/2/0/2 decision lifecycle was all zero | DecisionQueueTest passed |
| 67-fault-mix-red → 68-fault-mix-green | Known wrong selected fraction was accepted | FaultCriteriaTest passed |
| 69-lifecycle-boundary-red → 70-lifecycle-boundary-green | Production LifecycleSettings dependencies on the public TLS value violated the boundary | SimulatorArchitectureTest passed; concrete transport negative probe retained |
| 71-pressure-samples-red → 72-pressure-samples-green | Expected request/reply counts and bytes were incorrectly zero | PressureSamplerTest passed |
| 73-pressure-composition-red → 74-pressure-composition-green | Real run did not report or stream pressure observations | LoadRunTest passed |
| 75-lifecycle-options-red → 76-lifecycle-options-green | `keepalive-idle` was rejected as unknown | SimulatorArgumentsTest passed |
| 77-tls-composition-red → 78-tls-composition-green | All four role/profile pairings failed against a peer requiring TLS | LifecycleEndpointTest passed with actual encrypted enquiry and cleanup |
| 79-reconnect-composition-red → 80-reconnect-composition-green | Peer closure did not produce a fresh generation | ChurnEndpointTest passed with three attempts, fresh ordinals and final loop cancellation |
| 81-pressure-criteria-red → 82-pressure-criteria-green | Every known ownership breach returned an empty failure list | PressureCriteriaTest and real-role composition passed |
| 85-recovery-cohort-red → 86-recovery-cohort-green | Final burst baseline had an80-second cohort without the required separate first30seconds | Python8 cases passed; total duration/count unchanged |
| 87-recovery-assessment-red → 88-recovery-assessment-green | Assessment had no first unmet level or actual recovery result | Python9 cases passed; known30-second success/failure and malformed offset covered |
| 89-observed-rate-red → 90-observed-rate-green | One-second plan/1.02-second observation passed the99% gate | RunCriteriaTest plus report/composition suites passed |
| 91-concurrency-ratio-red → 92-concurrency-ratio-green | Fixed concurrency accepted an undefined arrival-rate ratio | SimulatorArgumentsTest plus criteria/report/composition suites passed |
| 95-assessment-report-red → 96-assessment-report-green | Aggregated report omitted explicit assessment applicability | Python9 cases passed |
| 97-retained-resource-bound-red → 98-retained-resource-bound-green | Unknown large resource extension was retained in every aggregate row | Python9 cases passed with fixed numeric retention schema |
| 99-diagnostic-plan-count-red → 100-diagnostic-plan-count-green | Short diagnostic manifest retained100 planned requests although the actual plan had1 | Python9 cases passed |

Corrections to the evidence: initial log21 failed first on schema version before
the more informative performance-map assertion; log21b is the explicit map red.
Log51 was an accidental test method declaration error and log53 was a strict
compiler warning for the initial fixture's AutoCloseable signature. Neither is
counted as behavioral evidence;51b and53b are the actual relevant reds. Log87's
missing level manifested as a TypeError while accessing that required result;
this was the tested missing behavior, not a compilation/environment failure.
Python campaign tests intentionally retain failed finite child reports; their
`FINISH passed=false` output is expected while the enclosing assertions pass.

The late rate fix used these exact additional invocations:

```sh
./gradlew :simulator:test --tests kg.aidarbek.simulator.RunCriteriaTest --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'
./gradlew :simulator:test --tests kg.aidarbek.simulator.RunCriteriaTest --tests kg.aidarbek.simulator.RunReportTest --tests kg.aidarbek.simulator.LoadRunTest --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'
./gradlew :simulator:test --tests kg.aidarbek.simulator.SimulatorArgumentsTest --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'
./gradlew :simulator:test --tests kg.aidarbek.simulator.SimulatorArgumentsTest --tests kg.aidarbek.simulator.RunCriteriaTest --tests kg.aidarbek.simulator.RunReportTest --tests kg.aidarbek.simulator.LoadRunTest --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'
```

## Final verification and measurement separation

Formatting ran separately before final inventory/hashes:

```sh
./gradlew spotlessApply --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'
./gradlew :simulator:test :simulator:javadoc :simulator:installDist solidReviewInventory --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'
```

Logs93-rate-fix-format and94-rate-fix-validation passed. Final isolated behavior
was **93 tests, zero failures/errors/skips**, with fresh `:simulator:test`
execution, Javadoc, installed launcher and parser-generated inventory. Compile
and other eligible development outputs reused caches normally. The earlier
83/84 snapshot passed91 tests before the rate-gate/configuration regressions;
it is superseded for the final Java review. Root's later integrated run passed96
simulator cases, including the three unchanged Step17 TLS adapter cases; that
broader build is separate evidence and is not counted as isolated execution.

Actual fresh command101, after the final installed build:

```sh
python3 simulator/scripts/load_runs.py run --profile W-SMOKE --version 3.4 --direction submit --revision 387aed9ef523c85fb3cfa0f8f245438759deb6fc --source-root . --launcher simulator/build/install/simulator/bin/simulator --output /tmp/step18-candidate-diagnostic --diagnostic-seconds 2 --warmup-seconds 0 --drain-seconds 1 --rate 10 --connections 1
```

This two-second functional campaign produced20 planned/attempted/admitted/
successful originating requests, zero skips and complete report/process cleanup
for both roles. It is not capacity or soak evidence. Early steady snapshot
checks44/49 and fresh diagnostics45/50 are retained as earlier-source evidence,
not substituted for final measurements. Root separately executes the full
both-profile campaign from immutable candidate inputs and records shared-host
contention, misses and every failed run. No criterion was relaxed after the
baseline/target diagnostic generator skips.

The final candidate handoff at source freeze contained42 Java files plus the
Python runner/tests. Its manifest is `/tmp/step18-candidate-freeze.manifest.json`
(SHA-256 `a5818c95d1483ad5718d7fb8939aa5b69b4972ec56fa5d8d81670dd9b28628e3`),
archive `/tmp/step18-candidate-freeze.tar.gz`
(`4e749d08928050653a9760eaa611af6d81be13f70fdbf6e7b76ec6e8047cb443`) and
base387aed9 patch `/tmp/step18-candidate-freeze.patch`
(`ef71d299ceae89c2f135e37cb65080f098325b98899ea04685fba99be345d12f`).
The runner SHA-256 is
`91834a0dc7c987441b8ec89d2551aa277933e53c8219f4678e8d6375efeb9c45`.
Subsequent documentation/review additions do not change the measured Java inputs.

The fixed numeric aggregation schema, finite process deadlines, exact input hash
algorithm, 30-second recovery policy and observation limitations are documented
in [LOAD_TESTING.md](../LOAD_TESTING.md). The final 70-block validation is scoped to
this owned inventory; root integration owns the full project review/build check.
A passing inventory or architecture rule is supporting evidence, not a substitute
for the following contract-by-contract review. Remaining reviewed findings: none.

The actual scoped validator command was `java -cp build/classes/java/review
kg.aidarbek.smpp.review.ReviewCheck --check-files .
/tmp/step18-owned-coverage.tsv`, followed by one `--source` argument for each of
the 42 unique source paths below and `--report
docs/reviews/0017-load-scenarios.md`. Log102-owned-review-check exited 0 and its
coverage contains all 70 current identities with matching whole-file hashes.
This explicit scope verifies the owned handoff; it does not claim that copied
prerequisite reviews were rewritten in the isolated worktree.

## Python orchestration review

The runner has no classes and 20 functions including its nested fixed-schema
selector. The test module has one unittest class and 10 functions including the
independent interval fixture. Python is outside the Java evidence validator;
its functional and ownership review is recorded explicitly here.

| Functions / fixture | Responsibility, contract and evidence |
| --- | --- |
| `source_manifest` | Fingerprints 1..10,000 explicitly scoped production/build/tool inputs, each at most32MiB, with a deterministic path-delimited SHA-256; source and executable identities remain separate. Cycle37/38 checks the exact path list and content sensitivity. |
| `profile` | Copies finite workload definitions so callers cannot mutate the preset table. Cycles27/28 and85/86 preserve full soak and burst/ramp timing/counts. |
| `assess_intervals` | Checks at most64 original scheduled cohorts, matching rates/holds/contiguous offsets and known histogram populations; reports first unmet level and exact30-second recovery without averaging percentiles. Cycles87/88 and95/96 cover threshold boundaries and report inclusion. |
| `merge_histograms`, `bounded_int` | Validate integer populations and ordered unique bucket bounds; at most65,536 merged buckets and128billion aggregate samples, with overflow separate. Cycles33–36 and55/56 check weighted rank/population corruption. |
| `validate_cohort`, `merge_cohort` | Preserve planned/attempted/admitted/terminal equations, original outcome categories and in-phase subset constraints; merging sums counters and bucket populations. Cycles39/40 and55/56 provide independent valid/corrupt reports. |
| `aggregate_reports` | Accepts1..128 distinct bounded reports with distinct run identities and matching schema/configuration/source/executable/histogram contracts. Retains scalar per-run spread and explicit recovery verdicts; no aggregate wall-clock rate is invented for overlapping processes. |
| `resource_summary`, nested `selected` | Retain only the known signed numeric snapshot schema and boolean baseline presence. Unknown report extensions cannot multiply retained state across128 inputs. Cycle97/98 demonstrates the previously retained blob and corrected bounded projection. |
| `read_json` | Reads at most32MiB plus one overflow byte and rejects nonfinite JSON constants; raw input reports remain on disk. It does not accept an arbitrary object deserializer. |
| `build_options`, `nanos`, `iso_duration` | Translate finite preset durations into exact integral nanoseconds and explicit per-role options, splitting even data rates equally. Cycle29/30 verifies true per-JVM soak counts; cycle99/100 keeps diagnostic manifest count consistent with commands. |
| `run_pair`, `command` | Own at most two actual child processes, finite shared deadline, bounded READY parsing, stop-all-before-reap cleanup and exact argument arrays without shell interpretation. Cycle31/32 uses real finite waiting children; exit/error observations remain explicit. |
| `main`, `run_campaign` | Enforce finite repeats/overrides and fresh output, persist every attempted command/result, stop after interruption or incomplete child cleanup, and never transform a failed measurement into a replay success. Cycle41/42 tests actual child report retention; command101 executes the installed launcher freshly. |
| `extra_options` | Whitelists bounded non-secret lifecycle/content/pacing/resource options; cannot override owned report paths, ports or source identity, and exposes environment names rather than password values. |
| `write_json` | Uses exclusive creation for immutable output and an owned pending-file replacement for evolving campaign metadata; JSON must contain finite numbers. It never overwrites an existing campaign selected by the caller. |
| `LoadRunsTest` and nested `interval` fixture | One unittest class covers pure profile/arithmetic/aggregation contracts and finite real-child ownership. Its interval fixture derives coherent counters from explicit rate/time/success values; no process double pretends to implement subprocess cleanup. All 9 test methods pass. |

These functions keep schedule/configuration translation, collection, acceptance,
output and process ownership separate without introducing a plugin framework.
Process and filesystem effects stay in their adapters; arithmetic accepts plain
values, and subprocess arguments never pass through a shell. The full source-input
manifest identifies the final Python bytes independently of the measured JARs.

## Complete final type reviews

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ArrivalSchedule.java
type: kg.aidarbek.simulator.ArrivalSchedule
sha256: 8f687fde033d51efc91eb3d69e0ba891b898e52ee7503e2dc097848e35d29660
responsibility: Owns one finite original-arrival timeline and newest-due cursor.
consumers: TrafficRunner and ConnectionChurn poll deadlines and account for every skipped identity.
S: pass | Only finite arrival timing and cursor accounting change this type; it has no sending, sleeping or metrics sink.
O: pass | Rates, explicit unequal holds and count caps vary through LoadPlan; completion policy remains outside the timeline.
L: pass | Each original identity is emitted or skipped once, finish is idempotent, and subtraction handles monotonic wrap; exact fractional and burst fixtures pass.
I: pass | Callers receive only arrival, next-deadline and count/step queries required for pacing and attribution.
D: pass | Depends on immutable plan values and supplied timestamps, without clocks, endpoints, executors or file access.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ArrivalSchedule.java
type: kg.aidarbek.simulator.ArrivalSchedule.Arrival
sha256: 8f687fde033d51efc91eb3d69e0ba891b898e52ee7503e2dc097848e35d29660
responsibility: Carries the original index/time and skipped prefix of one scheduled attempt.
consumers: TrafficRunner and ConnectionChurn consume immutable arrival components.
S: pass | The three scalar components describe one scheduling event.
O: pass | New scheduling policies produce the same event shape without adding send behavior.
L: pass | Primitive record equality preserves negative and wrapped timestamps; no timestamp normalization changes the original event.
I: pass | Consumers need the three exact components and no mutable schedule access.
D: pass | Only primitive timing/count values cross the scheduling boundary.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ConnectionChurn.java
type: kg.aidarbek.simulator.ConnectionChurn
sha256: 9bdd8100da8be62ffad7a6548d80eacb7ff10e92edb40d71058eff2021b9197a
responsibility: Schedules finite deliberate slot replacements without retry backlog.
consumers: SimulatorEndpoint brackets start/advance/stop and reports Snapshot.
S: pass | Only churn cadence, slot choice and admission accounting drive changes.
O: pass | The IntPredicate slot action varies independently of the unchanged ArrivalSchedule.
L: pass | Starts once, counts skipped/busy/failed actions, finishes remaining events once and preserves clock-wrap ordering in controlled tests.
I: pass | The coordinator needs start, advance, stop and an immutable snapshot; no message or connection implementation leaks.
D: pass | Replacement effects enter through a bounded slot predicate; scheduling uses values and explicit time.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ConnectionChurn.java
type: kg.aidarbek.simulator.ConnectionChurn.Snapshot
sha256: 9bdd8100da8be62ffad7a6548d80eacb7ff10e92edb40d71058eff2021b9197a
responsibility: Publishes one finite churn-accounting observation.
consumers: SimulatorRun reconciles planned, skipped, attempted and actual initiation categories.
S: pass | Contains only related schedule counters and lifecycle flags.
O: pass | Report formatting and acceptance change in consumers, not this scalar observation.
L: pass | Primitive record values are immutable; unstarted snapshots report zero planned events and explicit started=false.
I: pass | Read-only fields match the churn reconciliation equations.
D: pass | Depends on no endpoint, scheduler or report infrastructure.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/DeadlinePacer.java
type: kg.aidarbek.simulator.DeadlinePacer
sha256: ff03a932d0f94ec3400bcb910f1e55c881629540db755b107f918a94a81d7b6c
responsibility: Waits to a monotonic deadline with a bounded final spin allowance.
consumers: SimulatorRun supplies real clock/park/spin and TrafficRunner supplies finite waits.
S: pass | Owns waiting accuracy and interruption only, not the arrival schedule.
O: pass | Injected clock, park and spin actions support deterministic alternatives without changing scheduling.
L: pass | Rejects spin outside 0..1ms, recomputes after park, preserves interrupt status and supports wrapped deadlines; known 50us oversleep regression passes.
I: pass | The generator requires one pause operation; injected collaborators are the narrow JDK functional contracts.
D: pass | All timing effects are supplied at composition; no hardwired executor or endpoint dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/DecisionQueue.java
type: kg.aidarbek.simulator.DecisionQueue
sha256: 05823916ab3bda25e6e87182c1e4c832d640ef80d4dc9037b9545a4122ff546c
responsibility: Owns bounded delayed/stalled handler decisions through physical callback return.
consumers: ReplyController admits decisions; the run owner advances or closes them.
S: pass | Admission, deadline release, cancellation and counters form one retained-decision lifecycle.
O: pass | Generic response values and a BooleanSupplier cancellation predicate support each operation without protocol branches.
L: pass | Capacity stays reserved through future notification, callbacks run outside the queue lock, close/advance detach each entry once, and cancelled stalls free capacity on advance.
I: pass | Callers get a completion future, advance/close and small observation queries; they cannot replace the queue storage.
D: pass | Depends on JDK futures and supplied cancellation/time values, without endpoint or socket imports.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/DecisionQueue.java
type: kg.aidarbek.simulator.DecisionQueue.Completion
sha256: 05823916ab3bda25e6e87182c1e4c832d640ef80d4dc9037b9545a4122ff546c
responsibility: Stores a detached decision and its cancellation choice for unlocked notification.
consumers: DecisionQueue.advance creates and completes these bounded temporary records.
S: pass | Encapsulates only the detached notification action.
O: pass | Value type remains generic through Deferred; no protocol-specific completion branch is added.
L: pass | Record component identity remains stable while its referenced future settles; completion is invoked after detachment and reservation release occurs in the outer finally.
I: pass | Only the queue uses complete plus deferred/cancelled components.
D: pass | Uses the queue-local Deferred abstraction and JDK future contract only.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/DecisionQueue.java
type: kg.aidarbek.simulator.DecisionQueue.Deferred
sha256: 05823916ab3bda25e6e87182c1e4c832d640ef80d4dc9037b9545a4122ff546c
responsibility: Retains one response, absolute deadline, future and request-cancellation predicate.
consumers: DecisionQueue owns these entries until notification returns.
S: pass | Every field belongs to one delayed response reservation.
O: pass | Generic T and cancellation predicate allow new reply types without queue changes.
L: pass | Future identity is intentionally shared with its package consumer; future mutation cannot release queue capacity before owner advance/close, as tested.
I: pass | The queue requires deadline/stall/cancellation observation and one complete action; no transport methods are present.
D: pass | Contains only generic values, primitive time and JDK completion/cancellation ports.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/DecisionQueue.java
type: kg.aidarbek.simulator.DecisionQueue.Snapshot
sha256: 05823916ab3bda25e6e87182c1e4c832d640ef80d4dc9037b9545a4122ff546c
responsibility: Publishes admitted, released, cancelled, rejected and retained decision counts.
consumers: FaultCriteria and report consumers reconcile handler decisions.
S: pass | Counters and peak describe one physical queue lifecycle.
O: pass | Acceptance thresholds and JSON representation remain outside the record.
L: pass | Immutable scalar equality is tested against an independently derived 2/1/1/2/0/2 lifecycle; snapshots do not settle decisions.
I: pass | All fields support the stated admission and terminal equations.
D: pass | No mutable queue or future reference crosses this observation boundary.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/FaultCriteria.java
type: kg.aidarbek.simulator.FaultCriteria
sha256: fd0cad9a63ca420911a87a89ad08c03fb5d4c14a53665e5980a237804e7a6f99
responsibility: Evaluates receiver decision reconciliation and an explicitly requested selected fault mixture.
consumers: SimulatorRun supplies final ReplyController snapshots and configuration.
S: pass | Only fault-accounting and selected-mixture acceptance belong here.
O: pass | Percentages, tolerance and delay policy vary as immutable inputs; injection mechanics remain in ReplyController.
L: pass | Always checks disjoint accounting and unfinished retention; requested mix requires 100 selections and refuses capacity fallbacks instead of inventing applied delays.
I: pass | One pure evaluation returns bounded failure names; consumers do not implement fault callbacks.
D: pass | Depends on snapshots/configuration and arithmetic, never worker or socket state.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/LoadPlan.java
type: kg.aidarbek.simulator.LoadPlan
sha256: c3be920a6b970dd8b845c121d6876dd0ad33333bd6daeed5a0c9f31c3965e59c
responsibility: Validates one finite warmup/measurement/drain and arrival or concurrency plan.
consumers: Arguments, ArrivalSchedule and TrafficRunner share normalized immutable timing/count values.
S: pass | Only workload-plan invariants and hold normalization change the record.
O: pass | Rates and holds express ramps/bursts without schedule subclasses; the original constructor keeps equal-hold behavior.
L: pass | Copies lists, rejects mixed concurrency/rates, caps counts/rates/durations and requires holds to sum exactly; submillisecond nonzero arrival warmup stays rejected.
I: pass | Plan components are the exact generation inputs; no observation, endpoint or file capability is exposed.
D: pass | Depends on JDK values and local plan rules, not runtime infrastructure.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/LoadPlan.java
type: kg.aidarbek.simulator.LoadPlan.Model
sha256: c3be920a6b970dd8b845c121d6876dd0ad33333bd6daeed5a0c9f31c3965e59c
responsibility: Names independent arrivals and finite-window refill modes.
consumers: Arguments, scheduling and criteria choose the stated mode.
S: pass | The enum represents only the load-model distinction.
O: pass | Alternative algorithms remain in scheduling policy; no fake extension hierarchy is needed for these two modes.
L: pass | Enum identity remains stable; each mode is paired with its validated rate-list contract.
I: pass | Two named constants are sufficient for consumers to select explicit behavior.
D: pass | No infrastructure references or mode-specific worker state are stored.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/LoadSettings.java
type: kg.aidarbek.simulator.LoadSettings
sha256: 7d3d07c35834bb9c49f1e02fea9b09b618d41555e3c539449a611d4022c5d2a5
responsibility: Validates finite pacing, churn, receiver delay, sampling and acceptance settings.
consumers: Arguments construct it; coordinator and report readers consume immutable options.
S: pass | The options belong to the non-secret heavy-load scenario configuration; execution remains elsewhere.
O: pass | Values and a non-secret report projection extend scenario choices without changing endpoint/request engines.
L: pass | Bounds reject inconsistent churn, excessive spin/sample/delay, invalid labels and nonfinite tolerance; immutable durations/maps cannot mutate a running plan.
I: pass | Consumers read named fields; report projection exposes numeric durations instead of requiring JSON support for arbitrary types.
D: pass | Only JDK values and local finite-duration validation are used.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/PressureCriteria.java
type: kg.aidarbek.simulator.PressureCriteria
sha256: 80de84c99fa56da844eabd4a17928f8b1fcc67048d81a0f1907792341f96a86f
responsibility: Checks sampled request/reply ownership ceilings and final physical retirement.
consumers: SimulatorRun evaluates the published PressureSampler summary after endpoint cleanup.
S: pass | Only ownership acceptance equations drive the type.
O: pass | Bounds derive from the same immutable configuration as endpoint reservations; observation collection remains independent.
L: pass | Does not mistake retained closed facades for live binds, checks all actual count/byte ceilings and final zero ownership, and passes known boundary values.
I: pass | One pure evaluate operation returns descriptive failures and does not expose mutable sampling state.
D: pass | Uses public-derived scalar observations and configuration, without request-window or transport ownership.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/PressureSampler.java
type: kg.aidarbek.simulator.PressureSampler
sha256: 17bfa926e150bc7ea03ed8cb8efbde432813e6ab49366c8d3c6141e4fabe8f00
responsibility: Serializes sampled public session ownership and publishes bounded immutable summaries.
consumers: SamplingLoop invokes sample; SimulatorRun marks baseline and reads summary; ReportWriter streams rows.
S: pass | Collecting, reducing and publishing one ownership series is its single measurement responsibility.
O: pass | A supplied observation function and clock support controlled or real data; physical counters remain in endpoint APIs.
L: pass | Each list is bounded, sums use exact arithmetic, fields stay nonnegative, per-field peaks remain explicit and summary reads do not wait for blocked observations.
I: pass | Sampling, one baseline mark and a read-only summary meet the coordinator needs; no socket/request mutation methods exist.
D: pass | Infrastructure enters through Supplier/LongSupplier; SessionResources is a public observation value and output is confined to ReportWriter.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/PressureSampler.java
type: kg.aidarbek.simulator.PressureSampler.Observation
sha256: 17bfa926e150bc7ea03ed8cb8efbde432813e6ab49366c8d3c6141e4fabe8f00
responsibility: Holds one finite aggregate sample of connections, facades, requests, replies, decisions and streams.
consumers: Sampler reducers and pressure criteria read immutable numeric fields.
S: pass | All components describe the stated ownership sample.
O: pass | Additional instrumentation belongs in sample construction, not new behavior in the record.
L: pass | Constructor rejects negative counts/bytes and impossible tool-wide structural maxima; record equality preserves independently sampled field values.
I: pass | Explicitly named accessors prevent callers treating request/reply bytes as transport-queue occupancy.
D: pass | Stores only primitive observations, with no session or queue references.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/PressureSampler.java
type: kg.aidarbek.simulator.PressureSampler.Summary
sha256: 17bfa926e150bc7ea03ed8cb8efbde432813e6ab49366c8d3c6141e4fabe8f00
responsibility: Publishes baseline, final sample, independent peaks and sampling presence/count.
consumers: SimulatorRun and JSON report consumers read an immutable pressure aggregate.
S: pass | Contains only summary state for the ownership series.
O: pass | Criteria and output formats can change without sampler-state mutation through this record.
L: pass | Producer supplies immutable Observation records; baselineRecorded distinguishes initial fallback from a real baseline and per-field peaks do not imply simultaneity.
I: pass | Consumers get the four observations/presence/count needed for honest reporting.
D: pass | References only immutable local observation values and scalar metadata.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReplyController.java
type: kg.aidarbek.simulator.ReplyController
sha256: ce9304cd0eb2a372c66b3f8748b46266d3ea23606c29ec4740cd443e71be80ed
responsibility: Owns bounded receive-stream content validation and configured response decisions.
consumers: Message/Common/BroadcastTraffic adapters call reply; SimulatorRun advances, closes and observes it.
S: pass | Receiver policy and its per-generation retained decision/content state stay together; it never allocates sequences or encodes wire frames.
O: pass | Generic commands, content suppliers and FaultPolicy support operation adapters without adding protocol logic to the queue.
L: pass | Counts cancelled/invalid/disconnect/selected paths separately, retains streams until physical termination, preserves retired SAR incompleteness and never merges fresh generations.
I: pass | Adapters need only typed reply creation; coordinator needs advance/close/snapshot, not internal map or queue mutation.
D: pass | Uses public IncomingRequest/BoundSession/HandlerResponse and explicit clock/content ports; architecture rejects a second request engine.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReplyController.java
type: kg.aidarbek.simulator.ReplyController.Snapshot
sha256: ce9304cd0eb2a372c66b3f8748b46266d3ea23606c29ec4740cd443e71be80ed
responsibility: Publishes the disjoint receive decisions and retained/retired stream counters.
consumers: FaultCriteria, pressure sampling, report assembly and real-peer tests consume the snapshot.
S: pass | Fields describe one receiver observation, including the separate deferred-decision population.
O: pass | New acceptance rules operate on the observation without injecting effects into it.
L: pass | Only immutable primitive values and DecisionQueue.Snapshot are retained; incompleteAssemblies includes retired scalar totals rather than lost stream history.
I: pass | Named selected, capacity and deferred categories expose exactly the population distinctions required by consumers.
D: pass | No IncomingRequest, content plan, future or live session escapes through the record.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReplyController.java
type: kg.aidarbek.simulator.ReplyController.Stream
sha256: ce9304cd0eb2a372c66b3f8748b46266d3ea23606c29ec4740cd443e71be80ed
responsibility: Retains one generation content plan, physical termination observation and received count.
consumers: ReplyController creates, validates and retires these map entries.
S: pass | Its state is scoped to one generation receive stream.
O: pass | ContentPlan supplies payload/receipt variation without changing retirement rules.
L: pass | Private retention follows a protected termination-stage copy; unsuccessful termination keeps ownership visible, and incomplete state is counted before successful removal.
I: pass | No public stream interface is exposed; only the owning controller touches validation and counters.
D: pass | Depends on ContentPlan and public session termination, with no transport or request engine access.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReportWriter.java
type: kg.aidarbek.simulator.ReportWriter
sha256: 27a5d8c4999cfea11d25e42ca84f08bdb62fc6f531999e530a82efad7d172ba8
responsibility: Owns fresh JSON and streamed resource/pressure report files.
consumers: ResourceSampler, PressureSampler and SimulatorRun write bounded report observations.
S: pass | File creation, streaming, final encoding and closure share the report-output ownership concern.
O: pass | Metrics arrive as values; adding scenario behavior never requires the writer to operate endpoints.
L: pass | CREATE_NEW preserves existing output, constructor failure closes opened writers, finish flushes both series and close attempts both owners with suppressed secondary I/O failure.
I: pass | Consumers use their numeric series method or final map; no parser or simulator configuration API is imposed.
D: pass | Filesystem and JSON wiring are confined to this concrete output adapter; core schedules never import it.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ResourceCriteria.java
type: kg.aidarbek.simulator.ResourceCriteria
sha256: 40d0cb35c0fa01cb4c145a01664f1c25b7c5b2f6f93bbb75e9d0da111982001d
responsibility: Evaluates explicit process peak-memory and post-baseline growth thresholds.
consumers: SimulatorRun passes final resource summary and LoadSettings.
S: pass | Only declared process-budget acceptance belongs here.
O: pass | Thresholds vary by configuration; operating-system sampling remains in RunEnvironment/ResourceSampler.
L: pass | Enabled unknown RSS/descriptor readings fail instead of becoming zero; peaks and final-minus-baseline growth use their correct populations.
I: pass | A pure list-returning evaluate call provides all consumer needs.
D: pass | Arithmetic depends on immutable snapshots and policy values, without platform-specific reads.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ResourceSampler.java
type: kg.aidarbek.simulator.ResourceSampler
sha256: cc5d2e9b4aa59efd854234b1571ba5f9f85d6ffce23fb1ac68310c93d8c998fa
responsibility: Serializes process observations and publishes initial/baseline/final/peak summaries.
consumers: SamplingLoop samples it; coordinator sets baseline and ReportWriter receives numeric rows.
S: pass | Collecting and reducing a single process resource series is cohesive; thread scheduling and acceptance remain separate.
O: pass | Supplied observation and monotonic clock allow deterministic fixtures without platform conditionals in the reducer.
L: pass | Unavailable values remain -1, peaks do not regress, baseline is a distinct sampled observation and volatile summaries can be read without owning the sample lock.
I: pass | Callers need sample/tick, one baseline mark and a summary; they do not receive file or OS access.
D: pass | Variable process observation and time enter through narrow suppliers; direct OS wiring is confined to the convenience constructor.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ResourceSampler.java
type: kg.aidarbek.simulator.ResourceSampler.Summary
sha256: cc5d2e9b4aa59efd854234b1571ba5f9f85d6ffce23fb1ac68310c93d8c998fa
responsibility: Carries immutable initial, final and baseline readings with peaks and sample metadata.
consumers: ResourceCriteria and RunReport interpret explicit resource populations.
S: pass | Stores one process-series summary only.
O: pass | Output formatting and threshold changes stay in consumers.
L: pass | RunEnvironment snapshots are immutable; baselineRecorded preserves fallback-versus-observed distinction and unknown peaks retain -1.
I: pass | Named accessors separate peak memory from final descriptor/thread growth.
D: pass | No mutable writer, sampler or worker references are retained.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunCriteria.java
type: kg.aidarbek.simulator.RunCriteria
sha256: 1327d1eb1e56606c523b0f5489d9621f8ba7bb6d148e4cb3f970dd5b3e7a0ab8
responsibility: Evaluates workload completion, content, latency and actual observed successful-rate gates.
consumers: SimulatorRun combines its Verdict with ownership/resource/fault criteria.
S: pass | Owns traffic acceptance only; collection, faults and resource thresholds remain in separate focused types.
O: pass | Configured expectations and thresholds vary without changing terminal accounting or generation.
L: pass | Expected faults never waive unfinished accounting/cleanup; throughput uses observed elapsed duration and rejects empty/shortened healthy phases, proven by the 1.02s regression.
I: pass | One evaluate operation and a small Verdict provide the coordinator needs without mutable metrics access.
D: pass | Uses immutable cohort/configuration snapshots and arithmetic, not clocks, sockets or handlers.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunCriteria.java
type: kg.aidarbek.simulator.RunCriteria.Verdict
sha256: 1327d1eb1e56606c523b0f5489d9621f8ba7bb6d148e4cb3f970dd5b3e7a0ab8
responsibility: Returns the immutable collection of workload-gate failures.
consumers: SimulatorRun combines failures; tests use passed for acceptance.
S: pass | Only gate outcome representation drives changes.
O: pass | Failure production belongs in criteria evaluators, not record subclasses.
L: pass | Copies the failure list and derives passed solely from emptiness; callers cannot mutate a recorded verdict.
I: pass | The failure list and passed predicate are the complete consumer surface.
D: pass | Depends only on immutable strings and JDK collection copying.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunReport.java
type: kg.aidarbek.simulator.RunReport
sha256: caf92da65eb882ece7dcfe4f2a0a520982e5e836075ef6d6ef4df421c455db24
responsibility: Assembles explicit schema, population semantics, configuration and workload observations.
consumers: SimulatorRun supplies settled snapshots and ReportWriter serializes the map.
S: pass | Its responsibility is report representation; it does not collect metrics, schedule traffic or decide endpoint lifecycle.
O: pass | Snapshot/configuration inputs isolate new measurement fields from generation and protocol code.
L: pass | Rates distinguish planned/configured from actual/observed time, receive-only and concurrency offered rates are null, and physical wire counts remain explicitly unavailable.
I: pass | One create method accepts the completed facts; no report consumer must implement endpoint callbacks.
D: pass | Uses immutable local snapshots and JDK maps; secrets and arbitrary endpoint exception objects are not serialized.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SamplingLoop.java
type: kg.aidarbek.simulator.SamplingLoop
sha256: 5ee473b6029cabfbbd861ba5bdd820eec11b93bd31d3743246d40bf1ef8d5d8f
responsibility: Owns one periodic observation worker and its bounded stop observation.
consumers: SimulatorRun starts it, polls failure/skipped count and joins it before final reporting.
S: pass | Only observation cadence and physical worker ownership drive changes.
O: pass | A Runnable supplies the observation task; the loop has no resource-field or endpoint knowledge.
L: pass | At most one invocation exists, missed periods are counted without backlog, close is idempotent, bounded stop reports a blocked task instead of pretending it ended.
I: pass | Start/stop/close plus failure/skipped queries are sufficient; the internal Thread is never exposed.
D: pass | JDK thread/park mechanics are isolated here and task behavior arrives through a narrow callback.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorArguments.java
type: kg.aidarbek.simulator.SimulatorArguments
sha256: 83d2514f3a7c6d2adf27899d772d4b40173e105964fdefa5928275fbebba75dd
responsibility: Parses explicit bounded non-secret command-line options into immutable run configuration.
consumers: SimulatorMain and deterministic tests call parse before side effects.
S: pass | Owns syntax/default translation; execution and detailed value validation stay in configuration types.
O: pass | LifecycleSettings supplies its separate option catalogue/parser, while workload option additions stay at this composition boundary.
L: pass | Rejects unknown/duplicate options, direct password arguments, invalid modes and ambiguous rate ratios; preserves exact duration/rate values and explicit defaults.
I: pass | Only parse is exposed; consumers receive the typed configuration instead of retaining a mutable option map.
D: pass | Depends on JDK parsing and pure configuration/adaptor values, never endpoint creation or report files.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorConfig.java
type: kg.aidarbek.simulator.SimulatorConfig
sha256: f901397871bd5f81763b7913ad9847eb4d2311aa457fbdff4215447e50ae95d8
responsibility: Holds validated immutable run identity, workload, endpoint role and non-secret options.
consumers: Arguments construct it; operation adapters and runtime composition read it.
S: pass | The record defines the supported finite run contract rather than executing its phases.
O: pass | Nested LoadSettings and LifecycleSettings keep workload and lifecycle option variation separate from request engines.
L: pass | Caps sessions/window/payload estimates, preserves immutable value components, rejects server/churn reconnect ownership conflicts and unsupported concurrency rate ratios.
I: pass | Named accessors serve role, traffic, reporting and lifecycle composition without exposing mutable builders.
D: pass | Depends on public profile/bind values and pure tool configuration, not internal transport/session ownership.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorConfig.java
type: kg.aidarbek.simulator.SimulatorConfig.Mode
sha256: f901397871bd5f81763b7913ad9847eb4d2311aa457fbdff4215447e50ae95d8
responsibility: Names TCP client or server ownership for a simulator run.
consumers: Arguments, role validation and endpoint construction select the explicit owner.
S: pass | Represents only the two launcher roles.
O: pass | Operation capability variation remains in TrafficOperation adapters, not enum inheritance.
L: pass | Stable enum identity is used without mapping it to inferred protocol write counts.
I: pass | Consumers need exactly the two role constants.
D: pass | No infrastructure or role-specific live state is stored.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorEndpoint.java
type: kg.aidarbek.simulator.SimulatorEndpoint
sha256: 3d496a2c85f71015a003d05d7a6f296cd47ccd7983ac571adbf3ca8bee0c4130
responsibility: Owns a finite physical endpoint and fixed session slots with explicit replacement policies.
consumers: SimulatorRun starts/adapts sources, advances churn/reconnect, samples and shuts down it.
S: pass | Connection cohort lifecycle is its cohesive responsibility; message generation, payload policy and observations remain separate.
O: pass | Public lifecycle/reconnect configuration and handlers vary through endpoint constructors; traffic operations do not alter slot ownership.
L: pass | Replacements wait for prior physical retirement, reset generation ordinals, obey attempt bounds and cancel loops before shutdown; both-role TLS and distinct-generation real tests pass.
I: pass | Coordinator gets fixed-slot facades, ordinals and lifecycle snapshots; no socket workers or sequence allocation are exposed.
D: pass | This composition adapter depends on public SmppClient/SmppServer capabilities; internal protocol engines remain prohibited by architecture tests.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorEndpoint.java
type: kg.aidarbek.simulator.SimulatorEndpoint.LifecycleSnapshot
sha256: 3d496a2c85f71015a003d05d7a6f296cd47ccd7983ac571adbf3ca8bee0c4130
responsibility: Publishes actual tool-observed initial/replacement binds and physical connection counts.
consumers: SimulatorRun reconciles deliberate churn and formats lifecycle reports.
S: pass | Contains one connection-lifecycle observation.
O: pass | Interpretation/reporting evolve outside this scalar record.
L: pass | Immutable counts preserve separate deliberate starts/failures/aborts and all observed replacement binds; no wire-write certainty is implied.
I: pass | Consumers can reconcile lifecycle work without querying mutable slots.
D: pass | Only primitive counters cross the observation boundary.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorEndpoint.java
type: kg.aidarbek.simulator.SimulatorEndpoint.ReconnectSnapshot
sha256: 3d496a2c85f71015a003d05d7a6f296cd47ccd7983ac571adbf3ca8bee0c4130
responsibility: Publishes finite reconnect loop attempts, completed publications and terminal reasons.
consumers: SimulatorRun checks unfinished ownership and serializes reconnect evidence.
S: pass | Fields describe the bounded reconnect cohort only.
O: pass | Library reason names are observed without extending tool connection logic for each outcome.
L: pass | Copies the terminal-reason map, retains no failures/session history and keeps unfinished loops separate from completed published-session totals.
I: pass | Counters and reason counts satisfy reporting without exposing ReconnectHandle mutation.
D: pass | Depends on scalar counts and immutable strings rather than endpoint internals.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorEndpoint.java
type: kg.aidarbek.simulator.SimulatorEndpoint.Slot
sha256: 3d496a2c85f71015a003d05d7a6f296cd47ccd7983ac571adbf3ca8bee0c4130
responsibility: Retains one current facade, termination observation, optional replacement attempt and ordinal.
consumers: SimulatorEndpoint alone advances or replaces fixed slot records.
S: pass | All state belongs to one bounded connection replacement slot.
O: pass | Public connection attempts supply variable outcomes without a second reconnect or request implementation in the slot.
L: pass | Old termination is observed before replacement admission; a failed retirement remains visible, and new installation resets ordinal without replaying prior requests.
I: pass | The private holder has no public methods forcing consumers to manage resources twice.
D: pass | Uses only public facade/attempt types and copied completion stages; physical ownership stays with endpoint APIs.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorMain.java
type: kg.aidarbek.simulator.SimulatorMain
sha256: 709a17ac234d385b13df40cb30dedc80925e4d8b6962da0e4232cfc65d80addc
responsibility: Exposes the standalone launcher, safe environment credential lookup and exit status.
consumers: Users invoke the installed script; process tests exercise roles, broadcast lookup and failure exits.
S: pass | CLI entry/error presentation is separate from parsing, generation and endpoint lifecycle.
O: pass | New bounded options are described and parsed through collaborators without embedding operation implementations here.
L: pass | Help returns without endpoint side effects, invalid configuration exits2, failed work exits1, and diagnostics exclude arbitrary messages and secrets.
I: pass | One main entry plus private construction fits an independently runnable tool.
D: pass | Concrete composition belongs at this entry point; the library runtime artifact does not depend on it.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorRun.java
type: kg.aidarbek.simulator.SimulatorRun
sha256: 38ef9453a8462740d275447e70fdfcf38e326cbe23727af9c759cef0bfc89f59
responsibility: Composes one finite workload, receiver, lifecycle, observations, criteria and fresh report.
consumers: SimulatorMain invokes it; real-role integration tests observe report and cleanup results.
S: pass | Coordinates the run lifecycle while focused collaborators own scheduling, counters, output and protocol behavior.
O: pass | Message/Common/Broadcast operation adapters and content/lifecycle policies compose without duplicating protocol request tracking.
L: pass | Preflights content/TLS, retains partial traffic on abort, stops scheduling before drain, closes receivers and endpoint owners, joins sampling and reports unavailable wire counters honestly.
I: pass | A single execute entry returns status and emits bounded textual events; consumers need no internal queue or worker interfaces.
D: pass | Uses public endpoint capabilities and tool ports; concrete workers and writers are wired only at this runtime composition boundary.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficMetrics.java
type: kg.aidarbek.simulator.TrafficMetrics
sha256: 76434fc23bbc29c6ef0bae03ffcd1fc65be1ed9f2aeb066b804fecb353205a5f
responsibility: Routes each original scheduled identity into complete and per-step cohort metrics.
consumers: TrafficRunner records attempts, skips, admissions and terminal outcomes through it.
S: pass | Owns attribution/reduction, not generation or histogram serialization.
O: pass | Existing CohortMetrics handles bounded statistics while rates/holds parameterize interval attribution.
L: pass | Skipped ranges split at original step boundaries and late outcomes retain origin; in-step completion counts require finishing before that step end, as controlled burst tests prove.
I: pass | Runner uses only event-recording methods and immutable snapshots/intervals.
D: pass | Depends on pure schedules and metrics values, never request handles, clocks or sockets.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner
sha256: 80df7cefe026b5f635bfa854eb2f1d715f57528b7c7a0e372eaf1d5be16a1214
responsibility: Executes one bounded generation/warmup/drain protocol over request-observation ports.
consumers: SimulatorRun supplies a PendingCall source, clock, pause and lifecycle hooks.
S: pass | Single-owner traffic phase progress is separate from endpoint and report concerns.
O: pass | PendingCall source, monotonic clock, pause, maintenance and phase hooks support real and controlled runs without altering accounting.
L: pass | Keeps arrival traffic independent of completions, never catches up skipped arrivals, bounds active calls, preserves failed warmup/abort cohorts and cancels unfinished calls once.
I: pass | Only request polling/certainty/cancellation is required; operation and wire APIs remain in the source adapter.
D: pass | Variable time, requests and side effects are injected; metrics and schedule dependencies are pure local collaborators.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Active
sha256: 80df7cefe026b5f635bfa854eb2f1d715f57528b7c7a0e372eaf1d5be16a1214
responsibility: Retains one pending observation with original planned/invoked times and index.
consumers: TrafficRunner owns a list bounded by maximumPending.
S: pass | Fields identify one admitted call for terminal attribution.
O: pass | PendingCall supports alternative operations without changing the active holder.
L: pass | Record equality uses stable call identity and primitive timestamps; owner removes settled calls once and preserves original timing through drain.
I: pass | Only the phase owner reads these components; no extra cancellation API is duplicated.
D: pass | Depends on the narrow PendingCall observation port rather than library request internals.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Interval
sha256: 80df7cefe026b5f635bfa854eb2f1d715f57528b7c7a0e372eaf1d5be16a1214
responsibility: Publishes one configured rate hold and its original-identity cohort snapshot.
consumers: RunReport and load_runs.py perform explicit level/recovery analysis.
S: pass | The four fields describe one scheduled cohort interval.
O: pass | Aggregation policies evolve outside the immutable value.
L: pass | Snapshot values preserve original scheduled outcomes and separate in-interval observations; late response attribution is covered by exact fixtures.
I: pass | Consumers receive rate, offset, duration and metrics with no schedule cursor exposure.
D: pass | Depends only on immutable local metrics and scalar timing.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Phase
sha256: 80df7cefe026b5f635bfa854eb2f1d715f57528b7c7a0e372eaf1d5be16a1214
responsibility: Carries one internal completed or aborted phase observation.
consumers: TrafficRunner.run assembles warmup and measurement Result from it.
S: pass | Only phase metrics, duration, drain, failures and intervals are represented.
O: pass | Hooks and endpoint behavior remain outside this internal value.
L: pass | Producer supplies immutable snapshots and immutable failure/interval lists; failed warmup remains observable without fabricating a measurement.
I: pass | Private components match run assembly needs without an externally mutable phase object.
D: pass | Contains only local measurement values and diagnostic strings.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Result
sha256: 80df7cefe026b5f635bfa854eb2f1d715f57528b7c7a0e372eaf1d5be16a1214
responsibility: Publishes explicit warmup/measurement/drain outcomes and original intervals.
consumers: Criteria and report consumers distinguish unstarted/partial/full measurement.
S: pass | The record is the immutable result of one finite run.
O: pass | Compatibility constructors retain old consumers while explicit fields support richer reporting.
L: pass | Copies failure/interval lists, preserves supplied phase durations and measurementStarted, and never merges failed warmup into measured traffic.
I: pass | Consumers read the exact cohorts and state required for acceptance and reporting.
D: pass | No live request, session, executor or writer reference escapes through the result.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ArrivalScheduleTest.java
type: kg.aidarbek.simulator.ArrivalScheduleTest
sha256: a932444d39493084185153fe3ae2b07a2a90c472f3cc22ae91fb4bd8cad807b3
responsibility: Checks known scheduled identities, holds, caps, skipped arrivals and wrapped timestamps.
consumers: JUnit invokes deterministic timeline examples; ArrivalSchedule is the subject.
S: pass | All tests address the finite arrival timeline contract.
O: pass | Data-driven rates/holds cover variation without transport fixtures.
L: pass | Expected identities/times are independent arithmetic examples; no real clock or timing-dependent pass assertion is used.
I: pass | Tests use only schedule polling/count/next-time observations.
D: pass | Pure plan values and explicit timestamps replace infrastructure.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ChurnEndpointTest.java
type: kg.aidarbek.simulator.ChurnEndpointTest
sha256: 8458901223d83c0a3b6e321442c6fbea212557ff1ee1757d7088beaed64bb9ff
responsibility: Exercises deliberate churn and finite reconnect against real client/server endpoint owners.
consumers: JUnit coordinates bounded owner startup and distinct-generation assertions.
S: pass | The shared run fixture serves only generation replacement and physical-bound behavior.
O: pass | A boolean scenario selects explicit churn versus library reconnect while preserving the same public cleanup assertions.
L: pass | Uses real endpoints, observes both fresh UUIDs and reset ordinals, bounds waits/joins and closes both owners on failure; no permissive transport double is used.
I: pass | Only public tool endpoint lifecycle/session observations and explicit shutdown are required.
D: pass | Functional protocol ownership remains in real library endpoints; test time bounds provide coordination rather than throughput claims.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ConnectionChurnTest.java
type: kg.aidarbek.simulator.ConnectionChurnTest
sha256: 5d7df4b8b0791a783c5de6315da8190e4e88f8bb451b8ff3a86a059c5ea4c6fb
responsibility: Checks skipped/busy churn admission and stop semantics across monotonic wrap.
consumers: JUnit invokes a finite callback-history fixture.
S: pass | All assertions describe the churn schedule population.
O: pass | Injected slot predicate supplies busy/accepted variation without endpoint setup.
L: pass | The explicit expected 5/3/2/1/1 counters and slot history prove no replay or hidden backlog.
I: pass | Uses only start/advance/stop/snapshot and constructor contracts.
D: pass | A plain list and caller-owned times replace live sockets and clocks.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/DeadlinePacerTest.java
type: kg.aidarbek.simulator.DeadlinePacerTest
sha256: 7963a73e1f1338f0488f0002089cceede8bf9e0870d4db730ec64a6005e4e03b
responsibility: Demonstrates bounded spin compensation for a known park oversleep distribution.
consumers: JUnit drives injected clock/park/spin actions.
S: pass | The test isolates deadline drift and total parked time.
O: pass | Functional timing seams model oversleep without modifying the pacer contract.
L: pass | Independent 10ms/9ms expected totals and a wrapped starting timestamp avoid environment-sensitive timing claims.
I: pass | Only the pacer pause operation and collaborator effects are observed.
D: pass | AtomicLong controlled time replaces physical sleep and scheduling noise.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/DecisionQueueTest.java
type: kg.aidarbek.simulator.DecisionQueueTest
sha256: 6238438f0b35a1d69b527418d363e50d7291f68f4e6fe5befd2dc93f7e09e6c0
responsibility: Verifies queue retention, stall cancellation, deadlines and final decision counts.
consumers: JUnit consumes standard CompletableFuture results from real DecisionQueue instances.
S: pass | All tests address one bounded deferred-decision lifecycle.
O: pass | Different payloads/cancellation predicates cover supported generic variation.
L: pass | Checks capacity before/after physical advance, close idempotence, clock wrap and exact admitted/released/cancelled/rejected totals; future cancel does not pretend capacity is already free.
I: pass | Exercises only the published package queue methods and future contract.
D: pass | Uses JDK futures and controlled timestamps, with no substituted transport or scheduler.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/FaultCriteriaTest.java
type: kg.aidarbek.simulator.FaultCriteriaTest
sha256: 01bc327111c0b465a29ee7c0203a03c206943326f8ad66add17fed1cb0cd5a2c
responsibility: Checks known selected fault fractions and explicit capacity-fallback rejection.
consumers: JUnit supplies scalar snapshots and immutable configuration.
S: pass | One fixture builds internally reconciled selected/deferred populations.
O: pass | Counts/tolerance parameterize independent corruptions without injection behavior in the evaluator.
L: pass | Known 25% distributions pass; 24/26%, capacity fallback and sparse population fail without fabricated network outcomes.
I: pass | Only pure evaluate output is asserted.
D: pass | Controlled values replace random selection, live requests and sockets.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/LifecycleEndpointTest.java
type: kg.aidarbek.simulator.LifecycleEndpointTest
sha256: c7db8b64b2d33545a453811848d7d6c57e14eabbd68aa0014632670f5073d4a0
responsibility: Verifies both simulator roles actually use TLS in both SMPP profiles.
consumers: JUnit pairs the simulator endpoint with a separately TLS-configured public peer.
S: pass | Fixture concern is encrypted endpoint composition, not TLS engine internals.
O: pass | Profile/role parameters share the same public enquiry/shutdown contract.
L: pass | Four real socket cases require the independently encrypted peer, bound all waits and shut down both owners; the development PKCS12/password stay fixture-only.
I: pass | Uses public lifecycle policies and enquiry/shutdown capabilities without internal transport mutation.
D: pass | JDK-backed LifecycleSettings and real endpoint owners supply encryption; no fake that could accept plaintext substitutes for TLS.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/LoadRunTest.java
type: kg.aidarbek.simulator.LoadRunTest
sha256: 190b6a67e69cfd1c4aaa1fd8d999b633c748f8b424e007c52e3223b939a9b6c9
responsibility: Checks complete run composition, baselines, pressure output and deliberate churn reporting.
consumers: JUnit runs actual client/server coordinators with fresh TempDir output.
S: pass | All assertions concern run-level wiring and physical cleanup.
O: pass | Two scenarios share only the bounded real-role harness; generation details remain in deterministic unit tests.
L: pass | Waits/joins are bounded, both real reports must pass, and output explicitly proves baseline, sampling stop, pressure rows and retired stream cleanup.
I: pass | Exercises execute/events and report files instead of reaching into endpoint internals.
D: pass | Uses real public endpoints and temporary files, with no mocked report outcomes.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/PressureCriteriaTest.java
type: kg.aidarbek.simulator.PressureCriteriaTest
sha256: 80c85e93d15c1cade9b5bd13ea13c50a9b5197a26946d23a987161e1797b0611
responsibility: Checks exact sampled ownership ceilings and final-zero criteria.
consumers: JUnit supplies boundary and one-over-limit immutable observations.
S: pass | The fixture isolates pressure acceptance equations.
O: pass | Configuration varies the true endpoint limits without constructing workers.
L: pass | Independently computed two-connection bounds pass; every exceeded dimension and remaining ownership gets its named failure, while closed observed facades are allowed.
I: pass | Only evaluate results are inspected.
D: pass | Pure configuration and scalar observations replace live pressure and timing.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/PressureSamplerTest.java
type: kg.aidarbek.simulator.PressureSamplerTest
sha256: ea9f623bb9fd394df15fffe6f4faabcf6c33c97aa4448e81eb3c3e2986616ba0
responsibility: Checks summed public ownership, independent peaks, baseline and streamed CSV values.
consumers: JUnit drives a real sampler/writer from immutable SessionResources fixtures.
S: pass | All observations concern one ownership-series reducer.
O: pass | Injected Supplier and clock vary sample values without endpoint work.
L: pass | Known sums 5/250/3/500 and exact CSV row establish aggregation; final idle does not erase earlier peaks.
I: pass | Uses only sample/baseline/summary plus the writer lifecycle.
D: pass | JDK atomic values and public resource records replace private endpoint state.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReplyControllerTest.java
type: kg.aidarbek.simulator.ReplyControllerTest
sha256: 63ddf3938ce56d55bc8e30b77e777751783509f4d4724f3b5d0903a4fb6fd2ae
responsibility: Checks real slow-consumer responses and stream retirement preserving incomplete SAR state.
consumers: JUnit owns a focused Peer fixture and controlled receiver clock.
S: pass | Tests target receive-decision and per-generation content ownership.
O: pass | Raw versus SAR content and explicit clock advance vary receiver policy through supported seams.
L: pass | Real requests remain pending until the exact delay, and a fresh generation cannot complete the retired generation SAR group; coordinated waits have finite deadlines.
I: pass | Uses typed operations and controller observations without bypassing request or transport contracts.
D: pass | Real endpoint capabilities and helper plans are combined with an injected clock, not forged IncomingRequest objects.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReplyControllerTest.java
type: kg.aidarbek.simulator.ReplyControllerTest.Peer
sha256: 63ddf3938ce56d55bc8e30b77e777751783509f4d4724f3b5d0903a4fb6fd2ae
responsibility: Owns the real client/server pair and receiver policy used by ReplyController tests.
consumers: The enclosing tests use its bound session, client and receiver observations.
S: pass | The fixture exists only for controlled receiver behavior over actual connections.
O: pass | Configuration/content suppliers parameterize the fixture without changing endpoint contracts.
L: pass | Constructor failure closes allocated owners; teardown attempts both bounded shutdowns via finally and closes both even after assertion/future failure.
I: pass | Private fields expose only what the two enclosing tests need; no general-purpose test server API is invented.
D: pass | Uses public endpoints/handlers with real frames, preserving the library physical ownership contracts.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ResourceCriteriaTest.java
type: kg.aidarbek.simulator.ResourceCriteriaTest
sha256: 2529046fc35e2a6a28155aaf420707caae9cd948efb6deb73784db7e90b02ac4
responsibility: Verifies unknown readings, peaks and final descriptor/thread growth thresholds.
consumers: JUnit supplies controlled resource observations and settings.
S: pass | Assertions isolate process-budget acceptance.
O: pass | Supplied observations vary OS availability and resource peaks without platform setup.
L: pass | Unknown enabled RSS fails; a returned-low latest heap does not erase peak failure; explicit disabled limits remain inactive.
I: pass | Tests consume the pure evaluator and immutable sampler summary.
D: pass | Temporary output and controlled JDK values replace platform resource dependence.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ResourceSamplerTest.java
type: kg.aidarbek.simulator.ResourceSamplerTest
sha256: 675358333e2b3c55211e7edaae91bd5c1b9e79131266b42a36dc1244d1188dbd
responsibility: Checks initial/baseline/final separation and unavailable descriptor preservation.
consumers: JUnit controls snapshots and monotonic timestamps.
S: pass | Tests only process-series reduction and periodic observation eligibility.
O: pass | Injected observations add peaks and unknown readings without changing runtime sampling.
L: pass | Exact scalar assertions prove peaks remain after usage falls and -1 stays unknown until a valid sample arrives.
I: pass | Only sample/tick/baseline/summary and writer ownership are exercised.
D: pass | No actual OS pressure or unstable memory threshold is used to decide a test pass.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/RunCriteriaTest.java
type: kg.aidarbek.simulator.RunCriteriaTest
sha256: f431e6eedefdcbd2863f4d858a3786aa9e35ee986fd01f5805ca29ee2687a88c
responsibility: Checks traffic acceptance for faults, cleanup, latency and observed successful rate.
consumers: JUnit constructs explicit cohort snapshots and truthful phase durations.
S: pass | Tests isolate acceptance equations from traffic generation.
O: pass | Configuration parameters vary expected failures and thresholds without new policy subclasses.
L: pass | The 1s plan/1.02s observed regression rejects misleading 100% completion fractions; partial/zero/empty phases also fail enabled rates, while exact timely work passes.
I: pass | Only Verdict failures/passed are observed; metrics setup uses the existing public package accounting methods.
D: pass | Controlled cohort values replace clocks and endpoint behavior, making threshold decisions reproducible.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/RunReportTest.java
type: kg.aidarbek.simulator.RunReportTest
sha256: df8e364ae89b016145158077d32356702dc30442c3e56fdd30a73ee1d92ad6d7
responsibility: Checks schema rates, drain attribution, settings serialization and unavailable offered-rate values.
consumers: JUnit assembles a report from known snapshots and temporary writer ownership.
S: pass | The fixture tests representation/population semantics only.
O: pass | Arrival, concurrency and receive-only configurations exercise the same report entry point.
L: pass | Known two-request history yields offered10/s versus successful5/s, late success stays in outcomes, and receive-only/concurrency offered rates are null.
I: pass | Uses create and serialized JSON output rather than internal map construction details beyond the public schema.
D: pass | Controlled snapshots and no live endpoint make report semantics independent of host timing.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SamplingLoopTest.java
type: kg.aidarbek.simulator.SamplingLoopTest
sha256: 3a62ea3d84b273bc39dd1748dd58a27caaac584d049f4091f7ea78c2169a26f1
responsibility: Proves one blocked sample cannot block the traffic owner or accumulate callbacks.
consumers: JUnit coordinates the owned worker with latches and bounded joins.
S: pass | All assertions concern physical sampling ownership and stop behavior.
O: pass | An injected blocked Runnable characterizes the same contract as real resource observation.
L: pass | Explicit entered/release/returned coordination proves start returns, stop times out honestly, only one invocation remains, and release permits termination.
I: pass | Uses lifecycle/failure queries only; the sampling worker itself is never exposed.
D: pass | JDK latches model a blocking observation without replacing Thread/Executor contracts.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest
sha256: 0fb391c9b1077a895784f7c3bf6a337014512ae6ac9ed56f1b2dec41e71cadd9
responsibility: Checks simulator dependencies against public library capabilities and targeted negative fixtures.
consumers: JUnit/ArchUnit imports actual production classes and isolated forbidden test classes.
S: pass | One boundary rule and its meaningful probes share the simulator dependency concern.
O: pass | Exact TlsConfig admission extends public configuration without allowing concrete transport ownership or internal request engines.
L: pass | Production selection is nonempty; real dependencies fail before the TLS value allow-list change and permanent RequestWindow/TcpTransport probes still report violations.
I: pass | The predicate and tests expose no runtime simulator capability.
D: pass | Architecture analysis depends on ArchUnit tooling only; intentional forbidden imports stay in test fixtures.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest#boundary/<anonymous>@21:109
sha256: 0fb391c9b1077a895784f7c3bf6a337014512ae6ac9ed56f1b2dec41e71cadd9
responsibility: Implements the actual ArchUnit predicate for public simulator dependencies.
consumers: The enclosing boundary rule invokes DescribedPredicate.test for imported JavaClass values.
S: pass | Only dependency admissibility is decided by the predicate.
O: pass | Public value namespaces and an exact extra-type set support declared capability additions without weakening engine ownership boundaries.
L: pass | Implements the inherited boolean predicate contract for every supplied JavaClass; membership is deterministic and production/probe tests exercise both outcomes.
I: pass | One inherited test method is the entire required interface.
D: pass | Uses ArchUnit metadata and immutable name/modifier checks, not source execution or runtime endpoint state.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest.ForbiddenRequestEngine
sha256: 0fb391c9b1077a895784f7c3bf6a337014512ae6ac9ed56f1b2dec41e71cadd9
responsibility: Provides a genuine forbidden RequestWindow dependency for the negative architecture probe.
consumers: Only the architecture test imports this test-only fixture.
S: pass | One field is sufficient to demonstrate the prohibited second request-engine dependency.
O: pass | No supported extension or runtime behavior is required of an intentionally forbidden fixture.
L: pass | No supertype or resource contract is imitated; it is never instantiated and cannot claim request ownership.
I: pass | The single field exposes exactly the dependency under test.
D: pass | The internal import is intentional test evidence and is excluded from production selection.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest.ForbiddenTransportOwner
sha256: 0fb391c9b1077a895784f7c3bf6a337014512ae6ac9ed56f1b2dec41e71cadd9
responsibility: Provides a genuine forbidden TcpTransport dependency despite permitted public TLS settings.
consumers: The architecture test imports only this test fixture for its transport negative case.
S: pass | One field demonstrates concrete transport ownership crossing the tool boundary.
O: pass | The fixture stays fixed while the dependency predicate is tested.
L: pass | It does not imitate FrameTransport or accept writes; no substitutable transport contract is weakened.
I: pass | Only the field dependency is needed; no fake socket methods are added.
D: pass | The deliberate concrete transport dependency remains test-only and must produce an ArchUnit violation.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArgumentsTest.java
type: kg.aidarbek.simulator.SimulatorArgumentsTest
sha256: 11db384f7e01cab22f19e9579dd4787fbfbfc486416e4d9a5925f8708705b4c0
responsibility: Checks CLI normalization, explicit defaults, finite bounds and unsupported ownership combinations.
consumers: JUnit parses pure configurations without starting endpoints.
S: pass | All cases test supported command-line input contracts.
O: pass | Lists of invalid options and explicit mode/lifecycle scenarios extend parser coverage without execution fixtures.
L: pass | Known ramps preserve units/roles; unknown/duplicate/secret arguments and nonfinite settings fail; arrival-rate ratios in fixed concurrency are rejected rather than silently reinterpreted.
I: pass | Only parse and typed configuration accessors are required.
D: pass | No environment secrets, network calls or output files are used by these parser tests.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest
sha256: 2bc45c305b67aea1b7ec1f249d66b37f3efdccb33c13f8262e4681edb8218c7a
responsibility: Checks controlled traffic accounting, phase hooks, saturation, failures and drain.
consumers: JUnit supplies narrow PendingCall fixtures and explicit clock/pause actions.
S: pass | All cases validate one generator phase protocol and its observation contract.
O: pass | Injected ports vary completion, rejection, blocking and maintenance failure without creating alternate request engines.
L: pass | Independent original-arrival arithmetic proves skipped/late attribution; aborts preserve cohorts and cancel once; warmup failures do not fabricate measurement.
I: pass | Fixtures implement only poll/certainty/cancel and no protocol methods.
D: pass | Controlled time and the PendingCall boundary separate generator correctness from endpoint and host performance.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest#concurrencyRefillsOnlyFiniteSlotsAndAccountsForUnfinishedDrain/<anonymous>@240:54
sha256: 2bc45c305b67aea1b7ec1f249d66b37f3efdccb33c13f8262e4681edb8218c7a
responsibility: Models one indefinitely pending call whose cancellation is observable.
consumers: The fixed-concurrency test supplies it through PendingCall.
S: pass | Its only behavior is pending observation and cancellation counting.
O: pass | The narrow PendingCall contract is sufficient without a fake protocol implementation.
L: pass | poll remains empty, certainty remains may-have-been-sent and cancel increments the independent counter; cancellation is not misrepresented as physical completion.
I: pass | Implements exactly the three observation methods required by the generator.
D: pass | Uses only a test-owned AtomicInteger and immutable Completion absence.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest#lifecycleActionsBracketMeasurementAfterWarmupDrainAndBeforeFinalDrain/<anonymous>@19:41
sha256: 2bc45c305b67aea1b7ec1f249d66b37f3efdccb33c13f8262e4681edb8218c7a
responsibility: Models a call released by the explicit measurement-stop hook.
consumers: The phase-hook test observes completion after scheduling ends.
S: pass | A single release flag determines pending versus successful observation.
O: pass | PendingCall admits this controlled completion policy without modifying generation.
L: pass | Returns the same known success after release and no result before; no-op cancel does not promise a completion, matching the narrow observation contract.
I: pass | Only poll/certainty/cancel are implemented.
D: pass | A test-owned atomic release flag replaces timers or fake endpoint state.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest#maintenanceAndPauseFailuresKeepAdmissionsAndCancelPendingCallsExactlyOnce/<anonymous>@154:56
sha256: 2bc45c305b67aea1b7ec1f249d66b37f3efdccb33c13f8262e4681edb8218c7a
responsibility: Models a pending call retained when generation maintenance or waiting fails.
consumers: The abort test checks cancellation and preserved UNFINISHED accounting.
S: pass | Only pending state and cancellation observation belong to the fixture.
O: pass | The PendingCall seam supports failure-path tests without modifying request or session types.
L: pass | Never fabricates a terminal response; cancellation count proves the owner attempts cleanup once after an unrelated runtime failure.
I: pass | Implements only the three required observation methods.
D: pass | Depends on test-owned counters rather than sockets or library internals.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest.FakeCall
sha256: 2bc45c305b67aea1b7ec1f249d66b37f3efdccb33c13f8262e4681edb8218c7a
responsibility: Supplies an immutable already-successful or permanently pending observation.
consumers: TrafficRunner tests use it for known warmup, late-completion and skipped-arrival scenarios.
S: pass | The record represents only a controlled completion state.
O: pass | Boolean state supplies the two needed observation cases through PendingCall.
L: pass | Immutable equality is intentional; poll yields stable known success or empty, certainty is explicit, and cancel makes no unsupported terminal-completion promise.
I: pass | Implements exactly poll/certainty/cancel without transport or future mutation.
D: pass | Uses the tool observation interface and immutable result values only.
findings: none
```

## Final installed payload audit

No project-owned Java/Python source or test changed for this evidence and no
Gradle invocation was made. External standard-library harnesses remained under
ignored run outputs. The final executing inputs and source declaration are
recorded in the [load guide](../LOAD_TESTING.md) and the copied candidate artifact/source
manifests. Library hash `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` and simulator hash `a59d553d3ec84de5d953a927696565f2c498384386a241a74a113426011d1d9e` were
independently checked in each of the 48 executing role reports and against every
installed file before and after execution.

`payload_matrix.py` SHA-256
`bc88d5fca8fc5bc5b4998dbdc8cb8802eb474c3e1ca8b3bede00f5156b098eb0`
ran the actual 24 pairs with a single bounded observer and at most two extra JVMs
at a time. Its original matrix exited 1: 23 pairs passed and one delivery/5.0/4096
pair failed only the blanket no-unanswered-controls criterion. The captured
client unbind sequence 2 remains recorded as lacking an observed response.
Both target processes in that pair exited 0 after four exact positively
acknowledged deliveries and full physical cleanup. Inspection of the final
`EndpointTermination` contract, shutdown documentation and five-second endpoint
shutdown call established the distinction between bounded physical retirement
and a proven graceful control exchange.

The subsequent independent `audit_application_captures.py` (SHA-256
`ce957bd4eba8361eafa6c2a57c0003c2c24512851cba22398f235b4872a4fee7`)
exited 0. It rebuilt full mandatory bodies from separate literal field layouts,
computed expected opaque bytes using source-verified JDK 21 seeded arithmetic,
checked exact header/status/sequence pairing, retained the unanswered unbind,
and reconciled all application counters and cleanup fields. It verified that
neither the captures nor installed files changed. Eight independently executed
JDK vectors and a deliberately corrupted byte verify the arithmetic/oracle
boundary; no product encoder output is used as the expected body. The measured
result is 128 application requests and 128 positive replies carrying 137216
exact bytes, not a reclassification of the original observer's failed criterion.
Audit output SHA-256: `35c1e8fedfac3217b68acce9a4d7b378e0770ff2289a0518f021b1e4bc1dcdfb`.

Earlier outcomes are preserved separately. Candidate2's first payload attempt
passed 7/24 and had 17 external three-second accept-budget failures before any
SMPP frame; a diagnostic trace established cold JVM startup. Its second attempt
with finite ten-second startup allowance retained 23 overbroad
`observedSessions == 0` checker failures and one real bidirectional-data local
rejection (three client successes plus one local rejection). The latter is not
classified as a checker defect. Candidate3 contains the independently tested
receiver-drain correction. A preliminary remote-JShell oracle invocation timed
out before the matrix; the retained local-execution JDK oracle then completed
all eight vectors. These setup/checker observations are not invented TDD reds.

Manual review of both external harnesses covers all declared types/functions
in `harness-review.md` under the final candidate3 evidence. Their scope remains
finite protocol observation; they expose no project API, introduce no runtime
dependency and do not modify the Java type inventory. Fixed bounds include one
connection pair, 8192-byte frames, 65536 queued bytes per direction, at most 64
recorded frames and 16 pending correlations, with finite startup/connect/proxy/
process waits and explicit owned cleanup. Peak observed queue/input/record values
were 4170 bytes, 4141 bytes and 20 frames. No throughput interpretation is made.
