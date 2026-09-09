# Review: session state and endpoint permissions

## Scope and contracts

Starting revision: `03959d4`. Work was performed in the isolated
`/tmp/lightweight-smpp-step8` checkout without committing or modifying the root
checkout. Step 8 adds nine production Java files and four test Java files:
14 current type identities including `VersionNegotiation.Outcome`. No temporary,
local or anonymous Java fixture type was created. Initial minimal API declarations
were completed through the behavioral red/green cycles below; no unsupported
production method stub remains.

The [session guide](../SESSIONS.md) owns the compiled API's lifecycle, correlation
boundary and version-policy explanation. Both endpoint roles, both profiles and
all three bind modes share deterministic scenarios. Exact operation tables are
separate from declared local implementation and per-send field requirements.
Session methods do not parse bytes, authenticate, construct network adapters,
invoke application callbacks, allocate windows or timers, or complete futures.
The only internally tracked requests are one bind and at most two directional
unbinds. Ordinary response context comes from an external correlation owner;
the API explicitly does not claim to prove or consume that context.

Prerequisite `protocol.BindMode` was copied byte for byte from the Step 6 owner's
worktree at SHA-256
`7b9a66ec95961d14eb1336a2e1630a41cb106604b62b0e28a3bb3d62bfd8625d`.
Its owner-written review is in the Step 6 report and is excluded from the Step 8
patch. The root-owned `ArchitectureTest` and its
[separate review](0007-session-architecture.md) were also copied only for
verification. They remain separate integration deliverables. Existing
protocol/profile/codec sources were not changed.

Primary expectations came from the supplied complete original specification
copies in `/tmp/lightweight-smpp-research-qzjlxvbt`, specifically SMPP 3.4
§§2.2–2.3, 3.3–3.4, 4.1–4.3, 4.7.1 and 5.1.4, and SMPP 5.0
§§2.3–2.4, 2.8.2, 2.11.2, 4.1 and 4.7.24. The online PDF fetch attempts did
not succeed; the original local PDF/text copies supplied the source content.
Library decisions such as close on failed bind, restrictive missing-advertisement
handling, crossed-unbind completion and draining matched responses follow
[API.md](../API.md) and are distinguished from the specification matrices.

## Review findings and corrections

- Preserved 3.4's bidirectional `data_sm` permission in every bound mode,
  TX-only `replace_sm`, and bound-only ordinary enquiry permission.
- Separated the server's accepted versions, advertised implementation and actual
  received request. The server cannot emit an inconsistent successful bind;
  refusal is atomic and permits a later appropriate negative response.
- Restricted outgoing TLVs and 5.0-only field values after missing advertisement.
  A TLV-free, independently valid `data_sm` remains possible; no mandatory
  payload condition was invented. Incoming raw extensions do not negotiate
  outgoing features.
- Kept duplicate bind and mismatched response rejection atomic. Negative bind
  and ESME version failure explicitly close. Version results retain unknown
  advertisements without enabling a profile.
- Kept equal sequence values in opposite namespaces distinct. Crossed unbinds
  require both successful replies; a completed direction cannot be replaced.
- Root review identified an invalid-header edge. The initial error-reply checker
  could echo an illegal sequence and could refuse a legal zero-sequence nack.
  Regression tests now require exact echo for usable sequences and only a
  negative generic nack with sequence zero for unusable sequences. A response
  is never interpreted as an offending request. Completely undecodable command
  headers cannot supply this API's request context.
- Final API review added explicit outgoing `SendRequirements` to unbind replies
  before consuming a lifecycle identity. Both ordinary `unbind_resp` and
  negative `generic_nack` paths have contract coverage.
- Removed duplicated bound-state selection while tests were green and documented
  thread confinement, immutable snapshots, exception atomicity and final-response
  write ordering. The state machine owns no resource and makes no false
  `AutoCloseable` or transport-completion promise.

All complete affected types, their inherited contracts and consumers were reviewed.
The following blocks identify the final formatted files. Remaining findings: none.

