# Protocol support inventory

Step 1 baseline, updated after Step 17. Bind/control codecs have
[Step 6 evidence](reviews/0005-session-command-codecs.md), and basic message codecs
have [Step 7 evidence](reviews/0006-message-codecs.md) for both profiles.
The shared [framing](reviews/0002-pdu-framing.md) and
[field/profile](reviews/0004-fields-profiles.md) evidence establishes their binary
foundation. [Step 8](reviews/0007-session-state.md) adds deterministic state and
permission evidence described separately below. [Step 9](reviews/0008-request-tracking.md)
adds general correlation, deadlines and bounded terminal notification.
[Step 10 transport](TRANSPORT.md) adds framed TCP I/O and bounded write/close
contracts with local peers. [Step 11 endpoints](ENDPOINTS.md) add real binding,
authentication, manual controls, request ownership and bounded cleanup for both
profiles. [Step 12 exchange](EXCHANGE.md) adds submission, delivery and data
senders/handlers, ordered reply bounds and handler cleanup. [Step 13](SIMULATORS.md)
adds runnable simulators. [Step 14](COMMON_OPERATIONS.md) adds common-operation
codecs/services, one-way alerts and authenticated outbind. [Step 16](BROADCAST.md)
adds all three broadcast services and completes the 5.0 field/TLV inventory,
including matched-response congestion observation. [Step 17](LIFECYCLE.md) adds
scheduled keepalives and explicit secure connection lifecycle. Independent-peer
results remain **planned**. This inventory complements the specifications.

`C` means our ESME client and `S` our message-center server. `TX`, `RX`, and `TRX`
are SMPP bind modes, independent of TCP connection direction. `B` means any bound
mode. In each operation row, the response travels opposite to the request.
Response permissions also require a corresponding request or protocol error.

The role rules and wire identifiers reference SMPP 3.4 §2.3/§4/§5 and SMPP 5.0
§2.4/§4.[^1][^2] The chosen default policies are described in [API.md](API.md).

## Operation inventory

Each row has a planned test identity `OP-<request name>`. That identity covers the
request, response when present, both profiles where applicable, and both of our
endpoint implementations. `Session` means the full endpoint contract, including
correlation, callbacks, deadlines and cleanup. The Step 8 policy subset is recorded
separately below. A linked subset names its implemented scope; `—` means no
evidence for that column. `Independent` requires a separately implemented SMPP
peer with pinned version/configuration; our raw byte fixtures are separate test
evidence and do not establish that claim.

