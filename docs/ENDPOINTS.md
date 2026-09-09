# Client and server binding

`SmppClient` and `SmppServer` provide real TCP connection, bind, `enquire_link`,
`unbind` and close operations. Both SMPP 3.4 and 5.0 support receiver,
transmitter and transceiver binds. `BoundSession` also exposes focused submission,
delivery and data-message capabilities with optional asynchronous handlers,
described in [EXCHANGE.md](EXCHANGE.md). [Common operations](COMMON_OPERATIONS.md)
add management/multiple-submission senders, alerts and reversed outbind owners.
[Simulators](SIMULATORS.md) exercise both roles. Automatic enquiry, reconnect/retry
and TLS remain later work.

The endpoint package composes the existing [session policy](SESSIONS.md),
[request window](REQUESTS.md) and [TCP frame transport](TRANSPORT.md). The
per-connection coordinator depends on the transport SPI; only the client/server
composition points select concrete TCP adapters. Codecs retain their independent
byte-oriented APIs. Endpoint state policy does not perform authentication or I/O.

## Run the compiled examples

The examples live in `src/examples/java/kg/aidarbek/examples`, outside the
published library and its source/Javadoc archives. Compile them once:

```shell
./gradlew examplesClasses --console=plain
```

Then run these Java 21 commands in separate terminals. The server exits after
its specified lifetime:

```shell
java -cp build/classes/java/main:build/classes/java/examples kg.aidarbek.examples.ExampleServer 2775 60
java -cp build/classes/java/main:build/classes/java/examples kg.aidarbek.examples.ExampleClient 2775
```

Individual `runExampleServer --args='2775 60'` and
`runExampleClient --args='2775'` Gradle tasks are also provided. Use one Gradle
invocation at a time in a checkout; concurrent Gradle builds can wait on the same
project execution lock.

[ExampleServer](../src/examples/java/kg/aidarbek/examples/ExampleServer.java)
listens only on loopback, accepts versions 3.4 and 5.0, and explicitly advertises
5.0. Its literal `demo` credentials are for this local example.
[ExampleClient](../src/examples/java/kg/aidarbek/examples/ExampleClient.java)
requests a 3.4 transceiver, requires an advertisement, submits raw example bytes,
acknowledges an independent welcome delivery and sends an enquiry. It drains
message work before unbinding and checks cleanup. Effective 3.4 is retained despite
the server's 5.0 advertisement. The compiled examples are tested together and
against raw peers; the [exchange guide](EXCHANGE.md) explains their application
acceptance behavior.

## Construct and observe a connection

Create immutable `EndpointOptions` for finite connection, request, byte,
notification and deadline limits. `ClientConfig` carries the complete immutable
`BindRequest` plus the explicit advertisement requirement. Addresses must already
be resolved; DNS remains caller-owned and cannot silently extend the connect
budget. The default options allow 100 combined connecting/active/closing
connections, 32 pending requests and 1 MiB retained request bytes per connection,
4 notification workers with 128 additional notification slots, a 5-second connect
budget, and 10-second bind, default request and abort-cleanup budgets.

`client.connect(config)` completes with a `BoundSession` after a successful bind.
`client.connectAttempt(config)` exposes the same workflow through
`ConnectionAttempt.result()` and `cancel()`. Cancellation can abort connect or
bind before internal readiness wins. After readiness, cancellation returns false;
close the bound session to abort it. Cancelling or manually completing a future
obtained from a protected completion stage changes only that observation.

`BoundSession.enquireLink()` and `unbind()` return the actual
`RequestHandle<ControlCommand>`. Optional `RequestOptions` supply the whole
invocation budget, including waiting for connection ownership, request admission,
transport queueing, write and response. The no-argument overloads use the endpoint
default; this version also treats a null explicit request-options argument as
that default. `handle.cancel()` settles the owned request; it does not imply that
the peer received no bytes. Inspect `RequestFailure.transmission()` and the
structured reason/cause. Bind and unbind cancellation, timeout or write failure
close their lifecycle connection. An enquiry failure leaves the connection usable
unless the transport itself failed.

Pending responses require both the matching command/sequence context and a
transport write guard that has claimed transmission. Unsent queued requests,
unmatched replies, wrong response commands and late replies cannot establish or
resurrect a session. Generic nacks settle their actual owning requests. Opposite
endpoints keep separate sequence namespaces, including equal numbers and crossed
unbinds. No operation is replayed automatically.

`connectionCount()` counts reservations through physical transport cleanup;
`sessions()` is an immutable snapshot of internally ready bound sessions. A session
may close immediately after a snapshot is taken. Applications receive immutable
version facts via `session.negotiation()` and current state via `session.state()`.

## Accept and authenticate

`ServerConfig` separately declares accepted requested versions and the version
advertised in successful bind responses. `SmppServer.start()` binds its one
listener and returns the actual address, including the assigned ephemeral port.
Invalid shutdown arguments leave a not-yet-started server usable; a successful
start or closure cannot be repeated to create a second listener.

