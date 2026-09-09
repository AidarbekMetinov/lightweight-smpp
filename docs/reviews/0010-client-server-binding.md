# Review: real client/server binding

## Scope

Starting revision: `210359e`, isolated Step 11 worktree. This change composes the
final Step 9 request window and Step 10 frame transport into real client/server
bind and control sessions. It owns 26 Java files containing 35 current type
identities: 15 production files/20 types, two runnable example types and nine
endpoint test/fixture files/13 types. Every owned identity and final whole-file
SHA-256 appears below. No pre-existing production type was changed.

The final copied prerequisites, shared FrameTransportContract and root-owned
ArchitectureTest/report are excluded from this implementation's patch and review
ownership. Their owner-written current evidence remains available to isolated
verification. `build.gradle` example wiring is a separate root-reviewed patch.
No commit, root checkout edit, cache reset or Step 12 positive messaging API was
performed by this worker.

## Contracts and findings corrected

The connection coordinator composes existing SessionStateMachine, VersionNegotiation,
RequestWindow and FrameTransport boundaries. Its actual control facade has no
unsupported-method stubs. Raw requested/advertised/effective versions stay distinct;
message-direction and unavailable-service decisions follow the existing policy,
with independently encoded real peer fixtures across both profiles and all modes.

The completed review corrected premature lifecycle acceptance of an unsent queued
response, bind write/disconnect failure certainty loss, timeout restart while
waiting for connection ownership, bind deadline outcome replacement, direct
throwing authentication, invalid shutdown mutation, premature unbind during
graceful drain, loss of exceptional physical cleanup, and early permit release
when connection construction failed after allocating transport. The fake's shared
contract review corrected pre-start admission, close-callback settlement ordering
and queue overflow retaining an unaccepted write. Each correction has actual
sequential red/green evidence listed below; final findings are none.

Shared endpoint notification workers retain capacity until application dependents
physically return. Authentication similarly retains active invocation/stage slots
after logical timeout. Tests show sockets still close and bounded termination
reports remaining work; supplied executors remain caller-owned. Cleanup failures
retain original causes and resource counts instead of claiming success.

## Current type reviews

## Type: kg.aidarbek.examples.ExampleClient

```solid-review
source: src/examples/java/kg/aidarbek/examples/ExampleClient.java
type: kg.aidarbek.examples.ExampleClient
sha256: ebdaebb4aed7af1edd8da9be2ce7eb533a7a36f380380f84ad7dc54a192097f7
responsibility: Demonstrate one actual bind/enquiry/unbind exchange with finite application waits and cleanup checks.
consumers: The runExampleClient JavaExec task, direct Java command and EndpointExamplesTest execute the same public static example API.
S: pass | Only the runnable client demonstration drives this class; no library protocol behavior or reusable application framework is implemented.
O: pass | The target address is an explicit input. Production connection behavior varies through the public library rather than an example-specific transport or tracker.
L: pass | Final utility has no custom subtype obligations. It owns and closes its client, bounds all waits, checks wire statuses and physical cleanup, and is tested against the compiled example server. Literal credentials are documented demo-only and never logged.
I: pass | Users need main or run only; the example does not add public production messaging capabilities.
D: pass | Depends solely on public endpoint/protocol/JDK APIs and lives outside the published smpp production source set.
findings: none
```

## Type: kg.aidarbek.examples.ExampleServer