| Operation | Request / response ID (hex) | Origin and bind permission | Versions | 3.4 / 5.0 sections | Step | Codec | Session | Independent |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `bind_receiver` | `00000001` / `80000001` | C in OPEN; S responds | 3.4, 5.0 | 4.1.3–4 / 4.1.1.3–4 | 6, 11 | [Step 6](reviews/0005-session-command-codecs.md) | [Step 11](reviews/0010-client-server-binding.md) | — |
| `bind_transmitter` | `00000002` / `80000002` | C in OPEN; S responds | 3.4, 5.0 | 4.1.1–2 / 4.1.1.1–2 | 6, 11 | [Step 6](reviews/0005-session-command-codecs.md) | [Step 11](reviews/0010-client-server-binding.md) | — |
| `bind_transceiver` | `00000009` / `80000009` | C in OPEN; S responds | 3.4, 5.0 | 4.1.5–6 / 4.1.1.5–6 | 6, 11 | [Step 6](reviews/0005-session-command-codecs.md) | [Step 11](reviews/0010-client-server-binding.md) | — |
| `outbind` | `0000000B` / none | S in OPEN; C receives and initiates bind | 3.4, 5.0 | 4.1.7 / 4.1.1.7 | 14 | [Step 14](reviews/0013-common-operations.md) | [Step 14](COMMON_OPERATIONS.md) | — |
| `unbind` | `00000006` / `80000006` | C or S in B | 3.4, 5.0 | 4.2 / 4.1.1.8–9 | 6, 11 | [Step 6](reviews/0005-session-command-codecs.md) | [Step 11](reviews/0010-client-server-binding.md) | — |
| `enquire_link` | `00000015` / `80000015` | C or S; see control-state note | 3.4, 5.0 | 4.11 / 4.1.2 | 6, 11, 17 | [Step 6](reviews/0005-session-command-codecs.md) | [Manual controls](ENDPOINTS.md) and [automatic keepalives](LIFECYCLE.md) | — |
| `generic_nack` | none / `80000000` | C or S as error response | 3.4, 5.0 | 4.3 / 4.1.4 | 6, 8, 11 | [Step 6](reviews/0005-session-command-codecs.md) | [Step 11 error paths](ENDPOINTS.md) | — |
| `submit_sm` | `00000004` / `80000004` | C in TX or TRX | 3.4, 5.0 | 4.4 / 4.2.1 | 7, 12 | [Step 7](reviews/0006-message-codecs.md) | [Step 12](reviews/0011-message-exchange.md) | — |
| `deliver_sm` | `00000005` / `80000005` | S in RX or TRX | 3.4, 5.0 | 4.6 / 4.3.1 | 7, 12 | [Step 7](reviews/0006-message-codecs.md) | [Step 12](reviews/0011-message-exchange.md) | — |
| `data_sm` | `00000103` / `80000103` | See version-specific note | 3.4, 5.0 | 4.7 / 4.2.2, 4.3.2 | 7, 12 | [Step 7](reviews/0006-message-codecs.md) | [Step 12](reviews/0011-message-exchange.md) | — |
| `query_sm` | `00000003` / `80000003` | C in TX or TRX | 3.4, 5.0 | 4.8 / 4.5.2 | 14 | [Step 14](reviews/0013-common-operations.md) | [Step 14](COMMON_OPERATIONS.md) | — |
| `cancel_sm` | `00000008` / `80000008` | C in TX or TRX | 3.4, 5.0 | 4.9 / 4.5.1 | 14 | [Step 14](reviews/0013-common-operations.md) | [Step 14](COMMON_OPERATIONS.md) | — |
| `replace_sm` | `00000007` / `80000007` | C in TX; TRX additionally in 5.0 | 3.4, 5.0 | 4.10 / 4.5.3 | 14 | [Step 14](reviews/0013-common-operations.md) | [Step 14](COMMON_OPERATIONS.md) | — |
| `submit_multi` | `00000021` / `80000021` | C in TX or TRX | 3.4, 5.0 | 4.5 / 4.2.3 | 14 | [Step 14](reviews/0013-common-operations.md) | [Step 14](COMMON_OPERATIONS.md) | — |
| `alert_notification` | `00000102` / none | S in RX or TRX | 3.4, 5.0 | 4.12 / 4.1.3 | 14 | [Step 14](reviews/0013-common-operations.md) | [Step 14](COMMON_OPERATIONS.md) | — |
| `broadcast_sm` | `00000111` / `80000111` | C in TX or TRX | 5.0 | — / 4.4.1 | 16 | [Step 16](reviews/0015-smpp5.md) | [Step 16](BROADCAST.md) | — |
| `query_broadcast_sm` | `00000112` / `80000112` | C in TX or TRX | 5.0 | — / 4.6.1 | 16 | [Step 16](reviews/0015-smpp5.md) | [Step 16](BROADCAST.md) | — |
| `cancel_broadcast_sm` | `00000113` / `80000113` | C in TX or TRX | 5.0 | — / 4.6.2 | 16 | [Step 16](reviews/0015-smpp5.md) | [Step 16](BROADCAST.md) | — |

There are 27 distinct command identifiers in the 3.4 inventory and 33 in 5.0,
counting each request and response separately. `generic_nack` has no request
counterpart; `outbind` and `alert_notification` have no response counterpart.
Names in this table use the command definitions: the 3.4 summary's
`submit_sm_multi` spelling is not an additional operation.

### Version-specific permissions and source inconsistencies

- The 3.4 §2.3 table permits `data_sm` from either endpoint in TX, RX, or TRX.
  The 5.0 §2.4 table restricts C-originated data to TX/TRX and S-originated data
  to RX/TRX. Preserve that distinction in profile tests.
- The 3.4 table lists `replace_sm` only for TX, while 5.0 includes TRX. Follow
  these tables in the baseline. A verified provider exception needs an explicit
  compatibility rule, not an undocumented use of the 5.0 table in a 3.4 session.
