# Review: message helpers and content scenarios

## Scope and contracts

Starting revision: `e79b9f4cb905056fe6d91ded563e2a19b48ce4d9` (Steps 1–11).
This Step 15 patch adds ten library source files, six library test files and two
simulator adapter/test files: **21 Java type identities**, including the private
reassembly group, fixed content plan and SAR content plan. It changes no existing
protocol codec, request owner, session or endpoint Java type. Every owned final
helper source identity and its post-format whole-file SHA-256 appears below.
The follow-up also updates ArchitectureTest and adds one nested negative fixture;
those two identities are reviewed in
[0014-message-architecture.md](0014-message-architecture.md). The complete Step 15
handoff therefore owns **23 affected type identities** across 19 Java files.

The pure `message` package provides explicit GSM7_UNPACKED and strict UCS-2,
encoded-unit segmentation, canonical octet-aligned UDH, bounded SAR reassembly,
and raw-preserving receipt text/TLV helpers. `HelperContentPlans` adds six bounded
simulator scenarios through ContentPlan. The full public policies and primary
SMPP/ETSI sources are in [MESSAGE_HELPERS.md](../MESSAGE_HELPERS.md).

The review examined complete types and consumers, including immutable
OctetString/OptionalParameters/Tlv ownership, ContentPlan's generation/validation
contract, AutoCloseable behavior, caller-controlled clock requirements and the
simulator's per-stream plan ownership. No source depends on the concrete network
or endpoint implementation. No GSM packing, implicit data_coding interpretation,
automatic receipt correlation or autonomous retransmission is claimed.

## Actual TDD evidence

All Gradle commands below ran sequentially in the isolated Step 15 worktree with
normal caches preserved and this exact suffix:

```
--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step15'
```

Command abbreviations in the table are exact task/filter prefixes before that
suffix:

```
E: ./gradlew test --tests 'kg.aidarbek.smpp.message.TextEncodingTest'
H: ./gradlew test --tests 'kg.aidarbek.smpp.message.*Test'
S: ./gradlew :simulator:test --tests 'kg.aidarbek.simulator.HelperContentPlansTest'
```

Logs were retained as `/tmp/step15-NN-description.log`; the integration handoff
also includes copies and hashes. Each row describes an observed behavioral red
followed by a green with its assertions preserved.

| Cycle / command | Red log and observed missing behavior | Green log |
| --- | --- | --- |
| GSM / E | `01-gsm-red`: independent alphabet fixture expected 63 octets, got 0 | `02-gsm-green` |
| Strict conversion / E | `03-strict-encoding-red`: known GSM decode expected `Hello €!`, got empty | `04-strict-encoding-green` |
| Unit segmentation / H | `05-segmentation-red`: escaped boundary expected part lengths [152, 10], got [162] | `06-segmentation-green` |
| SAR trio / H | `07-sar-red`: expected tags 0x020c/0x020e/0x020f, got an empty list | `08-sar-green` |
| Reassembly / H | `09-reassembly-red`: final out-of-order fragment produced no complete value | `10-reassembly-green` |
| Capacity / H | `11-reassembly-bounds-red`: two group/segment-capacity cases failed to throw IllegalStateException | `12-reassembly-bounds-green` |
| Canonical UDH / H | `13-udh-red`: independent header fixture expected 10 octets, got 4 | `14-udh-green` |
| Receipt text / H | `15-receipt-text-red`: parsed example lacked the required ID | `16-receipt-text-green` |
| Receipt variants / H | `17-receipt-variants-red`: FLEXIBLE rejected missing example fields | `18-receipt-variants-green` |
| Receipt TLVs / H | `19-receipt-tlv-red`: raw receipt ID was absent | `20-receipt-tlv-green` |
| TLV bounds / H | `21-receipt-tlv-bounds-red`: malformed receipt TLV did not throw | `22-receipt-tlv-bounds-green` |
| Canonical ambiguity / H | `23-receipt-build-ambiguity-red`: leading-space field name did not throw | `24-receipt-build-ambiguity-green` |
| Text adapter / S | `29-adapter-text-red`: known GSM fixture expected 6 octets, got 0 | `31-adapter-text-green` |
| SAR adapter / S | `32-adapter-sar-red`: factory rejected the selected SAR scenario | `33-adapter-sar-green` |
| Receipt adapters / S | `34-adapter-receipt-red`: factory rejected the selected receipt scenario | `35-adapter-receipt-green` |
| Compatibility / S | `36-adapter-compatibility-red`: expected six named scenarios, got none | `37-adapter-compatibility-green` |
| Replacement metadata / S | `49-replacement-metadata-red`: gsm7/replace compatibility expected false, got true because replace_sm cannot declare the fixture's original encoding/flags | `50-replacement-metadata-green` |