```solid-review
source: src/examples/java/kg/aidarbek/examples/ExampleServer.java
type: kg.aidarbek.examples.ExampleServer
sha256: cbec1e709dd7164be02ef65ff34ba7c33e14f1c96830f272873faffc00a4eda5
responsibility: Demonstrate a finite loopback listener using explicit version policy and a focused credential callback.
consumers: The runExampleServer task/direct Java command run main; EndpointExamplesTest creates the same configured server with a bound observer.
S: pass | Only demo wiring and a finite runnable lifetime drive this class; protocol/session logic stays in the library.
O: pass | Port, lifetime and observation are inputs. Authentication is the same public callback boundary shown to applications, without adding a production extension solely for the example.
L: pass | Final utility owns its main-created server and closes it in finally. Lifetime is bounded to 1..3600 seconds; cleanup is checked. The factory returns explicit ownership of an unstarted server. The compiled example test binds on an ephemeral loopback port.
I: pass | Factory and main provide the two real example consumers; no unrelated simulated message handlers are required.
D: pass | Uses public endpoint/profile/JDK APIs only and is excluded from published library, sources and Javadoc artifacts.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.AuthenticationDispatcher

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/AuthenticationDispatcher.java
type: kg.aidarbek.smpp.endpoint.AuthenticationDispatcher
sha256: 9d30c9f41d3a18da642c01bd089803c0db6c0af221ed85f57a3cbd1667f718df
responsibility: Own finite execution of application authentication, including physical invocation and returned-stage lifetime.
consumers: SmppServer creates it; EndpointConnection submits tickets and EndpointResources observes owned worker cleanup; dispatcher and real-peer tests exercise cancellation and overload.
S: pass | Only authentication scheduling, capacity retention and executor ownership drive this type; protocol version decisions and wire replies belong to its caller.
O: pass | The actual variation is BindAuthenticator plus optional supplied Executor. Inline/rejecting executors use the same bounded owned dispatcher instead of changing network orchestration.
L: pass | AutoCloseable close is idempotent and nonblocking. Fixed daemon workers and finite queue retain active capacity after logical cancellation; supplied executors remain open. Tests coordinate incomplete stages, queued cancellation, rejection and physical shutdown without sleeps.
I: pass | Session orchestration needs submit/cancel; endpoint cleanup needs counts and bounded termination. Neither consumer must implement message handling or arbitrary executor management.
D: pass | Depends on the focused application callback, immutable protocol values and JDK executor mechanisms. No socket, codec or state machine is constructed.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.AuthenticationDispatcher.Ticket

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/AuthenticationDispatcher.java
type: kg.aidarbek.smpp.endpoint.AuthenticationDispatcher.Ticket
sha256: 9d30c9f41d3a18da642c01bd089803c0db6c0af221ed85f57a3cbd1667f718df
responsibility: Represent exactly one bounded authentication invocation and its cancellation/physical-completion ownership.
consumers: The dispatcher executor calls run; EndpointConnection holds cancellation; queued removal and shutdown share the same ticket.
S: pass | Terminal notification suppression and exactly-once capacity release are the one work-item lifecycle responsibility.
O: pass | Authentication policy varies through the captured callback and supplied executor; this work item contains no credential policy branches.
L: pass | Runnable execution releases once in finally; cancellation can remove queued work but cannot falsely release an active invocation or unfinished stage. Runtime failures, direct Errors, null stages and null decisions become internal failure notifications; late success is suppressed.
I: pass | The owned executor needs only Runnable; the connection needs cancel. No unsupported future mutation or executor shutdown method is exposed to the application.
D: pass | Uses its dispatcher ownership, BindAuthenticator, a narrow internal BiConsumer and JDK stages/atomics; version and wire policy remain outside the task.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.BindAuthenticator

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/BindAuthenticator.java
type: kg.aidarbek.smpp.endpoint.BindAuthenticator
sha256: 738a23a85e9a6a8850d7804098e4672a01b661ea7be802747f1066f3e040b131
responsibility: Define the application credential-decision boundary independently of protocol capabilities.
consumers: Applications implement one async method; AuthenticationDispatcher invokes it with immutable BindRequest and remote address metadata.
S: pass | Only application authentication requirements drive this callback; bind state, version negotiation and transport progress are not its responsibilities.
O: pass | New authentication providers implement the callback and return a stage without editing endpoint or wire-codec loops.
L: pass | The documented contract requires a non-null asynchronous decision. The adapter contains invalid null/throwing implementations with negative bind replies, and tests cover both compliant asynchronous and deliberately failing callbacks.
I: pass | One method matches the authentication consumer; applications are not forced to implement submission, delivery, listener or timer hooks.
D: pass | Uses CompletionStage, SocketAddress metadata and an immutable protocol value, without depending on a concrete transport or executor.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.BindDecision

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/BindDecision.java
type: kg.aidarbek.smpp.endpoint.BindDecision
sha256: 05968d5fa224ac23c909eb6a91ccafb75109e72af9126625bdcbebbab495120e
responsibility: Preserve one unsigned SMPP authentication response status.
consumers: Authenticators return decisions; EndpointConnection maps success or exact negative status to bind responses; values and raw-peer tests verify status handling.
S: pass | Only the unsigned status invariant drives this immutable record.
O: pass | Unknown but representable statuses remain raw values; extending application authentication does not require changing an enum or codec catalogue.
L: pass | Record equality/hashCode and immutable long storage remain standard; constructor rejects values outside uint32, and ACCEPT is a valid zero decision.
I: pass | The caller needs only commandStatus; no credential parser or capability-grant method is bundled with it.
D: pass | Primitive value validation has no infrastructure dependency.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.BoundSession

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/BoundSession.java
type: kg.aidarbek.smpp.endpoint.BoundSession
sha256: 673241f35d3eef4eb0df2a54f451c422e9515c90da600bc49e33262c04d9d786
responsibility: Expose implemented bound-connection controls and immutable/current session observations.
consumers: Client connection results and server bound listeners receive the facade; applications use enquiry, unbind, cancellation handles and termination.
S: pass | All methods serve this connection capability and delegate lifecycle/request ownership to EndpointConnection; no message business service is implemented here.
O: pass | Request policy varies through RequestOptions and endpoint options. Future messaging capability belongs in an explicit implemented facade rather than unsupported sender methods here.
L: pass | Final facade retains Object identity. Thread-safe state access and serialized admission are delegated; request budgets start before monitor acquisition, close is idempotent, and protected termination observes physical cleanup. Tests cover both initiators, all modes/profiles and cancellation.
I: pass | RX/TX/TRX all support the exposed control subset; no role must implement a meaningless submission or delivery handler. Null explicit options are documented as the default.
D: pass | Depends only on the internal coordinator and public request/protocol/session values; it never chooses a TCP adapter or executor.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.ClientConfig

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ClientConfig.java
type: kg.aidarbek.smpp.endpoint.ClientConfig
sha256: c5acdce7728839b7c5c7451f65d6f760b9d0b0f01d63c737aed0efbcfe4f0189
responsibility: Keep a resolved target and explicit outgoing bind/version requirement as an immutable value.
consumers: SmppClient preflights and connects from this record; EndpointConnection creates client version policy; values and real advertisement-table tests consume it.
S: pass | Only one connection attempt configuration drives validation and requestedVersion conversion.
O: pass | Bind modes and supported version choices are data inputs; server advertisement policy is independently computed later and does not alter this value.
L: pass | Record equality remains value based, referenced BindRequest is immutable and hides credentials in its diagnostic representation. Nulls, unresolved addresses, zero port and unsupported requested octets fail before resource admission.
I: pass | Client callers supply only target, bind and strictness; server acceptance, authentication and handlers are absent.
D: pass | Depends on JDK address metadata and existing immutable protocol/profile types. DNS and sockets are not created by validation.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.ConnectionAttempt

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ConnectionAttempt.java
type: kg.aidarbek.smpp.endpoint.ConnectionAttempt
sha256: 7a0984245d883332ec118d1db496b852e1f7c831d8bb776c16748eb1a3bb97ed
responsibility: Provide cancellation ownership while one connect/bind workflow has no ready BoundSession.
consumers: SmppClient.connect delegates to its result; early-cancellation callers use cancel and later use BoundSession.close.
S: pass | Contains only protected observation and the corresponding cancellation capability for the same workflow.
O: pass | Uses the existing coordinator cancellation action and CompletionStage; it introduces no second request engine, scheduler or replay variation.
L: pass | Final Object identity is appropriate for a live capability. Result stages protect internal completion; cancel returns true only for a winning connecting/binding cancellation and false after readiness. Tests cover before-start, blocked-authentication, repeat and post-readiness cancellation.
I: pass | Callers need just result/cancel before binding; they are not given transport write or internal future mutation access.
D: pass | Depends on CompletionStage and BooleanSupplier abstractions plus the eventual facade, with no concrete transport or worker ownership.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointConnection

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection
sha256: 58390aaf4915aefd5832cfc34a5e103da9ece761f09cf74bc73e43fb65056962
responsibility: Serialize one connection lifecycle while composing protocol policy, wire codecs, request correlation and the transport port.
consumers: SmppClient/SmppServer construct it; BoundSession exposes controls, EndpointResources ticks/closes it, FrameTransport invokes internal events, and AuthenticationDispatcher returns internal decisions.
S: pass | Its change driver is connection orchestration. Byte encoding, permission tables, version decisions, general request tracking, executor bounds and TCP construction are delegated to focused collaborators rather than reimplemented.
O: pass | FrameTransport is the actual I/O variation boundary; authentication is supplied separately. Command catalogue and version differences remain in existing codecs/session policies. The implemented control set is explicit and has no messaging stubs.
L: pass | Synchronized lifecycle mutation and RequestWindow terminal outcomes prevent cancellation/late-response resurrection. A response requires a claimed write guard. The same-sequence opposite namespace, crossed unbinds, graceful drain, pending reply flush, deadline precedence, failed write certainty and physical cleanup are tested with both real TCP and the shared-contract fake.
I: pass | FrameListener provides only internal connected/frame/closed events; the application receives BoundSession. ResponseContext is built from the actual pending request, and common field requirements are explicit for the exposed TLV-free controls.
D: pass | All network operations go through FrameTransport/WriteObserver, with SocketAddress used only as metadata. Root architecture probes rejected EndpointConnection to TcpTransport and EndpointOptions to java.io.File dependencies; codecs/profile/protocol/session/request collaborators are existing lower-layer boundaries.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite
sha256: 58390aaf4915aefd5832cfc34a5e103da9ece761f09cf74bc73e43fb65056962
responsibility: Track one accepted protocol reply through transport completion and possible lifecycle closure.
consumers: EndpointConnection.writeReply creates it; FrameTransport calls its guard and terminal write outcome.
S: pass | Only internal reply completion, pending-reply ownership and close-after-flush behavior drive this observer.
O: pass | Caller supplies the internal post-write action and close policy; this observer needs no command-specific authentication or version branches.
L: pass | WriteObserver guard refuses after closure. Successful completion decrements the accepted reply count before notifying internal readiness or closing; failure closes the connection. Crossed-unbind and bind-rejection raw/fake tests verify reply-before-close ordering.
I: pass | The transport consumes only WriteObserver; application work is dispatched through bounded notifications by the coordinator action.
D: pass | Depends on the SPI observer contract and its connection owner, without calling a concrete TCP writer or executor.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite
sha256: 58390aaf4915aefd5832cfc34a5e103da9ece761f09cf74bc73e43fb65056962
responsibility: Bridge transport write ownership to the existing request handle certainty and terminal failure.
consumers: EndpointConnection writes bind/enquiry/unbind requests with this observer; FrameTransport invokes beforeWrite/written/failed.
S: pass | Only request transmission and write-failure settlement drive this bridge; response matching and scheduling are delegated.
O: pass | Any FrameTransport honoring the shared port can invoke it; all request kinds use the same RequestWindow path.
L: pass | The guard atomically calls beginWrite and can refuse cancelled/expired requests. Failure settles once through the window; lifecycle failure publishes the winning RequestFailure with cause and NOT_SENT/MAY_HAVE_BEEN_SENT certainty before close. Successful write alone does not complete a request.
I: pass | The three observer methods satisfy the port; written intentionally needs no action because only a response/terminal request event completes the request.
D: pass | Depends on RequestWindow/RequestHandle and SPI TransportFailure, not concrete transport or application callbacks.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointException

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointException.java
type: kg.aidarbek.smpp.endpoint.EndpointException
sha256: a486cfbe36dfcd31b746e6d4553b485ea2a6b429e5db3bcb590331c37f709ae4
responsibility: Represent endpoint admission/bind/version/protocol outcomes with credential-free diagnostic metadata.
consumers: ConnectionAttempt results and endpoint admission callers inspect reason, generation, raw peer status and raw advertisement.
S: pass | Only endpoint-level failure identity and available raw diagnostic fields drive this exception; transmission certainty remains RequestFailure-owned.
O: pass | Unknown wire status and advertisement values remain raw fields instead of requiring a new endpoint exception subtype for every peer value.
L: pass | RuntimeException serialization uses stable primitive/enum/UUID fields and serialVersionUID; absent raw values are exposed as empty optionals. Real peer tests preserve rejected status and unsupported/missing advertisement without credential data.
I: pass | Callers read only the relevant metadata; there are no transport controls or authentication requests attached to an exception.
D: pass | Depends on JDK exception/value APIs and its nested reason vocabulary. Transport causes remain separate structured failures rather than a concrete adapter dependency.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointException.Reason

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointException.java
type: kg.aidarbek.smpp.endpoint.EndpointException.Reason
sha256: a486cfbe36dfcd31b746e6d4553b485ea2a6b429e5db3bcb590331c37f709ae4
responsibility: Name the finite local endpoint and bind-failure categories.
consumers: EndpointConnection and EndpointResources create categorized errors; callers and tests compare stable enum identities.
S: pass | Contains only endpoint outcome vocabulary with no lifecycle mutation or wire parsing.
O: pass | Raw peer statuses remain separate data, so vendor statuses do not force enum growth.
L: pass | Standard immutable Enum identity/equality is retained; each declared category corresponds to a real admission, cancellation, timeout, peer/version or protocol path.
I: pass | Consumers require only category identity; no unrelated failure-resolution interface is imposed.
D: pass | Uses JDK enum support and is scoped with the corresponding exception; no infrastructure is referenced.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointOptions

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointOptions.java
type: kg.aidarbek.smpp.endpoint.EndpointOptions
sha256: 20feca3f131e4ae8f3e3367c48c205d770315b39acf4ffb2d31d114198e5e942
responsibility: Store finite endpoint-wide and per-session resource/deadline limits.
consumers: Client/server composition, request windows, transport configuration and endpoint resources consume their relevant fields; value and deadline/overload tests verify policy.
S: pass | The configuration groups the endpoint owner's resource policy; it contains no scheduling or protocol state mutation.
O: pass | Existing bounds and default deadlines vary as immutable inputs. Separate ServerConfig owns authentication acceptance/concurrency rather than inventing transport policy subclasses.
L: pass | Record equality and immutable Duration/PduLimits references remain value based. Positive capacities/deadlines, nonnegative extra notification capacity, null limits and monotonic-range overflow are checked; shutdown permits zero grace separately before mutation.
I: pass | Consumers use only the limits they implement. Notification capacity is honestly documented as worker count plus additional reservations, including promised work.
D: pass | Depends on JDK values and codec allocation limits; no executor, socket or environment lookup occurs in the record.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointPdus

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointPdus.java
type: kg.aidarbek.smpp.endpoint.EndpointPdus
sha256: bfbb4985ca26b9a51b22cbf4d812f006d76bb0dfe20f25d1886e2d6b76ec074a
responsibility: Compose existing bounded codecs and explicit negative-response wire shapes for endpoint orchestration.
consumers: EndpointConnection encodes/decodes frames; SmppClient preflights bind bytes; raw hex and both-direction message rejection tests verify output.
S: pass | Only endpoint wire composition and negative response construction drive this helper; it owns no permissions, authentication or request correlation.
O: pass | Codec implementations and profile validation remain in the registry layers. Known paired negative shapes are selected here for the implemented catalogue; unknown commands use generic_nack without a speculative positive service.
L: pass | Owns no mutable caller buffers; codecs enforce bounds and preserve raw advertisements. Independent sixteen-byte fixtures cover paired negatives, unknown commands and invalid-sequence nack zero; real matrix tests verify original request direction for data_sm responses.
I: pass | The coordinator needs only bounded encode/decode/header/negative operations and the shared empty optional parameters value.
D: pass | Depends inward on codec/protocol/profile and endpoint-role values, without transport, executor or callback dependencies.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointResources

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointResources.java
type: kg.aidarbek.smpp.endpoint.EndpointResources
sha256: 2c271107bcafb578a2e5d856e9f97f5dca1f467feeb8b2e1638383c7b3267c1b
responsibility: Own global endpoint admission, notification capacity, one deadline scan and bounded cleanup observation.
consumers: Client/server composition reserve connection permits and register the listener; sessions are ticked/closed; applications observe immutable termination reports.
S: pass | All methods serve the endpoint resource owner. Connection protocol behavior is delegated, while authentication work lifetime and request completion execution remain in dedicated bounded collaborators.
O: pass | Options vary limits; listener cleanup is supplied as close action plus CompletionStage. One shared BoundedNotifications instance handles all sessions, avoiding per-connection stuck-worker accumulation.
L: pass | ReentrantLock/Condition protects the reservation registry. Shutdown validates before mutation, gracefully drains or aborts, and publishes one protected snapshot after cleanup/deadline. Failed transport/listener cleanup retains counts/causes. Blocking authentication/notifications remain globally counted; tests prove network cleanup proceeds.
I: pass | Composition needs reserve/listener/shutdown, public facades need counts/snapshots, and the cleanup thread needs termination observations. No application callback or transport-write interface is exposed.
D: pass | Uses the existing bounded request notification abstraction, focused auth dispatcher, connection coordinator and JDK scheduling. It does not choose a TCP adapter or duplicate general request tracking.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointResources.Permit

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointResources.java
type: kg.aidarbek.smpp.endpoint.EndpointResources.Permit
sha256: 2c271107bcafb578a2e5d856e9f97f5dca1f467feeb8b2e1638383c7b3267c1b
responsibility: Own one admission reservation through attachment or failed-construction transport cleanup.
consumers: Client/server composition reserve then attach/retire; successful I/O cleanup releases; endpoint shutdown counts retained permits.
S: pass | Only reservation transfer, exactly-once release and cleanup retention drive this nested owner.
O: pass | Attachment observes a connection or an explicit cleanup stage; resource tracking does not depend on a particular transport implementation.
L: pass | Locked attached/released flags reject double consumption. Successful cleanup releases once; exceptional cleanup retains the slot and original failure. Tests cover an allocated transport whose connection construction fails and a normal attached transport whose close fails.
I: pass | Composition gets the three needed ownership operations; no public session or arbitrary capacity mutation API is added.
D: pass | Depends on its endpoint registry, EndpointConnection and CompletionStage cleanup observation; no socket is opened or concrete adapter imported.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointTermination

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointTermination.java
type: kg.aidarbek.smpp.endpoint.EndpointTermination
sha256: 013ac1bfaa21af81beb3ba9e7a1a864d000aebf535ee169c01714383bb1cdfd4
responsibility: Capture bounded endpoint cleanup results without claiming blocked application work was stopped.
consumers: Client/server shutdown observers and runnable examples inspect complete, remaining counts, worker flags and original cleanup failure references.
S: pass | Only the final cleanup snapshot invariant drives this record; it neither stops workers nor retries cleanup.
O: pass | Counts and flags represent existing owned resource categories; later cooperation can be observed operationally without mutating a previously delivered snapshot.
L: pass | Record equality uses immutable counts/flags and a copied unmodifiable failure list; exception identities are deliberately preserved, not advertised as deeply immutable. Negative counts/null entries fail. complete requires all counts zero, all workers stopped and no failures.
I: pass | Callers need a compact result and precise incomplete-work details rather than an executor/transport management interface.
D: pass | Depends on JDK List/Throwable only; no infrastructure implementation is retained in the public report.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.ServerConfig

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ServerConfig.java
type: kg.aidarbek.smpp.endpoint.ServerConfig
sha256: c3ea6652e92a681191fec30e6c9393312be8456ecda69458b22fd99533a0614f
responsibility: Preserve resolved listener addressing, explicit accepted/requested version policy and authentication bounds.
consumers: SmppServer binds and creates bounded authentication; EndpointConnection evaluates received version against the independent advertisement.
S: pass | Only server endpoint configuration drives this value; credentials are decided through BindAuthenticator and transport execution remains elsewhere.
O: pass | Accepted versions, advertised implementation and authentication limits are independent inputs. Protocol compatibility is delegated to VersionNegotiation rather than a second version table.
L: pass | Copies accepted versions and validates nulls, unresolved address, finite auth bounds, coherent version policy and wire-valid systemId. Record value semantics remain safe; toString omits systemId. Tests mutate the original set and reject inconsistent advertised policy.
I: pass | Server callers provide only server concerns; no client bind request, retry or messaging callbacks are required.
D: pass | Uses immutable JDK/profile/protocol values and the pure version policy; does not resolve DNS, open a listener or run authentication.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.SmppClient

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/SmppClient.java
type: kg.aidarbek.smpp.endpoint.SmppClient
sha256: fbb07d6e431fa31adc8dfb03cefdc734e4a7bf851a445ffadb20c4ab7c77bbaf
responsibility: Compose and own bounded outgoing ESME TCP connections and expose connect/bind workflow capabilities.
consumers: Applications and the compiled example use connect/connectAttempt, sessions, close and bounded termination; real peer and pair tests exercise the public API.
S: pass | All methods serve the outgoing endpoint owner; protocol orchestration, byte encoding and general request completion are delegated.
O: pass | ClientConfig and EndpointOptions vary target, bind and resource policy. TCP adapter selection is intentionally at this composition root; the coordinator remains SPI-based.
L: pass | Preflights before socket writes and reserves capacity before creation. Cancellation targets the same connect/bind workflow, failed construction retains allocated transport cleanup, supplied observation futures cannot complete internals, and close is idempotent. Tests cover refusal, disconnect, overload, timeout and both profiles.
I: pass | Exposes only supported control-session establishment and lifecycle observation; no empty send/delivery/retry capability is advertised.
D: pass | This is the concrete composition boundary allowed to select TcpTransport. Shared session policy and EndpointConnection do not acquire that dependency.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.SmppServer

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/SmppServer.java
type: kg.aidarbek.smpp.endpoint.SmppServer
sha256: efdc7bdeb98b85e3f1223309cdbb8d90fedced93f77797338c6c4beb72fcf4f5
responsibility: Compose one bounded message-center listener with explicit authentication and bound-session observation.
consumers: Applications/examples start the server and observe sessions/cleanup; TcpListener transfers accepted transports through the fast internal accept callback.
S: pass | All methods serve listener/session ownership. Credential decisions, connection state and detailed transport cleanup are delegated to their focused collaborators.
O: pass | Authentication is the actual application variation point; accepted/ad version and finite options are data. The optional executor is caller-owned and invoked through the bounded dispatcher.
L: pass | Start is single-use; invalid shutdown arguments are atomic. Acceptance reserves before ownership, failed construction remains tracked until cleanup, and close preserves supplied executor usability. Real tests cover finite active/auth queues, blocked work, all bind modes and successful unbind from either endpoint.
I: pass | The authenticator and optional bound observer are separate callbacks; implementing a server does not require nonexistent message service methods.
D: pass | TcpListener/TcpTransportConfig are selected only here at the composition boundary. The connection receives FrameTransport, and the pure session policy remains infrastructure-free.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.AuthenticationDispatcherTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AuthenticationDispatcherTest.java
type: kg.aidarbek.smpp.endpoint.AuthenticationDispatcherTest
sha256: 7f91d5921728da6e48f4a4327825cf7b7aa834a01c0db8e7a81da793cad7a6c5
responsibility: Verify physical authentication capacity and optional executor substitution without network fixtures.
consumers: JUnit runs two coordinated cases using actual dispatcher tickets, stages, latches and inline/rejecting Executor lambdas.
S: pass | Only scheduling/cancellation/executor ownership is tested; protocol wire decisions stay in endpoint integration tests.
O: pass | New executor behavior can be supplied through the actual Executor seam without a duplicate authentication scheduler.
L: pass | No custom test superclass; owned dispatchers close in finally. Incomplete stage capacity, queued removal, late suppression and off-caller inline execution use explicit gates and bounded waits, with no sleeps.
I: pass | Uses only dispatcher submit/cancel/count/termination contracts needed by each assertion.
D: pass | Depends on Jupiter, the production dispatcher API and JDK coordination; no private reflection or production-to-test dependency exists.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointAdversarialTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointAdversarialTest.java
type: kg.aidarbek.smpp.endpoint.EndpointAdversarialTest
sha256: 03945a23e29a58ff6b555df86520a6272927f31e6293b0dc1aaad04ed0fa4ccf
responsibility: Exercise public endpoint composition against independently encoded local TCP peers and blocked application work.
consumers: JUnit runs 35 real-peer cases: all 16 version-table combinations, auth failure modes, resource bounds, unavailable-message permissions and request cancellation/nacks.
S: pass | Only externally visible endpoint behavior drives the suite. RawPeer owns framing details, and production internals are not read to derive expected protocol decisions.
O: pass | ValueSource/MethodSource express real mode/version/auth variations; fixed raw frames and callback gates add edge cases without an alternate endpoint implementation.
L: pass | No custom supertype; all sockets/endpoints/executors have local ownership. Bounded waits coordinate actual frames, callback entry and deadline outcomes. Blocked callbacks are released in finally; reports preserve unfinished work honestly. No public network or sleeps are used.
I: pass | Uses the actual public client/server/session/config/result capabilities; message frames are negative-service probes, not unsupported sender methods.
D: pass | Depends on JDK socket fixtures, Jupiter and public library APIs. Expected wire status/command values are literal, with neither codec roundtrips nor internal permission tables as the oracle.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest
sha256: 5c63e36ec16ac3eacf50bf23a93db4420710e233edba41218a827f9eea567b5a
responsibility: Verify serial connection composition, atomic terminal events and the alternate frame-port contract.
consumers: JUnit runs 16 deterministic/coordinated scenarios with a bounded fake, shared FrameTransportContract, raw frames and injected monotonic time.
S: pass | Cases focus on the one connection owner: lifecycle/correlation/write boundaries, deadlines, authentication results and port cleanup.
O: pass | Fake transport timing and LongSupplier clock are the actual injected variations; production request-window and session-policy implementations are reused rather than replaced by mirrors.
L: pass | No inherited test contract. Every owned notifier/authenticator/connection is closed in finally; gates bound intentional races. Actual reds covered queued early responses, certainty, monitor waiting, bind expiry and fake cleanup/queue bugs. The same port contract runs on TCP in the transport owner suite.
I: pass | Helpers expose only inbound frame, observed write, deferred guard and controlled failure operations needed for scenarios, without unsupported session APIs.
D: pass | Depends on the SPI and existing window/policy/codecs plus JDK/Jupiter. Literal wire fixtures and external clock advancement provide independent observations.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@108:49

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@108:49
sha256: 5c63e36ec16ac3eacf50bf23a93db4420710e233edba41218a827f9eea567b5a
responsibility: Gate an accepted fake write before transport settlement to exercise concurrent cleanup.
consumers: The close-barrier test supplies this WriteObserver to one raw control frame.
S: pass | Only guard timing is observed; successful/failed callbacks require no extra action because the test observes transport termination.
O: pass | Gate release varies scheduling without inventing request or protocol behavior.
L: pass | The intentionally blocked internal guard is always released in finally and returns one boolean decision. The fixture supplies no throw or mutable buffer side effect; its purpose is to verify retained accepted-write ownership during close.
I: pass | Satisfies WriteObserver without an unrelated request future or handler implementation.
D: pass | Depends on SPI contracts and JDK gates, not the concrete transport or production request tracker.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@88:45

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest#fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement/<anonymous>@88:45
sha256: 5c63e36ec16ac3eacf50bf23a93db4420710e233edba41218a827f9eea567b5a
responsibility: Gate the winning fake close callback so termination ordering is directly observable.
consumers: The close-barrier test installs this FrameListener on the fake and controls its release with a future.
S: pass | Only the close-callback completion barrier drives this local observer; unused connected/frame events legitimately have no observation in this scenario.
O: pass | Release timing is supplied through the test gate; no application or protocol policy is embedded.
L: pass | The deliberately gated callback is a coordinated adversarial fixture, released in finally. It does not claim a normal nonblocking application callback; the test proves termination cannot report completion while its accepted internal close work remains unfinished.
I: pass | Implements the exact three FrameListener callbacks; only closed needs an observation for this scenario.
D: pass | Depends on SPI events and JDK coordination only, tied to the enclosing test rather than production wiring.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest.FakeTransport

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest.FakeTransport
sha256: 5c63e36ec16ac3eacf50bf23a93db4420710e233edba41218a827f9eea567b5a
responsibility: Provide a bounded deterministic immediate/deferred FrameTransport substitute with owned frame bytes and explicit cleanup.
consumers: Connection and resource tests use it; the shared FrameTransportContract supplies the same valid lifecycle/ownership assertions as TcpTransport.
S: pass | Only controllable port timing/failure and wire observation drive this fixture; it does not authenticate, parse message semantics or match requests.
O: pass | Deferred guard and injected terminal/write failures are explicit fixture modes, preserving one shared port implementation instead of per-test incomplete stubs.
L: pass | Shared contract checks pre-start/closed refusal, frame bounds/ownership, both write classes, guard refusal, immutable termination and repeated close. Additional coordinated regressions retain accepted writes and winning close callback before termination, reject full deferred admission before incrementing ownership, and cancel queued writes once.
I: pass | The production consumer sees only FrameTransport; tests separately use bounded queues to drain writes/inject frames. Immediate operation has no fictitious successful queued cancellation.
D: pass | Implements the SPI with JDK queues/stages and protocol header validation. No concrete TcpTransport or socket is constructed by the substitute.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest.FakeTransport.PendingWrite

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java
type: kg.aidarbek.smpp.endpoint.EndpointConnectionTest.FakeTransport.PendingWrite
sha256: 5c63e36ec16ac3eacf50bf23a93db4420710e233edba41218a827f9eea567b5a
responsibility: Own one accepted fake-transport write until guard execution, cancellation or failure.
consumers: FakeTransport queues or immediately sends it; test coordination drains it; callers receive only its WriteHandle cancellation capability.
S: pass | Only one write item's settlement and active-write release drive this nested fixture.
O: pass | Observer and bytes are captured inputs; request-specific lifecycle decisions remain in production RequestWrite.
L: pass | Synchronized settlement allows one winner, copies bytes before acceptance, removes queued ownership, checks deadline, invokes one terminal observer result and decrements active ownership in finally. Queue-bound and termination regressions verify no retained unaccepted write.
I: pass | WriteHandle consumers need cancel only; send is an internal fixture operation and does not expose a general transport implementation API.
D: pass | Depends on its fake owner, SPI observer/failure contracts and immutable captured bytes/deadline; no request-window or application policy is replicated.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointExamplesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointExamplesTest.java
type: kg.aidarbek.smpp.endpoint.EndpointExamplesTest
sha256: cb24a9e86781d9e6bfe8937f55991e35a0d57a877cc4272b6c466d95ab7320b2
responsibility: Ensure the documented compiled example pair performs an actual local control-session exchange.
consumers: JUnit invokes ExampleServer.create and ExampleClient.run from the dedicated examples output on the test classpath.
S: pass | Only executable example correctness drives this test; general endpoint semantics are covered in focused suites.
O: pass | Example public methods are tested directly; no production API was widened to add example-only instrumentation.
L: pass | No custom subtype contract. A latch observes the real server binding, the client checks enquiry/unbind/cleanup, and server cleanup is awaited in finally. The initial no-work client failed the actual-bound assertion before implementation.
I: pass | Uses only the minimal example entry points and existing endpoint termination result.
D: pass | Test code depends on the examples source set and public endpoint API; examples and tests remain outside the published library.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointPdusTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointPdusTest.java
type: kg.aidarbek.smpp.endpoint.EndpointPdusTest
sha256: 308161075607c7236c4164bd8e9214a83ad09ec249ecbb5f4bdf657c965fbbe3
responsibility: Verify independent byte fixtures for paired negative responses and generic-nack sequence fallback.
consumers: JUnit applies literal hexadecimal expectations to both protocol profiles through EndpointPdus.
S: pass | Only negative wire shape is asserted; lifecycle permission and real I/O are tested separately.
O: pass | New catalogue negatives can add explicit vectors without changing fake transports or deriving expected bytes from codecs.
L: pass | No custom supertype or resource. Exact sixteen-byte arrays independently check response ID/status/sequence, including high-bit original sequence producing nack zero; arrays are local.
I: pass | Uses only the negative encoding helper and explicit profile/header inputs.
D: pass | Depends on Jupiter and existing values plus HexFormat; it does not generate expectations by the encoder under test.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointResourcesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointResourcesTest.java
type: kg.aidarbek.smpp.endpoint.EndpointResourcesTest
sha256: 005b4b27105ac555361b8fe3853b151b90993db7d6fd35f7f5f91b791b88c560
responsibility: Verify endpoint admission retention and truthful cleanup failure snapshots.
consumers: JUnit runs three cases using actual resource permits, an exceptional fake transport and controlled listener/construction cleanup stages.
S: pass | Only resource ownership and failure reporting drive the suite; authentication/version/wire behavior is unnecessary.
O: pass | Cleanup CompletionStages provide the real failure/unfinished-work seam without introducing a second endpoint registry.
L: pass | No custom supertype; tests close owned resources, use finite waits, and assert original error identity, non-released capacity and incomplete cleanup. The failed-construction red exposed premature release before physical termination.
I: pass | Consumes reserve/attach/retire/close/report operations and the existing port fake; no unsupported production cleanup hooks are introduced.
D: pass | Depends on the resource boundary, shared fake and JDK stages/errors, with no reflection or concrete socket adapter needed.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.EndpointValuesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/EndpointValuesTest.java
type: kg.aidarbek.smpp.endpoint.EndpointValuesTest
sha256: ab965e2de75917572fcd72d775986f411a9bfc6143a36c5825a18df303982108
responsibility: Check unsigned authentication decisions and immutable finite endpoint configuration boundaries.
consumers: JUnit runs value cases with independent invalid capacities/versions and a mutable caller-owned accepted set.
S: pass | Only value invariants and defensive ownership are asserted; no socket or executor is created by these fixtures.
O: pass | Additional boundary values fit the same public constructors without fixture inheritance or implementation-table access.
L: pass | No custom subtype/resource contract. Tests verify uint32 extremes, invalid deadlines/capacity, unsupported requested version, unresolved addresses, copied accepted set and inconsistent advertisement rejection.
I: pass | Consumes just public record constructors/accessors and immutable protocol values.
D: pass | Uses Jupiter, public values and finite JDK collections; expected bounds do not call private validation helpers.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.RawPeer

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/RawPeer.java
type: kg.aidarbek.smpp.endpoint.RawPeer
sha256: 47a968a2df73b9e075a8429daa7614c1c3514c715d61a7e8e39b69ac858028dc
responsibility: Own one bounded local socket peer that independently reads/writes SMPP frame fixtures.
consumers: Real endpoint tests create/accept it, send raw bytes, inspect uint32 headers and observe endpoint closure.
S: pass | Only test socket ownership and raw frame exchange drive this helper; it implements no authentication, request correlation or endpoint behavior.
O: pass | Callers supply raw command/status/sequence/body inputs; fixture construction is independent of the library codec catalogue.
L: pass | AutoCloseable closes its owned Socket. Connect/accept/read operations are bounded, malformed/truncated reads fail explicitly, allocation is limited to 1 MiB, and EOF/reset observation is distinguished from read timeout. All tests own it in try-with-resources.
I: pass | Provides only operations needed by local peer scenarios; it is not advertised as a general FrameTransport substitute.
D: pass | Uses JDK sockets/ByteBuffer exclusively, without library codec or framing dependencies, keeping wire expectations independent.
findings: none
```