## Type: kg.aidarbek.smpp.session.EndpointRole

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/EndpointRole.java
type: kg.aidarbek.smpp.session.EndpointRole
sha256: 3365636fbccf9e99d9f04bdcb63adbc882ff33a2c9c1b8c83c1af1ca2dc88942
responsibility: Identify the SMPP ESME or message-center role independently of TCP connection direction.
consumers: PduDirection resolves origin roles; SessionPermissions selects exact matrix rows; SessionStateMachine factories and parameterized tests keep role identity explicit.
S: pass | The two immutable constants represent one protocol distinction and carry no networking, authentication or bind-mode policy.
O: pass | Role identity is a closed protocol vocabulary; command/profile variation is handled by SessionPermissions and does not add behavior to this enum.
L: pass | Inherits Enum identity, name and immutable singleton semantics without overrides; both roles run the same lifecycle and permission tests, including wrong-origin bind rejection. No resource or concurrency ownership is introduced.
I: pass | Consumers need only the role identity; no sender methods or mandatory handlers are attached to either role.
D: pass | Depends only on JDK enum facilities; higher session policy consumes the value, never the reverse.
findings: none
```

## Type: kg.aidarbek.smpp.session.PduDirection

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/PduDirection.java
type: kg.aidarbek.smpp.session.PduDirection
sha256: d69325179f2acb0d4e9ccacb6caf3e2ddd1f2ce6737831827bcfb011e87f31b4
responsibility: Represent local-relative wire direction and resolve the originating SMPP role.
consumers: SessionStateMachine lifecycle and response checks, ResponseContext, and tests for simultaneous requests and crossed unbinds.
S: pass | opposite and origin express the same direction coordinate system; no sequence allocation, protocol permission or I/O is performed.
O: pass | The two wire directions are fixed; endpoint-role and operation variation is supplied by callers without introducing command branches here.
L: pass | Retains Enum identity and immutable behavior; null origin roles fail explicitly. Shared tests exercise both request/response orders, equal opposite-direction sequences and wrong-direction rejection. No concurrent mutable state exists.
I: pass | Consumers use only direction identity, opposite and origin; no pending-request, transport or application interface is imposed.
D: pass | Depends only on EndpointRole and JDK Objects; it has no codec or infrastructure dependency.
findings: none
```

## Type: kg.aidarbek.smpp.session.ResponseContext

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/ResponseContext.java
type: kg.aidarbek.smpp.session.ResponseContext
sha256: d84822c743d64d5cea4920264df5bda73a5d06959ce2822eeebe02cd8a6c4149
responsibility: Carry immutable original-header and direction context across the correlation or protocol-error boundary.
consumers: SessionStateMachine.responsePermission and protocolErrorPermission; the future request owner must establish generation, outstanding status and uniqueness externally.
S: pass | Stores only original context and validates non-null ownership; it neither tracks requests nor decides protocol errors, terminal outcomes or timing.
O: pass | Correlation implementations can supply the same narrow value without changing state policy; future pending-window variation stays outside this record.
L: pass | Both components are immutable; generated equality/hashCode and raw unsigned header preservation are tested. Invalid raw values remain available for error handling. Documentation explicitly denies token, freshness or consumption semantics, and does not permit fabricating an undecodable command identity.
I: pass | Response policy needs only request header and direction, so callers do not implement a future, transport or application callback just to provide context.
D: pass | Depends inward on protocol.PduHeader, PduDirection and JDK Objects; no concrete tracker, codec or socket is referenced.
findings: none
```

## Type: kg.aidarbek.smpp.session.SendRequirements

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/SendRequirements.java
type: kg.aidarbek.smpp.session.SendRequirements
sha256: 98530156dd2c432012870e937b77daf761cd1d825d0bfcb1662912f31b55c878
responsibility: Describe the validated outgoing field baseline and whether the operation uses optional parameters.
consumers: VersionNegotiation.permits and all ordinary request/response and unbind-send permission checks; command validation supplies actual field requirements.
S: pass | The record captures feature requirements only and explicitly leaves field inspection and raw incoming TLV semantics to the codec layer.
O: pass | Supported 3.4/5.0 field variation is supplied as immutable data; adding a command requiring an existing feature combination changes no session method or this record.
L: pass | Non-null SmppVersion and a boolean are deeply immutable; tests establish ordinary record equality/hashCode and null rejection. COMMON is a safe immutable value, and tests show it cannot enable TLVs or 5.0-only fields under restricted negotiation.
I: pass | Consumers receive the two facts needed for this permission boundary without taking a full message, encoder or transport configuration.
D: pass | Depends on the profile version enum and JDK Objects only; no session implementation, codec or infrastructure is constructed.
findings: none
```

