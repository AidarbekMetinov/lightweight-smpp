# Review: Basic message codecs

## Scope

Starting isolated baseline: `03959d4`. Step 7 implements the six message commands,
immutable values, their profile/direction-specific optional tables, all 51 used
TLV structures, and explicit original-request response validation. The final
change has **27 Java files and 28 types**: 20 production types, seven test classes
and the nested `MessageTlvValueCodecTest.Fixture` record. No temporary, local or
anonymous Java types were created; provisional implementations were earlier
revisions of these same types. No runtime dependencies, transport, text helpers,
receipt parsers or session tracking were added.

Shared Step 6 sources (`Command`, `Pdu`, `CommandCodec`, `PduCodec`, `PduLimits`,
`CommandDispatchException`, `CommandCodecChecks`) were copied into this isolated
worktree only to compile and integrate. They are excluded from this patch and
this change's new-type inventory; Step 6 owns their review and commit. Existing
foundation files and root-owned status/architecture documentation are unchanged
by the Step 7 patch. Root integration applies Step 6 first.

The following blocks are the complete affected type inventory, with whole-file
SHA-256 values from the JDK-parser inventory after `spotlessApply`. Review covers
all methods, constructors, inherited Java/Command/TlvValueCodec contracts and
consumers, not only changed lines. `MessageTlvValueCodecTest.Fixture` shares the
hash of its enclosing source as required.

## Findings and interpretation

The primary sources and exact selected policies are documented in
[MESSAGES.md](../MESSAGES.md). Both complete downloaded specification PDFs/texts
were inspected, including the command tables and individual TLV definitions.
The review corrected reserved incoming mandatory-field rejection, UCS2 callback
display pairing, null limits on directly encoded omitted error bodies, outgoing
TLV interpretation before aggregate bounds, and a per-response reconstruction
of the entire immutable default TLV registry. Each
behavior correction has an actual failing regression below; the allocation
change was a behavior-preserving refactor from green tests.

Sharing uses composition: `ShortMessage` shares stored layout while the codec
retains profile/direction/command differences. `DataSm` has its own smaller
contract. Raw arrays are owned at public boundaries and diagnostics exclude
contents. Unknown/unexpected incoming TLVs and reserved values are retained;
known malformed lengths/structures fail. Companion checks use supported values,
with incomplete incoming SAR and unsupported callback groups ignored semantically.

5.0 error-body omission is an explicit baseline choice following its global
rule, despite tension with transaction diagnostic TLV tables. Failed 5.0 reply
body diagnostics require a separately specified compatibility policy; this
change neither silently supports them nor manufactures a successful status.
Request context is supplied to `MessageResponseRules`; no codec can infer it
from a response body. External network/handset service behavior remains opaque,
while every message TLV's declared SMPP structural envelope is implemented.

## Type reviews

### kg.aidarbek.smpp.codec.MessageCommandCodec

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageCommandCodec.java
type: kg.aidarbek.smpp.codec.MessageCommandCodec
sha256: 750a80e0ebb7d7a7479aa3802d9d5ed1dd150b141e5145daf29bcd4ce565da17
responsibility: Translates one of the six basic message body layouts under explicit profile, status and data direction.
consumers: PduCodec dispatch and direct CommandCodec callers; MessageFields, MessageTlvSupport and shared CommandCodecChecks.
S: pass | Only message wire layout and validation drive this translator; it has no transport, retry, receipt parser or request map.
O: pass | New operations register other CommandCodec implementations; these six configured identities share their actual layouts without changing unrelated dispatch.
L: pass | All six implementations satisfy CommandCodec ownership, full consumption, request-zero/status-width, null and body/TLV bounds in directBodyCallsEnforceTheSameNullStatusBoundsAndOwnershipContract and independent complete frames. Raw TLV count/byte preflight now precedes interpretation and value copies; outgoingAggregateLimitsPrecedeValueInterpretationForEveryMessageCommand verifies both limits for all six IDs with malformed recognized values. Error-body ignoring is explicit and bounded.
I: pass | Dispatch needs only ID, value class and encode/decode; generic T is recovered through the registered class without unchecked casts or unsupported methods.
D: pass | Depends inward on protocol/profile values and field/TLV codecs; variable vendor interpretation arrives through TlvValueCodec, with no infrastructure dependencies.
findings: none
```

### kg.aidarbek.smpp.codec.MessageCommandCodecs

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageCommandCodecs.java
type: kg.aidarbek.smpp.codec.MessageCommandCodecs
sha256: 2fe232a16483f09b7598048937c0e36ee3bc27b7be5407734c56f8a102ca3b34
responsibility: Constructs immutable standard or explicitly extended message codec registrations.
consumers: Applications constructing PduCodec and MessageResponseRules reading a direction-specific typed registry.
S: pass | Registration wiring owns the exact six IDs and two data directions; it does not execute message services.
O: pass | Vendor contexts are supplied as immutable MessageTlvExtension declarations, and unrelated commands still use the shared dispatcher extension boundary.
L: pass | Returns immutable lists of conforming codecs and immutable registries; two safely published standard compositions avoid rebuilding registrations per response. Existing behavior tests remained green through this refactor.
I: pass | Consumers choose either body registrations or value interpretation; data direction is required and there is no session/authentication configuration.
D: pass | This composition point wires concrete standard codecs while receiving variable vendor codecs through their existing abstraction; class initialization has no path back from MessageTlvSupport.
findings: none
```

