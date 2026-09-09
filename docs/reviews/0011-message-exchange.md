# Review: Message exchange

## Scope and design

Starting revision: `e79b9f4cb905056fe6d91ded563e2a19b48ce4d9`.
Step 12 connects submission, delivery and data messages to both endpoint roles
under SMPP 3.4/5.0. This report reviews **48 current type identities in 33 Java
files**, including changed existing types, examples, the extracted frame fixture,
its member class and four location-bound anonymous observers. The complete
project inventory contains **228 identities**. The blocks below are the exact
formatted source inventory and whole-file SHA-256 evidence. No separate temporary
Java fixture or dependency source is excluded from this change's ownership.

The public boundary is `OperationSender<Q,R>` plus optional
`RequestHandler<Q,R>` registrations and immutable decision/configuration values.
The coordinator still composes one RequestWindow, SessionStateMachine and frame
transport port. `MessageExchange` owns receive-order reply reservations;
`HandlerDispatcher` separately owns globally bounded physical invocation/stage
work. Logical timeout cannot free unfinished application capacity, and a full
ordinary transport queue retains the already-owned head reply and first write
budget. Protocol permission remains in the existing state policy, including
3.4 data_sm in both directions/all bound modes and the different 5.0 table.

Requested later common operations have a concrete paired descriptor seam:
directional codecs, actual TLV predicates, original-request response validation
and a negative body factory receiving original request plus effective profile.
Raw malformed-input error construction stays separate because no decoded request
exists. No query, outbind, alert, broadcast, encoding, receipt or replay service
is claimed by this step. The core production snapshot handed to the later owner
matches all 19 final production file hashes; later-step sources are not included.

## Findings corrected during development

- Sender and handler services were initially absent; genuine wire/value reds
  established typed acceptance and the single-window send path.
- Invocation order, arrival-to-decision budget and queued-expiry checks were
  tightened before the next behavior was added.
- Reply reservations now include completed tail decisions and queued/active
  output. Oversized decisions use the already-reserved negative body, and a full
  transport queue does not lose ownership or refresh the write budget.
- Original-request transaction rules now apply to emitted and received replies;
  invalid responses settle connection/request failure without response loops.
- Independent read-only concurrency review found synchronous write-completion
  recursion. A 6,000-reply regression emitted only 938 of 6,001 total frames
  before correction; the iterative guarded drain now completes the entire queue.
  That reviewer checked the actual red/green logs and found no further concrete
  issue in its bounded MessageExchange/HandlerDispatcher/coordinator pass.
- The extracted shared fake was tightened through real regressions for FULL
  classification, independent control reserves, dequeued write retention and
  exceptions from guard/terminal/close callbacks. Test cleanup no longer masks
  those failures. These changes affect test code, not the production TCP adapter.
- Runnable examples now submit and acknowledge a separate delivery. They use
  graceful endpoint drain so an accepted incoming acknowledgement precedes unbind.

No known SOLID violation remains in the reviewed current types. A source hash
and passing architecture test establish freshness/structural evidence, not a
substitute for the following contract review.

## Complete current type review

### kg.aidarbek.examples.ExampleClient

```solid-review
source: src/examples/java/kg/aidarbek/examples/ExampleClient.java
type: kg.aidarbek.examples.ExampleClient
sha256: dfe3dee47e409b03f4782c5a579fbd0980f391c90e62b9bc5a157c110f4374b5
responsibility: Runs one finite documented bind/submission/independent-delivery/enquiry/graceful-drain demonstration.
consumers: CLI users and EndpointExamplesTest call main/run; the companion demo server provides acceptance and delivery.
S: pass | All methods support the runnable example and its raw payload construction, with no library implementation responsibilities.
O: pass | Uses public typed sender/handler/configuration APIs; it extends example application policy without modifying transport or request tracking.
L: pass | Private utility construction, resolved loopback target, finite waits and finally cleanup preserve lifecycle. Submission status, delivery notification, enquiry and complete shutdown are checked. Raw-peer client regression proves submission precedes controls and delivery ack flushes before unbind; standalone JVM smoke exits zero.
I: pass | Implements only the delivery handler it needs and requests only submission capability; it uses the same RequestHandle mechanism as any application.
D: pass | Depends only on public library API and JDK values/concurrency. The examples source set remains excluded from runtime/source/Javadoc library archives.
findings: none
```

### kg.aidarbek.examples.ExampleServer

```solid-review
source: src/examples/java/kg/aidarbek/examples/ExampleServer.java
type: kg.aidarbek.examples.ExampleServer
sha256: 0ad505fa0de4f9be7e271761c2522bf38b7d7f9051c49aa4564849253dcbce91
responsibility: Runs a finite loopback demonstration of authentication, in-memory submission acceptance and separate welcome delivery.
consumers: CLI users and EndpointExamplesTest call main/create; ExampleClient consumes its public wire behavior.
S: pass | The application example owns its simple demo acceptance/ID/delivery policy and endpoint cleanup, without becoming a simulator or durable message store.
O: pass | Handlers installed through EndpointHandlers customize application behavior without touching library coordinator code; later production storage is deliberately outside this example.
L: pass | Finite lifetime, finally shutdown, explicit version acceptance/advertisement and demo-only credentials preserve the existing example lifecycle. Submission IDs are atomic across sessions; independent delivery failure closes that session. Real example tests and standalone JVM smoke verify successful exchange/cleanup without claiming handset delivery.
I: pass | Only submission handling and an optional delivery capability are used; no unrelated operation callbacks or unsupported stubs.
D: pass | Depends on public endpoint/protocol APIs and JDK atomics/stages. Credentials/payload are not logged; no dependency enters production archives.
findings: none
```

