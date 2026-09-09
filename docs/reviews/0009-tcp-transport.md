# Review: Bounded TCP frame transport

## Scope and reviewed contracts

This Step 10 change starts at `210359e` in the isolated worktree
`/tmp/lightweight-smpp-step10`. It adds six network-independent `spi` files,
four concrete `transport` files, shared port-contract tests, local-peer tests
and a development-only echo experiment. There are **35 new Java types in 23
files: 12 production types and 23 test/tooling types**, including every nested
member. No local, anonymous or discarded temporary Java type was introduced.
Minimal declarations and test fault modes evolved within these same types.

The dependency boundary is inward: concrete TCP code depends on the frame port
and existing framing; the port exposes no socket, codec, request-window or
session dependency. Request ownership, profiles, authentication and application
execution remain external. [Transport contracts](../TRANSPORT.md) describe exact
ownership, deadline, scheduling, error and cleanup behavior, plus primary JDK 21
sources and the fresh measurement limits.

Review covered complete types and consumers, including admission before copy,
queued plus active reservations, cancellation/guard races, error classification,
callback exceptions and public-stage dependents. Connection/read/write/accept
loops use virtual threads. State locks contain no socket I/O or callback calls.
The one per-connection deadline worker scans bounded outstanding work, owns no
external scheduler tasks and ends at closure. There is no global connection pool;
endpoint admission must limit the number of owned transports.

A queued cancel is terminal once and prevents all bytes. A writer claim prevents
queue cancellation; the immediately preceding request guard can still decline
physical output. A successful local write is not peer acceptance. Deadlines use
wrap-safe subtraction on the same nanoTime origin, with positive future offsets
below 2^63 ns. Control capacity is finite and independent, but control writes
cannot preempt active output and continuous control traffic can starve ordinary
queues. These are explicit scheduling contracts, not hidden fairness claims.

The final cleanup barrier waits for the winning physical close, all I/O workers,
all reservations and all internal terminal notifications. It also covers a
cancellation caller that removed a queued frame before concurrent close drained
it. Rejected accepted transports remain listener-owned until their internal
cleanup completes, including normal rejection after start and concurrent close.
A bounded first cleanup cause makes termination fail rather than claim successful
physical closure. User-stage actions cannot delay owned socket/I/O cleanup.

## Actual TDD sequence

All commands below ran in the isolated worktree. Every Gradle invocation used:

```sh
--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step10'
```

In the table, a selector `X` expands to
`./gradlew test --tests 'kg.aidarbek.smpp.transport.X'` plus those options.
Multiple selectors mean repeated `--tests` arguments in that invocation. Except
for cycle 01, each green command was
`./gradlew test --tests 'kg.aidarbek.smpp.transport.*Test'` plus those options.
Cycle 01 green repeated its focused command. Logs use the prefix `/tmp/step10-`.
Every listed behavioral red compiled and actually executed tests; every paired
green actually executed `:test`. Normal compilation/task/configuration caches
were preserved, and no clean, rerun, refresh or cache-disable flag was used.

| Log stem | Red selector(s) | Observed behavioral red | Minimal pass / subsequent verification |
| --- | --- | --- | --- |
| `01-config-{red,green}.log` | `TcpTransportConfigTest` | Invalid maximum frame length was accepted. | Validate count, byte, frame and buffer bounds before a socket exists. |
| `02-read-{red,green}.log` | `TcpTransportTest` | Expected fragmented/coalesced frame was null. | Add owned reader/framer path and lifecycle callbacks; large frame spans the read buffer. |
| `03-write-{red,green}.log` | `TcpTransportTest.ownsQueuedFramesAndWritesOrdinaryTrafficInAdmissionOrder` | Provisional writer rejected an otherwise valid frame. | Admit owned arrays and serialize ordinary writes; mutation/FIFO assertions unchanged. |
| `04-admission-{red,green}.log` | `TcpAdmissionTest` | Control order was 2 instead of 4; malformed frame and exhausted capacity were accepted. | Preflight frame and independent count/byte bounds; select control before queued ordinary frames. |
| `05-cancel-{red,green}.log` | `TcpAdmissionTest.concurrentQueuedCancellationWinsOnceAndPreventsEveryByte`, `TcpAdmissionTest.declinedRequestGuardPreventsWriteAndReleasesCapacity` | Sixteen cancellation callers produced zero winners instead of one. The guard-refusal characterization already passed. | Remove queued work atomically, settle once and release capacity; marker bytes prove the cancelled frame never appeared. |
| `06-deadline-{red,green}.log` | `TcpDeadlineTest` | Expired admission succeeded; queued and blocked-write outcomes timed out waiting for completion. | Add bounded deadline worker; queued expiry sends nothing, active expiry closes an actual slow-reader socket. |
| `07-connect-{red,green}.log` | `TcpConnectTest` | Unresolved address accepted, peer accept timed out, and expired connect reported READ_FAILED instead of CONNECT_TIMEOUT. | Pending direct connection starts after listener installation; bound connect, classify outcomes and validate adoption. |
| `08-listener-{red,green}.log` | `TcpListenerTest` | Accepted transport was null; rejected peer read timed out. | Add single acceptor, peer metadata, provisional ownership and close-on-reject. |
| `09-failure-{red,green}.log` | `TcpFailureTest`, `TcpListenerTest.rejectsInvalidListenerArgumentsBeforeBindingOrStartingWorkers` | Guard Error stranded work; callback IllegalArgumentException was misclassified as malformed wire; null listener configuration was accepted. | Keep wire and observer failures distinct, handle terminal faults, validate listener inputs before binding. |
| `10-termination-red.log` | `TcpFailureTest` | Termination completed while an accepted write's failure callback was still latched. | Join workers and retain reservation/notification barriers; EOF/RST characterization was already green. |
| `10-physical-red.log` | `TcpFailureTest.terminationCannotPrecedeTheWinningPhysicalSocketClose`, `TcpListenerTest.terminationWaitsForAnUntransferredConnectionDuringConcurrentClose` | Both expected pending-termination waits returned early. | Add winning-close completion and listener-owned child cleanup; `10-termination-green.log` passed all transport cases. |
| `12-rejected-cleanup-{red,green}.log` | `TcpListenerTest.retainsRejectedTransportOwnershipUntilItsInternalCleanupFinishes` | Listener termination completed while a normally rejected transport's closed callback remained latched. | Wait for rejected transport internal cleanup on the acceptor before accepting again. |
| `13-cleanup-failure-{red,green}.log` | `TcpFailureTest.failedPhysicalCloseReportsCleanupFailureInsteadOfSuccessfulTermination`, `TcpFailureTest.terminalObserverFailuresAreReportedWithoutChangingTheWinningConnectionReason`, `TcpListenerTest.reportsFailedCleanupOfARejectedConnection` | Physical close and terminal callback failures produced normal termination; rejected-child failure left listener termination pending. | Retain first cleanup failure, finish all cleanup and report CLEANUP_FAILED; preserve winning connection reason. |
| `15-experiment-{red,green}.log` | `TransportExperimentTest` | Two expected echoes/writes were reported as zero; invalid experiment bounds were accepted. | Implement bounded real echo cohort and count every expected local write/peer echo before cleanup. |