Cycle 23's test was renamed from
`buildingRejectsLabelsAndSpacingThatWouldChangeFieldValuesOnParsing` to
`canonicalBuildingRejectsAmbiguousLabelsAndNonTextEdgeSpaces`; no assertion was
weakened. Log `30-adapter-text-green` actually contains a compiler rejection for
an unnecessary cast under `-Werror`, **not a passing run or a behavioral red**.
The cast was removed and log 31 records the passing result.

The additional alphabet, maximum-part, incomplete-expiry and coordinated
concurrent-final-duplicate cases passed immediately in
`25-boundary-characterization`; they characterize existing behavior and are not
presented as fabricated reds. The final concurrency fixture uses a latch and
bounded Future.get calls, with no deadline sleeps.

## Review corrections and final checks

`26-format` and `28-doc-format` record earlier separate formatter runs.
`27-core-inventory-test-javadoc` passed tests but exposed 23 helper Javadoc tag
warnings. The malformed compact tag layout was corrected; no warnings were
suppressed. `38-pre-review-green` ran H and S together after Javadoc cleanup.
The existing receipt-bound tests established green before a behavior-preserving
refactor that bounds labels/output before normalization and regex inspection;
`39-early-bound-refactor-green` returned H and S to green with unchanged assertions.

Final formatting was a separate `./gradlew spotlessApply` invocation, log
`40-final-format`. The subsequent command
`./gradlew solidReviewInventory test :simulator:test javadoc` succeeded in log
`41-final-inventory-test-javadoc`: the root test task freshly executed **515
cases** (498 inherited plus 17 helper cases) with zero failures/skips, and the
simulator task freshly executed **4 adapter cases** with zero failures/skips.
Inventory contains 223 identities: 199 baseline, 21 owned here and 3 copied
simulator prerequisite types. The library Javadoc completed without warnings;
unqualified `javadoc` also selected the simulator task, which reported 9 missing
comment/@param warnings in those three unfinished Step 13 prerequisites only.

The authorized prerequisite copies are root build.gradle/settings.gradle,
simulator/build.gradle, and ContentPlan/TrafficContent/RawContent. They are
excluded from this patch. A separate local-only review covers their exact copied
Java revisions for isolated review coverage; it does not replace the Step 13
owner's final documentation/review. At this initial handoff the inherited
architecture suite verified its existing boundaries. The subsequently authorized
message-package allow-list and permanent negative fixture are now covered by the
companion architecture review and the later verification below.

`./gradlew check :javadoc` then passed in `42-final-check`. It freshly ran
Spotless verification and `solidReview`, reused already-green behavior/Javadoc
outputs as UP-TO-DATE, and restored review-tool compilation/tests FROM-CACHE.
It does not claim those cached tooling tests freshly executed. Coverage confirms
all **223** current identities, including the separate local prerequisite review.
The final owned Markdown links resolve locally. Updating this report's factual
outcome does not change any Java hash; `43-final-review-coverage` verifies the
final report version with `./gradlew solidReview`.
No runtime dependency or simulator scheduler/report core is included in this
patch. All findings in the owned types were resolved before the source hashes
below were recorded.

A final allocation-order review moved the existing three-byte network-error
builder check before OctetString.value() defensively copies the value. The
already-green malformed-builder cases establish the rejection contract; no
artificial red was created for this behavior-preserving refactor. Separate
formatter log `44-error-bound-format` preceded refreshed source hashes, and
`45-error-bound-green` passed the 17 helper cases, 4 adapter cases and library
Javadoc. The complete ReceiptTlvs type and callers were re-reviewed at the new
hash; no API or wire output changed. `46-refreshed-final-check` covers the final
formatted sources and this updated report.