## Type: kg.aidarbek.smpp.endpoint.SmppEndpointsTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/SmppEndpointsTest.java
type: kg.aidarbek.smpp.endpoint.SmppEndpointsTest
sha256: 023ac36eb3ce70ad63fd3e477d6173d56dd8cf559881e93519b63fa314d17926
responsibility: Verify real client/server bind and successful control lifecycle across both endpoints and deadline transitions.
consumers: JUnit runs 16 cases, including twelve mode/version/unbind-initiator combinations with real local TCP.
S: pass | All cases assert endpoint public behavior: paired binds, enquiry directions, cancellation, graceful drain, invalid shutdown atomicity and listener close.
O: pass | Parameterized initiator/mode/version values share the same real contracts. RawPeer supplies an independently controlled graceful-drain peer rather than a second tracker.
L: pass | No inherited test contract. Owned servers/clients/sockets close in finally; latches and received frames coordinate state. All twelve combinations assert both session cleanup paths, and invalid grace cannot consume server start.
I: pass | Uses only implemented binding/control and termination methods; receivers are never forced to implement a sender interface.
D: pass | Depends on public endpoint APIs, raw bounded peer fixture and JDK/Jupiter. Expected messages are independently encoded, and no production private state is reflected.
findings: none
```

## TDD and verification

All Gradle commands used normal caches and the literal suffix
`--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step11'`.
No clean, forced rerun, dependency refresh, disabled warning, skipped test or
unconditional failing assertion supplied evidence. New API declarations were
made compilable before behavioral red runs. The table records actual sequential
observations, with logs under `/tmp/`; test selectors are relative to
`kg.aidarbek.smpp.endpoint`. The command prefix was
`./gradlew test --tests 'kg.aidarbek.smpp.endpoint.SELECTOR'`; `*` means the full
endpoint suite. Refactor/build/Javadoc commands are separately identified below.

