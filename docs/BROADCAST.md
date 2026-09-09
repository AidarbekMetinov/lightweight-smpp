# SMPP 5.0 broadcast and inventory completion

Step 16 completes the declared binary and endpoint scope for SMPP 5.0. Broadcast
submission, query and cancellation use immutable protocol values, bounded codecs,
typed optional application handlers and the existing request lifecycle. The same
catalogue still contains exactly 27 command IDs for 3.4 and 33 for 5.0. All 64
declared 5.0 TLV tags now have applicable structural interpretation and command
contexts. Independent implementation interoperability is separate evidence;
loopback endpoints and independently written byte fixtures do not establish it.

## Typed services and application ownership

`BoundSession.broadcast()`, `queryBroadcast()` and `cancelBroadcast()` return the
corresponding `OperationSender<Q, R>` only for a currently bound SMPP 5.0 ESME in
TX or TRX mode. The general `sender(BroadcastOperations.BROADCAST_SM)` form uses
the same descriptor. An MC receives these operations; it cannot originate them.
The advertised capability describes local sending permission, not the peer's
provisioned service. Retaining a sender does not bypass subsequent closure or
draining checks.

Register an MC service through the existing handler composition:

```java
EndpointHandlers handlers = EndpointHandlers.builder()
        .on(BroadcastOperations.BROADCAST_SM, request -> {
            BroadcastSm submitted = request.pdu().command();
            // The application decides acceptance, identity and durable storage.
            BroadcastSmResponse accepted = new BroadcastSmResponse(new MessageResponse(
                    Optional.of("mc-id"), new OptionalParameters(List.of())));
            return CompletableFuture.completedFuture(new HandlerResponse<>(0, accepted));
        })
        .build();
```

The query and cancellation keys have their own request and response types.
Applications supply storage, geographic selection, radio-network access,
replacement/group matching and outcome decisions. A successful local submission
response acknowledges the application-selected acceptance; it does not assert
that every base station or handset received the message. The library neither
persists submissions nor schedules repeated broadcasts.

Absent, failed or invalid application handlers produce the existing paired
`ESME_RSYSERR` (`0x00000008`) response with the canonical error body. Application
statuses, including unfamiliar unsigned values, remain intact. The existing
physical handler bounds, total deadlines, ordered replies, control capacity,
request cancellation and termination contracts also cover these operations.
Saturation follows [message exchange](EXCHANGE.md): a bounded negative response
is used when its reply reservation exists; otherwise the connection closes.
An unsupported 3.4 command receives an invalid-command nack; a known 5.0 command
in the wrong role or bind mode receives an invalid-bind-state nack. Unknown
commands do not acquire a fabricated typed request or a broadcast handler.

## Bodies, identities and limits

`BroadcastCommandCodecs.all()` supplies all six body codecs. Add that immutable
list to a `PduCodec` composition alongside the existing command families. Every
direct body call validates its status, explicit profile and configured bounds;
SMPP 3.4 rejects all six command identities in both directions.

| Command | Ordered standard body, before TLVs |
| --- | --- |
| `broadcast_sm` / `00000111` | service C6; source TON, NPI, C21; message ID C65; priority; scheduled time C17; validity C17; replacement; data coding; canned-message ID |
| `broadcast_sm_resp` / `80000111` | message ID C65 |
| `query_broadcast_sm` / `00000112` | message ID C65; source TON, NPI, C21 |
| `query_broadcast_sm_resp` / `80000112` | message ID C65 |
| `cancel_broadcast_sm` / `00000113` | service C6; message ID C65; source TON, NPI, C21 |
| `cancel_broadcast_sm_resp` / `80000113` | no standard body fields |

`C<n>` includes the terminating NUL and accepts only non-NUL ASCII content.
Message IDs are opaque, case-preserved strings of at most 64 characters. A
source address has at most 20 characters. Times are empty or the existing
16-character SMPP absolute/relative grammar plus terminator; no century,
timezone, calendar-to-duration conversion or normalization is inferred.
Incoming reserved TON/NPI/priority/replacement octets retain their raw value;
only their supported meaning participates in interpretation. Outgoing TON/NPI,
priority and replacement values must be supported. Data coding and canned-message
IDs preserve their unsigned octets; applications select the payload encoding and
canned-message catalogue.

