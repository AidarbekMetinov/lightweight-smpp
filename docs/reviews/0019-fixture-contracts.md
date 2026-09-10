# Readiness audit: endpoint fixture contracts

The [fresh endpoint audit](0019-endpoint-audit.md) found observable differences
between two test ports and the `FrameTransport` contract. The library production
implementation already enforces the relevant ownership barriers; no production
source changed. All work below was performed sequentially in the isolated
`/tmp/lightweight-smpp-endpoint-audit` checkout at
`094a84f595b61779904c0190ef15f6430561cd22`, without root-worktree edits or commits.

## Contract and correction

`beforeWrite()` returning true is conservative permission, not an obligation to
emit bytes after closure or expiry. An accepted write keeps its original deadline
through queueing and active progress. A transport abort must retain its reason,
rather than impersonating explicit queued cancellation. Connected/frame/guard
failures close with `OBSERVER_FAILED`; successful cleanup still terminates
normally. A failed terminal callback separately produces `CLEANUP_FAILED`.
Termination waits for every already-entered internal listener/write callback.
These obligations were checked against the independently reviewed SPI and real
TCP adapter, not inferred from the old fixture implementation.

`FakeFrameTransport` now rechecks closure and its original deadline after the
guard, before atomically recording output. It tracks connected/frame invocation
ownership through return and failure, and defers termination until that ownership
retires. Queued writes aborted by the transport retain the closure cause and
reason. Internal callback invocation and completion publication stay outside
fixture locks; the already-claimed write remains counted through its terminal
callback. The fixture's one-time settlement and queued cancellation behavior
are preserved.

`CommonTestTransport.Pending` now retains the write deadline. Driven queued
progress checks it before invoking the guard; claimed completion checks it before
recording bytes. Active expiry closes the stream, whereas queued expiry leaves
unrelated healthy work usable. Progress failures retain their original transport
reason while successful cleanup completes normally. Terminal callback failures
still settle the rest of the accepted work and fail cleanup, while also reporting
the original observer failure to the listener. No background timer or additional
production scheduler was added to these explicitly driven fixtures.

One existing assertion in `AlertWriteContractTest` expected exceptional cleanup
after a throwing guard even though its failure notification and physical cleanup
both succeeded. That expectation contradicted the SPI. It was changed only
after the independent regression demonstrated the wrong progress classification.
The separate terminal-callback cleanup-failure test remains and still requires
exceptional `CLEANUP_FAILED` with all other accepted writes settled. No production
assertion was weakened and no concurrency coverage was removed.

## Actual sequential TDD evidence

Every Gradle invocation below used the same checkout and exact suffix:

```sh
--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=endpoint-audit'
```

The table's task/selection is appended to `./gradlew` before that suffix. Each
listed `/tmp/maven-endpoint-*.log` is the combined stdout/stderr from that actual
command. Red evidence is an executed behavioral assertion failure, not a compiler
or environment failure. Green runs executed the selected `:test` task; normal
compilation and configuration cache reuse remained enabled.

