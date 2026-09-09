# Review: Java 21 carrier progress and admission deadlines

## Scope and retained failure

Baseline library: `387aed9ef523c85fb3cfa0f8f245438759deb6fc`. The first final-candidate
measurement used declared source SHA-256
`b31f25ac069814c7dc17a148ffda671f985cb7f925cc571a9dd22d16bfe20cf2`, library JAR
`d7ed9830384a5acdf13475f4dba6e61c3ef061dd117134527a2a816ae98c89ea`, and Java 21.
Two W-WINDOW clients exhausted all 12 virtual-thread carriers while their
coordinator intrinsic monitors enclosed a contended notification lock. Their
main and endpoint deadline threads also blocked on those coordinators.
The bounded helper deadlines terminated these failed pairs. Root then stopped
the campaign, preserving five finished pairs, four interrupted pairs and 45
unstarted cells; none was silently retried. See [the matrix evidence](../MATRIX_RESULTS.md).

The fix changes seven production files and six test files, covering all 22 current
identities listed below. The production guard conversions preserve statement
order and existing critical-section boundaries: EndpointConnection 32,
MessageExchange 3, NotificationExchange 5, RequestWindow 10, ReconnectHandle 7,
SmppServer 3 and OutbindListener 3. Exchange callbacks share the coordinator’s
one final guard; request windows and lifecycle wrappers own their respective
private guards. No carrier count, callback capacity, executor ownership,
transmission certainty, request replay, command capability or runtime dependency
was changed.

A separate admission edge is fixed after a real regression: time spent obtaining
notification capacity now counts before deciding deadline versus backlog and
before consuming sequence/count/byte ownership. An acquired reservation is
released when the deadline or injected clock fails. Result dispatch remains
after window state-lock release; application notifications remain outside the
notifier lock and outside I/O.

The audit followed whole affected types and their contracts: frame callbacks,
control/message/one-way observers, typed descriptors and handler dispatch,
request matching/deadline precedence, explicit reconnect retirement, listener
admission and owned/supplied worker cleanup. EndpointResources releases snapshots
before calling existing coordinators/reconnect handles; notification workers
release their lock before invoking callbacks. The independent request-tracking
reviewer checked lock order, all 13 frozen file hashes/archive bytes, and the
post-reservation deadline cases with no remaining finding. The simulator’s
separate virtual sampler issue and per-session workload scheduling are owned by
separate changes, not hidden in this library delta.

## Sequential TDD and verification

Every Gradle invocation below used
`--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step17'` in the isolated
Step 17 worktree. Logs are retained at `/tmp/step19-carrier-*.log`.

| Cycle | Actual command selection and observation |
| --- | --- |
| 01 | Unqualified `test --tests kg.aidarbek.smpp.endpoint.VirtualThreadProgressTest` hit the simulator subproject’s empty filter. This is explicitly not behavioral red evidence. |
| 02 →03 | `:test --tests kg.aidarbek.smpp.endpoint.VirtualThreadProgressTest` first failed when two response deliveries timed out in a fresh two-carrier JVM. After coordinator/shared-exchange conversion, the probe plus `EndpointConnectionTest` and `ExchangeConnectionTest` passed 27 cases. |
| 04 →05 | The new admission scenario timed out while the existing response scenario passed. Converting RequestWindow’s state guard made both pass alongside `kg.aidarbek.smpp.request.*`. |
| 06 →07 | The new idle reconnect cancellation scenario timed out; response/admission remained green. Converting ReconnectHandle made all three pass with `kg.aidarbek.smpp.endpoint.Reconnect*`. |
| 08 →09 | All six new ordinary/reversed listener start/shutdown/close scenarios timed out; the earlier three remained green. After both wrapper conversions, full `:test` executed 747 cases in 112 classes with zero failures/errors. |
| 10 | `spotlessApply` ran separately before inspecting the next regression. |
| 11 →12 | `:test --tests kg.aidarbek.smpp.request.RequestAdmissionContentionTest` failed both controlled cases: expired admission succeeded with available capacity, and backlog incorrectly beat expiry with full capacity. The post-reservation recheck then passed both, all request tests, the nine carrier probes, and coordinator/exchange tests. |
| 13 →14 | Final `spotlessApply`, then separate `solidReviewInventory`: 451 isolated Java identities, all 22 owned identities inventoried at final hashes. |
| 15 | `:test :simulator:test javadoc` executed 749 library cases in 113 classes and 67 isolated Step 17 simulator cases in 22 classes, with no failures or Javadoc/compiler warnings. This scope excludes later Step 18/sampler/receipt changes being integrated separately by root. |
| 16 | `check build` passed with 23 actionable tasks: seven executed and 16 up-to-date. `solidReview` freshly verified all 451 current identities, including these 22. Library and simulator tests reused the successful cycle-15 outputs; this is not a second fresh test execution. Formatting checks and archive/build tasks passed. |

