# Review: bounded request tracking and completion delivery

## Scope and contracts

Starting revision: `210359e` (`Add session state rules`). Step 9 was implemented
in `/tmp/lightweight-smpp-step9` without committing or changing the root checkout.
The owned change adds nine production files and five test files under the new
`kg.aidarbek.smpp.request` package: 16 type identities, including the nested
`BoundedNotifications.Reservation` and `RequestFailure.Reason`. No local,
anonymous or removed temporary Java fixture type was created; minimal API
stubs in the same source files were completed through the recorded TDD cycles.
The helper scripts and run logs under `/tmp` are not Java or runtime artifacts.

[REQUESTS.md](../REQUESTS.md) explains the compiled API. Session policies, codecs
and protocol types remain unchanged. The owning endpoint supplies one immutable
connection generation, exact encoded request bytes, a monotonic clock, a regular
expiry pump and an optional shared notification dispatcher. Transport supplies
physical writing and invokes the before-write guard. There is no autonomous
replay, wrapping sequence policy, socket owner, scheduler or application service.

The root-owned Step 9 `ArchitectureTest` snapshot and
[architecture review](0008-request-architecture.md) were copied solely for complete
verification and are excluded from the owned patch. That Java file's SHA-256 is
`fffc8b6983a19620a2517dbd837f7fb4be30ef8b17c44523f587e1e11e94cf2c`.
Its author records real forbidden codec and socket probes, along with final
coverage for the request boundary. The final inventory contains 129 total
project types, including the 16 reviewed here and the existing review tooling.

## Findings resolved during implementation and review

- Reserved completion capacity at admission, independently of pending count and
  bytes. Internal settlement releases pending resources immediately, while the
  completion reservation remains occupied until physical callback return. A
  shared dispatcher bounds blocked completion work across connection churn.
- Exposed a minimal CompletionStage and a separate internal terminal snapshot.
  Caller mutation of derived futures cannot alter request settlement. A blocked
  non-async dependent cannot block request guards, expiry or disconnect.
- Kept normal negative peer statuses in typed Pdu results. Only generic_nack uses
  PeerNackException, preserving ordered TLVs and raw status as a known peer
  outcome. Local RequestFailure retains operation, generation, sequence and
  conservative transmission knowledge.
- Kept deadline checks on every relevant transition and used subtraction across
  signed nanoTime wrap. The invocation-start overload accounts for prior
  validation/admission work; explicit expiry pumping remains the endpoint's job.
- Checked admission arguments and all bounds before consuming capacity or a
  sequence; sequence exhaustion cannot reserve another notification or wrap.
- Moved future completion from RequestOutcome to RequestHandle, leaving the
  outcome as a stable terminal selection. Inherited Throwable metadata remains
  standard Java mutable exception state without changing the terminal selection.
- Replaced the notifier's long-lived Object.wait parking with ReentrantLock and
  Condition after the root review identified Java 21 carrier retention. Existing
  tests were green before this behavior-preserving change and green afterward.
  No application callback executes while either request or dispatcher lock is held.
- Documented reservation ownership, post-close draining, timeout pumping and
  bounded cleanup reports. Close never silently drops accepted notifications;
  callers can report outstanding callbacks that have not returned.
- Used serializable primitive/UUID/enum snapshots for exception fields; raw nack
  TLVs are reconstructed as immutable protocol values. Serialization and binary
  ownership tests pass without warning suppressions or protocol changes.