### kg.aidarbek.smpp.codec.MessageFields

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageFields.java
type: kg.aidarbek.smpp.codec.MessageFields
sha256: c34af76c908a953e8a6c10e43a09d10c3d7f1e4312fa671195388dc8af998e95
responsibility: Translates and validates standard fields whose wire contracts are shared by message layouts.
consumers: MessageCommandCodec and callback TON/NPI interpretation in MessageTlvValueCodec.
S: pass | Owns bounded addresses, standard flag domains, wire time grammar and byte writing; its writer consumes the already bounded TLV block supplied by the command codec without re-encoding it. No elapsed clock, century or text interpretation is present.
O: pass | Profile/direction differences are explicit arguments rather than inherited submit/deliver assumptions; new commands can use only the applicable helpers.
L: pass | MessageCodecValidationTest covers independent bounds, malformed strings, 254/255 limits, raw reserved incoming numeric values and ignored relative suffixes. Readers consume exact fields, writers preflight bounded total length, and independent complete frames verify unchanged TLV bytes after the caller-side preflight refactor.
I: pass | Package-private methods expose only field translation and relevant validators to the body codec; application callers are not coupled to these helpers.
D: pass | Uses protocol values, profile identifiers and bounded field/TLV codecs; the caller supplies encoded TLV bytes without a dependency on its semantic validator. No filesystem, sockets or mutable global infrastructure.
findings: none
```

### kg.aidarbek.smpp.codec.MessageResponseRules

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageResponseRules.java
type: kg.aidarbek.smpp.codec.MessageResponseRules
sha256: 1318a36ad5f2d2c1aecb2a62f1d9ae57643144d5301e730655d544c3d62a9f44
responsibility: Validates response diagnostics that require the original message request or an explicitly known delivery outcome.
consumers: Applications and later request handling after both body codecs, using immutable Pdu values and the cached typed registry.
S: pass | Transaction and known-outcome consistency are the sole change driver; sequence correlation, timers and downstream delivery inference are excluded.
O: pass | The overload with explicit outcome adds the needed context without changing raw wire decoding or introducing request tracking.
L: pass | MessageResponseRulesTest covers 3.4 versus 5.0 text applicability, transaction diagnostics, reserved incoming values, command mismatch and explicit success contradictions; inputs are never mutated.
I: pass | Callers that know only the original request need no fabricated delivery result; the additional outcome is a separate overload.
D: pass | Depends on immutable protocol/profile values and TLV interpretation, reusing cached standard compositions rather than constructing network/session machinery.
findings: none
```

### kg.aidarbek.smpp.codec.MessageTlvExtension

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageTlvExtension.java
type: kg.aidarbek.smpp.codec.MessageTlvExtension
sha256: 2358b563c8250c8bcf31c0230024cd92ead63fc6f7f5902fa9fafe7ceeea6363
responsibility: Declares one explicit vendor tag interpretation and outgoing permission context.
consumers: MessageCommandCodecs and MessageTlvSupport; application-supplied TlvValueCodec of OctetString.
S: pass | Only exact context metadata, vendor range, repeatability and codec contract validation are owned here.
O: pass | Vendor-specific structure extends through TlvValueCodec without modifying standard tag switches, operation tables or dispatch.
L: pass | The record keeps immutable metadata and references a contract-conforming thread-safe codec; record equality includes that codec identity/equality contract. Vendor direction/profile/command isolation and duplicate rejection are tested.
I: pass | The declaration contains only information required to permit and interpret one tag; it does not require providers to implement message sending or session callbacks.
D: pass | Variable vendor interpretation is an injected TlvValueCodec abstraction; standard profile values and JDK references supply the remaining metadata.
findings: none
```

### kg.aidarbek.smpp.codec.MessageTlvSupport

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageTlvSupport.java
type: kg.aidarbek.smpp.codec.MessageTlvSupport
sha256: ef590e65b99e9b4ec3940a281f0298c37e17bf2ff7b13e86124560cc488840d3
responsibility: Composes occurrence rules, typed value validation and message-local companion conditions.
consumers: MessageCommandCodec, MessageCommandCodecs and the response-context helper; TlvRules and TypedTlvRegistry.
S: pass | All operations implement the same optional-parameter validation boundary, from context selection through supported companion interpretation.
O: pass | Explicit vendor declarations add scoped codecs and permission without changing built-in structure checks; immutable maps separate construction from use.
L: pass | MessageOptionalParametersTest verifies ordered retention, strict output, unsupported input, payload precedence, SAR completeness, UDHI, callback counts, network/NP companions and byte/count bounds. Unsupported callback groups and incomplete SAR do not acquire accidental semantics.
I: pass | Only package-private registry and validation helpers are exposed; general callers retain the narrow factory and command codec APIs.
D: pass | Depends on profile occurrence declarations and the TlvValueCodec registry abstraction; all collections are immutable after construction and no infrastructure is created.
findings: none
```