### kg.aidarbek.smpp.endpoint.BoundSession

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/BoundSession.java
type: kg.aidarbek.smpp.endpoint.BoundSession
sha256: 8737d87678f8fa27469526d367640b3cf6f6b3edcdd0716d4f9cccf3ff90e316
responsibility: Exposes thread-safe session observation, control operations and focused implemented message capabilities.
consumers: Client callers, server bound observers, IncomingRequest handlers and examples use the facade.
S: pass | Every method concerns this one session; storage, worker ownership, framing and negotiation computation are delegated.
O: pass | Generic sender(Operation) supports catalogue expansion, while named submission/delivery/data methods are ergonomic views. It does not add unsupported common-operation methods.
L: pass | Optional capability reflects current role/mode/profile/state and each stored sender rechecks on send. Metadata/negotiation are snapshots; protected stages prevent external completion. AutoCloseable close aborts only its own connection. Full matrix, stored-sender and existing control/cancellation tests remain green.
I: pass | Applications choose operation-specific typed senders, control handles or observation methods; optional handlers remain separate contracts.
D: pass | Delegates to EndpointConnection and exposes protocol/request/session values plus SocketAddress metadata; concrete network adapters are absent.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointConnection

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection
sha256: b0020d95374655795d1a1593d438ea78d6cd0d34240fb88a1a542efebea8b297
responsibility: Serializes one connection's lifecycle and composes policy, codecs, request ownership and inbound message exchange over SPI.
consumers: SmppClient/SmppServer construct it; BoundSession, deadline resources, authentication and frame-port callbacks consume it.
S: pass | The coordinator sequences existing owners rather than reimplementing their responsibilities. MessageExchange owns reply decisions; RequestWindow owns all outgoing correlation, deadlines and certainty; SessionStateMachine owns exact permissions.
O: pass | Paired descriptors and OperationCatalog add actual operations without handler switches. Extracted beginClientBind preserves the existing total bind deadline for later explicit flows; no outbind behavior is implemented here.
L: pass | FrameListener/AutoCloseable contracts retain one closure, pre-write NOT_SENT response gating, independent peer/local namespaces, total send invocation time, protected notifications and late-auth suppression. Original request contexts are bounded by the existing window and pruned on send/tick/response/close. Both field-context directions are validated; invalid responses close without nacks. The full 138 endpoint cases preserve controls and add message races/cleanup.
I: pass | Public users see BoundSession and RequestHandle. Package helpers separate typed sending, inbound reply writing and lifecycle; application code is scheduled off transport and locks.
D: pass | Depends on FrameTransport/WriteObserver ports, policy, request and codec contracts; only SocketAddress metadata comes from java.net. Twelve existing nonempty architecture rules pass on the expanded package; concrete TCP wiring stays in endpoints.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite
sha256: b0020d95374655795d1a1593d438ea78d6cd0d34240fb88a1a542efebea8b297
responsibility: Completes one internal control/error response write and its bounded close-after-flush obligation.
consumers: FrameTransport invokes it; bind readiness and crossed-unbind completion use its internal afterWrite action.
S: pass | All methods serve control reply completion, with application publication delegated to bounded notifications.
O: pass | The transport observer seam supports real and controlled fake adapters without concrete socket branches.
L: pass | Guard rejects closed connections; written decrements tracked replies before readiness or close progression; failed aborts once. afterWrite is a library action, not application code. Existing authentication-before-notification, crossed-unbind and shared-port tests remain green.
I: pass | Implements only WriteObserver and carries the two required control-completion values; no message handler or outgoing correlation methods.
D: pass | Uses SPI observer/failure and the coordinator; no concrete I/O or application implementation.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite
sha256: b0020d95374655795d1a1593d438ea78d6cd0d34240fb88a1a542efebea8b297
responsibility: Connects one existing outgoing request handle to transport transmission and failure settlement.
consumers: FrameTransport invokes it for bind, controls and typed message sends; RequestWindow owns the result.
S: pass | Its sole change driver is request-to-wire ownership and certainty, independent of operation bodies.
O: pass | The same bridge supports all typed RequestHandle instances and SPI implementations without a second response engine.
L: pass | beforeWrite delegates atomic deadline/cancellation/transmission ownership to window.beginWrite. written intentionally performs no second settlement; failed preserves the window's winning local outcome and lifecycle closure for bind/unbind. Existing NOT_SENT/started-write and deadline regressions plus message-correlation tests verify behavior.
I: pass | Only the guard and terminal port callback methods are required; the no-op written correctly reflects response-owned request completion, not an unsupported operation stub.
D: pass | Depends on RequestWindow/RequestHandle and the frame-port contract; no concrete adapter or user callback.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointHandlers

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointHandlers.java
type: kg.aidarbek.smpp.endpoint.EndpointHandlers
sha256: ec3ad5c5eff926212d479ecbc1a08458afda6d1f31430921837ad7273d811927
responsibility: Owns an immutable snapshot of optional typed operation handlers for an endpoint.
consumers: Client/server ExchangeConfig share it across sessions; MessageExchange looks up registrations.
S: pass | Registration lookup and snapshot ownership change together. Invocation scheduling and failure translation remain separate collaborators.
O: pass | The typed operation-key map supports further implemented pairings without new mandatory callback methods.
L: pass | Map.copyOf prevents later builder mutation from changing a running endpoint; handlers themselves are explicitly required to tolerate shared concurrent use. Duplicate-registration and real both-endpoint tests exercise typed lookup; identity of canonical keys is deliberate.
I: pass | Empty registration is supported and sends a defined negative; applications register only the operations they serve.
D: pass | Uses Operation keys, Registration wrappers and JDK immutable collections; no concrete transport or application backend.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointHandlers.Builder

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointHandlers.java
type: kg.aidarbek.smpp.endpoint.EndpointHandlers.Builder
sha256: ec3ad5c5eff926212d479ecbc1a08458afda6d1f31430921837ad7273d811927
responsibility: Collects one mutable, unshared set of distinct typed handler registrations.
consumers: Application setup and examples use on/build; EndpointHandlers copies the result.
S: pass | Only registry construction, canonical-key validation and duplicate detection drive this builder.
O: pass | Generic on handles every implemented catalogue pairing; no separate builder method is needed for each paired message operation.
L: pass | Builder is explicitly not thread-safe. Non-null checks and putIfAbsent make duplicate rejection atomic without replacing the original; build returns an independent immutable snapshot. ExchangeValuesTest provides the duplicate regression.
I: pass | Applications need only on and build and never see internal Registration casts or scheduling.
D: pass | Depends on the internal implementation catalogue and handler contract, not sockets, authentication or persistence.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointHandlers.Registration

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointHandlers.java
type: kg.aidarbek.smpp.endpoint.EndpointHandlers.Registration
sha256: ec3ad5c5eff926212d479ecbc1a08458afda6d1f31430921837ad7273d811927
responsibility: Retains one typed operation/handler relationship and constructs its typed incoming context.
consumers: EndpointHandlers stores it; MessageExchange invokes it only through HandlerDispatcher.
S: pass | The wrapper localizes the type-safe cast established during registration; it owns no capacity or response ordering.
O: pass | Q/R capture supports each paired descriptor without unchecked casts or a switch on every Java request class.
L: pass | Class.cast verifies the decoded request, then a new immutable Pdu preserves original status/sequence. Exceptions and null stage results propagate to the dispatcher failure boundary. Matrix and failure tests cover typed invocation under both endpoints.
I: pass | Only one package-private invoke method is needed by the scheduler; application code sees the narrower RequestHandler contract.
D: pass | Uses protocol Pdu, the typed handler and IncomingRequest; it does not call concrete I/O or construct workers.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointPdus

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointPdus.java
type: kg.aidarbek.smpp.endpoint.EndpointPdus
sha256: 0eefd58b0b26ba6e716088e00a3778a0a8e682cd884ee261334e124537de4a8c
responsibility: Composes directional bounded codecs and raw-header protocol-negative shapes for one endpoint role.
consumers: EndpointConnection preflights/decodes/encodes traffic; error paths use negative; tests inspect independent bytes.
S: pass | Directional codec selection and recoverable wire error response construction belong to endpoint wire composition; it performs no authentication or lifecycle admission.
O: pass | Codec registration now comes from OperationCatalog. Raw malformed-input negative construction remains separate because no valid Q exists; each later command's required error shape can be added there without fabricating a decoded original request.
L: pass | Response direction derives from its original request, including data_sm. Header-only errors refuse response-to-response generation, preserve usable sequence or use zero for invalid sequence, and enforce nonzero error status. EndpointPdusTest and profile/data/context/raw-negative tests remain green.
I: pass | The coordinator needs only encode/decode/header/negative/control; concrete body codec internals are not exposed in BoundSession.
D: pass | Composes codec contracts, protocol/profile values and JDK buffers; no sockets, workers or application handlers.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointResources

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointResources.java
type: kg.aidarbek.smpp.endpoint.EndpointResources
sha256: 6f76cad73cde57d801a704558d66d17fa28e0d04a1b01913d2a0cbb1799c6bb7
responsibility: Owns endpoint-wide connection admission, shared bounded workers, deadline scan and bounded termination.
consumers: SmppClient/SmppServer own it; permits attach coordinators; public termination exposes its immutable cleanup snapshot.
S: pass | All resources share endpoint lifetime. This owner composes separate authentication, message and notification policies without encoding commands or deciding application acceptance.
O: pass | ExchangeConfig adds a dedicated handler collaborator; per-session exchanges share it so reconnects cannot create replacement worker pools. Existing connection and notification policy remains separate.
L: pass | AutoCloseable abort is idempotent and asynchronous; graceful shutdown drains both outgoing requests and incoming replies before unbind, then closes handlers/notifications/timer. Physical unfinished handlers remain counted after transport permit release. Original cleanup failures and blocked-worker counts survive in a bounded snapshot. Resource-failure, graceful-drain and five-reconnect retention tests cover these contracts.
I: pass | Composition consumes admission, session snapshots and termination; application code never receives executor or mutable permit maps.
D: pass | Uses coordinator/dispatcher contracts and JDK scheduling/locking; listener close/termination are supplied callbacks/stages, and it constructs no socket adapter.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointResources.Permit

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointResources.java
type: kg.aidarbek.smpp.endpoint.EndpointResources.Permit
sha256: 6f76cad73cde57d801a704558d66d17fa28e0d04a1b01913d2a0cbb1799c6bb7
responsibility: Retains one connection capacity reservation through construction and physical cleanup.
consumers: Client/server composition attach a connection or retire an incompletely constructed transport; cleanup releases it.
S: pass | Attach, retire and release all track the same one-shot capacity ownership obligation.
O: pass | CompletionStage cleanup lets the same permit cover successfully constructed connections and failed construction without adapter-specific branches.
L: pass | Owner locking forbids duplicate attach/retire and makes release idempotent. Exceptional cleanup records the original cause and keeps the slot; shutdown races close attached connections. EndpointResourcesTest verifies both failure paths and no early slot reuse.
I: pass | Package-only one-shot operations are sufficient; the resource map and stage publication are hidden.
D: pass | Depends on its resource owner and abstract cleanup stages/connection I/O termination, not concrete TCP implementation.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointTermination

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointTermination.java
type: kg.aidarbek.smpp.endpoint.EndpointTermination
sha256: ecfaeba84960ce9b222ebb97114c14361857933f105fd1d4208cbd09eda916ee
responsibility: Stores one immutable bounded endpoint-cleanup observation, including unfinished message handlers.
consumers: Applications, examples and shutdown tests inspect counts, completion flags and original failures.
S: pass | All fields describe the same shutdown snapshot; it neither retries cleanup nor changes when application work later finishes.
O: pass | The added handler count extends the actual cleanup observation; the original seven-argument constructor remains available and delegates zero handler work.
L: pass | Nonnegative counts and List.copyOf preserve snapshot/value semantics; Throwable identity is intentionally retained and list mutation is forbidden. complete requires every count zero, all worker/listener flags and no failures. Blocked-stage and cleanup-failure tests show that late cooperation does not rewrite a prior result.
I: pass | Read-only accessors and complete provide focused observation without forcing callers to manage workers.
D: pass | Only JDK immutable-list ownership and primitives; no session, socket or application scheduling dependency.
findings: none
```

### kg.aidarbek.smpp.endpoint.ExchangeConfig

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ExchangeConfig.java
type: kg.aidarbek.smpp.endpoint.ExchangeConfig
sha256: 8472cc11755ccbbf7ef9246dfa4c20e76e5daf811c03c6d2bbfa26fb261845eb
responsibility: Pairs immutable exchange limits with optional handler registrations.
consumers: SmppClient/SmppServer pass it to shared resources and per-connection exchanges.
S: pass | Endpoint message configuration is its only change driver; it does not perform dispatch.
O: pass | Configured bounds and typed registrations supply the actual variation; no inheritance is needed for this two-value composition.
L: pass | Both members are non-null immutable references; record equality is safe and defaults use empty handlers with finite bounds. Existing-constructor negative tests and configured-handler integration verify defaults do not imply acceptance.
I: pass | Consumers configure message services without extending authentication or requiring a supplied executor.
D: pass | Composes endpoint value/configuration types and JDK Objects; no concrete transport or application storage.
findings: none
```

