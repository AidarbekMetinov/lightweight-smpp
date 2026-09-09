# Library contracts

Step 1 design baseline, updated through Step 15. Binding, control, message
exchange, common operations and pure message helpers are compiled and tested. Executed contracts are
documented in [FRAMING.md](FRAMING.md), [FIELDS.md](FIELDS.md),
[COMMANDS.md](COMMANDS.md), [MESSAGES.md](MESSAGES.md),
[SESSIONS.md](SESSIONS.md), [REQUESTS.md](REQUESTS.md),
[TRANSPORT.md](TRANSPORT.md), [ENDPOINTS.md](ENDPOINTS.md) and
[EXCHANGE.md](EXCHANGE.md), [COMMON_OPERATIONS.md](COMMON_OPERATIONS.md),
[BROADCAST.md](BROADCAST.md) and [LIFECYCLE.md](LIFECYCLE.md). Refine future API
names through tests while preserving the behavior or documenting an intentional change.

## Scope and decisions

Provide an ESME client and a message-center server, sharing protocol values,
codecs, and session policies. Target Java 21 and SMPP 3.4/5.0. Keep one library
artifact initially, with no runtime dependencies until a concrete requirement
justifies one. Simulator tooling is a separate application subproject.

Use one asynchronous request mechanism. A caller may wait on its result; a future
blocking convenience API must delegate to the same mechanism. Expose focused
capabilities for submission, delivery, message management, and broadcast rather
than requiring every session or handler to implement every operation.

Connection role and SMPP role are distinct. Normal ESME binding opens TCP from
client to server. Outbind additionally needs an ESME listener and a message-center
connector; it sends the notification and subsequent bind on that connection.
`OutbindListener` and `OutbindConnector` implement these explicit owners, with
authentication before the follow-up bind and no automatic retry.[^1][^2]

The [protocol inventory](PROTOCOL.md) owns wire and role rules. The
[workload criteria](WORKLOADS.md) define benchmark inputs. Both the production
endpoints and simulators must use these contracts.

## Client and server binding

[Endpoint contracts](ENDPOINTS.md) contain the implemented API and runnable client
and server examples. The examples compile separately from the production library
and remain outside its binary, source and Javadoc archives.

`SmppClient.connect(config)` completes after a positive bind satisfying the
configured version policy. `connectAttempt(config)` exposes cancellation of that
same connection/bind workflow before a `BoundSession` exists. Its result uses the
same protected readiness stage; cancelling a derived future only changes that
observation. A failed or expired bind closes its connection, and reconnect never
replays outstanding requests.

`reconnect(config, policy, observer)` explicitly owns a bounded sequence of fresh
client connections. OutbindConnector has the analogous initiating-owner method.
The attempt bound includes the initial connection and admission failures; a new
generation waits for physical retirement, backoff and the previous observer's
return. `ReconnectHandle` owns cancellation and a protected terminal result.
Already-offered callbacks retain their notification ownership after cancellation.
No transmitted or pending message is copied to a replacement connection.

`SmppServer` receives listener/version configuration, endpoint resource limits,
a focused `BindAuthenticator` and a bound-session notification callback. Supplied
executors are used only for authentication invocation and remain caller-owned.
Authentication timeout cannot release capacity still occupied by blocked
application code. The server's connection and callback bounds remain independent.

A `BoundSession` exposes connection/version/bind metadata, `enquireLink`, `unbind`,
closure and termination observation, plus optional submission, delivery and
data-message senders, common management/multiple-submission senders and one-way
alerts. Normal binding supports RX, TX and TRX under 3.4 and 5.0. Each send
rechecks current state. User callbacks and future publication
run outside socket progress and coordinator locks.

## Messaging application contracts

`BoundSession.submission()`, `delivery()` and `dataMessages()` expose optional
`OperationSender` capabilities according to negotiated profile, bind role and
current state. `sender(MessageOperations.SUBMIT_SM)` and the corresponding
operation keys provide the typed generic form. `send(command)` uses the default
request deadline; overloads accept `RequestOptions` and stricter
`SendRequirements`. Actual TLVs and version-specific fields are checked from the
command, so a caller cannot weaken missing-advertisement restrictions.

Register optional typed `RequestHandler` callbacks with `EndpointHandlers` and
pass them through `ExchangeConfig` to the client or server. `IncomingRequest`
contains the immutable PDU, session, decision deadline and cancellation
observation. An asynchronous `HandlerResponse` carries status and the typed body;
the library supplies correlation and validates original-request response rules.
See [EXCHANGE.md](EXCHANGE.md) for exact signatures and bounds.