CBS has no `esm_class`, `registered_delivery`, `sm_length` or `short_message`
field. Supplied binary payload uses `message_payload` (`0424`), whose value can
be empty or up to 65,535 bytes, subject to the configured frame/TLV byte and
count bounds. A canned-message reference is application policy; the codec does
not fetch or synthesize payload. All arrays are copied on input/output and raw
TLV order is retained. Aggregate outgoing TLV byte/count bounds are checked
before typed value interpretation. Incoming framing likewise establishes bounds
before TLV structure or semantics are inspected.

Outgoing MC message ID in `broadcast_sm` is used only with replacement flag 1.
Replacement requires exactly one of nonempty MC ID and `user_message_reference`
(`0204`); an ESME reference can also identify an initial submission. A query
requires exactly one of those identifiers. Cancellation accepts either, or
neither for an application-selected group, but never both. An irrelevant
incoming MC ID with a nonreplacement flag is retained rather than promoted to
replacement semantics; it cannot conflict with a supported initial-submission
user reference. This also applies when the incoming replacement flag is reserved.

Generic CBS priority supports 0..4. For content network GSM (1), priority 3 is
reserved; TDMA/CDMA (2/3) support 0..3. Priority 1 is immediate broadcast:
outgoing repetition TLV `0604` must be absent, and any incoming occurrence is
retained but ignored, even when its value does not match the usual two-byte
representation. Its raw TLV framing must still be well formed and bounded.

## Optional parameters and supported interpretation

The following sets are exact command contexts, not a global permission to send
any declared tag. Repeated entries retain their relative order.

| Context | Tags and occurrence |
| --- | --- |
| `broadcast_sm` | Required `0601`, `0604`, `0605`, repeatable `0606`; immediate priority removes `0604`. Optional `0005 000D 0019 0201 0202 0203 0204 020A 020B 020D 0302 0303 0381 0600 0602 0603 060A 1201 1203 1204 130C`. Callback tags `0302 0303 0381` repeat; all other optional tags and `0424` payload are singleton. |
| `broadcast_sm_resp` | Repeatable failed areas `0606` and corresponding errors `0607`; singleton `0428` congestion. |
| `query_broadcast_sm` | Optional singleton `0204`. |
| `query_broadcast_sm_resp` | Required singleton `0427` state, repeated `0606` areas and `0608` success; optional singleton `0609` end time, `0204` reference and `0428` congestion. |
| `cancel_broadcast_sm` | Optional singleton `0601` content type and `0204` reference. |
| `cancel_broadcast_sm_resp` | Optional singleton `0428` congestion. |

The broadcast-specific value rules are:

| Tag | Value structure and supported interpretation |
| --- | --- |
| `0600` channel | One byte: 0 basic, 1 extended. |
| `0601` content type | Three bytes: network selector and unsigned 16-bit type. Selectors 0..3 are supported. Generic selector 0 supports the explicitly listed categories `0000..0002`, `0010..0023`, `0030..0039`, `0040..0041`, `0070..0071`, `0080..0085`, `0100`. Network-specific type octets remain opaque. |
| `0602` content information | 1..255 opaque bytes. |
| `0603` message class | One byte: literal table values 0, 2 or 3; see ambiguity below. |
| `0604` repetition | Unsigned two-byte count; 0 has the specification's indefinite/implicit interpretation, decided with the supplied validity and frequency by the application. |
| `0605` frequency | Three bytes: unit 0 (as frequently as possible), or 8..14 (seconds through years), followed by an unsigned 16-bit count. No conversion of months/years to `Duration`. |
| `0606` area / failed area | 1..101 bytes: selector 0 named alias, 1 ellipsoid or 2 polygon, followed by at most 100 opaque area-format bytes. Geographic interpretation and validity in a particular network remain application policy. |
| `0607` error status | Four-byte unsigned SMPP status, retained without a closed enum of known statuses. |
| `0608` area success | One byte: 0..100 percent, or 255 when unavailable. |
| `0609` end time | Exactly 17 bytes: the 16-character absolute SMPP time followed by NUL. Relative or malformed times fail structural validation. |
| `060A` service group | 1..255 opaque bytes. |
| `0427` in a CBS query | One byte: defined states 0..9 except `DELIVERED` (2), which the CBS footnote excludes. |