## Type: kg.aidarbek.smpp.session.SessionDecision

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/SessionDecision.java
type: kg.aidarbek.smpp.session.SessionDecision
sha256: bfc9a0026b4648b554096309b0022c7107c30be416eb5159c43d430d0c47d302
responsibility: Distinguish deterministic policy acceptance, atomic refusal and the two terminal bind refusals.
consumers: SessionStateMachine callers decide the next write, protocol-error response or close action; tests assert decisions together with resulting state.
S: pass | Names policy results only, without choosing wire statuses, storing payloads or triggering external effects.
O: pass | Operation-specific statuses stay in protocol headers and external response construction; new codec implementations do not require result-enum changes.
L: pass | Preserves immutable Enum identity and explicitly documents the terminal BIND_REJECTED and VERSION_REJECTED exceptions to refusal atomicity. Shared bind, capability and response tests verify those distinctions; unknown numeric statuses are not coerced into enum values.
I: pass | Callers consume a result identity and retain their own header/context; no broad result interface requires unsupported timing, transmission or authentication methods.
D: pass | Depends only on JDK enum machinery; it imports no transport, codec or application type.
findings: none
```

## Type: kg.aidarbek.smpp.session.SessionPermissions

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/SessionPermissions.java
type: kg.aidarbek.smpp.session.SessionPermissions
sha256: ca97d2856aa0377e9a8fbac9c29ea926401e8359ed1b469fc3fd577339d492dc
responsibility: Apply the exact SMPP 3.4 and 5.0 request-origin and session-state operation matrices independently of implementation claims.
consumers: SessionStateMachine admission/capability checks and callers querying catalogue policy; SessionPermissionsTest supplies independently listed role/state expectations.
S: pass | The single query reads explicit profile, origin, state and numeric command identity; parsing, capabilities, lifecycle tracking and error-response procedures are outside it.
O: pass | Protocol variation is isolated in this table and ProtocolProfile membership; additional declared codecs or application services do not modify matrix policy. A new specification rule belongs here rather than in socket or callback loops.
L: pass | A final static utility has no custom subtype obligations; applicable contracts still cover null rejection, unsigned exact membership before narrowing, response/unknown rejection, deterministic thread-safe queries and the documented 3.4/5.0 differences, all exercised across the complete request inventory.
I: pass | The query accepts only policy context; callers need no broad session, handler or resource interface and catalogue permission is explicitly distinct from usable capability.
D: pass | Depends inward on ProtocolProfile/SmppVersion and local identity enums plus JDK Objects. Full-package ArchUnit checks reject codec and infrastructure edges from the session package.
findings: none
```

