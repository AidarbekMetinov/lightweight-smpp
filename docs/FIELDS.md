# Fields, TLVs, and protocol profiles

Step 5 adds the binary field and optional-parameter foundation for both endpoint
roles. The [framer](FRAMING.md) assembles complete frames; these codecs operate on
bounded complete byte blocks. Full command bodies, binding, version negotiation,
role permissions, sessions, and networking remain later steps.

## Binary field cursors

`kg.aidarbek.smpp.codec.FieldReader` copies a supplied byte array only after
checking its configured maximum length. `FieldWriter` owns a fixed-capacity array.
Both are mutable cursors requiring thread confinement. Each operation either
finishes completely or leaves the cursor/output unchanged. Empty raw blocks and
zero-length raw octet fields are allowed.

| Wire field | Reader / writer methods | Java representation |
| --- | --- | --- |
| Unsigned octet | `readUnsignedByte` / `writeUnsignedByte` | `int`, 0..255 |
| Unsigned two-octet integer | `readUnsignedShort` / `writeUnsignedShort` | `int`, 0..65535 |
| Unsigned four-octet integer | `readUnsignedInt` / `writeUnsignedInt` | `long`, 0..0xffffffff |
| Fixed-count raw octets | `readOctets` / `writeOctets` | Owned `byte[]`; no text interpretation |
| C-octet string | `readCOctetString` / `writeCOctetString` | ASCII `String` with a terminator-inclusive bound |

Integers use network byte order. C-octet bounds must be at least one octet;
empty strings encode as a single NUL. Non-ASCII characters, embedded outgoing
NULs, missing incoming terminators, and overlong values are rejected. Reader
errors use `FieldCodecException`, an `IllegalArgumentException` subtype, for
malformed wire content. Invalid arguments/output capacity use
`IllegalArgumentException`; null inputs are rejected. Errors describe structure
or offsets without including credentials or payload contents.

`readOctets` and `toByteArray` return caller-owned copies. `remaining` reports
unconsumed input bytes. Field-specific character sets, address lengths, reserved
values, flags, and combinations still belong to the command codec using these
primitives. Message-payload text encoding, segmentation, and receipt interpretation
remain with their later codecs and services.

## Raw optional parameters

`protocol.Tlv` owns an unsigned 16-bit tag and up to 65535 raw value octets. It
copies input/output arrays and compares tag plus binary contents in equality and
hashing. Empty values and unknown tags remain representable. Its diagnostic string
shows only tag and value length.

`protocol.OptionalParameters` copies an ordered list of immutable TLVs. Order and
repetitions participate in equality; returned entries are unmodifiable. Despite
the conventional name, the collection also holds parameters required by a command.
Its diagnostic string reports only entry count.

`codec.TlvCodec.decode(bytes, maximumLength, maximumCount)` returns the raw ordered
collection. It rejects truncated headers/values and configured count/byte excess;
claimed lengths are checked before reading or allocating their value bytes.
`encode(parameters, maximumLength, maximumCount)` checks aggregate size using a
`long` before allocating output, then writes two-octet tag/length fields and values.
Both limits may be zero for an empty block; negative limits are invalid. Unknown
tags and repeated entries survive raw translation without implying supported
semantics.

## Specification profiles and occurrence rules

`profile.SmppVersion` explicitly identifies 3.4 (`0x34`) or 5.0 (`0x50`).
`ProtocolProfile.forVersion(version)` provides an immutable descriptor with exact
command/tag membership from the [operation](PROTOCOL.md) and [TLV](TLVS.md)
inventories: 27 commands/44 tags for 3.4 and 33 commands/64 tags for 5.0.
Membership queries never truncate out-of-range numeric inputs and do not claim
an implemented command codec, field semantics, role permission, or peer capability.

`TlvRules` owns immutable permitted, required, and repeatable tag sets. Required
and repeatable sets must be subsets of permitted tags. Incoming validation checks
known-context required/singleton occurrences and ignores unexpected entries
semantically, preserving their raw representation. Outgoing validation also
rejects unexpected tags.

`ProtocolProfile.tlvRules(commandId)` currently supplies rules for the three bind
responses, `unbind_resp`, `enquire_link_resp`, and `generic_nack`. Bind responses
permit `sc_interface_version`; 5.0 responses also permit `congestion_state`.
An empty result means this context's rules are not implemented here and supplies
no permission to send.

`broadcastRequestTlvRules(priorityFlag)` supplies 5.0 broadcast occurrence rules:
content type, frequency interval, and at least one area are required; repetition
is required except for immediate priority `1`. At that priority it is omitted on
output and ignored if received. Areas and the three callback tags can repeat.
Only the priority octet width is checked at this layer. TLV content, callback
count relationships, network-specific priority meanings, body/status conditions,
and other cross-field validation remain with the appropriate command codecs.

## Typed interpretation and extension

`codec.TypedTlvRegistry.standard()` supplies these initial interpretations:

| Parameter | Context | Interpretation |
| --- | --- | --- |
| `sc_interface_version` (`0210`) | Three bind responses in either profile | Exactly one raw unsigned octet. Unknown advertisements are retained for later bind policy. |
| `congestion_state` (`0428`) | Defined 5.0 responses | Exactly one octet; values 0..100 are supported. Reserved incoming values return an empty interpretation; unsupported outgoing values fail. |

`decode(version, commandId, tlv, valueType)` returns an empty `Optional` for an
unregistered context or unsupported value. Malformed known-context lengths still
fail. `encode` requires a matching registration and Java value type. Neither call
implements version negotiation or response-body policy.

Use immutable `Registration<T>` values and `with(registration)` to create an
extended registry. Duplicate version/command/tag keys are rejected, even when
their codecs are identical. Registrations require a command defined by that
profile and an unsigned tag; custom tags may be registered explicitly. Extending
typed interpretation does not automatically extend occurrence rules.

`TlvValueCodec<T>` is the narrow extension contract. Implementations must be
thread-safe, keep metadata stable, return immutable typed values, avoid retaining
or mutating decoding input, and return fresh bounded encoding arrays. The supplied
`UnsignedByteTlvCodec` is exercised through this shared contract for both supported
domains. Extension code does not replace the enclosing raw block's bounds.

Incoming processing is raw bounded decode, applicable occurrence validation, then
supported typed interpretation. Outgoing command codecs check raw TLV byte/count
bounds before typed interpretation can copy values, then validate applicable
occurrences and semantics before returning an encoded command. A bounded raw
encoding may be retained for final body assembly. This keeps unknown-data
preservation separate from permission and interpretation.

## Evidence and remaining work

The [Step 5 review](reviews/0004-fields-profiles.md) records 29 actual TDD cycles,
additional characterization probes, all new/updated type reviews, and integrated
verification. The implementation has no runtime dependency. Steps 6 and 7 add
[bind/control codecs](COMMANDS.md) and [message codecs](MESSAGES.md), including
all message-table TLV structures, callback correlations and message companion
conditions. Remaining operation contexts and external service interpretation
stay planned in the inventories.

Wire primitives and compatibility rules follow
[SMPP 3.4](https://smpp.org/SMPP_v3_4_Issue1_2.pdf), §§3.1, 3.3, and 5.3, and
[SMPP 5.0](https://smpp.org/SMPP_v5.pdf), §§2.11.1, 3.1, 4.4.2, and 4.8.
The immediate-broadcast exception is specified in 5.0 §4.8.4.13.