| Red / green log stems (`step11-… .log`) | Red selector and observed failure | Passing green scope |
| --- | --- | --- |
| `01-values-red` / `02-values-green` | `EndpointValuesTest`: unrepresentable unsigned decision accepted | Values |
| `03-config-red` / `04-config-green` | `EndpointValuesTest`: zero capacity accepted | Values |
| `05-auth-capacity-red` / `06-auth-capacity-green` | `AuthenticationDispatcherTest`: declared authenticator was never invoked | Authentication |
| `07-supplied-auth-red` / `08-supplied-auth-green` | `AuthenticationDispatcherTest`: supplied executor invocation count remained zero | Authentication |
| `09-negatives-red` / `10-negatives-green` | `EndpointPdusTest`: empty result differed from independent 16-byte negative fixture, both profiles | Negative wire fixtures |
| `11b-server-bind-corrected-red` / `12-server-bind-green` | `EndpointConnectionTest`: valid minimal raw bind produced no response | Connection |
| `13-controls-red` / `14-controls-green` | `EndpointConnectionTest`: established server returned no real enquiry handle | Connection |
| `15-client-bind-red` / `16-client-bind-green` | `EndpointConnectionTest`: client emitted no bind request | Connection |
| `17-version-rejection-red` / `18-version-rejection-green` | `EndpointConnectionTest`: server omitted negative reply; client lost raw status/advertisement outcome | Connection |
| `19b-bind-deadline-red` / `20-bind-deadline-green` | `EndpointConnectionTest`: idle/binding connection stayed OPEN after its bound | Connection |
| `21-protocol-rejection-red` / `22-protocol-rejection-green` | `EndpointConnectionTest`: prebind request received no required negative | Connection |
| `23-server-listener-red` / `24b-server-listener-green` | `SmppEndpointsTest`: requested ephemeral listener remained port zero | `*`; 14 cases |
| `25-client-server-red` / `26-client-server-green` | `SmppEndpointsTest`: each of six actual mode/profile connects returned no bound facade | `*`; 20 cases |
| `27-fake-contract-red` / `28-fake-contract-green` | `EndpointConnectionTest`: fake accepted a pre-start write contrary to shared port contract | `*`; 21 cases |
| `29-attempt-cancel-red` / `30-attempt-cancel-green` | cancellation before start/during real authentication returned false | `*`; 23 cases |
| `31-cleanup-failures-red` / `32-cleanup-failures-green` | `EndpointResourcesTest`: transport cleanup error not propagated and listener error reported complete | `*`; 25 cases |
| `33-graceful-drain-red` / `34-graceful-drain-green` | `SmppEndpointsTest`: early unbind overtook pending enquiry; invalid shutdown consumed start | `*`; 27 cases |
| `35-bind-certainty-red` / `36-bind-certainty-green` | failed initial bind write published generic CLOSED instead of RequestFailure | `*`; 28 cases |
| `37-invocation-deadline-red` / `38-invocation-deadline-green` | monitor wait incorrectly restarted a full control request budget | `*` |
| `39-early-response-red` / `40-early-response-green` | matching response to queued unsent bind changed BINDING to BOUND_TRX | `*` |
| `41-bind-failure-red` / `42-bind-failure-green` | notification-backlog bind admission escaped the internal connected callback | `*`; includes retained disconnect cause/certainty |
| `43-auth-errors-red` / `44-auth-errors-green` | `EndpointAdversarialTest.authenticationFailuresProduceBoundedNegativeReplies`: direct Error yielded EOF instead of paired failure response; 1/6 failed | `*`; all six application failure variants pass |
| `45-bind-deadline-outcome-red` / `46-bind-deadline-outcome-green` | expired bind replaced the winning request deadline outcome with EndpointException | `*` |
| `48-fake-close-barrier-red` / `49-fake-close-barrier-green` | fake termination completed while its winning closed callback was still gated | `*` |
| `51-construction-cleanup-red` / `52-construction-cleanup-green` | `EndpointResourcesTest.failedConstructionKeepsItsPermitUntilTransportCleanupActuallySucceeds`: still-owned transport slot could be readmitted | `*` |
| `53-examples-red` / `54-examples-green` | `EndpointExamplesTest`: declared client did no work and actual example server never observed binding | `*`; compiled examples perform real exchange |
| `57-fake-queue-red` / `58-fake-queue-green` | full fake deferred queue retained an unaccepted write, preventing owned notification/termination cleanup | `*`; bounded refusal and cleanup pass |

