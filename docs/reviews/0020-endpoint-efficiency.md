# Review: endpoint frame reuse, fixed codec catalogues and allocation evidence

## Scope and contract

Starting revision: `4da2aecd6394f9ff18d700bb8b47ae1422451002`.
The user requested at least 99% planned-request success under heavy load and more
efficient code. This bounded change removes a duplicate full encoding from the
outgoing paired-request, bind, enquiry, unbind and alert paths. A second bounded
refactor shares two fixed default codec registration lists during connection
setup, while retaining per-instance dispatch maps and limits. These changes add no
public API, codec policy, pending window, deadline policy, queue bound, transport
reservation, handler execution or retry/replay behavior.

Each path still validates/encodes its immutable command before admission, using
legal placeholder sequence **1**. `Pdu.validateHeader` does not permit sequence
zero for ordinary commands. After successful admission, `EndpointPdus.assignSequence`
sets only bytes 12–15 of that fresh endpoint-owned frame, in network byte order.
Both profiles share the four-field, 16-byte header; command length, ID, status
and body remain unchanged. The `PduCodec` contract returns a fresh owned frame,
and body codecs receive command/status/profile/limits, never sequence. The fixed
endpoint catalogue supplies immutable supported commands. No caller-owned byte
array is patched and the transport's own defensive copy remains.

The final immutable request envelope is still retained for paired response
validation. Permission checks, total invocation timestamps, window byte accounting,
`beforeWrite` cancellation/deadline checks, transmission certainty, negative
responses and close/drain ownership keep their original order. Alert preflight
also remains before callback/sequence admission; its former second encoding and
length comparison are replaced by assignment into the exact validated frame.
The one-shot outbind path already encoded once and is unchanged.

Seven Java source files contain all 13 affected identities: four coordinator
types, EndpointPdus, three notification types, two characterization classes and
three diagnostic types. The final parser inventory contains 530 identities;
all affected final whole-file hashes appear in the blocks below. Formatter
execution preceded inventory and review. No temporary Java implementation or
fixture was introduced beyond the probes' archived source copies; these have
the same reviewed statements, with only the send probe subsequently formatted.

## Source-backed candidates left unchanged

The review also identified bounded work which should be profiled under the
actual target workload before redesign:

- `EndpointConnection.discardFinishedMessageContexts` scans live contexts and
  calls locked `RequestWindow.pending` for each, before/after sends and responses
  and on ticks. `response` also calls the window-wide expiry scan. This creates
  work proportional to the live window, but changing expiry timing or context
  retirement would need its own behavioral evidence.
- `EndpointResources` snapshots live connections on its 5 ms timer. Window expiry,
  message expiry and notification expiry scan bounded ownership collections;
  temporary lists/arrays are present even when little work is due.
- `TcpTransport` uses an event/deadline-driven worker, not a fixed polling loop.
  Queue scans and condition signals protect independent accepted-write ownership.
  `BoundedNotifications` similarly releases its lock before application work.
  No measured contention finding justifies weakening these bounds or changing
  notification wakeup policy in this patch.

The retained [allocation samples](../ALLOCATION.md) locate codec validation streams
and byte arrays under a heavily contended historical workload. They are sampling
weights, not exact per-request allocation or isolated capacity evidence. This
change uses the separate exact calling-thread counter below to test its own
narrow hypothesis; codec-loop optimization is a separate change.

## Before/after allocation diagnostic

`EndpointSendAllocationProbe` is a manually invoked **test-source** diagnostic.
It uses one real coordinator/window/codec path and the existing controlled frame
port. Every cycle sends one immutable request, verifies the outgoing command
and assigned sequence, injects an independently constructed success response,
and observes the real request completion before the next cycle. Submit carries
160 short-message octets; data carries a 160-octet `message_payload` TLV.
Request construction and connection setup precede the measured interval.
Endpoint manager/timer work and actual socket I/O are outside this fixture.

