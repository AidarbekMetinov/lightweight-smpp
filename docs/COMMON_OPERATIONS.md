# Common operations

`CommonCommandCodecs.all()` composes `query_sm`, `cancel_sm`, `replace_sm`,
`submit_multi`, their four concrete responses, `outbind`, and
`alert_notification`. The values are immutable and retain raw payload octets,
ordered optional parameters, and per-destination results. These codecs use an
explicit `ProtocolProfile`; they neither negotiate a version nor implement
message storage, delivery, cancellation matching, or distribution-list expansion.

| Operation | Application responsibility | Binary contract |
| --- | --- | --- |
| Query | Find the original message and report its known state and completion time. | Message ID up to 64 ASCII characters, source address up to 20; response state/error octets and nullable absolute completion time. |
| Cancel | Match the ID, or service/source/destination selection when the ID is empty; decide whether cancellation remains possible. | C-octet identifiers and addresses; header-only standard response. |
| Replace | Find a replaceable queued message and apply supplied fields. Empty schedule/validity retains the original settings. | Raw replacement bytes, with a 5.0 payload TLV alternative; no invented data-coding field. |
| Multiple submission | Expand distribution lists, submit each destination, return unsuccessful SME addresses and raw status values. | Ordered SME/distribution-list alternatives and an ordered response failure list; no implicit expansion or deduplication. |
| Alert | Interpret the source/ESME addresses and supported availability status. | One-way notification; both addresses allow up to 64 ASCII characters. |
| Outbind | Authenticate the MC notification and decide whether to perform the configured bind on that connection. | System ID/password only; no version, bind mode, or response command in the notification. |