### kg.aidarbek.smpp.endpoint.ExchangeOptions

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ExchangeOptions.java
type: kg.aidarbek.smpp.endpoint.ExchangeOptions
sha256: 0b46bbe97e4af9716a4bb924a30845727aa0e712b94462b266df11c85c96bfb0
responsibility: Defines finite handler execution and per-session ordered reply accounting limits.
consumers: Applications configure it; EndpointResources and MessageExchange consume the independent bounds.
S: pass | All fields describe message application work or retained reply ownership, distinct from connection and request-window options.
O: pass | Resource/deadline policy varies through immutable data rather than scheduler subclasses; defaults are explicit library policy.
L: pass | Rejects nonpositive active/reply limits, negative queue, combined int overflow, undersized reply budget and invalid bounded durations. Record value semantics are immutable. Value, count, byte, queued-deadline and saturation tests exercise effects; wire accounting is explicitly not exact heap accounting.
I: pass | Separates handler/reply limits from unrelated listener/authentication settings; clients and servers consume the same focused policy.
D: pass | Depends only on Duration and the existing shared duration validator; no executor, socket or application implementation.
findings: none
```

### kg.aidarbek.smpp.endpoint.HandlerDispatcher

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/HandlerDispatcher.java
type: kg.aidarbek.smpp.endpoint.HandlerDispatcher
sha256: 04aa4203b6c4bc15933ba4ca778bd46a79082c9453b606bdb1a4168c1640b95b
responsibility: Owns globally bounded physical handler invocation/stage work and lane scheduling for one endpoint.
consumers: EndpointResources owns shutdown; MessageExchange submits and cancels work; HandlerDispatcherTest coordinates lifecycle.
S: pass | Admission, fixed worker ownership, lane eligibility and bounded termination all serve the same physical application-work budget. Protocol encoding and reply ordering are elsewhere.
O: pass | Supplier/completion collaborators and explicit Lane identities support typed operations and other actual handler consumers without changes to worker ownership.
L: pass | AutoCloseable close is idempotent and nonblocking; shutdown rejects new work, removes unstarted work, retains active invocation/stage capacity, and reports unfinished workers honestly. Callbacks run outside its ReentrantLock. Condition waits are bounded and interruption is propagated. Direct dispatcher and reconnect-retention tests cover these invariants.
I: pass | Consumers receive a ticket/lane and a few admission/cleanup observations, not the underlying ExecutorService. There is no unsupported supplied-executor path.
D: pass | A purpose-built endpoint execution adapter uses JDK executor/lock contracts and injected functions. It has no sockets, codecs or application persistence; pure session/request packages remain independent.
findings: none
```

### kg.aidarbek.smpp.endpoint.HandlerDispatcher.Lane

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/HandlerDispatcher.java
type: kg.aidarbek.smpp.endpoint.HandlerDispatcher.Lane
sha256: 04aa4203b6c4bc15933ba4ca778bd46a79082c9453b606bdb1a4168c1640b95b
responsibility: Tracks one connection identity and receive-order invocation queue within its owning dispatcher.
consumers: MessageExchange holds the lane; HandlerDispatcher checks ownership and selects eligible tickets.
S: pass | The owner pointer, waiting deque and invoking flag implement only per-session invocation-start ordering.
O: pass | Multiple lanes enable independent sessions while retaining a common bounded scheduler; no subclass or policy interface is needed for this internal identity.
L: pass | A lane cannot be submitted to another dispatcher. All mutable lane fields are accessed under its dispatcher lock, and the next invocation waits for the previous invocation to return, not for its stage to finish. The ordered-start/cross-session test establishes this distinction.
I: pass | Package consumers hold the opaque lane without mutating its queue or worker state.
D: pass | Depends only on its enclosing scheduler and JDK deque; no protocol or transport knowledge.
findings: none
```

### kg.aidarbek.smpp.endpoint.HandlerDispatcher.Ticket

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/HandlerDispatcher.java
type: kg.aidarbek.smpp.endpoint.HandlerDispatcher.Ticket
sha256: 04aa4203b6c4bc15933ba4ca778bd46a79082c9453b606bdb1a4168c1640b95b
responsibility: Retains one accepted invocation and returned stage until both physically settle.
consumers: Owned workers run it once; MessageExchange cancels the logical decision; dispatcher counts its physical ownership.
S: pass | Cancellation, stage observation, single publication and physical release are facets of the same accepted unit; response bytes and protocol policy stay outside.
O: pass | Generic R and supplied invocation/completion bridge support typed handlers without per-operation Runnable subclasses.
L: pass | Runnable is internally scheduled once. Cancellation removes queued work but never releases active capacity early. Atomic observation/publication guards prevent duplicate decisions; finally blocks release lane and physical ownership only after invocation/stage completion. An unobservable stage is conservatively retained. Tests cover blocked invocation, blocked stage, cancellation, queue expiry and cleanup deadlines.
I: pass | Only package-level cancel is exposed; consumers cannot force the owned stage complete, execute the ticket again or free its slot.
D: pass | Depends on JDK stage/functions/atomics and its scheduler. The bridge invokes library completion outside locks, while application work stays off transport threads.
findings: none
```

### kg.aidarbek.smpp.endpoint.HandlerResponse

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/HandlerResponse.java
type: kg.aidarbek.smpp.endpoint.HandlerResponse
sha256: 85a595153a156407f86b2f57936bfa077d2629c58a2ed5a999585a0558937294
responsibility: Stores the application response status, immutable typed command and optional stricter send requirements.
consumers: RequestHandler returns it; MessageExchange validates and encodes it using the received request.
S: pass | Unsigned status and non-null decision data are cohesive value invariants. It does not assign a peer sequence or determine delivery state.
O: pass | Generic R and SendRequirements support implemented pairings without subclassing or special response schedulers.
L: pass | Record equality/hash code follow immutable collaborators; uint32 bounds and null checks preserve representation, and the convenience constructor delegates COMMON without disabling actual-field validation. ExchangeValuesTest and invalid-response/missing-advertisement tests cover rejection. Protocol command toString redaction remains inherited from the underlying values.
I: pass | A handler supplies precisely status/body/requirements; no correlation, socket or result-window API is required.
D: pass | Only protocol Command, immutable SendRequirements and JDK Objects; acceptance policy remains with the application.
findings: none
```

### kg.aidarbek.smpp.endpoint.IncomingRequest

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/IncomingRequest.java
type: kg.aidarbek.smpp.endpoint.IncomingRequest
sha256: 69e8ea67c299aa5798399cc589e1c288a31196085cdd64665bced6c58d493cb9
responsibility: Carries one immutable received PDU/session/deadline with read-only cooperative cancellation observation.
consumers: RequestHandler implementations, including simulator consumers and the examples, inspect the context; Registration constructs it.
S: pass | Only application decision context drives the type. It neither completes responses nor interprets payload or storage outcomes.
O: pass | Generic Q supports catalogue operations without adding handler-specific fields; further lifecycle mutation stays in the owning exchange.
L: pass | Immutable references preserve peer identity and deadline; AtomicBoolean provides cross-thread cancellation visibility without exposing a setter. Closed-session and expiry tests assert cancellation and suppression of late results. Default identity semantics do not reveal payload through a generated field dump.
I: pass | Handlers receive only session, PDU, deadline and cancellation accessors; no write observer, resource permit or internal window is exposed.
D: pass | Uses protocol values, BoundSession and JDK AtomicBoolean; it contains no infrastructure construction or application implementation.
findings: none
```

