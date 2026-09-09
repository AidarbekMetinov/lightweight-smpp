# Message codecs

Step 7 supplies immutable values and complete bounded body codecs for `submit_sm`,
`deliver_sm`, `data_sm` and their three responses in SMPP 3.4 and 5.0. The codecs
compose with Step 6's `PduCodec`, `Pdu` and `PduLimits`. This is binary codec
support. Step 8 supplies separate [session/permission policies](SESSIONS.md);
live endpoints, receipt parsing, text conversion, segmentation and independent-peer
interoperability remain later work.

```java
var profile = ProtocolProfile.forVersion(SmppVersion.V5_0);
var codecs = MessageCommandCodecs.all(MessageDirection.SUBMISSION);
var codec = new PduCodec(codecs, new PduLimits(70_000, 66_000, 128));
var empty = new OptionalParameters(List.of());
var fields = new ShortMessage("", new Address(5, 0, "Example"),
        new Address(1, 1, "12025550101"), 0, 0, 0, "", "", 1, 0, 4, 0,
        new OctetString(new byte[] {0, (byte) 0xff, 65}), empty);
byte[] frame = codec.encode(new Pdu<>(0, 1, new SubmitSm(fields)), profile);
```

`MessageDirection` describes the original **data request** direction: `SUBMISSION`
is ESME to message center, `DELIVERY` is message center to ESME. Its response uses
the same configuration and travels oppositely. Submit and deliver identities
already identify their request direction. A caller handling both data directions
keeps the corresponding codec configurations explicit; an ID or profile cannot
infer that information. This does not decide whether a session may send a PDU.

The Step 12 [endpoint exchange API](EXCHANGE.md) uses these byte contracts.
Step 15 [message helpers](MESSAGE_HELPERS.md) add explicit text conversion,
segmentation/reassembly and receipt parsing separately. Wire codecs continue to
retain raw payloads and TLVs and do not infer provider conventions or handset
outcomes. [Simulators](SIMULATORS.md) exercise selected helper conventions.

## Values and standard fields

`OctetString` copies input and output arrays and compares their contents. The
immutable command records compose `ShortMessage` or `MessageResponse` only where
the stored field layout is shared. Command/profile validation remains separate.
`DataSm` has its own reduced layout. `OptionalParameters` preserves order,
repeated entries, unknown tags and their owned bytes. Diagnostics do not print
addresses, message IDs or payload contents.

| Field | SMPP 3.4 | SMPP 5.0 |
| --- | --- | --- |
| `service_type` | At most 5 non-NUL ASCII characters | Same; carrier-defined strings are retained |
| Submit/deliver addresses | At most 20 non-NUL ASCII characters | Same |
| Data addresses | At most 64 non-NUL ASCII characters | Same |
| `short_message` | 0..254 octets | 0..255 octets |
| Predefined message ID | Outgoing 0 means unused; 1..254 selects a message | Outgoing 0 means unused; 1..255 selects a message |
| Delivery schedule/validity | Empty, each occupying one NUL octet | Empty or 16-character forwarding time |
| Delivery replacement/predefined ID | Outgoing zero; incoming unused numeric values retained | Supported numeric values permitted for forwarding |
| Response `message_id` | 0..64 non-NUL ASCII characters for submit/data; deliver is empty | Same outgoing; incoming deliver IDs up to 64 characters are retained |

Every C-octet bound includes its final terminator on the wire. ASCII controls and
DEL follow the existing field primitives; non-ASCII, embedded NULs, missing
terminators, overlong fields and truncated binary fields fail. Empty addresses
are preserved rather than replaced with an application-specific address.

Outgoing TON supports 0..6; NPI supports 0, 1, 3, 4, 6, 8, 9, 10, 14 and 18.
Basic-message priority supports 0..3; replacement supports 0 or 1. The special
5.0 CBS priority 4 belongs to broadcast/network-service support outside these
basic message codecs. `protocol_id` and `data_coding` remain opaque unsigned
octets: their external-network meanings do not select an implicit text encoding.