- The 5.0 matrix allows enquiry/error traffic in OPEN, OUTBOUND, bound, and
  UNBOUND states; 3.4's summary lists it in bound states. Scheduled keepalives
  start only after binding. Protocol-error responses before binding still need
  the command/error procedure, not a blanket prohibition on responding.
- OUTBOUND in the 5.0 state model permits explicitly configured RX/TX/TRX
  follow-up binds. The 3.4 outbind text explicitly requires receiver binding.
  Both use the existing outbind connection; no application message is allowed
  until binding completes.
- The 3.4 `data_sm` prose mentions `short_message`, although its wire syntax uses
  payload TLVs. Follow the actual PDU field layout. Likewise, the 5.0 query
  broadcast response table labels `message_id` as Integer but references its
  C-octet definition in §4.7.14; use the field definition and verify independent
  bytes. Record these decisions in the corresponding fixture reviews.

These observations concern the cited document editions. They are not claims that
every deployed peer follows the same interpretation.[^1][^2]

## Executed state-policy evidence

[Step 8](reviews/0007-session-state.md) verifies the role/state subset independently
of byte parsing or networking. These cases run for both roles and both profiles:

| Scope | Executed policy evidence | Live integration and remaining scope |
| --- | --- | --- |
| All catalogue request permissions | `SessionPermissionsTest` enumerates defined requests, modeled states and originating roles, including data/replace/enquiry version differences. | Steps 12/14/16 implement all declared message, common, one-way and broadcast services; live broadcast tests include every profile/role/mode and unavailable-handler outcomes. |
| Bind RX/TX/TRX and version policy | `SessionStateMachineTest` and `VersionNegotiationTest` cover both endpoints, accepted/rejected/duplicate binds, raw advertisements and restricted field requirements. | Step 11 adds authentication, actual writes, connection/bind deadlines and cleanup for all bind modes and both profiles. |
| Unbind and closure | `SessionStateMachineTest` covers either initiator, crossed equal sequences, draining state and repeated close. | Steps 9–11 add request settlement, bounded reply flush and socket shutdown; messaging drain and later lifecycle features remain scoped to their steps. |
| Ordinary responses and protocol errors | `SessionResponsePermissionTest` covers paired commands/directions/sequences, explicit correlation context, negative replies and invalid-header nack sequences. | Steps 9–11 integrate generation/late-response ownership and exactly-once completion for binding/control traffic. Step 12 applies the same ownership to typed message senders and handler replies. |

Implementation declarations are explicit inputs. A permitted catalogue operation
does not imply a local codec or service exists. [Session contracts](SESSIONS.md)
define this boundary and composition with the implemented codecs. No full
`Session` or `Independent` table cell is completed by state-policy tests alone.
Step 11 session evidence is recorded separately in its [endpoint review](reviews/0010-client-server-binding.md);
Step 12 message lifecycle evidence is in the [exchange review](reviews/0011-message-exchange.md).
Manual and optional automatic enquiries are implemented. The complete declared
5.0 command inventory has the separate codec and endpoint evidence above;
binding alone does not establish command or remote application-service support.

## Header and standard-field inventory

Both profiles include these field identities. A field's occurrence, length,
meaning, and reserved values still depend on its enclosing command. The test
identity for each field is `FIELD-<name>`; grouped aliases each need their own
applicable cases. Raw header values/translation and generic bounded unsigned,
ASCII C-octet, and raw-octet primitives are verified. Bind/control fields have
[command evidence](COMMANDS.md); submit/deliver/data fields, time grammar, flags,
payload alternatives and response-body rules have [message evidence](MESSAGES.md).
[Common](COMMON_OPERATIONS.md) and [broadcast](BROADCAST.md) contracts complete
the remaining standard bodies. See [field contracts](FIELDS.md).