`BindAuthenticator` receives the immutable request and remote socket identity,
and returns a non-null `CompletionStage<BindDecision>`. Status zero accepts the
credentials; a nonzero unsigned status produces the corresponding negative bind
response. An exception, null stage/decision or failed stage produces
`ESME_RSYSERR`. Authentication cannot override the version acceptance policy or
grant an unsupported protocol capability. Do not expose credentials through logs.

The bind deadline starts when a server connection is accepted, before its first
bind arrives. For a client it starts on connection readiness and covers sending
the bind and receiving its response. Waiting for authentication invocation,
completion of its returned stage and writing the bind response consumes that
same budget. Idle peers and stalled authentication therefore retain only bounded
resources. A timed-out or closed bind suppresses late authentication success.

The [API version table](API.md) is enforced without implicit downgrade:
requested 3.4 remains effective 3.4 when the peer advertises 5.0; requested 5.0 with
an explicit 3.4 advertisement fails. Missing advertisement, when allowed, retains
the raw absence and restricts effective sending to known common, TLV-free 3.4
operations and fields. Unknown advertisements fail. Existing `SendRequirements`
and negotiated profiles govern current message capabilities: actual TLVs and
version-specific fields are derived or checked before admission. These control
methods send only common fields and no optional parameters.

## Workers and admission bounds

Each endpoint owns one deadline worker and one shared bounded notification pool.
Admitted connection readiness/failure, request results, bound-session observers
and public session termination are published on this pool, outside transport
read/write progress. Pre-admission validation/capacity results and server start
results may already be complete when returned. Dependents attached after any
completion follow ordinary CompletionStage caller/executor semantics. A blocking
non-async `CompletionStage` dependent consumes its notification reservation until
it physically returns. Reservations include work promised but not yet queued:
each connection reserves its bind observation and termination notification, and
each outgoing request reserves its result notification. Thus notification capacity
can reject a connection or request before its nominal connection/window limit.
There is no unbounded completion fallback or per-session notifier worker.

Message handlers use a separate endpoint-wide fixed dispatcher with bounded
concurrency/queueing, per-session invocation order and physical capacity retention
after timeout or closure. [Exchange options](EXCHANGE.md) also bound ordered
reply counts/bytes through write completion.

A server separately owns a fixed authentication dispatcher with the configured
concurrency and finite queue. An active slot remains held while invoking the
authenticator and while its returned stage is incomplete. Logical timeout does
not release physical capacity occupied by blocked application code. Queued
cancelled work can be removed immediately. Exhausted authentication admission
returns a negative bind response when its write deadline still permits one.

The optional supplied `Executor` is used only for authentication invocation. An
owned bounded authentication worker calls its `execute` method and waits for the
actual invocation/stage; an inline, rejecting, blocking or silently discarding
executor cannot move application work onto a transport thread or produce
unbounded replacements. The endpoint never closes a supplied executor. Ordinary
result/lifecycle notifications always use the owned notification pool.

A connection permit is retained until successful physical cleanup, including a
transport allocated before connection construction fails. Failed cleanup retains
the permit and its reported cause. Successfully closed connections can release
their transport permits while globally bounded authentication/message/notification work
remains occupied; further admission still respects those independent global
limits. The accept loop has no application queue of accepted sockets.

## Shutdown and protocol errors

`BoundSession.close()` aborts one connection idempotently. Endpoint `close()`
aborts all owned connections immediately and initiates cleanup using the configured
shutdown bound. It does not synchronously wait for application callbacks.
`shutdown(grace)` stops connection/request admission, drains already pending
outbound requests and admitted incoming message replies, sends unbind when both
drain, and closes transports
when the total grace expires. Peer-originated unbinds are answered during this
process; crossed unbind requests retain both obligations until replies finish.
Repeated calls share one termination result; abort may shorten a pending grace.

`endpoint.termination()` returns an immutable `EndpointTermination` snapshot when
cleanup finishes or the bound expires. `complete()` requires zero retained
connections/authentication/handlers/notifications, stopped listener/timer/application
workers, and no cleanup failures. Inspect each count and the original failure
references when it is false. A bounded result does not claim that uncooperative
application code was stopped. Such work remains globally bounded on daemon
workers and may finish later; the original snapshot is not rewritten. Exceptional
transport cleanup also reaches the session termination stage.

The endpoint's final cleanup result is published after internal cleanup on one
owned publication thread. A blocking dependent can retain that one thread, but
cannot stop socket cleanup. Socket operations, authentication scheduling and
notification dispatch never wait for application-result dependents. Deadline
expiry is checked at request boundaries and by one periodic 5 ms endpoint scan;
it is a bounded policy with scheduling resolution, not a real-time guarantee.

Well-formed messages without a registered handler receive the paired
`ESME_RSYSERR` response only where the exact negotiated version/mode/direction
matrix permits that request; otherwise they receive `ESME_RINVBNDSTS`. Unknown or
unimplemented command codecs receive `generic_nack` with `ESME_RINVCMDID`.
Recoverable malformed requests receive a protocol negative and close; malformed
responses close without generating another response. Invalid original sequence
numbers use the session policy's permitted nack-zero fallback. Peer bind rejection
and version failures preserve their raw wire status or advertisement in
`EndpointException`, separately from local `RequestFailure` and `TransportFailure`.