| Order and log basename | Task / selection | Actual outcome |
| --- | --- | --- |
| `01-guard-red.log` | `:test --tests kg.aidarbek.smpp.endpoint.FakeFrameTransportTest` | Six cases, two assertion failures: close and expiry during a claimed guard both reported success. |
| `02-guard-green.log` | `:test --tests kg.aidarbek.smpp.endpoint.FakeFrameTransportTest --tests kg.aidarbek.smpp.endpoint.EndpointConnectionTest` | Selected cases pass after the post-guard lifecycle/deadline check. |
| `03-listener-red.log` | `:test --tests kg.aidarbek.smpp.endpoint.FakeFrameTransportTest` | Eight cases, two assertion failures: termination was complete while connected/frame callbacks were gated. |
| `03b-listener-errors-red.log` | `:test --tests kg.aidarbek.smpp.endpoint.FakeFrameTransportTest` | Ten cases, four assertion failures: the two retirement failures plus escaped connected/frame exceptions. |
| `04-listener-green.log` | `:test --tests kg.aidarbek.smpp.endpoint.FakeFrameTransportTest --tests kg.aidarbek.smpp.endpoint.EndpointConnectionTest --tests kg.aidarbek.smpp.endpoint.ExchangeConnectionTest` | Selected cases pass after listener invocation ownership and failure handling. |
| `05-deferred-deadline-red.log` | `:test --tests kg.aidarbek.smpp.endpoint.CommonTestTransportTest` | Two cases fail: expired queued work still claimed its guard; expired active work recorded successful output. |
| `06-deferred-deadline-green.log` | `:test --tests kg.aidarbek.smpp.endpoint.CommonTestTransportTest --tests kg.aidarbek.smpp.endpoint.AlertWriteContractTest --tests kg.aidarbek.smpp.endpoint.AlertHandlerContractTest` | Selected cases pass with retained original deadlines and queued/active distinction. |
| `07-progress-failure-red.log` | `:test --tests kg.aidarbek.smpp.endpoint.CommonTestTransportTest` | Five cases, three assertion failures: connected/frame/guard failures reported `CLOSED` instead of `OBSERVER_FAILED`. |
| `08-progress-failure-green.log` | `:test --tests kg.aidarbek.smpp.endpoint.CommonTestTransportTest --tests kg.aidarbek.smpp.endpoint.AlertWriteContractTest --tests kg.aidarbek.smpp.endpoint.AlertHandlerContractTest` | Selected cases pass, including normal successful cleanup after progress failure and the retained exceptional terminal-callback test. |
| `09-abort-reasons-red.log` | `:test --tests kg.aidarbek.smpp.endpoint.FakeFrameTransportTest --tests kg.aidarbek.smpp.endpoint.CommonTestTransportTest` | Nineteen cases, four assertion failures: two fake queued aborts were labelled `CANCELLED`; common written/failed callback errors were labelled `CLOSED`. |
| `10-endpoints-green.log` | `:test --tests 'kg.aidarbek.smpp.endpoint.*'` | 254 cases in 39 classes, zero failures/errors, including all corrected fixture paths and existing endpoint consumers. |
| `11-format.log` | `spotlessApply` | Formatter executes successfully, separately before final inventory and hash review. |
| `12-inventory.log` | `solidReviewInventory` | Inventory executes successfully; compiler-backed discovery supplies the final 21 affected identities below. Review-tool compilation is restored from the normal cache. |
| `13-library-green.log` | `:test` | Post-format complete library run executes 764 cases in 114 classes, zero failures/errors/skips; production/examples compilation remains up to date. |
| `14-check-build.log` | `check build` | Full checks/build pass: 23 tasks, 11 executed, six from cache, six up to date. Library tests are up to date from the fresh 764-case run; 137 simulator and 60 review-tool cases are restored from matching caches. All 523 current Java identities have matching reviews. Formatting and coverage execute; Javadoc is restored from cache without warnings. |

The new assertions use finite gates to establish callback ownership. Deadline
cases wait until the original absolute monotonic deadline, then explicitly drive
the deferred action; no retry extends the deadline. All gate releases and close
operations are in cleanup paths. Runtime exceptions represent deliberate faulty
internal callbacks. Such fault gates are not a claim that blocking an internal
port is valid application behavior. Existing real TCP/TLS timeout and cleanup
suites continue to test the actual autonomous adapter.

There are fifteen added cases: eight in `FakeFrameTransportTest` and seven in
the new `CommonTestTransportTest`. No test class moved to a production source set.
The changes introduce no library dependency and leave SPI files owned by the
protocol reviewer unchanged. The separate audit records two documentation-only
contract qualifications and the exact 133-file baseline coverage.

## Final whole-type SOLID review

The following 21 blocks cover every type in all five changed/new Java files,
including every anonymous listener/observer and unchanged nested fixture in a
changed file. Each hash is from the post-formatter whole-file inventory. The
review covers complete current types, not only changed lines; earlier blocks
for old hashes remain historical. No temporary Java source or external Java
fixture was introduced by this audit.