| Field or related aliases | 3.4 section | 5.0 section | Planned validation focus |
| --- | --- | --- | --- |
| `command_length` | 5.1.1 | 4.7.4 | Header-inclusive length, allocation bound, truncated frame. |
| `command_id` | 5.1.2 | 4.7.5 | Unsigned bits, registry identity, unknown command. |
| `command_status` | 5.1.3 | 4.7.6 | Request zero, response status, unknown numeric value. |
| `sequence_number` | 5.1.4 | 4.7.24 | Valid request range, echoed response, invalid-header nack exception. |
| `system_id` | 5.2.1 | 4.7.30 | Bind and outbind limits; redacted credential context. |
| `password` | 5.2.2 | 4.7.18 | Termination, byte length, no diagnostic exposure. |
| `system_type` | 5.2.3 | 4.7.31 | Empty and maximum-length values. |
| `interface_version` | 5.2.4 | 4.7.13 | Explicit 3.4/5.0 values, missing advertisement handled separately. |
| `addr_ton`, `source_addr_ton`, `dest_addr_ton`, `esme_addr_ton` | 5.2.5 | 4.7.1 | Context, reserved values, unsigned octet. |
| `addr_npi`, `source_addr_npi`, `dest_addr_npi`, `esme_addr_npi` | 5.2.6 | 4.7.2 | Context and supported numeric plan. |
| `address_range` | 5.2.7 | 4.7.3 | Bound length and application-controlled matching policy. |
| `source_addr` | 5.2.8 | 4.7.29 | Command-specific maximum and permitted empty value. |
| `destination_addr` | 5.2.9 | 4.7.8 | Address octets, limits, multiple-destination element. |
| `esme_addr` | 5.2.10 | 4.7.11 | Alert destination fields. |
| `service_type` | 5.2.11 | 4.7.25 | Empty default and explicit service identifier. |
| `esm_class` | 5.2.12 | 4.7.12 | Mode/type/feature bits by direction and version. |
| `protocol_id` | 5.2.13 | 4.7.20 | Applicable network value and reserved context. |
| `priority_flag` | 5.2.14 | 4.7.19 | Network/profile-specific interpretation. |
| `schedule_delivery_time` | 5.2.15, 7.1 | 4.7.23 | Empty, absolute and relative syntax; command restrictions. |
| `validity_period` | 5.2.16, 7.1 | 4.7.23 | Empty, expiry representation, relative calendar components. |
| `registered_delivery` | 5.2.17 | 4.7.21 | Receipt/acknowledgement bits; version-specific options. |
| `replace_if_present_flag` | 5.2.18 | 4.7.22 | Replacement permission and command context. |
| `data_coding` | 5.2.19 | 4.7.7 | Preserve raw octet; explicit encoding policy. |
| `sm_default_msg_id` | 5.2.20 | 4.7.27 | Default-message reference and payload relationship. |
| `sm_length` | 5.2.21 | 4.7.28 | Encoded byte count and empty payload rules. |
| `short_message` | 5.2.22 | 4.7.26 | Raw octets, limits, interaction with payload TLV. |
| `message_id` | 5.2.23 | 4.7.14 | Opaque case-preserved string; success/error body rules. |
| `number_of_dests` | 5.2.24 | 4.7.17 | Count matches encoded destination elements. |
| `dest_flag` | 5.2.25 | 4.7.9 | Address versus distribution-list alternative. |
| `no_unsuccess` | 5.2.26 | 4.7.16 | Count matches per-destination failures. |
| `dl_name` | 5.2.27 | 4.7.10 | Distribution-list string bounds. |
| `message_state` (standard field) | 5.2.28 | 4.7.15 | Query outcome; distinguish same-named TLV. |
| `final_date` | 4.8.2, 7.1 | 4.7.23 | Empty or absolute completion time. |
| `error_code` | 4.8.2, 6.1 | 4.5.2.2 | Query network error octet; not header status. |
| `error_status_code` | 4.5.2.1, 5.1.3 | 4.7.6 | Four-octet per-destination status. |

The 5.0 field-definition heading spells the schedule field
`scheduled_delivery_time`; command layouts also use `schedule_delivery_time`.
Use one documented API identity and do not invent a second wire field.
SMPP relative time contains calendar components; do not silently convert every
relative value to a fixed Java `Duration`.

### Field occurrence and layout checks