Outgoing `esm_class` accepts message types 0, 8 and 16 on 3.4 submissions;
3.4 deliveries and 5.0 messages additionally support 4, 24 and 32. Mode and GSM
feature bits remain independent. Incoming 3.4 delivery mode bits are ignored
semantically and retained. `registered_delivery` permits bits 0..4, with the
success-only receipt setting 3 in bits 0..1 requiring 5.0. The 3.4 label calls
the intermediate flag “bit 5”, but its diagram specifies `xxx1xxxx`; the codec
uses that diagram and 5.0's explicit bit 4 (`0x10`).

Reserved incoming mandatory numeric values are retained without normalization;
this codec does not act on their reserved meanings. Sending those same values
requires the supported outgoing domain. Wire-length and grammar errors still
fail. This follows the version compatibility rules in 3.4 §3.3 and 5.0 §2.11.1.

Time fields are either empty or `YYMMDDhhmmsstnnp`. Absolute fields enforce the
listed component ranges, including months 1..12, days 1..31 and quarter-hour
offsets 0..48. They do not infer a century, calendar instant or current timezone.
Relative fields permit zero month/day components, with maxima 12/31, hours 23
and minutes/seconds 59. This follows the relative-offset prose despite the
copied absolute minima in the 5.0 relative table. Outgoing relative times require
the unused `tnn` suffix `000`; incoming decimal suffixes are ignored semantically
and preserved exactly. Calendar offsets are not converted to `Duration`.

## Payload and optional parameters

`data_sm` has no `short_message` or `sm_length`. `message_payload` is its only
user-data carrier, but an empty data body with no TLVs is valid: neither profile's
operation table declares that TLV mandatory. Empty payload TLVs are also valid.
Payload values hold 0..65535 octets subject to configured full-PDU, TLV-byte and
TLV-count limits. Those limits apply before allocation at each wire boundary.
Outgoing TLV count and aggregate byte bounds are checked before standard or
vendor value interpretation; the single bounded encoding is reused in the body.

Outgoing submit/deliver messages use a zero-length short-message field when a
payload TLV is present. Incoming 3.4 messages using both carriers fail the
explicit mutual-exclusion rule. Incoming 5.0 messages preserve both raw carriers;
the payload TLV supersedes `short_message` according to the 5.0 command layout.
The library neither discards nor concatenates those bytes.

`MessageTlvRules` is the exact occurrence-table source. The 3.4 tables contain
28 submit tags, 18 deliver tags and 38 data tags; data uses that edition's shared
operation table in either direction. The 5.0 tables contain 44 submission tags
and 32 delivery tags, and data selects the appropriate direction. Three callback
tags may repeat where permitted; other recognized tags are singletons. No
message command has an unconditional mandatory TLV.

`MessageCommandCodecs.tlvRegistry(direction)` exposes **value-level**, message-only
interpretation as `OctetString`: SMPP widths, string termination, discriminants
and supported subfields are checked, and the resulting octets remain immutable.
It does not imply receipt interpretation or external billing, routing, network,
WAP, USSD or handset-service implementation. Compound network-specific data
(subaddresses, bearer protocol IDs, display text, language, user response codes,
signal, session and vendor billing content) remains raw inside its validated SMPP
envelope. Individual interpretation must still be composed with command and
companion validation; the registry alone does not validate those conditions.

All 51 distinct tags used by these message tables have structural value checks.
Notable distinctions include:

- `source_telematics_id` is one octet, while `dest_telematics_id` is two, following
  both editions' individual value tables. The 3.4 destination meaning is undefined
  and retained raw; 5.0 supports its protocol octet with a zero reserved octet.
- `alert_on_message_delivery` is zero length in 3.4 and zero or one octet in 5.0.
  `ms_validity` is one octet in 3.4 and one or four in 5.0; relative behavior 4
  requires its unit/count extension. Units 0..6 are supported.
- State 0 and 9 and network-error types 4..8 are supported only in 5.0.
- Callback numbers validate mode, TON/NPI and the selected ASCII-digit or TBCD
  layout. TBCD filler is accepted only in the final high nibble. Display-tag
  octets remain independent of text conversion. Known UCS2 display data requires
  complete two-octet units; reserved coding values remain uninterpreted.
