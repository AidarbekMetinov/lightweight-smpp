# Header and framing contracts

Step 3 implements the common SMPP 3.4/5.0 wire envelope. Its three production
types have no runtime dependencies. Command bodies, session permissions,
sequence allocation, networking, and protocol error responses remain later steps.

## Header values and translation

`kg.aidarbek.smpp.protocol.PduHeader` is an immutable record with four unsigned
four-octet fields represented by Java `long`: `commandLength`, `commandId`,
`commandStatus`, and `sequenceNumber`. Length includes the 16-octet header and
must be between 16 and `0xffffffff`; the other fields preserve the full unsigned
range. Record equality and hashing compare the four scalar values.

Unknown commands/statuses and raw sequence zero or high-bit values survive
translation. Request sequence legality belongs to the session layer; a header
alone does not establish a legal request. Both specifications allow a zero
sequence in certain `generic_nack` cases when the request header cannot be decoded.
See [SMPP 3.4](https://smpp.org/SMPP_v3_4_Issue1_2.pdf), §§3.1–3.2, 4.3.1, and 5.1,
and [SMPP 5.0](https://smpp.org/SMPP_v5.pdf), §§3.1–3.2, 4.1.4.1, 4.7.4, and 4.7.24.

`kg.aidarbek.smpp.codec.PduHeaderCodec` translates exactly 16 bytes at a
`ByteBuffer`'s current position, always in network byte order. It preserves the
buffer's limit and byte-order setting. Decode accepts heap, direct, sliced, and
read-only buffers; encode needs a writable destination. Successful calls advance
the position by 16; failed calls do not advance it or partially write output.

Short decode input throws `BufferUnderflowException`; short output throws
`BufferOverflowException`. An encoded length below 16 throws
`IllegalArgumentException`. Null arguments throw `NullPointerException`.
Encoding to read-only output throws `ReadOnlyBufferException`.

## Stream assembly and ownership

`kg.aidarbek.smpp.codec.PduFramer` is a thread-confined incremental reader. Its
constructor requires an explicit maximum total frame size of at least 16 octets.
This is an allocation policy; it does not redefine a command's field limits.
The endpoint baseline remains a configurable 1 MiB maximum when endpoints arrive.

`read(ByteBuffer)` returns `Optional<byte[]>` and consumes at most one complete
frame. Repeat with the same input to obtain additional coalesced frames. An empty
result means the available bytes were consumed and retained until the next call.
This pull contract lets the caller control admission without an internal output
queue or callback lifecycle.

The framer collects four length bytes first, interprets them as unsigned, and
rejects a length below 16 or above the configured maximum before allocating body
storage. It then retains at most one bounded frame. Rejection consumes exactly
through the fourth length byte, releases retained storage, throws
`IllegalArgumentException`, and permanently terminates the framer. Earlier frames
have already been returned; a later error cannot silently discard them.

Consumed input is copied, so callers may reuse backing storage. A returned array
includes the header and transfers exclusively to the caller. Mutating it cannot
change subsequent frames. Source limit/order are preserved, and read-only/direct
input works. No payload bytes appear in errors.

Call `endOfInput()` after draining supplied buffers. A complete frame boundary or
empty stream ends successfully; an incomplete header/body throws
`IllegalArgumentException`. Either result terminates and releases retained storage.
Repeated end calls are harmless. Further reads throw `IllegalStateException`.
There is no resynchronization after malformed framing; use a new framer for a new
stream. Null input throws `NullPointerException` without damaging active state.

## Verification

`PduHeaderTest`, `PduHeaderCodecTest`, and `PduFramerTest` cover `FRAME-01` through
`FRAME-08`, including independent wire bytes, every split boundary of a fixture,
coalescing, unsigned bounds, position/order behavior, and ownership/termination.
`ArchitectureTest` verifies actual production imports, allowed dependencies,
infrastructure separation, and package cycles. Isolated injected violations
demonstrated detection with unchanged assertions.

Run `./gradlew check --console=plain`. The [change review](reviews/0002-pdu-framing.md)
records observed red/green runs, the exact final source inventory, and per-type
SOLID findings. Operation, session, and independent-peer evidence remain pending
in [the protocol inventory](PROTOCOL.md).