### kg.aidarbek.smpp.endpoint.MessageExchange

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange
sha256: 5582d5ad76797ac0d8657f5e0d18cac946c8ce8d679fed046efa9dfc80600511
responsibility: Owns one connection's bounded incoming decisions and receive-order paired response output.
consumers: EndpointConnection delegates valid message requests/expiry/drain/close; HandlerDispatcher returns decisions and FrameTransport settles writes.
S: pass | Reply reservations, decision deadlines, fallback encoding and ordered output are the cohesive inbound exchange lifecycle. Outgoing sequence/correlation remains RequestWindow.
O: pass | Operation and typed registry collaborators support additional paired families; no handler switch repeats codec families. Profile-specific negatives and original-request validators are descriptor seams.
L: pass | Owner synchronization covers pending count/bytes and response state; application invocations stay outside that lock. Reservations include original request plus fallback or encoded response through active write settlement. FULL retains ownership and original budget; inline callbacks drain iteratively. Count/byte/arrival/deadline/saturation/6000-reply regressions and shutdown tests establish boundedness and liveness.
I: pass | Coordinator needs receive, expire, pendingCount and close; applications see RequestHandler/IncomingRequest rather than reply entries or transport guards.
D: pass | Uses endpoint collaborators plus protocol values, SPI observer/failure and an injected monotonic clock. It never constructs sockets, invokes storage, or creates a second request window.
findings: none
```

### kg.aidarbek.smpp.endpoint.MessageExchange.Entry

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange.Entry
sha256: 5582d5ad76797ac0d8657f5e0d18cac946c8ce8d679fed046efa9dfc80600511
responsibility: Retains one received request, decision deadline, cancellation token and reserved/encoded reply state.
consumers: Only MessageExchange and its ReplyWrite manipulate the entry.
S: pass | Each field belongs to the lifetime of one admitted incoming request and its output ownership.
O: pass | The descriptor/request pair stores operation variation as data; no per-operation entry hierarchy is needed.
L: pass | Identity equality makes duplicate same-sequence peer arrivals distinct ownership entries. Bytes, fallback replacement, attempted/write deadline and writing flag remain owner-locked; cancellation visibility uses AtomicBoolean. Count/byte/active-write tests prove retention; clearing fallback after decision avoids double-buffer ownership.
I: pass | Private fields are available only to the enclosing exchange, so application handlers cannot mutate its response or release accounting.
D: pass | References immutable protocol/descriptor data and a dispatcher ticket, with no concrete I/O or independently owned executor.
findings: none
```

### kg.aidarbek.smpp.endpoint.MessageExchange.ReplyWrite

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange.ReplyWrite
sha256: 5582d5ad76797ac0d8657f5e0d18cac946c8ce8d679fed046efa9dfc80600511
responsibility: Bridges one admitted ordered response to the frame port's guard and terminal callbacks.
consumers: FrameTransport calls it; MessageExchange releases its entry and EndpointConnection advances graceful drain.
S: pass | Guard, written and failed implement one response-write ownership handoff, not application notification or request correlation.
O: pass | The SPI permits inline or asynchronous callbacks; the enclosing iterative flush guard handles both without adapter-specific code.
L: pass | beforeWrite refuses an entry no longer owned. written releases its count/bytes once and continues FIFO/drain; failed preserves the transport failure and closes. Active/queued slot retention, full-queue retry and 6000 inline reply tests cover exactly-once settlement and stack safety.
I: pass | Implements only WriteObserver; no request handle or application method is required.
D: pass | Depends on the frame-port observer/failure contract and its exchange owner; no TcpTransport dependency.
findings: none
```

### kg.aidarbek.smpp.endpoint.MessageOperations

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageOperations.java
type: kg.aidarbek.smpp.endpoint.MessageOperations
sha256: 8249b9c6620b947ec71777c3b84d7eb9fd5e746ae2403dd678c5925306273375
responsibility: Publishes the three implemented submit, deliver and data pair descriptors.
consumers: Application senders/registries and OperationCatalog use its immutable constants.
S: pass | Changes follow these three message families and their existing codec rules; this class does not decide application acceptance or state permissions.
O: pass | Other paired families extend OperationCatalog with their own descriptors. Shared MessageCommandCodecs and MessageResponseRules remain the source of wire and original-request checks.
L: pass | Constants preserve exact request/response types and IDs. Negative bodies are valid omitted message responses under both implemented profiles; actual request/response TLVs are inspected. Twelve real matrix cases and raw negative fixtures exercise these contracts.
I: pass | Applications select only required operation constants; no mandatory query, broadcast or delivery implementation is imposed on submission users.
D: pass | Composes existing message codecs with immutable protocol values and JDK collections; no socket, executor or persistence dependency.
findings: none
```

### kg.aidarbek.smpp.endpoint.Operation

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/Operation.java
type: kg.aidarbek.smpp.endpoint.Operation
sha256: 5c581585a4bd3a9622422cb7949a0cc1963a647eae8368f05462b16a43876e9b
responsibility: Defines one immutable library-owned typed request/response pairing and its wire validation collaborators.
consumers: BoundSession and OperationSender expose keys; EndpointHandlers, EndpointPdus, EndpointConnection and MessageExchange consume the pairing.
S: pass | Pair IDs, Java types, codecs, negative body, actual TLV requirements and original-request validation all change with one implemented operation; it owns no scheduling.
O: pass | Package-private collaborators are the concrete extension needed by requested common operations; BiFunction negatives retain original request and profile, and ResponseValidator avoids repeating codec-family switches in the coordinator.
L: pass | Canonical singleton identity is intentional, not value equality. All collaborators are non-null; typed casts reject mismatched bodies, requirement merging only tightens optional-parameter use, and codec lists are immutable. ExchangeMatrixTest and ExchangeCapabilitiesTest exercise the public consequences.
I: pass | Public callers need only IDs/types and typed capability keys; internal codec factories and validation are not exposed as an application plugin API.
D: pass | Depends on protocol/profile values, codec contracts and JDK functions, with no transport or worker construction. The existing endpoint architecture rule covers it.
findings: none
```

### kg.aidarbek.smpp.endpoint.Operation.ResponseValidator

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/Operation.java
type: kg.aidarbek.smpp.endpoint.Operation.ResponseValidator
sha256: 5c581585a4bd3a9622422cb7949a0cc1963a647eae8368f05462b16a43876e9b
responsibility: Checks response conditions that require the decoded original request and direction.
consumers: Operation delegates to MessageResponseRules; EndpointConnection uses it before accepting peer responses or emitting handler responses.
S: pass | Only paired field/transaction semantics drive this functional contract; sequence allocation and lifecycle remain elsewhere.
O: pass | A codec family supplies its own validator without altering request tracking or transport. This is the explicit seam requested for common-operation response rules.
L: pass | The four inputs retain original request/response, effective profile and original direction. Validation throws for invalid context; both handler and peer transaction-diagnostic regressions prove enforcement without nack loops.
I: pass | One validation method is sufficient; it does not require handlers to decode bytes or allocate request IDs.
D: pass | Only immutable protocol values and profile/direction appear in the signature; no endpoint resources or concrete I/O.
findings: none
```

### kg.aidarbek.smpp.endpoint.OperationCatalog

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OperationCatalog.java
type: kg.aidarbek.smpp.endpoint.OperationCatalog
sha256: 98db0f31084b036f3a623f8fece13730d7a0e5a259c92c10f4f1c300329e54df
responsibility: Provides the single internal list and lookup of implemented paired endpoint operations.
consumers: EndpointPdus, EndpointConnection and EndpointHandlers.Builder consult the same catalogue.
S: pass | The locally implemented pairing list is its only change driver; catalogue permission itself remains SessionStateMachine policy.
O: pass | Adding an implemented family changes this composition list rather than several handler dispatch switches. No unsupported common-operation descriptors are installed in Step 12.
L: pass | Returns the immutable message descriptor list and Optional absence for other request IDs. Builder canonical-key checks and capability matrix tests prevent unsupported positive claims.
I: pass | Two package-level queries serve the codec, admission and registration consumers without exposing mutation.
D: pass | Depends only on Operation, MessageOperations and JDK List/Optional; it does not import transport or application services.
findings: none
```

### kg.aidarbek.smpp.endpoint.OperationSender

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OperationSender.java
type: kg.aidarbek.smpp.endpoint.OperationSender
sha256: c8894985030c83c834d1894356d282cda2eab10684a389af40af39459cbcb518
responsibility: Exposes one typed send capability over a particular session and operation.
consumers: Applications and examples send Q and observe the existing RequestHandle<R>; EndpointConnection owns execution.
S: pass | The three overloads only select default options and extra requirements before delegation; they introduce no new request engine.
O: pass | The same sender supports every catalogue pairing, so requested common operations do not require duplicating lifecycle/correlation logic.
L: pass | Stored capabilities recheck current permission; null options explicitly select defaults while null command/requirements fail. Total invocation time is captured before owner locking; typed nonzero statuses, nack, cancellation and certainty remain RequestWindow outcomes. Capability and correlation tests cover these paths.
I: pass | Each capability accepts only its Q and returns its R; receiver users need not implement or call submission methods.
D: pass | Delegates to the coordinator and uses protocol/request/session value contracts; no sockets, external executor or codec mechanics enter the public API.
findings: none
```

