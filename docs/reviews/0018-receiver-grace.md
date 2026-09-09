# Receiver service through the measured drain budget

The simulator now keeps receiver/endpoint maintenance active until the original
measured-phase drain deadline, even when its own originating requests have already
settled. This prevents local sender completion from prematurely withdrawing inbound
service from an independently phased bidirectional peer. The only production change
is the measured drain loop in `TrafficRunner`; no library code, frame transport,
request correlation, queue capacity, source routing or message codec changes belong
to this patch.

## Finding and policy

The immutable candidate2 matrix at
`build/runs/step19-corrected-matrix/cells/34-data-w-window-32/` recorded 188 client
`PEER_NEGATIVE` outcomes with status 8. Server receiver counters showed all
1,314,337 observed requests accepted, with no configured reject, invalid-content,
cancel-before-decision or capacity fallback. Source inspection found that
`TrafficRunner` returned as soon as its own active set emptied; `SimulatorRun`
then called `ReplyController.close()` before endpoint shutdown. A still-bound peer
could therefore receive status 8 without increasing receiver counters. Endpoint
shutdown also legitimately rejects new incoming operations, so moving receiver
closure after shutdown would not supply the missing receiver grace.

The previous guide explicitly described closure after the originator's own cohort
drained; it did not promise a shared peer phase barrier. That policy was inadequate
for the healthy bidirectional workload expectation. The corrected policy consumes
the already configured final drain budget as both completion observation and
receiver grace. Warmup retains its existing early drain when its pending calls
settle. The measured deadline remains `stopped + drain`, with subtraction-based
clock comparisons; a late local completion never starts a second grace period.
No peer synchronization protocol, new timer/thread/queue, additional retry or replay
was introduced. The local source is never invoked during grace. Existing bounded
maintenance continues deferred decisions, cancellation/expiry, stream retirement
and endpoint lifecycle observation. Local request capacity and terminal metrics
retain their original owners and cohort attribution.

Failure or interruption still aborts drain, preserves completed and unfinished
accounting and the caller's interrupt flag, and proceeds through the existing
cleanup path. Scheduler or collaborator delay can consume or overrun the original
deadline; the actual elapsed duration remains reported and no extra budget is
added afterward. `LoadPlan` still requires a positive drain; its minimum is one
nanosecond. Endpoint shutdown and receiver closure remain finite after that
budget. Peers remain independently phased; a peer whose sending lifetime exceeds
the other endpoint's declared receive lifetime can still fail. Old candidate2
reports are retained unchanged and are not relabelled as corrected measurements.
The old report lacks absolute measurement-start/stop events and per-response
teardown attribution; the independent regression establishes the mechanism, not
the precise provenance of every one of those 188 recorded responses.

## Actual TDD and verification

All Gradle commands below ran only in `/tmp/lightweight-smpp-step18` and appended
`--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'`.
There was one build at a time. Seven current root library sources were copied as
compile prerequisites, listed in `/tmp/step19-receiver-prerequisites.json`; they
are excluded from the patch and this review. Existing receipt fixtures were not
overwritten. Root and immutable candidates were never edited.

1. `/tmp/step19-receiver-01-red.log`: initial fixture compilation rejected an
   `AutoCloseable.close()` declaration that could throw `InterruptedException`.
   The fixture was corrected to preserve interruption and report bounded cleanup
   failure through assertions. This was not a behavioral red.
2. `/tmp/step19-receiver-02-peer-red.log`: actual `:simulator:test --tests
   kg.aidarbek.simulator.ReceiverGraceTest` behavioral red. Both 3.4 and 5.0
   cases expected status 0 but received status 8, with received=accepted=0.
   A real peer, real codecs and real TCP endpoint owners are used. The local
   originating call has already succeeded before a latch permits the independent
   peer request. Controlled owner time selects the first receiver-grace pause;
   on the old loop no such pause exists, so the same latch opens only after the
   receiver closes, exactly matching the surrounding `SimulatorRun` ownership
   order. The endpoint remains live to observe the peer's actual response. No
   guessed sleep duration, permissive fake transport or source-pattern assertion
   supplies the failure.
