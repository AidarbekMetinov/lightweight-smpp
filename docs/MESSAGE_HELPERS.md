# Message helpers

Step 15 implements explicit text, segmentation, reassembly and delivery-receipt
helpers in `kg.aidarbek.smpp.message`. They use immutable protocol values and the
JDK, work without a session, and add no library runtime dependency. Wire codecs
continue to preserve caller-supplied octets. An application selects the provider
convention, `data_coding`, payload placement, receipt classification and reference
scope before using these helpers.

## Explicit text conversion

`TextEncoding.GSM7_UNPACKED` uses the GSM default alphabet and extension table,
with one seven-bit value in each octet. Extensions `\f`, `^`, `{`, `}`, `\`, `[`,
`~`, `]`, `|` and `€` take two octets, including ESC. For example, `A£Δä^` encodes
as `41 01 10 7b 1b 14`. It does not pack septets. `TextEncoding.UCS2` produces
big-endian two-octet BMP values: `AΩЖ漢` is `00 41 03 a9 04 16 6f 22`.
The alphabet and UCS-2 fixtures derive from sections 6.2.1, 6.2.1.1 and 6.2.3 of
[3GPP TS 23.038 / ETSI TS 123 038 V16.0.0](https://www.etsi.org/deliver/etsi_ts/123000_123099/123038/16.00.00_60/ts_123038v160000p.pdf).

Both values expose `encode(String)`, `decode(OctetString)` and
`encodedLength(String)`. Unsupported characters throw `IllegalArgumentException`;
there is no replacement, transliteration, alphabet fallback or normalization.
GSM encoding rejects a literal ESC and national-language shift characters outside
the supported tables. Decoding rejects high-bit octets, dangling ESC and unknown
or reserved extension codes. This is a strict converter policy; it does not apply
handset display fallback rules. UCS-2 rejects every surrogate code unit, including
a valid UTF-16 pair for a non-BMP character. Decoding rejects odd byte lengths.
No BOM is inserted; an explicitly supplied BOM or NUL remains ordinary BMP data.
Null input is rejected. The encoded length is a Java `int`; arithmetic overflow
is reported rather than wrapped. Encoding allocations are proportional to that
caller-selected input length.

SMPP `data_coding = 0` means the SMSC default alphabet. It does not universally
select unpacked GSM. A provider may require packed GSM, another default alphabet
or a separate convention. Those transformations and national shift tables are
outside these helpers. The application must agree on a convention with its peer;
the simulator's GSM fixtures explicitly choose unpacked GSM at coding 0.
See `data_coding` in [SMPP 3.4 section 5.2.19](https://smpp.org/SMPP_v3_4_Issue1_2.pdf).

## Segmentation and wire metadata

`MessageSegments.split(text, encoding, reference, singlePartLimit, multipartLimit)`
returns immutable ordered `MessageSegment` values. Limits measure **encoded
payload octets**, excluding UDH and TLV headers. Both are 1..65535 and the
multipart limit cannot exceed the single-part limit. Empty text yields one empty
part. No GSM escape pair or UCS-2 two-octet unit is split; an odd UCS-2 limit leaves
the unusable final octet empty. A limit too small for a complete unit or a message
requiring more than 255 parts is rejected. Unused space at unit boundaries can
make a message exceed 255 parts even when its length is at most `255 * limit`.

Each segment has a caller-allocated unsigned 16-bit reference, total 1..255,
one-based number at most the total, and an immutable payload of at most 65535
octets. References are not session request sequence numbers. The helper does not
allocate, recycle or establish uniqueness for them.

`segment.sarParameters()` builds the complete trio: `sar_msg_ref_num` (0x020c,
two big-endian octets), `sar_total_segments` (0x020e, one octet), and
`sar_segment_seqnum` (0x020f, one octet). `MessageSegments.fromSar(payload, tlvs)`
returns empty if all three are absent; it rejects partial, duplicate, incorrectly
sized or invalid metadata. Other TLVs remain the caller's original values and are
not interpreted. This stricter helper rejection policy makes ambiguity visible;
the SMPP field definitions describe ignoring an incomplete trio. See
[SMPP 3.4 sections 5.3.2.22–24](https://smpp.org/SMPP_v3_4_Issue1_2.pdf).

`ConcatenationHeader` is a separate, explicit helper for **octet-aligned UCS-2 or
binary payloads**. It reads or writes a sole canonical concatenation information
element. `prepend8` produces `05 00 03 rr tt ss`; `prepend16` produces
`06 08 04 rr-hi rr-lo tt ss`. `read` rejects malformed, truncated, combined or
unsupported headers, instead of silently discarding other information elements.
The raw input stays available to the caller. Its largest accepted input is 65542
octets and its returned fragment still has the 65535-octet segment bound.

This UDH helper neither packs GSM nor sets `esm_class`. The application sets
UDHI when carrying a header and selects the appropriate operation/payload bound.
For GSM networks SMPP prohibits combining the SAR trio with encoded UDH; choose
one representation. See [SMPP 3.4 section 5.2.12](https://smpp.org/SMPP_v3_4_Issue1_2.pdf).
Radio limits are separate from SMPP octet-field limits:

| Sole radio UDH | Header octets | Binary payload octets | UCS-2 characters | GSM payload septets, with separate packing |
| --- | ---: | ---: | ---: | ---: |
| 8-bit reference | 6 | 134 | 67 | 153 |
| 16-bit reference | 7 | 133 | 66 | 152 |

These limits and the requirement not to split encoding units come from sections
9.2.3.24.1 and 9.2.3.24.8 of
[3GPP TS 23.040 / ETSI TS 123 040 V16.0.0](https://www.etsi.org/deliver/etsi_ts/123000_123099/123040/16.00.00_60/ts_123040v160000p.pdf).
For UCS-2 with an 8-bit UDH, explicit limits of 140 and 134 encoded payload octets
produce at most 70 characters in a single part or 67 per multipart fragment;
prepend the UDH only to multipart fragments. A 16-bit UDH needs a multipart limit
of 132 usable UCS-2 octets. The helper does not infer these limits from SMPP fields.

## Bounded reassembly

`SegmentReassembler(maximumGroups, maximumSegments, maximumBytes,
maximumMessageBytes, lifetime, nanoClock)` owns explicitly bounded fragment state.
All limits are positive. `ReassemblyKey(namespace, reference)` bounds the namespace
to 256 UTF-16 units and the reference to 0..65535. Include source, destination,
encoding and provider or connection generation where they identify separate
messages. Reuse within a retained group's lifetime remains the caller's decision.

`accept(key, segment)` handles out-of-order parts and returns the complete ordered
raw `OctetString` once. Identical duplicates return empty. A conflicting duplicate,
total, reference or per-message size throws `IllegalArgumentException` and
preserves valid existing fragments. Exhausted group, fragment or total-byte
capacity throws `IllegalStateException`; it does not evict another live group.
Admission first removes expired groups, so a failed admission may still release
expired state. Empty fragments consume segment and group capacity.

Completed groups retain their fragments for duplicate suppression until the same
fixed deadline as incomplete groups. They count against every applicable bound;
the byte count is the sum of retained payload octets, not a heap-size estimate.
Metadata is additionally bounded by at most 255 reference slots per group and
the bounded key. Returned assembled copies are caller-owned and outside retained
state accounting. `retainedGroups()`, `retainedSegments()` and `retainedBytes()`
report current retained state without implicitly advancing time.

The clock is an injected prompt monotonic `LongSupplier`. Deadlines use signed
subtraction, including `System.nanoTime()` wraparound. Lifetime must be positive
and at most `Long.MAX_VALUE / 4` nanoseconds. The caller must sample/expire within
the monotonic half-range; signed clock values may be negative. The first part
starts the lifetime; duplicates and later parts never extend it. `expire()` and
`accept()` drive expiry, with no scheduler or background thread. `close()` is
idempotent, clears all retained state and permanently rejects admission. All
state operations are synchronized; no application callback runs inside them.

## Delivery receipts

`DeliveryReceipts.parse(rawText, ReceiptFormat)` returns an immutable
`DeliveryReceipt` containing the exact original text and an ordered map of raw
values. It makes no message-ID numeric conversion, date/time-zone inference,
delivery-state inference or automatic request correlation. `field(label)` uses
canonical lowercase labels and returns empty for absent fields.

Textual receipt formats are vendor-specific. SMPP 3.4 Appendix B gives a typical
example, not a universal grammar. These are explicit library profiles:

| Profile | Required fields and interpretation |
| --- | --- |
| `EXAMPLE` | All eight lowercase labels `id`, `sub`, `dlvrd`, `submit date`, `done date`, `stat`, `err`, `text`; counts/error are three decimal digits, dates are ten raw digits, state has seven UTF-16 units, text at most twenty. ID is an opaque nonempty token of at most 64 units, deliberately allowing hexadecimal/case/leading zeros instead of enforcing the appendix's decimal example. Calendar validity and the state vocabulary are not inferred. |
| `FLEXIBLE` | Fields may be absent, labels are case-insensitive and normalized to lowercase, and unknown labels/states/raw date spellings remain available. Empty text means no fields. |

Both profiles bound input to 4096 UTF-16 units, 32 fields and 32 units per label.
Recognized labels use ASCII letters followed by letters, digits or underscores,
with an optional ` date` suffix. Fields are separated by ASCII spaces; separator
spacing is retained in `rawText()`. The terminal `text:` field owns the complete
remainder, including embedded label-like text. Duplicate normalized labels and
unrecognized leading text are rejected. A malformed receipt throws
`IllegalArgumentException`; it never fabricates a delivery status. These helper
profiles are documented choices around
[SMPP 3.4 Appendix B](https://smpp.org/SMPP_v3_4_Issue1_2.pdf).

`DeliveryReceipts.build(fields, format)` emits known fields in canonical order,
then unknown fields in supplied iteration order, with `text` last. It requires
explicit values and adds none. To avoid ambiguity, non-text values containing
field markers or beginning/ending with ASCII spaces are rejected without
trimming. The text value retains all its spaces. Parsing an accepted provider
text preserves its original text; canonical rebuilding is an explicit operation
and is not a promise of byte-for-byte formatting reproduction.

`ReceiptTlvs.read(parameters)` preserves the complete original immutable TLV
block, with a limit of 64 entries and 65535 total value octets. It provides
independent optional views of `receipted_message_id` (0x001e, one ASCII C-octet
string including a final NUL, 1..65 octets), `message_state` (0x0427, one unsigned
raw octet) and `network_error_code` (0x0423, exactly three raw octets). Missing
values stay absent; unknown state values, ID case/leading zeros and unrelated
TLVs remain intact. Duplicate known fields or malformed values are rejected.
An explicitly present empty ID remains distinct from a missing ID.

`ReceiptTlvs.build(Optional<String>, OptionalInt, Optional<OctetString>)` builds
only supplied fields, in ID/state/error order. It uses the same validation and
preserves unknown unsigned state values. Text and TLV receipts are independent
views: when they disagree, the caller chooses a provider-specific precedence
policy. The helper does not classify a `deliver_sm` or `data_sm`, infer `esm_class`
or establish a receipt-to-message association. Field definitions are in
[SMPP 3.4 sections 5.3.2.12, 5.3.2.31 and 5.3.2.35](https://smpp.org/SMPP_v3_4_Issue1_2.pdf)
and [SMPP 5.0 sections 4.8.4.37, 4.8.4.42 and 4.8.4.47](https://smpp.org/SMPP_v5.pdf).

## Simulator content scenarios

The package-private simulator adapter
`HelperContentPlans.create(variant, payloadBytes, seed)` implements its
`ContentPlan` seam. `names()` returns the immutable available names;
`supports(variant, operation)` checks content/operation pairing before endpoint
creation. The simulator separately checks endpoint role and bind mode. The
operation adapter selects short-message versus message-payload placement and
enforces version-specific limits. A receive-only endpoint (`--operation=none`)
skips the send check and can still validate incoming content.
Both ends must select the same variant, size and seed for exact fixture validation.

| `--content` | Explicit `--payload` meaning | Send scenarios |
| --- | --- | --- |
| `gsm7` | Exact unpacked GSM octets, 0..65535; includes extension characters | `submit`, `deliver`, `data`, `multi` |
| `ucs2` | Exact even UCS-2 octets, 0..65534 | `submit`, `deliver`, `data`, `multi` |
| `sar` | One complete logical GSM fixture, 161..39015 octets that fit 255 parts at a 153-octet part limit | `submit`, `deliver`, `data`, `multi` |
| `receipt` | Final ASCII text-field length, 0..20; fixed receipt metadata adds further octets | Server/MC-originated `deliver`, `data` |
| `receipt-flexible` | Final ASCII text-field length, 0..2000; includes an unknown provider state/field and absent `dlvrd` | Server/MC-originated `deliver`, `data` |
| `receipt-tlv` | Must be 0; receipt ID, state and network-error TLVs carry the fixture | Server/MC-originated `deliver`, `data` |

For example, select `--content=receipt --payload=20`,
`--content=receipt-flexible --payload=160`, `--content=receipt-tlv --payload=0`,
`--content=ucs2 --payload=140` or `--content=sar --payload=306`, together with an
allowed operation and the rest of the simulator configuration. A default payload
of 160 deliberately fails for `receipt`; it is never truncated. These helper
scenarios exclude `replace` because `replace_sm` has no `data_coding` or
`esm_class` with which to declare their encoding or receipt flags. Replacement
requires an explicit interpretation of the original message, supplied by the
application or operation adapter. This is a bounded simulator scenario choice,
not a protocol-wide restriction on replacing encoded message bytes. SMPP 3.4
uses the mandatory short-message field; SMPP 5.0 also permits message_payload.
See [SMPP 3.4 section 4.10](https://smpp.org/SMPP_v3_4_Issue1_2.pdf) and
[SMPP 5.0 section 4.5.3](https://smpp.org/SMPP_v5.pdf).

Text and receipt scenarios repeat one deterministic fixture. Receipt traffic
originates from the server/MC; a receive-only client can validate it. This is an
explicit simulator scenario policy, not the complete protocol direction matrix:
[SMPP 5.0 section 4.7.12](https://smpp.org/SMPP_v5.pdf) also lists the MC delivery
receipt message type for ESME-to-MC submission fields, including `data_sm`.

SAR selects a part by `index % total` and deliberately repeats the same fixed
reference on later cycles. With several originating connections, the caller
passes a per-connection ordinal to `next`; a global ordinal would route only a
subset of parts to some connections. Each receive connection generation owns a
separate plan. One bounded reassembler retains that fixture's duplicate history with a paused fixture clock
for the run: at most one group, 255 fragments and the configured logical bytes.
It never merges streams or accumulates an unbounded reference history. Expected
payload and part copies are also bounded by the same configured logical length.
`incompleteAssemblies()` is 1 after any valid part until the first completion,
otherwise 0. The simulator reports the sum at shutdown. Repeated cycles test
duplicate handling; they are not counts of newly completed logical messages.
An entirely absent fixture has no observed partial assembly and therefore counts
as 0 here; the simulator's send/receive accounting handles missing traffic.

These helpers do not implement automatic receipt correlation, provider-specific
text normalization, GSM septet packing, arbitrary UDH composition, national shift
tables, background reassembly cleanup or automatic retransmission. Their test and
per-type review evidence is in [the Step 15 review](reviews/0014-message-helpers.md).
The [message architecture review](reviews/0014-message-architecture.md) records
the pure-package dependency rule and its negative fixture.