### kg.aidarbek.smpp.codec.MessageTlvValueCodec

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageTlvValueCodec.java
type: kg.aidarbek.smpp.codec.MessageTlvValueCodec
sha256: ea1e601b35e5d43b93590fc69be5d5c46f6c2ba1f78f0f9b933563b15ccd568e
responsibility: Interprets the SMPP structure and supported discriminants of one standard message TLV as immutable raw octets.
consumers: TypedTlvRegistry and message optional validation; callers explicitly requesting OctetString interpretation.
S: pass | Owns per-tag wire lengths, known subfield structure and unsupported-value decisions, leaving external service semantics and companion conditions to their owners.
O: pass | Standard definitions change this standard codec; vendor variation uses separate TlvValueCodec implementations and cannot override standard permission implicitly.
L: pass | Implements the complete TlvValueCodec contract: immutable metadata/results, no input mutation or retention, fresh output, malformed-structure exceptions and empty interpretation for reserved values. All 51 tags have independent fixtures, with UCS2 pair, TBCD and profile-specific regressions.
I: pass | The existing three-method value codec interface is sufficient; no text conversion, receipt parser or handset-service methods burden callers.
D: pass | Uses only immutable profile metadata, OctetString and bounded ASCII field reading; external network formats remain raw within the specified SMPP envelope.
findings: none
```

### kg.aidarbek.smpp.profile.MessageDirection

```solid-review
source: src/main/java/kg/aidarbek/smpp/profile/MessageDirection.java
type: kg.aidarbek.smpp.profile.MessageDirection
sha256: cf42f4700f2828ebfa11547a1f3682b99e013133e78e6aea64c46d0eae9082cf
responsibility: Names the two original data request directions independently of TCP or session role.
consumers: Message codec configuration, TLV occurrence selection and response-context validation.
S: pass | Only ESME submission versus message-center delivery determines these enum values.
O: pass | An exhaustive factory switch keeps the supported two-way protocol distinction explicit; connection/session variation is outside this value.
L: pass | Enum identity, equality and immutability follow Java contracts; both constants are exercised by complete frame and exact-table fixtures.
I: pass | Consumers need only the direction identity; no networking role, credentials or session capabilities are bundled into it.
D: pass | The enum has only JDK enum inheritance and no infrastructure or codec dependency.
findings: none
```

### kg.aidarbek.smpp.profile.MessageTlvRules

```solid-review
source: src/main/java/kg/aidarbek/smpp/profile/MessageTlvRules.java
type: kg.aidarbek.smpp.profile.MessageTlvRules
sha256: 7c59207bad81dbe4eaa3f8853a4660bf5ce56c22490f646fd4904542f13473b2
responsibility: Declares exact profile, command and data-direction message TLV occurrence tables.
consumers: MessageTlvSupport, MessageTlvValueCodec construction and specification-table tests.
S: pass | The operation table is its change driver; it deliberately does not interpret bytes or decide response/request mode.
O: pass | Changes to a message table stay in this catalogue; vendor additions are composed outside it without modifying stable standard sets.
L: pass | Returns immutable permitted sets and existing TlvRules with required/repeatable subsets; exactMessageTablesMatchBothSpecificationProfiles independently checks every one of the six IDs in both versions and data directions.
I: pass | Consumers can request the tag set or occurrence policy without depending on encoding, sockets or session state.
D: pass | Profile policy depends only on other profile values, immutable protocol occurrence storage through TlvRules and JDK collections; package direction remains acyclic.
findings: none
```

### kg.aidarbek.smpp.protocol.Address

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/Address.java
type: kg.aidarbek.smpp.protocol.Address
sha256: a20220035ebb24199b133b3e451c3e68d5310d1abb6bc86f39d01b03aa7befd9
responsibility: Stores a raw immutable TON/NPI/address triple with representation bounds.
consumers: ShortMessage, DataSm and body field translation.
S: pass | Only raw address representation changes the record; command-specific 21/65-octet and supported numeric rules belong to codecs.
O: pass | The value preserves raw unsigned metadata without adding network-specific address parsers; other command contexts choose their own bounded field translation.
L: pass | String and integers are immutable and record equality is content-based; MessageValuesTest and boundary fixtures check 64-character storage, NUL/non-ASCII rejection and reserved incoming TON/NPI retention.
I: pass | Consumers receive only three address components; no delivery, lookup or formatting service is attached.
D: pass | Depends only on JDK values and protocol MessageValueChecks; no codec/profile dependency points outward from the protocol layer.
findings: none
```

### kg.aidarbek.smpp.protocol.DataSm

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/DataSm.java
type: kg.aidarbek.smpp.protocol.DataSm
sha256: 13779379807021377628fe103ddf1883b8860d69421097e417f4198b0ff24bde
responsibility: Stores immutable data_sm standard fields and ordered optional parameters under fixed command ID 0x103.
consumers: Pdu and the data branch of MessageCommandCodec, plus response-context validation.
S: pass | Owns data-message representation; the reduced body has no unrelated short-message, scheduling or receipt-parsing helpers.
O: pass | Additional optional data uses the ordered TLV value and scoped codec extension, while data-direction variation remains explicit codec configuration.
L: pass | Fulfills Command with a stable unsigned ID and immutable fields; record equality delegates to immutable Address and OptionalParameters. Independent full frames, empty bodies, 64-character addresses and unsigned/null boundaries are tested.
I: pass | Exposes only data_sm fields plus command identity, so callers are not forced to set submit/deliver-only fields.
D: pass | Protocol values depend only on protocol/JDK values, with no sockets, profiles or concrete codecs.
findings: none
```

### kg.aidarbek.smpp.protocol.DataSmResponse

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/DataSmResponse.java
type: kg.aidarbek.smpp.protocol.DataSmResponse
sha256: 7d9a49d29b86ed9382513bd68dbbf915befa1c6da7d67218d78263d20c580d3f
responsibility: Identifies an immutable DataSmResponse command under fixed ID 0x80000103 and composes its MessageResponse fields.
consumers: Pdu/Command dispatch, MessageCommandCodec and callers constructing DataSmResponse.
S: pass | The DataSmResponse command identity and non-null immutable body are its sole responsibility; profile/status behavior stays in the codec.
O: pass | Other operations use other Command values/codecs; optional content extends through MessageResponse rather than inheritance between commands.
L: pass | Fulfills Command with stable unsigned ID 0x80000103, immutable nested values and record equality/hashCode. Independent complete fixtures in both profiles and response/field-boundary tests verify this exact type.
I: pass | Consumers use only commandId and the relevant MessageResponse fields; no session, send or receipt parsing methods are imposed.
D: pass | Depends only on protocol values and JDK Objects; no outward dependency on profiles, codecs or infrastructure.
findings: none
```