### kg.aidarbek.smpp.endpoint.RequestHandler

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/RequestHandler.java
type: kg.aidarbek.smpp.endpoint.RequestHandler
sha256: c28ce28dc5b9bd2f2c4d70f65ca882061869d1257b2667da6d1ff792dddf879d
responsibility: Defines one asynchronous typed application acceptance decision.
consumers: Applications implement it; EndpointHandlers.Registration and HandlerDispatcher invoke it.
S: pass | The sole responsibility is returning status/body after application-required acceptance work. Transport response writing is not delegated to the handler.
O: pass | Each optional operation can install its own handler without modifying session policy or implementing unrelated services.
L: pass | The documented contract permits asynchronous overlap across decisions and concurrent sessions, while requiring a non-null stage/result. Boundary tests handle throws, Error, nulls, failed stages, invalid decisions and timeout with protocol negatives; blocked invocation/stage tests establish cancellation and physical retention.
I: pass | A single handle method avoids a monolithic listener with unsupported-method stubs; typed Q/R retain the operation relationship.
D: pass | Signature depends on IncomingRequest, HandlerResponse, protocol Command and CompletionStage. Application storage/executor implementations remain outside the library.
findings: none
```

### kg.aidarbek.smpp.endpoint.SmppClient

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/SmppClient.java
type: kg.aidarbek.smpp.endpoint.SmppClient
sha256: e2609b0db89d2bb9d640d3ebbc8df496b16dd3bfcd3163bfe8febaccfb7665e6
responsibility: Composes and owns normal ESME TCP connections and shared endpoint resources.
consumers: Applications/examples construct clients, start cancellable binds, query sessions and request bounded shutdown.
S: pass | All methods concern client endpoint lifetime and concrete wiring; protocol decisions remain in the coordinator/policies.
O: pass | New ExchangeConfig constructor supplies optional message services while existing constructors retain finite defaults. Typed catalogue sends need no per-operation client network code.
L: pass | AutoCloseable abort remains idempotent; admitted readiness is protected and published off I/O, pre-admission failures may already be complete. Constructor inputs are validated before resources, failed construction retains permits until cleanup, and cancellation targets the same workflow. Existing client tests plus real message matrix/examples verify ownership.
I: pass | Client creation does not require authentication or handlers for unowned services; sending occurs through a bound capability.
D: pass | This explicit composition boundary selects TcpTransport, resource options and codec preflight; EndpointConnection remains SPI-only. No runtime dependency or application persistence is introduced.
findings: none
```

### kg.aidarbek.smpp.endpoint.SmppServer

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/SmppServer.java
type: kg.aidarbek.smpp.endpoint.SmppServer
sha256: 8c4d339a514e024ab7a8f552de969358d1c3cdd67f351adf5ac1a2f0a9a61964
responsibility: Composes a normal message-center listener, bounded authentication and optional message services.
consumers: Applications/examples configure the server; accepted transports become EndpointConnections and bound observers receive sessions.
S: pass | Listener admission and endpoint ownership form its responsibility; authentication decisions and message processing are separate contracts.
O: pass | The ExchangeConfig constructor adds optional typed services without replacing existing constructor behavior or adding operation switches to the accept loop.
L: pass | AutoCloseable close is idempotent, start is single-use, invalid graceful duration does not mutate lifecycle, accepted transport ownership transfers only after admission, and supplied authentication executor remains caller-owned. Existing adversarial tests, all-profile/mode message matrix and blocked-handler shutdown pass.
I: pass | A caller implements authentication and only needed message registrations; no query/delivery stubs or executor ownership transfer is required.
D: pass | Concrete TcpListener/TcpTransportConfig wiring is intentionally here; coordinator work uses SPI, authentication and typed handlers. The architecture rules permit this composition boundary and forbid reverse infrastructure dependencies.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointConnectionTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest
sha256: 36aa1e374b7c125aadd7a74824e18d3d608f9c3a9583f2a01defc18b734cc638
responsibility: Verifies coordinator binding/control ownership and the substitutable frame fixture's lifecycle.
consumers: JUnit executes sixteen current cases; EndpointConnection, RequestWindow, authentication and FakeFrameTransport are exercised.
S: pass | Cases focus on connection protocol ownership: pre-write response gating, version/authentication, deadlines, certainty, close and crossed namespaces. Step 12 message scenarios were extracted to a separate test class.
O: pass | Parameterized profile/status cases and a reusable fake support meaningful new lifecycle scenarios without production test hooks for individual commands.
L: pass | Tests use independent hex headers, controlled clocks and explicit latches with bounded waits; all owned notifications/connections are closed. Existing assertions remain, with queue refusal corrected to SPI FULL through a real red. Shared FrameTransportContract still runs against the extracted fake and real TCP adapter.
I: pass | Uses package-level composition only where timing must be controlled; each anonymous observer serves one cleanup race and is reviewed separately.
D: pass | Test-only JUnit, JDK coordination, library APIs and the SPI fixture; it introduces no production dependency or claimed independent provider interoperability.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@105:49

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@105:49
sha256: 36aa1e374b7c125aadd7a74824e18d3d608f9c3a9583f2a01defc18b734cc638
responsibility: Gates one accepted write guard while a competing close callback runs.
consumers: The same fake termination race passes this WriteObserver to the frame port.
S: pass | Only accepted-write callback retention drives this observer fixture.
O: pass | WriteObserver provides the actual substitution boundary for explicit timing without changing endpoint behavior.
L: pass | beforeWrite signals entry and waits for the test-controlled release before returning true; terminal callbacks are intentionally unused observations. The test releases both gates in finally and awaits physical termination, validating retained ownership.
I: pass | Implements exactly the port observer contract; it does not emulate an application handler or request result.
D: pass | Uses SPI and JDK coordination only; no concrete socket or production scheduler dependency.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@85:45

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@85:45
sha256: 36aa1e374b7c125aadd7a74824e18d3d608f9c3a9583f2a01defc18b734cc638
responsibility: Gates one close callback to prove cleanup waits for the winning internal callback.
consumers: The fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement test installs this FrameListener.
S: pass | Only the close race drives this fixture; connected/frame are intentional unused lifecycle observations.
O: pass | The existing FrameListener seam supplies controlled callback timing without production test branches.
L: pass | The deliberately blocked internal callback is a fault/coordination probe, not recommended application behavior. It signals entry and waits on a future released in finally; the test proves termination cannot precede its return.
I: pass | Implements the three required listener methods; empty connected/frame bodies are correct unused observations, not unsupported operations.
D: pass | Depends on FrameListener, JDK latch/future and the narrow transport failure value; no external infrastructure.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointExamplesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointExamplesTest.java
type: kg.aidarbek.smpp.endpoint.EndpointExamplesTest
sha256: a2c107155c126108b1fc782acb4998d0f5cc0c5b2fcd1dbded239f1dcac336e2
responsibility: Executes the compiled example pair and independently inspects each example's message traffic.
consumers: JUnit invokes ExampleClient.run and ExampleServer.create against paired endpoints and RawPeer.
S: pass | The three cases test actual documented example behavior, distinct from low-level coordinator policy.
O: pass | The public example entry methods enable ephemeral loopback testing while main remains runnable; no test-only production branch is added.
L: pass | Raw fixtures assert submit command/sequence, typed acceptance ID, separate delivery ack and drain-before-unbind. Threads complete observable futures, socket reads/waits are bounded and endpoints close in finally. Genuine example server/client reds preceded implementation; all three cases pass.
I: pass | Uses public application APIs and the narrow existing raw socket fixture; no private coordinator state is required.
D: pass | Examples remain a separate compiled source set and tests use only JUnit/JDK/library APIs. No example tooling is published in the runtime artifact.
findings: none
```

### kg.aidarbek.smpp.endpoint.EndpointResourcesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointResourcesTest.java
type: kg.aidarbek.smpp.endpoint.EndpointResourcesTest
sha256: 4da1ea1f9bc84ef31e85ffac6f24b1f0e56aca9507082bba794509751cb7db27
responsibility: Verifies connection-permit and original-failure retention through bounded endpoint cleanup.
consumers: JUnit exercises EndpointResources, its Permit, coordinator I/O termination and the controlled failing transport.
S: pass | All three cases establish cleanup truthfulness: failed construction, failed transport close and failed listener cleanup.
O: pass | CompletableFuture cleanup and the extracted fake's explicit failure injection support these resource contracts without changing production timing or replacing worker implementations.
L: pass | Assertions distinguish exceptional cleanup from merely completed stages, forbid early permit reuse and inspect original cause identity. Every resource is closed in finally; current source only adopts the extracted fake name and all three cases remain green.
I: pass | Tests use only permit/termination operations needed for cleanup; no message-handler stubs are required.
D: pass | Depends on JUnit/JDK and package-level resource/SPI composition. The deliberate raw cleanup failure is test injection, not a new production exception contract.
findings: none
```