The two existing deadline fixtures still enforce original invocation/arrival
budgets; they now wait for the exact reentrant guard queue rather than JVM
`BLOCKED` monitor state. Carrier probes use explicit intended-thread queue checks,
not an approximate queue length. Each fresh JVM has parallelism and maximum pool
size 2, finite coordination/result waits and unconditional parent process reaping.
All final source hashes follow formatting; no generated fixture or temporary Java
type is excluded from this review. The 22 blocks below cover every identity in
the 13 owned files, including both anonymous test observers.

## Complete per-type review

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection
sha256: db0798c685671dc7bb3fb062bb62da17c9a4d420945312a0659242f8f4bbbd21
responsibility: Owns one connection generation’s protocol lifecycle, permissions, correlated requests and finite reply retirement over a frame port.
consumers: SmppClient, SmppServer, reversed outbind owners, BoundSession, exchange collaborators and FrameTransport callbacks use its existing internal lifecycle surface.
S: pass | Lifecycle serialization is the change driver; codecs, permission/version policy, request ownership and application dispatch remain focused collaborators rather than new scheduler responsibilities in this coordinator.
O: pass | Paired operation descriptors, handler registries, profiles and FrameTransport remain the existing variation points; changing lock mechanics does not add a command switch or require callers to choose a new executor.
L: pass | One final reentrant guard preserves every former critical section, early return and nested call. Frame arrival and public request invocation ticks remain before contention. Correlation still rejects unsent/late/wrong responses, failed bind preserves certainty, and close/unbind remain idempotent. Nine two-carrier probes plus full real endpoint/profile/mode tests establish progress and unchanged ownership.
I: pass | The package-private coordinator serves its frame listener and narrow facade/callback consumers; transport details or mutable locks are not added to the public BoundSession API.
D: pass | The guard is a JDK lock. Actual I/O still crosses FrameTransport/WriteObserver; profile/session/request collaborators remain inward dependencies and the existing coordinator architecture rule passes.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.OutbindWrite
sha256: db0798c685671dc7bb3fb062bb62da17c9a4d420945312a0659242f8f4bbbd21
responsibility: Authorizes the single configured outbind notification within the existing total bind deadline.
consumers: The coordinator supplies it to the frame port for an explicit outbound notification before ordinary bind authentication.
S: pass | Only the pre-bind notification write decision and failure continuation are owned; connection creation, credentials and follow-up bind policy remain outside this adapter.
O: pass | It implements the existing write-observer port without defining another notification or reconnect engine.
L: pass | beforeWrite still checks the total bind deadline before setting sending; the same reentrant coordinator guard now permits carrier release. Local written deliberately does not imply peer acceptance, while failed closes the owning workflow. Outbind and lifecycle tests cover successful, refused and timed-out attempts.
I: pass | The three port callbacks each represent write authorization or settlement; the empty successful callback has no required follow-up because this notification has no response entry.
D: pass | It depends on its coordinator and TransportFailure, with no socket, executor or application callback dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite
sha256: db0798c685671dc7bb3fb062bb62da17c9a4d420945312a0659242f8f4bbbd21
responsibility: Reconciles one accepted control or bind reply with coordinator reply counts and close-after-flush continuation.
consumers: The coordinator’s control/bind reply path and the frame port invoke the observer.
S: pass | Reply settlement and its internal continuation are one ownership transition; authentication invocation and application notification remain separate.
O: pass | The existing WriteObserver contract supports both concrete TCP and the contract-checked fake; no transport-specific branch is introduced.
L: pass | Authorization still refuses a closed generation, successful writes decrement the tracked count once under the shared guard, and failure closes with its cause. Internal afterWrite continuations remain inside the same critical section; application work is queued elsewhere. Crossed-unbind, negative-bind and deferred-write tests pass.
I: pass | It retains only the close flag and required internal continuation; it exposes no request submission or user-handler surface.
D: pass | Its dependencies are the coordinator, Runnable for internal continuation, and frame-port failure values.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite
sha256: db0798c685671dc7bb3fb062bb62da17c9a4d420945312a0659242f8f4bbbd21
responsibility: Bridges physical write authorization and failure into the generation’s existing RequestWindow.
consumers: Bind, unbind, enquiry and typed sender request paths provide one instance to FrameTransport.
S: pass | Transmission certainty and transport failure settlement belong together; response matching remains in the request window/coordinator response path.
O: pass | All supported paired commands reuse the same bridge without command-specific transport implementations.
L: pass | beginWrite remains the single NOT_SENT to MAY_HAVE_BEEN_SENT gate; a successful local write never completes a peer request. Failure retains the winning RequestFailure and only triggers lifecycle closure for bind/unbind. Existing before/after-guard failure and queued-response regressions pass under the new window lock.
I: pass | The minimal observer callbacks match the write lifecycle; written is intentionally empty because local completion cannot promise an SMPP result.
D: pass | It uses RequestHandle, RequestWindow and TransportFailure rather than TCP, timing threads or application callbacks.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange
sha256: 0f0c4c9f816f05667203a3ef4386e8d7470f43f9dc268d4ff8148c268a50c4ee
responsibility: Retains ordered inbound paired-message decisions, fallback responses and bounded reply ownership for one session.
consumers: EndpointConnection feeds validated operations/frames, HandlerDispatcher supplies decisions, and frame observers settle writes.
S: pass | Decision/reply ownership is cohesive; descriptor validation, handler invocation, wire encoding and connection lifecycle stay with their dedicated collaborators.
O: pass | Operation descriptors and registered handlers remain the variation boundary for submit/deliver/data/common/broadcast work; guard conversion adds no operation special case.
L: pass | All three asynchronous callback sections use the coordinator’s identical final guard. FIFO includes active writes and completed tails; timeout retains physical handler capacity, FULL retries retain the original deadline, and inline settlement stays iterative. Existing 6000-reply, retained-slot, queue-bound and handler-expiry tests all pass.
I: pass | The internal receive/expire/close/counter surface serves only the coordinator, while application handlers retain their typed narrow API.
D: pass | It composes descriptor policy, HandlerDispatcher and frame observer abstractions through the coordinator; it neither creates sockets nor owns an extra request window.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange.Entry
sha256: 0f0c4c9f816f05667203a3ef4386e8d7470f43f9dc268d4ff8148c268a50c4ee
responsibility: Stores one retained request’s immutable identity/deadline and mutable cancellation, byte and reply-write bookkeeping.
consumers: MessageExchange and its ReplyWrite observer access entries while holding the shared connection guard; handlers only see the atomic cancellation flag.
S: pass | Each field belongs to that request’s bounded ownership interval, including fallback and active-write retention.
O: pass | The stored descriptor carries operation variation without subclasses or command-specific entry shapes.
L: pass | Identity/deadline remain final; mutable buffer/accounting fields remain private to guarded owner code and cancellation is atomic. Entries are removed once and their retained byte count survives ready/active writes, as tested by saturation and delayed-head cases.
I: pass | No public interface or caller mutators are added; the owner needs exactly this retained state.
D: pass | It stores protocol values, a descriptor, a dispatcher ticket and JDK cancellation state, with no infrastructure construction.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange.ReplyWrite
sha256: 0f0c4c9f816f05667203a3ef4386e8d7470f43f9dc268d4ff8148c268a50c4ee
responsibility: Settles the ordered reply for one retained message entry against its owner’s queue and byte reservations.
consumers: MessageExchange installs it on FrameTransport through the coordinator.
S: pass | Authorization and settlement affect one queue entry; application invocation and protocol result construction are separate.
O: pass | It uses the same WriteObserver port for inline fake and asynchronous TCP completion.
L: pass | The shared guard atomically rejects removed entries, removes a successful entry at most once, subtracts its bytes and resumes the iterative FIFO drain. Failure closes the generation. The large inline-drain and active/queued ownership regressions verify both callback timings.
I: pass | Only the observer lifecycle is implemented; it has no independent session or sender API.
D: pass | The adapter depends on its entry, MessageExchange/coordinator and the frame-port failure contract.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange
sha256: a5d2d0ed318840f8f2ce7360c2643f0f7c9d10d52696e5c15e1ccf66acf206a0
responsibility: Owns bounded incoming alert decisions and outbound one-way notification settlement without response-window entries.
consumers: BoundSession/EndpointConnection send and receive notifications, the shared handler lane invokes application work, and write observers finish local output.
S: pass | Logical notification ownership is the sole change driver; permission/encoding, physical handler capacity and network I/O remain collaborators.
O: pass | The focused alert hook and one-way write path coexist with paired operation descriptors; they do not manufacture request-window replies or reconnect policy.
L: pass | All five callback/cancellation sections use the same coordinator guard. Sequence allocation remains separate from response reservations, cancellation cannot retract started writes, expiry and failures retain certainty, and callbacks keep their notification permit until physical return. Alert/outbind, cancellation and full exchange tests remain green.
I: pass | Incoming application notification and outbound local-write observation stay distinct focused APIs; neither requires a fabricated SMPP response.
D: pass | It uses the existing HandlerDispatcher lane, BoundedNotifications and frame observer contracts; no concrete transport or new executor is introduced.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange.Incoming
sha256: a5d2d0ed318840f8f2ce7360c2643f0f7c9d10d52696e5c15e1ccf66acf206a0
responsibility: Retains one incoming notification’s deadline, atomic cancellation flag and physical handler ticket.
consumers: NotificationExchange owns its guarded membership and HandlerDispatcher observes cancellation/physical completion.
S: pass | All state describes one logical incoming decision and its retained application ownership.
O: pass | There is no supported subtype variation; registered typed hooks supply behavior without changing this state holder.
L: pass | The final deadline and atomic cancellation preserve cross-thread visibility; removing logical membership does not release a blocked physical ticket. Expiry/handler-retention tests cover this distinction.
I: pass | No custom interface or public mutators exist; only the owner and dispatcher need this small state.
D: pass | Dependencies are the JDK atomic flag and the existing dispatcher ticket.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange.Outgoing
sha256: a5d2d0ed318840f8f2ce7360c2643f0f7c9d10d52696e5c15e1ccf66acf206a0
responsibility: Retains the local outcome, cancellation guard and notification reservation for one outgoing one-way write.
consumers: NotificationExchange, NotificationSend and FrameTransport callbacks share its state through the connection guard.
S: pass | The sequence, deadline, start flag and completion reservation all follow one outbound write’s ownership.
O: pass | The standard WriteObserver boundary supports alternate frame ports without changing user notification policy.
L: pass | beforeWrite refuses removed/expired work before setting started; written/failed finish membership once under the same reentrant guard. A possibly written failure closes the generation, and completion stays off I/O with capacity held through dependent callbacks. One-way cancellation/failure tests remain green.
I: pass | Its three observer methods and protected future serve exactly local-write observation; no peer-response promise is added.
D: pass | It composes frame-port values, CompletableFuture and the bounded dispatcher reservation, with all network access through the coordinator.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindListener.java
type: kg.aidarbek.smpp.endpoint.OutbindListener
sha256: b33cae913494337f0350b385a46f094fce9093c8d05eaf9c62d68c8d001e7222
responsibility: Composes an explicit reversed ESME listener with bounded outbind authentication and same-socket bind ownership.
consumers: Applications use start/connection count/shutdown/termination; the TCP accept callback transfers permitted transports to EndpointConnection.
S: pass | The owner manages its configured listener and shared endpoint resources; authentication, binding state and message handling remain delegated.
O: pass | Existing configuration, authenticator, handlers and optional lifecycle policy remain extension seams; a state-lock replacement changes no public constructor or policy.
L: pass | Start-once and closed-state decisions remain serialized; every return/failure releases the reentrant guard. The three two-carrier start/shutdown/close regressions prove resource-lock contention cannot strand the owner, and existing authenticated outbind/TLS/termination tests preserve physical transfer and cleanup.
I: pass | This explicit listener API does not require ordinary client submission or automatic reconnect behavior.
D: pass | As a composition root it alone may create TcpListener; the per-connection coordinator still receives only FrameTransport. Application callbacks remain bounded and off transport workers.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ReconnectHandle.java
type: kg.aidarbek.smpp.endpoint.ReconnectHandle
sha256: 63c1ec6474035c4e0c836945b079a33ce0ea88718d4b1b6251b30b6573be4005
responsibility: Owns a finite sequence of explicit connection attempts and readiness offers without message replay.
consumers: SmppClient/OutbindConnector create it, EndpointResources ticks/closes it, and applications observe sessions or cancel/await retirement.
S: pass | Attempt lifetime and notification ownership are cohesive; message traffic and transport recovery remain outside the policy loop.
O: pass | The bounded ReconnectPolicy and internal connection factory preserve supported variation; no retry algorithm or replay queue is introduced.
L: pass | All seven critical sections retain their existing boundaries using a private reentrant guard. Cancellation suppresses future offers, preserves already offered callback ownership, and waits for physical attempt/observer retirement. A fresh two-carrier idle-cancellation regression and the complete reconnect failure/ambiguity/observer suite pass.
I: pass | The public surface is current-session observation, attempt count, cancellation and termination; it exposes no request-window internals or callback executor.
D: pass | It depends on EndpointResources and a connection-attempt supplier, not sockets. Observers execute outside its guard and notification workers release their own lock before invoking them.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/SmppServer.java
type: kg.aidarbek.smpp.endpoint.SmppServer
sha256: 4809c4ff5b17e7fb0740b760b29a676758bee31f5614d3c5eb0251a44c049255
responsibility: Composes the ordinary MC listener, accepted-session admission and owned/supplied authentication lifecycle.
consumers: Applications configure and start/shut down the owner; the accept callback creates generation coordinators under EndpointResources permits.
S: pass | Its methods own listener/resource lifetime; protocol permissions, correlated work and application invocation remain focused collaborators.
O: pass | Existing configurations, supplied authentication executor, async authenticators, typed handlers and TLS/keepalive policy retain their established seams.
L: pass | Start/shutdown/close retain atomic state decisions and early validation under a private reentrant guard. The three fresh two-carrier listener regressions and real both-profile/mode bind, rejection, bounded cleanup and supplied-executor tests preserve the API while removing pinning.
I: pass | The server presents listener and termination ownership rather than forcing client connection or unsupported command methods.
D: pass | Concrete TcpListener/TcpTransport wiring remains at this composition root; per-generation logic remains behind FrameTransport and bounded dispatch collaborators.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestWindow.java
type: kg.aidarbek.smpp.request.RequestWindow
sha256: 45c0f5767eb7ce5a9a190ef44f06c202c7991750b13b02a2d365d2769d817460
responsibility: Owns bounded pending requests, one generation’s monotonic sequences, transmission certainty and exactly one terminal result per admission.
consumers: EndpointConnection and typed request handles use admission, matching, write guards, expiration, cancellation and disconnect; shared or owned bounded notification dispatchers deliver results.
S: pass | Correlation/resource accounting is its change driver; endpoint permissions, codecs, timers and sockets are absent.
O: pass | Command IDs/response types, caller-owned dispatch and an injected monotonic clock support the established variations without operation-specific lifecycle code.
L: pass | The new state lock preserves the ten prior critical sections and dispatch after state release. Admission now rechecks its total deadline after notifier contention, before backlog interpretation or sequence/count/byte ownership; it releases temporary reservations on expiry/clock failure. Both capacity states fail with DEADLINE_EXPIRED/NOT_SENT and preserve next sequence 1. Existing generation, nack, cancel, deadline-precedence and physical callback-bound tests pass.
I: pass | Focused RequestHandle observations remain separate from mutable window ownership; optional notification cleanup helpers do not expose sockets or session capabilities.
D: pass | Only protocol values, JDK synchronization/clock APIs and BoundedNotifications are used. The existing architecture contract rejects infrastructure dependencies, and notification callbacks still run after all window locks are released.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CarrierContention.java
type: kg.aidarbek.smpp.endpoint.CarrierContention
sha256: f2c0fa0159bdbf0ec426f9e5da746cb0b68edcd19e463d3a17f9e03eb237dd0d
responsibility: Selects a deterministic lock-contention ordering in a bounded fresh child JVM.
consumers: VirtualThreadProgressProbe supplies two actual library call sites and the corresponding real lock objects.
S: pass | It only coordinates holder/waiter progress; it does not invent responses, alter library scheduling configuration, or own endpoint behavior.
O: pass | Lists of two actions/locks support shared-notifier and distinct-resource contention without endpoint-specific switches in the fixture.
L: pass | The virtual holder acquires real locks and unmounts on a latch; exact hasQueuedThread checks identify both intended waiters before release. All waits are finite and the parent reaps a failed child, so a regression cannot leave unbounded project workers. Two-carrier reds were observed before each relevant conversion.
I: pass | Only lock lookup and finite contention execution are exposed within tests; reflection is restricted to project-owned private lock fields to select an otherwise nondeterministic ordering.
D: pass | It uses JDK synchronization and reflection only, with no production hook, scheduler replacement or added runtime dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest
sha256: b3aa03838a807142ccf61b1f876c2857ae7d977ff6993a78ec62d43fa3ebe44e
responsibility: Verifies one coordinator’s control, authentication, version, correlation and frame-port ownership contracts with explicit fixture control.
consumers: JUnit executes the suite using the shared-contract-checked FakeFrameTransport, raw headers, bounded notifications and controlled clocks/stages.
S: pass | Tests remain about coordinator behavior; the only adjusted fixture now contends the actual reentrant guard while verifying original invocation-budget expiry.
O: pass | The fake frame port and focused async collaborators provide variation without modifying production protocols or adding a second request engine.
L: pass | All 16 cases preserve independent expected bytes/statuses, sequence namespaces, NOT_SENT rejection, negative/unsupported binding outcomes and exactly-once fake cleanup. Exact guard queuing replaces a JVM monitor-state assumption; no deadline assertion is weakened.
I: pass | Each fixture supplies only its relevant authenticator/frame callbacks; tests do not require unrelated simulator or codec service behavior.
D: pass | Dependencies remain JUnit, JDK gates and protocol/frame-port/endpoint APIs. Contract reuse ties the fake to the real transport guarantees.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@105:49
sha256: b3aa03838a807142ccf61b1f876c2857ae7d977ff6993a78ec62d43fa3ebe44e
responsibility: Delays one accepted fake write’s authorization to expose ownership during simultaneous close.
consumers: The fake termination regression installs this adversarial WriteObserver on its controlled frame port.
S: pass | Only the before-write barrier and terminal callback shape are supplied.
O: pass | It uses the real observer interface; the release gate varies timing without a new fake transport implementation.
L: pass | The intentionally blocked internal observer is a fault fixture, not a claim of production nonblocking compliance. Explicit release and bounded result waits verify that accepted-write ownership outlives close until settlement; successful/failed callbacks do not claim peer acceptance.
I: pass | All three callbacks serve the one write lifecycle; terminal no-ops are deliberate because the enclosing test observes transport retirement.
D: pass | It depends only on JDK latches/futures and the WriteObserver contract, with no concrete networking.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@85:45
sha256: b3aa03838a807142ccf61b1f876c2857ae7d977ff6993a78ec62d43fa3ebe44e
responsibility: Delays the fake port’s winning close notification to expose physical cleanup ordering.
consumers: The fake termination regression installs this adversarial FrameListener on its isolated transport.
S: pass | Only a close-notification barrier is owned; protocol framing and application state are absent.
O: pass | The existing frame listener port supplies the timing seam without altering production transport code.
L: pass | The intentionally blocked close callback is explicitly released by the test, and termination must remain incomplete until both notification and accepted write settle. Connected/frame no-ops are valid because the fixture receives no application PDUs.
I: pass | It implements only the frame lifecycle contract required by the fake; no user authentication or sender methods are present.
D: pass | Dependencies are FrameListener, TransportFailure and JDK gates/futures; the test remains independent of sockets.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ExchangeConnectionTest.java
type: kg.aidarbek.smpp.endpoint.ExchangeConnectionTest
sha256: b76368bc818095f34f0aaad4c39c452d53ddb99ad2d911d1c8f4ad68398feab9
responsibility: Verifies ordered paired-message reply ownership, transport pressure, handler deadlines and physical callback retention.
consumers: JUnit drives the contract-checked immediate/deferred fake and controlled handler stages.
S: pass | All 10 cases target exchange ownership and deadline behavior, including the retained-slot and iterative-drain regression.
O: pass | Descriptor/handler and fake-port seams vary reply timing without adding protocol branches or production scheduling hooks.
L: pass | The arrival-budget fixture now waits for the exact coordinator guard queue, advances the existing clock and still requires an error reply with no application invocation. The 6,000 inline replies, FULL retry deadline, finite control reserve and physical handler retention assertions are unchanged and green.
I: pass | Fixtures implement only the requested typed handler or frame behavior; no broad application mock is required.
D: pass | The suite uses endpoint/protocol/SPI contracts and JDK coordination, with no socket infrastructure added to the coordinator.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/VirtualThreadProgressProbe.java
type: kg.aidarbek.smpp.endpoint.VirtualThreadProgressProbe
sha256: 8dc2381f6651d45cc17be4f52e7b1f2d6e77f9f72344688e39f292ca5f99bac3
responsibility: Exercises nine finite library progress scenarios in an isolated JVM with two virtual-thread carriers.
consumers: VirtualThreadProgressTest launches its main method; CarrierContention schedules real notifier/resource contention for it.
S: pass | Its sole output is proof of callback/resource retirement or a failing process; the process contains no throughput measurement or production retry policy.
O: pass | The explicit scenario switch varies the regression operation, while contention ordering stays in its helper and actual library collaborators retain their contracts.
L: pass | Valid bind/enquiry PDUs use the established fake-port contract; request results, idle reconnect cancellation and real listener lifecycle cleanup must finish under unchanged bounds. Failed children terminate/reap separately, and successful paths assert no retained notification or endpoint ownership. No carrier enlargement masks the defect.
I: pass | The public main exists solely for the child process; helper methods are private and no fixture API enters library artifacts.
D: pass | It depends on actual endpoint/request APIs, the existing fake, and JDK process-fixture primitives. Private-field access controls real contention rather than supplying a different lock implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/VirtualThreadProgressTest.java
type: kg.aidarbek.smpp.endpoint.VirtualThreadProgressTest
sha256: ba559f5c639e500009a94b8b3104b62db857a58fb9f9e9ed5303d0b94ff04f04
responsibility: Owns fresh bounded JVM execution and result verification for the nine carrier-progress regressions.
consumers: JUnit provides temporary log paths and parameterized scenario names; the child probe performs library work.
S: pass | Process lifetime/diagnostic ownership is distinct from the probe’s endpoint assertions.
O: pass | The scenario parameter supplies the finite matrix while the launched JVM and cleanup contract remain common.
L: pass | Every child explicitly starts with parallelism and maximum pool size 2 before scheduler initialization; exit0 and the retirement marker are required. Failure/timeout always reaches destroy/reap in finally and retains the log for assertion diagnostics. This preserves fresh behavioral evidence independently of the Gradle test JVM.
I: pass | Only a parameterized test and private bounded file reader are present; no runtime configuration API is added.
D: pass | It uses JDK ProcessBuilder and actual test/main class locations, without external libraries or alternate scheduler implementations.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/request/RequestAdmissionContentionTest.java
type: kg.aidarbek.smpp.request.RequestAdmissionContentionTest
sha256: f7148d6c07a5ad2f87b6eca3407d6b804fa65a0a2cd62009e15f16021a7d50f6
responsibility: Proves total admission-deadline precedence and zero resource/sequence consumption after notification-lock waiting.
consumers: JUnit supplies both capacity states; a controlled clock, real RequestWindow/BoundedNotifications and an exact queued virtual caller exercise admission.
S: pass | The two cases focus on one atomic admission boundary rather than duplicating general request-window implementation tests.
O: pass | Capacity availability is parameterized while original deadline and resource invariants remain constant.
L: pass | Both actual reds were observed: expired admission succeeded with space and backlog won when full. Green requires DEADLINE_EXPIRED/NOT_SENT, unchanged pending bytes/count, only the pre-existing reservation, and subsequent sequence 1. Finally closes the window and retires dispatcher ownership in both failed and successful test paths.
I: pass | The fixture needs only admission/counters/cancel and bounded notification ownership; no endpoint or socket collaborator is introduced.
D: pass | It uses JDK gates/clock/reflection and the real request types. Reflection selects notifier contention without changing production visibility or adding test-only hooks.
findings: none
```

## External bounded-admission diagnostic appendix

The following whole-type review is retained here from the external diagnostic
report. Its single Java type is outside the project source inventory; it does not
add a tagged project identity or change the 22-type carrier-fix inventory.

This single temporary Java fixture was compiled outside the repository with
`javac -Xlint:all -Werror` against the immutable candidate2 library JAR, then
executed once with two virtual-thread carriers. It is an explanatory diagnostic,
not a red/green implementation cycle or a capacity measurement. Project Java,
configuration, acceptance criteria and candidate binaries were unchanged.

Type: `WindowOneWriteProbe` (no nested, local or anonymous types).
Source: `/tmp/step19-window1-diagnostic/WindowOneWriteProbe.java`.
Final source SHA-256: `6b5e046525598dc543ddcb7d1fb5ec515c53afa9b7a9ddbd844684950031bea6`.
Library JAR SHA-256: `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f`.

S: The fixture answers one question: whether a locally admitted request can fail
with exact transport FULL while one request per real session is maintained.
It owns only finite endpoint composition, cause/accounting observation and cleanup;
there is no rate estimate or scheduler modification in library code.

O: The public sender, immutable endpoint options and typed handler are the real
variation seams. No transport mock or production test hook substitutes for the
actual TCP adapter. The explicit two-carrier process property selects the
adversarial scheduling condition before any scheduler initialization.

L: Each of 100 workers waits for its one request to finish before issuing another
original call. Failures are counted once and never retried. Exact structured
request reason/certainty and underlying transport kind are preserved. A successful
enquiry after the finite 20-second interval establishes subsequent control
progress; pending counts and endpoint termination establish ownership retirement.
All futures use finite waits; the external process supervisor kills and reaps after
55 seconds if the fixture itself cannot finish.

I: The fixture uses only client/server creation, binding, submission, enquiry,
resource observation and termination. No absent receipt/application contract is
stubbed. The unused session observer is a valid optional no-op, and authentication
returns an explicit completed acceptance for the local development peer.

D: It depends on the published library protocol/endpoint/request/SPI values and
JDK concurrency primitives. Real transport ownership remains within endpoints.
A concurrent bounded set of 100 workers records only the finite structured failure
categories; it does not expose internal mutable library state or locks.

Result: compilation had no warnings; execution exited 0. Of 84,958 attempted
calls, 84,408 succeeded and the peer received exactly 84,408. The 550 other calls
all failed with `WRITE_FAILED / NOT_SENT / FULL / started=false`. Enquiry and both
endpoint cleanups succeeded, and no pending request ownership remained. This
confirms the bounded transport-admission mechanism. The original preflight report
retains its own 459 aggregate failures without retrospectively inventing their
unreported individual cause fields.

Exact command, PID, UTC/monotonic overlap interval, source/artifact identities and
exit status are retained in `run.json`; output is retained in `run.log`. The extra
diagnostic JVM overlapped the corrected matrix/healthy/soak campaigns and provides
no isolated capacity comparison. Findings: none in this bounded fixture.
