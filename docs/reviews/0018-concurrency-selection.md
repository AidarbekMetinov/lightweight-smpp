# Review: fixed-concurrency session selection

Fixed-concurrency generation now chooses a session slot with locally tracked
pending room. One session completing a request no longer causes the generator
to revisit another session whose local window remains occupied. Independent
arrival traffic retains its declared offered-index routing.

## Observed defect and policy

The preserved Step19 diagnostic
`build/runs/step19-candidate-matrix/cells/34-submit-w-window-32/repeat-001/client/report.json`
recorded2 warmup `WINDOW_FULL` refusals and349 measurement refusals for100
sessions with window32. All686,066 admitted warmup requests and3,891,463 admitted
measurement requests succeeded. The measured aggregate pending peak was2,897,
below the3,200 global cap; measurement had3,891,812 attempted requests.
Those reports remain unchanged. The library correctly enforced each session's
own window. The old generator combined a global active-count bound with
unconditional `index % connections` routing, so a completed request on one
session did not ensure capacity on the next selected session.

The correction applies only to `FIXED_CONCURRENCY`. `SessionTrafficSource`
rotates among configured slots with locally observed pending counts below their
window. It reserves a local count before calling the sender once. A thrown
refusal is propagated unchanged and releases that tentative count; no request
is replayed on another session. The library remains authoritative for real
admission, including byte, control-request, notification and transport pressure.
Local counters do not claim an atomic `SessionResources` observation or promise
that admission cannot be rejected.

One wrapper per admitted `PendingCall` releases the count exactly once when
the owner polls an actual terminal outcome. Calling cancel alone never releases
it, even when cancellation later settles the underlying request. Transmission
certainty and terminal results remain delegated to the underlying observation.
The source adds one array of at most4,096 counters and no queue or request
engine; configured total local ownership is at most65,536. Selection examines
at most the configured number of sessions. The existing traffic owner serializes
source invocation, cancellation and terminal polling, so no new worker or lock
is required.

`SimulatorRun` creates payload content and calls `endpoint.nextOrdinal(slot)`
using the selected slot. The original offered index remains a distinct sender
argument. Warmup and measurement reuse the same source; an unfinished warmup
still prevents measurement. Session replacement remains responsible for fresh
generation ordinals. This change does not reset ordinals, alter SAR incomplete
accounting, suppress refusals, change arrival routing, or invent an offered-rate
denominator for concurrency mode.

## Scope and source identities

The accepted Step18 `SimulatorRun.java` baseline SHA-256 was
`38ef9453a8462740d275447e70fdfcf38e326cbe23727af9c759cef0bfc89f59`.
This follow-up changes that composition file, adds `SessionTrafficSource` and
one test file, and reviews their six identities below. The already integrated
sampling follow-up and copied library/lifecycle prerequisites are excluded.

The early Java checkpoint was sent after focused meaningful green tests and
separate formatting. Its two production file hashes remain final. Subsequent
test-only review corrected a fixture's transmission transition to move from
not-sent to possibly-sent, matching the underlying request contract; no
production change followed the checkpoint.

## Actual TDD and verification

Commands run in `/tmp/lightweight-smpp-step18`; every Gradle invocation appends
`--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'`.

1. `/tmp/step19-concurrency-01-red.log`: extracted the existing round-robin
   source into the narrow composition seam and ran `./gradlew :simulator:test
   --tests 'kg.aidarbek.simulator.SessionTrafficSourceTest'`. Compilation passed;
   the asymmetric two-session regression failed because the third request
   selected still-full slot0 after slot1 completed. Its assertion received the
   fixture's real bounded-admission `RejectedExecutionException`.
2. `/tmp/step19-concurrency-02-green.log`: local fixed-mode counts, bounded
   selection and terminal-observation wrappers made the unchanged regression
   pass with `TrafficRunnerTest`.
3. `/tmp/step19-concurrency-03-contracts.log`:20 cases across
   `SessionTrafficSourceTest`, `TrafficRunnerTest`, `HelperSimulatorTest` and
   `SimulatorArchitectureTest` passed freshly. The added characterizations
   cover cancellation retention, terminal-poll idempotence, unchanged refusal
   identity without replay, arrival routing, and controlled runner accounting.
   The asymmetric runner/SAR case observes slots0,1,1,1; offered indices0,1,2,3;
   per-slot ordinals0,0,1,2; SAR part numbers1,1,2,1; and exactly4 admitted
   successes with zero rejections and peak pending2. Existing real helper
   process cases cover both protocol profiles.