### kg.aidarbek.smpp.protocol.DeliverSm

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/DeliverSm.java
type: kg.aidarbek.smpp.protocol.DeliverSm
sha256: 77f6a8de2a1e1331d02f4234a5e1c3908f662b1e9633af12d57575e39abf2ed8
responsibility: Identifies an immutable DeliverSm command under fixed ID 0x00000005 and composes its ShortMessage fields.
consumers: Pdu/Command dispatch, MessageCommandCodec and callers constructing DeliverSm.
S: pass | The DeliverSm command identity and non-null immutable body are its sole responsibility; profile/status behavior stays in the codec.
O: pass | Other operations use other Command values/codecs; optional content extends through ShortMessage rather than inheritance between commands.
L: pass | Fulfills Command with stable unsigned ID 0x00000005, immutable nested values and record equality/hashCode. Independent complete fixtures in both profiles and response/field-boundary tests verify this exact type.
I: pass | Consumers use only commandId and the relevant ShortMessage fields; no session, send or receipt parsing methods are imposed.
D: pass | Depends only on protocol values and JDK Objects; no outward dependency on profiles, codecs or infrastructure.
findings: none
```

### kg.aidarbek.smpp.protocol.DeliverSmResponse

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/DeliverSmResponse.java
type: kg.aidarbek.smpp.protocol.DeliverSmResponse
sha256: 1f28bd3ee77a8bd5fdb40d3e9a8172e9bfe7ea02ab72d6d7d4db1ffc362921fd
responsibility: Identifies an immutable DeliverSmResponse command under fixed ID 0x80000005 and composes its MessageResponse fields.
consumers: Pdu/Command dispatch, MessageCommandCodec and callers constructing DeliverSmResponse.
S: pass | The DeliverSmResponse command identity and non-null immutable body are its sole responsibility; profile/status behavior stays in the codec.
O: pass | Other operations use other Command values/codecs; optional content extends through MessageResponse rather than inheritance between commands.
L: pass | Fulfills Command with stable unsigned ID 0x80000005, immutable nested values and record equality/hashCode. Independent complete fixtures in both profiles and response/field-boundary tests verify this exact type.
I: pass | Consumers use only commandId and the relevant MessageResponse fields; no session, send or receipt parsing methods are imposed.
D: pass | Depends only on protocol values and JDK Objects; no outward dependency on profiles, codecs or infrastructure.
findings: none
```

### kg.aidarbek.smpp.protocol.MessageResponse

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/MessageResponse.java
type: kg.aidarbek.smpp.protocol.MessageResponse
sha256: 6094e106cdf4f436fafc53496cc282ba899ae15b5254e1341be921614b960086
responsibility: Stores a message response body while distinguishing omission from a present empty C-octet ID.
consumers: Three typed response values, message body codecs and original-request validation.
S: pass | Only immutable body representation and the no-TLVs-with-omitted-body invariant are owned here.
O: pass | Status and profile policy live in the command codec; preserving unknown TLVs does not require modifying this value.
L: pass | Optional<String> and OptionalParameters are immutable and record equality is value-based; tests cover empty versus omitted, 64/65-character IDs, NUL/non-ASCII errors and diagnostics redaction.
I: pass | Callers need only the optional ID and TLVs; response correlation and acceptance logic are not mixed into this value.
D: pass | Depends only on protocol storage and JDK Optional/Objects, keeping response policy outside protocol values.
findings: none
```

### kg.aidarbek.smpp.protocol.MessageValueChecks

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/MessageValueChecks.java
type: kg.aidarbek.smpp.protocol.MessageValueChecks
sha256: 34a25d8b40e015b9fd951cc47fbcc76fc0cd652d0ae9a1c6c3398a8cc46e3aa5
responsibility: Enforces non-NUL ASCII and unsigned-octet representation invariants for immutable message values.
consumers: Address, ShortMessage, DataSm and MessageResponse constructors.
S: pass | The shared representation invariant is its sole responsibility; profile semantics and wire cursor management remain outside.
O: pass | Callers supply each actual field bound; new bounded message fields do not force changes to unrelated validators.
L: pass | Stateless checks reject only null/representation violations without mutating inputs or disclosing values; MessageValuesTest covers widths, lengths and character boundaries across consuming values.
I: pass | Two package-private operations match constructor needs without exposing a public validation framework.
D: pass | Only JDK primitives, String and Objects are used; no dependency points from protocol values into codecs.
findings: none
```