`07-connect-compile.log` and `07-connect-compile2.log` were compiler-warning
failures in test resource declarations, fixed before the behavioral red. They
are **not TDD reds**. `12-rejected-cleanup-probe.log` used an interruptible latch
inside an already-interrupted closed callback. That gate could unwind early and
was inadequate to establish the intended interleaving. The corrected fixture
uses a bounded wait that preserves interruption; the corrected behavioral red
and green above, independently reviewed, are the evidence.

`11-contract-green.log` added characterization via the shared port suite against
real TCP; it did not manufacture a missing-behavior red. The endpoint-owned fake
initially violated pre-start rejection: the Step 11 owner recorded
`/tmp/step11-27-fake-contract-red.log`, then fixed its lifecycle/admission behavior
and executed all 21 then-current endpoint cases including the shared helper in
`/tmp/step11-28-fake-contract-green.log`. That fake has immediate completion and
no queued cancellation phase. Queue, concurrency and active-I/O obligations are
therefore exercised through controlled real peers. The fake and its review are
owned by Step 11 and absent from this patch/type inventory.

`14-lifecycle-javadoc.log` ran the transport suite plus Javadoc successfully,
including concurrent close and overflow characterization, but reported 53
missing Javadoc-tag/comment warnings. `16-javadoc.log` executed compilation and
Javadoc with no warnings after documenting all public parameters, results,
failure categories and serialized exception fields. No warning was suppressed.

After green, explicit imports and the formatter changed layout only.
`./gradlew spotlessApply` passed in `17-format.log`, separately before hashing.
`./gradlew test javadoc solidReviewInventory` passed in `18-postformat.log`:
375 library tests executed, including 34 transport/experiment test cases;
Javadoc and inventory executed without warnings. Review-tool compilation came
from the matching cache; production/test compilation and tests executed freshly.

## Independent review and measurement

The root reviewer identified the winning-close/termination race. The request
tracking reviewer independently inspected accepted-write ownership, capacity,
timeout/cancellation and close paths, then identified normal rejected-child
cleanup and silently lost cleanup-failure diagnostics. Each finding has the
behavioral red/green above. The reviewer checked cycle 12 and 13 source and
actual logs after the fixes and reported no further actionable finding in that
scope. Final source changes after that pass were documentation, imports and
formatting; the new experiment is outside production.

`/tmp/step10-19-experiment.log` and the [guide](../TRANSPORT.md) record three fresh
separate-JVM runs. Each reconciled 1,600 client writes/echoes and server writes,
with cleanup completed, zero process exit failures and zero recorded JFR pinned
events at the default 20 ms threshold. Counts, intervals, heap, RSS, NMT, exact
commands and environment are preserved. These short cold instrumented loopback
runs establish accounting only, without heavy-load or production claims.

For final isolated checks, the finalized Step 9 request sources/tests and their
reviews, plus the root-owned Step 10 architecture snapshot/review, were copied
as dependencies. They are excluded from this patch and the 35-type ownership
inventory. The architecture source SHA is
`7a5e7bbb55820be949bc797b5e9747bd9b33dbf45860bb73a1ec1dec22846994`;
its independent real forbidden-dependency reds/greens are documented in
[the architecture review](0009-transport-architecture.md).

## Complete per-type review