### kg.aidarbek.smpp.endpoint.ExchangeCapabilitiesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ExchangeCapabilitiesTest.java
type: kg.aidarbek.smpp.endpoint.ExchangeCapabilitiesTest
sha256: 32ef8709563d86641da53ad1015c57449d1d5715460dea43852911807018d58d
responsibility: Proves actual outgoing message fields and handler responses obey missing-advertisement restrictions.
consumers: JUnit runs both requested versions against a raw peer and typed client services.
S: pass | All assertions concern negotiated send capability enforcement before admission and after lifecycle changes.
O: pass | Parameterized requested version and independently constructed messages exercise policy variation without modifying codec/session rules.
L: pass | COMMON cannot conceal actual TLVs or 5.0 registered_delivery flags; failed validation consumes no sequence, valid TLV-free data receives sequence two, and response TLVs become a negative. A saved sender fails after unbind. Bounded raw I/O and finally cleanup retain fixture ownership.
I: pass | Only data-message capability and its handler are registered, demonstrating optional services without submission/delivery stubs.
D: pass | Depends on public endpoint/protocol/request/session values and a test-only raw socket peer; it does not import mutable production internals to derive expected permissions.
findings: none
```

### kg.aidarbek.smpp.endpoint.ExchangeConnectionTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ExchangeConnectionTest.java
type: kg.aidarbek.smpp.endpoint.ExchangeConnectionTest
sha256: b196b431908541b536406e753bca72005a5dd7e5089d1a9d3d8a0a6947230695
responsibility: Controls arrival, handler, reply and transport-admission boundaries for one message connection.
consumers: JUnit runs ten current cases with EndpointConnection, shared notifications, HandlerDispatcher and FakeFrameTransport.
S: pass | The coherent subject is incoming-message ownership under deadline/order/output pressure; low-level generic fixture defects found by these scenarios are fixed in the shared fake.
O: pass | Injected clock and explicitly deferred/inline port behavior provide meaningful variation without sleeps or socket-specific coordinator code.
L: pass | Tests retain ordered slots through queued and active output, preserve original FULL-retry deadline, reject expired queued invocation, account owner wait from frame arrival, bound control reserves and drain 6000 replies iteratively. Gates are released and workers closed in finally; intended reds preceded fixes, while added active-write coverage was honest characterization.
I: pass | Package helpers supply only the deterministic seams needed for races; the same public sender/control facade admits outgoing traffic to fill real fixture capacity.
D: pass | Depends on the transport port fake and JDK/JUnit coordination, with independent raw headers; production coordinator remains free of concrete adapters and tests make no load-performance claim.
findings: none
```

### kg.aidarbek.smpp.endpoint.ExchangeFailureTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ExchangeFailureTest.java
type: kg.aidarbek.smpp.endpoint.ExchangeFailureTest
sha256: edcf853af84e65c7c7cbf0c653f671c90b4888591c9e224543db3f17ff8c68ca
responsibility: Verifies negative application policy, prompt controls and bounded physical retention over real TCP.
consumers: JUnit runs twenty cases against a configured server and RawPeer.
S: pass | Absent/failed/invalid decisions, blocked application work and shutdown are all failure-side message ownership contracts.
O: pass | Eight behavior cases across two profiles plus explicit stage/invocation gates exercise failure variation through public handlers; the network owner is unchanged.
L: pass | Raw response command/status/body length distinguish explicit rejection from system error. Enquiry/unbind progress with blocked work, immutable remainingHandlers snapshots and five reconnects prove global retention after a socket closes. Finally releases application gates and endpoint cleanup remains bounded.
I: pass | Only submission handlers are installed; absent registration is a first-class tested negative, not a fake successful service.
D: pass | Uses public endpoint values, JDK futures and raw peer fixtures. No test depends on production scheduler queue layout or makes independent-provider claims.
findings: none
```

### kg.aidarbek.smpp.endpoint.ExchangeMatrixTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ExchangeMatrixTest.java
type: kg.aidarbek.smpp.endpoint.ExchangeMatrixTest
sha256: 095a78b41b4c0a6e9f70ec2a0e1fb75064490f0f66bd9b26e33b6b7a470bda93
responsibility: Exercises live submission/delivery/data capabilities and application status across the profile/role/mode matrix.
consumers: JUnit runs twelve parameter combinations using real SmppClient/SmppServer and shared immutable message fixtures.
S: pass | The class owns end-to-end capability and typed-response evidence; simple reusable data factories serve neighboring exchange/example tests.
O: pass | Independent version/mode/rejection arguments cover the matrix without per-operation network adapters. Both endpoints use the same public registration surface.
L: pass | Assertions encode the exact specification differences directly rather than reading SessionPermissions. Each permitted direction sends real traffic; TRX equal sequence two values coexist, raw data TLV bytes survive and unknown nonzero statuses remain typed. Finally checks complete cleanup of both endpoints.
I: pass | Uses only present Optional sender capabilities and handler methods for the three implemented pairs; empty capabilities are asserted for forbidden roles/modes.
D: pass | Consumes public endpoint/protocol API and JDK/JUnit. This same-library loopback is explicitly not independent-provider interoperability evidence.
findings: none
```

### kg.aidarbek.smpp.endpoint.ExchangeSessionTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ExchangeSessionTest.java
type: kg.aidarbek.smpp.endpoint.ExchangeSessionTest
sha256: a97371de3bb1ea5d4d40f7ca356e617320569bf3662f78aff2a31e27f7df45e0
responsibility: Establishes raw message response, ordering, byte-budget and original-request/correlation contracts.
consumers: JUnit executes eight cases against real endpoints and raw peers; reusable local helpers create bounded server and message fixtures.
S: pass | All scenarios concern observable message exchange outcomes rather than socket adapter mechanics or text interpretation.
O: pass | Typed handler decisions and raw peer frames vary status, completion order and invalid contexts through existing public seams.
L: pass | Hex fixtures provide independent wire expectations; both profiles cover out-of-order replies, vendor status, generic nack, cancellation certainty and late/duplicate isolation. Transaction context checks operate in both directions. Ordered-byte tests coordinate stage registration before completion; sockets/endpoints/gates have bounded cleanup.
I: pass | Tests select only the message capabilities/handlers needed for each contract and reuse the single RequestHandle result/cancel API.
D: pass | Uses JUnit/JDK and public endpoint/protocol/codec APIs; codec construction is limited to explicit wire fixtures, not as the sole source of expected behavior.
findings: none
```

### kg.aidarbek.smpp.endpoint.ExchangeValuesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ExchangeValuesTest.java
type: kg.aidarbek.smpp.endpoint.ExchangeValuesTest
sha256: af29a9760d61b452bbeaa7f135debe5fb076233666467b9b2729a0e4f713cc8f
responsibility: Checks representative public response, capacity/deadline and registration invariants.
consumers: JUnit calls HandlerResponse, ExchangeOptions and EndpointHandlers.Builder.
S: pass | Only value/admission configuration contracts drive this test; no network or worker lifecycle is mixed in.
O: pass | Additional invalid boundary cases can be added directly to the relevant value assertion; no fixture hierarchy is needed.
L: pass | Independent uint32 and positive-duration/capacity expectations caught missing validation, and duplicate handler rejection cannot silently replace a service. No external resources or mutable shared state exist.
I: pass | Constructs only the value/registration subset needed for its assertions; no unsupported handler interface is implemented.
D: pass | Uses JUnit/JDK and public immutable endpoint/protocol values; no concrete transport or timing dependency.
findings: none
```

### kg.aidarbek.smpp.endpoint.FakeFrameTransport

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransport.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransport
sha256: 63f15da582940d86f1f46db07beb4fe15420f4a1d38670ab2a31c96be4bc0c75
responsibility: Provides a bounded controlled frame-port implementation for coordinator ownership and timing tests.
consumers: EndpointConnectionTest, ExchangeConnectionTest, EndpointResourcesTest and FakeFrameTransportTest use it; shared FrameTransportContract also runs on real TcpTransport.
S: pass | Start/owned frame delivery, deferred writes, finite output observations and cleanup all serve deterministic port substitution. Diagnostic knobs model chosen failure/guard/active-settlement boundaries only.
O: pass | Tests vary deferral, failure injection and beforeWritten coordination through one reusable port; the coordinator has no fake-specific branches.
L: pass | Clones accepted/input arrays, validates frame length and deadline, refuses pre-start/closed writes, retains separate eight-slot ordinary/control budgets and all accepted writes including dequeued unclaimed ones, and publishes protected cleanup once after winning close/accepted callbacks. Throwing guard/terminal/closed tests fixed callback escape and stranded work; the shared contract covers normal substitution. A constructor-injected cleanup cause is explicitly a fault fixture.
I: pass | Implements only FrameTransport and exposes narrow package testing controls. Unused callbacks in consumers do not become unsupported public transport methods.
D: pass | Depends on SPI and JDK queues/atomics/futures; header validation uses the existing bounded header helper. No socket or production worker pool is hidden inside the fake.
findings: none
```

### kg.aidarbek.smpp.endpoint.FakeFrameTransport.PendingWrite

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransport.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransport.PendingWrite
sha256: 63f15da582940d86f1f46db07beb4fe15420f4a1d38670ab2a31c96be4bc0c75
responsibility: Retains one cloned accepted frame, class budget, deadline and observer through a single terminal attempt.
consumers: FakeFrameTransport owns it; tests may dequeue/send it to coordinate guard and settlement; WriteHandle callers may cancel.
S: pass | Send/cancel/settle are the same one-shot accepted-write ownership transition.
O: pass | The observer contract permits inline callback timing or explicit deferred driving without special operation knowledge.
L: pass | Synchronization picks one winner, cancellation after settlement returns false, owned registration survives dequeue and capacity releases only after callback return. Guard failures attempt failed once; terminal callback throws never trigger a second terminal callback and are recorded as cleanup failure. New callback and dequeued-ownership regressions plus shared port tests pass.
I: pass | Public substitutable surface is only WriteHandle.cancel; package send exists for controlled test driving and does not expose frame mutation.
D: pass | Uses SPI observer/failure and the fake owner; no session policy, application callback or real I/O.
findings: none
```