After adding the final result narrative, the exact final report coverage command
is `./gradlew solidReview --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=endpoint-audit'`,
with output retained in `/tmp/maven-endpoint-15-final-coverage.log`.

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AlertWriteContractTest.java
type: kg.aidarbek.smpp.endpoint.AlertWriteContractTest
sha256: 23ab6a14d045f2e5fcda48ca0f4b698411a7a93369e74ac6242d2f76ba8d4792
responsibility: Verifies one-way alert local-write admission, cancellation, result publication and graceful drain, including its controlled port obligations.
consumers: JUnit runs the full class; NotificationExchange and the port fixture are observed through results, independent frame headers and retained callback counts.
S: pass | Every case concerns notification write ownership; socket setup and guard behavior are factored into the two narrow nested fixtures.
O: pass | Scenarios vary the existing deferred-port and notification APIs without a second request or alert implementation.
L: pass | The corrected guard assertion follows FrameTransport successful-cleanup semantics; terminal callback failure still requires CLEANUP_FAILED and settlement of other writes.
I: pass | Tests consume AlertSender, NotificationSend and the small SPI contract rather than a general callback facade.
D: pass | Coordinator behavior is driven through a FrameTransport substitute; the same shared ownership contract is independently applied to actual TCP/TLS adapters.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AlertWriteContractTest.java
type: kg.aidarbek.smpp.endpoint.AlertWriteContractTest.Fixture
sha256: 23ab6a14d045f2e5fcda48ca0f4b698411a7a93369e74ac6242d2f76ba8d4792
responsibility: Owns one bound test MC coordinator, controlled transport, authentication dispatcher and bounded notification pool.
consumers: The enclosing alert tests obtain the fixed session/transport and close this fixture after each scenario.
S: pass | Setup and teardown for one bound MC are its single responsibility; behavior assertions remain in the enclosing test.
O: pass | Only fixed test construction is needed; the transport and dispatcher seams already supply deliberate scenario variation.
L: pass | AutoCloseable closes coordinator and owned workers and awaits their finite termination; no supplied application executor is shut down.
I: pass | The package-private fixture exposes only the test collaborators it actually owns and a close operation.
D: pass | It is the test composition root for concrete in-memory collaborators, while the production coordinator receives the frame port.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AlertWriteContractTest.java
type: kg.aidarbek.smpp.endpoint.AlertWriteContractTest.GuardObserver
sha256: 23ab6a14d045f2e5fcda48ca0f4b698411a7a93369e74ac6242d2f76ba8d4792
responsibility: Records terminal write/failure counts and optionally throws at a selected guard or failure boundary.
consumers: The enclosing controlled-port tests pass it as WriteObserver and assert exact callback counts after driven completion.
S: pass | It observes one small write lifecycle; it has no transport, deadline or application policy.
O: pass | Two explicit fault flags cover the needed observer scenarios without subclass hierarchies.
L: pass | It implements all WriteObserver methods; deliberate guard/terminal throws are fault injection whose settlement/cleanup expectations are asserted separately.
I: pass | The three-method write observer is the only implemented contract and every method contributes to the asserted lifecycle.
D: pass | It depends only on the SPI failure value and local counters; callback timing is controlled by the enclosing fixture.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransport.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransport
sha256: e01f6403a4da3cbabfd8a0ff3ca819a0db62614d9c2a8496dda14ad06bf9b38b
responsibility: Provides a finite frame-port test instrument with explicitly driven guard claim, cancellation and terminal settlement.
consumers: Common-operation, alert and lifecycle coordinator tests use it as FrameTransport; CommonTestTransportTest challenges its delayed and failure contracts.
S: pass | Pending-frame ownership and driven I/O are cohesive; protocol interpretation, request tracking and application dispatch stay with the consumer.
O: pass | Explicit deferred/cancellation hooks vary only tested progress order; finite capacity and the original deadline remain enforced for every driven path.
L: pass | The corrected substitute copies frames, rejects late queued progress, prevents late active output, preserves abort causes and distinguishes successful cleanup from failed terminal callbacks.
I: pass | It implements only the frame SPI, with package-private driver operations used by deterministic tests; it adds no business-service methods.
D: pass | Only JDK state and SPI listener/observer values are required; callbacks run outside its guard and the actual adapter separately owns autonomous network timers.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransport.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransport.Pending
sha256: e01f6403a4da3cbabfd8a0ff3ca819a0db62614d9c2a8496dda14ad06bf9b38b
responsibility: Holds one admitted owned frame, observer, queue class, immutable deadline and guarded claim state until settlement.
consumers: Only CommonTestTransport admission, claim, cancellation and settle paths access this private entry.
S: pass | All fields describe the lifetime of exactly one accepted write, including the newly retained original deadline.
O: pass | A private final state holder has no independent variation point; owner logic interprets its queue class and claim.
L: pass | It introduces no subtype behavior or exposed equality; its frame was already defensively copied at transport admission and state is accessed under the owner guard.
I: pass | No public interface or unrelated accessors expose entry mutation to coordinator consumers.
D: pass | It stores primitive/JDK data and the narrow WriteObserver contract, with no protocol or concrete socket dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransportTest.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransportTest
sha256: aff782e24e91702de8e3c1e7d5ff399bc29778c7b2e6da0cde69b2e7f1f6531e
responsibility: Checks the controlled port retains original deadlines and correct connection-versus-cleanup failure classification.
consumers: JUnit runs seven parameterized queued/claimed and callback-phase cases against the complete CommonTestTransport fixture.
S: pass | Each assertion concerns a frame-port temporal or terminal contract; no endpoint business behavior is duplicated.
O: pass | Named callback phases and one claim-state parameter cover needed variation with the existing driver seam.
L: pass | Both queued/active deadline cases and progress/terminal error distinctions derive from FrameTransport; original causes and no output are asserted after actual reds.
I: pass | Tests use only start/write/termination and explicit fixture progress, with narrow anonymous listener/observer implementations.
D: pass | The expected outcomes come from the SPI and real adapter audit; finite monotonic waits drive original deadlines rather than extending or replacing them.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransportTest.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransportTest#deferredProgressCannotEmitAFrameAfterItsOriginalDeadline/<anonymous>@105:45
sha256: aff782e24e91702de8e3c1e7d5ff399bc29778c7b2e6da0cde69b2e7f1f6531e
responsibility: Observes transport closure while a deferred write is driven past its original deadline.
consumers: CommonTestTransport invokes this FrameListener; the enclosing case checks active expiry closes while queued expiry does not.
S: pass | It records only the close reason; connected and frame callbacks are intentional no-ops for this write-only scenario.
O: pass | The queued/claimed variation belongs to the enclosing case, so the listener needs no configurable behavior.
L: pass | The listener returns promptly and stores the exact original failure once in the test future, without changing transport ownership.
I: pass | It implements the three required frame-listener callbacks with no unrelated application methods.
D: pass | It depends only on the SPI failure and a local observation future; it does not close the port or decide deadlines.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransportTest.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransportTest#deferredProgressCannotEmitAFrameAfterItsOriginalDeadline/<anonymous>@120:108
sha256: aff782e24e91702de8e3c1e7d5ff399bc29778c7b2e6da0cde69b2e7f1f6531e
responsibility: Counts guard entry and records whether a deferred write reports success or its original timeout failure.
consumers: The controlled port invokes it; the test independently requires zero/one guards, no frame output and the appropriate timeout certainty.
S: pass | Guard count and terminal outcome describe one write and do not duplicate the port state machine.
O: pass | A single permissive observer supports both queued and already-claimed deadline cases.
L: pass | It returns true without blocking, preserves the supplied failure, and lets the enclosing assertions detect an illegal late success.
I: pass | Only the three WriteObserver methods are implemented; no listener or request-handler responsibility is combined.
D: pass | Atomic count and local CompletableFuture observation introduce no network, timer or protocol dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransportTest.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransportTest#failedCallbacksPreserveTheCauseAndDistinguishProgressFromCleanup/<anonymous>@31:54
sha256: aff782e24e91702de8e3c1e7d5ff399bc29778c7b2e6da0cde69b2e7f1f6531e
responsibility: Injects a selected connected/frame progress error and observes the resulting transport close reason.
consumers: The failure-classification cases pass it to CommonTestTransport and require the original cause plus correct cleanup behavior.
S: pass | It models only listener progress failure and closure observation for one case.
O: pass | Two named progress phases share this listener without subclassing or introducing a second callback dispatcher.
L: pass | Deliberate unchecked progress failure is the documented fault under test; closed itself completes promptly and preserves the exact supplied reason.
I: pass | The listener implements only the required connected/frame/closed contract.
D: pass | Local failure identity and observation future are independent of the fixture cleanup implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransportTest.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransportTest#failedCallbacksPreserveTheCauseAndDistinguishProgressFromCleanup/<anonymous>@56:45
sha256: aff782e24e91702de8e3c1e7d5ff399bc29778c7b2e6da0cde69b2e7f1f6531e
responsibility: Injects a guard, written or failed callback exception at the selected write boundary.
consumers: CommonTestTransport invokes it; tests separate OBSERVER_FAILED connection outcome from successful or failed cleanup.
S: pass | All branches represent one write-observer fault boundary, with no responsibility for transport cleanup.
O: pass | Named fault phases reuse the minimal observer contract and keep expected classification in independent assertions.
L: pass | It deliberately throws only at the selected method; a refused guard drives the failed-callback case, while ordinary guard failure is observed as a single supplied failure.
I: pass | It implements only WriteObserver and stores no unrelated session state.
D: pass | It uses local failure/future values and cannot manufacture the transport termination result it asserts.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransport.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransport
sha256: 93fc2e1e47ae78e88f06c1702ce30be1dfdff82ba17f030dad0e4eb9e1762989
responsibility: Supplies bounded immediate or explicitly deferred complete-frame I/O for coordinator tests, including selected observer and cleanup faults.
consumers: Endpoint, exchange, keepalive and two-carrier regression fixtures inject it through FrameTransport; direct fake tests verify its ownership contract.
S: pass | It owns fake frame admission, callback lifetimes and termination, while endpoint state and message behavior remain in real collaborators.
O: pass | Existing finite deferred/failure hooks vary scheduling and faults; correcting listener ownership does not add a parallel scheduler or protocol implementation.
L: pass | Close and expiry prevent post-guard output, aborts preserve queued causes, callback failures settle once, and termination waits for active writes plus entered listener callbacks.
I: pass | Consumers receive the frame port; package-private queues/hooks are confined to explicit test control and not published as a library capability.
D: pass | It uses SPI callbacks and JDK bounded state; all callback callouts and termination publication occur outside fixture locks.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransport.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransport.PendingWrite
sha256: 93fc2e1e47ae78e88f06c1702ce30be1dfdff82ba17f030dad0e4eb9e1762989
responsibility: Owns one copied accepted write from deferred admission through one guard/cancel winner and one terminal callback.
consumers: FakeFrameTransport admission returns its WriteHandle; tests may explicitly send a deferred entry, and abort settles queued entries with the transport cause.
S: pass | Its once-only settlement, deadline and callback accounting belong to one write, with aggregate ownership delegated to the enclosing transport.
O: pass | One shared settle path handles send, queued cancel and transport abort; no duplicated output engine or additional operation subtype is needed.
L: pass | WriteHandle cancellation wins only before settlement/claim; a permitted guard is rechecked against closure/deadline and cannot force bytes after either wins.
I: pass | The public contract is only cancel; explicit send remains a test-driver operation, and frames/observer/deadline stay private.
D: pass | It invokes the SPI observer after releasing its own monitor, never nests that monitor across the owner guard, and retains aggregate capacity until callback return.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Challenges immediate/deferred fake transport closure, deadlines, callback failure and physical callback-retirement barriers.
consumers: JUnit parameterization exercises twelve cases; the fixture is observed through frames, counts, original failures and protected termination.
S: pass | All scenarios assert frame-port behavior independently from the endpoint request engine; protocol headers are fixed minimal test input.
O: pass | Parameterized close/expiry and callback-phase variants use finite hooks without adding fake-only production behavior.
L: pass | Assertions distinguish connection outcome from cleanup, including normal cleanup after progress failure and exceptional terminal callback failure; eight new cases first failed behaviorally.
I: pass | Anonymous listener and observer fixtures implement just their small SPI surfaces; gates and outcome futures are local to each case.
D: pass | Tests rely on independent SPI semantics and explicit monotonic deadlines, not production transport internals or a second request implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#closeOrDeadlineDuringAClaimedGuardPreventsEveryFrame/<anonymous>@172:45
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Supplies a neutral started listener for the claimed-write close/deadline race.
consumers: The fake transport invokes this listener while the enclosing case observes only write output and termination.
S: pass | It only enables frame-port startup; it intentionally contributes no protocol or fault policy.
O: pass | Both race variants need the same neutral listener, so no variable behavior or subclass seam is warranted.
L: pass | All callbacks return promptly and cannot interfere with the separately gated write observer.
I: pass | The three required listener methods are present and no extra callback interface is implemented.
D: pass | The listener has no state or dependency and does not consult fixture implementation details.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#closeOrDeadlineDuringAClaimedGuardPreventsEveryFrame/<anonymous>@185:120
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Gates a claimed write guard and records its terminal outcome after closure or expiry wins.
consumers: The fake writer thread invokes it; the enclosing test waits for exact guard entry, selects the winner and checks zero output.
S: pass | Gate lifetime and the single terminal outcome describe one write race.
O: pass | The same observer supports both close and deadline variants using externally controlled finite latches.
L: pass | The deliberate bounded guard pause exposes the conservative true contract; true after release must still permit the transport to reject output, which the assertions require.
I: pass | It implements only WriteObserver and restores interrupt status on a failed gate wait.
D: pass | Latches/future establish the ordering and outcome independently of private transport state.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#failedListenerProgressAbortsThePortWithoutEscapingToItsCaller/<anonymous>@85:54
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Injects an unchecked connected or frame failure and captures the resulting close cause.
consumers: The listener progress regression installs it and checks no exception escapes the fixture caller.
S: pass | It models one progress failure and its close notification, not cleanup publication.
O: pass | The selected event controls one explicit failure branch; no general fault framework is introduced.
L: pass | The deliberate failure is observable through the close callback while that terminal callback itself completes normally, separating connection failure from cleanup failure.
I: pass | It implements only FrameListener and adds no application-handler surface.
D: pass | Original failure identity and the local close future are independent of the transport implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#observerFailuresCloseAndSettleOwnedWorkWithoutASecondTerminalCallback/<anonymous>@239:45
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Counts close notifications, preserves their reason and optionally throws from the terminal close callback.
consumers: The pre-existing observer fault matrix uses it to distinguish one connection notification from failed callback cleanup.
S: pass | It observes the listener terminal boundary only; write counters belong to the separate observer.
O: pass | The named fault phase selects the close exception without a new listener subtype.
L: pass | Deliberate terminal failure must be retained as CLEANUP_FAILED; close count and cause are captured first so the test can independently assert exactly once notification.
I: pass | Neutral connected/frame methods and one close observer satisfy the small listener interface.
D: pass | Atomic observations and fixed failure identity do not depend on the fake cleanup implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#observerFailuresCloseAndSettleOwnedWorkWithoutASecondTerminalCallback/<anonymous>@253:54
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Counts terminal write callbacks and injects the selected guard/written/failed observer fault.
consumers: The pre-existing matrix passes it to immediate/deferred writes and checks that a faulty terminal callback is never followed by another terminal callback.
S: pass | The three methods describe one internal write-observer lifecycle and its selected fault.
O: pass | Named fault phases vary the explicit contract boundary without duplicating transport state.
L: pass | Guard refusal and unchecked faults are intentional inputs; independent counts require one terminal selection and correct normal versus exceptional cleanup.
I: pass | It implements only WriteObserver, keeping listener closure and application work separate.
D: pass | Atomic counters and original failure identity are local observations; no callback mutates transport counters or its future.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#terminationWaitsForEveryAlreadyInvokingListenerCallback/<anonymous>@120:54
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Holds exactly one connected/frame callback at a finite gate so termination ordering can be observed.
consumers: The enclosing regression starts the selected callback on a virtual caller and closes the fixture only after gate entry.
S: pass | The private hold helper controls one callback lifetime and is shared only by the two relevant methods.
O: pass | The event parameter selects which lifecycle callback is gated without changing the actual transport cleanup implementation.
L: pass | This is explicit bounded fault injection into a normally nonblocking port; gate expiry throws and cleanup releases it, while the test asserts retained ownership before return.
I: pass | Only the listener contract and its private gate helper are exposed; closed does not block.
D: pass | CountDownLatch establishes ordering independently from fixture counters or termination internals.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#transportAbortPreservesItsReasonForEveryQueuedWrite/<anonymous>@30:45
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Provides the selected inbound listener failure used to abort a queued fake write.
consumers: The abort-reason test drives frame only in its listener-failure variant; ordinary close uses the same installed listener.
S: pass | It injects one original failure and otherwise leaves connection progression to the fixture.
O: pass | The enclosing boolean controls whether the error is triggered, with no additional listener implementation.
L: pass | The deliberate frame exception is a documented fault scenario; connected and closed return promptly, allowing successful physical cleanup.
I: pass | Only the small frame-listener methods are implemented.
D: pass | It uses a fixed local Throwable identity and cannot rewrite queued write reasons.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#transportAbortPreservesItsReasonForEveryQueuedWrite/<anonymous>@48:41
sha256: 97734d019942b1480f6b200221deb6d7df2a0a1db06873cd59e60ae642d297fd
responsibility: Detects any illegal guard claim and observes the terminal reason for an aborted queued write.
consumers: FakeFrameTransport owns this observer; the test requires CLOSED or the original OBSERVER_FAILED instead of CANCELLED.
S: pass | The observer represents one unsent queued write and does not decide its transport outcome.
O: pass | One observer works for both explicit close and listener-failure abort.
L: pass | A guard call deliberately fails the test because the write must remain queued; terminal methods expose exact success/failure for independent assertions.
I: pass | Only guard/written/failed methods are required and implemented.
D: pass | A local completion future records port-provided values without depending on its settlement flags.
findings: none
```