### kg.aidarbek.smpp.protocol.OctetString

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/OctetString.java
type: kg.aidarbek.smpp.protocol.OctetString
sha256: ef0c60128375fb1f55cd077134138ca784df6751b31e38a146910bb2036fa679
responsibility: Owns immutable uninterpreted binary content with content equality and redacted length diagnostics.
consumers: ShortMessage, standard/vendor typed TLV codecs and application binary payload code.
S: pass | Binary storage, ownership and equality are its only change drivers; text encoding and wire field limits are deliberately external.
O: pass | All binary coding schemes use the same representation without subclassing or modifying the value.
L: pass | Copies construction and accessor arrays, implements matching Arrays equality/hashCode and exposes length only in diagnostics. The first red/green cycle and later complete frame tests prove input/output isolation including NUL and 0xff.
I: pass | Only bytes and length are exposed; callers are not required to choose a text alphabet or parser.
D: pass | Uses JDK arrays/Objects only, so protocol payload ownership has no runtime library or infrastructure dependency.
findings: none
```

### kg.aidarbek.smpp.protocol.ShortMessage

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/ShortMessage.java
type: kg.aidarbek.smpp.protocol.ShortMessage
sha256: d1564d4c17f53ef05ebca671571934d2e3e94fd8c30450e6dbaa9c08a7b8046f
responsibility: Stores the immutable common standard-field layout of submit_sm and deliver_sm.
consumers: Typed submission/delivery commands and MessageFields translation.
S: pass | The shared wire field representation drives changes; command-specific unused fields, versions and directions remain codec decisions.
O: pass | Optional fields extend through OptionalParameters; sharing composition avoids an inheritance hierarchy that would equate different command contracts.
L: pass | All nested values are immutable, payloads use OctetString content equality, raw octets are bounded and strings have representation limits. MessageValuesTest and full-frame/profile boundary tests verify these contracts and redacted diagnostics.
I: pass | The record represents exactly the shared standard body; DataSm uses a separate smaller value instead of meaningless fields.
D: pass | Depends exclusively on protocol/JDK values; it performs no clock, encoding, network or session work.
findings: none
```

### kg.aidarbek.smpp.protocol.SubmitSm

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/SubmitSm.java
type: kg.aidarbek.smpp.protocol.SubmitSm
sha256: f8bc132ead67ca489d8edb41494b10764c2e976f46b2a056c8f1e13d21972fbb
responsibility: Identifies an immutable SubmitSm command under fixed ID 0x00000004 and composes its ShortMessage fields.
consumers: Pdu/Command dispatch, MessageCommandCodec and callers constructing SubmitSm.
S: pass | The SubmitSm command identity and non-null immutable body are its sole responsibility; profile/status behavior stays in the codec.
O: pass | Other operations use other Command values/codecs; optional content extends through ShortMessage rather than inheritance between commands.
L: pass | Fulfills Command with stable unsigned ID 0x00000004, immutable nested values and record equality/hashCode. Independent complete fixtures in both profiles and response/field-boundary tests verify this exact type.
I: pass | Consumers use only commandId and the relevant ShortMessage fields; no session, send or receipt parsing methods are imposed.
D: pass | Depends only on protocol values and JDK Objects; no outward dependency on profiles, codecs or infrastructure.
findings: none
```

### kg.aidarbek.smpp.protocol.SubmitSmResponse

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/SubmitSmResponse.java
type: kg.aidarbek.smpp.protocol.SubmitSmResponse
sha256: 47fb069fc81fb04c854cef520c06a2d6a0166733729505cb35fafeb9e6876206
responsibility: Identifies an immutable SubmitSmResponse command under fixed ID 0x80000004 and composes its MessageResponse fields.
consumers: Pdu/Command dispatch, MessageCommandCodec and callers constructing SubmitSmResponse.
S: pass | The SubmitSmResponse command identity and non-null immutable body are its sole responsibility; profile/status behavior stays in the codec.
O: pass | Other operations use other Command values/codecs; optional content extends through MessageResponse rather than inheritance between commands.
L: pass | Fulfills Command with stable unsigned ID 0x80000004, immutable nested values and record equality/hashCode. Independent complete fixtures in both profiles and response/field-boundary tests verify this exact type.
I: pass | Consumers use only commandId and the relevant MessageResponse fields; no session, send or receipt parsing methods are imposed.
D: pass | Depends only on protocol values and JDK Objects; no outward dependency on profiles, codecs or infrastructure.
findings: none
```

### kg.aidarbek.smpp.codec.MessageCodecValidationTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/MessageCodecValidationTest.java
type: kg.aidarbek.smpp.codec.MessageCodecValidationTest
sha256: 2261641534bdc5cb7ace5250cded7de2e3aa6dfa28d75122add94a3ff5d2d6ed
responsibility: Verifies standard message boundaries, compatibility handling and response body layout against observable wire behavior.
consumers: JUnit Jupiter, message values/codecs and independent byte mutation fixtures.
S: pass | Only standard-field/profile validation is tested; optional-value and original-request-specific tests are kept in their corresponding suites.
O: pass | Scenario loops add profile/command boundary cases without altering production or invoking external peers.
L: pass | JUnit test lifecycle and deterministic in-memory fixtures are respected; independent known offsets and explicit field values detect malformed input and raw preservation instead of relying only on round trips.
I: pass | No custom test interface or resource lifecycle is required; local helpers build only the field scenarios used by these tests.
D: pass | Depends on public protocol/codec contracts and JUnit assertions, with no mocks of transport or unrelated infrastructure.
findings: none
```

### kg.aidarbek.smpp.codec.MessageCommandCodecsTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/MessageCommandCodecsTest.java
type: kg.aidarbek.smpp.codec.MessageCommandCodecsTest
sha256: 074e3d7d4cca4d6e56f1b60c58565e3936904ad63e28a2e9162bf4b7900421d5
responsibility: Verifies all six complete wire fixtures and the shared direct-body codec contract.
consumers: JUnit Jupiter, PduCodec, CommandCodec and immutable message values.
S: pass | Complete frame translation and substitutable body-codec behavior are the sole test purpose.
O: pass | The same loops exercise both profiles and directions and accept new fixture cases without weakening known byte expectations.
L: pass | Common direct calls verify metadata-driven casts, nulls, statuses, bounded output and owned arrays for every registered codec; independent complete frames prevent complementary encoder/decoder errors from hiding.
I: pass | Generic local helper methods invoke the existing narrow CommandCodec interface without unchecked casts or fabricated session contracts.
D: pass | Uses real in-memory codecs and independent hex fixtures rather than implementation-generated expected bytes or infrastructure mocks.
findings: none
```