The source tables are SMPP 3.4 §§4.1.7, 4.5, 4.8–4.10, 4.12 and SMPP 5.0
§§4.1.1.7, 4.1.3, 4.2.3–4.2.5, 4.5.1–4.5.3. See the original
[3.4 specification](https://smpp.org/SMPP_v3_4_Issue1_2.pdf) and
[5.0 specification](https://smpp.org/SMPP_v5.pdf).

## Profile and compatibility choices

Short replacement/multiple-submission payloads and destination counts allow
0–254 payload bytes / 1–254 destinations in 3.4, and 0–255 / 1–255 in 5.0.
The counted unsuccessful response list allows 0–255 entries in either profile.
An expanded distribution list can produce more failed SME addresses than the
number of original destination alternatives. The codec does not impose an
unsupported count or membership relationship between those lists.

SMPP 3.4 reserves `submit_multi.replace_if_present_flag` and excludes transaction
message mode. Outgoing 3.4 multiple submissions require replacement flag zero
and a non-transaction mode; 5.0 supports transaction mode and replacement flag
0 or 1. The predefined-message index 255 and registered-delivery value 3 require
5.0. Reserved incoming numeric values stay available as raw octets; applications
must ignore their unsupported semantics. The outgoing profile checks reject
unsupported values. Malformed strings, impossible counted layouts, and truncated
values fail independently of compatibility handling.

Query state 0 (scheduled) and 9 (skipped) are defined in 5.0. A non-final known
state requires an empty `final_date`; a supplied final date must use the absolute
16-character SMPP time grammar. Empty dates remain accepted for known final
states and UNKNOWN because the specifications do not universally mandate a
reported completion timestamp. The library does not invent a date, infer a
century or timezone, or normalize a raw date. Replacement and multiple-submission
times use the shared [message time interpretation](MESSAGES.md).

The 3.4 query and multiple-submission response tables contain no error-body
omission note: their standard bodies are required on success and failure. In
5.0, §3.2.1.3 requires ignored incoming failed-response bodies and canonical
header-only outgoing failures. `QuerySmResponse` and `SubmitMultiResponse`
therefore distinguish an absent body from an empty identifier in a present body.
Cancellation and replacement standard responses have no standard body in either
profile. 5.0 adds supported congestion feedback on successful responses.

## Optional parameters and request context

`CommonTlvRules` records exact operation/profile sets. `tlvRegistry()` exposes
the supported raw-octet interpretation separately from raw retention. Known
permitted values receive structural validation; well-formed unexpected tags and
reserved values remain in the immutable raw parameters without acquiring
supported semantics. Outgoing unsupported values and tags are rejected. TLV byte
and count bounds are checked before outgoing value interpretation.

Vendor outgoing values require `CommonTlvExtension` and an explicit
`CommonCommandCodecs.all(extensions)` composition. Each declaration names one
command/profile and a vendor-range tag; it cannot replace a standard tag's rules.
The codec must satisfy the shared value-codec ownership contract. Registration
does not enable vendor permission in the standard endpoint configuration or
advertise any vendor business behavior.

The 3.4 multiple-submission table is narrower than `submit_sm`. Both versions
explicitly prohibit `dest_subaddress` for `submit_multi` in its tag definition
(3.4 §5.3.2.16; 5.0 §4.8.4.28), despite its inclusion in a broader submission
table. The codec follows the explicit prohibition. Repeated callback numbers and
matching companion lists retain their order; SAR, UDHI/ports, network/node, and
number-portability companions use the established message contracts. Incomplete
incoming SAR and reserved unsupported values are ignored semantically.

Only 5.0 permits `message_payload` on replacement. Outgoing replacement and
multiple-submission commands choose one payload location. Incoming 5.0 preserves
superseded nonempty `short_message` bytes alongside `message_payload`; incoming
3.4 replacement treats its unexpected payload TLV as uninterpreted raw data.

`CommonResponseRules.validate(request, response, profile)` checks an echoed
query ID and 5.0 transaction-only multiple-submission response diagnostics after
both body codecs have succeeded. Tracking separately checks sequence identity
and deadlines. A matched `generic_nack` follows the error path and skips this
concrete-response validator. The 5.0 failed-response omission policy prevents
encoding failed transaction diagnostic TLVs; a provider compatibility rule is
required before claiming that specific diagnostic interchange. No successful
delivery or downstream failure is inferred from missing application context.

## Typed session services

`CommonOperations.QUERY_SM`, `CANCEL_SM`, `REPLACE_SM`, and `SUBMIT_MULTI` are
paired operation keys. Register only the services an application supplies:

```java
EndpointHandlers handlers = EndpointHandlers.builder()
    .on(CommonOperations.QUERY_SM, request -> application.query(request))
    .on(CommonOperations.SUBMIT_MULTI, request -> application.submitMultiple(request))
    .build();
```

The application methods return `CompletionStage<HandlerResponse<R>>` for their
specific response type. They implement actual message storage and selection.
The library supplies typed immutable requests, the negotiated session, a total
handler deadline and cancellation observation. Missing services receive a
bounded negative reply, with the correct 3.4 or 5.0 error body. Invalid handler
results use the same profile-correct fallback. Handler concurrency, queued work,
reply count/bytes and physical stage retention follow [message exchange](EXCHANGE.md)
and the endpoint's `ExchangeConfig`.

`BoundSession.query()`, `cancel()`, `replace()`, and `multipleSubmission()` return
optional typed senders. A retained sender rechecks the current lifecycle on every
call. Query, cancel and multi require an ESME TX or TRX session in both profiles.
Replacement requires ESME TX in 3.4, and ESME TX or TRX in 5.0. An available local
sender does not imply that the peer implements the associated business service.
Every paired operation uses the existing bounded request window, invocation
deadline, cancellation, generation/sequence matching and protected result stage.

## One-way alerts

A bound MC RX or TRX session exposes `alerts()`. Register an optional ESME
`EndpointHandlers.Builder.onAlert(NotificationHandler<AlertNotification>)` hook
for incoming availability notifications. An absent hook ignores a valid alert.
No alert response command exists, and no pending response entry is created.

`AlertSender.send(...)` returns `NotificationSend`: its sequence is allocated
from the same non-reused local sequence allocator as paired requests, its
`result()` means **local physical write settlement**, and `cancel()` returns true
only if it prevents physical transmission. A guard that has already permitted
writing makes cancellation return false. A received generic nack cannot consume
a different request because notification identities are never reused.

Outgoing ordinary transport count/byte limits include queued and active alert
writes. Notification callback reservations are separately bounded and remain
occupied while synchronous result dependents execute. The total write deadline
starts at API invocation, includes validation/admission/queueing, and uses the
same monotonic basis as other operations. A queued expiry prevents writing;
expiry after physical writing has begun closes the connection because a partial
frame cannot safely be continued. Immediate lifecycle/validation/callback-capacity
failures reject the API invocation; a transport rejection after sequence admission
settles the returned local result exceptionally.

Incoming alert hooks use the same physical `HandlerDispatcher` capacity and
per-session invocation lane as paired message hooks. Invocation order is retained
across those hook kinds; independent returned asynchronous decisions may overlap.
The handler timeout includes invocation queue time. A failed, expired or overloaded
registered alert hook closes the connection; there is no invented response or
silent unbounded application queue. Logical cancellation never frees a still
executing invocation or non-cooperating returned stage. Endpoint termination
reports that retained work through `remainingHandlers`. Graceful shutdown rejects
new alerts and drains accepted local writes/hooks within the endpoint bound.

## Explicit outbind ownership

`OutbindListener` is an ESME TCP listener. Its `OutbindListenerConfig` supplies a
resolved listen address and a fixed `BindRequest`. After exactly one incoming MC
notification, a required `OutbindAuthenticator` checks its immutable credentials
and peer identity on bounded off-I/O handler workers. Only a positive decision
within the total bind deadline sends the configured bind on the accepted socket.
Rejection, duplicate notification, malformed input, cancellation or expiry closes
that socket. ESME credentials are not sent before successful MC authentication.
Physical authentication invocation/stage capacity shares the handler pool and
remains retained after logical expiry until the application work ends.

`OutbindConnector` is an MC owner. Each explicit
`connectAttempt(resolvedAddress, outbind)` opens at most one TCP connection,
sends one notification, and authenticates the follow-up ESME bind through the
existing bounded `BindAuthenticator`. Its `ConnectionAttempt` completes after
successful binding, supports cancellation before binding completes, and exposes
rejected follow-up bind status. DNS resolution belongs to the caller. Both owners
preflight outgoing wire configuration before opening/admitting the relevant
socket. They expose sessions, current connection reservations, bounded shutdown
and independent endpoint termination. A `connectAttempt` performs one connection
without retries. The optional `OutbindConnector.reconnect` method explicitly
owns a bounded sequence of fresh attempts under [the lifecycle policy](LIFECYCLE.md),
without replaying messages. A matched negative `generic_nack` for the still-pending
outbind flow closes that attempt without creating a response-window entry.

SMPP 3.4 §§2.2.1 and 4.1.7 explicitly describe a receiver bind after outbind; both
the listener configuration and connecting MC enforce that restriction. SMPP 5.0
§2.3.7 presents receiver binding as an example, and its OUTBOUND operation matrix
allows the three bind commands; explicitly configured RX, TX and TRX are supported.
The wording “originate a outbind request” in 5.0 §4.1.1.7 is interpreted as the
follow-up bind, consistently with the preceding workflow and state tables. No
version or requested mode is inferred from the outbind body itself.

## Simulator common-operation coverage

The separate simulator adapters provide `query`, `cancel`, `replace`, and `multi`
operations through the public typed senders and deterministic `ReplyController`.
Role/mode/profile checks happen before a request attempt. Query/cancel carry no
message content; their identifiers are deterministic synthetic values. Multi
contains one configured SME and one `sim-list` distribution-list alternative;
the simulator does not claim delivery or actual list expansion. Positive and
negative query/multi responses preserve their different 3.4/5.0 body contracts.
Receiver extraction and validation run inside the counted, bounded stream decision.

Replacement traffic explicitly assumes an existing unsegmented binary message
with original DCS 4 and esm_class 0. Those fields do not exist in `replace_sm`,
so incompatible text/segmentation metadata is rejected rather than discarded.
The binary short field allows 254 bytes in 3.4 and 255 in 5.0; larger 5.0 replacement
content uses `message_payload`. This is a simulator assumption about a synthetic
original message, not a wire claim or a storage implementation.

One-way operations have separate finite executable checks rather than fictitious
request pacing. After `:simulator:installDist`, run, for example:

```sh
java -cp 'simulator/build/install/simulator/lib/*' kg.aidarbek.simulator.CommonOperationSmoke alert 3.4
java -cp 'simulator/build/install/simulator/lib/*' kg.aidarbek.simulator.CommonOperationSmoke outbind 5.0
```

Each invocation uses real loopback endpoints, demo-only local credentials, exactly
one notification, observed binding/authentication counts, and bounded physical
cleanup. These checks establish finite end-to-end behavior; they make no
heavy-load, capacity, or production-latency claim.