## Type: kg.aidarbek.smpp.session.SessionState

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/SessionState.java
type: kg.aidarbek.smpp.session.SessionState
sha256: ae1e7bce77e9a4e4e8069b2cb2bb5203a91ee2b0941045042273e7f7b96fd34f
responsibility: Name connection, pre-bind, bound-mode, unbinding and terminal lifecycle states without performing transitions.
consumers: SessionStateMachine returns current state; SessionPermissions interprets relevant operation contexts; architecture and shared lifecycle tests use the real production enum.
S: pass | Contains only documented state identities, including the 5.0 OUTBOUND state and library BINDING/UNBINDING overlays; there are no network or transition side effects.
O: pass | Lifecycle vocabulary is explicit; supported commands vary in SessionPermissions and new codecs do not modify this enum.
L: pass | Preserves immutable Enum semantics and no custom resource contract; parameterized lifecycle tests establish all bound states, outbind behavior, closed permanence and rejection atomicity while request-table tests enumerate every value.
I: pass | Consumers receive a state identity without inheriting sender operations, request storage or transport lifecycle methods.
D: pass | Depends only on JDK enum facilities. The separate architecture fault probes used temporary external references only in the root-owned isolated copy and removed them before final coverage.
findings: none
```

## Type: kg.aidarbek.smpp.session.SessionStateMachine

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/SessionStateMachine.java
type: kg.aidarbek.smpp.session.SessionStateMachine
sha256: 7f30bd4670b84df54fbd28bc33b05181a312777adb1af69429752981dbafce84
responsibility: Own deterministic connection/bind/unbind state and combine specification, implementation and outgoing-feature permission without owning infrastructure or general requests.
consumers: Future endpoint composition supplies validated headers/events and honest implementation declarations; SessionPermissions and VersionNegotiation are focused collaborators; four session test suites exercise public contracts.
S: pass | Lifecycle identity ownership and permission rechecking are cohesive; exact matrix rows and version decisions are delegated. The machine does not parse, authenticate, store messages, schedule callbacks, manage sockets or implement Step 9 correlation/deadlines/windows.
O: pass | Existing command implementations are declared through copied IDs while stable lifecycle transitions remain unchanged; version/role variation goes through the policy collaborators and explicit endpoint factories. Only commands with lifecycle effects have special transitions, so later message codecs do not require new state branches.
L: pass | No custom supertype or resource contract is claimed. Callers serialize mutation; immutable inputs/outputs are retained safely and sets are copied. Tests cover null/range failures, rejection atomicity, duplicate/mismatched binds, explicit terminal failures, same-sequence crossed unbinds, repeated close, no reopening, retained version results, capability rechecks, draining matched replies and zero-sequence error rules. The documented correlation context remains caller-owned and cannot be treated as a consumed token; final response acceptance does not certify a write.
I: pass | Focused lifecycle methods and pure permission queries serve actual state/capability consumers. There is no universal sender or unsupported method stub. Required SendRequirements covers ordinary and unbind outputs; bind metadata follows its separate negotiation contract. The machine forces no application callbacks or transport implementation on its caller.
D: pass | Dependencies point only to session policy/value types, protocol.BindMode/PduHeader, profiles and JDK values. Infrastructure is absent, so no unused socket or executor port is invented. The final complete production package passes the root-owned nonempty architecture rules.
findings: none
```