Each variant ran in three fresh, sequential JVMs before and after the patch:
5,000 warmup plus 10,000 measured cycles per process, 24 processes total and
360,000 successful exchanges including warmup. Every process reports zero
pending requests and complete cleanup. Each has a 30-second external process
bound, and each result/cleanup wait is bounded. No CPU/load benchmark ran during
the root-owned healthy baseline.

The JDK was Ubuntu OpenJDK `21.0.12+8-1-24.04-Ubuntu` with `-Xms128m -Xmx256m`.
The exact per-variant command, repeated three times, is:

```sh
java -Xms128m -Xmx256m \
  -cp build/classes/java/main:build/classes/java/test \
  kg.aidarbek.smpp.endpoint.EndpointSendAllocationProbe 3.4 submit 5000 10000
```

Replace `3.4` with `5.0` and/or `submit` with `data` for the other variants.
These are calling-thread bytes per complete cycle, including its response decode,
correlation and controlled-port fixture work. Worker allocations, physical network
I/O, JVM native memory and setup are outside this counter. Elapsed nanoseconds
are retained for provenance, not used as a capacity/latency comparison.

| Profile / operation | Before bytes/cycle, median (range) | Reuse bytes/cycle, median (range) | Median saving |
| --- | ---: | ---: | ---: |
| 3.4 submit | 13,483 (13,111–13,541) | 8,527 (8,496–8,658) | 4,957 bytes / 36.8% |
| 3.4 data | 15,965 (15,491–16,067) | 10,186 (10,172–10,198) | 5,779 bytes / 36.2% |
| 5.0 submit | 13,084 (12,989–13,682) | 8,616 (8,606–8,675) | 4,468 bytes / 34.2% |
| 5.0 data | 16,025 (15,839–16,207) | 10,109 (10,060–10,227) | 5,917 bytes / 36.9% |

The unchanged root baseline, supplied by the coordinating reviewer, already
achieved 599,754 successful requests out of 600,000 planned (99.959%), with 246
generator skips and zero rejections/non-success responses: 3.4 submit, 10,000/s,
100 connections, 30 seconds warmup plus 60 measured. It ran without competing
load campaigns. That result is **before this refactor**; isolation is not a code
optimization and the allocation reduction does not itself establish a throughput
or 99% guarantee. Final integrated load qualification belongs to the root-owned
fresh workload runs.

All raw stdout/stderr, exact commands, timestamps, counter values and per-class
hashes are retained in `/tmp/endpoint-efficiency-allocation/`, including
`baseline-results.json`, `frame-reuse-results.json`, `comparison.json`,
`baseline-inputs.json`, both compiled-class manifests and the archived baseline
classes. The measured probe source was byte-identical in both runs, SHA-256
`2d4f11d13c98ae235e9ed29b8d97fcd565c4dd71e4ecfe956d1577af3ed1b4b2`;
its final formatter-only revision is reviewed below. Existing FakeFrameTransport
was `93fc2e1e47ae78e88f06c1702ce30be1dfdff82ba17f030dad0e4eb9e1762989`
and RawPeer was `47a968a2df73b9e075a8429daa7614c1c3514c715d61a7e8e39b69ac858028dc`.
Neither fixture was modified. The final delivery evidence archive retains these
inputs and every execution log; the frame-reuse class/source manifests identify that first measured checkpoint.
Its EndpointPdus source was `60fc39b88e509ea767f8f8f10b5d977d31c880db7cf89fa8d039390099f0be17`;
its other two production hashes remain those below. The subsequent setup-only
change has its own class manifest and construction measurements; the base commit
identifies the common predecessor.

## Bounded construction follow-up

The coordinating reviewer requested one further measured candidate before final
qualification: repeated EndpointPdus construction. The initial hypothesis was
corrected by source inspection. Default message/common/broadcast TLV support was
already shared; the remaining repeat work was command wrappers, temporary
registration/filter lists and per-instance dispatch maps. This patch shares
only **two fixed immutable default registration lists**, one per original request
direction. Each EndpointPdus still owns its local role and creates two PduCodec
instances with the caller's PduLimits. There is no global map keyed by user
configuration, shared mutable buffer, profile negotiation cache or retention of
caller/vendor extension codecs.

