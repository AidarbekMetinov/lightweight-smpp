# TLV support inventory

Step 1 baseline, updated after Step 8. Raw bounded TLV encoding/decoding and
ordered immutable storage are implemented. Bind/control interpretation covers
`0210` and `0428`; message codecs structurally validate all 51 tags in their exact
profile/direction tables. External network services, receipt/text interpretation
and remaining operation contexts stay **planned**.
Profile catalogues contain all 44 distinct 3.4 tags and 64 distinct 5.0 tags.
`—` in the 3.4 column means a 5.0 addition. References are to the cited
specifications, not Java implementation sections.[^1][^2]

Each row has a test identity `TLV-<four-digit tag>`. For example, `TLV-0424`
covers payload encoding, decoding, limits, and command context. Implement these
as needed in Steps 5–7 and complete the declared inventory by Step 16.

## Tag and definition index

| Tag | Canonical parameter name | 3.4 definition | 5.0 definition |
| --- | --- | --- | --- |
| `0005` | `dest_addr_subunit` | 5.3.2.1 | 4.8.4.23 |
| `0006` | `dest_network_type` | 5.3.2.3 | 4.8.4.26 |
| `0007` | `dest_bearer_type` | 5.3.2.5 | 4.8.4.24 |
| `0008` | `dest_telematics_id` | 5.3.2.7 | 4.8.4.29 |
| `000D` | `source_addr_subunit` | 5.3.2.2 | 4.8.4.54 |
| `000E` | `source_network_type` | 5.3.2.4 | 4.8.4.57 |
| `000F` | `source_bearer_type` | 5.3.2.6 | 4.8.4.55 |
| `0010` | `source_telematics_id` | 5.3.2.8 | 4.8.4.61 |
| `0017` | `qos_time_to_live` | 5.3.2.9 | 4.8.4.46 |
| `0019` | `payload_type` | 5.3.2.10 | 4.8.4.44 |
| `001D` | `additional_status_info_text` | 5.3.2.11 | 4.8.4.1 |
| `001E` | `receipted_message_id` | 5.3.2.12 | 4.8.4.47 |
| `0030` | `ms_msg_wait_facilities` | 5.3.2.13 | 4.8.4.40 |
| `0201` | `privacy_indicator` | 5.3.2.14 | 4.8.4.45 |
| `0202` | `source_subaddress` | 5.3.2.15 | 4.8.4.60 |
| `0203` | `dest_subaddress` | 5.3.2.16 | 4.8.4.28 |
| `0204` | `user_message_reference` | 5.3.2.17 | 4.8.4.62 |
| `0205` | `user_response_code` | 5.3.2.18 | 4.8.4.63 |
| `020A` | `source_port` | 5.3.2.20 | 4.8.4.59 |
| `020B` | `destination_port` | 5.3.2.21 | 4.8.4.30 |
| `020C` | `sar_msg_ref_num` | 5.3.2.22 | 4.8.4.48 |
| `020D` | `language_indicator` | 5.3.2.19 | 4.8.4.35 |
| `020E` | `sar_total_segments` | 5.3.2.23 | 4.8.4.50 |
| `020F` | `sar_segment_seqnum` | 5.3.2.24 | 4.8.4.49 |
| `0210` | `sc_interface_version` | 5.3.2.25 | 4.8.4.51 |
| `0302` | `callback_num_pres_ind` | 5.3.2.37 | 4.8.4.17 |
| `0303` | `callback_num_atag` | 5.3.2.38 | 4.8.4.16 |
| `0304` | `number_of_messages` | 5.3.2.39 | 4.8.4.43 |
| `0381` | `callback_num` | 5.3.2.36 | 4.8.4.15 |
| `0420` | `dpf_result` | 5.3.2.28 | 4.8.4.32 |
| `0421` | `set_dpf` | 5.3.2.29 | 4.8.4.52 |
| `0422` | `ms_availability_status` | 5.3.2.30 | 4.8.4.39 |
| `0423` | `network_error_code` | 5.3.2.31 | 4.8.4.42 |
| `0424` | `message_payload` | 5.3.2.32 | 4.8.4.36 |
| `0425` | `delivery_failure_reason` | 5.3.2.33 | 4.8.4.19 |
| `0426` | `more_messages_to_send` | 5.3.2.34 | 4.8.4.38 |
| `0427` | `message_state` | 5.3.2.35 | 4.8.4.37 |
| `0428` | `congestion_state` | — | 4.8.4.18 |
| `0501` | `ussd_service_op` | 5.3.2.44 | 4.8.4.64 |
| `0600` | `broadcast_channel_indicator` | — | 4.8.4.7 |
| `0601` | `broadcast_content_type` | — | 4.8.4.8 |
| `0602` | `broadcast_content_type_info` | — | 4.8.4.6 |
| `0603` | `broadcast_message_class` | — | 4.8.4.12 |
| `0604` | `broadcast_rep_num` | — | 4.8.4.13 |
| `0605` | `broadcast_frequency_interval` | — | 4.8.4.11 |
| `0606` | `broadcast_area_identifier` | — | 4.8.4.4 |
| `0607` | `broadcast_error_status` | — | 4.8.4.10 |
| `0608` | `broadcast_area_success` | — | 4.8.4.5 |
| `0609` | `broadcast_end_time` | — | 4.8.4.9 |
| `060A` | `broadcast_service_group` | — | 4.8.4.14 |
| `060B` | `billing_identification` | — | 4.8.4.3 |
| `060D` | `source_network_id` | — | 4.8.4.56 |
| `060E` | `dest_network_id` | — | 4.8.4.25 |
| `060F` | `source_node_id` | — | 4.8.4.58 |
| `0610` | `dest_node_id` | — | 4.8.4.27 |
| `0611` | `dest_addr_np_resolution` | — | 4.8.4.22 |
| `0612` | `dest_addr_np_information` | — | 4.8.4.21 |
| `0613` | `dest_addr_np_country` | — | 4.8.4.20 |
| `1201` | `display_time` | 5.3.2.26 | 4.8.4.31 |
| `1203` | `sms_signal` | 5.3.2.40 | 4.8.4.53 |
| `1204` | `ms_validity` | 5.3.2.27 | 4.8.4.41 |
| `130C` | `alert_on_message_delivery` | 5.3.2.41 | 4.8.4.2 |
| `1380` | `its_reply_type` | 5.3.2.42 | 4.8.4.33 |
| `1383` | `its_session_info` | 5.3.2.43 | 4.8.4.34 |