The replacement follow-up removes `replace` from every helper scenario. The
earlier matrix assertion was moved into a dedicated all-variant regression with
the stronger expected rejection; the rest of the operation matrix remains
unchanged. SMPP 3.4 section 4.10 and 5.0 section 4.5.3 list no data_coding or
esm_class in replace_sm. A caller can replace encoded bytes with an explicit
original-message interpretation, but this bounded helper scenario does not
invent one. The guide now separates content/operation pairing from role/bind
preflight, identifies MC-originating receipts, and uses actual --name=value CLI
syntax. Root-owned integration verifies endpoint direction and per-connection
SAR ordinals; those process regressions are not claimed as adapter unit tests.

`51-architecture-adapter-format` ran formatting separately before refreshed
hashes. `52-architecture-adapter-verification` ran
`./gradlew :test --tests 'kg.aidarbek.smpp.message.*Test' --tests kg.aidarbek.smpp.architecture.ArchitectureTest :simulator:test --tests kg.aidarbek.simulator.HelperContentPlansTest :javadoc solidReviewInventory`
with the common suffix: **31 root cases** (17 helper and 14 architecture) and
**5 adapter cases** freshly passed. The already-clean library Javadoc was
UP-TO-DATE. The inventory now has **224** identities: 198 unchanged baseline,
23 owned affected types and the same 3 excluded prerequisite types. The complete
HelperContentPlans, both nested plans and HelperContentPlansTest were re-reviewed
at their final formatted hashes; their retained-state and ownership contracts
remain intact.

The complete `./gradlew check :javadoc` passed in
`53-final-architecture-check`: **517 library tests** and **5 adapter tests**
freshly executed with zero failures/errors/skips, and `solidReview` confirmed all
**224** identities. Formatting verification, tooling tests and library Javadoc
reused matching UP-TO-DATE results. The source hashes did not change when these
actual outcomes were added; `54-final-review-coverage` verifies the final reports.

A final behavior-preserving visibility refactor makes HelperContentPlans
package-private, matching the simulator's internal scenario contract. The full
green check in log 53 established the baseline; no artificial red was introduced
for reducing this tool type's exposure. `55-visibility-refactor-format` ran
formatting separately, and the same focused command as log 52 passed in
`56-visibility-refactor-green`. All **5 adapter cases** executed freshly; the
unchanged **31 helper/architecture cases** were restored FROM-CACHE and library
Javadoc remained UP-TO-DATE. Inventory refreshed the factory and both nested-plan
hashes. Their complete types were reviewed again; only access scope changed.
The final `check :javadoc` in `57-final-visibility-check` passed with all 224
review identities current, 5 adapter tests freshly executed and the unchanged
517 library tests restored FROM-CACHE. `58-final-review-coverage` checks these
final report updates without changing source hashes.