An application owns storage and acceptance policy. A durable handler completes
acceptance after its required storage succeeds; a simulator may accept in memory.
Acknowledging an incoming delivery means accepting that PDU, independently of
handset delivery or a later receipt for another submitted message. Optional query,
replacement, cancellation and multiple-destination services use their own typed
operation keys. SMPP 5.0 `broadcast()`, `queryBroadcast()` and `cancelBroadcast()`
use the same capability and handler contracts for ESME TX/TRX origination and
MC receipt. [Broadcast contracts](BROADCAST.md) define exact payload/TLV rules,
application responsibilities and source interpretations. `congestion()` returns
an immutable optional observation from the latest valid, matched 5.0 response;
it does not change admission or retry policy.

An absent, failed, invalid or expired handler decision returns the paired
`ESME_RSYSERR`; exhausted handler capacity returns `ESME_RTHROTTLED` when an
ordered reply can be reserved. If no reply ownership can be reserved, the
connection closes. Invalid state/direction and unknown commands retain distinct
error responses documented in [ENDPOINTS.md](ENDPOINTS.md). Sender capability
presence does not promise remote application acceptance.

## Common services and one-way operations

`CommonOperations` supplies query, cancel, replace and multiple-submission keys.
`BoundSession.query()`, `cancel()`, `replace()` and `multipleSubmission()` expose
only permitted optional senders. They use the same request result/cancellation
contract. A protocol `cancel_sm` is a separate application operation from local
`RequestHandle.cancel()`. Missing handlers receive profile-correct negatives.

`alerts()` exposes an MC RX/TRX `AlertSender`; `onAlert` registers an optional
ESME notification handler. `NotificationSend.result()` reports local physical
write settlement and never remote acknowledgement. It has no response-window
entry; sequence numbers still share the connection's non-reused allocator.
Incoming alerts share bounded physical handler capacity and invocation ordering.

`OutbindListener` authenticates an MC notification before sending its configured
ESME bind. `OutbindConnector.connectAttempt()` opens one explicit connection,
sends one outbind and authenticates the subsequent ESME bind. Both owners retain
physical capacity after logical cancellation and expose bounded shutdown. SMPP
3.4 outbind uses RX; explicit 5.0 RX/TX/TRX modes follow its state table. See
[COMMON_OPERATIONS.md](COMMON_OPERATIONS.md) for exact failure and deadline rules.

## Requests, results, and cancellation

| Contract | Decision |
| --- | --- |
| Request identity | The connection-owned request window assigns sequence numbers; applications provide message data, not manually reused sequence keys. |
| Correlation | Match session generation, local request sequence, and expected response command. Incoming peer requests use a separate namespace. |
| Terminal outcome | Exactly one of peer response, local failure, cancellation, or deadline expiry wins. Late responses cannot complete a newer request. |
| Peer rejection | Return the typed operation response with its numeric status and permitted body/TLVs, preserving unknown statuses. A correlated `generic_nack` uses the distinct `PeerNackException` because it has no operation-specific response body. It is a known peer outcome. |
| Local failure | Complete exceptionally with structured reason, operation, session identity, and transmission certainty. |
| Cancellation | `RequestHandle.cancel()` competes for the terminal outcome; it never sends `cancel_sm` or withdraws an already transmitted message. |
| Result observation | `RequestHandle<R>.result()` exposes `CompletionStage<Pdu<R>>` without allowing callers to complete the library's internal result. Use the handle to cancel; terminal-state observation does not wait for application notification. |
| Blocking wait | Waiting or interrupting a caller's `get()` does not automatically cancel its protocol request. |
| Retries | No automatic message replay. Reconnect creates a new session and does not inherit outstanding request identifiers. |

Use transmission certainty values such as `NOT_SENT` and `MAY_HAVE_BEEN_SENT` on
local failures. A partial or completed local write cannot prove peer acceptance.
A positive or negative peer response is a known protocol outcome; handset delivery
and later receipt state remain separate. Do not describe timeout or cancellation
as guaranteed message rejection.

Step 9 allocates sequence numbers monotonically from 1 through `0x7fffffff`
within a unique connection generation. It never wraps or reuses a number, even
after completion. Exhaustion rejects admission; establish a new connection
instead of resetting the existing window. Verify same-valued sequence numbers
can coexist in opposite directions.[^1][^2]

Default admission is fail-fast when the outbound request window or byte bound is
full. A waiting-admission option, if added, must have its own bounded queue and
consume the same overall deadline. No public method hides an unbounded queue.

## Deadlines and callback execution

The request timeout starts at API invocation and includes validation/admission,
writing, and waiting for a response. The terminal transition uses monotonic time.
Endpoint configuration controls TCP connection, binding/authentication, manual
requests and shutdown deadlines. `ExchangeOptions` controls message-handler
completion from complete-frame arrival, including owner waits and queueing. TLS
handshake bounds are explicit in `TlsConfig`. An accepted connection's total
bind budget includes TLS and authentication; initiating endpoints start their
bind budget after TLS readiness. `KeepalivePolicy` retains the original idle-plus-
response deadline across request-window, byte and notification saturation.
Both manual and automatic enquiries use bounded CONTROL transport capacity
while sharing the ordinary request window; no extra request slot is implied.