## Type: kg.aidarbek.smpp.session.VersionNegotiation

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/VersionNegotiation.java
type: kg.aidarbek.smpp.session.VersionNegotiation
sha256: 48aa331dbe556eb38091f8aa01fd0948d3ed93f4fd469b53b07a4469910074cf
responsibility: Evaluate explicit client/server version policy and retain requested, advertised, effective and field-capability decisions separately.
consumers: SessionStateMachine bind completion and per-send permission; endpoint configuration may preflight the same message-center accepted-version policy.
S: pass | Only version compatibility and its immutable result drive the type; credential decisions, codecs, command origins and state transitions remain outside it.
O: pass | Client strict/missing advertisement policy and server accepted sets are explicit inputs. Changes to version policy are isolated from lifecycle correlation and command dispatch; no speculative pluggable strategy or implicit provider exception is introduced.
L: pass | The private constructor guarantees coherent success/failure profile combinations. Results are immutable and explicitly have Object identity equality; raw unknown octets and absence are preserved. Tests cover every API-table outcome, version bounds/nulls, no implicit upgrade/downgrade, immutable acceptance evaluation, nonempty consistent server configuration and outgoing TLV/5.0-field rejection after missing advertisement.
I: pass | Factories match the two real endpoint policies and accessors expose only result facts plus the field requirement predicate; callers need no parser, authenticator, session callback or socket method.
D: pass | Depends on profile values, SendRequirements and JDK Optional/Set facilities only. It constructs an immutable profile descriptor, not infrastructure, and has no reverse dependency on SessionStateMachine.
findings: none
```

## Type: kg.aidarbek.smpp.session.VersionNegotiation.Outcome

```solid-review
source: src/main/java/kg/aidarbek/smpp/session/VersionNegotiation.java
type: kg.aidarbek.smpp.session.VersionNegotiation.Outcome
sha256: 48aa331dbe556eb38091f8aa01fd0948d3ed93f4fd469b53b07a4469910074cf
responsibility: Name the successful, restricted and explicit failed outcomes of the version-policy collaborator.
consumers: VersionNegotiation constructs invariant-preserving results; session callers and VersionNegotiationTest distinguish supported, missing, unknown, old and unaccepted versions.
S: pass | Enumerates only version-policy classifications and contains no mutation, wire status mapping, bind-mode rules or transport behavior.
O: pass | The explicit policy vocabulary is closed for the implemented versions; peer octets remain raw result data instead of requiring a new enum constant for every unknown value.
L: pass | Retains Enum identity and immutability without overrides. Tests pair every outcome with its promised effective-profile presence and requirement permissions, including all failure outcomes returning no usable profile.
I: pass | Consumers need just an outcome identity; neither peers nor implementations are forced to supply unrelated lifecycle or error-detail methods.
D: pass | Depends only on JDK enum machinery and is scoped with its cohesive version-policy owner, with no infrastructure dependency.
findings: none
```

## Type: kg.aidarbek.smpp.session.SessionPermissionsTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/session/SessionPermissionsTest.java
type: kg.aidarbek.smpp.session.SessionPermissionsTest
sha256: 5edac075deadb45ba91ef885d0181d384e73da442d2fd22bb216938244fc9533
responsibility: Verify the complete specification request matrix independently of state mutation, implementation declarations and codecs.
consumers: JUnit Jupiter executes seven cases; literal command groups and allowed state sets are derived from the two original operation tables.
S: pass | Tests only request-matrix rules, their exact version differences, unknown/response identities and null context; no network or unrelated value fixture is owned.
O: pass | New specification cases extend this table-focused suite without changing lifecycle fixtures or production wiring; expected sets remain independent of the production result.
L: pass | The final test class has no custom subtype contract. Jupiter cases are deterministic and enumerate both profiles, both origins and every state; exact unsigned probes prevent truncation bugs. Fixtures are local immutable sets/primitive arrays, with no shared mutable resources or counterfeit interface implementation.
I: pass | Uses only the public pure permission query and explicit profile/role/state values needed by its observations.
D: pass | Depends on Jupiter, production policy/value APIs and JDK collections only; no production class depends on tests or reads a private implementation table.
findings: none
```