### kg.aidarbek.smpp.codec.MessageOptionalParametersTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/MessageOptionalParametersTest.java
type: kg.aidarbek.smpp.codec.MessageOptionalParametersTest
sha256: 659359eb9cebe0a7e3664b5b0d553ee9ec88cbe539d93bb81aed61b69fa869dd
responsibility: Verifies message-level optional applicability, preservation, payload alternatives and companion relationships.
consumers: JUnit Jupiter, message codec factory, typed registry and immutable TLV fixtures.
S: pass | Only composition of optional parameters with their message context is covered; tag-only structure lives in a separate value-codec suite.
O: pass | Additional cases extend explicit context fixtures; vendor variation is tested through the same public extension declaration as real callers.
L: pass | Exercises raw incoming retention, unsupported interpretation, outgoing rejection, malformed framing, bounds, SAR, callbacks, network companions and direction isolation without reordering inputs. Malformed known values distinguish premature interpretation from the required byte/count rejection for all six outgoing command IDs. Helpers create caller-owned frames.
I: pass | Only small TLV/frame fixture helpers are shared with response tests; no broad test base class or fake service obligations exist.
D: pass | Uses real bounded codecs and explicit byte fixtures, including recognized malformed TLVs as preflight-order sentinels; no artificial counting codec, external peer, mutable clock or networking dependency is introduced.
findings: none
```

### kg.aidarbek.smpp.codec.MessageResponseRulesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/MessageResponseRulesTest.java
type: kg.aidarbek.smpp.codec.MessageResponseRulesTest
sha256: e739a36f3af16a400ff7fe3be2881d15a4a0975199e65d704f38ae0be299d9c7
responsibility: Verifies response conditions requiring original request mode or known delivery outcome.
consumers: JUnit Jupiter, immutable request/response values and MessageResponseRules.
S: pass | Contextual response applicability is isolated from the wire-layout tests, except one independent negative data response fixture establishing the profile distinction.
O: pass | New request/outcome scenarios are explicit test inputs without adding production tracking state or extending a mock session.
L: pass | Tests unsupported incoming values, transaction modes, mismatched operations and definite success contradictions; the 3.4 negative-body fixture is independently specified and 5.0 omission is asserted.
I: pass | Helpers supply only request context required by the public validator; no invented delivery result is required for the context-only overload.
D: pass | Uses real protocol values and validators rather than callback, socket or persistence substitutes.
findings: none
```

### kg.aidarbek.smpp.codec.MessageTlvValueCodecTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/MessageTlvValueCodecTest.java
type: kg.aidarbek.smpp.codec.MessageTlvValueCodecTest
sha256: b47f9eed564a7617a62876325157bc7541602a9091e3b51c5d7f371160fd9e43
responsibility: Verifies all 51 standard message TLV structures, supported-value ranges and version distinctions.
consumers: JUnit Jupiter and real TlvValueCodec implementations, using independent hex/length fixture metadata.
S: pass | Only per-value structure and interpretation are tested; command occurrence and companion behavior use separate suites.
O: pass | The fixture list and targeted structure cases grow with supported standard parameters; equality to the union of independently tested message tables detects omitted tag coverage.
L: pass | Checks the TlvValueCodec ownership/result contract, malformed lengths, reserved values, UCS2 pairs, TBCD, ASCII termination, decimal node widths and 3.4/5.0 changes with deterministic inputs.
I: pass | One small immutable fixture record contains exactly the tag/bytes/bounds needed by the loop; no production-only testing hooks are required.
D: pass | Expected bytes come from specification tables, not encoding implementation output; dependencies are JUnit and in-memory protocol code only.
findings: none
```

### kg.aidarbek.smpp.codec.MessageTlvValueCodecTest.Fixture

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/MessageTlvValueCodecTest.java
type: kg.aidarbek.smpp.codec.MessageTlvValueCodecTest.Fixture
sha256: b47f9eed564a7617a62876325157bc7541602a9091e3b51c5d7f371160fd9e43
responsibility: Stores one independently specified TLV tag, hex value and structural size bounds for the value-codec test loop.
consumers: Enclosing MessageTlvValueCodecTest only.
S: pass | The tuple is fixture data only and owns no test execution or production behavior.
O: pass | Additional tag cases add tuples; no framework or inheritance extension is required.
L: pass | Record fields are immutable primitives/String, so equality/hashCode and safe sharing follow record contracts; fixture bytes are parsed into fresh arrays before each test use.
I: pass | The four components are exactly the enclosing loop requirements; no callback, resource or codec methods are imposed.
D: pass | Depends only on JDK record/String semantics and remains outside the runtime source set.
findings: none
```