`ConnectionLifecycle` supplies optional TLS and keepalive settings to each owner.
Existing constructors retain plain TCP and manual enquiries. TLS role follows
TCP origin, independently of SMPP role; clients require an explicit certificate
identity and normal trust verification. Caller-supplied SSLContext/key/trust
configuration remains caller-owned. See [lifecycle contracts](LIFECYCLE.md) for
provider/callback cooperation and the distinction between socket abort and full
physical termination.

The implemented defaults are 5 seconds for TCP connection and 10 seconds for
binding, requests, handler decisions and abort cleanup. These configurable values
are library policy, not protocol-mandated timings; workload profiles may override them.

Bind authentication and message handlers return asynchronous typed decisions.
Both execute through separate bounded dispatchers outside transport read/write
progress and coordinator locks. The internal terminal
state must settle even when application notification is delayed. Bound dispatch
queues and concurrency; define overload results before accepting callback work.
Reserve capacity for control responses and shutdown.

The message service begins ordinary callbacks in receive order while allowing
asynchronous processing to overlap. Its response scheduler preserves peer-request
order, with bounded reply counts/bytes through active write settlement and handler
deadlines. Logical timeout or close retains physical capacity until invocation
and returned stage finish. A full transport queue retains the same head reply
and write budget for a later scan. Control replies use separate capacity so one slow message
cannot prevent an `enquire_link` response. Correlation accepts out-of-order peer
responses even though our default emission policy is ordered.

Applications must use an appropriate executor for expensive dependent future
actions and cooperate with cancellation. The library cannot forcibly stop
arbitrary application code. Document this limitation without blocking socket
cleanup or allowing callback work to grow without bound.

## Values, fields, and version handling

Public protocol values are immutable. Defensively copy mutable byte arrays and
collections at the public boundary; expose no mutable internal buffers. Decide
binary equality explicitly. Keep payload octets and `data_coding` independent of
text conversion; no implicit encoding or segmentation occurs during sending.

Preserve TLVs as an ordered collection of tag/value entries, including repeated
tags. A map would lose repeated broadcast areas and other permitted multiplicity.
Typed access applies only to supported command/profile contexts. Incoming unknown
or unexpected well-formed TLVs are retained as raw information and ignored
semantically; malformed lengths still fail validation.[^1][^2]

Require an explicit requested version and accepted-version set. The server
advertises its implemented version in successful bind responses. Keep requested
version, raw advertisement, effective profile, and usable TLVs separate.

| Client request / advertisement | Default library policy |
| --- | --- |
| 3.4 / 3.4 or 5.0 | Use the implemented 3.4 profile; do not enable 5.0 operations. |
| 5.0 / 5.0 | Use implemented 5.0 capabilities, subject to bind role. |
| 5.0 / 3.4 | Fail the requested-version requirement and close; no automatic rebind. |
| Either / missing | Retain the requested version as metadata; expose only common 3.4-compatible non-TLV operations/field values and reject sends requiring additional capabilities. |
| Either / unrecognized value | Preserve the advertisement and fail with an explicit unsupported-peer-version outcome. |

Missing advertisement does not prove full version support. A caller requiring
verified capabilities can configure binding to fail in that case. Compatibility
adjustments require named rules, scope, and test evidence; they never silently
change credentials or resubmit messages. The missing-TLV default follows the
specification's backward-compatibility guidance.[^1]

Validate outgoing data against profile, command, role, state, and configured bounds
before reserving network work. Decode incoming raw values independently from the
decision to invoke an application handler. Field and capability checks must not
be scattered across socket loops.

## Explicit message helpers

The `kg.aidarbek.smpp.message` package depends only on immutable protocol values
and JDK value/time/collection facilities. `TextEncoding.GSM7_UNPACKED` and `UCS2`
provide strict encode/decode/encoded-length operations. They do not choose a
provider coding convention, replace unsupported characters or pack GSM septets.
`MessageSegments`, `MessageSegment` and `ConcatenationHeader` expose explicit
SAR or octet-aligned concatenation metadata. `SegmentReassembler` uses caller
limits, namespace/reference identity and a monotonic clock; incomplete and completed
deduplication groups remain bounded until the fixed deadline or explicit close.

