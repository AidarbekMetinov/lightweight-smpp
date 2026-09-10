# Fresh protocol source audit for Maven readiness

## Scope and inspected material

Baseline: `094a84f595b61779904c0190ef15f6430561cd22`, reviewed on 10 September 2026
in the isolated `/tmp/lightweight-smpp-protocol-audit` worktree. The parent assigned
the frozen `protocol` partition from `file-partitions.json` (SHA-256
`140556b0e936f5be231623d4a2452f177b7f7b19016957b01ff88b1824739955`).
AGENTS.md and the current SOLID, TDD and review-format policies governed the work.

Every assigned file was read in full, including its Javadocs, methods, nested
fixtures, local declarations and assertions. Earlier review conclusions were not
used as a substitute for that inspection. The original partition contains **162
Java files: 108 production and 54 test files, 688135 bytes and 14346 lines**.
Its packages contain codec 70, protocol 45, message 16, session 13, profile 10,
SPI 7 and architecture 1 files. The final parser inventory identifies 184 types
inside these files; the whole repository inventory has 512 identities. The
latter count is an automated inventory, not a claim that this reviewer inspected
the other agents' files.

| Coverage classification | Original assigned files |
| --- | ---: |
| Complete UTF-8 text inspection | 162 |
| Inspected binary | 0; no assigned binary files |
| Automated-only inspection | 0 |
| Uninspected | 0 |

[The exact per-file manifest](0019-protocol-audit-files.json) records baseline and
final SHA-256 values, byte/line counts and inspection notes. Its SHA-256 is
`4119cf8da888d0f74f193423006ddbc5321299f2eeb822e50177e95470ca6453`. All baseline hashes were reconciled against the parent's frozen
partition. All 108 production files remain byte-identical to the baseline. The
only final Java change is the test correction below. Root checkout files and
publication configuration were not changed by this work.

## Findings and resolved questions

**P-AUDIT-01 — ownership assertion did not observe the mutated input's result.**
`MessageTlvValueCodecTest.everyApplicableTagHasIndependentBytesAndMalformedLengthEvidence`
decoded a second input array, discarded that value, mutated that array, and then
asserted an earlier value from a different array. This did not establish the
intended decoder ownership guarantee. The corrected test retains the value
returned from the same array that is later mutated. The production decoder
already constructs a defensively copied `OctetString`; no production defect or
behavior change was required. The parent authorized the bounded test correction.

The temporary removal of constructor cloning from `OctetString` made the corrected
assertion fail with `expected: <4> but was: <7>`. The mutation was discarded and
the original file restored exactly. This is an actual mutation-test failure,
not an invented pre-fix production red. The final source contains neither the
mutation nor a new runtime dependency.

No other release-blocking defect was identified in the assigned production files.
The following potentially confusing contracts were checked explicitly:

- Raw bind requested/advertised octets are deliberately preserved, including
  unknown values. Effective version policy belongs to `VersionNegotiation`;
  catalogue membership and incoming extensions do not grant outgoing capability.
- Failed response layouts are operation/profile-specific. The documented 5.0
  diagnostic-TLV versus general error-body tension and the separate control
  congestion interpretation remain explicit supported policies, not undisclosed
  interoperability guarantees.
- Short-message limits are 254 in 3.4 and 255 in 5.0. Addresses, count alternatives,
  C-octet terminators, allocation bounds and unsupported incoming TLVs remain
  separately validated. `data_coding`/`protocol_id` are deliberately raw octets.
- Contextual response validators do not claim sequence correlation. Ordinary
  request correlation, pending ownership and endpoint resources remain outside
  this partition. Unknown responses cannot authorize a nack loop in the pure
  policy layer.
- The SPI explicitly requires retained write deadlines, no post-close emission,
  and termination after physical cleanup and internal callbacks. This independently
  confirms the endpoint auditor's test-double findings; their corrections and
  transport verification belong to that separate audit. The shared immediate
  contract test alone does not cover every deferred/racing implementation case.