4. `/tmp/step19-concurrency-04-format.log`: separate `./gradlew spotlessApply`
   before the initial production checkpoint and final simulator build.
5. `/tmp/step19-concurrency-05-final-check.log`: `./gradlew :simulator:check
   :simulator:javadoc :simulator:installDist solidReviewInventory` passed100
   fresh simulator cases in35 classes, with zero failures/errors/skips. The
   preserved XML files are `/tmp/step19-concurrency-final-results/`.
6. `/tmp/step19-concurrency-06-fixture-contract.log`: after the test-only
   monotonic-certainty correction, all5 selector cases passed freshly.
   `/tmp/step19-concurrency-07-final-format.log` separately formatted the final
   sources. `/tmp/step19-concurrency-08-formatted-check.log` passed inventory and
   formatting checks; the matching five-case test input was `UP-TO-DATE`, not a
   fresh measurement.

`/tmp/step19-concurrency-09-review-check.log` passed focused review coverage with
the repository's `ReviewCheck --check-files` using exactly these three Java
sources and this report; `/tmp/step19-concurrency-coverage.tsv` lists all6
identities. The
whole-worktree inventory independently supplies the same six source/type/hash
triples. These behavior and architecture checks do not establish a workload
capacity target; the corrected candidate needs a fresh W-WINDOW campaign.