Known applicable malformed lengths/structures fail. Well-formed unknown tags,
unexpected known tags and reserved incoming discriminants retain raw bytes but
have no supported typed meaning. `BroadcastCommandCodecs.tlvRegistry()` exposes
the distinction through `Optional<OctetString>`; it performs no text, geographic
or scheduling conversion. Outgoing unsupported values/tags fail. Explicit
`BroadcastTlvExtension` registrations can permit vendor-range `1400..3FFF` tags
for one exact 5.0 command, with explicit multiplicity and an immutable/thread-safe
value codec. Such standalone codec composition does not enable a vendor service
or bypass endpoint capability policy.

Shared callback, subaddress, port, payload, validity, language, privacy and signal
rules use the existing [message interpretations](MESSAGES.md), including callback
mode/TON/NPI and UCS-2 code-unit structure. Callback presentation/alpha-tag
occurrences, when supplied, must match their ordered callback numbers. Per-area
success occurrences match query areas. Supplied error statuses match failed
areas; failed areas can appear without detailed statuses. Unsupported incoming
semantic entries are ignored when a companion comparison would otherwise
misinterpret a future value. A supported active state (scheduled/enroute) cannot
have a broadcast end time.

`BroadcastResponseRules.validate` adds checks that need the original paired
request after both body codecs pass: MC query IDs are echoed case-sensitively,
supplied user references agree, and failed area occurrences form a multiset
subset of the submitted areas. The endpoint calls this validator before
accepting the response; sequence/generation matching stays with its request
owner. `generic_nack` follows the separate protocol-error path.
`validateQueryResult(originalBroadcast, queryResult)` can additionally compare
all query areas to a separately stored original submission, including
multiplicity and allowing order changes. The library does not retain that
submission automatically. Area comparisons deliberately skip unsupported area
formats rather than inventing geographic equality.

## Error bodies and source ambiguities

The baseline applies SMPP 5.0 §3.2.1.3 to all three failed responses: after the
bounded frame is established, incoming nonzero-status response bodies are
ignored; outgoing failed responses are canonical header-only. A
`MessageResponse` with `Optional.empty()` represents omission, while
`Optional.of("")` represents an encoded empty C-octet message ID on a successful
response. Failed responses cannot encode a message ID or TLVs. This deliberately
retains the same policy as the existing 5.0 message/common operations.

The specific broadcast diagnostic-TLV sections describe failed-area/error
information while the general error-body rule omits it on a failed operation.
The supported baseline permits structured per-area details on a successful
operation response and ignores failed-operation bodies. It does not encode
diagnostic TLVs on nonzero-status responses or claim compatibility with peers
that require those bodies. No provider-specific exception is enabled silently.