The immutability audit read all six default command implementation families:
BindRequestCodec, BindResponseCodec, ControlBodyCodec, MessageCommandCodec,
CommonCommandCodec and BroadcastCommandCodec. Their fields are final enum/ID/type
metadata or immutable support; all readers, writers, arrays and temporary lists
are local to a codec call. The support closure uses Map.copyOf/Set.copyOf in
MessageTlvSupport, CommonTlvSupport, BroadcastTlvSupport, TypedTlvRegistry and
TlvRules. Default value codecs hold final primitive/profile metadata and return
owned bytes or immutable OctetString values. No supplied extension implementation
enters these two default lists. CommandCodec already requires thread safety,
stable metadata, no retained mutable caller input and fresh outputs. Operation
catalogue construction has no reverse dependency on EndpointPdus.

`EndpointPdusIsolationTest` first passed before sharing and then passed unchanged
after it. Twelve simultaneously released tasks combine both roles, both profiles
and three independently owned limit sets. Each performs 30 repetitions of fixed
complete control/data bytes, strict whole-frame/TLV bounds for encode and decode,
re-encoding after mutation of a previous output, the 5.0 data submission/delivery
TLV difference and 3.4/5.0 error-body rules. The existing independent endpoint
wire characterization and negative-frame cases run alongside it.

`EndpointPdusConstructionProbe` ran three fresh processes per implementation,
500 warmup plus 1,000 measured constructions in each, alternating ESME and MC
with PduLimits(65536,4096,128). A single volatile retained instance prevents
construction from disappearing through escape analysis; it is cleared at the
end and there are no sockets/workers to clean up. The source is byte-identical
in both measurements, SHA-256
`af8836b541349278eb1103db3172ab91b4ca7a160f099e797041a618f1ed625c`.

```sh
java -Xms128m -Xmx256m -cp build/classes/java/main:build/classes/java/test kg.aidarbek.smpp.endpoint.EndpointPdusConstructionProbe 500 1000
```

| Repeated construction | Before median (range) | Shared lists median (range) |
| --- | ---: | ---: |
| Calling-thread bytes per instance | 18,200.7 (18,196.1–18,201.4) | 6,489.1 (6,488.9–6,489.1) |
| Time for 1,000 constructions | 24.76 ms (24.19–25.43) | 9.71 ms (9.30–10.24) |

The observed allocation saving is 11,711.6 bytes per instance (64.35%). This is
modest warmed setup work; class initialization, TCP/TLS/authentication and full
connection establishment are outside the interval. It is not evidence that codec
construction caused earlier startup failures. The raw six results, exact
commands/timestamps, comparison and final production class manifest are retained
as `construction-*-results.json`, `construction-comparison.json` and
`shared-catalogue-class-hashes.json` in the same diagnostic directory.

## Characterization, refactoring and verification

This is a behavior-preserving refactor under [TDD policy](../TDD.md), so its
sequence is existing/characterization GREEN → refactor → GREEN. No behavioral
failure is fabricated. The first two fixture compilation attempts are retained:
`01` used a nonexistent `OctetString.bytes()` instead of `value()`; `02` hit the
strict AutoCloseable warning for a broadly declared checked exception. Both
fixture problems were corrected before the first passing baseline; neither is
claimed as a behavioral RED.

Every Gradle invocation used normal caches and these exact common flags:

```sh
--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=endpoint-efficiency'
```

Logs `/tmp/endpoint-efficiency-01-...log` through the final checks are retained.
The repeated focused command was:

```sh
./gradlew :test \
  --tests kg.aidarbek.smpp.endpoint.EndpointOutboundFramesTest \
  --tests kg.aidarbek.smpp.endpoint.EndpointConnectionTest \
  --tests kg.aidarbek.smpp.endpoint.ExchangeCapabilitiesTest \
  --tests kg.aidarbek.smpp.endpoint.AlertWriteContractTest \
  --tests kg.aidarbek.smpp.endpoint.OutbindEndpointTest \
  --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=endpoint-efficiency'
```