Canonical-name notes: 5.0 uses `dest_port` for tag `020B`; this is the same wire
tag as 3.4's `destination_port`. `SC_interface_version` and
`alert_on_msg_delivery` are spelling variants in tables. The broadcast response's
`failed_broadcast_area_identifier` shares tag `0606`; its meaning depends on the
response context and does not create a 65th tag or a second global registration.

## Command applicability index

Presence in the tag catalogue does not permit a parameter on every command.
The operation-specific table and individual definition jointly determine its
direction, multiplicity, requiredness, and semantic conditions. Endpoint roles
and bind permissions inherit from [the operation inventory](PROTOCOL.md).

| Context | 3.4 applicability source | 5.0 applicability source | Planned context cases |
| --- | --- | --- | --- |
| Bind responses | 4.1.2, 4.1.4, 4.1.6 | 4.1.1.2, 4.1.1.4, 4.1.1.6 | Version advertisement present, missing, malformed. |
| `submit_sm` | 4.4.1 | 4.2.1.1, 4.2.4 | Profile-specific field set, payload alternatives, repeatability. |
| `submit_multi` | 4.5.1 | 4.2.3.1, 4.2.4 | Multi-destination context; do not copy 3.4 submit-sm allowances blindly. |
| `data_sm` toward S | 4.7.1 | 4.2.2.1, 4.2.4 | Payload and transaction-mode conditions. |
| `deliver_sm` | 4.6.1 | 4.3.1.1, 4.3.3 | Receipt parameters, payload, delivery-only constraints. |
| `data_sm` toward C | 4.7.1 | 4.3.2–3 | Direction-specific delivery parameter set. |
| `data_sm_resp` | 4.7.2 | 4.2.5 or 4.3.4 by direction | Failure details, transaction-mode and DPF conditions. |
| `submit_sm_resp` | 4.4.2 | 4.2.5 | Version-specific response extensions. |
| `deliver_sm_resp` | 4.6.2 | 4.3.4 | Version-specific response extensions. |
| `alert_notification` | 4.12.1 | 4.1.3.1 | Availability status; no generated reply. |
| `replace_sm` | 4.10.1 | 4.5.3.1, 4.5.3.3 | 5.0 replacement payload extension. |
| `broadcast_sm` | — | 4.4.1.1, 4.4.2 | Required areas/content/repetition/interval and optional parameters. |
| `broadcast_sm_resp` | — | 4.4.1.2, 4.4.3 | Failure details and repeated failed areas. |
| `query_broadcast_sm` | — | 4.6.1.1–2 | Optional message reference. |
| `query_broadcast_sm_resp` | — | 4.6.1.3–4 | State/areas/success correlation and optional end time. |
| `cancel_broadcast_sm` | — | 4.6.2.1–2 | Content/reference selection. |
| Response congestion | — | 4.8.4.18 | Applicable to operation responses, including normally header-only controls. |

An absent 3.4 TLV section in a command layout is not permission to use the 5.0
extension set. Outgoing typed construction enforces the applicable set. Incoming
well-formed unknown/unexpected TLVs follow compatibility handling: preserve raw
data and ignore its unsupported semantics. Do not reject a message solely because
it contains a well-formed extension the local application does not understand.

## Executed foundation evidence

The [field/profile review](reviews/0004-fields-profiles.md) records these tests:

| Scope | Executed evidence | Remaining scope |
| --- | --- | --- |
| Raw TLV framing/storage | `TlvCodecTest`, `TlvTest`, `OptionalParametersTest`: independent bytes, malformed lengths, byte/count bounds, unknown/repeated values, ownership and equality. | Per-tag meanings and full command bodies. |
| `TLV-0210` | `TypedTlvRegistryTest`, `UnsignedByteTlvCodecTest`: one raw version octet in all three bind responses for both profiles, including unknown values. | Live endpoint behavior; Step 6 verifies body policy and Step 8 verifies pure version negotiation. |
| `TLV-0428` / `TLV-CONGESTION` | Same typed tests: 5.0 response context, exact length, supported 0..100 range, reserved incoming values ignored semantically. | Session observation/admission policy and independent peers. |
| `TLV-REPEAT` / `TLV-BROADCAST` | `ProtocolProfileTest`, `TlvRulesTest`: required/singleton/repeatable occurrence checks, preserved unknown input, and immediate-priority repetition exception. | Other command contexts, value semantics and per-area/callback correlations. |
| `TLV-EXTENSION` | `TypedTlvRegistryTest`: scoped registration, duplicate rejection, immutable extension, and continued raw block bounds. | Provider-specific interoperability evidence. |

Catalogue equality is verified separately from supported interpretation. These
checks apply to network-independent binary/profile behavior and establish no
session or independent-peer result. [The field guide](FIELDS.md) states exact
API contracts and composition order.

## Executed command evidence

[Step 6](reviews/0005-session-command-codecs.md) adds complete bind/control
wire fixtures: version advertisement presence/absence and raw unknown values,
5.0 congestion on operation responses, singleton validation, ignored reserved
incoming values, and bounded preservation of unexpected extensions.
[Command contracts](COMMANDS.md) distinguish failed-bind body omission from
control-response congestion and document the specification interpretations.
These codec results do not establish negotiated endpoint capabilities. Step 8
adds [pure negotiation and outgoing requirement tests](reviews/0007-session-state.md),
including missing/unknown advertisements and raw incoming extensions that cannot
enable outgoing TLVs. Live endpoint composition remains pending.

[Step 7](reviews/0006-message-codecs.md) adds `MessageTlvRulesTest` with exact
3.4/5.0 command/direction sets; `MessageTlvValueCodecTest` with independently
specified values and invalid lengths for all 51 message tags; and
`MessageOptionalParametersTest` with payload, SAR, UDHI, callback, network and
number-portability companion cases. `MessageResponseRulesTest` checks conditions
requiring the original request or known delivery outcome.
[Message contracts](MESSAGES.md) state the version-specific value layouts, vendor
registration scope and the explicit 5.0 failed-response body limitation. Raw
receipt identifiers and payload bytes do not implement receipt/text parsers.

## Planned validation for every tag

For each `TLV-xxxx`, create a version/command-specific fixture with independently
derived bytes and the definition above. Include the value representation and
length, boundary and reserved values, truncated tag/length/value, invalid length,
and the result when the tag is permitted, unexpected, or unsupported.

Expand the context sources into executable per-command cases when that command
is implemented. Require both directions where applicable, and record the test
names in its evidence. Implemented occurrence rules cover bind/control bodies,
submit/deliver/data messages and responses in both directions, and initial 5.0
broadcast request requirements with explicit priority. Complete broadcast codecs
and remaining operation contexts still need executable evidence.

The following cross-field scenario groups remain the inventory checklist.
The executed message subset is recorded above; remaining operations and services
need their own evidence:

- `TLV-PAYLOAD`: `short_message` and `message_payload` interactions; `data_sm`
  payload requirements; byte lengths rather than Java character counts.
- `TLV-SAR`: required companion parameters, segment range/count consistency,
  reference identity, and interaction with user-data headers.
- `TLV-RECEIPT`: raw message ID, state, and network error; missing values and
  provider-specific text are explicit parser outcomes.
- `TLV-REPEAT`: repeated callbacks and broadcast areas retain order and count;
  duplicates of singleton parameters follow their specific rule.
- `TLV-BROADCAST`: required request TLVs, per-area query results and failure
  correlation; no accidental loss through map-based storage.
- `TLV-VERSION`: changed definitions such as `alert_on_message_delivery` and
  `ms_validity` receive separate 3.4 and 5.0 fixtures, even though their tags match.
- `TLV-CONGESTION`: range/context validation and delivery to observation policy
  without silently changing message retry behavior.
- `TLV-EXTENSION`: supported vendor range, duplicate codec registration,
  preservation of unknown bytes, and unchanged frame/queue bounds.

Test typed interpretation independently from raw TLV framing. Retaining billing,
network, or broadcast data does not implement those external services. All new
or changed types and tests require the recorded [SOLID review](SOLID.md) and
[TDD cycle](TDD.md).

## Sources

[^1]: SMPP Developers Forum. [SMPP 3.4, Issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf), §5.3 and operation-specific layouts, 12 October 1999.
[^2]: SMS Forum. [SMPP 5.0](https://smpp.org/SMPP_v5.pdf), §4.8 and operation-specific layouts, 19 February 2003.