## Whole-type review

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SessionTrafficSource.java
type: kg.aidarbek.simulator.SessionTrafficSource
sha256: 614fc49c05f8e873fa6103ca73d39473f28399d532fc0bcbe7cb842df5e1b071
responsibility: Selects originating session slots according to the declared arrival or fixed-concurrency model and owns only local fixed-mode quotas.
consumers: SimulatorRun injects the actual per-slot sender; the single TrafficRunner owner calls LongFunction.apply and polls returned observations.
S: pass | Model routing and local slot reservations are one source-admission responsibility; wire commands, session state, timing, payload construction and cohort accounting stay in existing collaborators.
O: pass | The injected BiFunction supplies operation/session/content variation without altering selection; the two declared LoadPlan models remain explicit policies rather than a new plugin framework.
L: pass | LongFunction accepts the offered index and propagates sender failures once; constructor enforces finite connection/window limits, fixed mode never exceeds its local quota, arrival preserves floorMod routing, and source rejection unwinds only the tentative reservation without replay.
I: pass | TrafficRunner consumes only the existing LongFunction port; the sender receives the selected slot and original offered index, with no sampled resource or protocol-engine API requirement.
D: pass | Depends on PendingCall and JDK function/value types; library admission remains inside the injected sender and no RequestWindow, endpoint implementation, executor or transport is constructed.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SessionTrafficSource.java
type: kg.aidarbek.simulator.SessionTrafficSource.ObservedCall
sha256: 614fc49c05f8e873fa6103ca73d39473f28399d532fc0bcbe7cb842df5e1b071
responsibility: Holds one local slot until the owning traffic loop observes a terminal result for its admitted call.
consumers: TrafficRunner uses the unchanged PendingCall poll/certainty/cancel contract; its enclosing source receives one counter decrement.
S: pass | The slot index, delegate and released flag serve one local reservation lifecycle, without duplicating response correlation or terminal outcome selection.
O: pass | Any conforming PendingCall can be wrapped; operation and terminal-result variants remain delegated and do not require changes to quota release.
L: pass | Poll returns the delegate result and releases exactly once only when present; cancel and transmission certainty delegate unchanged, repeated terminal polling cannot underflow quota, and cancellation retains ownership until actual observation as tested.
I: pass | Implements exactly the three existing observation methods needed by TrafficRunner; it exposes no extra callback, future or local-counter manipulation API.
D: pass | Uses the existing PendingCall abstraction and its enclosing bounded counter array; no library request internals or sampled-session counters determine ownership.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorRun.java
type: kg.aidarbek.simulator.SimulatorRun
sha256: b121f766258a05563bb80885a1eb410e5fe81eb0775656507a32f71f1893b56b
responsibility: Composes one finite simulator execution from validated configuration through endpoint ownership, generation, observations, cleanup and final criteria/report publication.
consumers: SimulatorMain supplies explicit configuration, credentials and event output; focused process tests exercise helper/profile and failure paths.
S: pass | Methods coordinate the single run lifecycle while scheduling, source selection, endpoint replacement, receiver decisions, measurements and report calculations remain in focused collaborators.
O: pass | Existing operation/content lookup and injected endpoint/sampling actions retain their supported variation; SessionTrafficSource adds the missing selection policy without rewriting TrafficRunner or message helpers.
L: pass | Preflight still precedes endpoint resources, partial accounting and cleanup failures remain reported, all original callback/receiver bounds persist, and selected-slot content construction preserves per-generation ordinals while arrival routing and real helper process behavior remain covered.
I: pass | The launcher needs only execute; collaborator inputs carry the relevant narrow sending, maintenance, sampling and cleanup operations without exposing private request-engine state.
D: pass | This is the intentional composition boundary using public endpoint capabilities and tool collaborators; the new source receives a sender function and library protocol admission remains delegated rather than sampled or reimplemented.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SessionTrafficSourceTest.java
type: kg.aidarbek.simulator.SessionTrafficSourceTest
sha256: 39c2cfd885281e104a4363b4c86d70d77296cec92b45015d91342c8a3ed61386
responsibility: Verifies asymmetric session selection, observation-based release, unchanged arrival/refusal behavior and selected-slot content/accounting composition.
consumers: JUnit runs five bounded cases with the real source, TrafficRunner, helper content and explicitly controlled PendingCall observations.
S: pass | Every case probes the source-selection and local-ownership contract; the runner/SAR case checks its actual consumer boundary without introducing sockets or a second request engine.
O: pass | Existing function and PendingCall seams vary completion order, cancellation and refusal; these variations do not need production-only test hooks or broader fixture inheritance.
L: pass | The first assertion reproduced the old bounded-session refusal; controlled outcomes are stable after completion, cancellation is independently observable, certainty moves only forward, and exact counters/indices/ordinals ensure no hidden replay or lost accounting.
I: pass | JUnit needs only five test methods; private value/observation fixtures provide the small surfaces each case consumes.
D: pass | Depends on actual tool policies and their narrow observation/function seams; independently chosen two-session bounds and SAR sequence expectations do not derive expected selections from the implementation.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SessionTrafficSourceTest.java
type: kg.aidarbek.simulator.SessionTrafficSourceTest.ControlledCall
sha256: 39c2cfd885281e104a4363b4c86d70d77296cec92b45015d91342c8a3ed61386
responsibility: Supplies one explicitly completed request observation with separately controlled cancellation and transmission certainty.
consumers: Source wrappers and TrafficRunner consume PendingCall; enclosing tests choose completion order and inspect cancellation delivery.
S: pass | Terminal value, cancellation count and certainty are the three observations of the same test request lifecycle.
O: pass | Existing Outcome values and explicit completion choose required scenarios without command-specific fixture classes or production changes.
L: pass | Poll is empty until explicit completion and stable afterward in every fixture flow; cancel records the request without fabricating completion, and the certainty characterization progresses from false to true, matching real request observation semantics.
I: pass | Implements only the existing poll/certainty/cancel port; private completion setup is not exposed as an application capability.
D: pass | Uses only the tool observation/value contract and Optional; no transport, session, time scheduler or callback executor is faked.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SessionTrafficSourceTest.java
type: kg.aidarbek.simulator.SessionTrafficSourceTest.Emission
sha256: 39c2cfd885281e104a4363b4c86d70d77296cec92b45015d91342c8a3ed61386
responsibility: Records the chosen slot, original offered index, stream ordinal and independently decoded SAR part for one test send.
consumers: The integrated runner case compares an exact immutable sequence of four emissions against the expected asymmetric routing.
S: pass | All primitive fields describe the same observed send and expose no unrelated behavior.
O: pass | Test sequences vary through record values; routing and content policies remain outside this immutable evidence value.
L: pass | Primitive record components give stable value equality and hash code, with no mutable alias or resource-ownership contract.
I: not applicable | No custom interface is implemented; the test uses record equality and its declared primitive components only.
D: pass | Contains only primitive evidence values and no dependency on endpoint or source internals.
findings: none
```