3. `/tmp/step19-receiver-03-peer-green.log`: same two real-peer cases passed
   after changing only the measured drain continuation/break predicates.
4. `/tmp/step19-receiver-04-contracts-green.log`: 21 focused cases executed;
   one new fixture used an invalid zero drain and was rejected by the existing
   `LoadPlan` contract. That attempt is retained, not claimed as another bug red.
   It was replaced with the supported one-nanosecond boundary case.
5. `/tmp/step19-receiver-05-contracts-green.log`: all 21 selected cases passed
   freshly across `ReceiverGraceTest`, `TrafficRunnerTest`, `ReplyControllerTest`,
   `DecisionQueueTest` and `LoadRunTest`. Added controlled-clock coverage checks
   both traffic models, signed clock wrap, local settlement during drain without
   resetting the deadline, an already expired deadline, exact sub-tick clipping
   and interruption after all local calls have completed. Existing tests cover
   warmup ordering, bounded pending cancellation and delayed/stalled decisions.
   The existing final-drain assertion changes from zero to its explicit five-ms
   configured budget; its warmup and before/after lifecycle assertions remain.
6. `/tmp/step19-receiver-06-format.log`: `spotlessApply` ran separately and
   passed before inventory and all hashes below.
7. `/tmp/step19-receiver-07-final-simulator.log`: final `:simulator:test
   :simulator:javadoc solidReviewInventory` execution and result are recorded in
   the final-check appendix below.

There are 13 affected named/anonymous Java identities in three source files. All
whole-file hashes below were recomputed after formatting and matched the fresh
inventory. The three-file manifest is
`/tmp/step19-receiver-formatted-java.json`. The protocol-independent drain policy
uses existing injected time/pause/maintenance ports; no architectural boundary is
expanded. No new Gradle task, dependency or measurement threshold is introduced.