| Body group | Commands and source layout | Planned fixture focus |
| --- | --- | --- |
| Bind credentials and addressing | Three binds; operation sections above | Same common field order, selected bind command ID; response identity and version TLV. |
| Outbind credentials | `outbind` | Only its declared fields; no bind-mode/version fields added. |
| Ordinary message body | `submit_sm`, `deliver_sm` | Independently encoded source/destination, flags, times, payload; command-specific reserved fields. |
| Data body | `data_sm` | Compact field set and payload TLV; no invented `sm_length`/`short_message` pair. |
| Multiple destinations | `submit_multi` | Shared source, count, ordered address/list alternatives, remaining message fields. |
| Multiple results | `submit_multi_resp` | Message ID, failure count, ordered address/status elements. |
| Query | `query_sm`, `query_sm_resp` | Message/source identity; state, final date, and network error. |
| Cancellation | `cancel_sm` | Service/message/source/destination identity and matching semantics delegated to application. |
| Replacement | `replace_sm` | Message/source identity, times, receipt/default fields, payload; 5.0 replacement TLV context. |
| Alert | `alert_notification` | Source and ESME addressing; availability TLV; no response. |
| Broadcast | `broadcast_sm` and response | Standard broadcast fields, required TLVs, repeated areas, optional failure details. |
| Broadcast query | `query_broadcast_sm` and response | Identity, state and area-result TLVs, correlated repeated values. |
| Broadcast cancellation | `cancel_broadcast_sm` and response | Service/message/source identity and permitted selection TLVs. |
| Control/error | `enquire_link`, `unbind`, `generic_nack` | Empty bodies are valid; apply the 5.0 congestion-response extension separately. |

For each response, test success and nonzero status independently, including when
its standard body may be absent. Never infer one universal error-body layout from
a single command. Record exact status constants and body requirements in that
operation's fixtures before implementing it.

The 5.0 congestion definition (§4.8.4.18) permits the TLV on operation responses.
Account for this explicit extension even where a control response's basic syntax
lists only a header; do not universally reject every control response over 16 bytes.

## Optional parameters and 5.0 additions

[TLVS.md](TLVS.md) inventories all 44 distinct 3.4 tags and 64 distinct 5.0 tags,
with definition references, applicability sources, and planned test identities.
Both endpoints must encode/decode the applicable fields. Application policy
implements the meaning of services such as billing, routing, and broadcasting.

The following support items are required in addition to adding command names:

| Item | Scope and planned evidence |
| --- | --- |
| Bind version/capability decisions | API decision table, no implicit downgrade/retry, missing advertisement tests. |
| 5.0 broadcast services | All three request/response pairs, required and repeatable TLVs, typed handlers. |
| 5.0 receipt and message-state options | Raw bits/states, version checks, receipt helper and simulator cases. |
| Congestion feedback | Typed 5.0 TLV, valid response context, observation independent from admission policy. |
| Extended billing/routing/number-portability data | Every listed 5.0 tag is preserved and validated; application decisions remain explicit. |
| Changed field/TLV definitions | Separate fixtures per version even when tag/field names match; do not reuse 3.4 length rules blindly. |
| Unknown command or TLV | Nack unknown commands; preserve and semantically ignore well-formed unsupported TLVs; bounded malformed-input handling. |
| Provider exceptions | Named, opt-in rules with peer/version evidence and tests; baseline remains distinguishable. |

## Evidence and implementation order

Start with header/framing tests `FRAME-01` through `FRAME-08` in
[TEST_PLAN.md](TEST_PLAN.md). Then add field primitives and profiles, control
codecs, message codecs, and sessions in roadmap order. Implement only scenarios
needed by the current step, using real red/green/refactor evidence.

Before marking an operation implemented, expand its evidence by profile and our
endpoint role. Record test names/revision for codec and session checks; record
peer implementation/version/configuration for independent verification. A row's
summary may say complete only when all its applicable subcases have evidence.
Simulator evidence is recorded alongside the session evidence with its scenario
and run ID. No `—` is a passing result.

SMPP 3.3, vendor-specific commands, production account storage, billing, routing,
carrier delivery, and distributed simulator orchestration are outside this
baseline. Raw extension support must not be advertised as implementing a vendor's
business behavior.

## Sources

[^1]: SMPP Developers Forum. [SMPP 3.4, Issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf), 12 October 1999. Operation matrix pp. 17–18; command and field references are identified in the tables.
[^2]: SMS Forum. [SMPP 5.0](https://smpp.org/SMPP_v5.pdf), 19 February 2003. Operation matrix pp. 29–30; command and field references are identified in the tables.