- `03-baseline-characterization.log`: 30 executed cases pass before production edits.
- `04-expanded-baseline-characterization.log`: 32 executed cases pass before
  production edits, adding independent MC alert/control frames under both profiles
  and the client unbind frame to the characterization.
- `05-frame-reuse-green.log`: the identical 32 cases execute and pass after reuse.
- `06-library-contracts.log`: `./gradlew :test :javadoc` with the same flags executes
  all 768 library/architecture cases across 115 classes; zero failures, errors or
  skipped cases. Javadoc succeeds without warnings. This includes real peers,
  both profiles/modes/roles, request/notification ownership, cancellation,
  authentication retention, keepalives, reconnect, carrier progress and TLS tests.
- `07-format.log`: separate `./gradlew spotlessApply` succeeds.
- `08-inventory.log`: separate `./gradlew solidReviewInventory` succeeds after
  formatting; all 11 affected identities reconcile to the five paths below.

- `09-final-check-build.log`: `./gradlew check build` with the same flags succeeds
  after formatting and the complete review. All 768 library cases and
  141 simulator cases execute freshly, with zero failures/errors/skips;
  62 review-tool cases are restored from their matching cache.
  Strict compilation, Spotless verification, Javadoc/artifact generation and
  current review coverage all pass. The production compiler is up to date and
  at that first checkpoint all 202 production class hashes exactly equal the
  measured frame-reuse classes.
  Binary, source and Javadoc JAR inspection confirms both diagnostic/test types
  are absent and `META-INF/LICENSE`/`META-INF/NOTICE` remain present.

- `10-final-review-check.log`: the first completed evidence revision passes
  `./gradlew solidReview` before the separately authorized construction follow-up.
- `11-construction-probe-compile.log`: the new finite diagnostic compiles with
  strict warnings; its three baseline processes then execute freshly.
- `12-catalogue-baseline-characterization.log`: the seven selected cases pass
  before production catalogue sharing.
- `13-shared-catalogue-green.log`: the same seven cases pass after the bounded
  EndpointPdus change; its three after processes then execute freshly.
- `14-final-format.log` and `15-final-inventory.log`: separate formatter then
  parser inventory succeed; final coverage comprises 13 affected types among
  530 current identities.

- `16-final-check-build.log`: the final `./gradlew check build` with the common
  flags succeeds in 1m50s. All 769 library/architecture cases across 116 classes
  and 141 simulator cases across 42 classes execute freshly with zero failures,
  errors or skips. The 62 review-tool cases remain up to date from matching
  inputs. Strict compilation, Javadoc, Spotless, artifact tasks and all 530
  current review identities pass. All 202 final production class hashes exactly
  match the shared-catalogue construction measurement. Only this report's final
  result narrative was added afterward; no Java source changed.

The exact catalogue characterization command, with the common flags, is:

```sh
./gradlew :test --tests kg.aidarbek.smpp.endpoint.EndpointPdusIsolationTest --tests kg.aidarbek.smpp.endpoint.EndpointOutboundFramesTest --tests kg.aidarbek.smpp.endpoint.EndpointPdusTest --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=endpoint-efficiency'
```

Current architecture rules are unchanged and execute against the full production
package set, including the coordinator-to-SPI boundary and existing negative
probes. The manually executed allocation probe is not counted as a JUnit test;
its successful exchanges are diagnostic iterations. Passing tests, counters and
review coverage do not prove the absence of all defects.

## Whole-type review

