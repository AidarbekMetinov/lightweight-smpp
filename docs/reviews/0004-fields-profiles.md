# Review: Field primitives, raw TLVs and version profile foundation

## Scope

Starting revision: `bde0e1e` (`Set up code checks`). Implementation was developed
in detached worktree `/tmp/lightweight-smpp-step5`, independently of the Step 3
framing and Step 4 review-tool changes that precede integration. The integration
baseline is `06f632b` (`Check SOLID review coverage`). This report
covers 21 new Java source files and all 23 contained types: 12 top-level production
types, two nested records, and nine test classes. There are no local or anonymous
Java types in this increment. The updated `ArchitectureTest` is also reviewed
below, for 24 affected type identities in total. Final formatted source hashes
are in each record below.

The planned responsibilities before implementation were primitive wire codecs,
owned raw TLV values/framing, explicit version catalogues/occurrence rules and a
separate typed interpretation boundary. The resulting APIs retain those boundaries:

- `FieldReader` owns a copy of a bounded complete byte block; `FieldWriter` owns a
  fixed-capacity buffer. Unsigned integers use big endian order. C-octet string
  limits include the terminator and accept only ASCII; failures leave the current
  read/write state unchanged. These mutable cursors require thread confinement.
- `Tlv` and `OptionalParameters` have immutable, owned data and explicit binary,
  order-sensitive equality. The collection can hold mandatory TLVs as well as
  optional ones. `TlvCodec` preserves all tags/order/repetitions and applies byte,
  count and value-width bounds before reading or allocating claimed values.
- `ProtocolProfile` contains exact specification membership (27/33 command IDs and
  44/64 distinct TLV tags). Membership does not advertise implemented command
  codecs, field semantics, endpoint permission or peer capability.
- Occurrence rules cover bind responses, unbind/enquiry responses, generic_nack,
  and 5.0 broadcast requests with explicit priority. Supported singleton duplicates
  and missing required tags fail; unknown/unexpected incoming entries stay raw and
  are ignored semantically. Outgoing unexpected entries fail. Immediate broadcast
  priority omits repetition and ignores that parameter if received.
- Typed interpretation covers raw `sc_interface_version` octets on both profiles'
  three bind responses, and 0..100 `congestion_state` on defined 5.0 responses.
  Unknown advertisements remain numeric data for later version policy. Reserved
  incoming congestion values produce an empty interpretation; outgoing values
  outside that codec's supported domain fail. Registry extension rejects duplicate
  profile/command/tag keys and remains subject to ordinary raw block bounds.

This is a foundation, not a completed command-codec or full TLV-semantic claim.
Value lengths/content for the other 62 tags, callback count correlations, other
cross-field rules, response status/body rules, endpoint roles, version negotiation
and session/application services remain at their roadmap steps. The field and raw
TLV wire contracts are identical for client/server origin; role behavior is not
claimed by these network-independent fixtures.