### kg.aidarbek.smpp.profile.MessageTlvRulesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/profile/MessageTlvRulesTest.java
type: kg.aidarbek.smpp.profile.MessageTlvRulesTest
sha256: 05cc0712603a4a67a41038c7bc14efc6a2698a44dc7f6c3976e65b9e5918fb08
responsibility: Checks every message occurrence table and singleton/repeatable compatibility behavior independently of typed value parsing.
consumers: JUnit Jupiter and profile MessageTlvRules/TlvRules with raw TLV fixtures.
S: pass | The specification catalogue and occurrence policy are the only test change drivers.
O: pass | Independent textual tag sets allow additional profile/context scenarios without deriving expectations from production maps.
L: pass | Both profile identities and both data directions exercise all six command IDs; callback repeats, singleton rejection and preserved unexpected incoming entries verify the occurrence contract.
I: pass | The helper compares only expected tag membership for a requested context; no encoding or network fixture is required.
D: pass | Uses JDK set parsing, immutable protocol values and profile policy; no production table is used as its own expected result.
findings: none
```

### kg.aidarbek.smpp.protocol.MessageValuesTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/protocol/MessageValuesTest.java
type: kg.aidarbek.smpp.protocol.MessageValuesTest
sha256: 3d0192b3e23a4d1f2cac72a555e7657fb516880ec3d4d24859a5db5b3f7588cd
responsibility: Checks immutable message value representation, ownership, equality and redacted diagnostics.
consumers: JUnit Jupiter and protocol value constructors/accessors.
S: pass | Only value invariants are tested; profile and body policy are intentionally left to message codec suites.
O: pass | Additional value boundary examples extend local assertions without introducing a general builder or mock hierarchy.
L: pass | Proves copy-in/copy-out behavior, content equality/hashCode, unsigned bounds, ASCII/NUL rules, absent-body invariants and null handling against concrete public contracts.
I: pass | No custom test supertype exists; each test needs only the constructors and accessors of the value under test.
D: pass | Uses JUnit and immutable protocol values only, with no codec coupling needed to establish constructor contracts.
findings: none
```

## TDD and verification

All commands used the checked-in wrapper, `--console=plain`, and
`-Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step7'`. The worker property isolates
this worktree's daemon filesystem state; caching, watchers and shared build
settings were not disabled. Logs remain in `/tmp/step7-*.log`; none of the red
runs below is a compilation, dependency or environment failure.

Focused command arguments and actual outcomes:

| Cycle / log prefix | Scenario and red command after `./gradlew` | Observed red | Green |
| --- | --- | --- | --- |
| `01` | `test --tests 'kg.aidarbek.smpp.protocol.MessageValuesTest'` | Owned octets expected length 3, provisional implementation returned 0; one failed test | Same command; one passed test |
| `02` | Same value-test command | Invalid negative TON did not throw; one of two failed | Same command; both passed |
| `03` | `test --tests 'kg.aidarbek.smpp.codec.MessageCommandCodecsTest'` | Independent submit frame expected 45 bytes, provisional body encoder returned a 16-byte header-only frame | Same command; all six frames in both profiles/directions passed |
| `04` | `test --tests 'kg.aidarbek.smpp.codec.MessageCodecValidationTest'` | Four failing scenarios: malformed times accepted, reserved outgoing flags accepted, 3.4 length 255 accepted, omitted error response decoded as required C-octet | Focused message/value suites passed after layout and field validation |
| `05` | `test --tests 'kg.aidarbek.smpp.profile.MessageTlvRulesTest'` | Empty provisional tag set disagreed with independent 3.4 table; permitted callbacks were rejected | Same command; two passed tests |
| `06` | `test --tests 'kg.aidarbek.smpp.codec.MessageTlvValueCodecTest'` | Reserved values interpreted, malformed strings accepted and fixed-length violation not detected; three failed scenarios | Same command; all 51 tag fixtures and structure scenarios passed |
| `07` | `test --tests 'kg.aidarbek.smpp.codec.MessageOptionalParametersTest'` | Five failed scenarios: payload coexistence, companions, direction, unknown output and SAR conditions not enforced | Focused message/profile/value suites passed |
| `08` | `test --tests 'kg.aidarbek.smpp.codec.MessageResponseRulesTest'` | Two failed scenarios: transaction context and explicit successful-delivery contradiction accepted | Focused suites passed; independent 3.4 negative data response bytes also passed |
| `12-reserved` | `test --tests 'kg.aidarbek.smpp.codec.MessageCodecValidationTest.requestFlagsAndDeliveryOnlyFieldsFollowTheChosenProfile'` | Incoming reserved esm_class caused an unexpected rejection | Focused suites passed after raw numeric preservation |
| `14-contract` | `test --tests 'kg.aidarbek.smpp.codec.MessageTlvValueCodecTest.ucs2CallbackDisplayUsesCompleteOctetPairsAndReservedCodingHasNoInterpretation' --tests 'kg.aidarbek.smpp.codec.MessageCommandCodecsTest.directBodyCallsEnforceTheSameNullStatusBoundsAndOwnershipContract'` | UCS2 odd-byte callback display accepted; omitted error encoding accepted null limits; both failed | Focused suites passed with UCS2 structure checks and shared direct-call preconditions |
| `21-preflight` | `test --tests 'kg.aidarbek.smpp.codec.MessageOptionalParametersTest.outgoingAggregateLimitsPrecedeValueInterpretationForEveryMessageCommand'` | Expected configured TLV byte-bound rejection, but malformed recognized value was interpreted first; one failed test | Focused suites passed; byte and count limits precede interpretation for each of the six outgoing message commands |

The focused-suite command used in green cycles 07, 08, 12, 14 and 21 was:

```sh
./gradlew test --tests 'kg.aidarbek.smpp.codec.Message*Test' --tests 'kg.aidarbek.smpp.profile.MessageTlvRulesTest' --tests 'kg.aidarbek.smpp.protocol.MessageValuesTest' --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step7'
```

Cycle 04 green selected message codec tests plus the value test. The initial
03/05/06 green commands reused their respective focused red selection. Cycle 09
added characterization of already implemented constructor, framing and typed
context behavior and passed; no earlier failure is claimed for those assertions.
Cycles 10/11 fixed Javadoc warnings and formatted sources. The cycle 10 inventory
ran before that invocation's formatter, so it was **not** used for final hashes.
Cycle 13 ran ordinary tests and Javadoc successfully, with only then-pending
shared Step 6 serial-field warnings. Those shared comments were subsequently
resynced from their owner.