The complete affected files, their observers/entries, supported codec and frame
ownership contracts, actual callers and related tests were reread. The reviews
below cover full types rather than just the changed encoding calls. No unresolved
contract or SOLID finding remains within this bounded change.

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection
sha256: bed9cd11ffdc15aef7e0e4061a3f2051c26d8cda8064df6af48082d487886001
responsibility: Serializes one endpoint connection lifecycle and composes codecs, request ownership, message/notification exchange and frame I/O.
consumers: BoundSession senders and endpoint owners invoke it; FrameListener and internal write/handler callbacks advance the same connection; SessionStateMachine, RequestWindow, EndpointPdus and bounded dispatchers own their separate policies.
S: pass | Its change driver is atomic per-connection coordination. Authentication, command encoding, permissions, pending-request bookkeeping, handler execution and socket implementation remain separate collaborators; the full lifecycle/send/receive/shutdown implementation was reread.
O: pass | Paired operation variation stays in OperationCatalog descriptors and profile-aware codecs. Frame reuse applies once to every registered paired sender, with the existing control/outbind composition left intact; no operation-specific optimization switch was added.
L: pass | FrameListener ordering, AutoCloseable idempotence, total invocation deadlines, NOT_SENT early-response refusal, exact response context, callback isolation and physical termination remain intact. Preflight still precedes sequence/byte admission, and failed writes retain RequestFailure certainty; the full library suite covers these contracts.
I: pass | Public consumers still use BoundSession capabilities and connection-attempt/shutdown results. The internal coordinator exposes only the existing operations required by owners and exchange collaborators; it adds no public encoding or buffer mutation API.
D: pass | All connection I/O continues through FrameTransport/WriteObserver; SocketAddress is metadata only. Policies depend on protocol/profile/request collaborators, with application authentication and handlers dispatched through bounded workers. All existing architecture rules pass.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.OutbindWrite
sha256: bed9cd11ffdc15aef7e0e4061a3f2051c26d8cda8064df6af48082d487886001
responsibility: Bridges the single outgoing outbind write to its total bind deadline and sending state.
consumers: EndpointConnection.sendOutbind creates it; FrameTransport calls its WriteObserver methods and OutbindFlow reads the guarded sending state.
S: pass | Only this notification write admission and failure affect the adapter; it stores no message or application policy.
O: pass | Outbind variations remain in the established OutbindFlow/configuration; this observer is a fixed bridge without an invented extension point.
L: pass | beforeWrite checks the original bind deadline and closed state under the shared reentrant guard, written is correctly empty because outbind has no response/completion promise, and failure closes the owning flow. Real outbind endpoint/raw-peer and cancellation tests remain green.
I: pass | It implements exactly WriteObserver; no request-result or application handler methods are imposed on this one-way bridge.
D: pass | It uses the transport observer port and enclosing lifecycle methods, never sockets, executors or application authentication directly.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite
sha256: bed9cd11ffdc15aef7e0e4061a3f2051c26d8cda8064df6af48082d487886001
responsibility: Completes an internal control/bind reply and advances close-after-flush or readiness only after transport completion.
consumers: EndpointConnection.writeReply supplies private readiness/no-op actions; FrameTransport invokes the observer and the coordinator owns pendingReplies.
S: pass | The adapter owns only the reply-write transition; user readiness execution is delegated by notifyBound to reserved notifications.
O: pass | The private afterWrite action handles the supported internal completion variation without changing transport or public callbacks.
L: pass | Its guard refuses closed connections; successful completion decrements the reserved reply exactly once under the connection guard, respects crossed-unbind flush ownership, and failures close with the transport cause. Bind/readiness, control failure and crossed-unbind tests pass.
I: pass | The three WriteObserver methods are the complete required transport subset; the internal Runnable is never a user-supplied callback.
D: pass | It depends on the observer port and coordinator transitions, with application completions on the existing bounded notification path.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite
sha256: bed9cd11ffdc15aef7e0e4061a3f2051c26d8cda8064df6af48082d487886001
responsibility: Connects one admitted RequestHandle to physical write certainty and terminal transport failure.
consumers: EndpointConnection.writeRequest creates it for bind/control/paired requests; FrameTransport invokes it and RequestWindow decides the winning request outcome.
S: pass | The adapter only delegates transmission/terminal ownership for one handle; it neither serializes commands nor interprets peer responses.
O: pass | Request type variation is already represented by RequestHandle<?>; bind/unbind closure is the existing lifecycle-specific consequence, so frame reuse introduces no new branching.
L: pass | beforeWrite still calls RequestWindow.beginWrite to atomically enforce deadline/cancellation and MAY_HAVE_BEEN_SENT; written does not complete an SMPP request; failed preserves the winning window outcome and lifecycle cause. Queued early-response, cancellation, deadline and pre/post-guard failure tests pass.
I: pass | WriteObserver is the precise needed interface; response futures remain solely on the request handle.
D: pass | The bridge depends on the request owner and transport port, with no concrete socket or application callback dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointPdus.java
type: kg.aidarbek.smpp.endpoint.EndpointPdus
sha256: 61757a4443691072108e6c0f2058f4b7cb08f97b9e9e38255499a7176ac56eda
responsibility: Composes the fixed endpoint codec catalogue, selects original-request direction and constructs explicit protocol-negative frames.
consumers: EndpointConnection encodes/decodes and builds negatives; EndpointConnection and NotificationExchange now assign reserved sequences to their private preflight frames.
S: pass | All methods concern the endpoint wire representation. The small assignment helper modifies only a header field in a fresh validated frame; sequence allocation and lifecycle admission remain outside it.
O: pass | Actual command variation remains in OperationCatalog/codec registration and profile direction selection. The two fixed default lists derive from that catalogue once, with no cache keyed by user configuration or shared vendor instances; new default operations still enter through their descriptors.
L: pass | Default codec fields/support are immutable and call buffers remain local; each instance retains separate PduCodec maps/limits and role. Concurrent mixed limits/profiles/roles verify decode/encode rejection and fresh outputs. Negative framing stays intact; assignSequence accepts a private validated frame and positive 31-bit sequence, modifies only network-order bytes 12-15, and preserves the independently tested remaining bytes.
I: pass | This package-private wire collaborator exposes only codec/header/negative helpers needed by the coordinator and notification owner, not a new mandatory interface.
D: pass | It composes codec/profile/protocol values and JDK collections/ByteBuffer. There are no transport, worker, authentication or application dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange
sha256: d2ab9e9d072fd1ca69368f8f0e834da344bf47f4ac88e5fd76e0c33c383ecc0e
responsibility: Owns bounded local outgoing alerts and incoming alert decisions without a response-window entry.
consumers: EndpointConnection admits alerts and runs expiry/drain/close; HandlerDispatcher shares the per-connection invocation lane; BoundedNotifications owns outgoing completion delivery; transport observers settle local writes.
S: pass | One-way alert lifecycle is the single change driver. Handler invocation and physical capacity stay in HandlerDispatcher, frame encoding in EndpointPdus, and network ownership in FrameTransport.
O: pass | Application behavior varies through the optional alert handler and existing notification sender settings. Reusing its validated frame removes duplicate serialization without adding alternate command/transport logic.
L: pass | Callback reservation still precedes sequence allocation and is released on failed setup; accepted writes remain owned through cancellation, guard, timeout, failure and physical settlement. Every asynchronous mutation uses the shared reentrant connection guard. Ordered-lane, capacity, graceful drain and controlled/real transport tests pass.
I: pass | Callers receive local NotificationSend completion/cancellation only; an alert handler has no request-response obligation and absent handlers remain optional.
D: pass | HandlerDispatcher, BoundedNotifications and connection frame ports retain variable execution/I/O ownership. This class imports no concrete sockets or TLS provider and runs no application code in transport callbacks.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange.Incoming
sha256: d2ab9e9d072fd1ca69368f8f0e834da344bf47f4ac88e5fd76e0c33c383ecc0e
responsibility: Retains one logical alert decision deadline/cancellation signal and its dispatcher ticket.
consumers: NotificationExchange receive/complete/expire/close mutate the entry; HandlerDispatcher observes its AtomicBoolean and owns unfinished physical invocation/stage capacity.
S: pass | The fields all belong to one incoming decision lifetime; no reply, network or application behavior is hidden in the entry.
O: pass | The private fixed entry has no supported independent extension. Application behavior varies at NotificationHandler and scheduling at HandlerDispatcher.
L: pass | Final deadline and cancellation identity do not change; ticket assignment is guarded by the connection owner and AtomicBoolean safely crosses the handler thread. Logical removal never claims the physical stage returned; handler retention and deadline tests pass.
I: not applicable | No custom interface or inherited resource contract is declared; the enclosing owner uses exactly the three retained fields.
D: pass | It depends on the dispatcher ticket and JDK cancellation signal, not an executor implementation or transport.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange.Outgoing
sha256: d2ab9e9d072fd1ca69368f8f0e834da344bf47f4ac88e5fd76e0c33c383ecc0e
responsibility: Retains one accepted alert write, bounded completion reservation, physical-start flag and local result.
consumers: NotificationExchange creates/cancels/expires it; FrameTransport calls WriteObserver and the reserved notifier completes its protected NotificationSend result.
S: pass | Only the state of one local alert write drives changes; encoding, endpoint admission and user notification execution remain elsewhere.
O: pass | Transport variation is behind WriteObserver; the fixed alert result has no response/peer retry extension to simulate.
L: pass | beforeWrite rejects an already terminal or expired entry before setting started; cancellation cannot win after started. written/failed remove the entry once, and a started failure closes the connection while preserving writeStarted. Deferred-port cancellation, timeout, failure, close and blocked-dependent capacity contracts remain green.
I: pass | It implements only the transport observer callback surface; callers receive completion and cancellation through NotificationSend rather than its mutable fields.
D: pass | State changes use the existing shared coordinator guard and completion reservation. No application callback is invoked directly and no concrete I/O or executor is constructed.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointOutboundFramesTest.java
type: kg.aidarbek.smpp.endpoint.EndpointOutboundFramesTest
sha256: 8646b0f1bb1b578f8c55b8964a631b6ed7afd273acac511fbec899b156ee45c1
responsibility: Characterizes complete outgoing frames, assigned request/notification identities, preflight rejection and caller payload ownership.
consumers: JUnit invokes four version-parameterized cases; the real coordinator and existing contract-tested FakeFrameTransport expose peer-visible bytes, while bounded authentication/notification owners are explicitly retired.
S: pass | Both cases test the same wire/admission invariant for ESME paired/control requests and MC one-way/control requests. Expected bytes are independently written fixed SMPP frames, not generated by the implementation.
O: pass | The two supported profiles are explicit EnumSource variation and only their bind version octet differs. Existing operation/lifecycle suites remain the extension location for unrelated command and failure scenarios.
L: pass | The test uses the established port contract, waits on observed completions with finite bounds, cancels its outstanding requests and closes all owned workers in finally. It checks invalid payload metadata consumes no sequence/frame and repeated sends preserve original OctetString bytes; all four cases pass before and after refactoring.
I: pass | JUnit supplies only parameterized execution; local fixture helpers build inputs and parse fixed expectations without extra mock or infrastructure interfaces.
D: pass | Protocol values and the actual coordinator/request path are exercised through a frame-port fixture. No concrete external dependency, sleep-based new scenario or production-only test seam is added.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointSendAllocationProbe.java
type: kg.aidarbek.smpp.endpoint.EndpointSendAllocationProbe
sha256: 917d3d51c0b13721d5e1aa9343014977968869477cf48c0265b3c33937b96039
responsibility: Runs a finite manually requested before/after calling-thread allocation diagnostic through actual endpoint request logic.
consumers: Developers invoke its standalone main with version, submit/data, warmup and measured counts; Connection supplies identical exchanges and JDK ThreadMXBean supplies the calling-thread allocation counter.
S: pass | Argument validation, bounded loops, allocation interval and one result record all serve this diagnostic. It does not become a production benchmark service or alter endpoint limits.
O: pass | The diagnostic intentionally fixes payload size and controlled-port composition while exposing only the four measured variants and finite cycle counts; new workloads belong in the standalone simulator.
L: pass | Invalid options/counter support and failed exchange/cleanup exit exceptionally; success is printed only after no pending request and complete cleanup. Fresh process measurements preserve exact inputs, and no latency or whole-JVM allocation claim is inferred from the caller-only counter.
I: pass | Its only executable surface is a conventional main; it adds no library API or benchmark task and is absent from published main/source/Javadoc artifacts.
D: pass | The probe uses JDK management APIs and existing test/coordinator contracts only. The management dependency is confined to test sources and no library runtime dependency or external reference code is introduced.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointSendAllocationProbe.java
type: kg.aidarbek.smpp.endpoint.EndpointSendAllocationProbe.Connection
sha256: 917d3d51c0b13721d5e1aa9343014977968869477cf48c0265b3c33937b96039
responsibility: Owns one bound coordinator, its finite controlled transport/notifier resources and one fixed immutable request for allocation cycles.
consumers: The enclosing probe constructs, repeatedly exchanges and closes it; FakeFrameTransport carries fresh frames, RequestWindow correlates raw independent replies and BoundedNotifications delivers request outcomes.
S: pass | Setup, one exchange and bounded teardown all serve the same measured fixture lifetime; no load scheduling or statistical/reporting responsibility is embedded here.
O: pass | Its two command values are the intentionally supported submit/data measurement variants, with profile chosen once. A separate fixture would own a materially different measurement topology.
L: pass | It validates each outgoing command/sequence, supplies a matching independent raw success response and waits for the real result. Constructor bind failures trigger teardown; close is idempotent through owned collaborators, restores interrupt status and reports exceptional/incomplete cleanup instead of success. All 24 fresh probe processes complete.
I: pass | AutoCloseable is the exact resource contract needed by main and exchange is the only repeated operation; it does not expose mutable requests, futures or coordinator state to callers.
D: pass | The fixture consumes existing endpoint/request/protocol contracts and a contract-tested controlled frame port. It owns its bounded notification worker and requires no socket, application server or runtime dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointPdusConstructionProbe.java
type: kg.aidarbek.smpp.endpoint.EndpointPdusConstructionProbe
sha256: af8836b541349278eb1103db3172ab91b4ca7a160f099e797041a618f1ed625c
responsibility: Measures finite calling-thread allocation and warmed elapsed time for repeated endpoint wire-collaborator construction.
consumers: Developers invoke main with bounded warmup/measured counts; EndpointPdus is the measured collaborator and JDK ThreadMXBean provides the allocation counter.
S: pass | Count validation, alternating-role construction, one retained escape barrier and the JSON measurement serve one diagnostic; it owns no workload scheduling or production metrics.
O: pass | Limits and alternating roles are intentionally fixed to isolate the requested setup candidate. Configurable runtime caches or general benchmarking extension points would not serve this bounded probe.
L: pass | Counts are limited to 1..5000 and unsupported counters fail explicitly; the final retained object is cleared before success output. All six fresh processes use identical source and report actual positive allocation/time without claiming socket establishment or retained-heap size.
I: pass | A conventional main is its only executable surface. It creates no library API, benchmark task or custom service interface.
D: pass | JDK management and existing endpoint/profile-limit values are confined to test sources. The object graph has no external dependency, socket or worker ownership.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointPdusIsolationTest.java
type: kg.aidarbek.smpp.endpoint.EndpointPdusIsolationTest
sha256: 4ae1b5bf20e9fbcb00eccfac0ba90b0a64093d08c81bda757f8c3b7ac8e9d96a
responsibility: Characterizes independent codec roles, profiles, limits and output ownership under concurrent endpoint construction/use.
consumers: JUnit drives twelve fixed tasks through actual EndpointPdus instances; known raw frames, profile rules and rejection assertions observe supported codec contracts.
S: pass | All checks target isolation of configuration and per-call wire state while default registrations are shared; it does not exercise unrelated session/authentication behavior.
O: pass | Both profiles/roles and three limit sets form the explicit supported variation. The command bytes and direction/profile differences are independent expectations rather than copies of the factory implementation.
L: pass | A ready/start barrier creates actual overlap; finite waits propagate worker assertion failures, and finally releases/cancels tasks before the owned executor closes. Fresh-byte mutation, whole-frame/TLV rejection and status/profile differences pass before and after sharing.
I: pass | The test requires only JUnit, fixed executor lifecycle and encode/decode; no production seam or factory-identity assertion is added.
D: pass | It tests the real wire collaborator using immutable protocol values and JDK concurrency. No socket transport, mock registry or external implementation can mask default codec sharing errors.
findings: none
```