## Whole-type review

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner
sha256: ad934e440cb182b9884c07c076a07b98fde6b16df55c53b5af1e586192dea6e8
responsibility: Owns bounded phase generation, terminal observation and receiver maintenance within one measured drain budget.
consumers: SimulatorRun supplies a finite LoadPlan, SessionTrafficSource, monotonic clock, pacer and endpoint/receiver lifecycle callbacks; contract tests supply controlled equivalents.
S: pass | One owner applies the finite warmup/measurement/drain policy and accounts for every originating attempt; the new grace remains part of that existing phase responsibility.
O: pass | Protocol operations vary through LongFunction<PendingCall> and maintenance/lifecycle callbacks without changes to the phase loop; the two declared load models remain a deliberate closed policy set.
L: pass | Each admitted PendingCall is observed once at terminal or cancelled once as unfinished; phase failures retain previous counts, warmup failure cannot invent measurement, and grace neither reissues a source call nor changes response certainty.
I: pass | Callers supply only the narrow source, observation, clock, pause and lifecycle actions the phase owner uses; they need no socket, handler or endpoint implementation interface.
D: pass | Traffic policy depends on PendingCall and JDK functional time/action ports; the concrete RequestFailure dependency only preserves public local-rejection reason values and never introduces transport ownership.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Active
sha256: ad934e440cb182b9884c07c076a07b98fde6b16df55c53b5af1e586192dea6e8
responsibility: Retains one admitted call with its original invocation, schedule and cohort index until observation or final cancellation.
consumers: TrafficRunner.issue creates entries, observe removes terminal entries, and phase cleanup cancels remaining entries.
S: pass | The record groups exactly the data required to attribute one active call to its originating cohort.
O: pass | Private tuple construction has no advertised extension point; varying operation behavior remains behind PendingCall.
L: pass | Generated record semantics preserve the call reference and primitive timestamps/index; the enclosing owner removes an entry only after the matching observed outcome, with no copied or replacement request engine.
I: pass | Only the call and original accounting metadata are exposed to the enclosing phase implementation.
D: pass | The retained variable collaborator is the narrow PendingCall port, with all other components primitive metadata.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Interval
sha256: ad934e440cb182b9884c07c076a07b98fde6b16df55c53b5af1e586192dea6e8
responsibility: Carries one original scheduled rate step and its immutable cohort snapshot for reporting.
consumers: TrafficMetrics builds at most the declared finite rate-step count; RunReport and criteria read rate, offset, duration and metrics.
S: pass | Only original schedule attribution and its already-accounted cohort are represented.
O: pass | This final record defines a closed report tuple; alternative traffic operations do not add behavior to it.
L: pass | Generated record accessors/equality preserve the supplied step and snapshot without shifting late outcomes into a later interval or conflating the longer drain with measurement duration.
I: pass | Consumers receive exactly rate, schedule offset, duration and metrics, without runner mutation or resource ownership methods.
D: pass | Only primitive metadata and the immutable CohortMetrics snapshot are retained; no clocks, executors or transports are accessed.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Phase
sha256: ad934e440cb182b9884c07c076a07b98fde6b16df55c53b5af1e586192dea6e8
responsibility: Carries one internal phase outcome, its observed duration/drain and immutable failure/interval lists.
consumers: TrafficRunner.phase constructs it from snapshots; run combines warmup and measurement while retaining unstarted/failed phases.
S: pass | The tuple represents the result of one phase and contains no lifecycle actions.
O: pass | It is a private final intermediate value with no supported subtype or variable behavior.
L: pass | All construction paths supply snapshot values and immutable List.of/List.copyOf-backed lists, preserving observed durations and partial failures when run assembles its result.
I: pass | Only the internal values needed to combine phases are exposed.
D: pass | It depends exclusively on immutable accounting values and JDK lists, not on runtime collaborators.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Result
sha256: ad934e440cb182b9884c07c076a07b98fde6b16df55c53b5af1e586192dea6e8
responsibility: Publishes the finite run cohorts, actual phase spans, measurement-start state, failures and original intervals.
consumers: SimulatorRun creates the report and criteria consume it; controlled tests also construct explicit truthful scenarios through convenience constructors.
S: pass | All members describe one finished or aborted traffic run; physical endpoint shutdown remains outside this value.
O: pass | The immutable result supports existing convenience construction without requiring endpoint subclasses or protocol-specific variants.
L: pass | The canonical constructor copies failure/interval lists, convenience constructors retain existing measurement-start inference, and original warmup/measurement snapshots are preserved on failure; drain now truthfully includes the measured receiver grace.
I: pass | The record exposes only observations needed by criteria/reporting, with no source, pause, cancel or transport operations.
D: pass | It uses accounting snapshots, primitive durations and copied JDK lists, keeping reporting independent of live runtime owners.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiverGraceTest.java
type: kg.aidarbek.simulator.ReceiverGraceTest
sha256: 6f7fcf8bf8e3c6cad272c5257f33dc7cffbf0453bf036ac34ed1e5caf3ae0ee2
responsibility: Verifies receiver service through the original measured drain boundary using controlled time and coordinated real bidirectional peers.
consumers: JUnit runs two profile cases, two load-model cases and three expiry/minimum-budget/interruption cases against TrafficRunner and public endpoint composition.
S: pass | Every case exercises a boundary of the receiver-grace contract or preservation of completed accounting and interruption.
O: pass | Profile/model parameters and existing injected timing ports vary behavior without adding a production hook, replacement transport or test-only phase API.
L: pass | Real peer requests receive actual paired responses after an explicit latch; pure-clock calls obey PendingCall observation/cancellation semantics, and finally blocks release and join the bounded peer worker and close both endpoint owners.
I: pass | Fixtures expose only the configured sessions/replies and clock controls needed for these assertions; no unrelated simulator/report API is required.
D: pass | The tests use public endpoint APIs for wire behavior and the existing PendingCall/time ports for deterministic deadlines; expected status zero, original deadline and exact counters are independently specified.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiverGraceTest.java
type: kg.aidarbek.simulator.ReceiverGraceTest.DuplexPeers
sha256: 6f7fcf8bf8e3c6cad272c5257f33dc7cffbf0453bf036ac34ed1e5caf3ae0ee2
responsibility: Owns one real local client, one real remote server, their registered receiver controllers and the resulting bound sessions.
consumers: The live-peer regression sends one data request in each direction and relies on bounded AutoCloseable cleanup after assertions or setup failure.
S: pass | It groups the inseparable lifecycle of one duplex test connection and contains no workload-generation policy.
O: pass | The profile parameter selects real 3.4 or 5.0 configuration through existing MessageTraffic registrations; the final fixture advertises no implementation substitution.
L: pass | Both endpoints and controllers are genuine implementations, startup waits are bounded, setup failure closes owned resources, and cleanup attempts both graceful shutdown owners through finally before hard-close/controller retirement.
I: pass | The enclosing test receives only its two sessions, configs and local receiver observation; no generic network testing interface is introduced.
D: pass | It composes public endpoint/configuration values and JDK futures; infrastructure details stay in the fixture while timing assertions remain in the test.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiverGraceTest.java
type: kg.aidarbek.simulator.ReceiverGraceTest.ScheduledCall
sha256: 6f7fcf8bf8e3c6cad272c5257f33dc7cffbf0453bf036ac34ed1e5caf3ae0ee2
responsibility: Models one already-transmitted PendingCall whose terminal outcome becomes visible at a controlled monotonic deadline.
consumers: The controlled-time cases inspect late success or cancellation without creating real traffic for timing-only boundaries.
S: pass | Only the observable terminal/cancellation state of one deterministic call is modeled.
O: pass | Clock and completion deadline vary the supported schedule directly; the private final fixture needs no subclass hierarchy.
L: pass | Poll stays empty before its wrap-safe deadline, then returns stable success, or stable cancellation if cancellation won; certainty stays MAY_HAVE_BEEN_SENT-equivalent and cancellation is idempotent for pending work.
I: pass | It implements exactly poll, certainty observation and cancellation from PendingCall, with a private counter used only to check unwanted cancellation.
D: pass | Its only variable input is the injected AtomicLong clock; it accesses no endpoint, sequence allocator or transport.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest
sha256: 475ae017aca05ae488cba7f9abb099f30f7e6cf76f3800d1c4264fa3b6e72aab
responsibility: Checks finite source/phase accounting, lifecycle ordering, failures and the observation-port contract.
consumers: JUnit runs seven deterministic scenarios, now asserting the configured measured grace while retaining the original warmup and lifecycle boundaries.
S: pass | Cases all concern one phase runner contract, including skipped identities, local rejection, unfinished cleanup and original-step attribution.
O: pass | Injected clocks, source callbacks and narrow PendingCall fixtures cover failure schedules without production branching for tests.
L: pass | All old cohort/count/cancellation/lifecycle assertions remain; the final-drain duration alone changes to the now-required configured five milliseconds, and source fixtures retain legal terminal/nonterminal behavior.
I: pass | The test uses only the runner constructor/result and PendingCall methods necessary to observe those contracts.
D: pass | Expected counts and timings come from explicit schedules and controlled inputs rather than copied runtime output; no socket implementation or hidden clock is required.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest#concurrencyRefillsOnlyFiniteSlotsAndAccountsForUnfinishedDrain/<anonymous>@240:54
sha256: 475ae017aca05ae488cba7f9abb099f30f7e6cf76f3800d1c4264fa3b6e72aab
responsibility: Represents one unresolved transmitted call occupying a finite concurrency slot through drain expiry.
consumers: The fixed-concurrency test creates exactly two calls and checks both become unfinished with one cancellation request each.
S: pass | Only indefinite pending visibility and cancellation observation are needed to expose the runner capacity bound.
O: pass | The local counter captures cancellation while the fixture otherwise defines one fixed pending behavior.
L: pass | Poll never fabricates a terminal response, certainty remains true and cancellation records the request without implying remote delivery or immediate terminal settlement.
I: pass | It implements exactly the PendingCall surface used by bounded phase cleanup.
D: pass | All behavior is local Optional/counter state; no second pending-request engine or network dependency exists.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest#lifecycleActionsBracketMeasurementAfterWarmupDrainAndBeforeFinalDrain/<anonymous>@19:41
sha256: 475ae017aca05ae488cba7f9abb099f30f7e6cf76f3800d1c4264fa3b6e72aab
responsibility: Represents one transmitted call released specifically by the after-scheduling lifecycle action.
consumers: The lifecycle-order test polls it during the final drain after verifying warmup and before-measurement ordering.
S: pass | It has only the terminal-visibility switch needed to distinguish lifecycle ordering from in-phase completion.
O: pass | The captured release flag supplies the single variable behavior; this local implementation is not an advertised extension point.
L: pass | Poll remains empty until explicit release and then reports stable success, certainty remains true, and unused cancellation has no physical resource to retire.
I: pass | It implements only the PendingCall observation/cancel operations consumed by the runner.
D: pass | It depends on the captured atomic release signal and immutable Completion value, without a real endpoint or clock.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest#maintenanceAndPauseFailuresKeepAdmissionsAndCancelPendingCallsExactlyOnce/<anonymous>@154:56
sha256: 475ae017aca05ae488cba7f9abb099f30f7e6cf76f3800d1c4264fa3b6e72aab
responsibility: Represents one unresolved transmitted call and records cancellation requested after a maintenance or pause failure.
consumers: The abort-accounting test verifies admitted work is retained as unfinished and that the runner requests cancellation exactly once.
S: pass | Its only behavior is pending visibility plus observation of the owner cancellation request.
O: pass | The captured cancellation counter supplies the required variation; no wider fake endpoint or inheritance contract is offered.
L: pass | It truthfully stays unsettled and already transmitted; cancel records a request rather than falsely promising a completed physical operation, matching the asynchronous PendingCall port.
I: pass | Only poll, certainty and cancellation are implemented.
D: pass | The fixture uses a local atomic counter and Optional without runtime resources or transport assumptions.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest.FakeCall
sha256: 475ae017aca05ae488cba7f9abb099f30f7e6cf76f3800d1c4264fa3b6e72aab
responsibility: Represents a permanently successful or still-unsettled call for deterministic runner schedules.
consumers: TrafficRunnerTest uses done=true for immediate successful observations and done=false for finite unfinished-drain accounting.
S: pass | The immutable boolean selects the single terminal-visibility behavior needed by these scenarios.
O: pass | The private final record intentionally offers two fixed behaviors rather than an extension hierarchy.
L: pass | Poll consistently returns the declared success or remains empty; certainty is consistently already-transmitted, and cancellation may remain unobserved as permitted by the narrow asynchronous observation port.
I: pass | Only the three PendingCall operations are implemented; the generated boolean accessor adds no lifecycle dependency for consumers.
D: pass | It depends on immutable completion values and Optional, without real timing or infrastructure.
findings: none
```

## Final-check appendix

Final formatted run07 executed **107 tests in 36 classes: 102 passed, five failed,
zero errors and zero skipped**. All seven `ReceiverGraceTest` cases passed. The
four failures in `SimulatorProcessTest` were existing eight-second child process
wait assertions (three at `pair:211`, one at the standalone path `:141`); the
existing six-pair receipt method in `HelperSimulatorTest` reached its 45-second
JUnit timeout. The corresponding XML, including stacks and timestamps, is
preserved in `/tmp/step19-receiver-final-results/`; nothing was omitted or
relabeled as successful. The process fixtures already declare short finite drains;
no default 30-second-drain assumption was used to explain these failures. Host
contention is a plausible contributor, not a proven per-process diagnosis from
these timeout-only assertions. Existing timeouts and production bounds were not
loosened. The required quieter full replay was pending at this checkpoint and
is recorded in the final integrated replay below. This scoped isolated run
itself remains a failed run.

`:simulator:javadoc` and `solidReviewInventory` completed in run07. Its inventory
contains all 13 affected identities listed above at the recorded formatted hashes.
The change adds seven test cases, one test class and three Java type identities;
all other affected types existed before the patch. Root's additional receipt and
lifecycle prerequisite tests remain separate from this isolated 107-case suite.

The immutable candidate2 matrix/soak/JFR artifacts remain tied to the old simulator
identity. Root independently assembled candidate3 from the exact one-production-
file checkpoint, retaining identical library bytes. New measurements must identify
that simulator separately; no new capacity or latency claim is made by this fix.

The explicit-file review checker completed successfully in
`/tmp/step19-receiver-08-review-check.log`, using the three owned Java files and
this report only. `/tmp/step19-receiver-reviewed.tsv` contains 13 current entries.
This is scoped review coverage, not a claim of checking copied prerequisite types.
Final `spotlessCheck :simulator:javadoc` passed in
`/tmp/step19-receiver-09-format-doc-check.log`; formatting checks executed and
Javadoc was up to date from its successful run07 generation. Source hashes were
rechecked after both commands and remain those above.

As separate fresh integration evidence, root ran candidate3 full 120-second
bidirectional W-WINDOW32 repetitions. The first completed repetition in each
profile was independently read from
`build/runs/step19-final-bidirectional/w-window-{34,50}-data/repeat-001/`.
All four role reports passed with complete cleanup, zero local rejections and only
SUCCESS measurement outcomes: 3.4 client/server 843,559/865,960 and 5.0
client/server 874,636/919,073. Actual measured spans were 120.000–120.003 s;
actual receiver drains were 30.000–30.001 s. This supports the corrected composition
at the recorded concurrency load and does not replace the retained failed
candidate2 runs or the required quieter replay of the five process-test deadlines.
No throughput capacity comparison or all-suites-green claim follows from it.

## Final integrated replay

The required quieter replay completed on 9 September 2026. Root's final
`check build solidReviewInventory` plus publication, distribution and simulator
Javadoc tasks freshly executed all 137 integrated simulator cases in 41 suites,
with zero failures, errors or skips. The matching 749 library results were
restored from the build cache and 60 unchanged review-tool results were reused.
This run took 117.264 seconds. Its log and command/result manifest are
`build/runs/step19-reproducibility/step19-final.log` and `step19-final.json`.

A separate fresh checkout then executed all 749 library, 137 simulator and
60 review-tool cases, including the seven receiver-grace cases and the earlier
process-test deadline assertions, with zero failures, errors or skips. It used
`--no-daemon --no-build-cache --no-configuration-cache` with fresh task outputs;
download caches were retained. The whole run took 128.559 seconds. Final source
hashes are unchanged from the records above, and all 512 current project Java
identities have fresh matching review coverage. The preserved isolated-run
failures remain evidence of that run; their bounds were not loosened.

The [release evidence](../RELEASE.md) records commands, artifact reproducibility
and the final XML manifest. The [measurement record](../MEASUREMENTS.md) separately
records all three full fixed-window repeats per profile and the completed
full-duration soak results. Both final soaks observed their complete 3600 s
measurement and original 30 s drain. All 31891984 admitted measurement calls
succeeded, with zero admission rejection or other terminal outcome and complete
cleanup; 4108016 skipped arrivals still failed the original workload criteria.
Successful deterministic verification and measured workload criteria remain
distinct. No source or test assertion changed after the reviewed correction.