Primary-source checks used the original PDF text already downloaded for project
research. The tool browser was also asked to open both original PDFs and timed out;
the local complete copies were available and were inspected directly:
[SMPP 3.4 issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf), §§3.1, 3.3 and 5.3;
[SMPP 5.0](https://smpp.org/SMPP_v5.pdf), §§2.11.1, 3.1, 4.4.1.1, 4.4.2,
4.8.4.13, 4.8.4.18 and 4.8.4.51. The independent integer fixture `A3 12` is 41746;
the TLV fixture `00 07 00 01 04` follows the parameter-type example. Command/tag
catalogues were reconciled against `docs/PROTOCOL.md` and `docs/TLVS.md`.

## TDD and verification

Every numbered development cycle below ran the test first with only the necessary
API declarations or prior behavior present, observed the relevant failure, then
implemented the selected behavior and observed a passing focused run. There were
no unconditional `fail()` tests or substituted dependency/compilation failures.
Some malformed-TLV cases added in cycle 14 already passed through the tested field
reader; the new entry-count scenario supplied that cycle's actual red.

All Gradle commands ran from `/tmp/lightweight-smpp-step5` and included
`--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=fields-profiles'`.
The distinct daemon option avoided filesystem-watcher conflicts between isolated
worktrees without changing project build configuration. Let
`C(CLASS)` mean `./gradlew test --tests 'CLASS'` plus those exact options;
all abbreviated class names below are in `kg.aidarbek.smpp`, with the package shown.
For each row, logs `/tmp/step5-NN-red.log` and `/tmp/step5-NN-green.log` retain the
actual output. Every row's green `:test` executed freshly; normal compilation and
configuration caches remained enabled.

| Cycle | Red command and selected test | Observed red | Green command/result |
| --- | --- | --- | --- |
| 01 | `C(codec.FieldReaderTest)`; `readsUnsignedIntegersFromIndependentBigEndianBytes` | Expected 255, got 0. | Same; pass. |
| 02 | `C(codec.FieldReaderTest)`; `truncatedIntegerDoesNotConsumeAnyBytes` | ArrayIndexOutOfBoundsException instead of FieldCodecException. | Same; pass. |
| 03 | `C(codec.FieldReaderTest)`; `boundsAndOwnsInputAndReturnedOctets` | Oversized input accepted. | Same; pass. |
| 04 | `C(codec.FieldReaderTest)`; `readsAsciiStringWithinTerminatorInclusiveBound` | Expected Hello, got empty string. | Same; pass. |
| 05 | `C(codec.FieldReaderTest)`; `malformedStringsLeaveReaderAtTheStart` | Unterminated input raised array bounds exception. | Same; pass. |
| 06 | `C(codec.FieldWriterTest)`; `writesUnsignedIntegersAsIndependentBigEndianBytes` | Expected seven bytes, got zero. | Same; pass. |
| 07 | `C(codec.FieldWriterTest)`; `rejectsOutOfRangeAndOversizedWritesWithoutPartialOutput` | NegativeArraySizeException instead of IllegalArgumentException. | Same; pass. |
| 08 | `C(codec.FieldWriterTest)`; `writesTerminatedAsciiAndOwnedRawOctets` | Expected nine bytes, got zero. | Same; pass. |
| 09 | `C(codec.FieldWriterTest)`; `rejectsNonAsciiEmbeddedNullAndStringBoundsAtomically` | Rejected write left three partial bytes. | `C(codec.Field*Test)`; pass. |
| 10 | `C(protocol.TlvTest)`; `ownsValueOctetsAndUsesBinaryEqualityWithoutLeakingPayload` | Mutating input changed value byte 115 to 0. | Same; pass. |
| 11 | `C(protocol.TlvTest)`; `enforcesUnsignedTagAndValueLengthWithoutInterpretingUnknownTags` | Invalid tag accepted. | Same; pass. |
| 12 | `C(protocol.OptionalParametersTest)`; `retainsRepeatedUnknownTagsInAnImmutableOrderedValue` | Clearing caller list emptied stored entries. | Same; pass. |
| 13 | `C(codec.TlvCodecTest)`; `decodesKnownUnknownRepeatedAndEmptyValuesInWireOrder` | Expected three entries, got zero. | Same; pass. |
| 14 | `C(codec.TlvCodecTest)`; `rejectsCountAndByteBoundsBeforeAcceptingOptionalData` | Excess entry count accepted. | Same; pass; malformed header/value characterization also passed. |
| 15 | `C(codec.TlvCodecTest)`; `encodesOrderedRawValuesAsIndependentBytes` | Expected 14 bytes, got zero. | Same; pass. |
| 16 | `C(codec.TlvCodecTest)`; `boundsEncodingBeforeAllocatingAndAcceptsMaximumWireValueLength` | Oversized block encoding accepted. | Same; pass, including 65535-byte value and aggregate overflow guard. |
| 17 | `C(profile.ProtocolProfileTest)`; `definesExactVersionedCommandInventoryWithoutImplyingCodecSupport` | Expected 27 common IDs, got empty set. | Same; pass. |
| 18 | `C(profile.ProtocolProfileTest)`; `definesExactTagCatalogueForEachVersionWhileUnknownTagsRemainRaw` | Expected 44 common tags, got empty set. | Same; pass. |
| 19 | `C(profile.TlvRulesTest)`; `enforcesRequiredAndSingletonOccurrencesButRetainsIncomingExtensions` | Missing required parameter accepted. | Same; pass. |
| 20 | `C(profile.TlvRulesTest)`; `rejectsInconsistentOrInvalidRuleDeclarations` | Inconsistent required subset accepted. | Same; pass. |
| 21 | `C(profile.ProtocolProfileTest)`; `appliesBindAndControlResponseOccurrenceRulesToBothProfiles` | Required implemented rule result absent. | Same; pass. |
| 22 | `C(profile.ProtocolProfileTest)`; `broadcastRulesRequireDeclaredParametersAndRetainRepeatedAreas` | Valid content-type tag rejected by empty rules. | Same; pass. |
| 23 | `C(profile.ProtocolProfileTest)`; `immediateBroadcastOmitsRepetitionAndIgnoresItIfReceived` | Immediate request incorrectly required repetition. | `C(profile.*Test)`; pass. |
| 24 | `C(codec.UnsignedByteTlvCodecTest)`; `encodesAndInterpretsIndependentUnsignedOctets` | Expected Optional[255], got empty. | Same; pass. |
| 25 | `C(codec.UnsignedByteTlvCodecTest)`; `distinguishesMalformedLengthsFromUnsupportedReservedValues` | Reserved 101 interpreted as supported. | Same; pass. |
| 26 | `C(codec.TypedTlvRegistryTest)`; `appliesRegisteredInterpretationOnlyToItsVersionCommandAndTag` | Registered vendor value had no interpretation. | Same; pass. |
| 27 | `C(codec.TypedTlvRegistryTest)`; `rejectsAmbiguousRegistrationAndExtendsWithoutMutatingExistingRegistry` | Duplicate registration accepted. | Same; pass. |
| 28 | `C(codec.TypedTlvRegistryTest)`; `rejectsInvalidRegistrationContextsAndTagWidths` | 5.0 broadcast registration accepted in 3.4. | Same; pass. |
| 29 | `C(codec.TypedTlvRegistryTest)`; `standardInterpretationPreservesUnknownVersionAndLimitsCongestionToVersion5Responses` | Raw unknown advertisement 0x60 lost. | `./gradlew test` with the same options; all 30 tests pass. |

An independent reviewer noted that cycle 09's embedded-NUL example could fail on
capacity before inspecting the NUL. The additional
`FieldWriterTest.rejectsEmbeddedNullEvenWhenTheCompleteStringFits` first passed
against existing behavior (`/tmp/step5-30-characterization.log`). A temporary,
uncommitted removal of only `character == 0 ||` then caused that exact test to
fail because no exception was thrown (`/tmp/step5-30-probe-red.log`). Restoring the
same implementation passed with matching `FROM-CACHE` test output
(`/tmp/step5-30-restored-green.log`). This is a strengthened characterization and
fault-detection probe, not an invented new-behavior TDD cycle.

Final verification after adding public ownership/error/thread-safety documentation:

- The first combined formatting/check/Javadoc run succeeded but reported 11
  Javadoc missing-description warnings (`/tmp/step5-31-final.log`). Descriptions
  were added, and formatting was subsequently run before verification.
- `./gradlew spotlessApply` with the shared options passed; final formatting runs
  are `/tmp/step5-32-format.log` and `/tmp/step5-34-format.log`.
- `./gradlew check javadoc` with the shared options passed with **31 tests, zero
  failures/errors/skips and no warnings** after final source changes
  (`/tmp/step5-35-final.log`). The final test and Javadoc tasks executed freshly;
  formatting verification reused matching outputs. This isolated baseline's
  `check` includes Jupiter and Spotless; integrated architecture and review-tool
  verification is recorded separately by the integration owner.
- The Step 4 parser was invoked directly from its compiled isolated build:
  `java -cp /tmp/lightweight-smpp-review-tool/build/classes/java/review
  kg.aidarbek.smpp.review.ReviewCheck --inventory /tmp/lightweight-smpp-step5
  /tmp/step5-type-inventory.txt`. It discovered exactly the 23 types reviewed below,
  including `TypedTlvRegistry.Registration` and `TypedTlvRegistry.Key`. This uses
  javac parsing, not a regular-expression type inventory.
- The same compiled tool then checked the completed report with
  `java -cp /tmp/lightweight-smpp-review-tool/build/classes/java/review
  kg.aidarbek.smpp.review.ReviewCheck /tmp/lightweight-smpp-step5
  /tmp/step5-review-coverage.txt`; it exited successfully and wrote current
  coverage for all 23 types.
- Source/test files were inspected for additional nested/local declarations,
  compiler warnings, control-byte artifacts and untracked omissions. All 21 Java
  files were staged when creating `/tmp/step5-java.patch`; no probe mutation or
  temporary fixture remains in the final sources.

Independent code review checked the complete primitive/raw value/codec types,
profile rules and typed registry. Findings corrected were the misleading
embedded-NUL assertion and the generic encode wording: raw advertisement encoding
supports an octet, while advertised-version semantic policy belongs to bind
validation. No unresolved production or SOLID violation remains in these types.
The report's source hashes identify the final formatted implementation and tests.

## Per-type SOLID findings

## Type: kg.aidarbek.smpp.codec.FieldCodecException

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/FieldCodecException.java
type: kg.aidarbek.smpp.codec.FieldCodecException
sha256: 0620b818616e37a85b3f6481a81dc6ef27dfc910f774f175d0a9ebaf8a5342d0
responsibility: Communicate malformed or truncated bounded field structure without exposing value contents.
consumers: FieldReader, TlvCodec, UnsignedByteTlvCodec and callers handling IllegalArgumentException.
S: pass | Owns one structural failure category; messages describe offsets, lengths or counts and never copy credentials or payloads.
O: pass | New malformed-field cases use this category without changing unrelated framing, registry or session behavior.
L: pass | Preserves the IllegalArgumentException and Throwable contracts, including serialVersionUID; focused malformed-field tests catch it through its declared category. No custom resource or concurrent state contract is introduced.
I: pass | Consumers need an exception message and category only; the type introduces no parsing or transport methods.
D: pass | Depends only on its JDK exception supertype; wire readers raise it without depending on sessions or applications.
findings: none
```

## Type: kg.aidarbek.smpp.codec.FieldReader

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/FieldReader.java
type: kg.aidarbek.smpp.codec.FieldReader
sha256: 91ca4395b64686c4f669cc98c36887c84f163f3157b7ec1d306050a27673a450
responsibility: Decode sequential SMPP primitive fields inside an explicitly bounded owned byte block.
consumers: TlvCodec and future command body codecs; FieldReaderTest observes range, consumption and ownership contracts.
S: pass | All methods translate primitive wire representations and maintain a single read position. Frame assembly and field meaning remain separate.
O: pass | New command codecs compose the same reads and supply field limits; new tag semantics do not alter this reader.
L: pass | Final type with inherited identity equality; read contracts are atomic on failure, arrays are copied, unsigned results preserve width, and thread confinement is explicit. Five tests cover truncation, ASCII, limits and retained bytes.
I: pass | Primitive consumers request only octets, unsigned integers or C-octet strings and remaining length; no network or profile responsibilities are exposed.
D: pass | Depends on JDK arrays and explicit ASCII encoding plus FieldCodecException; no profile, transport, persistence or callback dependency.
findings: none
```

## Type: kg.aidarbek.smpp.codec.FieldWriter

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/FieldWriter.java
type: kg.aidarbek.smpp.codec.FieldWriter
sha256: 3f67ef29ba3a0fe3883338f32ca4fe5eb9664958e7758a8a893e797752ce7764
responsibility: Encode primitive SMPP fields within a fixed output capacity without partial failed writes.
consumers: TlvCodec and future command body codecs; FieldWriterTest exercises binary output and rejected writes.
S: pass | Range checks, ASCII checks and complete-length preflight serve the single wire-writing responsibility; no command or application policy is embedded.
O: pass | Callers compose stable primitive methods for new commands and field bounds; new TLV semantics do not require writer changes.
L: pass | Final type preserves Object identity behavior and returns copied snapshots. Writes are atomic on rejection and thread confinement is documented. Five tests include an ample-capacity embedded-NUL assertion confirmed with a temporary mutation probe.
I: pass | Consumers use only byte, unsigned integer, C-octet writes and output snapshots; no unused lifecycle or transport capability is required.
D: pass | Depends only on JDK ownership helpers and its private numeric checks, keeping external infrastructure outside primitive encoding.
findings: none
```

## Type: kg.aidarbek.smpp.codec.TlvCodec

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/TlvCodec.java
type: kg.aidarbek.smpp.codec.TlvCodec
sha256: 6e93d1168b0c8cbf9d645f4f67ebbc55c7f8a832d7d36211002f63d7d879fe95
responsibility: Frame complete raw TLV blocks with explicit aggregate byte and entry-count bounds.
consumers: Command body codecs, raw OptionalParameters/Tlv values, FieldReader/Writer, and TlvCodecTest.
S: pass | Owns tag/length/value framing only; it neither deduplicates nor interprets tags and never decides command permission.
O: pass | Unknown and vendor tags pass through the same stable raw loop; typed extensions belong in the separate registry and profiles.
L: pass | Stateless final utility has no substitutable subtype or resources. Input and results have owned copies, long aggregate arithmetic prevents overflow before allocation, and deterministic exceptions cover truncated headers/values. Five tests verify independent bytes, maximum length and limits.
I: pass | Encode and decode share the actual raw-block consumer boundary with explicit byte/count limits; no typed, role or callback methods are forced on callers.
D: pass | Depends on primitive field codecs, protocol values and JDK collections only. Profile and infrastructure decisions cannot bypass this allocation boundary.
findings: none
```

## Type: kg.aidarbek.smpp.codec.TlvValueCodec

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/TlvValueCodec.java
type: kg.aidarbek.smpp.codec.TlvValueCodec
sha256: 79411e55a086c56f4477da5481c2e6f8a702a399867a382150cb8e6d669d5b24
responsibility: Define the extension contract for interpreting and encoding one TLV value domain independently of tag framing.
consumers: TypedTlvRegistry consumes this abstraction; UnsignedByteTlvCodec is the initial implementation.
S: pass | All three methods describe one typed value domain: stable class metadata, bounded value decoding and encoding.
O: pass | New vendor or standard representations are supplied as codecs at the registry boundary without modifying raw TLV framing or unrelated policies.
L: pass | Explicit contracts require stable non-null reference metadata, immutable results, owned output arrays, non-mutated input, non-null Optional, thread safety and malformed-structure errors. UnsignedByteTlvCodecTest exercises supported and reserved domains through this interface.
I: pass | The registry requires all three methods for checked type dispatch and bidirectional translation; no command, socket or callback operations are included.
D: pass | The abstraction exposes only JDK Class, Optional and value arrays. Registry lookup depends on it; concrete standard codecs are composed at the standard factory.
findings: none
```

## Type: kg.aidarbek.smpp.codec.TypedTlvRegistry

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/TypedTlvRegistry.java
type: kg.aidarbek.smpp.codec.TypedTlvRegistry
sha256: e1012ca780d6ed8901949bfcbe138ca551ab62714a12b2729a11292f29fc7adc
responsibility: Register and dispatch typed TLV value interpretation by exact version, command and tag.
consumers: TlvValueCodec implementations, ProtocolProfile membership, immutable raw Tlv values and TypedTlvRegistryTest.
S: pass | Owns one lookup responsibility plus the initial standard registration composition. Occurrence, value-domain, frame-bound and session decisions stay in their respective collaborators.
O: pass | Constructor and with support explicit new contexts; duplicate composite keys are rejected even for different codecs, protecting existing registrations. Standard supported interpretations are deliberately limited to two tags.
L: pass | Immutable copied metadata preserves earlier registries after extension; supplied codecs must honor the interface thread-safety contract. Tests verify scope isolation, type mismatch, reserved values, unknown contexts and raw ownership. Identity equality is inherited without a value-equality promise.
I: pass | Consumers have focused registration extension and typed encode/decode APIs; absent incoming semantics use Optional while unsupported outgoing interpretation fails explicitly.
D: pass | Dispatch uses TlvValueCodec abstractions and protocol/profile values. The standard factory is the composition boundary for UnsignedByteTlvCodec; no transport or application implementation enters lookup.
findings: none
```

## Type: kg.aidarbek.smpp.codec.TypedTlvRegistry.Key

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/TypedTlvRegistry.java
type: kg.aidarbek.smpp.codec.TypedTlvRegistry.Key
sha256: e1012ca780d6ed8901949bfcbe138ca551ab62714a12b2729a11292f29fc7adc
responsibility: Provide the immutable exact profile/command/tag identity for registry lookup.
consumers: TypedTlvRegistry internal immutable map; not exposed as a public API.
S: pass | Contains only the three dimensions of registration ambiguity and dispatch; excludes Java value type so incompatible codecs cannot coexist under one wire context.
O: pass | Additional registered contexts become data entries; the key does not need command-specific switches or infrastructure extensions.
L: pass | Record-generated equality and hashing use immutable enum and numeric components. Registry tests distinguish version and command contexts while rejecting duplicate keys; no custom subtype, array or resource contract exists.
I: pass | Only registry map operations consume the record components; the private nested type adds no public obligations.
D: pass | Depends only on SmppVersion and primitive values, keeping stable key identity independent of codec implementations.
findings: none
```

## Type: kg.aidarbek.smpp.codec.TypedTlvRegistry.Registration

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/TypedTlvRegistry.java
type: kg.aidarbek.smpp.codec.TypedTlvRegistry.Registration
sha256: e1012ca780d6ed8901949bfcbe138ca551ab62714a12b2729a11292f29fc7adc
responsibility: Bind one conforming typed value codec to a valid version, command and unsigned tag.
consumers: TypedTlvRegistry constructors/with and application-supplied codec registration.
S: pass | Validates only registration metadata and retains the codec explicitly; it does not parse values or grant outgoing command permission.
O: pass | Each new standard or vendor interpretation is another descriptor using TlvValueCodec rather than edits to raw codecs.
L: pass | Record equality includes the supplied codec under that implementation's equality contract; codec ownership is retained, not deep-copied. Version/codec nulls, undefined commands and invalid tag widths fail construction. Registration tests cover these invariants.
I: pass | Four components provide exactly the metadata needed by registry consumers; no broader application service contract is imposed.
D: pass | Depends on the TlvValueCodec abstraction, profile membership and JDK values, with concrete codec construction left to the caller or standard factory.
findings: none
```

## Type: kg.aidarbek.smpp.codec.UnsignedByteTlvCodec

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/UnsignedByteTlvCodec.java
type: kg.aidarbek.smpp.codec.UnsignedByteTlvCodec
sha256: 42253a7824b3a958dc7205750e3c88655c81257cf64826115f392eafab9c154e
responsibility: Interpret one unsigned TLV octet within a configured inclusive supported range.
consumers: TlvValueCodec consumers and the registry's version-advertisement/congestion registrations; UnsignedByteTlvCodecTest.
S: pass | Owns one-octet length, numeric domain and owned encoding; it knows neither the tag nor the enclosing command or application meaning.
O: pass | Different supported maxima are configuration; other wire representations receive separate TlvValueCodec implementations.
L: pass | Implements every interface promise with immutable Integer results, stable Integer.class metadata, copied one-octet output and stateless reads. Interface-typed tests cover maximums 255/100, reserved incoming values, strict outgoing rejection, nulls and malformed lengths.
I: pass | Implements only class metadata and value encode/decode needed by the registry; no unsupported interface methods are present.
D: pass | Depends on the value-codec interface, structural failure category and JDK Optional/Objects, with no profile or infrastructure dependency.
findings: none
```

## Type: kg.aidarbek.smpp.profile.ProtocolProfile

```solid-review
source: src/main/java/kg/aidarbek/smpp/profile/ProtocolProfile.java
type: kg.aidarbek.smpp.profile.ProtocolProfile
sha256: cd7607efa54ed19544e21569d7d1f5efc63741051d7b82369ff42562ca1a11e3
responsibility: Expose exact versioned specification membership and the implemented command TLV occurrence rules.
consumers: TypedTlvRegistry registration checks/standard composition, future command validators, and ProtocolProfileTest.
S: pass | Catalogue membership and occurrence sets share the version-rule change driver. Value bytes, negotiation, roles, response bodies and application policy are explicitly outside this descriptor.
O: pass | New supported rule contexts are added at this profile boundary; raw codecs and request tracking need no edits. Unknown commands return no rule implementation, preventing catalogue membership from masquerading as codec support.
L: pass | Immutable final descriptors return unmodifiable sets and exact-width membership; inherited Object identity is not advertised as version-value equality. Five tests cover both exact inventories, unknown/wide values, command contexts, required/repeated broadcast tags and immediate-priority exception.
I: pass | Consumers can query membership without decoding, request optional simple command rules or supply broadcast priority to its dedicated rule method; no unrelated role or transport methods are required.
D: pass | Depends only on profile data/TlvRules and JDK collections. Protocol values are reached through occurrence-rule contracts; codecs never become dependencies of the profile layer.
findings: none
```

## Type: kg.aidarbek.smpp.profile.SmppVersion

```solid-review
source: src/main/java/kg/aidarbek/smpp/profile/SmppVersion.java
type: kg.aidarbek.smpp.profile.SmppVersion
sha256: f3434a677ec1b1bf5d77d91c1aeaf951b6cc5b613b7819ca9402bb3c877a946f
responsibility: Name the two supported specification identities and their interface-version octets.
consumers: ProtocolProfile and TypedTlvRegistry metadata; ProtocolProfileTest verifies wire values.
S: pass | Owns only stable specification identity and its explicit unsigned octet; raw unrecognized peer advertisements remain separate data.
O: pass | Future supported specification identities would be introduced at this explicit catalogue boundary, rather than inferred in socket or field code.
L: pass | Enum identity, comparison and serialization retain inherited Enum contracts; values are immutable and tests assert 0x34/0x50. No custom subclass, resource or cancellation contract applies.
I: pass | Consumers need named identity and a wire octet only; the enum does not expose command, session or negotiation services.
D: pass | Depends solely on Java enum/value facilities; higher-level profiles and codec registration consume it.
findings: none
```

## Type: kg.aidarbek.smpp.profile.TlvRules

```solid-review
source: src/main/java/kg/aidarbek/smpp/profile/TlvRules.java
type: kg.aidarbek.smpp.profile.TlvRules
sha256: 43a77ef951a5346c54b684f55807c1ec8ff9d1705303fa241be49b411b366620
responsibility: Validate permitted, required and repeatable tag occurrences for a supplied command context.
consumers: ProtocolProfile declarations, bounded raw OptionalParameters blocks and TlvRulesTest.
S: pass | Owns occurrence policy only, with distinct incoming compatibility and outgoing permission operations. Value content and cross-field correlations remain separate.
O: pass | New command/vendor tag sets are declarative inputs; existing validation control flow is unchanged when those sets vary.
L: pass | Final immutable sets are copied and validated as consistent unsigned-tag subsets. Incoming unexpected entries remain untouched, supported singleton duplicates and missing required tags fail, and outgoing unexpected tags fail. Tests exercise each contract without claiming full command semantics.
I: pass | Callers choose exactly incoming or outgoing validation; they need not implement callbacks or provide unused field/body/session data.
D: pass | Depends only on raw protocol values and JDK collections, keeping field codecs and infrastructure below neither direction of this policy.
findings: none
```

## Type: kg.aidarbek.smpp.protocol.OptionalParameters

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/OptionalParameters.java
type: kg.aidarbek.smpp.protocol.OptionalParameters
sha256: 955c82c2269853c379e4406fcb2dc9cc44b827c65b30b38c1f406c44d1db1521
responsibility: Own an immutable ordered raw TLV collection preserving unknown and repeated entries.
consumers: TlvCodec, TlvRules and command values; OptionalParametersTest checks ownership/order/equality.
S: pass | Owns collection order, null invariants and value equality only. Despite its conventional name, command-required TLVs can be represented without interpretation.
O: pass | New tags and permitted multiplicity use ordinary entries; adding semantics cannot require collection-storage changes.
L: pass | List.copyOf prevents mutable collection exposure and Tlv provides immutable element contents. Explicit equality/hashCode are order-sensitive; tests cover source mutation, returned-list mutation, reversed order, equal values and null rejection.
I: pass | Consumers need only ordered immutable entries and value methods; no map coercion, typed callback or networking API is imposed.
D: pass | Depends on Tlv and JDK List only; wire and command-policy dependencies point toward this value type.
findings: none
```

## Type: kg.aidarbek.smpp.protocol.Tlv

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/Tlv.java
type: kg.aidarbek.smpp.protocol.Tlv
sha256: 48cf39d6da11592c290dd6cf32a1a2d0894e4ae9ad22732566b69c118b512fac
responsibility: Own one immutable raw unsigned tag and binary value with an explicit wire-width invariant.
consumers: OptionalParameters, raw/typed codecs and occurrence rules; TlvTest validates ownership/equality/ranges.
S: pass | Owns only tag/value invariants, binary equality and payload-safe diagnostics; it neither interprets tags nor performs I/O.
O: pass | Unknown and reserved tags already fit the raw value contract, so new standard/vendor semantics belong in codecs and profiles.
L: pass | Final value overrides equals/hashCode consistently using tag and byte contents, copies constructor/accessor arrays and preserves unknown tags. Tests cover mutation, equality, different tag/content, nulls, zero/max lengths and payload-safe toString.
I: pass | Consumers receive only tag, value length, copied bytes and normal value operations; no application or codec interface is forced on them.
D: pass | Depends solely on JDK arrays/ownership helpers, forming the inward protocol boundary.
findings: none
```

## Type: kg.aidarbek.smpp.codec.FieldReaderTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/FieldReaderTest.java
type: kg.aidarbek.smpp.codec.FieldReaderTest
sha256: 05deeb6d9cf375639a4e23c83ac37fdfe761f3f361546f9d85cf17825110edbe
responsibility: Verify externally observable unsigned, octet and C-octet read contracts.
consumers: JUnit Jupiter, FieldReader and structural field failures.
S: pass | Five focused methods test one reader boundary: independently specified numeric/ASCII bytes, ownership, bounds and failure consumption.
O: pass | Additional field cases can be added here without changing production APIs or unrelated test fixtures.
L: pass | JUnit methods are deterministic and keep state local; assertions preserve the independent expected bytes and verify post-failure reader state. No custom test supertype or resource contract is introduced.
I: pass | Uses only the reader operations and assertions needed by each scenario; no broad fixture/server setup is required.
D: pass | Depends on Jupiter and the reader boundary plus JDK values, never transport, reflection into implementation or external peers.
findings: none
```

## Type: kg.aidarbek.smpp.codec.FieldWriterTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/FieldWriterTest.java
type: kg.aidarbek.smpp.codec.FieldWriterTest
sha256: 835208a8294abff919641c414f71cd55bb1ff9b4c3cbec5bdc038cf0fd65bd55
responsibility: Verify exact primitive wire output, ownership and atomic rejection of invalid writes.
consumers: JUnit Jupiter and FieldWriter.
S: pass | Five methods focus on one writer contract, including a separate ample-capacity NUL check so capacity cannot mask character validation.
O: pass | New primitive edge cases extend tests without adding production-only hooks or coupled network fixtures.
L: pass | Expected octets are independently specified and mutation checks observe public snapshots. The added NUL characterization passed, failed under removal of that validation clause, and passed after restoration. No custom inherited contract or timing dependence exists.
I: pass | Exercises writer methods directly with only the capacity and values needed by each case.
D: pass | Depends on Jupiter, FieldWriter and byte arrays; no transport or implementation-private state is accessed.
findings: none
```

## Type: kg.aidarbek.smpp.codec.TlvCodecTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/TlvCodecTest.java
type: kg.aidarbek.smpp.codec.TlvCodecTest
sha256: 84c62e0c182a608baddce9b02748b0df4d1446017df6aeded5bc6538aed4547f
responsibility: Verify raw TLV block framing, ordering and deterministic allocation-bound rejection.
consumers: JUnit Jupiter, TlvCodec and immutable raw protocol values.
S: pass | Five methods cover one raw-wire responsibility: independent fixtures, malformed structures, byte/count limits, max value width and aggregate overflow.
O: pass | Additional raw framing cases are data fixtures; command semantics and typed-value tests remain in their own consumers.
L: pass | Known bytes supplement round trips, and the overflow fixture repeats one small immutable value rather than allocating a huge wire block. Tests are local and deterministic with explicit exception assertions.
I: pass | Needs only raw encode/decode and value equality; no handler/profile/session methods or broad mock interface.
D: pass | Depends on Jupiter, raw codecs/values and JDK collection helpers, keeping semantic and network dependencies out of framing tests.
findings: none
```

## Type: kg.aidarbek.smpp.codec.TypedTlvRegistryTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/TypedTlvRegistryTest.java
type: kg.aidarbek.smpp.codec.TypedTlvRegistryTest
sha256: abeb294c192bf645ab3ca02bfebd3f859875b3d10138aab1f6172558e03723c9
responsibility: Verify context-specific typed dispatch, registration ambiguity and initial supported interpretations.
consumers: JUnit Jupiter, registry declarations, UnsignedByteTlvCodec, profiles and raw TLV codec/values.
S: pass | Four methods focus on the registry boundary: lookup scope, extension ownership, metadata rejection and standard context composition.
O: pass | Explicit registration fixtures exercise the real codec extension API instead of adding test-only dispatch paths.
L: pass | Tests preserve generic type checks and unchanged original registries, differentiate malformed/unsupported input, assert raw advertisement preservation and reapply block bounds after typed encoding. There are no fake interface implementations or mutable shared fixtures.
I: pass | Each scenario uses only registration and typed translation capabilities; value-domain and raw-framing details are exercised through their public boundaries.
D: pass | Depends on production codec/profile/value contracts and Jupiter; no direct internal-map access, sockets or persistence.
findings: none
```

## Type: kg.aidarbek.smpp.codec.UnsignedByteTlvCodecTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/UnsignedByteTlvCodecTest.java
type: kg.aidarbek.smpp.codec.UnsignedByteTlvCodecTest
sha256: de4db613b77e5c5cfa4f42187d8d8ccd6385a5d32ee54bc17004d5e833d8104d
responsibility: Verify the initial TlvValueCodec implementation against unsigned-domain and compatibility contracts.
consumers: JUnit Jupiter and TlvValueCodec<Integer> configured with UnsignedByteTlvCodec.
S: pass | Two methods test one value representation: independently encoded octets and the supported/reserved/malformed distinction.
O: pass | Additional configured domain boundaries can be added without modifying the registry or raw TLV codec.
L: pass | Calls through TlvValueCodec<Integer> verify its substitutable metadata, Optional, exception and ownership promises for supported maxima 255 and 100. No custom fixture subtype or resource lifecycle is needed.
I: pass | Uses exactly the interface metadata and encode/decode operations consumed by the registry.
D: pass | Depends on Jupiter, the value-codec abstraction/concrete configuration and JDK values only.
findings: none
```

## Type: kg.aidarbek.smpp.profile.ProtocolProfileTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/profile/ProtocolProfileTest.java
type: kg.aidarbek.smpp.profile.ProtocolProfileTest
sha256: fc779cef61a53f6dc084ae404b05b314a60ed6a22c27bf2d84443aeaa4baec11
responsibility: Verify exact catalogue membership and the implemented profile-specific occurrence contexts.
consumers: JUnit Jupiter, ProtocolProfile, SmppVersion, TlvRules and raw TLV values.
S: pass | Five methods trace one profile boundary: exact command/tag sets, bind/control rules, broadcast requirements/repetition and priority exception.
O: pass | New profile contexts extend dedicated scenarios while the tests keep catalogue membership separate from implemented codecs and permissions.
L: pass | Both profiles have independently listed inventory expectations, unsigned-width probes and immutable-set assertions. Occurrence fixtures deliberately do not claim complete TLV value semantics or callback correlations; all state is local and deterministic.
I: pass | Uses membership queries and context-specific rule methods only, without requiring negotiation, transport or application handlers.
D: pass | Depends on profile and raw-value contracts plus Jupiter/JDK sets; no codec-private state, external service or socket dependency.
findings: none
```

## Type: kg.aidarbek.smpp.profile.TlvRulesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/profile/TlvRulesTest.java
type: kg.aidarbek.smpp.profile.TlvRulesTest
sha256: b5a8518805360c2ddafcf2bbe75f049bb5bf44ccea6c2e40f340bd2319c07620
responsibility: Verify occurrence-policy declarations and incoming/outgoing differences independent of any one command.
consumers: JUnit Jupiter, TlvRules and raw OptionalParameters/Tlv values.
S: pass | Two methods exercise the one policy responsibility: valid immutable declarations and observable required/singleton/repeat/extension decisions.
O: pass | Rule variation is supplied as tag sets, demonstrating the declarative extension boundary without changing validation code.
L: pass | Tests mutate the source set after construction, cover invalid tag/subset declarations, and check both permitted repetitions and untouched unsupported incoming values. No substitute fixture contracts or resources are introduced.
I: pass | Uses only construction and the two validation operations; no broad profile or handler interface is imposed.
D: pass | Depends on Jupiter, occurrence policy and protocol values only; wire parsing and infrastructure are absent.
findings: none
```

## Type: kg.aidarbek.smpp.protocol.OptionalParametersTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/protocol/OptionalParametersTest.java
type: kg.aidarbek.smpp.protocol.OptionalParametersTest
sha256: 570800d418699a0ab82d45d37ec98ebd05bba4bb7b3318c4adc1df23f1122305
responsibility: Verify ordered raw collection ownership and equality through the public value API.
consumers: JUnit Jupiter, OptionalParameters, Tlv and JDK lists.
S: pass | One focused scenario observes source-list mutation, unmodifiable access, repeated tag order, equality/hashCode and null invariants.
O: pass | Additional collection contract examples fit this value-focused class; no codec or session fixture must change.
L: pass | Uses independently constructed equal/reordered values and mutation attempts to verify Object and collection contracts; state is local and there is no custom inherited test contract.
I: pass | Uses only constructor, entries and normal value methods required by collection consumers.
D: pass | Depends on Jupiter and protocol values/JDK lists, preserving inward value-layer test dependencies.
findings: none
```

## Type: kg.aidarbek.smpp.protocol.TlvTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/protocol/TlvTest.java
type: kg.aidarbek.smpp.protocol.TlvTest
sha256: 1c35732ed881c20070a846c6bf063adcbdf560fb971fbe39ab66326db6c9909c
responsibility: Verify raw TLV ownership, binary equality, wire widths and safe diagnostics.
consumers: JUnit Jupiter and immutable Tlv values.
S: pass | Two methods focus only on one value boundary; no command-specific meaning or codec behavior is asserted here.
O: pass | New tag examples fit the existing unknown-tag contract without changes to test harnesses or production extension hooks.
L: pass | Asserts copied input/accessor arrays, consistent equality/hashCode, differing binary values/tags, empty/max widths and null rejection. Tests use local values and no resource or asynchronous contracts.
I: pass | Exercises only the value constructor/accessors/Object methods needed by actual consumers.
D: pass | Depends on Jupiter, Tlv and byte-array assertions; no higher-layer policy or transport dependencies.
findings: none
```

## Independent architecture review and integrated library verification

This updates the architecture test from Step 3 commit `c684414`. The prior
formatted test hash was
`0353f7416b12f196e6b84e2d01ad8096243a9109fb3014c8855c0540e5e2c0bf`.
The work ran only in `/tmp/lightweight-smpp-archunit-step3`; the root checkout
and the field/profile agent's worktree were never modified by this reviewer.

The final `src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java` was
integrated into the root checkout. Production copies served as architecture-rule
inputs; their behavior and complete final-source reviews appear above.

### Change and actual scope

The profile package now has real production types. A new nonempty rule permits
its dependencies only on profile/protocol types and JDK language, collection,
math, and time facilities. The existing infrastructure prohibition continues to
exclude network, NIO channels/files, and `java.util.concurrent`. Codecs may
consume profiles; profiles cannot depend on codecs. The coverage assertion now
anchors both `ProtocolProfile` and `SmppVersion`, along with the three existing
header/framer types, while still checking that the architecture test itself is
excluded from imported production classes. The cycle rule selects all three
actual package groups. No empty-selection override was added.

### Type review

```solid-review
source: src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java
type: kg.aidarbek.smpp.architecture.ArchitectureTest
sha256: 28759b22ecfe105767c2e2eeb3d4a44c9753acb27292020b9722d8416a581159
responsibility: Enforce the production package dependency graph as version profiles join protocol values and codecs.
consumers: JUnit Jupiter discovers six test methods; ArchUnit imports production bytecode and checks dependencies and cycles; five production class identities anchor import coverage.
S: pass | All six tests enforce one project architecture requirement: select real production types, enforce allowed edges and infrastructure exclusions, and reject package cycles. The class adds no wire decoding, version policy, mutable fixtures, or resource lifecycle.
O: pass | Package scanning continues to include new production types automatically. The new rule is the local change required when a real profile boundary appears; supported commands and TLV codecs within these boundaries require no further test change. Explicit profile coverage anchors prevent a misleading empty-package result.
L: pass | The final package-private class has no custom superclass or interface and retains Object behavior. Its six zero-argument methods meet Jupiter discovery contracts, verified by six executed passing tests. The imported JavaClasses state is queried without mutating shared protocol values or owning resources.
I: pass | Jupiter uses focused annotated methods, and no production caller implements or imports this test API. Profile validation and codecs are not forced into a common artificial interface to satisfy the architecture checks.
D: pass | Test-only dependencies point to Jupiter, ArchUnit, and production identities used as coverage anchors. Production classes never depend on this test. The actual forbidden profile-to-codec dependency was detected independently of the permitted reverse codec-to-profile direction.
findings: none
```

### Actual TDD and verification

All commands ran in the isolated worktree. The invocation-only daemon argument
avoided shared-worktree daemon interference; no build setting was committed.

1. Baseline: `./gradlew test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   passed five executed tests against copied header, field, TLV, and profile
   sources before the new rule.
2. Red: added the new profile rule and profile import anchors, then inserted
   `private static final kg.aidarbek.smpp.codec.FieldReader FORBIDDEN_DEPENDENCY = null;`
   into the isolated copy of `ProtocolProfile`. Its source SHA-256 was
   `a2a136d0854f28df1515904dbfe7d7895d33b1f1e1b2b3cd1892b253c3a55dbe`.
   `./gradlew test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest.profilesDependOnlyOnProfilesProtocolAndJdkValues' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   executed one test and failed with an actual ArchUnit field dependency
   violation naming `ProtocolProfile.FORBIDDEN_DEPENDENCY` and `FieldReader`.
3. Green: removed only that field. The unchanged full `ArchitectureTest` command
   passed all six tests; tests executed and corrected production compilation was
   restored from cache. No temporary Java helper or fixture type was introduced.
4. Formatting: `./gradlew spotlessApply --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   passed using the pinned project formatter.
5. Check: `./gradlew check --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   passed formatting and compilation checks with six executed tests, zero
   failures, errors, or skipped cases. This isolated check includes the
   architecture suite, not the complete field/profile behavior suite; final
   integration must run both.

The temporary profile field deliberately violated the profile's single
responsibility and dependency direction. It created no supported extension
boundary; removing it restores separation. It changed no public method,
substitution, equality, mutability, ordering, or interface contract, because it
was a private static null reference; it instantiated no codec. All five SOLID
principles were considered for that transient snapshot, and the deliberate
dependency/responsibility violations were removed. Complete corrected
production-type reviews remain in the field/profile report.

The complete final test source was reviewed: one changed top-level Java type,
no new top-level, member, local, or anonymous type. Its current formatted hash
and all five principle findings are recorded above. The assertions and rule
selection remain meaningful; no disabled test or vacuous success was used.

### Final combined verification and independent review

After the field/profile source was finalized and formatted, all 21 of its Java
source/test files were copied byte for byte from `/tmp/lightweight-smpp-step5`
into this isolated worktree. The three Step 3 behavior test files were copied
from the root checkout as well. A read-only byte comparison confirmed every
Step 5 file matched its owner's final revision.

`./gradlew check javadoc --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
then passed with all six tasks executed: compilation, formatting verification,
Javadoc, and the combined test task. XML results contain 95 executed tests across
13 suites: 58 Step 3 behavior cases, 31 Step 5 cases, and six architecture checks;
zero failures, errors, or skipped cases. No warning was emitted. The test source
hash remains `28759b22ecfe105767c2e2eeb3d4a44c9753acb27292020b9722d8416a581159`.
This verifies the complete final production package graph, including the typed
registry's intentional dependency on profiles. It does not exercise the separate
Step 4 review source sets; the root integrated build owns that check.

The independent review covered all 23 new Step 5 type identities: 12 top-level
production types, `TypedTlvRegistry.Registration` and `TypedTlvRegistry.Key`, and
nine test classes. Complete final API contracts and all five SOLID principles
were considered, including inherited exception/enum/record/Object contracts,
binary and ordered equality, caller byte ownership, failed-operation atomicity,
thread confinement or codec thread safety, interface type/domain contracts,
custom codec extension, and dependency direction. No remaining defect or SOLID
violation was identified. The owner's change report supplies the individual
source hashes and detailed per-type review blocks for those sources.

Specific review follow-ups were completed:

- An ample-capacity embedded-NUL test now isolates content validation. The owner
  observed that removing the NUL guard makes that test fail, then restored the
  guard and ran the full suite.
- Broadcast rules use explicit priority context and correctly omit/ignore
  `broadcast_rep_num` for immediate broadcasts, per SMPP 5.0 section 4.8.4.13.
- Reserved congestion values remain in raw storage but produce no supported
  incoming typed value; malformed known-context lengths still fail. Unknown
  contexts are left uninterpreted before value validation.
- Public docs distinguish catalogue membership, occurrence rules, typed scalar
  interpretation, frame bounds, and future command/bind/session policies. The
  raw version advertisement octet can preserve unknown values without claiming
  that the advertised version is supported.
- A separate read-only comparison matched every numeric catalogue entry against
  the independently documented inventory: 27 commands and 44 tags for 3.4,
  33 commands and 64 tags for 5.0.

## Root integration verification

The root checkout combines the committed Step 3 framing, committed Step 4 review
tool, final Step 5 source/test files, and the updated architecture test. No Java
source changed after the final isolated verification described above.

`./gradlew build solidReviewInventory --console=plain` passed in one second
(`/tmp/lightweight-smpp-step5-evidence/01-integrated-build.log`). It executed
formatting verification, all three archive tasks, review coverage, and the type
inventory. Production/test compilation, Javadoc, and the library test task reused
matching build-cache entries from the independently executed combined suite.
`reviewTest` and its compilation were up to date from Step 4. This integration
run validates those matching results; it does not claim the cached tests executed
again. Gradle stored the configuration-cache entry for the combined invocation.

The resulting XML contains 95 library/architecture tests and 60 review-tool tests,
with zero failures, errors, or skipped cases: 155 cases in total. The inventory
and successful coverage output contain 43 current Java type identities. A separate
read-only check recomputed every covered source SHA-256 and matched all 43 rows
against the actual files. The Step 5 report supplies current reviews for all 24
affected identities; the earlier reports cover the remaining unchanged sources.

`./gradlew dependencies --configuration runtimeClasspath --console=plain` passed
with configuration-cache reuse and reported no runtime dependencies. Archive
inspection confirmed that the binary, source, and Javadoc JARs contain the field
API and exclude review-tool, test, and architecture-test classes/sources.

After the documentation update, `./gradlew build --console=plain` passed in
895 ms with configuration-cache reuse: review validation executed and the other
12 actionable tasks were up to date
(`/tmp/lightweight-smpp-step5-evidence/02-final-build.log`). Document verification
checked 20 Markdown files and 133 local links, including balanced fences, one
document title per file, final newlines, and footnote references. `git diff --check`
passed. These documentation edits changed no Java type or behavior.

The field guide and protocol, TLV, test, development, SOLID, overview, roadmap,
and agent guidance documents now describe completed Step 5 scope and the remaining
command/session work. Full client/server operation support and load simulators
remain later roadmap work. Step 6 is next; it is not part of this commit.