## Type: kg.aidarbek.smpp.session.SessionResponsePermissionTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/session/SessionResponsePermissionTest.java
type: kg.aidarbek.smpp.session.SessionResponsePermissionTest
sha256: 146e1167bd4a39c44e44e3f2c9bd4986318031d412dbbf4790befc3ab75350e7
responsibility: Verify the response authorization boundary, protocol-error exceptions and atomic outgoing unbind feature checks.
consumers: JUnit Jupiter runs role/profile cases against public SessionStateMachine methods and explicit ResponseContext values.
S: pass | Every scenario observes response pairing, direction, caller-supplied correlation, error-reply legality or reply feature permission; the helper builds only the bound/open lifecycle needed for those observations.
O: pass | Additional response edge cases fit focused public-contract scenarios; ordinary tracker implementations can change independently because this suite does not fabricate a pending map or assert private storage.
L: pass | No custom test superclass or fake production interface exists. Deterministic tests use equal opposite-direction sequences, absent context for late/unsolicited replies, invalid original/response identities, zero/high-bit sequence boundaries, raw record equality, null atomicity and corrected completion after refusal. Both generic_nack and unbind_resp field requirements are covered, and no sleep or resource is used.
I: pass | Uses the narrow response/lifecycle APIs and header/context values; it does not require an application handler or future simply to exercise permission.
D: pass | Depends on Jupiter, public session/protocol/profile contracts and finite JDK collections/streams. No codec bytes, transport, static private-table reflection or external service supplies expectations.
findings: none
```

## Type: kg.aidarbek.smpp.session.SessionStateMachineTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/session/SessionStateMachineTest.java
type: kg.aidarbek.smpp.session.SessionStateMachineTest
sha256: 6a9b1699328b3ab68e04648bde5b7b83cbb835e06ce3614b0017b73d5eec5ff3
responsibility: Verify shared lifecycle and capability contracts across both roles, both profiles and all three bind modes.
consumers: JUnit Jupiter executes 114 deterministic cases; local helper methods supply explicit implementation declarations and validated protocol headers.
S: pass | Scenarios focus on connection/binding, current-state capability, outbind notification context, unbinding and close; none owns a network peer, authentication service, pending-window implementation or unrelated fixture lifecycle.
O: pass | The variants source applies the same public-contract assertions to each role/profile/mode combination. Additional scenario methods can test new lifecycle behavior without subclassing the state machine or weakening existing expectations.
L: pass | The final test class has no custom inherited contract. Tests preserve local ownership, immutable snapshots, null/range rejection, failed-event atomicity, duplicate bind identity, failed bind closure, same-sequence crossed unbinds in all request/reply orders and permanent close. Simultaneous traffic is modeled as interleaved events under the documented single-owner contract, without claiming thread safety or owning resources.
I: pass | Helpers consume only the public methods required by each scenario; no universal fake session or unsupported sender methods are added for test convenience.
D: pass | Depends on Jupiter and public protocol/profile/session values with finite JDK sets and streams. Expected mode/permission sets are explicit specification expectations, not values copied from the implementation's permission query.
findings: none
```

## Type: kg.aidarbek.smpp.session.VersionNegotiationTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/session/VersionNegotiationTest.java
type: kg.aidarbek.smpp.session.VersionNegotiationTest
sha256: fa209dffe0bd164a91580abb918f2ba8b9d5540b7264e42596d6db32faf60cc4
responsibility: Verify the documented client version table, explicit message-center acceptance policy and immutable outgoing field requirements.
consumers: JUnit Jupiter executes 11 cases against VersionNegotiation and SendRequirements through their public APIs.
S: pass | Tests version metadata, effective-profile confidence, configuration bounds and requirement predicates; no bind lifecycle or credential fixture is needed.
O: pass | Table-driven profile cases and explicit raw advertisement examples isolate policy variation; new codec commands or request-tracking implementations require no test-harness change.
L: pass | No custom supertype or fake collaborator is introduced. Cases check raw unknown/missing octets, every policy outcome, no upgrade/downgrade, null and octet bounds, accepted-set mutation, inconsistent server policy and record equality/hashCode. All values are local and no timing or resource contract is assumed.
I: pass | Uses only version evaluation, result accessors and requirement checks, leaving state and message-handler APIs out of the fixture.
D: pass | Depends on Jupiter, the version/requirements boundary and JDK immutable values/sets; expectations come from API.md and the reviewed compatibility policy rather than codec output.
findings: none
```

## TDD and verification

Every Gradle call used normal build/configuration caching and the required literal
suffix `--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step8'`.
No clean, dependency refresh, disabled warning, skipped test, unconditional
failure or unrelated compilation failure supplied red evidence. Test runs below
executed their test task unless explicitly stated otherwise. The first invocation
redirected output and then displayed the saved log, so its shell wrapper exited
with the display command's status; the saved Gradle output explicitly records
`BUILD FAILED` and the two relevant assertion failures.

The focused command prefix was `./gradlew test --tests 'kg.aidarbek.smpp.session.CLASS'`.
The table gives the exact class selector (`*` selects the complete session suite),
log stem, relevant observed red failure and passing green scope. Expand the
selector and common suffix to obtain each literal command recorded in its log.