The initial `11-server-bind-red` fixture contained an extra byte; the corrected
23-byte fixture in `11b` supplied the relevant red. The initial
`19-bind-deadline-red` did not compile against an out-of-date copied SPI and is
not behavioral red evidence; the synchronized `19b` run is. The first listener
green attempt (`24-server-listener-green`) raced closure against kernel acceptance;
`24b` added an independent pre-bind enquiry handshake to establish accepted
ownership before checking closure. The first broad real-peer run
(`47-real-peer-characterization`) stopped on the strict compiler's explicit-close
warning inside try-with-resources; the corrected `47b-real-peer-characterization`
executed and passed. `56-javadoc-audit` succeeded but reported 38 documentation
warnings; corrected tags/serialized-field docs subsequently produced zero warnings.
None of these unsuccessful fixture/compilation/documentation attempts is counted
as an intended behavior red.

Already-correct behavior was characterized without manufacturing failures:
`47b-real-peer-characterization` covers all 16 requested/advertised/strict
combinations, six authentication failure forms, idle accepted expiry, physically
blocked invocation capacity, finite authentication queue, supplied executor
survival, globally retained blocked notifications, wrong/malformed responses,
refused connect/disconnect and grace expiry. `55-final-wire-characterization`
adds client admission, cancelled/late enquiry, generic_nack and unavailable
submission/delivery/data service negatives across both directions and every
mode/profile. `60-unbind-origin-characterization` expands the real client/server
pair to twelve mode/profile/unbind-initiator variants; all pass.