The primary [SMPP 5.0 specification](https://smpp.org/SMPP_v5.pdf) contains these
additional inconsistencies; independent fixtures fix the chosen interpretation:

| Source | Explicit baseline interpretation |
| --- | --- |
| Query response table 4-39 labels `message_id` as Integer but references §4.7.14. | Use the referenced C-octet grammar and maximum 65 bytes, as the request and broadcast response do. |
| Area format §4.8.4.4.1 says the format-specific data has maximum 100 bytes. | Permit a separate selector plus that data: total 1..101 bytes. Geometry remains opaque. |
| End-time table 4-70 says 16 bytes but specifies C-octet absolute time and references §4.7.23.4. | Encode the full 16-character grammar plus required NUL: 17 bytes. |
| Message-class table 4-73 assigns `02` to both Class 1 and Class 2. | Support the literal distinct values 0, 2, 3. Do not invent a Class 1 mapping to 1; incoming 1 remains raw and uninterpreted. |
| State table footnote 8 excludes `DELIVERED` from CBS. | Reject outgoing CBS state 2 while ordinary delivery receipts continue to support it; retain incoming 2 without interpreting it as a CBS outcome. |
| Cancellation prose mentions destination matching, but table 4-41 has no destination field. | Follow the actual service/message/source layout; the application decides group matching. |

These choices are part of the explicit supported interpretation. They are not
claims about every deployed CBC or undocumented provider convention.

## Congestion observation and the complete inventory

`BoundSession.congestion()` returns the last `CongestionObservation`: exact level
0..100, response command ID, sequence and monotonic observation time. It changes
only after body/profile/context validation and successful matching of a live
request. Bind, normal operation, control and matched nack responses use this
same rule. Duplicate, late, cancelled, expired, wrong-command, unmatched or
contextually invalid responses cannot replace it. Missing/reserved values or
3.4 input leave the previous sample intact. An invalid frame still follows its
normal protocol-error policy; it does not supply a sample.

The snapshot is immutable and safe to read after request completion. Elapsed
time uses subtraction on the same monotonic clock basis; it is not an epoch
timestamp. The library does not infer a rate, change the request window, retry
or reconnect from this feedback. An application may compose its own explicit
admission/rate policy with this observation.

The inventory reconciliation covers earlier 5.0 changes as well as new CBS
code. The disjoint groups below account for every declared tag:

| Group | Complete tag set | Structural/context evidence |
| --- | --- | --- |
| Bind version | `0210` | `UnsignedByteTlvCodecTest`, `TypedTlvRegistryTest`, bind fixtures and version-negotiation/live endpoint matrix. |
| Availability | `0422` | Common TLV/value fixtures and live one-way alert tests. |
| Message/shared parameters (51) | `0005 0006 0007 0008 000D 000E 000F 0010 0017 0019 001D 001E 0030 0201 0202 0203 0204 0205 020A 020B 020C 020D 020E 020F 0302 0303 0304 0381 0420 0421 0423 0424 0425 0426 0427 0428 0501 060B 060D 060E 060F 0610 0611 0612 0613 1201 1203 1204 130C 1380 1383` | Independent per-value fixtures for all 51 in `MessageTlvValueCodecTest`, exact profile/direction contexts, message/common companion tests and live services. |
| Broadcast parameters (11) | `0600 0601 0602 0603 0604 0605 0606 0607 0608 0609 060A` | `BroadcastTlvValueCodecTest`, exact occurrence tables, body and original-request response rules, live/raw peers and simulator adapter. |

`Smpp5InventoryTest` asserts equality of the composed codec/tag contexts to the
declared profile catalogues and the exact 27/33-command and 44/64-tag counts.
Those set assertions supplement independently derived value/byte tests; they do
not substitute for them. `Smpp5ServicesTest` also sends actual 5.0 success-only
receipt requests, scheduled/intermediate and skipped outcomes, billing data,
source/destination network and node IDs, and number-portability companions
through both application roles. Explicit `ReceiptTlvs` helpers preserve raw
receipt fields. No automatic receipt or billing/routing service is implied.
The preexisting per-profile tests retain changed `ms_validity`,
`alert_on_message_delivery`, message-state and field/payload definitions.

All declared codec and role-aware endpoint services are implemented under these
explicit policies. External account storage, billing, routing, geographic
interpretation, scheduling and radio delivery remain application collaborators,
not unfinished binary validation. Provider interoperability is recorded
separately in Step 19.

## Simulator scenarios and verification

`BroadcastTraffic` provides `broadcast`, `query-broadcast` and `cancel-broadcast`
for a 5.0 client in TX/TRX mode. Invalid version/role/mode fails before endpoint
creation. The server fixture registers all three handlers through the same
bounded `ReplyController` as ordinary traffic; configured delay/rejection/
disconnect behavior uses that existing seam. Broadcast payload extraction runs
inside its counted validation supplier, so malformed content cannot escape
received/invalid-content accounting.

The raw scenario uses DCS 4 payload in one `0424` TLV, two named areas `sim-a` and
`sim-b`, generic news content, repetition 1 and a one-minute interval. The
synthetic query returns state 9 (skipped), area success 100 and 255, and successful
responses advertise congestion 80. The fixture has no persistent broadcast
store, geographic expansion or radio service. It does not infer point-to-point
receipt/UDHI flags where CBS has no `esm_class`.

`BroadcastTrafficTest` exercises all three operations and both successful and
negative application responses over real local TCP, verifies the congestion
observation, and awaits endpoint shutdown. Its payload tests include empty,
254/255/256 and 65,535 bytes. `BroadcastCommandCodecsTest` independently fixes all
six full wire PDUs. Body/field/TLV tests cover ownership, truncation, malformed
strings and structures, required/repeated/singleton rules, bounds and extensions.
`BroadcastEndpointTest`, `BroadcastPeerTest` and `EndpointCongestionTest` cover
the profile/role/mode matrix, absent handlers, malformed and unsupported input,
application failures and response-correlation races.

The [Step 16 review](reviews/0015-smpp5.md) records actual red/green commands,
formatting, per-type reviews and final validation. Deterministic test execution
can use the normal build caches; these functional checks make no throughput or
heavy-load claim.