### kg.aidarbek.smpp.endpoint.FakeFrameTransportTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest
sha256: ddc194f6448522ea57252b59ebaaad2dd65d36cd28ecc5f782451d5b3799afd4
responsibility: Verifies faulty internal observers cannot escape accepted work or strand fake-port cleanup.
consumers: JUnit parameterizes guard, written, failed and closed callback failures against FakeFrameTransport.
S: pass | All four scenarios focus on accepted-write exactly-once callbacks and honest cleanup causes.
O: pass | One failure selector drives the same observer/listener contracts; no production-specific test branch is required.
L: pass | Assertions require no escaped callback exception, exact written/failed/closed counts, empty deferred work and original cause under CLEANUP_FAILED for terminal callback failure. Four genuine reds preceded correction; final cleanup uses ordinary close without swallowing failures.
I: pass | The two anonymous observers isolate only tested callback behavior and are reviewed separately; the top-level test consumes the port contract.
D: pass | Uses JUnit/JDK atomics and SPI interfaces with independent RawPeer header bytes; it changes no production transport source.
findings: none
```

### kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#observerFailuresCloseAndSettleOwnedWorkWithoutASecondTerminalCallback/<anonymous>@30:45

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#observerFailuresCloseAndSettleOwnedWorkWithoutASecondTerminalCallback/<anonymous>@30:45
sha256: ddc194f6448522ea57252b59ebaaad2dd65d36cd28ecc5f782451d5b3799afd4
responsibility: Counts the single close reason and optionally throws the selected terminal callback failure.
consumers: FakeFrameTransportTest installs it to verify original cleanup cause and accepted-work settlement.
S: pass | Only close notification fault observation drives this anonymous fixture; unrelated callbacks are intentionally empty.
O: pass | A parameter selects the closed-callback fault through FrameListener, preserving the reusable fake implementation.
L: pass | The intentional throw models a faulty internal consumer; counts and retained reason allow the test to require one notification and exceptional cleanup while two accepted writes still settle. It is not claimed as a conforming application callback.
I: pass | Implements the three required listener methods and no additional state machine or codec surface.
D: pass | Only SPI, atomic counters/reference and the test exception are captured; no network implementation.
findings: none
```

### kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#observerFailuresCloseAndSettleOwnedWorkWithoutASecondTerminalCallback/<anonymous>@44:54

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java
type: kg.aidarbek.smpp.endpoint.FakeFrameTransportTest#observerFailuresCloseAndSettleOwnedWorkWithoutASecondTerminalCallback/<anonymous>@44:54
sha256: ddc194f6448522ea57252b59ebaaad2dd65d36cd28ecc5f782451d5b3799afd4
responsibility: Counts write terminal callbacks and injects one selected guard/written/failed exception.
consumers: FakeFrameTransportTest passes it through the FrameTransport write contract.
S: pass | All state supports exactly-once terminal callback and failure-cause assertions.
O: pass | Parameterized faults exercise observer boundaries without creating multiple transport implementations.
L: pass | Intentional exceptions are adversarial inputs, not a claim of well-behaved observer substitution. Guard refusal and terminal counters independently detect duplicate callbacks; the test requires cleanup to retain the original exception and all accepted writes to finish.
I: pass | Only WriteObserver methods are implemented; no request or application acceptance concerns enter this fixture.
D: pass | Depends on SPI, JDK atomics and test-local immutable selector/cause; production has no dependency on it.
findings: none
```

### kg.aidarbek.smpp.endpoint.HandlerDispatcherTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/HandlerDispatcherTest.java
type: kg.aidarbek.smpp.endpoint.HandlerDispatcherTest
sha256: 5eb2e32acabf4ce97ff73aebc66fff2565d4223a28e65a021c61b6bbbb49f0f7
responsibility: Coordinates physical handler retention and per-session invocation ordering independently of sockets.
consumers: JUnit drives HandlerDispatcher lanes/tickets with explicit futures/latches.
S: pass | Both tests focus on the execution ownership contract: retained active stages, removable queued work, ordered invocation starts and cross-session progress.
O: pass | Injected suppliers/stages let concurrency behavior vary without scheduler subclasses or production conditionals.
L: pass | Bounded latch waits establish blocked invocation and async overlap; outstanding counts and awaitTermination prove logical cancellation does not release physical work. Finally releases every gate/stage and closes workers. Real capacity/order reds and final green are recorded.
I: pass | Only scheduler admission/cancellation/termination API is used; no PDU codec, server or callback registry fixture is imposed.
D: pass | Depends on JUnit and JDK synchronization with the package scheduler. Tests use no sleeps or network and do not copy scheduling implementation into expected outcomes.
findings: none
```

## TDD commands and observed results

Runs took place in `/tmp/lightweight-smpp-step12`. Every Gradle command used
`--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step12'`, retained
normal caches, and redirected output to `/tmp/step12-<log name>.log`. A selector
below is fully qualified with `kg.aidarbek.smpp.endpoint.`; thus a row selecting
`ExchangeValuesTest` means the actual command:

```shell
./gradlew test --tests 'kg.aidarbek.smpp.endpoint.ExchangeValuesTest' --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step12'
```

The table records the original test location at its red run. Controlled message
cases later moved from `EndpointConnectionTest` to `ExchangeConnectionTest` with
green characterization preserved. Each numbered green log records the executed
passing correction; the final complete `test` command below reran every scenario
against the final formatted sources.

| Red selector | Red / immediate green log suffix | Observed relevant red and correction |
| --- | --- | --- |
| `ExchangeValuesTest` | `01-values-red` / `02-values-green` | Negative status was accepted; response/options/registry invariants enforced. |
| `ExchangeSessionTest` | `03-send-red` / `04-send-green` | Bound TX had no submission capability; typed single-window sender added. Green selected the full endpoint package. |
| `HandlerDispatcherTest` | `05-handler-capacity-red` / `06-handler-capacity-green` | Admitted invocation never started; bounded owned execution and retained stage capacity added. |
| `HandlerDispatcherTest` | `07-handler-order-red` / `08-handler-order-green` | Second same-session invocation started through a blocked first invocation; lanes serialize invocation starts. |
| `ExchangeSessionTest` | `09-handler-exchange-red` / `10b-handler-exchange-green` | Registered acceptance still received system-error response; typed handler decision connected to output. |
| `ExchangeSessionTest.delayedDecisionPreservesReplyOrderWhileControlTrafficProgresses` | `11-order-red` / `12-order-green` | Later message response overtook the pending first decision; receive-order replies added while enquiry bypasses. |
| `EndpointConnectionTest.handlerExpiryRepliesOnceAndRetainsTheUnfinishedApplicationCapacity` | `13-handler-expiry-red` / `14-handler-expiry-green` | No negative arrived at the controlled decision deadline; expiry now responds once and retains unfinished physical work. |
| `EndpointConnectionTest.aReplyRetainsItsOrderedSlotUntilTheTransportSettlesTheWrite` | `15b-reply-count-red` / `16-reply-count-green` | A second request kept the session bound despite the only reply slot still being owned; ownership now lasts through write settlement. |
| `ExchangeSessionTest.replyBytesIncludeCompletedDecisionsWaitingBehindAnEarlierRequest` | `17b-reply-bytes-red` / `18-reply-bytes-green` | Oversized completed tail decision returned success; retained request/fallback/encoded bytes now enforce the budget. |
| `EndpointConnectionTest.handlerDeadlineIncludesWaitingToEnterTheConnectionOwner` | `19-arrival-budget-red` / `20-arrival-budget-green` | An owner-lock wait incorrectly granted a fresh decision budget and success; arrival is captured before locking. |
| `ExchangeSessionTest.handlerTransactionDiagnosticsAreCheckedAgainstTheOriginalRequest` | `21-handler-context-red` / `22-handler-context-green` | Invalid transaction diagnostics were emitted successfully; original-request response rules now reject them. |
| `ExchangeSessionTest.peerTransactionDiagnosticsCannotSettleAnIncompatibleOriginalRequest` | `23-peer-context-red` / `24-peer-context-green` | Contextually invalid peer response settled success; connection/request now fail without a response loop. |
| `EndpointConnectionTest.anExpiredQueuedHandlerCannotStartWhenAnEarlierStageReleasesCapacity` | `25-queued-deadline-red` / `26-queued-deadline-green` | Two callbacks ran although the second was already expired; deadline is rechecked at actual invocation. |
| `EndpointConnectionTest.ordinaryTransportSaturationRetainsOneReplyAndItsOriginalWriteDeadline` plus queue-bound case | `28b-reply-write-saturation-red` / `29-reply-write-saturation-green` | Actual eight-write ordinary queue saturation closed the session; reply now retains its reservation and first write budget. Fake rejection kind was corrected to FULL. |
| `ExchangeConnectionTest.inlineWriteCompletionDrainsALargeReadyReplyBacklogWithoutRecursiveStackGrowth` | `36-inline-reply-stack-red` / `37-inline-reply-stack-green` | Only 938 of 6,001 total writes completed under inline recursion; guarded iterative flush completes all. |
| `ExchangeConnectionTest.controlReserveStillHasItsOwnFiniteBoundWhenOrdinaryRequestsFillTheirQueue` | `40-reserved-control-fixture-red` / `41-reserved-control-fixture-green` | Fake rejected control traffic when ordinary capacity filled; separate finite class reserves now match the port. |
| `ExchangeConnectionTest.theFramePortStillOwnsADequeuedWriteUntilItsGuardOrCancellationSettles` | `42-dequeued-fixture-ownership-red` / `43-dequeued-fixture-ownership-green` | Fake termination timed out after close lost a dequeued unclaimed write; all admitted handles remain explicitly owned through settlement. |
| `EndpointExamplesTest.exampleServerAcceptsASubmissionAndOriginatesAnIndependentDelivery` | `45-example-server-red` / `46-example-server-green` | Runnable server returned status 8; example now accepts with its own ID and sends a separate delivery. |
| `EndpointExamplesTest.exampleClientSendsASubmissionAndAcknowledgesDeliveryBeforeGracefulUnbind` | `47-example-client-red` / `48-example-client-green` | Runnable client sent enquiry opcode 21 where submission opcode 4 was required; it now submits and drains its delivery acknowledgement before unbind. |
| `FakeFrameTransportTest` | `51-fake-callback-cleanup-red` / `52-fake-callback-cleanup-green` | All four throwing-callback cases escaped accepted write/close; cleanup now attempts accepted terminal callbacks once and retains terminal callback failure. |