Formatting was a separate task before inventory (`50-core-format`,
`59-final-format`, `61-final-format`, `63-document-format`, `65-review-format`).
`62-test-javadoc-inventory` ran `./gradlew test javadoc solidReviewInventory`:
**423 library/architecture cases across 39 suites** freshly executed, including
**77 endpoint cases** and **12 architecture cases**, with zero failures/errors/
skips. Production/example compilation was up to date; test compilation, Javadoc,
inventory and test execution ran; review-tool compilation used a matching cache.
`66-final-javadoc-inventory` rechecked final documentation/source hashes with no
Javadoc warnings. The inventory contains **174 current types**, including all
35 owned identities above; copied prerequisite tests were intentionally not
imported just to repeat their owners' independent suites.

Architecture evidence belongs to
[the root-owned endpoint architecture report](0010-endpoint-architecture.md).
The root's actual, compilable forbidden file-I/O and concrete-transport dependency
probes failed the intended new rules, were removed, and all twelve rules passed
on the restored formatted production graph. The coordinator and both observer
members are selected non-vacuously. Pure session/request/SPI boundaries remain
unchanged. The final coordinator hash stayed byte-identical to the reviewed
architecture snapshot; later option changes were Javadoc-only.

The isolated examples evidence is the compiled `EndpointExamplesTest` real peer
exchange. The individual JavaExec tasks are uncached operations with the explicit
JDK toolchain and examples runtime classpath; root owns their final CLI smoke and
combined build verification. The guide uses a prior `examplesClasses` compile
and direct Java commands in separate terminals to avoid concurrent Gradle project
execution locks. No two simultaneous Gradle executions in one checkout are
claimed as verified.