The Java 21 [CompletableFuture documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
confirms that non-async dependents may run on the completing thread and describes
minimalCompletionStage/copy protection. The [virtual-thread guide](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
informs the choice of JUC condition parking for long worker waits. Protocol
expectations use the already implemented unsigned Pdu/header contracts and the
agreed correlation/timeout decisions in [API.md](../API.md).

All final affected types, inherited contracts and consumers were reviewed against
all five principles. The blocks below use the inventory obtained after explicit
formatting. Remaining findings: none.

## Type: kg.aidarbek.smpp.request.BoundedNotifications

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/BoundedNotifications.java
type: kg.aidarbek.smpp.request.BoundedNotifications
sha256: 58c24608b4ab46604363c05865a342c5bd10cf1d44f425982e1c4b73491a36fb
responsibility: Own a bounded set of completion reservations and a fixed number of notification workers.
consumers: RequestWindow reserves before admission; RequestHandle dispatches after settlement; endpoint owners share and close the dispatcher; BoundedNotificationsTest and RequestConcurrencyTest exercise its resource contract.
S: pass | Capacity accounting, queueing, worker wakeup and bounded shutdown all serve completion delivery; no request correlation, protocol permission, socket or timeout scheduling enters the type. The latest-failure field is bounded delivery diagnostics.
O: pass | Runnable is the actual varying notification boundary; request-result completion and endpoint result publication use the same reservations. Worker count is configuration; adding a command or endpoint changes no dispatcher branch.
L: pass | AutoCloseable close is idempotent and nonblocking, preserves already reserved work and never closes another resource. Tests cover off-caller execution, blocked dependents, post-close dispatch, duplicate permit consumption, callback failure continuation, and bounded termination. ReentrantLock/Condition replaces long Object.wait parking for the Java 21 virtual-worker baseline.
I: pass | Owners need reservation/admission and cleanup reporting; request consumers use reserve and dispatch only. The dispatcher does not claim the much broader ExecutorService contract or require meaningless scheduling methods.
D: pass | Depends on JDK Runnable, bounded state and JUC locks; it knows no endpoint, transport, codec, profile or session implementation. Endpoint composition supplies the shared instance, avoiding per-connection accumulated completion work.
findings: none
```

## Type: kg.aidarbek.smpp.request.BoundedNotifications.Reservation

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/BoundedNotifications.java
type: kg.aidarbek.smpp.request.BoundedNotifications.Reservation
sha256: 58c24608b4ab46604363c05865a342c5bd10cf1d44f425982e1c4b73491a36fb
responsibility: Represent one thread-safe single-use permit belonging to its issuing dispatcher.
consumers: RequestWindow creates admitted work with a reserved permit; RequestHandle consumes it once; endpoint publishers can dispatch or release their permit directly.
S: pass | Only permit ownership and the transition to released or queued work drive this inner type; worker execution and request terminal state remain with their owners.
O: pass | Runnable supplies notification variation without changing permit state transitions. The concrete final token is not an extensible notification policy or public constructor.
L: pass | Dispatch and release serialize on the same lock; release is idempotent and cannot free a dispatched task early. Null dispatch does not consume capacity, duplicate dispatch fails, and dispatch remains legal after admission closes. Tests exercise each observable transition and retained callback capacity.
I: pass | Its consumer needs exactly two mutually exclusive consuming operations. It exposes no worker shutdown, request cancellation or transport API.
D: pass | The intentional inner reference binds the token to its issuing BoundedNotifications instance; task behavior depends only on Runnable and never on a request or socket implementation.
findings: none
```

## Type: kg.aidarbek.smpp.request.PeerNackException

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/PeerNackException.java
type: kg.aidarbek.smpp.request.PeerNackException
sha256: b0b0805a71e3b1790d4a330f1c382bc8f820c361129f7cce1069d7b8cecd50ba
responsibility: Retain a correlated negative generic_nack as a known peer result when the expected response has another Java type.
consumers: RequestWindow creates it only for exact generation/sequence and negative generic_nack correlation; handle result/snapshot consumers inspect its reconstructed nack; RequestValuesTest verifies serialization and binary ownership.
S: pass | All fields belong to the received nack identity and content. It does not invent a local failure reason, transmission certainty, replay policy or normal operation-response hierarchy.
O: pass | Ordered raw TLV tags/values and raw status preserve unknown peer extensions without command-specific edits. Operation-specific negative responses continue to use Pdu<R>, so this exceptional representation stays narrowly scoped.
L: pass | Retains RuntimeException behavior and safe diagnostic text. Private primitive/UUID/array snapshots satisfy inherited serialization without suppressions; getters reconstruct immutable Pdu/Tlv values and defensive copies. Tests verify repeated tags, unknown unsigned status, defensive access and serialized round trips. Throwable metadata remains standard Java mutable exception state.
I: pass | Consumers need only request identity and the known peer nack; local failure reason/certainty methods are absent because they would misstate this outcome.
D: pass | Depends inward on existing immutable control/PDU/TLV values and JDK collections/UUID. It has no codec, session, transport or future dependency.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestFailure

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestFailure.java
type: kg.aidarbek.smpp.request.RequestFailure
sha256: a2fca2ba67c9739783e034dff3042395e36f9c6ded2a1824d8fc3415f1c0a7cf
responsibility: Report a structured local admission or terminal request failure with transmission knowledge.
consumers: Admission callers catch fail-fast rejection; result and terminal snapshot consumers distinguish local reasons, operation, generation, sequence and cause; RequestWindow creates failures.
S: pass | All retained fields describe one local failure. Message text contains command/generation/sequence/certainty but no credentials, payload or raw peer body. Peer generic_nack remains a separate known-outcome type.
O: pass | The nested reason vocabulary varies local outcomes without command-specific subclasses. New command identities remain numeric metadata and do not affect this exception implementation.
L: pass | Retains RuntimeException cause, message, stack and suppressed-exception contracts. Final primitive/UUID/enum fields survive normal Throwable serialization without making protocol values serializable. Tests verify admission sequence zero, admitted identity, terminal certainty, cause identity and serialized fields. No serialization warning is suppressed.
I: pass | Consumers can inspect individual failure fields and optional admitted identity without depending on the window, future implementation or peer-nack representation.
D: pass | Depends on its simple request identity/enum values and standard RuntimeException/UUID/Optional; neither transport exception classes nor codec types leak into the structured failure API.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestFailure.Reason

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestFailure.java
type: kg.aidarbek.smpp.request.RequestFailure.Reason
sha256: a2fca2ba67c9739783e034dff3042395e36f9c6ded2a1824d8fc3415f1c0a7cf
responsibility: Identify the bounded local admission and terminal failure categories.
consumers: RequestWindow selects reasons; application and endpoint consumers branch on stable local categories; parameterized tests cover count, byte, notification, deadline, close, exhaustion and terminal outcomes.
S: pass | Every constant names a local request failure; peer status and generic_nack identity are deliberately represented outside this enum.
O: pass | A closed agreed local vocabulary is appropriate; command/profile variation does not need new reasons or overridden enum bodies.
L: pass | Inherits Enum immutable identity, ordering and serialization without overrides. Tests use shared terminal scenarios across cancellation, write failure, disconnect and deadline rather than weakening the contract for a reason.
I: pass | Consumers only need category identity. No exception formatting, response data, callback or resource method is attached.
D: pass | Depends solely on JDK enum machinery; higher-level diagnostics and lifecycle behavior depend on these values.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestHandle

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestHandle.java
type: kg.aidarbek.smpp.request.RequestHandle
sha256: 9cd26eba1db35fcaaa31437f8f876f15585dea4fdcf85d9c18214842abb42c53
responsibility: Expose one admitted request identity, terminal observation, protected result notification and local cancellation.
consumers: Request callers observe result or cancel; endpoint and transport coordinators inspect identity, header, deadline and terminal state; only its owning RequestWindow changes outcome or transmission state.
S: pass | The fields and methods belong to one request capability. Future publication stays here after removing completion behavior from RequestOutcome; this type never allocates sequences, writes bytes or decides session policy.
O: pass | The response parameter R and Pdu envelope support new immutable command representations without command-specific handle behavior. Timing and terminal competition stay in the owner rather than subclass hooks.
L: pass | Final construction and immutable metadata prevent caller key replacement; volatile outcome/transmission fields support immediate observation. minimalCompletionStage prevents mutations of derived futures from completing, cancelling or obtruding the internal result. Tests retain real peer status after caller mutation, verify cancellation certainty and blocked-notification independence, and leave Object identity semantics unchanged.
I: pass | Callers can observe and cancel without obtaining the owning window or a completion mutator. Endpoint metadata is read-only; transport ownership and scheduling methods are absent.
D: pass | Depends on protocol values, JDK future facilities and its narrow request owner/notification permit. No external executor, socket, codec or state-machine implementation is exposed.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestIdentity

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestIdentity.java
type: kg.aidarbek.smpp.request.RequestIdentity
sha256: cabca5aa327fe54d010cdd55a19c8204578aa5d0f065d7d148b68fa695b69d17
responsibility: Carry immutable connection generation and a legal locally allocated SMPP sequence.
consumers: RequestHandle, local failure and nack diagnostics, endpoint correlation metadata and boundary/equality tests.
S: pass | The only invariant is nonnull immutable generation and sequence in 1..0x7fffffff; this value neither allocates nor consumes keys.
O: pass | Generation values are supplied by connection owners. No supported identity variation needs inheritance or changes for new commands.
L: pass | UUID and long are immutable; record equality/hashCode retain value semantics. Tests cover equality, null generation, zero and overflow boundaries. The public identity is metadata, not a constructible pending-window token.
I: pass | Consumers need the two component accessors and value equality; no callback or operation capability is attached to this record.
D: pass | Depends only on JDK UUID and Objects. Higher request behavior consumes this value without introducing transport or session dependencies.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestOptions

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestOptions.java
type: kg.aidarbek.smpp.request.RequestOptions
sha256: 4e10fc026df29102345210c8b711bafd6bbb14e7aa714f85821a48a9c6579a1a
responsibility: Validate and retain a request total timeout as an immutable Duration value.
consumers: Callers configure timeout; RequestWindow derives the absolute monotonic deadline; RequestOptionsTest and RequestValuesTest verify invalid and boundary values.
S: pass | Only positive signed-nanosecond timeout representation drives this record. It owns no clock sample, scheduler, retry, admission queue or per-phase timer.
O: pass | Callers vary Duration without altering lifecycle behavior. A record is sufficient for the currently supported single total-budget policy; no unused strategy interface is added.
L: pass | Null, zero, negative and nonrepresentable durations fail explicitly; Long.MAX_VALUE nanoseconds remains supported. Immutable Duration and generated record equality are verified. Conversion overflow is deliberately reported as argument validation rather than a partially admitted request.
I: pass | Consumers need the timeout accessor and a convenient factory; separate unrelated endpoint deadline settings do not become mandatory fields here.
D: pass | Depends only on JDK Duration and Objects. The window supplies actual time and enforces the budget; the value has no future, clock or infrastructure dependency.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestOutcome

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestOutcome.java
type: kg.aidarbek.smpp.request.RequestOutcome
sha256: 0e9ac1c2825363c23b7ec4d869ee81290a0d34fc21d6005c70553caa1bac2db0
responsibility: Retain the winning terminal selection independently of delayed application notification.
consumers: RequestHandle exposes the snapshot and translates it into future completion; endpoint coordinators inspect the response or failure immediately after internal settlement.
S: pass | Only selection and observation of one response or one failure belong here. Refactoring moved CompletableFuture completion to RequestHandle so this snapshot has no asynchronous execution responsibility.
O: pass | Generic Pdu<R> covers command variation, while standard RuntimeException accommodates the two explicit failure families without command branching or a new inheritance hierarchy.
L: pass | Private construction and nonnull factories ensure exactly one populated outcome. The selection and references never change; Throwable retains its standard mutable stack/suppressed-exception contract without changing request settlement. Tests assert the exclusive alternatives, normal negative responses and stable failure identity across notification. No custom superclass or value-equality promise is introduced.
I: pass | Consumers need only optional response and failure observations. No result mutator, cancellation, transport or scheduling method is exposed.
D: pass | Depends only on protocol Command/Pdu and JDK Objects/Optional/RuntimeException; completion infrastructure and the request window consume the snapshot, never the reverse.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestWindow

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestWindow.java
type: kg.aidarbek.smpp.request.RequestWindow
sha256: b75248394b93afad62f11e7078fb84ae0ff57a10fa9e91ff3287a39afcc307d9
responsibility: Own local sequence correlation, pending capacity and exactly one terminal outcome within a single immutable generation.
consumers: Endpoint coordinators admit and correlate control or message requests, inspect pending metadata and pump expire; transport before-write guards call beginWrite; RequestHandle delegates cancellation; all request tests cover the window.
S: pass | Count/byte reservations, monotonic sequence assignment, deadline checks and terminal settlement describe one pending-request lifecycle. Encoding, permission checks, scheduler policy, sockets, authentication and retries are explicitly delegated outside this type.
O: pass | Class<R> and Command/Pdu supply response representation and command identity, and LongSupplier supplies the actual time variation. New command pairs require no branch; only the protocol-wide generic_nack exception differs. Notification capacity is composed through a shared dispatcher.
L: pass | All mutable pending state is synchronized, terminal snapshots are safely published, and notifications dispatch after the lock is released. Admission validates before reservation; sequence exhaustion cannot wrap; foreign handles fail; late/wrong/duplicate replies cannot mutate outcomes. Shared terminal tests cover cancellation, write failure, disconnect and expiry before/after writing, and explicit races verify single settlement and capacity release. AutoCloseable ownership and shared-dispatcher shutdown are documented and tested.
I: pass | Endpoint owners receive a focused request-tracking API; handles expose observation/cancellation to request callers. No sender, receiver, transport or application-handler interface is imposed. pending is documented as metadata rather than reusable correlation authority.
D: pass | Depends inward on immutable protocol values and JDK abstractions for time and response type; completion infrastructure is isolated in BoundedNotifications and supplied by endpoint composition. The root-owned request architecture rule and real forbidden codec/socket probes enforce the boundary.
findings: none
```

## Type: kg.aidarbek.smpp.request.TransmissionCertainty

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/TransmissionCertainty.java
type: kg.aidarbek.smpp.request.TransmissionCertainty
sha256: b2ead7b789fddb5f41d442e7a27b3a5df1f26eebc3625d6f0dfc16d1020b8ac4
responsibility: Name the two local facts available about physical request transmission.
consumers: RequestWindow establishes certainty at its before-write guard; RequestHandle and RequestFailure expose it; terminal and race tests check both values.
S: pass | The two enum constants describe one knowledge distinction; neither claims peer acceptance or performs a state transition.
O: pass | This is the agreed closed transmission vocabulary. New operations, transports or peer statuses use the same distinction without additional enum behavior.
L: pass | Preserves Enum immutable identity and serialization without overrides. Tests prove NOT_SENT before a winning cancellation/deadline and MAY_HAVE_BEEN_SENT once write authorization wins, including an explicitly coordinated race.
I: pass | Consumers need only the constant identity; there are no mandatory send, retry or acknowledgement methods.
D: pass | Depends solely on JDK enum facilities. Transport adapters report through RequestWindow and do not become dependencies of this value.
findings: none
```

## Type: kg.aidarbek.smpp.request.BoundedNotificationsTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/request/BoundedNotificationsTest.java
type: kg.aidarbek.smpp.request.BoundedNotificationsTest
sha256: c26761dc2c1e6a759e81979f4dfcf27e9624bc48a18600bc9d6f8741d213a28c
responsibility: Verify reservation, worker, failure-reporting and shutdown contracts of bounded completion delivery.
consumers: JUnit Jupiter executes four focused cases against the real dispatcher; CompletableFuture provides observable callback signals with bounded waits.
S: pass | Tests cover one resource contract: permits reserve before work exists, off-caller dispatch, exactly-once consumption, close-drain behavior and continuation after callback failure. No socket or request policy is duplicated.
O: pass | Behavior scenarios can be extended through callbacks and capacity configuration without inventing a replacement dispatcher interface or coupling to private queue representation.
L: pass | The final test class has no custom superclass; assertions propagate through the JUnit method. Resource cleanup uses finally where close itself is under test; all waits are bounded and callback signals surface failures. Post-close issued-permit behavior and inherited AutoCloseable semantics are exercised.
I: pass | Only JUnit test methods and local helpers exist; no production callback implementer is forced to adopt test lifecycle or executor APIs.
D: pass | Depends on the public dispatcher, JUnit and JDK signals. It does not mock private state or require a network, wall-clock sleep, vendor library or build tool API.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestConcurrencyTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/request/RequestConcurrencyTest.java
type: kg.aidarbek.smpp.request.RequestConcurrencyTest
sha256: eb0437254bd57aefdf0e62147935dc79697e9e872f80d0e5e4cf9d82de9a0d81
responsibility: Exercise protected future observation, bounded blocked callbacks and explicitly coordinated request races.
consumers: JUnit Jupiter executes real windows/dispatchers; CountDownLatch coordinates contenders and CompletableFuture propagates bounded thread results.
S: pass | Every scenario checks the request/notification concurrency contract. Helpers launch one controlled action and restore interruption; they do not own unrelated networking, credentials or timing policy.
O: pass | BooleanSupplier supplies contender variation; adding a terminal event or coordination scenario does not change production behavior or require a fake transport subclass.
L: pass | No custom superclass or substitutable fake is introduced. Contender exceptions are completed into observed futures, waits are bounded, and blocked callbacks are released in finally. Tests verify derived-future isolation, cross-window physical notification retention, one final alternative, capacity release and conservative write/cancel certainty.
I: pass | JUnit consumes only test methods; race helpers accept the minimum action boundary. There is no all-purpose endpoint fixture with unused callback methods.
D: pass | Depends on public request/protocol contracts and JDK synchronization primitives. Controlled AtomicLong time replaces scheduling assumptions; no socket, codec or external executor is required.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestOptionsTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/request/RequestOptionsTest.java
type: kg.aidarbek.smpp.request.RequestOptionsTest
sha256: 5bb5f04da5ff39e4b5a41804f3b6e4b466b7cba3bba6bb8b638e2e357f74c08c
responsibility: Detect invalid total timeout options before request admission.
consumers: JUnit Jupiter executes the original timeout-value TDD scenario against RequestOptions.
S: pass | The focused test owns only null, zero, negative and overflow validation expectations; request lifecycle behavior is tested separately.
O: pass | Additional duration boundaries can be added as value inputs without modifying production request strategies or introducing shared fixtures.
L: pass | No custom superclass or replacement implementation exists; standard JUnit assertThrows verifies the declared exception contract. The recorded first red was a behavioral missing-validation failure, not a compiler failure.
I: pass | One package-private test method is the entire test surface; no infrastructure or application-handler capability is implemented.
D: pass | Depends only on the public options value, immutable JDK Duration and JUnit assertions; no clock, timer or network dependency exists.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestValuesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/request/RequestValuesTest.java
type: kg.aidarbek.smpp.request.RequestValuesTest
sha256: 62df0f45c3a3f6211762331eac22e0ad4763df75bfded66d2804384ee2cc367e
responsibility: Characterize immutable request metadata and inherited exception ownership/serialization contracts.
consumers: JUnit Jupiter checks RequestIdentity, RequestOptions, RequestFailure and PeerNackException; an in-memory Java serialization helper verifies inherited Throwable behavior.
S: pass | All three cases concern request value boundaries, binary ownership or exception serialization. The helper only round-trips an explicitly typed value and owns its byte streams.
O: pass | Expected values are independently constructed; new boundary values or exception metadata can be checked without accessing or copying production internals.
L: pass | No custom test superclass or fake serializer exists. JDK Object streams exercise actual inherited serialization; Class.cast avoids unchecked casts, streams close deterministically, and assertions confirm record equality, stable fields and defensive TLV access.
I: pass | JUnit needs the three scenarios; the helper exposes only value and type, with no unrelated fixture lifecycle or resource capability.
D: pass | Depends on request/protocol public values, standard in-memory serialization and JUnit. It does not change protocol classes to accommodate exception tests or rely on external data.
findings: none
```

## Type: kg.aidarbek.smpp.request.RequestWindowTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/request/RequestWindowTest.java
type: kg.aidarbek.smpp.request.RequestWindowTest
sha256: fde723ea3610f851ec951394f5bbfbad44f7585b7c5130e3bae83aaf16d97396
responsibility: Verify bounded admission, exact correlation and shared terminal-state behavior with controlled time.
consumers: JUnit Jupiter runs parameterized bounds and terminal cases against RequestWindow; existing immutable control/bind PDUs supply independent expected responses.
S: pass | All 29 final cases concern one pending-window contract, including constructor atomicity, sequence exhaustion, generic nack, normal negative statuses, foreign handles, deadlines and cleanup. No endpoint permission or codec implementation is duplicated.
O: pass | CsvSource expresses admission and terminal variation; existing Pdu and command values vary expected peer data. Adding supported commands does not require a fake window implementation or switch in production correlation.
L: pass | No custom superclass or alternate transport is claimed. Tests cover each local terminal cause before and after write authorization, signed nanoTime wrap, exact deadline boundary, out-of-order/duplicate/wrong replies and fail-fast diagnostics. All future waits are bounded, resources close, and shared scenarios require consistent slot/byte release and exactly one terminal snapshot.
I: pass | JUnit consumes focused test methods and narrow PDU/admission helpers. There is no fake server or broad handler interface with unsupported methods.
D: pass | Depends on public request/protocol types, JUnit and injected AtomicLong or constant clocks. The package-private initial-sequence constructor permits direct exhaustion evidence without reflection or billions of admissions.
findings: none
```

## TDD and verification

All commands below ran in `/tmp/lightweight-smpp-step9`. Every Gradle invocation
used the checked-in wrapper and appended exactly:

```sh
--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step9'
```

For cycles 01, 02–03, and 04–11, the focused task prefixes were respectively:

```sh
./gradlew test --tests 'kg.aidarbek.smpp.request.RequestOptionsTest'
./gradlew test --tests 'kg.aidarbek.smpp.request.BoundedNotificationsTest'
./gradlew test --tests 'kg.aidarbek.smpp.request.RequestWindowTest'
```

Each numbered red and green run has its actual complete output in
`/tmp/step9-NN-red.log` and `/tmp/step9-NN-green.log`. These logs were captured at
execution, not reconstructed from later test results. Green runs in this table
executed the test task; normal configuration/build caches remained enabled.

| Cycle | Selected observable scenario and actual relevant red | Green result |
| --- | --- | --- |
| 01 | Nonpositive/nonrepresentable timeout: assertThrows found no IllegalArgumentException. | Timeout scenario passed. |
| 02 | Reserving a second permit at capacity one returned a permit; expected empty. | Reservation/release scenario passed. |
| 03 | Callback ran on the submitting test thread; assertNotSame failed. | Both notification scenarios passed on owned workers. |
| 04 | Two admissions both received sequence 1; expected second sequence 2. | Monotonic generation/byte metadata scenario passed. |
| 05 | Count and byte overload admitted work, and notification overload threw NoSuchElementException instead of RequestFailure; three failures. | All four window cases passed with structured fail-fast bounds. |
| 06 | A valid out-of-order matching response returned false instead of settling its handle. | Five window cases passed, including wrong/duplicate rejection and normal negative status. |
| 07 | Cancellation/write failure did not settle, before-write authorization returned false, and disconnect did not publish a terminal snapshot; all six terminal variants failed. | Eleven cases passed, including before/after-write certainty and repeated losing outcomes. |
| 08 | Earlier-invocation admission and six due-deadline transitions failed their expiry expectations; seven failures. | Eighteen cases passed with signed clock wrap and due-deadline precedence. |
| 09 | A matching negative generic_nack for a typed bind request returned false. | Nineteen cases passed, retaining known nack status and ordered TLVs. |
| 10 | Post-maximum sequence admission threw IllegalArgumentException rather than structured exhaustion. | Twenty cases passed; no extra notification was reserved. |
| 11 | A null connection generation was accepted by construction. | Twenty-one cases passed with all constructor/admission validations checked before reservation. |
| 14 | Owned-notifier close reporting returned false instead of reporting drained completion. | All 35 request-package cases passed with awaitNotifications and outstanding reporting. |

Cycle 14's red used the window test prefix above; its green used
`./gradlew test --tests 'kg.aidarbek.smpp.request.*'` with the same mandatory flags.
Two development compilation issues are recorded separately and are not claimed
as TDD reds: `step9-06-compile.log` caught a nonexistent OptionalParameters.empty
helper in a new test; `step9-12-compile.log` caught javac's warning about explicitly
closing try-with-resources variables in close-contract tests. Tests were corrected
to use the existing constructor and explicit finally ownership, respectively.
No assertion was weakened to obtain a pass.

The following characterization/refactor runs use actual results:

- `step9-12-characterization.log`: `test --tests 'kg.aidarbek.smpp.request.*'`
  executed 30 passing cases after adding coordinated terminal/write races,
  derived-future mutation, globally bounded blocked callbacks and dispatcher
  close/failure contracts. These behaviors already passed; no red is invented.
- `step9-13-characterization.log`: the same command executed 34 passing cases,
  adding real Throwable serialization, immutable binary access, generation/foreign
  handle and response-class contracts.
- `step9-15-refactor.log`: `test javadoc` executed 377 library/architecture cases
  after moving notification behavior out of RequestOutcome. Build succeeded;
  Javadoc reported 11 missing parameter/serialized-field comments, subsequently fixed.
- `step9-16-format.log`: `spotlessApply` applied formatting separately.
- `step9-17-verification.log`: `test javadoc solidReviewInventory` ran the passing
  behavior/architecture suite, but Javadoc rejected unescaped less-than signs in
  a throws description. This documentation failure is not behavioral red evidence.
- `step9-18-refactor.log`: `test javadoc` executed 377 passing cases after the
  ReentrantLock/Condition notifier refactor and Javadoc correction; no compiler
  or Javadoc warnings remained.
- `step9-19-characterization.log`: `test --tests 'kg.aidarbek.smpp.request.*'`
  executed 41 passing cases after expanding every deadline transition to both
  NOT_SENT and MAY_HAVE_BEEN_SENT. Those added checks already passed.
- `step9-20-format.log`: `spotlessApply` ran separately before the final inventory.
- `step9-21-verification.log`: `test javadoc solidReviewInventory` executed all
  383 library/architecture cases (41 request cases), with no failures or skips.
  Javadoc was UP-TO-DATE from its successful run; inventory executed and listed
  129 total identities. The formatter did not rewrite the root-owned architecture
  snapshot, whose recorded hash remains unchanged.

The architecture suite selects actual request and protocol production types and
runs all eight package/dependency/cycle checks. Runtime dependencies, full check
and licensed archive verification are recorded after review reconciliation below.

- `step9-22-check-build.log`: `check build` passed. `solidReview`, formatting
  verification and all three archive tasks executed; the 383-case library suite
  was UP-TO-DATE and the unchanged 60-case review-tool suite was FROM-CACHE.
  These reused results are not described as fresh test execution. Review coverage
  reconciled all 129 current type identities against final whole-file hashes.
- `step9-23-dependencies.log`: `dependencies --configuration runtimeClasspath`
  executed and reported no runtime dependencies. Python ZIP inspection confirmed
  LICENSE/NOTICE in binary, source and Javadoc JARs and no review-tool classes in
  those artifacts. Local links in both owned Markdown documents resolved, and the
  root-owned architecture snapshot hash was unchanged.

The owned patch contains only nine request production files, five request test
files, REQUESTS.md and this report. No root/common status document, existing Java
source, architecture snapshot or separate architecture report belongs to it.
The final source inventory contains every created Java identity, including the
two named nested types. No unresolved SOLID finding or unfinished production API
stub remains.