`DeliveryReceipts` requires EXAMPLE or FLEXIBLE interpretation and retains original
text plus raw fields. `ReceiptTlvs` retains the original parameter block and
independent optional ID/state/error views. No helper fabricates dates, delivery
state, ID normalization or receipt correlation. Applications still choose
`data_coding`, payload placement and provider semantics. See
[MESSAGE_HELPERS.md](MESSAGE_HELPERS.md) for exact limits and wire examples.

## Resource ownership and observability

Client owners track their connections; server owners track the listener and
accepted sessions. A session owns its connection and pending protocol state.
Closing a session does not close a shared application executor or its owner.
Internally created executors belong to the endpoint; supplied executors belong
to the caller unless ownership is explicitly transferred.

`shutdown(grace)` stops new work, drains outgoing requests and incoming message
replies within the deadline, unbinds eligible
sessions, and then closes connections. `close()` is the idempotent abort fallback:
stop I/O, settle internal pending outcomes, and request owned-executor shutdown.
It does not wait indefinitely for application code. A separate termination result
reports completed cleanup or the specific tasks that exceeded the shutdown bound.
`EndpointTermination.remainingHandlers()` includes physically unfinished message
callbacks/stages; `complete()` also requires their workers to terminate. See
[endpoint cleanup](ENDPOINTS.md) and [exchange cleanup](EXCHANGE.md) for bounded
notification and late-completion behavior.

Set explicit bounds for accepted and connecting sockets, requests, queued bytes,
frame size, handler work, reply buffering, and optional receipt/reassembly state.
The implemented default maximum accepted PDU is 1 MiB, configurable above the header
minimum; this is an allocation guard, not a change to protocol field limits.

Current endpoint APIs expose session metadata and bounded termination snapshots.
Additional metrics and observer contracts remain planned. Diagnostics include
operation, sequence, session, state and status where available.
Credentials and message bodies stay out of ordinary logs, exceptions, and generated
`toString()` output. Metrics must not require per-message retention.

## Responsibility and dependency review

```mermaid
flowchart TD
    Sim[Simulator application: planned] --> Api[Endpoint composition]
    Api --> Core[Connection coordinator]
    Api --> Transport[TCP adapter]
    Core --> Policies[Pure session policies]
    Core --> Requests[Request tracking]
    Core --> Ports[Frame transport ports]
    Transport --> Ports
    Transport --> Codec[Framing and body codecs]
    Core --> Codec
    Policies --> Profiles[Version profiles]
    Codec --> Profiles
    Profiles --> Values[Protocol values]
    Policies --> Values
    Requests --> Values
    Codec --> Values
    Core --> Auth[Bind authentication contract]
    Auth --> Values
    Core --> Handlers[Typed message handler contracts]
    Handlers --> Values
```

Composition constructs concrete adapters. The connection coordinator consumes
frame transport ports and combines the independent codec, session-policy and
request owners. Pure session policies decide permissions without codecs, clocks,
request maps or sockets. Request tracking owns correlation and deadlines without
session rules or byte parsing. Application implementations of handler contracts
remain outside the library. Dependencies never point back to simulator tooling.

| Responsibility | SOLID design obligation |
| --- | --- |
| Protocol values and field codecs | Keep value invariants and wire translation cohesive; exclude networking and application policy. |
| Profiles and codec dispatch | Add supported commands at explicit registration/profile boundaries; reject duplicate registrations. |
| State and request tracking | Separate permission decisions from correlation, deadlines, and terminal outcomes. |
| Transport and test adapters | Preserve ordering, ownership, failure, and close contracts across implementations. |
| Handler and capability interfaces | Serve actual consumers without unrelated mandatory callbacks or unsupported stubs. |
| Endpoint composition | Construct variable infrastructure at the boundary; keep session policies independent of concrete adapters. |
| Simulators | Separate schedules, response policies, counters, and report output; consume the public API. |

The diagram distinguishes library layers from the separate simulator application.
It does not establish SOLID compliance for future types. When code
arrives, review every affected type under [the SOLID policy](SOLID.md) and record
real [TDD evidence](TDD.md).

The first scenarios and acceptance evidence are in [the test plan](TEST_PLAN.md).
The first [TCP experiment](TRANSPORT.md) uses JDK sockets and Java 21 virtual
threads, with explicit TLS and [lifecycle policies](LIFECYCLE.md). External performance
targets and provider-specific exceptions remain open until supplied or measured.

## Sources

[^1]: SMS Forum. [SMPP 5.0](https://smpp.org/SMPP_v5.pdf), §§2.4–2.8, 2.11, 4.1.1.7, 4.7.24, and 4.8, 19 February 2003. Protocol facts inform the contracts; API names, deadlines, and resource policies are project decisions.
[^2]: SMPP Developers Forum. [SMPP 3.4, Issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf), §§2.3, 2.5–2.7, 3.3–3.4, 4.1.7, and 5, 12 October 1999.