| Cycle / logs under `/tmp/` | Red selector and relevant failure | Green selector / result |
| --- | --- | --- |
| `step8-01-{red,green}.log` | `SessionPermissionsTest`: valid TX submission refused; 2/2 failed | Same; 2 passed |
| `step8-02-{red,green}.log` | `SessionPermissionsTest`: required data permission absent; 2/4 failed | Same; 4 passed |
| `step8-03-{red,green}.log` | `SessionPermissionsTest`: OPEN bind permission absent; 2/7 failed | Same; 7 passed |
| `step8-04-{red,green}.log` | `VersionNegotiationTest`: wrong effective 5.0 profile and unrestricted 5.0 fields in 3.4; 2/2 failed | Same; 2 passed |
| `step8-05-{red,green}.log` | `VersionNegotiationTest`: missing/unknown/old advertisements accepted and null input ignored; 6/8 failed | Same; 8 passed |
| `step8-06-{red,green}.log` | `VersionNegotiationTest`: server acceptance/advertisement conflated and invalid policy accepted; 3/11 failed | `*`; 18 passed |
| `step8-07-{red,green}.log` | `SessionStateMachineTest`: connection remained CONNECTING; 12/12 failed | Same; 12 passed |
| `step8-08-{red,green}.log` | `SessionStateMachineTest`: negative/version-failed bind remained accepted, inconsistent server success accepted, close ineffective; 26/50 failed | Same; 50 passed |
| `step8-09-{red,green}.log` | `SessionStateMachineTest`: capabilities exposed unbound/forbidden/unimplemented commands and ignored outgoing fields; 14/64 failed | Same; 64 passed |
| `step8-10-{red,green}.log` | `SessionStateMachineTest`: unsolicited unbind response accepted; 12/76 failed | Same; 76 passed |
| `step8-11-{red,green}.log` | `SessionStateMachineTest`: crossing request refused and negative nack not completed; 24/100 failed | Same; 100 passed |
| `step8-12-{red,green}.log` | `SessionResponsePermissionTest`: absent ordinary correlation accepted; 4/4 failed | Same; 4 passed |
| `step8-13-{red,green}.log` | `SessionResponsePermissionTest`: error reply mismatches accepted, drain refused, lifecycle/notification responses misclassified; 12/17 failed | `*`; 135 passed |
| `step8-14-{red,green}.log` | `SessionStateMachineTest`: declarations unvalidated, incoming extensions incorrectly gated, 5.0 outbind remained OPEN; 8/114 failed | `*`; 149 passed |
| `step8-15-{red,green}.log` | `SessionResponsePermissionTest`: unknown request zero misclassified and incoming raw response extensions gated; 2/20 failed | `*`; 152 passed |
| `step8-18-red.log`, `step8-18b-red.log`, `step8-18-green.log` | `SessionResponsePermissionTest`: paired sequence zero accepted, then the same assertion set ordered high-bit first exposed refused nack zero; each run 4/24 failed | `*`; 156 passed |
| `step8-20-{red,green}.log` | `SessionResponsePermissionTest`: unsupported outgoing unbind extension consumed lifecycle; 1/25 failed | `*`; 157 passed |

Cycle 8's duplicate-bind/unexpected-response scenarios already passed the behavior
introduced with the initial bind implementation; those were recorded as green
coverage and were not claimed as additional red failures. The null-context case
in cycle 3 and 3.4 outbind cases in cycle 14 likewise covered already-correct
behavior. Minimal declarations made new APIs runnable before red; the failure
claims above concern observable assertions, not missing symbols.

After cycle 15, `./gradlew spotlessApply` passed (`step8-16-format.log`).
`./gradlew test javadoc solidReviewInventory` then passed with 248 executed
library/architecture cases and no warnings (`step8-17-refactor.log`). This included
the whole formatted session package and the root-owned seven architecture cases;
review-tool compilation was restored from its matching cache entry. That run was
followed by the review-driven sequence and unbind requirement corrections above.

Subsequent formatting ran in `step8-19-format.log`, `step8-21-format.log` and
`step8-24-format.log`. `step8-22-verification.log` records a passing full
test/Javadoc/inventory run after the unbind API correction. Then
`step8-23-characterization.log` added the negative `generic_nack` case to the
same unbind field-requirement test without changing production behavior: all
26 response-suite cases passed. That additional existing-behavior coverage did
not manufacture a red run. The last formatting preceded the final hashes here;
`step8-25-inventory.log` recorded the resulting parser inventory.

