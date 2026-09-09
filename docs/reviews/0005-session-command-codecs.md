# Review: session command codecs

## Scope

- Development baseline: `03959d4` (Steps 1–5); integration baseline: `202510a` adds the separately owned Apache license.
- Step 6 adds all bind modes/responses, unbind/enquiry pairs and generic_nack through immutable values and network-independent complete-PDU dispatch.
- The initial responsibility plan separated immutable command/envelope values, a small body-codec registration contract, shared bounded framing, bind/control body rules and focused fixture tests. Session policies, authentication and networking were excluded.
- Final inventory: **23 new Java files, 28 new type identities, 71 current repository type identities**. No pre-existing Java file was changed. Existing 43 identities retain their prior current reviews.
- The inventory includes `ControlCommand.Type` and four nested dispatch fixtures. There are no local or anonymous Java types, generated test source specimens or temporary Java fixtures outside the final inventory in this step. The intermediate helper name `ControlResponseTlvs` was renamed to `ControlTlvCodec` when its forward-compatibility responsibility expanded; the current complete helper is reviewed below.
- Source identities and whole-file SHA-256 values below were reconciled with a separate inventory invocation **after** formatting. Full affected types and their protocol/profile/codec consumers were reviewed, not just additions.
- Public usage, bounds, precise error-body interpretations, raw version separation and references are in [COMMANDS.md](../COMMANDS.md).

## Protocol decisions and corrections

