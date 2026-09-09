# Command codec contracts

Step 6 provides complete, network-independent bind and control codecs for SMPP
3.4 and 5.0. It adds eleven command identities: all three bind requests and
responses, the unbind/enquire-link pairs, and generic_nack. Codecs preserve data
needed by later session/version and correlation policies; they do not open a
connection, authenticate an account, assign sequences or choose capabilities.

## Public use

```java
ProtocolProfile profile = ProtocolProfile.forVersion(SmppVersion.V5_0);
PduLimits limits = new PduLimits(65536, 16384, 256);
PduCodec codec = new PduCodec(ControlCommandCodecs.all(), limits);
BindRequest bind = new BindRequest(
        BindMode.TRANSCEIVER, "esme", "secret", "", 0x50, 0, 0, "");
byte[] encoded = codec.encode(new Pdu<>(0, 1, bind), profile);
Pdu<Command> decoded = codec.decode(encoded, profile);
```

Imports are omitted. `Pdu` carries status, sequence and immutable command data;
its full wire length is calculated during encoding. Applications using this
low-level API supply an already valid sequence. `BindMode` contains `RECEIVER`,
`TRANSMITTER` and `TRANSCEIVER`, each with request/response command identifiers.
`BindResponse(mode, Optional<String> systemId, OptionalParameters parameters)`
distinguishes a missing error body from a successful empty system identifier.
`ControlCommand(Type, OptionalParameters)` represents its five named wire types.
For a standard control request, pass an empty `OptionalParameters` block.

`Command` exposes one stable unsigned ID. New command families supply immutable
values implementing that interface and thread-safe `CommandCodec<T>` body
translations. Combine their registrations with `ControlCommandCodecs.all()` in
a new `PduCodec`; no existing control codec or dispatcher switch needs editing.
A body codec declares its ID and Java representation and encodes/decodes an
entire bounded body with explicit status, profile and `PduLimits` arguments.
Registration rejects duplicate IDs even when Java representations differ.
Several different IDs may deliberately share one representation, as binds do.
The registry copies its input collection and checks metadata and result types.
Supplied implementations must honor stable metadata, immutability and thread
safety; registry checks cannot make arbitrary extension code safe.

## Frame and allocation boundaries