- The 3.4 outbind receiver-only follow-up is a full endpoint workflow condition.
  The pure operation table keeps 3.4 OPEN. The endpoint auditor independently
  confirmed `OutbindListenerConfig` and the `EndpointConnection` outbind branch
  enforce RECEIVER for a 3.4 follow-up, with existing endpoint tests. The table
  alone must not be presented as the entire outbind workflow.

## Fresh contract and SOLID inspection

The complete source review traced field/length arithmetic, unsigned handling,
array and collection ownership, failure atomicity, profile tables, supported and
reserved value domains, required/repeated TLVs, callback/SAR/network companions,
response contexts and lifecycle transitions. Independent byte fixtures and
negative assertions were read alongside their implementations. The inventory
reconciliation test complements independently enumerated tables; equality of two
implementation-derived sets is not by itself proof of specification correctness.

Primary SMPP 3.4 and 5.0 PDF/text copies supplied under
`/tmp/lightweight-smpp-research-qzjlxvbt` were used for focused checks. In
particular, the 5.0 callback display width, number-portability country/companion
rules, network/node encodings, ms_validity alternatives, SAR ignore conditions,
broadcast fields and query-state/time interpretations were checked against their
individual definitions. The sources are [SMPP 3.4 issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf)
and [SMPP 5.0](https://smpp.org/SMPP_v5.pdf). Existing explicitly documented source
ambiguities were preserved. No external implementation supplied expected bytes.

| Principle | Fresh assessment across the assigned files |
| --- | --- |
| S | Protocol values own representation invariants; field cursors own binary translation; command families own profile layouts; context rules own request-dependent checks; helpers own explicit text/receipt/fragment transformations; session policy owns lifecycle decisions. No reviewed type also owns networking, authentication or persistence. Tests and nested fixtures stay within those contracts. |
| O | Real variation is isolated in immutable command/TLV registrations and explicit profile/direction context. Standard catalogues remain finite protocol data. Helper format choices are explicit; no unsupported provider behavior is inferred. A simple value does not need an artificial extension interface. |
| L | Reviewed value equality, owned bytes/collections, raw reserved values, documented exceptions, cursor failure atomicity, single-owner session concurrency and synchronized reassembly behavior. SPI callback/cancellation/deadline obligations were compared with the shared test. P-AUDIT-01 strengthens one ownership assertion without changing its contract. |
| I | `Command`, `CommandCodec`, `TlvValueCodec` and separated frame listener/write observer/handle contracts expose the operations their consumers need. Session capability queries and original-request context do not pretend to provide a universal sender or request owner. |
| D | Production dependencies remain inward to protocol/profile values or the relevant pure package. Session policy owns no codec/socket/window; helpers own no session infrastructure. JUnit/ArchUnit are test dependencies. Architecture rules, their positive import anchors and their negative fixtures were all inspected; runtime dependency/publication audits remain the parent's separate scope. |

No unresolved owned finding remains. The two updated current type reviews below
cover the full test source, including all four test methods, every independent
per-tag fixture, loops and negative checks; the nested record shares its whole-file
hash. The temporary mutation is reviewed separately as discarded evidence.

## Additional contract documents

The following **eight additional documents** were read completely and cross-checked
against the reviewed production source. Their hashes matched current root files
at the end of this cross-check. They are additional review work, not extra files
added to the original 162-file partition. Explicitly dated Step 3/5/7/8 and
foundation evidence statements were treated as history. No current contract
contradiction was found.

| Document | SHA-256 | Cross-check outcome |
| --- | --- | --- |
| [FRAMING.md](../FRAMING.md) | `aba443e2858008d09a35e0eef012a5d5cc34254220f941109fc14378b6ac2cd1` | Header and incremental-frame bounds, position atomicity, ownership and termination match source; Step 3 future-work statements are historical. |
| [FIELDS.md](../FIELDS.md) | `9e23ee63af436d02d779018f2d12999c59dda83593dd4a7812164cc2a3f49ffc` | Field cursor/TLV/preflight/registry contracts and catalogue counts match source; Step 5 scope statements are historical. |
| [COMMANDS.md](../COMMANDS.md) | `9e3948d08b388ad376a53b7a55021de376e45090b4a9712b0b79c2d18309d9bb` | Direct/full codec contracts, raw bind versions, profile-specific failed bind omission and explicit 5.0 control-congestion interpretation match source. |
| [MESSAGES.md](../MESSAGES.md) | `39a041c4a377d5236856e52d2eefb83f778d72e0bf33219972f3c98c9de6208f` | Raw payload/DCS/address/time contracts, exact profile TLV contexts, companion rules and negative response interpretations match source; later helper/exchange links distinguish current layers. |
| [MESSAGE_HELPERS.md](../MESSAGE_HELPERS.md) | `cdd6ce76ae5c3e923d5dc0c89cd7494f6f534de854976e632799214fa40933bf` | Explicit GSM/UCS2 conversion, canonical UDH/SAR, bounded clock-driven reassembly and receipt grammar/raw-value limits match source; provider/transport semantics are not inferred. |
| [PROTOCOL.md](../PROTOCOL.md) | `54a9bf315c2b7ccbc19458a88460b041b153d4ca148ff3bdcc54568b41bc2449` | Command/tag inventory and role matrix match reviewed production; historical independent-peer scope remains dated. 3.4 outbind RX requirement belongs to the endpoint flow, independently confirmed by its reviewer. |
| [TLVS.md](../TLVS.md) | `42e3153a40cc1df866d7ce71ad834057e55382ecf86d36cbd47c622b0e4a8f3e` | All catalogue IDs, occurrence/typed distinction, context counts and structural/compatibility obligations match reviewed source; foundation evidence tables are historical. |
| [SESSIONS.md](../SESSIONS.md) | `43d2e54475e253bd78ab041556dc6be5f4e249544ed22fec39f181295d85f474` | Single-owner lifecycle, explicit negotiation, requirements/correlation responsibilities, crossed unbind and protocol-error sequence rules match source; endpoint outbind constraints compose over this table policy. |

## Test correction and verification evidence

All Gradle calls used the isolated worktree, `--console=plain` and
`-Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=protocol-audit'`. Caches were retained;
there was no `clean`, disabled check or forced rerun. Commands below include those
common flags. Test counts refer to executed or cached results as stated, not a
fresh execution of every inspected test.

| Order | Task/filter and retained log | Actual result |
| --- | --- | --- |
| 1 | `:test --tests kg.aidarbek.smpp.codec.MessageTlvValueCodecTest`; `/tmp/protocol-audit-ownership-baseline.log` | Original test class green: four cases; test executed, three compilation tasks restored from cache. |
| 2 | Same filter; `/tmp/protocol-audit-ownership-corrected-green.log` | Corrected class green: four cases; test compilation and test executed, configuration cache reused. |
| 3 | `:test --tests kg.aidarbek.smpp.codec.MessageTlvValueCodecTest.everyApplicableTagHasIndependentBytesAndMalformedLengthEvidence`; `/tmp/protocol-audit-ownership-mutation-red.log` | One actual behavioral failure after temporarily removing constructor cloning: index 0 expected 4, observed 7 at line 97. |
| 4 | Full class filter after exact production restoration; `/tmp/protocol-audit-ownership-restored-green.log` | Four passing cases restored from valid cache; production compilation also restored from cache. |
| 5 | `spotlessApply`; `/tmp/protocol-audit-format.log` | Passed; formatting ran separately before inventory. |
| 6 | `solidReviewInventory`; `/tmp/protocol-audit-inventory.log` | Passed; 512 repository identities, 184 assigned identities, exactly two identities in the changed test file. |
| 7 | `solidReview reviewTest spotlessCheck`; `/tmp/protocol-audit-final-checks.log` | Passed; current review verification executed for all 512 identities, formatter result up to date, and 60 review-tool cases restored from cache with zero failures/errors/skips. |

The original and final `OctetString.java` hash is
`ef0c60128375fb1f55cd077134138ca784df6751b31e38a146910bb2036fa679`.
The temporary mutated source is retained outside the repository at
`/tmp/protocol-audit-mutant-OctetString.java` with hash `1905456e034a239d658bdede75a79b6fe9d3de4b37651f63fd7a491d8f23e56d`.
No compile/setup error is counted as a TDD red. Full integration builds, artifact
packaging, Maven metadata/signing and external publication credentials are owned
by the parent and must be reported from their own results. This audit does not
claim absolute correctness or authorize publication on its own.

## Current affected types

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/MessageTlvValueCodecTest.java
type: kg.aidarbek.smpp.codec.MessageTlvValueCodecTest
sha256: 37c858f092c083dc92f0dbcc36e91ac1fdff4197ed014165acdc6d2e823c67a6
responsibility: Verifies standard message TLV value structures, profile domains, ownership and reserved-value behavior against independent fixtures.
consumers: Jupiter executes four tests; codec/profile maintainers use failures to detect wire or ownership regressions.
S: pass | Every method addresses the MessageTlvValueCodec value contract, including compound fields and the complete applicable-tag fixture catalogue; it owns no unrelated lifecycle or I/O.
O: pass | New supported value cases extend the local fixture list or focused semantic assertions while stable codec/public extension boundaries remain unchanged.
L: pass | Assertions exercise malformed lengths versus unsupported values, exact output octets and ownership; the corrected assertion observes the mutated input's decoded value, and a discarded aliasing mutation demonstrably fails it.
I: pass | The class implements no custom test-double interface; Jupiter needs only the focused test methods and the private fixture representation serves their independent expected data.
D: pass | Dependencies are Jupiter assertions, JDK immutable/test data and public codec/profile/value contracts; no transport, endpoint, persistence or runtime framework enters the test.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/MessageTlvValueCodecTest.java
type: kg.aidarbek.smpp.codec.MessageTlvValueCodecTest.Fixture
sha256: 37c858f092c083dc92f0dbcc36e91ac1fdff4197ed014165acdc6d2e823c67a6
responsibility: Stores one independently specified tag, expected hexadecimal value and minimum/maximum wire lengths for the enclosing value-codec test.
consumers: The enclosing test reads the four scalar/string accessors to generate positive and boundary assertions.
S: pass | The record is solely expected test data; codec behavior and assertion control flow stay in the enclosing test.
O: pass | Additional standard value cases add record instances without requiring new mutable fixture behavior or unrelated test changes.
L: pass | Primitive integers and immutable String data give ordinary stable record equality/hash and accessor semantics; no mutable array or resource ownership is hidden.
I: not applicable | There is no custom interface obligation; the four record accessors are the complete local consumer requirement.
D: pass | The record depends only on Java scalar/String values and has no production-infrastructure collaborator.
findings: none
```

## Discarded mutation type

This historical block records the deliberate negative fixture, not a current
production violation. The entire temporary type was re-read: it retained the
public copying contract and Object methods while intentionally removing only
the constructor copy. The original implementation was restored before formatting
and inventory.

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/OctetString.java
type: kg.aidarbek.smpp.protocol.OctetString
sha256: 1905456e034a239d658bdede75a79b6fe9d3de4b37651f63fd7a491d8f23e56d
responsibility: Temporarily demonstrates an aliasing defect in the immutable raw-octet value solely to assess the ownership test.
consumers: The corrected value-codec test consumes this discarded mutation; it is not part of the final source or artifact.
S: pass | The temporary type still only represents binary content; the intentional defect changes its ownership behavior, not its responsibility.
O: pass | No supported extension seam or stable neighboring implementation was changed; one constructor statement was mutated and then restored.
L: fail | Intentionally retaining the caller's array violates immutable ownership and can change equality/hash results; the corrected test detects this with expected 4 versus observed 7.
I: pass | Its accessor/length/Object surface remains the same small value contract; no meaningless methods were introduced.
D: pass | Dependencies remain only JDK array and null-check facilities; no framework or external code was introduced.
findings: Intentional aliasing mutation rejected by the actual test and discarded; final production source restored exactly.
```
