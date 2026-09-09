# Submission and delivery exchange

Step 12 connects `submit_sm`, `deliver_sm`, `data_sm` and their responses to the
real client/server endpoints under SMPP 3.4 and 5.0. Applications supply immutable
protocol values and asynchronous acceptance decisions. The existing request
window owns every outgoing request; message exchange adds no separate sequence,
correlation, cancellation or retry engine. See [message codecs](MESSAGES.md),
[session policy](SESSIONS.md), [requests](REQUESTS.md), and the
[TDD/SOLID evidence](reviews/0011-message-exchange.md).

## Send an implemented operation

`BoundSession.submission()`, `delivery()` and `dataMessages()` return optional
`OperationSender<Q,R>` capabilities. The generic equivalent is
`session.sender(MessageOperations.SUBMIT_SM)` (or `DELIVER_SM` / `DATA_SM`).
An empty capability reflects the local role, negotiated profile, bind mode or
current lifecycle. Capability presence does not promise that a remote application
will accept a request. Every send checks the current state again, so a stored
sender cannot admit work after unbinding, closure or endpoint shutdown.

| Operation | Origin | SMPP 3.4 modes | SMPP 5.0 modes |
| --- | --- | --- | --- |
| `submit_sm` | ESME | TX, TRX | TX, TRX |
| `deliver_sm` | Message center | RX, TRX | RX, TRX |
| `data_sm` | ESME | RX, TX, TRX | TX, TRX |
| `data_sm` | Message center | RX, TX, TRX | RX, TRX |

The difference for `data_sm` follows the exact specification tables, as recorded
in [the protocol inventory](PROTOCOL.md). Response traffic travels in the
opposite direction and keeps the original request's message-direction context.

A sender offers `send(command)`, `send(command, RequestOptions)`, and
`send(command, RequestOptions, SendRequirements)`. Missing request options select
the endpoint default. The result is the existing `RequestHandle<R>`, whose
protected `result()` contains a typed `Pdu<R>`. Nonzero peer statuses remain typed
responses; a correlated generic nack is `PeerNackException`. Local failures retain
request identity, cause and transmission certainty. Explicit cancellation is
`handle.cancel()`; cancelling an observation future does not withdraw the request.

The total request deadline starts before waiting for the connection owner and
includes validation, admission, queueing, writing and response matching. Requests
are admitted fail-fast against the same count, byte and notification limits as
controls. Wrong, duplicate, late or not-yet-transmitted response identities do not
settle another request. Opposite directions have separate sequence namespaces.

Actual outgoing TLV presence is derived from command data. The negotiated codec
checks actual field features; caller-supplied `COMMON` cannot enable TLVs or
5.0-only fields when a missing advertisement restricts sending to the common
3.4 subset. Extra requirements can only tighten this policy. Validation occurs
before request-window or transport admission. Payload octets and `data_coding`
remain explicit: sending performs no implicit text conversion, splitting or
receipt interpretation.

## Register optional typed handlers

Build an immutable registry with `EndpointHandlers.builder().on(operation,
handler).build()`. Pass it in `new ExchangeConfig(options, handlers)` to
`SmppClient(EndpointOptions, ExchangeConfig)` or the six-argument `SmppServer`
constructor. Existing constructors use an empty handler registry. Registrations
are optional and independent: applications implement only the services they own.
A builder rejects duplicate or unsupported operation keys; later builder changes
do not alter already-built registries.

`RequestHandler<Q,R>` receives `IncomingRequest<Q>` and returns a non-null
`CompletionStage<HandlerResponse<R>>`. The context contains the immutable PDU,
owning session, absolute monotonic decision deadline and cooperative
`isCancelled()` observation. A handler response supplies unsigned command status,
typed body and optional stricter send requirements. The library supplies the
original peer sequence number and validates the result against the operation,
profile and original request. Transaction-only response diagnostics cannot be
attached to a nontransaction request. Invalid peer response context closes the
connection without a response-to-response nack loop.

An application owns its storage and acceptance policy. Complete a successful
submission only after its required acceptance work succeeds. A delivery
acknowledgement accepts that PDU; it is independent of handset delivery and any
later delivery receipt. For example, a successful `SubmitSmResponse` contains an
application message ID; a successful `DeliverSmResponse` contains its required
empty message-ID field. Nonzero responses must use the profile's valid negative
body. The [compiled examples](../src/examples/java/kg/aidarbek/examples) show both
handlers without requiring a persistence service.

| Condition after a valid request reaches the message service | Result |
| --- | --- |
| Handler returns a valid decision within its deadline | Paired typed response with the application's status/body |
| No handler, thrown exception/error, null stage/result, failed stage, invalid response, or decision timeout | Paired `ESME_RSYSERR` (`0x08`) |
| Handler concurrency/waiting capacity is full after a reply slot was reserved | Ordered paired `ESME_RTHROTTLED` (`0x58`) |
| No ordered reply-count or minimum reply-byte reservation is available | Close the connection; retain no unbounded rejection queue |
| Completed reply cannot fit the remaining byte allowance | Replace it with the already-reserved paired system-error response |