- Network IDs validate bounded ASCII C-octet layout and supported format/address
  discriminants. Node IDs are six ASCII decimal digits without a terminator;
  number-portability information is ten ASCII decimal digits. Country information
  is the specified 1..5-octet integer representation.

Unknown or unexpected incoming tags are preserved, including repetitions, and
are not interpreted even if another context knows their tag. A known permitted
tag with a malformed value length or structure fails. A well-formed reserved
value yields no interpretation and remains raw. Output rejects unexpected tags
and unsupported values. A registered `MessageTlvExtension` explicitly grants
interpretation and outgoing permission for one profile, command, original request
direction and vendor tag in `0x1400..0x3fff`; duplicates and implicit context
spillover fail. Extension codecs retain the existing `TlvValueCodec` ownership
and thread-safety obligations.

The command codecs validate these relationships:

- Outgoing SAR has all three companions, nonzero total/sequence and sequence no
  greater than total. Incomplete or unsupported incoming SAR is ignored
  semantically and preserved, as required by the individual definitions.
- A complete supported SAR group or supported source/destination port conflicts
  with UDHI. An ignored incomplete SAR group does not create that conflict.
- Present callback presentation/display tags match the number and order of the
  callback-number entries. If an incoming callback group contains unsupported
  values, its correlated interpretation is ignored rather than misassociated.
- Source/destination network IDs require their corresponding node IDs; ported
  resolution 2 requires both number-portability information and country TLVs.

Receipt identifiers, state and errors remain raw. “Should be present” receipt
recommendations do not become invented mandatory TLVs. Conditions that depend on
external service selection or network technology are not inferred from an
opaque service label or payload.

## Responses and original-request context

`MessageResponse.messageId()` distinguishes an omitted body (`Optional.empty()`)
from the present empty C-octet string. Successful responses require the standard
body. Outgoing delivery response IDs are empty. Unknown numeric status values
remain unchanged in `Pdu`.

| Context | Error-body behavior |
| --- | --- |
| SMPP 3.4 `submit_sm_resp` | Omit on output; reject a present body on input, per §4.4.2 |
| SMPP 3.4 `deliver_sm_resp` / `data_sm_resp` | Header-only error accepted; a present body follows its operation layout and bounds |
| All SMPP 5.0 message responses | Canonical output is header-only; any bounded incoming error body is ignored in full, including malformed contents, per §3.2.1.3 |

5.0 §3.2.1.3's error-body omission rule conflicts with the transaction diagnostic
TLV tables when the transaction failed (`ESME_RDELIVERYFAILURE`). This baseline
follows the global omission/ignore rule. Encoding diagnostic TLVs on a failed
5.0 transaction reply would require a separate, explicit compatibility policy;
it is not supported by this baseline. A status-zero response is not manufactured
to work around that conflict. The 3.4 data response table has no omission note,
so its failure diagnostics can be retained and encoded in a negative response.

Call `MessageResponseRules.validate(request, response, profile, direction)` after
the two body codecs when the original request is available. It checks matching
message operation IDs and the transaction-mode applicability of supported
diagnostic TLVs. It does not correlate sequence numbers or infer a downstream
delivery result. The overload accepting `deliverySucceeded` also rejects
failure-only diagnostics that contradict an independently known successful
attempt. A 3.4 data response's additional status text does not require transaction
mode; 5.0 submission response text does. Delivery response table differences
remain explicit. No DPF request is inferred merely from an absent `set_dpf`.

## Evidence and sources

[The Step 7 review](reviews/0006-message-codecs.md) records real red/green cycles,
the full current type inventory, source hashes and final verification. Tests use
independent complete frames for all six commands in both profiles and both data
directions, exact TLV table sets and independently specified per-tag value bytes.
This establishes binary behavior, with no network or independent-peer claim.

Sources: [SMPP 3.4, issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf),
§§3.3, 4.4, 4.6, 4.7, 5.2, 5.3 and 7.1;
[SMPP 5.0](https://smpp.org/SMPP_v5.pdf), §§2.11.1, 3.2.1.3,
4.2, 4.3, 4.7 and 4.8.4. Local downloaded primary PDF/text copies were inspected;
no reference implementation supplied the expected wire bytes.