Independent fixtures use the locally available original specifications at `/tmp/lightweight-smpp-research-qzjlxvbt/smpp34.{pdf,txt}` and `smpp5.{pdf,txt}`. Primary web search also located the original [SMPP 3.4 specification](https://smpp.org/SMPP_v3_4_Issue1_2.pdf) and [SMPP 5.0 specification](https://smpp.org/SMPP_v5.pdf); implementation checks used their complete local text rather than relying on short search extracts.

Wire references: 3.4 §§4.1.1–6, 4.2, 4.3.1, 4.11, 5.1.1–4, 5.2.1–7, 5.3.2.25; 5.0 §§3.2.1, 4.1.1.1–6/8–9, 4.1.2, 4.1.4.1, 4.7.1–3, 4.7.24, 4.8.4.18/51. All successful bind identifiers are terminator-inclusive C-octet fields; requested version and raw advertised version remain independent from the supplied parsing profile and later effective-capability policy.

The 3.4 transmitter/receiver error-body omission notes are applied consistently to all bind modes; its transceiver table does not repeat that note. Incoming 5.0 bind errors ignore the complete supplied body under §3.2.1.3 and never produce a version advertisement from ignored bytes. Outgoing bind errors reject body data. 5.0 §§2.9/4.8.4.18 allow congestion in any operation response, so TLV-only control responses may include it even for nonzero status; this resolves the general error-body text against the specific extension. Enquiry's normal zero-status table is retained for normal replies, while explicit numeric peer errors remain representable under the error-procedure/API contract.

An initial overly narrow header-only interpretation rejected 3.4 control-response tails and request tails. Review against 3.4 §3.3 and 5.0 §2.11.1 corrected that interpretation: incoming well-formed unsupported TLVs are preserved in bind/control requests and responses without outgoing permission. Cycle 18 first demonstrated these failures, then changed decoding and the formerly incorrect strict expectation. Failed-bind omission remains a separate rule. Reserved incoming TON/NPI stays raw; outgoing reserved values are rejected. There is no implicit version negotiation or unsupported-method implementation.

The shared direct body-codec boundary needed consistent status/context/identity checks; cycle 19 introduced the focused `CommandCodecChecks` helper. Registry output bounds, type/ID/null postconditions and metadata validation were demonstrated before fixing them in cycles 16–17. One intentionally dishonest generic metadata cast exists only in the negative dispatch specimen; it is narrowly suppressed and documented. No production warning suppression was added.

## TDD evidence

All listed commands ran in `/tmp/lightweight-smpp-step6` using this exact suffix:

```sh
--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step6'
```

Each row means `./gradlew test <selection> <suffix> > /tmp/step6-NN-red.log 2>&1`, then the stated implementation change and `./gradlew test <green selection> <suffix> > /tmp/step6-NN-green.log 2>&1`. Cycle 06's real behavioral red uses `step6-06-red-behavior.log`. Selections below are literal command arguments. All twenty-one behavioral red runs reached `:test FAILED`; no syntax/dependency/environment failure is counted as red.

| Cycle | Test selection (red and green unless stated) | Observed behavioral red |
| --- | --- | --- |
| 01 | `--tests 'kg.aidarbek.smpp.codec.PduCodecTest'` | Expected 17-byte registered-command frame, got zero bytes. |
| 02 | `--tests 'kg.aidarbek.smpp.codec.PduCodecTest'` | Expected decoded immutable envelope, got null. |
| 03 | `--tests 'kg.aidarbek.smpp.codec.PduCodecTest'` | Length mismatch/oversize frame accepted instead of rejected. |
| 04 | `--tests 'kg.aidarbek.smpp.codec.PduCodecTest'` | Invalid request status/sequence accepted instead of rejected. |
| 05 | `--tests 'kg.aidarbek.smpp.codec.PduCodecTest'` | Invalid allocation limits accepted instead of rejected. |
| 06 | `--tests 'kg.aidarbek.smpp.codec.PduCodecTest'` | Unknown/unavailable command produced NullPointerException instead of classified dispatch failure. |
| 07 | `--tests 'kg.aidarbek.smpp.codec.BindCommandCodecTest'` | All six bind request fixtures expected 36 bytes, got a header-only 16 bytes. |
| 08 | Red: `--tests 'kg.aidarbek.smpp.protocol.BindRequestTest'`; green adds `--tests 'kg.aidarbek.smpp.codec.BindCommandCodecTest'` | Overlong bind value accepted instead of rejected. |
| 09 | `--tests 'kg.aidarbek.smpp.codec.BindCommandCodecTest'` | All six bind response fixtures expected 26 bytes, got 16 bytes. |
| 10 | `--tests 'kg.aidarbek.smpp.codec.BindCommandCodecTest'` | Header-only failed binds incorrectly attempted to parse a missing system-id terminator. |
| 11 | `--tests 'kg.aidarbek.smpp.codec.BindCommandCodecTest'` | Malformed recognized version TLV accepted instead of rejected. |
| 12 | `--tests 'kg.aidarbek.smpp.codec.ControlCommandCodecTest'` | All control decode fixtures returned null command data, causing envelope null rejection. |
| 13 | `--tests 'kg.aidarbek.smpp.codec.ControlCommandCodecTest'` | Congestion response fixtures expected 21 bytes, got 16 bytes. |
| 14 | `--tests 'kg.aidarbek.smpp.codec.ControlCommandCodecTest'` | Zero-status generic_nack accepted instead of rejected. |
| 15 | Red: `--tests 'kg.aidarbek.smpp.protocol.ControlValuesTest'`; green adds `--tests 'kg.aidarbek.smpp.codec.*CommandCodecTest'` | Null bind-response/control references accepted instead of rejected. |
| 16 | `--tests 'kg.aidarbek.smpp.codec.PduCodecTest'` | Oversized extension output accepted instead of rejected; scenario also asserts null/wrong-ID/type result rejection. |
| 17 | `--tests 'kg.aidarbek.smpp.codec.PduCodecTest'` | Out-of-range command-codec ID metadata accepted instead of rejected. |
| 18 | `--tests 'kg.aidarbek.smpp.codec.*CommandCodecTest'` | Forward-compatible request and 3.4 control-response tails rejected as forbidden body/trailing bytes. |
| 19 | `--tests 'kg.aidarbek.smpp.codec.CommandCodecContractTest'` | Direct body calls accepted invalid status/context instead of rejecting it. |
| 20 | `--tests 'kg.aidarbek.smpp.codec.BindCommandCodecTest'` | Reserved TON/NPI bind output accepted instead of rejected. |
| 21 | `--tests 'kg.aidarbek.smpp.codec.ControlBoundsTest'` | Oversized invalid typed TLV reported its semantic length error before the configured byte/count limit; the preflight-order assertions failed. |

All twenty-one green runs executed `:test` (none restored that task from cache). Relevant unchanged compilation tasks were reused where Gradle reported `UP-TO-DATE`; configuration cache entries were reused when command selections matched. No clean, rerun-tasks or refresh-dependencies invocation was used.

An initial cycle 06 attempt failed `compileJava -Werror` because Throwable serialization would retain a nonserializable PduHeader field. That log, `/tmp/step6-06-red.log`, is explicitly **not behavioral red evidence**. Storing the four raw header primitives corrected the declared serialization contract without suppression; the subsequent red-behavior run reached the intended dispatch failure.

Additional existing-behavior characterization ran `./gradlew test --tests 'kg.aidarbek.smpp.codec.ControlBoundsTest' <suffix>` (`/tmp/step6-boundary-characterization.log`) and passed on its first run. It is not claimed as another red cycle. It checks exact and one-too-small PDU limits, every bind text boundary, truncated/non-ASCII wire fields, incoming TLV limits for all eleven IDs, zero count/bytes, error-body omission and large configured maxima without allocating those maxima.

A green refactor verification used `./gradlew test <suffix>` (`/tmp/step6-refactor.log`). Formatting/Javadoc/test/inventory were then requested together with `./gradlew spotlessApply test javadoc solidReviewInventory <suffix>` (`/tmp/step6-format-inventory.log`). That successful Gradle task graph scheduled inventory before formatting, so its provisional hashes were discarded. The separate subsequent command `./gradlew solidReviewInventory <suffix>` (`/tmp/step6-final-inventory.log`) generated the authoritative post-format hashes. A Python SHA-256 reconciliation verified every new source against that inventory before this report was written.

The initial `./gradlew test <suffix>` baseline (`/tmp/step6-baseline.log`) restored compilation and the baseline test task FROM-CACHE; it was not a fresh run. The first full reviewed build recorded 153 library/architecture cases, zero failures/errors/skips. Final allocation-order review then found that outgoing typed TLV interpretation could copy a value before its aggregate byte/count bound was checked. Cycle 21 demonstrated the incorrect failure precedence and moved bounded raw encoding ahead of typed interpretation. Its two profile cases bring the final suite to 155 cases. The private helper and boundary test were reformatted with `./gradlew spotlessApply <suffix>` (`/tmp/step6-final-format.log`), then reinventoried separately with `./gradlew solidReviewInventory <suffix>` (`/tmp/step6-final-inventory-after-21.log`). All block hashes below use that final inventory. Final `check build` and cache observations are recorded below after execution.

## Complete per-type review

Every block reviews the current whole type and its stated consumers. S/O/L/I/D findings were checked against the field/envelope/profile boundaries and executed contract evidence; passing the architecture or review-coverage checker alone was not treated as semantic review. The negative specimen's intentionally invalid inputs are isolated validation fixtures, not claimed conforming codecs. No unresolved design finding remains.

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/BindRequestCodec.java
type: kg.aidarbek.smpp.codec.BindRequestCodec
sha256: eb25e69df55a661c2948d51358dccbac70ec3b0d0d61488b1ea72e464fc3aa92
responsibility: Translates one configured bind request mode with bounded standard fields and raw incoming TLV extensions.
consumers: ControlCommandCodecs registers one instance per mode; PduCodec and direct callers consume CommandCodec<BindRequest>.
S: pass | Wire layout and outgoing bind field validity are the only drivers; authentication, routing regexes and version negotiation remain outside.
O: pass | Mode is immutable constructor data and shared field/TLV primitives handle representation; new command families register separate codecs.
L: pass | All three modes in both profiles pass independent byte fixtures, malformed/max fields, raw reserved octet and shared direct-call contracts; unsupported outgoing extensions fail without altering input.
I: pass | The implementation fulfills all four body-codec methods meaningfully and imposes no response-only methods.
D: pass | Depends on field/TLV helpers, protocol data and supplied profile/limits, with no socket or session dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/BindResponseCodec.java
type: kg.aidarbek.smpp.codec.BindResponseCodec
sha256: ad29f991a8e13a4fadff63cc76c153a2639f11f34a1580421744e994e9c52de2
responsibility: Translates bind response bodies with explicit status/profile-specific body omission rules.
consumers: PduCodec and direct CommandCodec callers consume one registration per BindMode; ControlTlvCodec validates optional tails.
S: pass | Its change driver is bind response wire layout, including the documented 3.4/5.0 error-body distinction; it never computes effective capabilities.
O: pass | Mode is configured once and raw/typed optional validation is delegated; other command families do not change this codec.
L: pass | Independent response fixtures preserve unknown statuses, distinguish absent/empty identifiers, ignore 5.0 failed-body contents and enforce direct-call ownership/bounds; tests exercise every mode/profile.
I: pass | Only BindResponse data is accepted; request credentials and session callbacks are not part of the interface.
D: pass | Uses protocol values, supplied profiles and local bounded helpers; no application authentication or transport implementation is referenced.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/CommandCodec.java
type: kg.aidarbek.smpp.codec.CommandCodec
sha256: 3a0eb5d02fe97c8f66750da2dadc4eaab9fa746ff74cbbaaf9f093f33a0250a9
responsibility: Defines translation and validation of one immutable command body at the dispatch extension boundary.
consumers: PduCodec consumes metadata/encode/decode; bind and control implementations satisfy the same direct-call contract.
S: pass | Owns body translation, its context and allocation/ownership obligations; sequence allocation, network framing and session policy are excluded.
O: pass | Additional commands provide a new implementation and registration without modifying dispatch or existing control codecs.
L: pass | Stable metadata, bounded owned arrays, immutable results, request/status validation and full-body handling are explicit and exercised across all eleven standard registrations by CommandCodecContractTest.
I: pass | Only identity, representation and bidirectional body translation are required; implementations have no unsupported command methods.
D: pass | The abstraction uses protocol values, profiles and bounded byte arrays rather than sockets or framework buffers.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/CommandCodecChecks.java
type: kg.aidarbek.smpp.codec.CommandCodecChecks
sha256: f8df0a48522f03400a16a98a5592f0eadb5806be9ca9ddbc8f291924931e4214
responsibility: Applies shared preconditions for direct body-codec calls that bypass full-frame dispatch.
consumers: All three standard body codec implementations and the conforming dispatch fixture reuse context and identity checks.
S: pass | Only direct-call context and command identity validation drive this helper; body layout/TLV semantics remain in owning codecs.
O: pass | New body codecs can reuse these fixed preconditions; adding body fields does not modify this helper.
L: pass | No custom supertype exists; deterministic null/range/identity rejection preserves the body interface preconditions, as shared direct-call tests verify.
I: pass | Its two package-private operations match separate context-only decode and command-bearing encode callers.
D: pass | Uses Pdu/PduHeader shared protocol invariants and a supplied profile; it constructs no variable infrastructure.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/CommandDispatchException.java
type: kg.aidarbek.smpp.codec.CommandDispatchException
sha256: c7caf5b4015f9014631efe8afd123ba0f4f415f72a57f220bccd6d1b03dffd6d
responsibility: Describes an undefined command or unavailable local body codec while retaining its header data.
consumers: PduCodec throws it; later session error policy can inspect header and definedByProfile without receiving body contents.
S: pass | Its sole responsibility is a dispatch failure diagnostic, not choosing a nack status or closing a connection.
O: pass | New command registrations do not alter this two-case failure contract; raw numeric header data survives unknown IDs/statuses.
L: pass | IllegalArgumentException/Throwable behavior is retained; primitive serialized fields preserve header data without introducing nonserializable state, and PduCodecTest verifies exact header/status/classification.
I: pass | Consumers have only the original header and a classification accessor; credentials and arbitrary PDU bodies are not exposed.
D: pass | Depends on the raw protocol header and JDK exception facilities; no transport or profile instance is retained.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/ControlBodyCodec.java
type: kg.aidarbek.smpp.codec.ControlBodyCodec
sha256: b5d237d200770d5400cfec21ae79c27b7345f83271b2bebb2fbd682afbd5fed8
responsibility: Translates one fixed control identity with no standard fields and explicit outgoing optional-extension policy.
consumers: ControlCommandCodecs supplies five instances; PduCodec and direct shared-contract tests consume them.
S: pass | Only control body rules, including nonzero nack status and congestion extension, drive this class; keepalive scheduling and shutdown state are absent.
O: pass | Type data selects one of the genuinely shared empty-standard-body contracts; unrelated commands add independent codecs.
L: pass | Independent fixtures cover both profiles, maximum/zero nack sequences and unknown statuses; shared direct-call tests verify matching identities, bounds and owned arrays.
I: pass | Every CommandCodec method has a meaningful implementation; no unsupported operation stubs or inheritance between unrelated commands exist.
D: pass | Depends on control values, supplied profiles and shared TLV/precondition helpers without infrastructure.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/ControlCommandCodecs.java
type: kg.aidarbek.smpp.codec.ControlCommandCodecs
sha256: 4010e489599b791ff53754686312fc80889de683ab3e68b455c33fdba8650755
responsibility: Composes the eleven standard control command registrations into an immutable list.
consumers: PduCodec callers combine this list with other command families; shared-contract tests iterate every returned implementation.
S: pass | This is the explicit composition point for control implementations only, not a service locator or parser.
O: pass | Other families supply independent registration lists and are concatenated by callers, leaving this control composition unchanged.
L: pass | Returned lists cannot be mutated and all supplied implementations have stable metadata/thread-safe state; immutable-list and shared-interface tests establish the contract.
I: pass | One factory is sufficient for consumers; no unrelated codec-family or infrastructure setup is required.
D: pass | Concrete body codecs are instantiated only at this focused composition boundary; dispatch itself consumes the interface.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/ControlTlvCodec.java
type: kg.aidarbek.smpp.codec.ControlTlvCodec
sha256: 41673aae6af85a445f12d37106c1e559dcbb139fac6769199dce20ae4e8ce849
responsibility: Frames and validates the trailing optional region of bind and control bodies.
consumers: BindRequestCodec, BindResponseCodec and ControlBodyCodec delegate trailing-byte/count and applicable typed/occurrence checks.
S: pass | Its single driver is control-context optional parameter validation; mandatory field parsing and status-specific body omission remain in callers.
O: pass | Existing profile rules and typed registry provide supported parameter contexts; unknown incoming tags remain raw without modifying unrelated dispatch code.
L: pass | No custom supertype exists; decode preserves order/repetition for unexpected entries, applies recognized singleton/length rules and checks limits before TLV copies; encode preflights aggregate bytes/count before typed copies and rejects unsupported contexts/values, including the cycle 21 allocation-order regression.
I: pass | Package-private encode and decode serve the distinct send/receive policies without exposing negotiation or response-state methods.
D: pass | Reuses ProtocolProfile/TlvRules and TypedTlvRegistry boundaries plus raw TlvCodec; it does not infer endpoint permission.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/PduCodec.java
type: kg.aidarbek.smpp.codec.PduCodec
sha256: c3d098215bd85e6f35c15743ca4960ece6c66be30c32ea76f6b08e3f4b686dfa
responsibility: Owns immutable command registration and bounded full-frame assembly/dispatch.
consumers: Callers supply CommandCodec implementations and PduLimits; body parsing is delegated through that interface.
S: pass | Framing validation, registry lookup and envelope assembly form one dispatch responsibility; body semantics and session error responses are delegated.
O: pass | An ID-to-codec map extends by registration; duplicate/invalid metadata is rejected and new message families need no growing switch.
L: pass | Valid fixtures round-trip with fresh output arrays, exact frame lengths and preserved status/sequence; adversarial result, profile, copied-registration and bounds tests check failure contracts.
I: pass | The public API offers construction plus one encode/decode pair; body implementers are not coupled to network or session callbacks.
D: pass | Depends on the CommandCodec boundary, immutable values and Java buffers; it does not instantiate command-specific implementations or infrastructure.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/PduLimits.java
type: kg.aidarbek.smpp.codec.PduLimits
sha256: 61b66cf4be5df595d510161d64dc184c320dea841c168a4fe5396efda2704375
responsibility: Carries mutually consistent complete-frame, aggregate-TLV-byte and TLV-count allocation bounds.
consumers: PduCodec, standard body codecs and TlvCodec adapters consume these limits during allocation checks.
S: pass | All three values govern one bounded-decoding requirement; timing, request windows and network buffering are absent.
O: pass | Different limits are supplied as immutable data; no subclass or policy hierarchy is needed for supported variation.
L: pass | Record equality and nonnegative consistent bounds hold; tests cover invalid limits, exact fit, one-byte-short limits, count zero and Integer.MAX_VALUE configuration without huge allocation.
I: pass | Consumers receive exactly the three constraints and derived body size they need.
D: pass | Depends only on the protocol header-size constant and primitives, without transport allocation types.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/BindMode.java
type: kg.aidarbek.smpp.protocol.BindMode
sha256: 7b9a66ec95961d14eb1336a2e1630a41cb106604b62b0e28a3bb3d62bfd8625d
responsibility: Names the three bind roles and their paired unsigned wire identities.
consumers: Bind values and codecs read its identities; later session policy reads its role without importing codecs.
S: pass | Only the bind role catalogue and ID pairing drive changes; authentication and state are absent.
O: pass | The three protocol-defined modes are fixed values; new command families extend Command and registry, not this enum.
L: pass | Enum identity, equality and thread-safe immutable long fields hold; all six independent request/response fixtures validate the pairings.
I: pass | Consumers need role identity and two IDs; no endpoint operations or meaningless handler methods are exposed.
D: pass | The enum depends only on Java enum facilities and primitive IDs, preserving protocol-to-profile separation.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/BindRequest.java
type: kg.aidarbek.smpp.protocol.BindRequest
sha256: bb89820eadfc78963ff87574ba359bdcbfada168d1d5fae1769f425637df3f3e
responsibility: Stores immutable bind credentials, raw address/version octets and retained incoming optional extensions.
consumers: BindRequestCodec translates its fields; application/session bind policy later interprets raw version and addressing.
S: pass | Field representation, ownership and safe diagnostics are its single data-value responsibility; it does not authenticate or negotiate.
O: pass | Mode variation is carried by BindMode; optional wire extensions are stored in OptionalParameters without changing field ownership.
L: pass | Command identity follows mode, record equality includes immutable data, strings are bounded strict ASCII, and diagnostics omit sensitive fields; value/boundary tests cover these obligations.
I: pass | The eight-argument convenience constructor serves standard sends and the canonical constructor serves raw receives; accessors impose no callback or session behavior.
D: pass | Only protocol immutable values and JDK strings/collections are referenced, leaving profiles and codecs outside the value layer.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/BindResponse.java
type: kg.aidarbek.smpp.protocol.BindResponse
sha256: c7280ea02fde957076f98d40a01d4a384f180832ad8dd139e5790771f832ba15
responsibility: Represents a bind response body with explicit absence and ordered raw optional data.
consumers: BindResponseCodec uses the Optional systemId and parameters; later version policy reads advertisements separately.
S: pass | Owns response-data invariants and redacted diagnostics only; numeric status stays in Pdu and capability negotiation stays outside.
O: pass | All bind modes share the same body representation, while new optional information remains raw in OptionalParameters.
L: pass | Absent and empty system IDs are unequal, null/invalid text is rejected, immutable component equality is preserved, and no identifier leaks through toString; ControlValuesTest verifies these contracts.
I: pass | Consumers need only mode, optional identifier and raw parameters; no request credential or endpoint methods are required.
D: pass | Depends solely on protocol values and JDK Optional/Objects, with no profile inference or codec dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/Command.java
type: kg.aidarbek.smpp.protocol.Command
sha256: 12a86258709ce42a11c615092949bc3f9c9201b9d501288ba3a2d45cc086841b
responsibility: Defines the minimal immutable command identity contract used by envelopes and dispatch.
consumers: Pdu and PduCodec consume commandId; BindRequest, BindResponse and ControlCommand implement it.
S: pass | The interface owns stable identity and immutable-data obligations only, without framing or peer policy.
O: pass | New commands implement this interface and add a CommandCodec registration; no control switch must grow.
L: pass | The stable unsigned-ID and immutable-value promises are explicit; standard implementations pass shared direct-codec contracts and independent fixtures.
I: pass | Only one identity method is required, so message/control values do not implement unrelated operations.
D: pass | No codec, profile or infrastructure type appears in the protocol interface.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/ControlCommand.java
type: kg.aidarbek.smpp.protocol.ControlCommand
sha256: fcd8ae4dc7b263a13b329a8790ef6e0d5eabe8909d9a880aaa937705c7d7e953
responsibility: Stores one control identity and an immutable ordered optional-parameter block for commands without standard body fields.
consumers: ControlBodyCodec translates the value and Pdu uses its Command identity.
S: pass | Its only change driver is control-value representation; request/response profile and status rules stay in codecs.
O: pass | The fixed Type catalogue selects these shared body contracts; unrelated future commands can use independent Command values.
L: pass | Null references are rejected, Command identity is stable, and record equality composes immutable Type and OptionalParameters values; ControlValuesTest and shared codec tests cover them.
I: pass | Two data components and commandId serve every consumer; there are no irrelevant bind credentials or messaging fields.
D: pass | Only protocol and Java value dependencies are present.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/ControlCommand.java
type: kg.aidarbek.smpp.protocol.ControlCommand.Type
sha256: fcd8ae4dc7b263a13b329a8790ef6e0d5eabe8909d9a880aaa937705c7d7e953
responsibility: Enumerates the five control wire identities whose standard bodies contain no fields.
consumers: ControlCommand, ControlCommandCodecs and ControlBodyCodec select identities through this enum.
S: pass | Only the control identity catalogue drives the type; status/TLV legality is not folded into the enum.
O: pass | The enum is a finite standard catalogue; new families extend Command registration rather than editing this catalogue.
L: pass | Java enum identity and immutable unsigned long IDs hold; independent fixtures exercise every constant in both profiles.
I: pass | One wire-ID accessor is the complete metadata needed by consumers.
D: pass | The enum relies on Java and primitives only; it cannot reach profiles, sessions or I/O.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/Pdu.java
type: kg.aidarbek.smpp.protocol.Pdu
sha256: 2626e521765cd817bbffb0d65cd75d13012f97ddb87827e32fbcc93a9a2117c9
responsibility: Pairs immutable command data with validated command-independent status and correlation sequence.
consumers: PduCodec and future session/correlation consumers use status, sequence and command; body codecs reuse header invariants through CommandCodecChecks.
S: pass | Envelope validation and computed-length separation serve one wire-envelope requirement; no command body or session lifecycle is interpreted.
O: pass | Registering new command bodies does not change this record; the fixed generic_nack zero-sequence exception is a shared header rule.
L: pass | Record equality composes immutable command equality; uint32/status/sequence boundaries and null rejection are exercised by PduCodecTest and control fixtures.
I: pass | Consumers obtain three fields and one reusable header validator; no transport or result-completion capability is imposed.
D: pass | Depends only on protocol Command/PduHeader and Java Objects; there is no forbidden profile dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/BindCommandCodecTest.java
type: kg.aidarbek.smpp.codec.BindCommandCodecTest
sha256: b1cf27fb3ad20fbec62adbea46b8ad479bbd6aa663f58b8b124e9fd3f0c5e27c
responsibility: Verifies bind request/response wire behavior and raw version/optional compatibility in both profiles.
consumers: JUnit executes independent fixtures against PduCodec plus standard control registrations; TypedTlvRegistry observes raw advertisement meaning.
S: pass | Tests address the bind wire contract only, without authentication or network lifecycle fixtures.
O: pass | Profile/mode cases are explicit parameterized fixtures; adding unrelated command tests does not require editing these assertions.
L: pass | JUnit lifecycle and assertion contracts hold; independent bytes prevent paired codec errors from masking one another and no mutable shared state is retained.
I: pass | Private frame helpers serve only these bind scenarios; the test implements no unused library capability.
D: pass | Depends on public protocol/codec/profile APIs and JUnit rather than transport, time or production internals.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/CommandCodecContractTest.java
type: kg.aidarbek.smpp.codec.CommandCodecContractTest
sha256: 6f1612e5ab8948524e83b5025a1c96e87df72a3d71a6246494aa2d58769943f6
responsibility: Runs one shared direct-call contract against all standard body codec registrations.
consumers: JUnit parameterizes profiles; ControlCommandCodecs supplies implementations consumed solely as CommandCodec<T>.
S: pass | Its driver is substitutability at the public body-codec boundary, not implementation-specific parsing details.
O: pass | Every registered standard control codec participates automatically; adding a standard family fixture extends data rather than copying contract assertions.
L: pass | Tests immutable decoded results, unchanged input, fresh outputs, context/status/null/bounds rejection and matching-ID acceptance through the shared generic interface.
I: pass | The generic verifier needs only the four public codec methods and command data; it uses no hidden implementation hooks.
D: pass | Depends on the body-codec abstraction and public immutable fixtures, with no infrastructure or timing dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/ControlBoundsTest.java
type: kg.aidarbek.smpp.codec.ControlBoundsTest
sha256: ede428d585d6cdf7549f55535e3dd3dbcecb433fc39877ae1091593875205cb6
responsibility: Characterizes exact allocation and field boundaries of control body codecs across profiles.
consumers: JUnit drives independent raw frames and small/exact/large configured PduLimits through PduCodec.
S: pass | All scenarios concern bounded wire representation; load measurements and memory-performance claims are excluded.
O: pass | Independent frame helpers vary one field/limit at a time without changing production validation internals.
L: pass | Assertions cover exact-fit, one-too-long, non-ASCII, truncation, byte/count bounds, outgoing preflight precedence before typed copies and ignored error regions; all data is local and deterministic.
I: pass | Only fixture-building helpers support the selected bounds; no unused production interface is required.
D: pass | Uses public APIs and JUnit; no sockets, clocks, file resources or hidden external fixtures are required.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/ControlCommandCodecTest.java
type: kg.aidarbek.smpp.codec.ControlCommandCodecTest
sha256: 2027e464712ffbcb03ecdfe7a4f2568653dd48418d2c38f56d0fc7d264f9c59f
responsibility: Verifies all control wire identities and explicit response/forward-compatibility policies.
consumers: JUnit uses PduCodec, immutable control values and typed congestion interpretation.
S: pass | One behavior area covers empty standard control bodies and their optional extensions; scheduling and session transitions are excluded.
O: pass | Enum/csv parameters share assertions across identities and versions, while command-family growth belongs in separate tests.
L: pass | Fixtures independently encode IDs/status/sequence and assert malformed/outgoing rejection; no shared mutable buffer or resource lifecycle violates JUnit contracts.
I: pass | Only the private complete-frame helper is shared; no handler or transport interface is implemented.
D: pass | Exercises public codec/value boundaries with JUnit and Java hex utilities, avoiding infrastructure dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/PduCodecTest.java
type: kg.aidarbek.smpp.codec.PduCodecTest
sha256: 254283b98594ffb8ca68d79f7bab59b1b737be1f2e709c2fe0d29e144815239a
responsibility: Verifies full-frame registry dispatch, metadata validation and extension boundary failures.
consumers: JUnit uses a conforming one-octet codec and isolated adversarial specimens to exercise PduCodec behavior.
S: pass | All methods test the dispatcher/envelope boundary; the one-octet fixture does not claim to implement real query_sm body semantics.
O: pass | Different registered implementations exercise extension variation without editing production control parsing.
L: pass | Independent header bytes, copied-registration checks, unknown-vs-unavailable classification and adversarial result checks validate observable API contracts; fixtures are local immutable data.
I: pass | Helpers are scoped to dispatch tests; no application, transport or session capability is implemented by the test class.
D: pass | Uses public dispatch/value/profile APIs and JUnit; deliberately invalid metadata is confined to a documented nested test specimen.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/PduCodecTest.java
type: kg.aidarbek.smpp.codec.PduCodecTest.AdversarialCodec
sha256: 254283b98594ffb8ca68d79f7bab59b1b737be1f2e709c2fe0d29e144815239a
responsibility: Produces explicitly invalid extension metadata/results as negative input to dispatcher validation tests.
consumers: Only PduCodecTest instantiates it; no standard registration factory or application uses it.
S: pass | One purpose is to probe enforcement of metadata, output-bound, identity/type and null postconditions.
O: pass | Configured scalar/type/result fields select each negative specimen; production codec implementations remain unchanged.
L: pass | This is an explicitly nonconforming negative-test specimen rather than a substitutable production codec; tests require each invalid metadata/result case to be rejected, and no passing standard-codec contract relies on it.
I: pass | The specimen implements exactly the boundary under test; the one narrow unchecked cast is documented as intentional dishonest metadata, not a warning suppression in production.
D: pass | Uses only immutable fixture metadata and fresh local arrays; the unsafe generic claim is isolated from runtime registrations and tests the abstraction boundary.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/PduCodecTest.java
type: kg.aidarbek.smpp.codec.PduCodecTest.OctetCodec
sha256: 254283b98594ffb8ca68d79f7bab59b1b737be1f2e709c2fe0d29e144815239a
responsibility: Implements a conforming bounded one-octet body format for independent dispatch tests.
consumers: PduCodec consumes it through CommandCodec<OctetCommand>; it uses shared direct-call preconditions and field cursors.
S: pass | Its only change driver is the small test body format; it does not pretend to implement the real query_sm command body associated with the chosen catalogue ID.
O: pass | The fixture can be replaced by another CommandCodec to test registry extension without production edits.
L: pass | It owns no mutable state, uses fresh bounded outputs, decodes exactly one octet, validates direct context and preserves input; fixture round-trip/header tests cover it.
I: pass | All four CommandCodec methods are meaningful for the fixture format, with no unsupported-operation methods.
D: pass | Uses the codec contract and bounded field helpers, not infrastructure or production command implementations.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/PduCodecTest.java
type: kg.aidarbek.smpp.codec.PduCodecTest.OctetCommand
sha256: 254283b98594ffb8ca68d79f7bab59b1b737be1f2e709c2fe0d29e144815239a
responsibility: Provides immutable one-octet fixture data with a fixed registered ID for envelope tests.
consumers: PduCodecTest.OctetCodec translates its value; dispatcher tests compare envelopes by record equality.
S: pass | Only fixture scalar storage and stable identity drive the record; it models no real message-management operation.
O: pass | Test variation is scalar data; unrelated command fixtures are independent Command implementations.
L: pass | The fixed ID is unsigned and immutable, record equality/hash contracts hold, and the codec validates the octet range before writing.
I: pass | Only value and commandId are exposed for the fixture consumers.
D: pass | Depends on Command and primitives; no profile or mutable array is retained.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/PduCodecTest.java
type: kg.aidarbek.smpp.codec.PduCodecTest.OtherCommand
sha256: 254283b98594ffb8ca68d79f7bab59b1b737be1f2e709c2fe0d29e144815239a
responsibility: Provides an immutable alternate command identity/representation for registry boundary tests.
consumers: PduCodecTest supplies it as valid or mismatched identity data to the adversarial fixture and dispatcher.
S: pass | Its sole purpose is test identity/representation variation, without actual command body semantics.
O: pass | The scalar ID parameter supplies supported test cases without adding class hierarchies.
L: pass | Used instances have stable unsigned IDs and immutable record equality; deliberately mismatched registration inputs are asserted to fail rather than used as working codecs.
I: pass | The generated commandId accessor is the complete fixture contract.
D: pass | Depends only on Command and a primitive scalar.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/protocol/BindRequestTest.java
type: kg.aidarbek.smpp.protocol.BindRequestTest
sha256: 3cb6803d75dae7e911f4a3dc6599c0508d867deeea47b86c2dd46db797519122
responsibility: Verifies bind credential field invariants, raw octet widths and redacted value diagnostics.
consumers: JUnit constructs BindRequest directly without codecs or sessions.
S: pass | The tests concern one immutable value contract rather than duplicating wire parsing or authentication tests.
O: pass | Input data supplies field-boundary variation; new protocol values can receive separate focused tests.
L: pass | JUnit assertions independently check overlong/non-ASCII/NUL/null/out-of-range rejection and preservation of unknown raw octets; test data remains immutable/local.
I: pass | One request construction helper serves all cases without requiring a fake endpoint interface.
D: pass | Depends only on the protocol value and JUnit, which keeps value tests independent of codec/profile implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/protocol/ControlValuesTest.java
type: kg.aidarbek.smpp.protocol.ControlValuesTest
sha256: a6518809cac321e64f1c98a074d988dbdc819c161ad4b44e9fb85f9b01f47e97
responsibility: Verifies immutable bind-response and control-value references, equality and diagnostics.
consumers: JUnit directly constructs BindResponse, ControlCommand and empty OptionalParameters.
S: pass | Both tested values share the control-data invariant requirement; status/profile parsing is left to dedicated codec tests.
O: pass | New codec or session behavior does not change these immutable value assertions.
L: pass | Asserts null/text rejection, absent-vs-empty distinction, equal hash codes and redacted identifiers using standard JUnit contracts and immutable shared fixtures.
I: pass | The test requires only value construction/accessors and Object methods, without unused interface methods.
D: pass | Uses protocol values and JUnit, with no dependency on profiles, codecs or external resources.
findings: none
```

## Final verification

The final current-source build passed:

```sh
./gradlew check build --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step6'
```

`/tmp/step6-final-check-build-after-21.log` records `BUILD SUCCESSFUL`, configuration-cache reuse, eight executed tasks and five up-to-date tasks. The library/architecture test task executed **155 cases**, with zero failures, errors or skips. The unchanged review-tool suite's **60 cases** were reused (`UP-TO-DATE` here, after `FROM-CACHE` in the first final build); no fresh tooling-test execution is claimed. Formatting verification, Javadoc, library/sources/Javadoc JAR assembly and the current SOLID coverage check passed. All Java compilation uses `-Xlint:all -Werror`; no production suppressions or Javadoc warnings were introduced.

`solidReview` covered all **71 current type identities**, including the 28 additions reviewed here. A separate SHA-256 reconciliation matched every current source file to the authoritative inventory. Existing architecture rules executed against all production protocol, codec and profile packages: permitted dependency direction, absence of network/filesystem/concurrency infrastructure, and package-cycle freedom. Their existing nonempty production import checks remain active. This step adds no new package boundary requiring a new rule.

```sh
./gradlew dependencies --configuration runtimeClasspath --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step6'
```

`/tmp/step6-runtime-dependencies.log` reports **No dependencies**. Runtime JAR inspection confirmed review-tool classes are excluded. Both new Markdown files' relative links resolve. No source or report was excluded from review scope, and no cache was cleared or disabled.

This completion narrative is followed by an independent `./gradlew solidReview --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step6'` check of the delivered report bytes, recorded in `/tmp/step6-delivery-review.log`. Network interoperability and session/capability policies remain later-step work; the completed claim here is the network-independent codec layer. Remaining Step 6 findings: none.
## Root integration

Applied the Step 6-only patch after `202510a` (`Add Apache license`) and reviewed
the final command values, dispatcher, body codecs, allocation ordering, direct
extension contracts and tests. No earlier Java file changed. Shared overview,
API, roadmap, protocol/TLV inventory, test-plan and policy status now describe
Step 6's completed codec scope and keep full endpoint evidence pending.

`./gradlew build solidReviewInventory --console=plain` passed in the root
checkout (`/tmp/lightweight-smpp-step6-integration-build.log`). Its 14 actionable
tasks comprised seven executed, four from cache and three up to date. Production
and test compilation, Javadoc and the 155-case ordinary test task were restored
FROM-CACHE from matching worktree results; the 60-case review-tool task was
UP-TO-DATE. These root results are explicitly reused, with fresh Step 6 execution
recorded above. The task graph was stored after recognizing the licensing build
configuration change.

The root inventory and coverage output agree on all 71 type identities and their
current file hashes. Archive inspection matched binary classes and source files
to production outputs, excluded review tooling, and verified exact `LICENSE` and
`NOTICE` bytes in binary, source and Javadoc JARs. Markdown validation checked
23 documents and 163 local links, balanced fences and footnotes; whitespace
validation passed. A matching build invocation after this narrative checks the
final report bytes and configuration-cache reuse before committing.