`decode(byte[], profile)` accepts exactly one complete frame. Use the existing
`PduFramer` for partial or concatenated network input. Lengths below 16, above the
configured maximum, or inconsistent with actual bytes fail before body dispatch.
Requests require status zero; responses preserve unknown unsigned statuses.
Sequences are 1 through `0x7fffffff`, with zero additionally permitted for a
negative `generic_nack` whose original sequence could not be obtained. Values
above that range are rejected without truncation or normalization. `generic_nack`
requires nonzero status. The raw `PduHeader` API remains permissive for framing
and invalid-peer diagnostics. These rules follow 3.4 §§5.1.1–4/4.3.1 and 5.0
§§3.2.1/4.7.24/4.1.4.1. [SMPP 3.4](https://smpp.org/SMPP_v3_4_Issue1_2.pdf),
[SMPP 5.0](https://smpp.org/SMPP_v5.pdf).

`PduLimits` bounds the complete PDU, aggregate trailing TLV bytes, and raw TLV
count. TLV bytes include their four-octet entry headers. The TLV byte bound must
fit the complete PDU's body bound; all counts are nonnegative. Input bounds are
checked before copies, and TLV value lengths before value allocation. Encoding
allocates the actual required body/frame size, rather than allocating the
configured maximum. The dispatcher rechecks extension output before allocating
the complete frame. Standard body codecs enforce the same bounds during direct
calls. Callers must not mutate an input array concurrently with decoding; returned
arrays are caller-owned and protocol values own all retained mutable data.

`CommandDispatchException.header()` retains the received header.
`definedByProfile()` is false for an undefined command and true when the chosen
specification defines the command but no local codec was registered. On an encode
failure before a body exists, its header has provisional length 16. Malformed
frames/fields and illegal headers are separate `IllegalArgumentException`
failures, with `FieldCodecException` identifying structural field failures.
Codecs do not send a nack or decide whether a session should close.

## Bind data and version separation

| Data | Public representation | Decision owner |
| --- | --- | --- |
| Requested version | `BindRequest.interfaceVersion()`, a raw unsigned octet | The initiating application/session policy |
| Raw peer advertisement | Ordered `0210` TLV in `BindResponse.optionalParameters()` | Peer-provided data; `TypedTlvRegistry` can read its unsigned octet |
| Explicit parsing profile | `ProtocolProfile` argument to encode/decode | Caller |
| Effective capabilities | Not inferred by these codecs | Later session/version policy, following [API.md](API.md) |

Unknown version octets remain raw; missing advertisement remains absent. Parsing
3.4 data with a peer advertising 5.0 does not enable 5.0 operations. Parsing a 5.0
request with a 3.4 advertisement does not itself accept the requested version.
A successful response can be decoded or encoded without an advertisement for
compatibility/fixture use; the later server binding policy owns the requirement
to advertise its implemented version. The codec never invents a missing TLV.

| Bind field | Maximum ASCII characters | Wire limit including NUL |
| --- | --- | --- |
| `system_id` | 15 | 16 |
| `password` | 8 | 9 |
| `system_type` | 12 | 13 |
| `address_range` | 40 | 41 |

Every field may be empty. Embedded NUL, non-ASCII text and overlong fields fail.
Strings and raw octets are preserved; neither address matching nor authentication
occurs in a codec. Reserved incoming TON/NPI octets remain raw, following forward
compatibility; no supported meaning is claimed for them. Outgoing TON is 0–6 and
NPI is one of 0, 1, 3, 4, 6, 8, 9, 10, 14 or 18. Credential-bearing diagnostics
omit system identifiers, passwords, system types and address-range contents.
Limits and field order are from 3.4 §§4.1.1–6/5.2.1–7 and 5.0
§§4.1.1.1–6/4.7.1–3. [SMPP 3.4](https://smpp.org/SMPP_v3_4_Issue1_2.pdf),
[SMPP 5.0](https://smpp.org/SMPP_v5.pdf).

## Response bodies and optional extensions

Failed binds encode a header only and reject supplied system IDs or TLVs.
Successful binds require a system-id field, even if empty. Incoming 3.4 failed
binds with any body are rejected: the explicit transmitter/receiver omission
notes in §§4.1.2/4 are applied consistently to all three modes (the transceiver
table does not repeat the note). Incoming 5.0 failed-bind bodies are ignored in
full, including malformed or excessive-as-TLV contents, within the enclosing PDU
bound. They produce neither a system identifier nor an advertisement. This is
5.0 §3.2.1.3's specific receiver instruction; ignored bytes are not parsed as a
TLV region. [SMPP 3.4](https://smpp.org/SMPP_v3_4_Issue1_2.pdf),
[SMPP 5.0](https://smpp.org/SMPP_v5.pdf).

For other complete bind/control bodies, both profiles retain bounded,
well-formed unknown or unexpected TLVs in order, including repetitions. This
also covers incoming request tails and 3.4 control-response tails. They remain
uninterpreted and do not grant permission to send them. This implements 3.4 §3.3
and 5.0 §2.11.1 forward compatibility. `BindRequest` has a ninth canonical
`OptionalParameters` argument for retained extensions; its eight-argument
constructor supplies an empty block. Malformed TLV framing still fails.
[SMPP 3.4](https://smpp.org/SMPP_v3_4_Issue1_2.pdf),
[SMPP 5.0](https://smpp.org/SMPP_v5.pdf).

Outgoing bind/control requests have no permitted TLVs. Bind responses permit
`sc_interface_version` in both profiles, and successful 5.0 bind responses also
permit `congestion_state`. Outgoing 3.4 unbind/enquiry/nack responses remain
header-only. In 5.0 their TLV-only bodies can carry congestion with a normal or
nonzero response status. Sections 2.9 and 4.8.4.18 explicitly allow congestion on
any operation response; this more specific extension is applied to control
responses despite their base header-only tables and general error-body omission
text. The enquiry response table normally specifies zero status; explicit peer
errors and unknown nonzero statuses are retained under the error procedure and
API numeric-status contract, rather than being rewritten as success. These are
explicit compatibility interpretations, not claims that the base syntax tables
list every case. [SMPP 5.0](https://smpp.org/SMPP_v5.pdf).

Recognized singleton TLVs cannot repeat. Version advertisements require one
value octet but retain unknown values. Congestion requires one octet in its
recognized 5.0 response context; 0–100 has typed meaning, and reserved incoming
values remain raw with no typed interpretation. Invalid outgoing/reserved values
are rejected. Unsupported contexts retain even differently sized raw values
without interpreting them. See [TLVS.md](TLVS.md) and the
[Step 6 review](reviews/0005-session-command-codecs.md) for executed evidence.

Session transitions, usable endpoint permissions, authentication, timers, TCP,
and independent peer interoperability are outside this codec step.