## Final isolated result and handoff

`./gradlew build --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step11'`
passed (`/tmp/step11-67-final-build.log`). The current **423 library/architecture
cases** freshly executed with zero failed, errored or skipped cases. The separate
**60 review-tool cases across four suites** were restored from matching cache;
they did not execute again. Compilation, Javadoc and formatting checks were
up to date from the preceding successful current-source runs. `solidReview` and
all three archives executed successfully. There were no warnings. The build
reported five executed tasks, two restored from cache and seven up to date.

The final inventory and successful coverage contain the same **174 current type
identities**. A separate SHA-256 comparison matched all 35 owned identities to
final files. Archive inspection confirmed examples, example tests and review
tooling are absent from library/source/Javadoc artifacts. Guide/review local
links, final newlines, balanced fences and trailing whitespace passed, as did
`git diff --check`. The final narrative's evidence input is rechecked by
`solidReview` in `/tmp/step11-68-final-coverage.log` before patch handoff.

The Step 11-only patch contains 28 new files: 15 production classes, nine test/
fixture files, two example files and these two endpoint documents. The separate
examples build patch remains root-owned. Copied Step 9/10 sources, guides, shared
fixture and reviews, plus the root-owned ArchitectureTest/report, are excluded.
The combined root suite and CLI helper execution remain explicit root integration
checks. No requested Step 11 behavior or known SOLID finding remains unresolved.