## Final verification and inventory reconciliation

`./gradlew check javadoc --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step8'`
passed (`/tmp/step8-26-check.log`). It executed the full library test task with
254 cases across 17 suites, including 158 new session cases and seven actual
architecture cases. There were zero failures, errors or skipped cases. The
separate review-tool test task restored a matching cache result containing 60
passing cases across four suites; those 60 cases did not execute again in this
run. Total represented test results: 314.

Formatting verification and `solidReview` executed successfully. Production
compilation and Javadoc were up to date from the preceding successful current-
source runs; no warning was emitted. Review-tool test compilation was restored
from cache. Normal caching was preserved throughout, and this final invocation
stored its configuration-cache entry.

The parser inventory and successful coverage output each contain 58 current
type identities: the baseline's 43 (including the updated root-owned architecture
test), the copied Step 6 BindMode prerequisite and the 14 types reviewed here.
All 14 source hashes were recomputed against the final files before insertion
into the review. The final inventory contains no additional local, nested or
anonymous session fixture type beyond `VersionNegotiation.Outcome`.

The architecture rules selected the complete real session package and enforce
only session/profile/protocol/JDK dependencies, infrastructure exclusion and
absence of package cycles. The root-owned report records the earlier actual
codec-reference and executor-reference red probes and their removal. Those
probes were never inserted into this implementation worktree.

Document checks verified the two Step 8 documents' local links, final newlines,
titles, trailing whitespace and balanced fences; `git diff --check` passed.
The deliverable contains only the nine session production files, four session
test files, session guide and this report. Copied prerequisite sources/reviews
and the root-owned architecture files are excluded. Full Step 6/7 codec suites
and combined root integration remain the root owner's verification scope;
this isolated result makes no independent-peer or network-runtime claim.

## Root integration

Applied the Step 8-only patch after `c15eeeb` (`Add message codecs`) together
with the root-owned ArchitectureTest and its separate review. The final delta
adds the 14 session identities above and updates that one existing architecture
test. No existing production Java source changed. The overview, roadmap, API,
protocol/TLV inventory and test/SOLID policy status now record Steps 1–8 complete
and Step 9 next. State-policy evidence stays distinct from full endpoint evidence.

The session guide now makes the reviewed composition requirements explicit:
original-request data direction, negotiated outgoing profile, honest field/TLV
requirements, separate generic-nack routing and raw invalid-header error context.
These are documentation of existing contracts, with no request-tracker or
transport implementation added. Root review covered the final sequence fallback,
unbind field preconditions, lifecycle atomicity, version decisions and consumers.

`./gradlew build solidReviewInventory --console=plain` passed in the root
checkout (`/tmp/lightweight-smpp-step8-integration-build.log`). It freshly
executed **341 library/architecture cases across 31 classes**, including the
complete bind/control/message suites, 158 session cases and all seven architecture
cases against the complete production graph. The unchanged **60 review-tool
cases across four classes** were UP-TO-DATE; they are reused evidence. No case
failed, errored or skipped. Production/test compilation, Javadoc, formatting
verification, all three archives, inventory and coverage succeeded without
warnings. The configuration cache was reused; 11 tasks executed and three were
up to date. No clean, forced rerun or dependency refresh was needed.

The inventory and coverage contain **113 matching current type identities**.
Independent SHA-256 checks matched each current Java source. Binary/source JAR
contents equal the production output/source sets; review tooling stays excluded.
All three JARs contain byte-exact project LICENSE and NOTICE files.
`./gradlew dependencies --configuration runtimeClasspath --console=plain` reports
**No dependencies** (`/tmp/lightweight-smpp-final-runtime-dependencies.log`).

Markdown validation covered 28 documents, 212 local links, titles, final newlines,
fences and footnotes; whitespace validation passed. The matching build invocation
after this narrative verifies the final report bytes before committing. The
completed result is deterministic codecs and session policies; live endpoints,
general correlation/deadlines, simulators and independent peers remain later work.