Invalid state/direction, malformed bodies and unknown commands retain the
separate [endpoint protocol-error policy](ENDPOINTS.md). These error/control
paths do not invoke application handlers.

## Ordering, bounds and deadlines

Each endpoint owns a fixed message-worker pool and one globally bounded handler
queue, separate from authentication and result notification. Defaults are four
active handler slots and 128 additional waiting slots. A handler must support
concurrent sessions. Within a session, the next invocation begins after the
previous invocation returns its stage; incomplete asynchronous stages may overlap.
No application handler runs on a transport thread or while a library lock is held.

An active slot remains occupied until both invocation and returned stage finish.
Timeout or closure cancels the logical decision, removes unstarted work and
suppresses late output. It cannot forcibly stop a blocked callback or unfinished
application stage. That physical work remains counted against the same global
limit, including after its socket closes; reconnecting cannot create unbounded
replacement workers. A stage whose completion cannot be observed because its
`whenComplete` registration throws is retained conservatively. Message invocation
always uses owned workers; the optional supplied executor remains authentication
only and caller-owned.

`ExchangeOptions.handlerTimeout` defaults to ten seconds from complete-frame
arrival. Decode, connection-owner waits, handler queueing and application decisions
consume that budget. Already-expired work does not begin an application callback.
The endpoint's existing 5 ms scan checks deadlines; completion boundaries also
check them. Scheduling resolution is not a real-time guarantee.

Replies for admitted ordinary application requests leave in receive order, even
when later decisions finish first. Defaults allow 32 pending replies per session
and 1 MiB of accounting: retained request wire sizes plus reserved or encoded
reply sizes. A valid paired system-error response is reserved before admitting a
handler. Completed replies waiting behind earlier decisions, queued output and
active response writes stay counted until transport settlement. These wire-size
bounds are distinct from exact JVM heap use; immutable objects held by unfinished
callbacks remain bounded by handler admission. Codec transient allocation stays
under the configured PDU/TLV limits. For a maximum-size incoming PDU, configure a
reply budget above its wire size so its negative response can also be reserved.

Only the ready head is offered to ordinary transport capacity. A full queue keeps
that reply in its existing slot for a later scan; it neither allocates another
queue entry nor refreshes the write deadline. The write budget is
`EndpointOptions.requestTimeout` from the first head-write attempt. It covers
transport admission retries and physical output; expiry or a terminal write
failure closes the connection. Inline transport completion is drained iteratively,
so a large finite ready backlog does not grow the Java call stack.

Control replies use the transport's independent finite control reserve. A delayed
or blocked message handler cannot prevent enquiry/unbind progress. Control traffic
still respects that reserve and cannot preempt an already active physical write.

## Shutdown and result observation

`endpoint.shutdown(grace)` stops new admission and drains both outgoing requests
and admitted incoming message replies before initiating unbind. The total grace
still bounds shutdown. `BoundSession.close()` and endpoint `close()` abort promptly;
peer unbind also suppresses outstanding application output while its control
response and transport cleanup finish.

`EndpointTermination.remainingHandlers()` reports invocations/stages still
retained at the shutdown bound. `complete()` also requires that count to be zero
and all owned workers to have terminated. The immutable snapshot does not change
when an uncooperative application later finishes. Socket cleanup proceeds
independently, and the existing authentication, notification and original cleanup
failure observations remain available.

Admitted request/readiness/session notifications use the endpoint's shared
bounded notification workers. Blocking non-async dependents occupy their original
reservation; there is no unbounded fallback. Pre-admission/start results may
already be complete, and dependents attached after completion follow ordinary
`CompletionStage` caller/executor semantics. Handler-stage dependents belong to
the application's chosen stage/executor; the library only registers its internal
bounded completion bridge.

## Run the messaging examples

Compile once, then use separate terminals with Java 21:

```shell
./gradlew examplesClasses --console=plain
java -cp build/classes/java/main:build/classes/java/examples kg.aidarbek.examples.ExampleServer 2775 60
java -cp build/classes/java/main:build/classes/java/examples kg.aidarbek.examples.ExampleClient 2775
```

The loopback server accepts the demonstration credentials, assigns an in-memory
submission ID, and sends a separate welcome delivery. The client submits raw
ASCII-compatible example bytes, accepts the delivery, checks enquiry, and drains
before unbinding. This example provides no durable store or handset-delivery
claim. Its fixed requested 3.4 profile remains effective even though the server
advertises 5.0. The server lifetime is finite; both processes check cleanup.

The existing `runExampleClient` and `runExampleServer` Gradle helpers remain
available as individual invocations. Avoid concurrent Gradle invocations in one
checkout. Tests execute the compiled pair and independently inspect each example's
message traffic. See [endpoint setup](ENDPOINTS.md) for binding configuration.

Common operations, simulators and encoding/receipt helpers remain their requested
later steps; complete 5.0 operation support, TLS/reconnect, heavy-load qualification
and pinned independent interoperability remain Steps 16–19. Raw-peer fixtures and
our own paired endpoints do not establish independent-provider interoperability.