Cycle 15 reused the cycle-14 green baseline, cached the two immutable standard
TLV compositions and removed duplicated local status checking in favor of the
shared precondition helper. Existing focused tests passed freshly. No identity
assertion or artificial red was introduced for the allocation refactor.

Cycle 16 ran `spotlessApply` separately before cycle 17's
`test javadoc solidReviewInventory`. Cycle 17 executed the ordinary tests,
Javadoc and inventory successfully with **no warnings**: 121 ordinary tests,
including 26 Step 7 methods with profile/direction/tag/boundary loops. All six
existing architecture tests ran against the actual protocol, codec and profile
packages and passed. These rules enforce package directions, absence of
infrastructure dependencies and package cycles; they are supporting structural
evidence rather than a substitute for the reviews above.

Every behavioral red/green run executed `test` freshly; ordinary compilation
could be up to date. Matching invocations reused the configuration cache after
the first graph was stored. Cycle 10 restored `compileReviewJava` from cache;
cycle 17 reused it up to date. No clean, dependency refresh, forced rerun or
cache-disabling flag was used. These are deterministic test results, not load or
independent-peer measurements.

Cycle 18 ran `./gradlew check build --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step7'` after resynchronizing the seven finalized Step 6 shared sources and their owner-written report. It passed: `solidReview`, formatting verification and the three archives executed; `test` and `javadoc` reused their matching up-to-date cycle-17 results. `compileReviewTestJava` and `reviewTest` were restored from the normal build cache, not freshly executed. This isolated worktree contains the necessary Step 6 dispatch dependencies, not the Step 6 control-command suite; the root integration separately runs that complete combined suite. No cache restoration is described as fresh behavioral evidence.

Cycle 19 ran `dependencies --configuration runtimeClasspath` with the same wrapper/console/JVM flags. It reported **No dependencies**. Python standard-library ZIP inspection confirmed the library JAR contains the message command/factory classes and excludes test/review tooling. Cycle 20 reran `check` to reconcile the then-final report narrative with declared review inputs, before the subsequent preflight regression.

Cycle 21 exposed the outgoing aggregate-bound ordering gap with a freshly executed behavioral red. Encoding now invokes the existing raw TLV codec's byte/count preflight before any standard or vendor interpretation, and reuses its single bounded encoding in every body writer. The focused green executed all 27 Step 7 methods freshly and reused the configuration cache. No shared Step 6 or existing TLV source was changed.

Cycle 22 ran `spotlessApply` separately. Cycle 23 ran `test javadoc solidReviewInventory`: all **122 ordinary tests**, including **27 Step 7 methods** and the six architecture tests, passed freshly with no warnings; Javadoc and inventory also executed, while compilation was up to date. The final hashes above come from this post-format inventory. The three changed types were reviewed again as whole types under all five principles, including their callers and the new bounded byte-array handoff.

Cycle 24 ran `check build` successfully after the updated reviews and documentation. `solidReview`, the library JAR and source JAR executed; formatting verification, ordinary/review tests, Javadoc and the Javadoc JAR were up to date. The normal configuration cache was reused. This closes the preflight finding with fresh cycle-23 behavioral evidence and current review hashes, without claiming a cached or up-to-date task executed again.

The 28 blocks above reconcile the complete Step 7 inventory. The copied shared sources receive their owner's matching review and are excluded from the Step 7 deliverable. No unresolved SOLID finding remains.

## Root integration

Applied the final Step 7-only patch after `88744c0` (`Add session command codecs`).
No existing Java source changed. The shared overview, API, roadmap, field guide,
protocol/TLV inventory and review/test status now record the implemented message
subset and preserve the distinction from endpoint/network support. The field
guide also records raw TLV bounds before typed interpretation.

`./gradlew build solidReviewInventory --console=plain` passed in the root
checkout (`/tmp/lightweight-smpp-step7-integration-build.log`). All **182**
library/architecture cases executed freshly, combining the complete bind/control
and message suites; all six architecture tests selected the actual combined
production packages. The unchanged **60** review-tool cases were UP-TO-DATE.
There were zero failures, errors or skips, and no compiler/Javadoc warnings.
The build reused its configuration cache, with 11 executed tasks and three up
to date. No clean, forced rerun or dependency refresh was used.

Inventory and coverage output agree on **99 current type identities**, with
every source hash independently recomputed. The binary and source JAR contents
match production outputs, review tooling remains excluded, and all three JARs
contain exact Apache `LICENSE` and `NOTICE` bytes. Markdown validation checked
25 documents and 184 local links, balanced fences and footnotes; whitespace
validation passed. A matching build after the final narrative validates the
report bytes with the normal declared review inputs.

The session implementation owner independently reviewed message/session API
composition and found no conflicting contract: data direction is the original
request origin; outgoing encoders require the effective profile and honest field
requirements; a generic nack bypasses the concrete message-response validator.
Raw invalid headers remain available for error authorization, and neither codec
registration nor raw advertisements establish endpoint capabilities. This was
a read-only semantic review, not an additional executed test or network result.

The control-codec owner independently reviewed the final Step 7 source: all six
command paths and direct-call contracts, exact tables, 51 value codecs, companion
and original-request rules, immutable extension/value ownership, tests, and the
documented specification interpretations. The final bound-before-interpretation
fix was included. That read-only review found no additional blocker; it did not
change files or claim another test execution.