The following hashes were obtained from the JDK inventory after final formatting.
All five principles were checked against each whole source type, its consumers
and applicable inherited contracts. No unresolved finding remains. Fault
fixtures are named below as fault fixtures rather than claimed conforming
production alternatives.

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/FrameListener.java
type: kg.aidarbek.smpp.spi.FrameListener
sha256: 4549a8d2f3236ea5f653e7f9e2ea528db26974514164474e2c9d0841cf0ea967
responsibility: Deliver one internal transport lifecycle and an ordered stream of owned frames.
consumers: TcpTransport invokes it; endpoint sessions and the real/fake contract fixtures consume lifecycle and frame events.
S: pass | All callbacks describe the lifetime or input of the same frame connection; authentication and business handlers stay outside.
O: pass | Alternate transports supply the same ordered lifecycle without changing listener consumers.
L: pass | Connected precedes frames, each frame transfers an owned array, closed occurs once, and implementations must stay fast and nonblocking; real and fake contract tests exercise these obligations.
I: pass | Connection consumers need these three lifecycle/input operations; write guards and outcomes have a separate observer interface.
D: pass | The port depends only on its local failure type and JDK byte arrays, with no codec or networking type.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/FrameTransport.java
type: kg.aidarbek.smpp.spi.FrameTransport
sha256: 0140a745fcb599875bfa040a5d0ddfd24b4f56d3237d756a293a869633800842
responsibility: Expose session-owned bounded complete-frame I/O and cleanup independently of concrete sockets.
consumers: Endpoint composition starts, writes and closes it; TcpTransport and the endpoint fake implement it.
S: pass | Admission, owned I/O and termination form one resource contract; protocol request tracking and application dispatch are excluded.
O: pass | A new transport implements the port without changing request or session policies; the shared suite establishes the common boundary.
L: pass | The full contract covers one start, owned arrays, finite classes, fail-fast rejection without callbacks, exactly-once accepted outcomes, guarded output, cancellation and read-only cleanup including exceptions.
I: pass | Only connection-level frame/lifecycle operations are exposed; the handle and observer split cancellation and transmission callbacks without exposing socket configuration.
D: pass | Signature types are SPI values and JDK CompletionStage; the pure session package remains network independent.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/TransportFailure.java
type: kg.aidarbek.smpp.spi.TransportFailure
sha256: f224e9c861958ad4e02f92e48f5435a72d42241d8191c44da8ba8c5198a80a36
responsibility: Carry one local transport failure category, conservative writer-claim flag and optional cause.
consumers: Transport admission callers, write observers, frame listeners and cleanup-stage consumers inspect the outcome.
S: pass | The exception represents local transport failure only; remote protocol status and request certainty are separate values.
O: pass | Callers interpret explicit failure categories; an implementation supplies a cause without subclassing the final exception or changing stable frame ownership rules.
L: pass | RuntimeException cause/message/stack semantics are preserved; nonnull category is enforced, message contains the category only, and the write flag does not claim peer receipt.
I: pass | The two accessors expose the local facts consumers need; no transport controls, payload conversion or mutable buffer is added.
D: pass | Only JDK exception/value utilities and the Serial annotation are required; no infrastructure exception class is forced into the port.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/TransportFailure.java
type: kg.aidarbek.smpp.spi.TransportFailure.Kind
sha256: f224e9c861958ad4e02f92e48f5435a72d42241d8191c44da8ba8c5198a80a36
responsibility: Name the supported local admission, connection, write and cleanup outcomes.
consumers: TransportFailure and endpoint result adapters classify failures by enum identity.
S: pass | Every constant identifies one phase/outcome of the local transport contract.
O: pass | The finite vocabulary changes only when the public outcome contract changes; transport implementations select existing values without adding a strategy hierarchy.
L: pass | Enum identity, names and immutable constants are preserved; cleanup and connection reasons remain distinct, as the regression tests assert.
I: pass | Consumers need classification only, so no socket or lifecycle methods are required.
D: pass | Constants depend only on JDK Enum facilities.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/WriteClass.java
type: kg.aidarbek.smpp.spi.WriteClass
sha256: 8849a60bf2db928a061c7950e0011b721d0782910a85d804c26ab339841b420b
responsibility: Select one of two independently bounded output scheduling classes.
consumers: FrameTransport callers select ordinary or reserved control admission; TcpTransport indexes their separate limits.
S: pass | Both constants belong to the single scheduling-capacity distinction.
O: pass | The contract intentionally has two classes; variation in their limits is configuration rather than user-defined enum extension.
L: pass | Enum identity/order are stable within this implementation and ordinary FIFO/control priority are tested through actual peer bytes.
I: pass | Scheduling callers only provide a class; no protocol command knowledge is attached to the enum.
D: pass | The enum depends solely on JDK language facilities.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/WriteHandle.java
type: kg.aidarbek.smpp.spi.WriteHandle
sha256: b4843bb0a23b8a0da6112f070873c052b295cebb1fdc640ef8d954e6a211a4d5
responsibility: Attempt cancellation of one admitted frame before writer claim.
consumers: Request/endpoint adapters and transport tests cancel admitted queued writes.
S: pass | The one operation controls local queue membership only, independently of request-terminal cancellation.
O: pass | Queued and immediate-completion transports implement the same cancellation result without exposing their queue mechanics.
L: pass | True guarantees no bytes and one cancellation failure; false makes no peer-acceptance claim. Real contention and the immediate fake cover both valid outcomes.
I: pass | One boolean operation is sufficient; the handle does not duplicate response futures, deadlines or connection lifecycle.
D: pass | The functional interface has no concrete transport or concurrency dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/WriteObserver.java
type: kg.aidarbek.smpp.spi.WriteObserver
sha256: d2363536026d158a78f7c458a98c73b36a2eef1fd3038d71963f284a5fec42c4
responsibility: Bridge the owning operation to the physical-write guard and one terminal local result.
consumers: TcpTransport invokes observers; request tracking adapts beginWrite/failWrite and tests record byte/outcome evidence.
S: pass | The three methods describe exactly one write lifecycle, leaving user notification delivery elsewhere.
O: pass | Owning requests or controls supply their guard and result behavior without changing the TCP writer.
L: pass | At most one guard, a false guard forbidding bytes, and exactly one terminal callback for admitted work are explicit. Fast nonblocking methods run outside locks; local completion never promises peer receipt.
I: pass | Write consumers receive only guard/success/failure; they need not implement frame or connection callbacks.
D: pass | The contract names only a local failure value and primitive result, allowing request adapters without socket dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/AcceptListener.java
type: kg.aidarbek.smpp.transport.AcceptListener
sha256: 4418102601754125803056bc6992ff1b11f413cb756dc294d1d75b4b678d3a50
responsibility: Decide provisional accepted-connection ownership at the TCP composition boundary.
consumers: TcpListener invokes it; endpoint admission reserves a slot and receives peer metadata before attaching session state.
S: pass | It decides connection admission and ownership only; authentication and frame interpretation are outside the callback.
O: pass | Endpoint-specific admission policies are supplied as callbacks without editing the accept loop.
L: pass | Receives an unstarted transport; true transfers only while open, false/exception closes and reaps it. Concurrent listener close and already-started rejection are covered with real peers.
I: pass | A single decision and peer address serve the listener consumer without forcing bind, message or authentication methods.
D: pass | The callback is in concrete transport because peer metadata uses SocketAddress; the frame resource itself is exposed through the inward SPI.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/TcpListener.java
type: kg.aidarbek.smpp.transport.TcpListener
sha256: 3c1356d82f284b092bdc28febd645e4f3ddf2ed70d9a2aa91a80608ed199318b
responsibility: Own one listening socket, bounded provisional handoff and accept-loop cleanup.
consumers: Server endpoint composition binds it and supplies AcceptListener; tests and the echo tool exercise admission and termination.
S: pass | All methods manage listener resource ownership; transferred connections and application authentication are owned elsewhere.
O: pass | Admission varies through AcceptListener and transport bounds through an immutable record; no endpoint policy is hard-coded.
L: pass | AutoCloseable closure is idempotent and preserves transferred connections. Termination waits for physical close and every listener-owned child, reports cleanup failure and ignores public child-stage dependents.
I: pass | Bind, actual address, close and termination form the concrete listener lifecycle; there is no meaningless request or message API.
D: pass | Uses the frame port, concrete adoption and JDK sockets/concurrency only. Root architecture checks prohibit request/session/endpoint dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/TcpTransport.java
type: kg.aidarbek.smpp.transport.TcpTransport
sha256: b209b9477f19cbf67a7ce41da4263011080fdcfb0ea385bbac83de1f969d9649
responsibility: Implement owned bounded complete-frame TCP I/O, deadlines and resource cleanup.
consumers: FrameTransport consumers, TcpListener adoption and the real peer/contract/experiment tests.
S: pass | Connect/adopt, framed reads, bounded output and shutdown share one socket-lifetime responsibility; no SMPP session policy or application dispatch is implemented.
O: pass | The stable boundary is FrameTransport, not subclass extension of the final JDK adapter; guards, listeners and explicit limits supply the supported variation.
L: pass | Shared real/fake tests plus guarded real peers cover owned arrays, FIFO/classes, preflight limits, one terminal result, cancellation, deadlines, EOF/RST and cleanup. Winning-close, external cancellation and rejected-child barriers prevent early termination.
I: pass | Implements only frame/lifecycle capabilities; its additional static factories configure concrete sockets at composition. Internal cleanup waiting is package-private for listener ownership.
D: pass | Depends inward on SPI and PduFramer and outward only on JDK networking/concurrency. ReentrantLock protects state only, no callbacks or socket I/O occur under it, and no runtime library is introduced.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/TcpTransport.java
type: kg.aidarbek.smpp.transport.TcpTransport.PendingWrite
sha256: b209b9477f19cbf67a7ce41da4263011080fdcfb0ea385bbac83de1f969d9649
responsibility: Retain one owned frame and its reservation/cancellation lifecycle inside a transport.
consumers: TcpTransport queue, writer, deadline/close paths and callers holding WriteHandle.
S: pass | Array, deadline, class, guard and terminal flags all belong to one accepted write.
O: pass | Its enclosing owner supplies scheduling and limits; a new external transport uses its own handle without modifying this private implementation.
L: pass | Implements cancel with one queue-removal winner before claim; false after claim/terminal and exactly-once settlement are tested under sixteen contenders. The copied array never escapes to callers.
I: pass | Only cancel is public through WriteHandle; queue bookkeeping is private implementation state.
D: pass | Uses the enclosing bounded transport and SPI observer/value contracts; no request-window or endpoint dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/TcpTransportConfig.java
type: kg.aidarbek.smpp.transport.TcpTransportConfig
sha256: 8b4f12bdbf8c8da7624dcefc03d64b7b6d8319fbd7683afca4ea24fb3fd38939
responsibility: Store immutable per-connection frame, count, byte and socket-buffer bounds.
consumers: TcpTransport, TcpListener and callers selecting explicit finite capacity or provisional defaults.
S: pass | All components bound resources of the same connection; operation timeouts remain per-operation inputs.
O: pass | Callers choose values, including zero ordinary capacity, without extending the final record or changing admission code.
L: pass | Primitive record components preserve value equality/hashing; invalid frame/count/byte/buffer ranges fail before resources exist. Long byte bounds avoid aggregate int overflow.
I: pass | Accessors expose exactly the limits the adapter needs; no authentication, request tracking or message settings enter the record.
D: pass | The immutable record depends solely on JDK language/value facilities.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/spi/FrameTransportContract.java
type: kg.aidarbek.smpp.spi.FrameTransportContract
sha256: e96b6ac6ceb1662ce52de1184650c2ed3899e11cc1b0d3da37dba49ee1835c47
responsibility: Exercise the observable obligations shared by real TCP and the immediate endpoint fake.
consumers: TcpPortContractTest and the Step 11 endpoint fake-contract test adapt peer input/output to this helper.
S: pass | Assertions concern only frame-port lifecycle, ownership, guard, deadline and terminal notifications.
O: pass | A new implementation supplies the port and two peer adapters; shared expected bytes and behavior remain unchanged.
L: pass | No custom production supertype is claimed. The helper uses bounded waits, checks derived-future isolation, both classes and false-guard bytes, and permits immediate completion with cancel false after terminal.
I: pass | One verify entry point needs only input injection and output observation; queue-specific tests remain in real transport cases.
D: pass | Uses SPI and JDK/Jupiter test facilities, with no concrete socket dependency in the shared checks.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/spi/FrameTransportContract.java
type: kg.aidarbek.smpp.spi.FrameTransportContract.Listener
sha256: e96b6ac6ceb1662ce52de1184650c2ed3899e11cc1b0d3da37dba49ee1835c47
responsibility: Record bounded lifecycle and frame observations for shared transport verification.
consumers: FrameTransportContract awaits its futures, bounded frame queue and closure counter.
S: pass | Stores only observations needed to verify one test connection.
O: pass | The shared helper varies the transport, not this private observation recorder; no artificial extension hook is needed.
L: pass | Implements all FrameListener methods promptly, owns received arrays and fails explicitly on the two-frame fixture bound; no application dependent is attached to internal completion.
I: pass | Only the three listener callbacks and private test state are provided; no output or cancellation capability is forced.
D: pass | Depends on SPI plus JDK bounded queue/future/atomic values.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/spi/FrameTransportContract.java
type: kg.aidarbek.smpp.spi.FrameTransportContract.Observer
sha256: e96b6ac6ceb1662ce52de1184650c2ed3899e11cc1b0d3da37dba49ee1835c47
responsibility: Record guard invocation and exactly-once terminal outcomes in the shared suite.
consumers: FrameTransportContract supplies allowed/refused observers and asserts counters/results.
S: pass | All fields describe one test write, with a constant guard choice.
O: pass | Allow/refuse is constructor data; alternate transport mechanics are tested through the same observer.
L: pass | The guard and terminal methods are fast and nonblocking, preserve success versus failure, and expose duplicate invocations through atomic counters rather than hiding them.
I: pass | Implements precisely WriteObserver; no connection callbacks or application interfaces are required.
D: pass | Uses only SPI and JDK futures/atomics as test recording facilities.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpAdmissionTest.java
type: kg.aidarbek.smpp.transport.TcpAdmissionTest
sha256: 56c66582cd02eb2d3c57b513b8fd36de565a8e0b6c6bf53e81430f5d8758a157
responsibility: Verify independent finite output admission, order and queued cancellation through real peer bytes.
consumers: Jupiter executes five cases; real TestPeer and controlled guard fixtures supply the transport boundary.
S: pass | All scenarios target reservation ownership and scheduling before physical write completion.
O: pass | Count/byte variants and both scheduling classes use data-driven fixtures; changing limits does not require production test hooks.
L: pass | JUnit/Object contracts hold; sixteen cancellation contenders, marker frames and copied independent expected bytes test exactly-once/no-byte claims without assuming a particular winning thread.
I: pass | Each test targets a public admission/cancellation scenario; no test-only production methods are introduced.
D: pass | Depends on public transport/SPI plus a real raw socket fixture and Jupiter, not private queue inspection.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpConnectTest.java
type: kg.aidarbek.smpp.transport.TcpConnectTest
sha256: 195840ccf5153db18a22f451d7b6428ea1166be6e9941f0e531b12eeb07d6826
responsibility: Verify pending resolved connect and validated socket adoption ownership.
consumers: Jupiter runs three local-server cases with bounded accept/read/termination waits.
S: pass | Success, deadline/refusal and argument/option checks all concern establishing one owned connection.
O: pass | Address and socket state are test inputs; no production connection factory seam is added solely for mocks.
L: pass | Tests preserve real Socket ownership on failed adoption, listener-before-read ordering and no connected callback on failure; resource scopes close peers and listeners.
I: pass | Tests use connect/adopt/start and lifecycle observations only, without bind/session APIs.
D: pass | Concrete JDK peers independently exercise the transport adapter and its SPI, while infrastructure remains test-side.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpDeadlineTest.java
type: kg.aidarbek.smpp.transport.TcpDeadlineTest
sha256: 5aef261b6996becf84d47e450ddaf980e11f4fe00da983556c28e4bcd880067d
responsibility: Verify admission, queued and physical-write deadline effects with bounded local peers.
consumers: Jupiter executes three timeout cases; the slow reader uses an 8 MiB frame and small socket-buffer hints.
S: pass | All assertions explain which deadline phase failed and whether any bytes could have been sent.
O: pass | Both write classes and configurable bounds vary through public inputs; no scheduler or clock test hook is exposed.
L: pass | Uses the documented nanoTime origin and bounded waits. An actual first header proves active output before peer reading stops; queued outcomes remain unclaimed and exactly once.
I: pass | Only write outcomes, peer bytes and termination are needed; private worker/queue state is not queried.
D: pass | Depends on real sockets and transport contracts; no session clock or request implementation is imported.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpFailureTest.java
type: kg.aidarbek.smpp.transport.TcpFailureTest
sha256: f966c6f5def92891f2db49f369372e6c9af315eece6b9ea9a34c1ab6e2ee3059
responsibility: Verify wire/observer/write failure classification and cleanup ownership under controlled races.
consumers: Jupiter runs eight cases with real sockets and two narrowly scoped close-fault socket subclasses.
S: pass | Each scenario establishes the transport failure and the promised cleanup outcome for one owned connection.
O: pass | Faults enter through public listeners/observers/socket adoption; core implementation has no test-only fault switch.
L: pass | Bounded gates expose early termination, EOF positions and actual RST output. Tests distinguish a failed physical close from successful cleanup and explicitly recover the fault socket after assertion.
I: pass | Failure tests consume the normal transport contract, with fault-specific fixture controls confined to test source.
D: pass | Raw JDK peers supply independent wire behavior; the only inherited production boundary exercised is Socket/FrameTransport, with no endpoint dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpFailureTest.java
type: kg.aidarbek.smpp.transport.TcpFailureTest.FailingCloseSocket
sha256: f966c6f5def92891f2db49f369372e6c9af315eece6b9ea9a34c1ab6e2ee3059
responsibility: Inject a permitted IOException from close before physically closing a real connected socket.
consumers: TcpFailureTest adopts it to verify exceptional cleanup, then disables the fault and closes the underlying socket.
S: pass | The sole deviation is the physical-close failure necessary for that ownership regression.
O: pass | A test-local boolean selects the fault; no production close API or strategy abstraction is changed.
L: pass | Socket.close already permits IOException. All other connected-socket behavior is inherited; after fault removal close delegates to super and the test explicitly owns recovery.
I: pass | Only close is overridden, preserving the inherited socket surface without unrelated fake operations.
D: pass | Depends only on the JDK Socket contract and test-controlled construction, and is absent from runtime artifacts.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpFailureTest.java
type: kg.aidarbek.smpp.transport.TcpFailureTest.PausingSocket
sha256: f966c6f5def92891f2db49f369372e6c9af315eece6b9ea9a34c1ab6e2ee3059
responsibility: Delay real physical close long enough to expose the winning-close cleanup race.
consumers: TcpFailureTest coordinates an external closer, peer EOF and the transport termination stage.
S: pass | Its latches control only the one close boundary under test.
O: pass | The existing adoption boundary supplies the socket subclass; no transport production seam was introduced.
L: pass | Delegates to real Socket.close after a bounded test gate, preserving actual socket state and IOException behavior. The test releases the gate in finally and joins the closer.
I: pass | Overrides only close and exposes two fixture latches, with no simulated networking implementation.
D: pass | Uses JDK sockets/latches and the bounded test wait helper; it cannot enter the library artifact.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpLifecycleTest.java
type: kg.aidarbek.smpp.transport.TcpLifecycleTest
sha256: bee407d27c74875b0586fcff8fe46bbd97b04141ebc3808b2f8024149b8325c1
responsibility: Verify concurrent close, protected termination and overflow-safe deadline characterization.
consumers: Jupiter executes three resource-lifetime cases using local sockets and bounded callback gates.
S: pass | All cases concern progress and termination guarantees over the same transport lifetime.
O: pass | Eight connection attempts and four closers vary contention without special production hooks or scheduler selection.
L: pass | Object/Jupiter contracts are preserved; closers are joined, blocked user dependents are released in finally, and a Long.MAX_VALUE positive offset exercises wrap-safe public timing.
I: pass | Tests use lifecycle/write ports and peer closure, rather than adding resource-inspection methods to production.
D: pass | Relies on transport/SPI plus JDK coordination and real peers, independently of request/session implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpListenerTest.java
type: kg.aidarbek.smpp.transport.TcpListenerTest
sha256: b022c78c0d3c8fc7d833d3b8588efb40cbd968ab83fb96e77a016c3d9e560296
responsibility: Verify accepted-connection handoff, rejection and listener-owned cleanup boundaries.
consumers: Jupiter runs six local-peer scenarios with synchronous admission callbacks and bounded race gates.
S: pass | Each scenario checks listener ownership before, during or after its handoff decision.
O: pass | Callbacks supply normal rejection, exceptions and started-rejected cases without extending the listener implementation.
L: pass | Transferred connections remain usable after listener close; rejected and concurrent provisional connections keep termination pending until internal cleanup, and faults report CLEANUP_FAILED.
I: pass | The listener API and frame port suffice; test latches and observation queues remain in fixtures.
D: pass | Uses concrete local TCP for ownership observations and inward frame contracts; no authentication or endpoint code is imported.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpPortContractTest.java
type: kg.aidarbek.smpp.transport.TcpPortContractTest
sha256: ea2a676eff91544098ba60505b16409695a33e0f6da235ecbd5c8f031b3bb90a
responsibility: Adapt a real raw local peer to the reusable frame-port contract.
consumers: Jupiter invokes the shared helper using TestPeer input/output.
S: pass | The class only wires real transport verification to the existing common obligations.
O: pass | The helper remains implementation-neutral; concrete I/O adaptation lives in this one test.
L: pass | No custom subtype is claimed. The resource scope closes both ends, checked I/O is preserved as UncheckedIOException in the Consumer, and read sizes match the independent 16-byte contract fixtures.
I: pass | Two narrow peer adapters are sufficient; no general mock transport interface is added.
D: pass | Concrete socket wiring is test-local while assertions depend on the SPI contract.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpTransportConfigTest.java
type: kg.aidarbek.smpp.transport.TcpTransportConfigTest
sha256: e0cd38fa67ddebab7d8fbed80494eec086d166681e88757f8bc4f95b15c67c04
responsibility: Verify invalid frame/count/byte/socket-buffer bounds are rejected before resource allocation.
consumers: Jupiter executes the constructor-boundary test.
S: pass | Every assertion is part of the one immutable configuration invariant.
O: pass | Boundary cases are explicit values and do not depend on transport internals or subclassing the record.
L: pass | No custom supertype obligation beyond Object/Jupiter applies; expected exceptions come from public invalid-input contracts and not implementation-produced expected data.
I: pass | The test only constructs the record; no networking fixture or irrelevant callback is required.
D: pass | Uses the pure configuration record and Jupiter only.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpTransportTest.java
type: kg.aidarbek.smpp.transport.TcpTransportTest
sha256: 7ddff810e28d71f066369409ba83f8a45b1555057079cb7d94dbeeb5d6b49642
responsibility: Verify basic owned framed reads and FIFO output, and supply bounded transport observation helpers.
consumers: Jupiter runs two baseline cases; neighboring transport tests reuse the independent frame/latch helpers.
S: pass | Cases and helpers support the same real-peer frame/ownership contract; no protocol body logic is embedded.
O: pass | Frame size/sequence and supplied callback actions vary fixtures without changing production codecs or socket implementation.
L: pass | Reads cover a frame larger than the fixed buffer plus coalescence; output expected bytes are saved before caller mutation. All race gates have bounded waits and finally release.
I: pass | Fixtures separate frame/lifecycle recording from write recording; helper methods expose only needed test operations.
D: pass | Uses real peer sockets, SPI callbacks and JDK coordination rather than private transport state.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpTransportTest.java
type: kg.aidarbek.smpp.transport.TcpTransportTest.Events
sha256: 7ddff810e28d71f066369409ba83f8a45b1555057079cb7d94dbeeb5d6b49642
responsibility: Record bounded input/lifecycle observations and inject explicitly selected callback faults.
consumers: Transport tests await connection/closure, consume up to eight frames and optionally coordinate callback races.
S: pass | All state describes the observed lifetime of one test connection.
O: pass | Constructor callbacks supply a bounded gate or throwing action for failure cases; normal recording stays unchanged.
L: pass | Ordinary mode is a prompt FrameListener implementation with owned frames and visible duplicate closure count. Deliberate bounded blocking/throwing modes are fault injection only, not claimed production substitutes, and tests release gates.
I: pass | Only FrameListener is implemented; output observation belongs to the separate Writes fixture.
D: pass | Depends on SPI and JDK bounded queues/futures/functional callbacks; no production testing hook is required.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TcpTransportTest.java
type: kg.aidarbek.smpp.transport.TcpTransportTest.Writes
sha256: 7ddff810e28d71f066369409ba83f8a45b1555057079cb7d94dbeeb5d6b49642
responsibility: Record one write guard/outcome and inject explicitly scoped guard or terminal-callback faults.
consumers: Transport tests inspect atomic call counts and a result future, and optionally latch guard/failure progress.
S: pass | Guard choice, counters and terminal actions belong to one test write lifecycle.
O: pass | BooleanSupplier and failure Consumer provide the narrow fault variation without changing the transport writer.
L: pass | Normal mode returns promptly and preserves every callback count. Deliberately blocked/throwing modes model contract misuse for cleanup regressions with bounded gates; no conforming production replacement is claimed for those modes.
I: pass | Implements only WriteObserver and exposes test observations; it does not model request correlation or frame input.
D: pass | Uses SPI plus JDK atomics/futures/callbacks, and remains test-only.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TestPeer.java
type: kg.aidarbek.smpp.transport.TestPeer
sha256: e18bd7a4c9c52703c9b090e26a7296c2e8f08ebf7fd1a8c5fd531661ea92ba1c
responsibility: Own a connected raw loopback socket and adopted transport for focused networking tests.
consumers: Real transport tests send/read independently constructed bytes and close the fixture after each case.
S: pass | Construction, read timeout and closure manage just this two-ended test resource.
O: pass | Explicit TcpTransportConfig varies bounds; low-level peer behavior is supplied by tests without a broad fake-socket layer.
L: pass | AutoCloseable closes transport and peer and awaits bounded cleanup. Failed adoption closes both sockets; intentionally failing cleanup tests instead own their explicit recovery paths.
I: pass | Tests need only the two ends and close; no unrelated protocol or simulator API is present.
D: pass | Concrete JDK socket construction is confined to this fixture and uses the public adoption boundary.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TransportExperiment.java
type: kg.aidarbek.smpp.transport.TransportExperiment
sha256: fcbe6eefbd9303f2271a6d13329196e9e0d13e40395e8af523a84fb7f5cde7a7
responsibility: Explicitly launch and account for a small bounded raw-frame echo cohort in separate JVM roles.
consumers: Developers run main outside default Gradle load checks; TransportExperimentTest verifies counts and bounds.
S: pass | The entry point, cohort runner and byte comparison serve one development experiment; no messages, authentication or performance-SLA reporting is implemented.
O: pass | Connection/round counts are explicit bounded inputs. A future workload belongs in simulator tooling rather than extending runtime transport APIs.
L: pass | No custom subtype is claimed. Client counts only exact peer echoes and completed local writes, uses bounded waits, closes every owned transport and exits exceptionally on missing/wrong data.
I: pass | Only main is public; fixture/result helpers are package-private or private and test-source-only.
D: pass | Concrete transport wiring, JDK counters and output reporting remain outside all library artifacts and introduce no runtime dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TransportExperiment.java
type: kg.aidarbek.smpp.transport.TransportExperiment.ClientPeer
sha256: fcbe6eefbd9303f2271a6d13329196e9e0d13e40395e8af523a84fb7f5cde7a7
responsibility: Record one experiment connection and a single outstanding echoed frame.
consumers: TransportExperiment.runClient sends one frame per round and awaits its connection, frame and write-count observations.
S: pass | Callbacks and bounded queue/counters all account for the same client echo stream.
O: pass | Round count is supplied data; protocol simulation or arbitrary application dispatch is outside this private fixture.
L: pass | FrameListener and WriteObserver methods are prompt, use nonblocking queue offer and internal future completion, report closure/failure and complete the write cohort only at its declared count.
I: pass | This echo participant uses both input and write outcomes, so implementing these two small ports is meaningful rather than an unused capability burden.
D: pass | Uses the public transport/SPI and JDK bounded coordination; no message codec, session or request-window dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TransportExperiment.java
type: kg.aidarbek.smpp.transport.TransportExperiment.Result
sha256: fcbe6eefbd9303f2271a6d13329196e9e0d13e40395e8af523a84fb7f5cde7a7
responsibility: Return completed echo count, local write count and measured exchange interval.
consumers: The experiment main prints values and its accounting test checks the independently expected cohort.
S: pass | All three primitive components report the same completed client cohort.
O: pass | The final record has no supported extension variation; richer workload statistics belong to later simulator reports.
L: pass | Primitive record components preserve immutable value equality/hash semantics, with values produced only by the validated bounded runner.
I: pass | Consumers need only three accessors; there is no mutable histogram or runtime control surface.
D: pass | The result depends solely on JDK language/value facilities.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TransportExperiment.java
type: kg.aidarbek.smpp.transport.TransportExperiment.ServerPeer
sha256: fcbe6eefbd9303f2271a6d13329196e9e0d13e40395e8af523a84fb7f5cde7a7
responsibility: Echo one bounded experiment connection and record its server-side local write completions.
consumers: ServerRun starts this internal listener/observer for an admitted connection.
S: pass | Received count, echo output and terminal error signaling all concern this one cohort participant.
O: pass | The owning ServerRun supplies the round bound; no runtime transport code changes for echo behavior.
L: pass | Callbacks are nonblocking, reject excess input, echo owned bytes through copied write admission and signal write failure. A premature close marks the cohort failed; all future waits occur outside callbacks.
I: pass | This participant genuinely consumes frame input and write completion, while connected is a valid no-op for an already adopted peer.
D: pass | Depends on the frame SPI and the tooling-owned cohort state, not protocol/session/application implementations.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TransportExperiment.java
type: kg.aidarbek.smpp.transport.TransportExperiment.ServerRun
sha256: fcbe6eefbd9303f2271a6d13329196e9e0d13e40395e8af523a84fb7f5cde7a7
responsibility: Own a bounded echo listener, accepted cohort and aggregate server completion.
consumers: The experiment server process and accounting test use its bound port, awaitFrames and AutoCloseable cleanup.
S: pass | Listener, accepted resources and expected count share one experiment-cohort lifetime.
O: pass | Validated connection/round counts drive the cohort, and excess connections are rejected through the production admission callback.
L: pass | The accepted list is bounded by the configured 1..64 cohort. Completion requires all expected writes; closure stops the listener then closes and awaits owned transports, with timeouts/errors propagated.
I: pass | The private tool wrapper offers only port, cohort completion and close; it is not a new public server endpoint API.
D: pass | Concrete TCP/JDK wiring remains entirely in test tooling; the bounded collection does not enter the library artifact.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TransportExperimentTest.java
type: kg.aidarbek.smpp.transport.TransportExperimentTest
sha256: fcaac613db3d58e487899cbe176d7a5c96659f8e01c931b4cb6338b36c5228da
responsibility: Verify echo-tool accounting and input guardrails before executing fresh measurements.
consumers: Jupiter executes two bounded real-socket experiment cases.
S: pass | Both cases establish the validity of the tool that reports cohort counts.
O: pass | Small declared cohort values make expected counts independent of the production transport and support later experiment input changes without new library hooks.
L: pass | The initial behavioral red observed zero counts instead of two and invalid bound acceptance. Assertions retained after implementation verify frames, writes, positive interval and owned server cleanup.
I: pass | Uses the tool runner and result only; it needs no statistical/performance framework or runtime reporting API.
D: pass | Depends on development-only tooling and Jupiter, with real loopback sockets supplied through the same public transport boundary.
findings: none
```

## Final integrated-dependency verification

`./gradlew check build dependencies --configuration runtimeClasspath` with the
same required options passed in `/tmp/step10-20-complete.log` after copying the
finalized prerequisite sources/reviews. All **419 library tests executed freshly**,
including all 34 transport/experiment cases and all ten architecture cases;
there were zero failures, errors or skips. The 60 unchanged review-tool tests
were restored from the matching cache, not rerun. The current `solidReview`
executed and covered **164 project types**, including all 35 owned types above.
Production/test compilation, format checking, Javadoc and all archives executed
successfully without warnings. The runtime classpath reported **No dependencies**.

Archive inspection confirmed that binary, source and Javadoc JARs include
`META-INF/LICENSE` and `META-INF/NOTICE`, and contain no experiment, shared
contract fixture, transport test or review-tool type. The observed archive
entry counts were 103 binary, 93 source and 137 Javadoc entries with the copied
Step 9 prerequisites present. All local Markdown links in this guide/review
resolved. No baseline build setting, runtime dependency, source header or cache
policy changed. The copied request/architecture sources are integration
prerequisites and are absent from the Step 10-only patch.