The broader immediate green commands used these actual selections, with the same
Gradle flags above: `04`/`30` selected `test --tests
'kg.aidarbek.smpp.endpoint.*'`; `12` selected `ExchangeSessionTest` and
`HandlerDispatcherTest`; `14` selected `EndpointConnectionTest`,
`ExchangeSessionTest`, `HandlerDispatcherTest`; `16`/`18` selected the first two
of those classes; `29` additionally selected `ExchangeMatrixTest`;
`37` selected `ExchangeConnectionTest`, `EndpointConnectionTest`,
`ExchangeMatrixTest`, `ExchangeFailureTest`; `41`/`43`/`44` selected
`EndpointConnectionTest` and `ExchangeConnectionTest`; `46`/`48` selected
`EndpointExamplesTest`; `52` selected `FakeFrameTransportTest`,
`EndpointConnectionTest`, `ExchangeConnectionTest`, `EndpointResourcesTest`.
Each class selection was supplied with its own fully qualified `--tests` option.
The remaining focused green commands reused their selected test or the already
established relevant endpoint test group; the log result is the execution
record, and no cached result is described as a newly observed red.

Honest characterization/refactor evidence:

- `27-profile-mode-characterization`: twelve real version/mode/rejection cases
  already passed; no artificial failing run was created.
- `30-catalogue-bind-refactor`: catalogue lookup, paired validators and extracted
  existing bind entry were behavior-preserving under the endpoint suite.
- `31-failure-cleanup-characterization` and `32-negotiated-fields-characterization`:
  failure/blocked-shutdown/graceful-drain and missing-advertisement field cases
  were already green through the established boundaries.
- `33`/`34b`: extracted the shared fake and controlled exchange test class, then
  verified the existing endpoint behavior. `34` was a missing renamed fixture
  reference, not a behavioral red. `34b` exposed Javadoc warnings, subsequently
  corrected before the final core snapshot.
- `44-active-reply-characterization` extended count retention to an active write
  paused before its terminal callback and was already green.
- `49-message-correlation-characterization` and
  `50-global-handler-characterization` added both-profile correlation and
  repeated-connect physical-capacity evidence without production changes.
- `10` was an initializer compilation mistake and is not TDD evidence. `15` had
  cleanup masking the intended assertion; `15b` is the proper behavioral red.
  `17b` improved completion-registration coordination while retaining the same
  failing byte-budget assertion. `28` injected FULL; `28b` strengthened it to
  actual finite-queue saturation before the correction.

No sleeps drive policy tests. Socket tests use bounded reads/waits; concurrency
cases use explicit gates and clocks. Specification expectations reuse the exact
3.4/5.0 operation table and wire body decisions already documented in
[PROTOCOL.md](../PROTOCOL.md) and [MESSAGES.md](../MESSAGES.md), backed by the
original full specification copies used during development. Raw expected byte
frames are independently constructed, rather than solely round-tripping the
library encoder.

## Final verification and limits

Actual final-source commands before review completion:

```shell
./gradlew spotlessApply --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step12'
./gradlew solidReviewInventory test javadoc --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step12'
```

`53-final-format` applied formatting; `54-final-inventory-tests` freshly compiled
changed examples/tests, inventoried types and executed **559 library/architecture
cases**, with zero failures/errors. These include **138 endpoint cases** and
**12 existing architecture cases**. Javadoc reused the matching up-to-date output
from the fresh warning-free core run `39-core-validation`; no production source
changed afterward. All 19 production SHA-256 checks matched the supplied core
snapshot. The review covers all 48 changed/current identities; all other current
source is the baseline and uses its existing review records.

The 61 additional cases relative to the 498-case Step 11 snapshot are 59 cases in
new exchange/dispatcher/fake test classes and two additional example cases. The exact
current endpoint counts are: ExchangeValues 1, ExchangeSession 8,
ExchangeCapabilities 2, ExchangeMatrix 12, ExchangeConnection 10,
ExchangeFailure 20, HandlerDispatcher 2, FakeFrameTransportTest 4,
EndpointConnection 16, EndpointExamples 3, EndpointResources 3,
EndpointAdversarial 35, SmppEndpoints 16, EndpointPdus 2, EndpointValues 2,
AuthenticationDispatcher 2. Counts describe discovered executed parameter cases,
not distinct Java methods or a performance measurement.

The existing nonempty architecture rules scan the final endpoint package and
keep EndpointConnection plus its members on SPI, allowing only SocketAddress
metadata from java.net. No new architecture rule or independent violation probe
was added in this step. Historical Step 11 probes remain documented in
[the architecture report](0010-endpoint-architecture.md).

A separate fresh JVM smoke (`55-example-smoke`) launched:

```shell
java -cp build/classes/java/main:build/classes/java/examples kg.aidarbek.examples.ExampleServer 0 3
java -cp build/classes/java/main:build/classes/java/examples kg.aidarbek.examples.ExampleClient 38085
```

The server's allocated loopback port was 38085. Both processes exited zero after
the client submission/independent-delivery/enquiry/drain and finite server cleanup.
This was a functional run, not a cached test result or a load measurement.

The common docs and [EXCHANGE.md](../EXCHANGE.md) distinguish protocol acceptance,
independent delivery and later receipt/handset state. Full 5.0 completion,
TLS/reconnect, heavy-load qualification and pinned independent interoperability
remain Steps 16–19. Same-library loopback and raw byte peers do not establish
independent-provider interoperability. Uncooperative application code can outlive
a shutdown bound, but remains counted under the same global handler capacity;
the immutable termination report is explicit about that limit.

Final `check build` with the same console/worker flags passed in
`56-check-build`: formatting verification and SOLID coverage executed, the full
559-case test output remained up to date from the fresh final-source run, and
60 unchanged review-tool cases were restored from the matching cache. All 228
current type identities have fresh matching review coverage. Binary, source and
Javadoc archives were produced without changes to their build configuration.
Local Markdown links in the ten changed/new documentation files were checked
and resolved. Common status documents mark only Step 12 complete in this patch;
subsequent requested steps remain separate work and commits.

The final status/report revision also passed `check build dependencies
--configuration runtimeClasspath` with the same flags (`57-final-check-runtime`);
`runtimeClasspath` reported no dependencies. The 559-case suite and 60 review-tool
cases were up to date, and revised SOLID evidence was checked again. A final
archive/document inspection (`58-docs-archives`) resolved all 240 local links in
the ten changed/new Markdown files. Binary/source/Javadoc JARs each contain the
exact repository LICENSE and NOTICE and exclude examples, tests and review tooling.

Integration into the main checkout passed `./gradlew check build
solidReviewInventory --console=plain` (`/tmp/lightweight-smpp-step12-integration.log`).
The 559-case suite and matching compiled/Javadoc outputs were restored from the
shared cache; the 60-case review-tool output was up to date. Inventory, formatting
and coverage checks executed. An independent XML/source-hash/archive audit
reconciled all 228 identities, the three production-only licensed JARs, and all
334 local links across the 39 current Markdown documents.