## Complete per-type review

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/HelperContentPlans.java
type: kg.aidarbek.simulator.HelperContentPlans
sha256: 10ae54d1c96d31c9c03e6bf513f87378948746343a7e762a69e0c89bc5219df6
responsibility: Composes deterministic message-helper content scenarios and their preflight operation compatibility for the simulator.
consumers: Selection within the simulator package calls names/create/supports; ContentPlan consumers generate or validate TrafficContent while operation adapters own payload placement.
S: pass | Variant construction, size interpretation and compatible send names form one scenario selection concern; scheduling, endpoints and report accounting remain outside.
O: pass | ContentPlan is the actual simulator variation boundary, while raw traffic stays in its separate provider; new helper fixtures require only this adapter factory, not the traffic runner or protocol codecs.
L: pass | Factories reject invalid sizes/names before resources and return bounded thread-safe fixtures and immutable names. The operation matrix rejects replace for every variant because original encoding/flags are unavailable; role/bind checks and version-specific payload placement remain operation-owned. Exact encoding/receipt fixtures and the stronger replacement regression verify the result.
I: pass | Three small static methods serve package-local selection/preflight needs and returned plans implement only content generation/validation/completion observation; the factory is not exposed as a public library or simulator API.
D: pass | Depends on pure message/protocol helpers and the simulator content seam; it imports no endpoint, transport, request tracker or scheduling implementation.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/HelperContentPlans.java
type: kg.aidarbek.simulator.HelperContentPlans.FixedPlan
sha256: 10ae54d1c96d31c9c03e6bf513f87378948746343a7e762a69e0c89bc5219df6
responsibility: Repeats one immutable text/receipt fixture and validates its exact received bytes/flags/TLVs with the selected pure helper.
consumers: ContentPlan callers use next/validate; HelperContentPlans supplies the immutable expected content and stateless internal decoder/parser Consumer.
S: pass | Exact fixture identity and its inspection are one receive-validation responsibility; no scheduling or accounting state is stored.
O: pass | The internal Consumer is a real small variation for GSM/UCS-2, receipt-text and receipt-TLV inspection; the ContentPlan contract remains unchanged.
L: pass | Every signed index returns the same immutable fixture; wrong content throws before inspection, internal inspectors are stateless and bounded, and the default incomplete count correctly remains zero for non-SAR variants.
I: pass | Implements the content interface without unrelated lifecycle methods; the default no-assembly observation is meaningful for these fixed single payloads.
D: pass | The wrapper relies on ContentPlan/TrafficContent and a supplied pure inspection action rather than importing transport or endpoint behavior.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/HelperContentPlans.java
type: kg.aidarbek.simulator.HelperContentPlans.SarPlan
sha256: 10ae54d1c96d31c9c03e6bf513f87378948746343a7e762a69e0c89bc5219df6
responsibility: Owns one deterministic multipart fixture and one bounded receive deduplication group for a single stream.
consumers: ContentPlan callers cycle next by per-connection ordinal, validate out-of-order parts and read incompleteAssemblies at shutdown.
S: pass | Fixed fragment generation, exact fixture validation and incomplete observation belong to one SAR scenario; stream allocation and report summation are root simulator responsibilities.
O: pass | Configured size/seed and the existing MessageSegments/SegmentReassembler policies supply needed variation without extending transport or scheduling code.
L: pass | Immutable parts are safely readable; synchronized receive state emits one completed fixture and counts a seen partial as one, duplicates never create new groups, and independent-plan tests prevent cross-stream merging. The paused fixture clock and 1/255/byte bounds are explicit.
I: pass | The three ContentPlan methods all have meaningful SAR behavior; callers need no direct access to the reassembler or its retained payloads.
D: pass | Uses the pure segmentation/reassembly API with an explicit fixed clock; there are no timers, sockets, executor tasks or global connection lookups.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/HelperContentPlansTest.java
type: kg.aidarbek.simulator.HelperContentPlansTest
sha256: 81bee462d8e1ced71ada594cbba21c1714c326cfb87d95917a717bf604a42a63
responsibility: Checks deterministic helper content integration, SAR stream isolation and pre-resource operation compatibility.
consumers: JUnit creates sender/receiver ContentPlan instances and reads immutable TrafficContent values.
S: pass | All scenarios establish simulator content contracts rather than scheduling or endpoint behavior.
O: pass | The variant operation matrix and separate explicit byte/receipt/SAR cases make new scenarios local to this adapter test.
L: pass | Independent GSM/UCS-2/receipt literals, wrong-content rejection, out-of-order duplicates and separate incomplete plans verify the observable interface contract. The dedicated replacement regression rejects every variant without weakening the remaining matrix; all tests are deterministic and allocate no threads or endpoint resources.
I: pass | Uses ContentPlan generation/validation/observation and helper parsing only; no transport mock or broad simulator harness is imposed.
D: pass | Depends on pure helpers and immutable protocol fixtures; expected metadata/bytes are independently stated and not copied from implementation output.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/ConcatenationHeader.java
type: kg.aidarbek.smpp.message.ConcatenationHeader
sha256: 1dbab0f8fe91b40e210bcd3297a9bada631d1fa52121381c1065790d933c8750
responsibility: Adds or extracts one canonical 8-bit or 16-bit concatenation UDH for octet-aligned data.
consumers: Applications carrying explicit binary/UCS-2 UDH use prepend8/prepend16/read alongside MessageSegment.
S: pass | Only canonical header composition/extraction is implemented; packing, UDHI flags and arbitrary IE combination are explicitly outside its responsibility.
O: pass | Raw payload values allow UCS-2 or binary callers without type changes; unsupported header combinations fail visibly and can use a separate future helper.
L: pass | Independent ETSI header fixtures cover big-endian references and unchanged payload octets; invalid references, totals, lengths and unsupported headers are rejected; returned OctetString/MessageSegment values own their bytes.
I: pass | Three static methods expose only the canonical header variants; consumers need no encoding, session or callback interface.
D: pass | Depends on the immutable fragment/protocol values and JDK byte copying, with no transport or provider configuration dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/DeliveryReceipt.java
type: kg.aidarbek.smpp.message.DeliveryReceipt
sha256: 34d26b79504b713fd925320bfc96057ce9991e2bc50b413322518e14130b19fc
responsibility: Preserves one parsed receipt text and its raw canonical-label field view immutably.
consumers: DeliveryReceipts constructs validated results; applications and simulator receipt checks read rawText, fields or optional field values.
S: pass | Only receipt result ownership and access live here; parsing grammar, calendar/state meaning and correlation remain outside.
O: pass | The raw map retains recognized provider labels without adding accessors or changing wire types for each vendor field.
L: pass | An unmodifiable LinkedHashMap copy preserves field order and prevents caller mutation; String values are immutable, absent lookups stay optional, diagnostics redact receipt contents and no value-equality promise is made.
I: pass | Raw-text, complete-map and individual-field access address distinct reader needs without an unrelated persistence or delivery callback contract.
D: pass | Uses JDK immutable/copying facilities only; it has no endpoint, parser configuration or transport dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/DeliveryReceipts.java
type: kg.aidarbek.smpp.message.DeliveryReceipts
sha256: f51f123860784296212be9759dfd834209a9d9ca488214d06dcb3c22aca91e1c
responsibility: Parses and canonically builds bounded receipt text under an explicit raw-field grammar.
consumers: Applications and simulator fixed receipt plans use parse/build; DeliveryReceipt stores results and ReceiptFormat selects policy.
S: pass | Parsing, canonical emission, ambiguity checks and allocation bounds implement one receipt grammar; receipt classification, state/date interpretation and TLV merging are separate.
O: pass | FLEXIBLE preserves unknown recognized labels/raw values and EXAMPLE is explicit; adding provider treatment does not require endpoint or wire-codec changes.
L: pass | Exact raw text, immutable values, absent fields, duplicate detection, label/text/field/output limits and ambiguous builder input are tested; canonical build rejects edge-space loss rather than silently trimming, and input bounds precede regex work.
I: pass | Two static operations serve receipt-text readers and writers; neither requires a PDU, session, TLV merge policy or message store.
D: pass | Depends on JDK text/collection facilities and focused receipt values only, with no network, scheduler or automatic correlation dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/MessageSegment.java
type: kg.aidarbek.smpp.message.MessageSegment
sha256: 677fe788a0cd75c80bfb8a2231576c062ed4bfb111952e38d9403dc9ae7d6e4d
responsibility: Owns immutable concatenation metadata and one bounded raw payload, driven by the SAR/concatenation field contract.
consumers: MessageSegments, ConcatenationHeader, SegmentReassembler and simulator SAR fixtures read record values and optionally request the complete SAR trio.
S: pass | Validation and SAR serialization describe the same fragment; no encoding, timing, reference allocation or transport is mixed in.
O: pass | Raw OctetString supports arbitrary payloads and OptionalParameters composes with other tags; new application encodings do not change fragment ownership.
L: pass | Generated record equality includes immutable OctetString content; finite reference/total/number/payload guards and independent big-endian SAR fixtures verify accepted inputs and owned output.
I: pass | Fragment consumers need only the four accessors; callers choosing SAR can use the focused sarParameters method without implementing an unrelated interface.
D: pass | Only immutable protocol values and JDK collections are used; no codec, session, clock or networking dependency is present.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/MessageSegments.java
type: kg.aidarbek.smpp.message.MessageSegments
sha256: 0ef9c1f630031db2cfcda9dc446c64cf91fa457362debf2d452b6835e8eb24ce
responsibility: Splits encoded text into valid fragment units and interprets explicit SAR metadata, driven by the fragmentation contract.
consumers: Applications and simulator SarPlan call split/fromSar; MessageSegment owns the resulting metadata.
S: pass | Part boundaries and their SAR extraction are one concatenation concern; reference allocation, radio packing, UDH and stateful reassembly are separate types.
O: pass | Caller-supplied encoding and payload limits isolate provider variation; unrelated codecs and endpoints remain unchanged when a caller chooses another bound.
L: pass | Immutable ordered output, one empty part, maximum 255 actual parts, complete encoding units and strict partial/duplicate SAR rejection are covered by known-byte and boundary tests; unrelated TLVs are not mutated.
I: pass | Two static operations cover sending and receiving metadata without forcing callers to own a reassembler or a session.
D: pass | Uses TextEncoding and immutable protocol values only; byte limits and reference identity arrive as explicit policy inputs.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/ReassemblyKey.java
type: kg.aidarbek.smpp.message.ReassemblyKey
sha256: ef86e9e5f6f99568f79be8a5198a229608634621598fbca75ff05130f7bb7ebc
responsibility: Carries one explicitly scoped bounded reference identity for fragment ownership.
consumers: Applications construct keys and SegmentReassembler uses their record equality to separate independent groups.
S: pass | Namespace/reference validation is its only policy; how source, destination, coding and generation enter that namespace remains caller-owned.
O: pass | An opaque namespace admits new provider scopes without changing the reassembler or protocol classes.
L: pass | Immutable String plus unsigned reference preserve record equality/hash semantics; namespace length and reference bounds are tested, and diagnostics redact the namespace.
I: pass | Two accessors are sufficient for grouping; the key imposes no session, address or persistence interface.
D: pass | Only JDK String validation is required; source and generation identity are supplied rather than discovered from infrastructure.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/ReceiptFormat.java
type: kg.aidarbek.smpp.message.ReceiptFormat
sha256: afb94d6d03aa89d6833fb49a9b442fcc5e1b70fb8e105766465b14453642502b
responsibility: Names the two explicitly supported receipt-text grammar and missing-field policies.
consumers: DeliveryReceipts and simulator receipt creation require a caller-selected profile.
S: pass | Only grammar policy selection is represented; state vocabulary, wire coding and TLV precedence are not inferred by the enum.
O: pass | Known EXAMPLE/FLEXIBLE choices keep vendor tolerance explicit; provider-specific parsing can remain an application helper rather than altering protocol codecs.
L: pass | Enum identity is immutable; EXAMPLE and FLEXIBLE have deliberately different documented grammars, and tests select them explicitly rather than expecting one to substitute for the other.
I: pass | One enum argument selects the needed policy without forcing a pluggable parser interface or optional methods.
D: pass | The enum has no dependencies on transport, configuration or sessions; applications supply the policy.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/ReceiptTlvs.java
type: kg.aidarbek.smpp.message.ReceiptTlvs
sha256: 7e91d23d2bb51508432bbaa040ceccfc7c5150d901332b97973ae6b53c0629dd
responsibility: Provides a bounded immutable view and explicit builder for receipt ID, raw state and raw network-error TLVs.
consumers: Applications and simulator receipt fixtures inspect raw original parameters and optional known fields or build supplied fields.
S: pass | Validation, raw field ownership and three-field construction belong to the receipt TLV contract; text grammar and delivery-state interpretation remain separate.
O: pass | Unknown TLVs and unsigned state values remain preserved; provider-specific interpretation extends at the caller without changing this reader or protocol value types.
L: pass | Known fields reject duplicates, malformed C-octet strings and wrong lengths; builder network-error width is checked before defensive copying. Tests preserve opaque ID case/zeros, unknown state 254, raw three-byte errors, original TLVs, missing/empty distinction and finite count/byte bounds.
I: pass | Consumers can read only the optional fields they need and still access original parameters; no mandatory text parser or correlation callback is exposed.
D: pass | Uses immutable protocol TLVs and JDK optionals only; provider precedence and state meaning are explicit caller responsibilities.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/SegmentReassembler.java
type: kg.aidarbek.smpp.message.SegmentReassembler
sha256: 364b3d4f2748bf9fa5154bb55d07ec100d1f446520c0582b8bd9263bc3a485dc
responsibility: Owns bounded multipart state, duplicate suppression and caller-driven expiry for one configured reassembly store.
consumers: Applications and simulator SarPlan call accept and retention/expiry methods with an explicit ReassemblyKey and monotonic clock.
S: pass | Admission, completion, deduplication, accounting and expiry are all consequences of owning fragments; text decoding, reference allocation and notification delivery remain outside.
O: pass | Injected clock, namespace and independent group/segment/global-byte/message-byte limits admit different application policies without changing protocol or session layers.
L: pass | AutoCloseable close is idempotent and clears state; synchronized transitions preserve counts and emit completion once under coordinated duplicate races; capacity rejection, conflicting data, fixed expiry, signed wrap and post-close rejection are tested.
I: pass | The API exposes fragment admission, explicit expiry, three retained-state counters and close; callers are not forced to provide a scheduler or callbacks.
D: pass | Time arrives through LongSupplier and payloads through immutable values; no background executor, transport or endpoint is constructed. The prompt-clock requirement is explicit.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/SegmentReassembler.java
type: kg.aidarbek.smpp.message.SegmentReassembler.Assembly
sha256: 364b3d4f2748bf9fa5154bb55d07ec100d1f446520c0582b8bd9263bc3a485dc
responsibility: Retains one fixed-size group of immutable fragment references and its fixed deadline/count/byte totals.
consumers: Only SegmentReassembler accesses this private nested state while holding the owning monitor.
S: pass | All fields describe one group; global admission and expiration decisions stay with the enclosing owner.
O: pass | The fixed slot array derives from the validated total and needs no provider extension; independent namespaces/policies remain outside the record of a group.
L: pass | There is no custom substitution interface; mutable identity remains private, slots are bounded to 255, and owner-lock confinement plus completion/expiry tests establish consistent counter updates.
I: pass | No public capability or meaningless interface is imposed; the enclosing owner uses every stored field for its invariant.
D: pass | Depends only on immutable OctetString references and primitive metadata; it does not fetch time or invoke external behavior.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/message/TextEncoding.java
type: kg.aidarbek.smpp.message.TextEncoding
sha256: 49ad05bcb86b8707e6174693177d18c8a709de6d09275766ae35abd95c532436
responsibility: Converts explicitly selected text alphabets to and from owned octets; the supported alphabet definition is its change driver.
consumers: MessageSegments and explicit application/simulator payload preparation use encode, decode and encodedLength; wire codecs never select this enum.
S: pass | Only default/extension GSM and strict UCS-2 conversion live here; segmentation, data_coding policy and provider fallback remain separate.
O: pass | The two supported encodings are explicit enum choices; future provider encodings belong at the application/helper boundary without changes to wire codecs or sessions.
L: pass | Enum identity and immutability are preserved; known byte fixtures establish ordering, strict unsupported/surrogate/escape rejection, null rejection and empty-input behavior, and Math.addExact prevents length wrapping.
I: pass | The three conversion methods are the complete capability needed by payload builders and segmentation; there is no session or receipt obligation.
D: pass | Depends only on JDK validation and immutable protocol OctetString; it receives the encoding choice instead of consulting infrastructure.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/message/ConcatenationHeaderTest.java
type: kg.aidarbek.smpp.message.ConcatenationHeaderTest
sha256: 199e597311f6c9339f07693c4588a8b5826df5f5709fda7fe41abe0ec42d3730
responsibility: Checks canonical UDH bytes and strict malformed-header rejection against independent ETSI fixtures.
consumers: JUnit invokes ConcatenationHeader with immutable raw payloads and fragment metadata.
S: pass | All cases concern the sole supported information element and preserving octet-aligned payloads.
O: pass | Additional independent header/error vectors fit the same bounded fixture style without production extension seams.
L: pass | Exact byte assertions detect paired read/write errors; invalid reference/total/header shapes are rejected, and test objects have local ownership and no lifecycle side effects.
I: pass | Only the header API and immutable segment values are required; the test does not force a GSM packer or session setup.
D: pass | Expected hexadecimal headers are specification-derived constants rather than generated by the implementation under test.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/message/DeliveryReceiptsTest.java
type: kg.aidarbek.smpp.message.DeliveryReceiptsTest
sha256: ca749e03e89ff5c352c5c9b119c9fbc15d1c2c895e0e9b0ef52eac3e84c1263d
responsibility: Checks explicit receipt grammar profiles, raw preservation, canonical building and bounded malformed-input behavior.
consumers: JUnit exercises DeliveryReceipts and reads DeliveryReceipt fields with ordinary local maps.
S: pass | Every scenario concerns the same receipt text contract, including unknown/missing fields and ambiguous canonical output.
O: pass | Provider examples vary through ReceiptFormat and raw maps; no subclass hierarchy or endpoint fixture is needed.
L: pass | Assertions preserve opaque ID/date/state strings, complete raw text and immutable maps; malformed/duplicate/boundary inputs fail visibly and the spacing regression retains its original assertions.
I: pass | Uses parse/build and raw result access only; no TLV precedence, message correlation or delivery callback interface is imposed.
D: pass | Expected text and field values are explicit test literals; neither clocks nor implementation-derived expected output are used.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/message/MessageSegmentsTest.java
type: kg.aidarbek.smpp.message.MessageSegmentsTest
sha256: 3a0f18e4a7b989b1c16bcb7df6162174dc71c0a61fe7be59b8ad21458fb5e227
responsibility: Checks encoded-unit segmentation and complete unambiguous SAR field ownership.
consumers: JUnit exercises public MessageSegments/MessageSegment APIs with owned OctetString/Tlv values.
S: pass | Known metadata bytes, part boundaries, maximum counts and malformed fragment input all establish the fragmentation contract.
O: pass | Boundary tables admit further cases without a mock hierarchy or changes to unrelated helper tests.
L: pass | Tests verify ordered immutable output, unchanged escaped/UCS-2 units, invalid limits and actual 256-part overflow; fixtures remain deterministic and local.
I: pass | Only fragmentation and immutable field APIs are needed; no session or reassembly fixture is imposed.
D: pass | Independent numeric TLV tags/bytes and explicit source strings drive expectations; no network or codec implementation dependency is used.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/message/ReceiptTlvsTest.java
type: kg.aidarbek.smpp.message.ReceiptTlvsTest
sha256: 34ff25857e8f44b56bc884503c7408d37f4a968b183c845ceeaf8b4d10822b47
responsibility: Checks raw receipt TLV interpretation/building and finite malformed/duplicate-field boundaries.
consumers: JUnit passes immutable raw OptionalParameters/Tlv fixtures into ReceiptTlvs.
S: pass | Cases cover one receipt-TLV contract including unknown raw state, opaque ID and original extension preservation.
O: pass | Tables admit new malformed field fixtures locally without changes to unrelated receipt-text or endpoint code.
L: pass | Independent raw bytes verify ordering and missing/empty distinctions; arrays/lists are caller-owned, immutable production outputs are checked, and tests have no external lifecycle.
I: pass | Only the receipt TLV view and builder are used; no whole-PDU parser or session fixture is required.
D: pass | Known tag/value constants are independent protocol fixtures; runtime infrastructure and automatic correlation remain absent.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/message/SegmentReassemblerTest.java
type: kg.aidarbek.smpp.message.SegmentReassemblerTest
sha256: b59f7b70219981e8a73f74cc84c9a35553b27647dff8fad06eb0782883b8a93f
responsibility: Checks bounded retention, deterministic expiry and single completion under fragment ordering/duplicate races.
consumers: JUnit owns each reassembler, AtomicLong clock and the one bounded executor used for explicit coordination.
S: pass | All assertions establish fragment-owner accounting and lifetime behavior; no transport or text codec is simulated.
O: pass | Clock and capacity inputs vary through the public constructor; new ordering cases need only local immutable fragments.
L: pass | Tests inspect counts after rejection/close, signed wrap and no deadline refresh, and coordinate concurrent final duplicates with a latch and timed Future.get; the executor is closed after tasks unblock.
I: pass | Uses the reassembly API and injected clock only; no application callbacks or unused handler methods are required.
D: pass | Time is controlled through LongSupplier and races through JDK coordination; no sleep-based deadline guesses or networking dependency is present.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/message/TextEncodingTest.java
type: kg.aidarbek.smpp.message.TextEncodingTest
sha256: 71079862452ca53c4c3a699186407ca3997822c541c8d36ca1f69db2dec2ff63
responsibility: Checks strict selected-alphabet conversion with independent byte fixtures and unsupported-input boundaries.
consumers: JUnit executes local TextEncoding calls; no mutable fixture state or external service is shared.
S: pass | All cases establish the text conversion contract, including known default/extension bytes, UCS-2 and strict rejection.
O: pass | Table/encoding loops add conversion cases locally without changes to production protocol or endpoint code.
L: pass | JUnit lifecycle is respected; exact independent fixture assertions are complemented by alphabet round trips, and all inputs/resources are local immutable values.
I: pass | Uses only the conversion API and JUnit assertions, without a network fixture or unrelated handler interface.
D: pass | Depends on the pure helper and JDK fixture decoding; no implementation output is generated as the independent expected wire fixture.
findings: none
```
