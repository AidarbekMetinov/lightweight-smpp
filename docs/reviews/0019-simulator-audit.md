# Simulator Maven-readiness audit

Fresh whole-file review of all **94 tracked simulator paths** frozen at
`094a84f595b61779904c0190ef15f6430561cd22`, completed on 10 September 2026.
The assigned manifest is the `simulator` partition in
`build/runs/maven-readiness-20260910/file-partitions.json`. Every path and baseline
SHA-256 is recorded below; all 94 hashes were checked against the retained
manifest and unchanged root files. No assigned file remains uninspected.

The partition contains 46 production Java files, 42 Java test/fixture files,
two Python tools, two Python test files, one Gradle configuration and one PKCS12
fixture: 587447 bytes. Every text file was read in full for this audit; earlier
reviews were context, not substitutes for inspection. The binary inspection is
qualified below. Baseline source contains 142 simulator Java identities.

No new simulator-runtime defect or simulator-specific Maven packaging blocker
was established by this inspection. Three reproducible **test-fixture** defects
were corrected with bounded regressions; production Java, runtime Python,
dependencies, workload plans and measured artifacts are unchanged. Publication
metadata/signing/Central validation belongs to the root audit and is not certified
by this report. This is accounted review coverage and supporting test evidence,
not an absolute correctness, interoperability, leak-freedom or capacity guarantee.

## Confirmed findings and corrections

1. **Full-run test owner mismatch.** `LoadRunTest` started a complete
   `SimulatorRun` on a virtual thread. Initial/baseline/final sampler calls are
   synchronous and retain their real intrinsic guard across observations; unlike
   the supported platform-main CLI, that fixture could consume the only Java 21
   carrier while waiting for a collaborator whose virtual releaser needed it.
   Two fresh one-carrier probes reproduced timeout for the real resource and
   pressure baseline guards. `SimulatorTestOwner` now starts a platform thread
   for both the actual test run and those probes. Existing dedicated periodic
   sampling-worker behavior is unchanged. This is a test invocation correction,
   not a claim that the platform CLI had this defect.
2. **Second process cleanup skipped on interruption.** The receipt process pair
   and both traffic process pair paths cleaned up client then server sequentially.
   An interrupted first wait skipped the live server. Two actual finite child
   JVMs proved the failure for both fixtures. Nested `finally` now attempts both
   owners, retaining the existing receipt graceful/forced policy and traffic
   forced policy; the latter also asserts successful bounded reaping. The shared
   existing `HelperSimulatorTest.WaitingChild` fixture is unchanged.
3. **Malformed-header test could spin on EOF.** The Python terminal-fault test
   concatenated `recv()` results until 16 bytes without checking an empty result.
   An early peer close returns empty bytes immediately, so a socket timeout does
   not bound this loop. A real socketpair in a separately bounded child process
   reproduced the spin without hanging the test runner. `read_malformed_header`
   now raises `EOFError` on early closure; the original exact malformed header
   expectation is retained. The raw peer runtime was already EOF-aware.
4. **Current documentation contradicted implementation.** The simulator overview
   retained old later-work claims for implemented receipt/raw-fault/churn/load
   scenarios and described a completion-count ratio rather than the actual rate
   gate. The raw-peer guide incorrectly excluded RX-originated 3.4 data. The load
   guide blurred periodic worker observations with synchronous CLI boundaries
   and initial connection spacing. These current descriptions are corrected;
   historical measurements and candidate identities are preserved.

No unresolved finding is hidden by a passing test. The audit stops at these
verified fixture/description corrections; it introduces no speculative runtime
refactor or new long-load campaign.

## Contract and test-quality assessment

The finite scheduler retains original arrival identities, counts skips rather
than building a catch-up backlog, and reports partial/aborted phases. Fixed
concurrency reserves per-session tool slots until terminal observation; it does
not replay refusals or infer atomic admission from sampled pressure. Request
correlation/deadlines remain public library capabilities. Receiver decisions,
actual peer outcomes, cleanup and unavailable physical wire counters remain
separate. Histograms use bounded storage and count-weighted aggregation with
explicit overflow/populations; source and binary fingerprints do not falsely
prove their build relationship.

The message adapters are cohesive operation boundaries. They place payload once,
check role/profile eligibility, and keep raw, explicit text, fixed SAR and receipt
semantics distinct. Raw validation checks shape, while helper fixtures compare
content. SAR repeats one bounded reference per generation and reports incomplete
assemblies; it is not a persistent message service. Receipt correlation uses
returned opaque IDs with count/window/deadline bounds and reports early, duplicate,
unmatched and missing events without fabricating delivery success.

Tests use independent byte literals, controlled clocks, legal blocking
collaborators and real sockets/JVMs where ownership or wire behavior matters.
Existing negative architecture fixtures reject a second request engine and raw
transport ownership. Time-bounded process checks are functional evidence, not
machine-independent latency/TPS measurements. The installed raw-peer matrix is
explicitly opt-in and was not rerun or counted as passing in this audit.

The Gradle application keeps HDR tooling outside the library runtime artifact;
JUnit/ArchUnit and TLS resources are test-scoped. Archive order/timestamps and
LICENSE/NOTICE inclusion are configured. No Cloudhopper implementation or
dependency was introduced. A standalone simulator distribution is separate from
the root Maven publication being prepared by the coordinating audit.

## Binary fixture inspection

`simulator/src/test/resources/lifecycle/development-only.p12` is a 2644-byte
explicit development fixture, SHA-256
`16d5cc34fde38f890586e82c58218604ce9f53652e2890aaf7ed1393bbc6068b`.
Read-only OpenSSL inspection using its documented development password succeeded:
one certificate bag and one encrypted/shrouded key bag, SHA256 MAC with 2048
iterations, PBES2/PBKDF2/AES256CBC with HMACSHA256 and 2048 iterations. No private
key bytes were exported. Public certificate metadata: self-issued CN localhost,
SAN DNS localhost / IP 127.0.0.1, validity 9 September 2026 to 16 August 2126, certificate
SHA-256 `05212bd96b9dac866fcc4f381cce6a7353ee6f02622db3d9ba1687a814b46a7f`.

This establishes file identity, parseability, fixture role and public certificate
metadata. It is not a byte-by-byte cryptographic proof or trust recommendation.
Its test-resource placement excludes it from the application/library runtime
source sets. Actual TLS fixture/endpoint tests remain separate behavior evidence.

## Actual red, green and final checks

All Gradle calls ran sequentially in `/tmp/lightweight-smpp-simulator-audit` at
the frozen base, with `--console=plain` and
`-Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=simulator-audit'`. No root Gradle
invocation, cache removal, forced GC or new load measurement was performed.

| Log | Actual command task/selection | Result |
| --- | --- | --- |
| `/tmp/maven-simulator-01-baseline-green.log` | `:simulator:test --tests kg.aidarbek.simulator.LoadRunTest --tests kg.aidarbek.simulator.SamplingProgressTest --tests kg.aidarbek.simulator.SamplingLoopTest` | Existing focused baseline passed; test task executed, compilation restored from cache |
| `/tmp/maven-simulator-02-baseline-owner-red.log` | `:simulator:test --tests 'kg.aidarbek.simulator.SamplingProgressTest.synchronous*'` | Both baseline probes failed with the expected virtual-releaser progress AssertionError caused by a bounded timeout |
| `/tmp/maven-simulator-03-baseline-owner-green.log` | Same focused selection as01 after the platform-owner fix | Passed; test task executed |
| `/tmp/maven-simulator-04-dual-cleanup-red.log` | `:simulator:test --tests kg.aidarbek.simulator.SimulatorProcessCleanupTest` | Both receipt/traffic cases failed because the actual second child remained alive after interrupted first cleanup |
| `/tmp/maven-simulator-05-dual-cleanup-green.log` | `:simulator:test --tests kg.aidarbek.simulator.SimulatorProcessCleanupTest --tests kg.aidarbek.simulator.ReceiptScenarioProcessTest --tests kg.aidarbek.simulator.SimulatorProcessTest` | Passed in76s; test task executed with unchanged existing process assertions |
| `/tmp/maven-simulator-06-format.log` | `spotlessApply` separately before Java hashes/inventory | Passed |
| `/tmp/maven-simulator-07-malformed-eof-red.log` | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s simulator/scripts/tests -p test_fault_peer.py -k truncated_malformed_header -v` | One relevant failure: bounded child timed out spinning after real early EOF; parent killed/reaped child |
| `/tmp/maven-simulator-08-malformed-eof-green.log` | Same focused Python command after EOF check | Passed |
| `/tmp/maven-simulator-09-final-validation.log` | `:simulator:test :simulator:javadoc solidReviewInventory` |141 Java cases in42 suites passed, zero failures/errors/skips; simulator test task executed. Javadoc restored FROM-CACHE, unchanged production. Inventory executed:144 simulator / 514 total identities |
| `/tmp/maven-simulator-10-python-final.log` | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s simulator/scripts/tests -v` |38 discovered, 36 passed, 2 explicit optional installed-target skips,4.103 seconds |

An attempted `:simulator:spotlessCheck solidReview` invocation in
`/tmp/maven-simulator-11-review-check.log` failed during task selection because
Spotless is configured on the root project. It ran no checks and is not behavior
evidence; the corrected root-task invocation is recorded below.

The corrected `spotlessCheck solidReview` invocation passed in
`/tmp/maven-simulator-12-review-check.log`: formatting and whole-repository
review validation executed successfully with all 514 current identities covered.
The exact 94 baseline rows and all ten changed-type/four guide hashes were also
checked independently against the manifest and current files.

The first cleanup red retained sequential calls after extraction; the fix changed
only the both-owner ordering. The EOF red retained the old unchecked loop; final
fixture cleanup also removed an unnecessary socketpair from the child script.
Neither compile errors nor environment failures are counted as behavioral reds.
Final Java checks add four cases and two test identities over the assigned
baseline; production identities and executable hashes are unaffected.

## Exact original-file coverage

These are the **original 94-file hashes** from the frozen assignment. An entry
marked as corrected in its assessment still retains its original identity here;
the final correction identities are recorded in the changed-file table and type
blocks below. The table covers all text plus the qualified binary inspection.

| Exact baseline path | SHA-256 | Classification | Whole-file assessment |
| --- | --- | --- | --- |
| simulator/build.gradle | `ec49e750ec4082073403aa1dbed79657df5e45498c4ab6cca97e048f951cc79b` | build configuration | Application packaging owns its dependency/runtime boundary; Java 21 and compiler warnings are explicit. Runtime is project library plus pinned HDR only; JUnit/ArchUnit remain test-scoped. Reproducible ordering/timestamps and LICENSE/NOTICE are installed in archives/distribution. No simulator Maven publication or production measurement task is added. |
| simulator/scripts/fault_peer.py | `96bd4be6d122077df1ac9e3de35445bb03e8238f004aa16396f0afe2e9c28e43` | Python production tooling | All1095lines reviewed: header-first bounded decoder, independent mandatory-field layouts, exact paired response bytes, one nonblocking finite connection cohort, shared count/byte output budgets retaining partial writes, separate control queue and no byte interleaving. Frame and write deadlines, fixed selected fault counts and completed/abandoned accounting are explicit. Header-only bounded samples exclude credentials/bodies; cleanup failures remain failures. Fixture supports declared subset, not general conformance or peer acceptance inference. |
| simulator/scripts/load_runs.py | `91834a0dc7c987441b8ec89d2551aa277933e53c8219f4678e8d6375efeb9c45` | Python production tooling | Fresh pair orchestration never treats cached builds as measurements; shared finite process deadline attempts both owners before reaping. Exact source-input digest scope and independent executable identity are explicit. Aggregation bounds files/categories/buckets, reconciles terminal and in-phase populations, weights counts rather than percentiles, and retains failed recovery/overload results. Additional endpoint options are syntactically preflighted while Java remains value-validation owner; supplied source relationship is not falsely proven. |
| simulator/scripts/tests/test_fault_peer.py | `43c4ba86e6359f387ab19861bbb93b4cd447424f8e7e7e4c1ac3f22f665aabc1` | Python tests | Whole949line baseline read: independent literal header/body/address/TLV oracles, physical FIFO/retention checks, legal wrapped real-socket failure injection, finite local process pairs, signal/frame/write/cohort bounds and optional56installed pairs with artifact manifests. Audit found malformed-header test loop ignored early EOF and could spin forever; bounded actual socketpair child regression and EOF correction recorded separately. Optional installed-target matrix is distinct from default suite and must not be counted when skipped. |
| simulator/scripts/tests/test_load_runs.py | `2b26f8c8fe53413107c07348246cbeca0d7a3bddc385137bc03a423ef07419bb` | Python tests | Nine independent tests cover fresh failed campaign retention, exact build manifest, corrupt accounting/incompatible artifact rejection, count-weighted histograms and overflow, full burst/soak/directional plans and controlled recovery. Real finite subprocess timeout proves both owners reaped. No cached benchmark or fake target capacity claim. |
| simulator/src/main/java/kg/aidarbek/simulator/ArrivalSchedule.java | `8f687fde033d51efc91eb3d69e0ba891b898e52ee7503e2dc097848e35d29660` | production Java | ArrivalSchedule and Arrival own finite virtual scheduling only. Rate/hold variation stays in immutable LoadPlan; subtraction handles nanoTime wrapping. Nonintegral arrivals round upward, poll emits newest due once, skipped identities remain countable, finish is idempotent, storage <=64 steps. No callbacks, transport or encoding ownership. |
| simulator/src/main/java/kg/aidarbek/simulator/BroadcastTraffic.java | `e0304bd09799e5684a3b50839dad5b28f582e38242bf22485b19cf9e02df575b` | production Java | 5.0 TX/TRX ESME preflight, typed service calls and two bounded named-area fixtures; congestion/query values are declared synthetic. Originating PDU count is not radio recipient count. No helper SAR compatibility or extra implementation state is added. |
| simulator/src/main/java/kg/aidarbek/simulator/CohortMetrics.java | `4284f8756e11c2675d4f5f71e5a6258c7664c721f89650d557f23b1571061693` | production Java | CohortMetrics/Outcome/Snapshot separate disjoint accounting from workload policy. Synchronized updates maintain available-attempt/pending invariants; 256 peer statuses plus excess and 32 distinct rejection labels plus OTHER bound cardinality. Nested Latencies locks have no reverse callback path. Snapshots defensively copy maps; terminal latency excludes UNFINISHED and phase completions differ from eventual results. No transport/request engine dependency. |
| simulator/src/main/java/kg/aidarbek/simulator/CommonOperationSmoke.java | `fc6d009e96d586270a3c91fbb4b0be843a33c49a767932e3920334e99157ca8a` | production Java | Finite real-loopback alert/outbind execution uses public ownership APIs, authentication and paired endpoint cleanup with bounded waits. Main is a diagnostic entry rather than throughput model; no notification is misreported as a paired request outcome. |
| simulator/src/main/java/kg/aidarbek/simulator/CommonTraffic.java | `db84444bca0c3809c66167ec499827b902a1ea77be613ed8f1a253bce3854df4` | production Java | Public typed common-operation capabilities enforce profile/role permissions before creation; replacement explicitly assumes original DCS4/esm0 and applies 3.4 vs5.0 payload bounds. Query/multi negative bodies are profile-specific; no persistent storage or recipient delivery is implied. |
| simulator/src/main/java/kg/aidarbek/simulator/ConnectionChurn.java | `9bdd8100da8be62ffad7a6548d80eacb7ff10e92edb40d71058eff2021b9197a` | production Java | ConnectionChurn/Snapshot reuse original finite ArrivalSchedule, rotating fixed slots without retry backlog. Initiation callback separates replacement infrastructure; skipped, busy, initiated and failed counts reconcile. Stop is idempotent and fills only scheduled omissions; no connection or message persistence. |
| simulator/src/main/java/kg/aidarbek/simulator/ContentPlan.java | `a59b0ed3f3fdd52e831a75c79638d8124298bf76bc2aa77eb52cc63664b9b6c3` | production Java | Small deterministic generation/validation interface with explicit bounded-implementation contract; incomplete count defaults to zero only for plans without assembly. No session or wire placement dependency. |
| simulator/src/main/java/kg/aidarbek/simulator/DeadlinePacer.java | `ff03a932d0f94ec3400bcb910f1e55c881629540db755b107f918a94a81d7b6c` | production Java | One pacing responsibility; injected clock/park/spin provide the real timing seam. Finite 0..1ms spin guard, original deadline and interruption checks preserve LongConsumer composition. Caller supplies bounded positive pauses; no task queue or infrastructure construction. One deterministic oversleep/wrap test exists, but not a comprehensive scheduler guarantee. |
| simulator/src/main/java/kg/aidarbek/simulator/DecisionQueue.java | `05823916ab3bda25e6e87182c1e4c832d640ef80d4dc9037b9545a4122ff546c` | production Java | Capacity covers waiting and completing deferred decisions until callback return; completion/cancellation runs outside its guard. Advancing is explicit and wrap-safe, stalled decisions require owner cancellation/close. Production cancellation supplier is a nonblocking atomic read; no scheduler backlog or unbounded queue. |
| simulator/src/main/java/kg/aidarbek/simulator/FaultCriteria.java | `fd0cad9a63ca420911a87a89ad08c03fb5d4c14a53665e5980a237804e7a6f99` | production Java | Pure receiver-accounting and optional selected-mix checks; consumer delay changes deferred-admission expectations explicitly. Reconciliation includes disconnect, invalid input, capacity, cancellation and delayed/stalled reservations. Capacity fallback cannot masquerade as an applied configured fault. Finite 100-sample minimum/tolerance policy is explicit; policy does not fabricate delivery success or inspect sockets. |
| simulator/src/main/java/kg/aidarbek/simulator/FaultPolicy.java | `32f0ae3a9c972a2ec9f55587fdfda6650b482ef6dc14ea67d669313e036adede` | production Java | Validated disjoint 0..100 buckets, bounded delays and deterministic seed/sequence mixing; finite sample percentages are decisions rather than promised observed ratios. Nonzero uint32 rejection and per-stream disconnect threshold are explicit. |
| simulator/src/main/java/kg/aidarbek/simulator/HelperContentPlans.java | `10ae54d1c96d31c9c03e6bf513f87378948746343a7e762a69e0c89bc5219df6` | production Java | Exact GSM-unpacked/UCS2 and raw-preserving receipt fixtures have explicit size policies. Fixed plans compare full values before interpretation. SAR uses one bounded fixed reference, per-generation stream ownership and retained deduplication with paused fixture time; repeated cycles do not claim independent message delivery. Replacement and unsupported receipt send roles remain preflight-owned. |
| simulator/src/main/java/kg/aidarbek/simulator/Json.java | `ad5a3e9deca15b04bba395c2afad66a2fbe71689f1b1a5f65bd9ff23837aca98` | production Java | Explicit finite numeric/scalar, list, supported-key map and record serialization; no arbitrary object fallback. Control and surrogate UTF-16 code units are escaped, non-finite numbers and unsupported types rejected. Recursion operates on tool-owned bounded report structures, not an untrusted general JSON API. |
| simulator/src/main/java/kg/aidarbek/simulator/Latencies.java | `08ab7984604b95e10d0de602f74ce8512c0bb28c8fe8b6184c416310bd3da945` | production Java | Latencies/Bucket/Snapshot adapt one fixed-range, three-significant-digit HDR histogram; microseconds are rounded up and >1h samples counted explicitly as overflow. Snapshot lists own immutable buckets. HDR remains the tooling dependency below metrics; no callback callouts, runtime growth beyond fixed histogram range or inferred exact percentile promises. |
| simulator/src/main/java/kg/aidarbek/simulator/LifecycleSettings.java | `7d1ddc610ef37da10a1f29a5982623905c788a1bd7de43bf2b69dc0424aa594d` | production Java | Separates simulator TLS material loading from library transport lifecycle. Selects and copies only known options; validates roles/dependent flags, bounded policies and environment-name syntax; loads PKCS12 with try-with-resources, clears transient password arrays, never stores resolved values in the record/report. SSLContext/KeyManager/TrustManager are deliberate JDK adapter dependencies. Default trust is JVM trust, never trust-all. Tests include real encrypted endpoints and invalid secrets. |
| simulator/src/main/java/kg/aidarbek/simulator/LoadPlan.java | `c3be920a6b970dd8b845c121d6876dd0ad33333bd6daeed5a0c9f31c3965e59c` | production Java | LoadPlan/Model own immutable validated workload configuration. Defensive copies preserve record semantics; limits cap rates, count, hold count and all durations. Arrival/fixed-concurrency rules and exact hold sums reject contradictory plans. Internal shape requires no extra interface; no endpoint/network dependency. |
| simulator/src/main/java/kg/aidarbek/simulator/LoadSettings.java | `7d3d07c35834bb9c49f1e02fea9b09b618d41555e3c539449a611d4022c5d2a5` | production Java | Owns finite pacing/churn/sample/fault/resource threshold values and nonsecret report projection. Durations/counts and NaN tolerance rejected; zero/-1 disabled forms are explicit. Immutable record inputs need no artificial interface; serialization delegates through a copied bounded map. |
| simulator/src/main/java/kg/aidarbek/simulator/MessageTraffic.java | `3e66490591c90d9718207b95a1f05ae5124940c6fac3cde73bf700a6336a4bd3` | production Java | Explicit role/profile routing and typed requests; payload goes exactly once in short_message <=254 or message_payload, with lazy inbound extraction inside accounting. Duplicate/mixed payload placement fails. Responses preserve version-owned library wire checks. |
| simulator/src/main/java/kg/aidarbek/simulator/PendingCall.java | `4f25cb49d5934574d4bfefae8dce179cff4134c0920d6205d6a84418dbe235ff` | production Java | Minimal request-observation port: terminal poll, transmission certainty and cancel, with Completion as outcome/status value. No sequence allocation, retry or transport access. Real and fixture implementations are reviewed with consumers separately; cancellation is not a promise that physical request ownership has ended. |
| simulator/src/main/java/kg/aidarbek/simulator/PressureCriteria.java | `80de84c99fa56da844eabd4a17928f8b1fcc67048d81a0f1907792341f96a86f` | production Java | Compares public sampled connection/request/reply/decision/stream ownership to the exact simulator-configured bounds and checks final retirement. Uses long arithmetic for aggregate limits. Does not infer transport queues or globally atomic session snapshots. Configuration/observations are immutable collaborators rather than additional request tracking. |
| simulator/src/main/java/kg/aidarbek/simulator/PressureSampler.java | `17bfa926e150bc7ea03ed8cb8efbde432813e6ab49366c8d3c6141e4fabe8f00` | production Java | PressureSampler/Observation/Summary sum <=4096 public SessionResources with checked long arithmetic, explicitly sample per-field peaks and historical facade count, and reject impossible negative/bounded observation inputs. No transport-queue inference. Volatile immutable publication keeps report reads independent of worker progress. Intrinsic sample/baseline calls into observer/writer: the one virtual SimulatorRun test-owner path is being corrected without production change. |
| simulator/src/main/java/kg/aidarbek/simulator/RawContent.java | `61c92d1b81130b1407aa70331dce0d29f80fb5bca141e1b19972248f643eec4d` | production Java | Allocations are bounded to 65535 bytes; seed/index deterministically generate octets with DCS4. Receive validation intentionally checks shape, not regenerated byte identity, because no per-message index is sent; it does not claim corruption verification. |
| simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenario.java | `5efd94e762fac9d229d1330c41c7066c56d7f51b9be297b8bbd362c284b05d3f` | production Java | Separate finite loopback client/server executable with fresh summary output, exact class/artifact digests, logical vs wire deadlines and bounded endpoint cleanup. Actual response IDs drive ledger correlation. Counts, remaining ownership and failure class gate result; no throughput or persistent delivery claims. |
| simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioLedger.java | `6f082c49b1508941ca688542ba9c7a7789d7cf48a099e617f375cde08ba2198a` | production Java | Fixed slot array plus bounded opaque-ID, early-receipt and duplicate structures; whole logical roundtrip holds window. Explicit owner expiry is wrap-safe and one-settlement; no ID normalization or fabricated missing delivery success. Counters preserve positive receipt decisions subsequently found unmatched. |
| simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioMessages.java | `99604b4d21472fd8f1759a0ace80f3be66025dd5cbdbece1a3ca34f683fa4d2b` | production Java | Independent 4-byte submission identity and registered-delivery flag; explicit TLV-only delivered fixture checks exact metadata and opaque ID. Helpers are synthetic workload values, not a provider-generic receipt policy. |
| simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioOptions.java | `e458378a9056eff4ed2985766d954a8d1cda7c7ed93b697426ffd6443e58b913` | production Java | Finite loopback-only role, count<=10000,window<=32,time bounds and fresh path; unknown/duplicate or incompatible server fault choices rejected. Separate fixture is not the load benchmark CLI. |
| simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioServer.java | `6dc1cc3cc6994b04dffb4caf58b3c9d5c347586900cbdd0d5d8302afeb8639a0` | production Java | Bounded handler decisions/receipt handles; receipt result observed by owner polling before positive submission decision, deliberately exercises early receipts. Fault injection is selected once and counted separately, no autonomous retry. Handler completion and sends occur outside monitor; close cancels remaining ownership and reports failure. |
| simulator/src/main/java/kg/aidarbek/simulator/ReplyController.java | `ce9304cd0eb2a372c66b3f8748b46266d3ea23606c29ec4740cd443e71be80ed` | production Java | Received increments before lazy extraction; closed receiver and pre-handler rejection remain outside declared cohort. Stream/content and deferred-decision bounds are independent. Counters distinguish decisions, queue rejection, disconnect and invalid payload. Completion/disconnect callouts are outside its monitor; built-in content collaborators are finite pure checks. Incomplete SAR is accumulated on successful physical stream retirement. |
| simulator/src/main/java/kg/aidarbek/simulator/ReportWriter.java | `27a5d8c4999cfea11d25e42ca84f08bdb62fc6f531999e530a82efad7d172ba8` | production Java | Fresh CREATE_NEW report/CSV ownership prevents measurement overwrite; constructor and close attempt both writers and retain suppressed cleanup errors. Numeric streaming stays bounded per row. Final report is emitted after sampling retirement; partial failed output is retained rather than relabelled successful. |
| simulator/src/main/java/kg/aidarbek/simulator/RequestObservation.java | `029db4ddac1a433865f59808c74590a3438828ddc2ac79f3d172899145b87102` | production Java | A narrow PendingCall adapter reads atomic library terminal snapshots, preserves peer status/generic_nack distinctions and maps request deadlines/cancellation/local failure separately. It polls internal outcomes independently of CompletionStage dependents, forwards certainty and cancellation, and never claims a local write guarantees peer acceptance. MessageTraffic/real endpoint tests cover composition rather than a duplicated future implementation. |
| simulator/src/main/java/kg/aidarbek/simulator/ResourceCriteria.java | `40d0cb35c0fa01cb4c145a01664f1c25b7c5b2f6f93bbb75e9d0da111982001d` | production Java | Evaluates explicit sampled/high-water RSS, heap and final-minus-baseline FD/thread gates; unknown RSS/FD or absent enabled baselines fail rather than count as zero. Optional thresholds are separate from resource collection. No GC, mutation or assumed native-memory attribution; no claim that finite sampled peaks establish leak freedom. |
| simulator/src/main/java/kg/aidarbek/simulator/ResourceSampler.java | `cc5d2e9b4aa59efd854234b1571ba5f9f85d6ffce23fb1ac68310c93d8c998fa` | production Java | ResourceSampler/Summary serialize optional process observations and publish immutable volatile snapshots. Initial/baseline/whole-lifetime peaks/latest remain distinct; unavailable descriptor/RSS values remain -1 until measured. Writer/clock/observation ports isolate runtime infrastructure. Intrinsic sections call blocking observation/writer operations: production CLI and SamplingLoop use platform threads, but virtual test-owner baseline path is under coordinated regression. |
| simulator/src/main/java/kg/aidarbek/simulator/RunCriteria.java | `1327d1eb1e56606c523b0f5489d9621f8ba7bb6d148e4cb3f970dd5b3e7a0ab8` | production Java | RunCriteria/Verdict own explicit acceptance policy independently of clocks/endpoints. Immutable failures preserve execution aborts; expected faults do not suppress unbalanced, unfinished, bad-content or cleanup failure. Rate uses observed duration and only in-phase successful completions against finite planned/configured rate; absent, partial and zero work cannot meet an enabled rate gate. Fixed-concurrency positive rate thresholds are rejected by configuration. |
| simulator/src/main/java/kg/aidarbek/simulator/RunEnvironment.java | `63df8d337290287450fc50552500e114ce8e244dde82a3de25e050306d9deae0` | production Java | RunEnvironment/Snapshot implement explicit observation/fingerprinting infrastructure: JMX heap/thread/GC and optional Linux FD/RSS/high-water, streamed SHA256 over executable JARs/classes. Missing OS readings are -1; GC is cumulative collection time, not count/native attribution. Allowed JVM argument filter omits arbitrary secrets. File streams/directories are closed. Whole-class ownership is portable observations plus recorded identity, separate from policy and JSON. |
| simulator/src/main/java/kg/aidarbek/simulator/RunReport.java | `caf92da65eb882ece7dcfe4f2a0a520982e5e836075ef6d6ef4df421c455db24` | production Java | Separates configured and observed phase durations, warmup and measured cohorts, peer handler decisions and actual wire observations. Rates use actual elapsed phase and planned/configured offered rate; unavailable transport/control/radio counters remain null. Limits and histogram populations are explicit; final composition map is intentionally owner-extensible. |
| simulator/src/main/java/kg/aidarbek/simulator/SamplingLoop.java | `b11a8924829dccc2d683aee2a023a4d009ec0b30a6d46d84fe64cbb866d4ec27` | production Java | Owns one fixed daemon platform observer worker, no queue, original periodic due times with missed slots counted, bounded idempotent stop and separate physical liveness/failure reporting. Slow samples do not queue or consume virtual carriers. Internal synchronized start/close do not wait on user work; volatile state and unpark coordinate shutdown. |
| simulator/src/main/java/kg/aidarbek/simulator/SessionTrafficSource.java | `614fc49c05f8e873fa6103ca73d39473f28399d532fc0bcbe7cb842df5e1b071` | production Java | Single-owner fixed-concurrency selection owns a <=4096 per-session counter array and <=65536 total slots. Arrival routing preserves original floorMod identity. Fixed mode reserves before sender, releases on thrown rejection/Error, never retries another slot, and ObservedCall releases exactly once only upon terminal poll, including after cancellation. Sender receives actual slot and original index; no sampled-resource admission or protocol sequence ownership. |
| simulator/src/main/java/kg/aidarbek/simulator/SimulatorArguments.java | `83d2514f3a7c6d2adf27899d772d4b40173e105964fdefa5928275fbebba75dd` | production Java | Exact CLI names, duplicate detection and explicit units feed focused immutable config/settings/policy values. No file/socket/worker side effects; arbitrary passwords are not accepted as CLI fields. Supported variation lives in operation/content/lifecycle seams rather than parser callbacks. Large parse lists are rejected by LoadPlan before workload allocation. |
| simulator/src/main/java/kg/aidarbek/simulator/SimulatorConfig.java | `f901397871bd5f81763b7913ad9847eb4d2311aa457fbdff4215447e50ae95d8` | production Java | SimulatorConfig/Mode provide immutable non-secret finite run inputs; role owns reconnect/churn, positive arrival ratio requires arrival model, addresses/revision/labels/aggregate count and estimated bytes are bounded before allocation. Independent operation adapters validate operation-specific capabilities; the narrower common <=20 address scenario is explicit fixture coverage, not a protocol-wide data_sm limit. |
| simulator/src/main/java/kg/aidarbek/simulator/SimulatorEndpoint.java | `3d496a2c85f71015a003d05d7a6f296cd47ccd7983ac571adbf3ca8bee0c4130` | production Java | SimulatorEndpoint/Slot/LifecycleSnapshot/ReconnectSnapshot own a fixed connection cohort and endpoint lifecycle through public library capabilities. Bounded ready queue, <=4096 immutable observed slots and finite reconnect lists avoid session accumulation; generation replacement resets per-stream ordinal. Churn waits actual old termination before reconnect, no autonomous request replay. Authentication compares supplied fixture credentials; socket endpoints/TLS are composition-only. Server cohort deadline is a retained fixed policy (16s + configured spacing), not a promise that every 1000-connection run succeeds. Shutdown cancels loops/replacements and reports cleanup; interruption is restored. |
| simulator/src/main/java/kg/aidarbek/simulator/SimulatorMain.java | `709a17ac234d385b13df40cb30dedc80925e4d8b6962da0e4232cfc65d80addc` | production Java | Sole public workload launcher wires environment credentials and exit codes; no password value appears in error messages. Help distinguishes role/profile/content/arrival/concurrency and disabled criteria. Library commands and reusable scheduling remain elsewhere; no subprocess publication or implicit measurement task. |
| simulator/src/main/java/kg/aidarbek/simulator/SimulatorRun.java | `b121f766258a05563bb80885a1eb410e5fe81eb0775656507a32f71f1893b56b` | production Java | Single composition root wires public operation adapters, per-generation content, endpoint owner, platform sampler, source routing, acceptance policies and final report. Preflight resolves helper role/content/TLS before report creation. Measured receiver grace remains original bounded drain; partial startup/execution reports preserve failures. Receivers close before endpoint shutdown only after runner grace. Sample failures abort generation, termination/pressure explicitly report ownership. CLI owner is platform; virtual-owner test call paths require separate pinning assessment. |
| simulator/src/main/java/kg/aidarbek/simulator/TrafficContent.java | `d935b40af8c350718b7eadc2aa35a8150ad09d0b0fd179c22a352734ec5e2fbc` | production Java | Immutable payload/metadata boundary validates octets, payload <=65535 and <=64 helper tags; message_payload placement is prohibited here and remains operation-owned. Value collaborators preserve defensive copies. |
| simulator/src/main/java/kg/aidarbek/simulator/TrafficMetrics.java | `76434fc23bbc29c6ef0bae03ffcd1fc65be1ed9f2aeb066b804fecb353205a5f` | production Java | Routes one originating identity to total and original scheduled-step counters; does not schedule or serialize. End offsets/count arrays stay <=64. Skipped ranges advance the original cursor, completion classification uses original step deadline, immutable snapshots leave histogram ownership below. Fixed-concurrency caller passes empty rates/holds with null schedule; its package-private constructor is a trusted composition seam. |
| simulator/src/main/java/kg/aidarbek/simulator/TrafficOperation.java | `7eaf60cc6fdeab2be2b6de5d3bbf447c782f043ffe71c463abdbb29bb6735ea8` | production Java | Single request-sending capability returns the library-owned handle; no duplicate correlation, replay or cancellation semantics are introduced. |
| simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java | `ad934e440cb182b9884c07c076a07b98fde6b16df55c53b5af1e586192dea6e8` | production Java | TrafficRunner plus Active/Phase/Result/Interval retain finite generation, original identities, terminal observation and bounded drain. Variable time/source/maintenance/lifecycle operations are injected; reports/CLI stay elsewhere. Admission and terminal counters distinguish refusal, skip and uncertainty. Runtime aborts preserve partial cohorts, cancel remaining calls and stop a failed warmup before measurement; measured drain uses original stop time while warmup can finish early. Result copies lists. Callback ports are internal trusted fast collaborators, not arbitrary application execution. |
| simulator/src/test/java/kg/aidarbek/simulator/ArrivalScheduleTest.java | `a932444d39493084185153fe3ae2b07a2a90c472f3cc22ae91fb4bd8cad807b3` | test Java | Four independently calculated time/index fixtures cover uneven holds, skips, no catchup, count cap, idempotent finish, signed/wrapping time and nonintegral no-early emission. Real scheduler receives immutable plans; no permissive fake transport or thread sleeps. |
| simulator/src/test/java/kg/aidarbek/simulator/BroadcastTrafficTest.java | `6f19642804446d524efcccde5ecd01c816bc5d8b159d51bcfee683546c94f926` | test Java | Independent payload count/bounds, version/role/mode failure, real positive/negative replies and congestion with two areas; no radio-delivery assumption. Uses real endpoint ownership and finite waits. |
| simulator/src/test/java/kg/aidarbek/simulator/ChurnEndpointTest.java | `8458901223d83c0a3b6e321442c6fbea212557ff1ee1757d7088beaed64bb9ff` | test Java | Real one-slot endpoint loops exercise deliberate churn and opt-in reconnect across two distinct generations, ordinal reset, finite attempt budget and physical bound. Owner virtual thread is joined and interrupted on failure. Does not replay messages or invent connection counts; no fake endpoint. |
| simulator/src/test/java/kg/aidarbek/simulator/CohortMetricsTest.java | `2d187a2c355773ca9390bac855cb6f93ed55c78d077b68b7ccf5d25a1e270ae6` | test Java | Independently constructed seven-arrival mixed outcomes test counter identities, phase exclusion, immutable maps and UNFINISHED latency exclusion. Additional histogram fixture tests overflow/counts/ranges and 300 peer statuses prove cardinality cap/excess. No networking fake; finite data exercises actual counters/HDR. |
| simulator/src/test/java/kg/aidarbek/simulator/CommonOperationSmokeTest.java | `faa3a27865daa17018937cc6b576a0380af2fbc5a05b236113d275cb2f10d897` | test Java | Real alert/outbind both-profile observations and cleanup counts plus invalid preflight; finite protocol smoke only. |
| simulator/src/test/java/kg/aidarbek/simulator/CommonTrafficTest.java | `5f05a65a19afaa8497ab244356a0bbe409c4195d88c6593b62cbbe80fc9f3f9e` | test Java | Both profiles/allfour operations and positive/negative response-body rules over real endpoints; independent replacement profile limits and absent-original-metadata rejection. No storage behavior assertion beyond fixture policy. |
| simulator/src/test/java/kg/aidarbek/simulator/ConnectionChurnTest.java | `5d7df4b8b0791a783c5de6315da8190e4e88f8bb451b8ff3a86a059c5ea4c6fb` | test Java | Controlled wrapping schedule independently expects five planned, three skipped, one initiated and one busy event; stopped work never replays and restart fails. Narrow IntPredicate fixture records only observed replacement attempts. |
| simulator/src/test/java/kg/aidarbek/simulator/DeadlinePacerTest.java | `7963a73e1f1338f0488f0002089cceede8bf9e0870d4db730ec64a6005e4e03b` | test Java | One controlled AtomicLong clock/park-oversleep/spin scenario spans long wrapping and ten waits; verifies total intended time and reduced park budget. Narrow deterministic collaborators satisfy the pacer port; does not claim actual operating-system pacing accuracy. |
| simulator/src/test/java/kg/aidarbek/simulator/DecisionQueueTest.java | `6238438f0b35a1d69b527418d363e50d7291f68f4e6fe5befd2dc93f7e09e6c0` | test Java | Explicit clock boundaries/wrap, cancellation physical retention, full/closed admission and stalled cleanup; independently asserted counters. No callback-under-lock timing assertion, covered by direct source review. |
| simulator/src/test/java/kg/aidarbek/simulator/FaultCriteriaTest.java | `01bc327111c0b465a29ee7c0203a03c206943326f8ad66add17fed1cb0cd5a2c` | test Java | Independent accepted/rejected/delayed/stalled populations verify exact mix, no population and capacity fallback. Snapshot fixture explicitly reconciles deferred accounting; tests actual policy lists rather than mocking infrastructure. Does not establish statistically likely fault distributions outside the fixed seed/policy tests. |
| simulator/src/test/java/kg/aidarbek/simulator/FaultPolicyTest.java | `8c54af98a329dd0413abc500a83da6b1d063d40172987c6db789490b7084e80d` | test Java | All100 disjoint bucket expectations and deterministic iteration-order independence; invalid mixtures/delays/status bounds, without treating seeded finite distribution as exact percentage guarantee. |
| simulator/src/test/java/kg/aidarbek/simulator/HelperContentPlansTest.java | `81bee462d8e1ced71ada594cbba21c1714c326cfb87d95917a717bf604a42a63` | test Java | Known independent GSM/UCS2 octets and exact receipt string, raw unknown/missing fields, operation matrix and size failures; SAR tests ordering, duplicate acceptance, wrong bytes and separate-stream incomplete counts. |
| simulator/src/test/java/kg/aidarbek/simulator/HelperSimulatorTest.java | `fdc4a9c4190e16f9da5b56163329c5135b701d79cd12a656306dc6622aa0ecb6` | test Java | Real child process matrix for helper text, receipt role and two-stream SAR, early invalid-config resource checks; existing finite WaitingChild test proves both cleanup attempts after interruption, reused unchanged by audit regression. |
| simulator/src/test/java/kg/aidarbek/simulator/LifecycleEndpointTest.java | `c7db8b64b2d33545a453811848d7d6c57e14eabbd68aa0014632670f5073d4a0` | test Java | Four role/profile combinations run real TLS simulator owners against public encrypted library peers, assert enquiry and resource shutdown, and clean up the bounded startup thread. Explicit SSL roles separate TLS direction from SMPP command roles. |
| simulator/src/test/java/kg/aidarbek/simulator/LifecycleSettingsTest.java | `023cf93bfd87e3b9fb9a052b6367062c656a5527c14da760dd7d338ede8b099a` | test Java | Owns lifecycle value contract tests: exact nonsecret paths/env names, omission of unrelated fields, finite policy conversion and contradictory inputs without key I/O. Uses real immutable lifecycle values; does not pretend parsing alone establishes TLS interoperability. |
| simulator/src/test/java/kg/aidarbek/simulator/LifecycleTlsTest.java | `520791660942d054b9a1d9aad25558ed70f1a1e4f7a6e932141dd35eb46f5e31` | test Java | Both profiles connect real TLS public endpoints using the development certificate, explicit trust and localhost name, exchange enquiry/unbind and await cleanup. Missing/wrong env-secret cases verify safe failure text; fixture source is declared. Fixed finite waits may fail on very contended hosts, as already retained evidence records; no permissive TLS stub. |
| simulator/src/test/java/kg/aidarbek/simulator/LoadPlanTest.java | `268944c245221899bfca8e33510bfeae2a964927eb6105a0289976ea0c2bb0d5` | test Java | Validates independent malformed and contradictory limits, submillisecond arrival warmup, owned rate copies and immutable access. Does not mirror formatted output; remaining hold-validation paths have integration coverage rather than exhaustive constructor branch cases. |
| simulator/src/test/java/kg/aidarbek/simulator/LoadRunTest.java | `190b6a67e69cfd1c4aaa1fd8d999b633c748f8b424e007c52e3223b939a9b6c9` | test Java | Real SimulatorRun pairs verify report baselines, sampling retirement, pressure CSV and generation replacement. Narrow report substring assertions complement structured Report/aggregator tests; they do not independently validate every JSON field. Server executes on virtual thread, exposing the conditional intrinsic-sampler baseline callout edge raised to parent; owner thread always joined/interrupted. |
| simulator/src/test/java/kg/aidarbek/simulator/MessageTrafficTest.java | `75fc9fab136b03c7c8f19e1f82fc36887ca90aa7b05aada833a92225210b2a00` | test Java | Independent short/payload boundary matrix through65535 and explicit data TLV placement; checks both version-dependent role restrictions and unknown-operation absence. |
| simulator/src/test/java/kg/aidarbek/simulator/PressureCriteriaTest.java | `80c85e93d15c1cade9b5bd13ea13c50a9b5197a26946d23a987161e1797b0611` | test Java | Boundary and boundary+1 values independently test every ownership ceiling and final nonzero state, including observed-session vs active ownership distinction. Values are explicit public observations, not a substitute transport queue. |
| simulator/src/test/java/kg/aidarbek/simulator/PressureSamplerTest.java | `ea9f623bb9fd394df15fffe6f4faabcf6c33c97aa4448e81eb3c3e2986616ba0` | test Java | Combines explicit real SessionResources values, fixed clock, exact baseline/peak/final snapshots and literal CSV row expectation. Does not mock a transport or assert globally atomic sampling. Small ownership fixtures cover numeric aggregation and immutable published state. |
| simulator/src/test/java/kg/aidarbek/simulator/RawContentTest.java | `f557da8bf6ed83443c68362757956ef93d82260b329118299e56150a0b5fc8a0` | test Java | Deterministic seed/index differences, DCS4, zero/max payload and malformed bounds; explicitly verifies raw shape rather than asserting nonexistent byte-correlation behavior. |
| simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioLedgerTest.java | `6c2ca6cd48ed454f9c762c409b7668fc45ddbbfac1aea4b382d402841a430f38` | test Java | Nine focused cases independently cover early/late IDs, duplicates, mismatch/case preservation, missing expiry, negative submissions, repeated response IDs and independent active/storage bounds with signed clock wrap. |
| simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioMessagesTest.java | `e286b971f661ca3a98d8834c702fc9361a64af1146c1caf6a3bd1b91077a7839` | test Java | Independent known byte index and exact receipt TLVs; rejects absent registered-delivery/invalid size/state/flags. Tests values rather than calling encoder to create expected bytes. |
| simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioOptionsTest.java | `a62fdc2fdc0995de364ab62ffe5efbd6e059bfdfb5e546e7e07254e54282b950` | test Java | Explicit finite parser matrix including server-only fault and all-rejected contradiction; defaults and unknown/repeated choices are verified before resources. |
| simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioProcessTest.java | `3fd7f25d1b9a1fd77083c2d4f1fc735ebd7e0c9ad1053b28959a3c7a5f888122` | test Java | Separate actual JVM pairs cover both profiles and positive/negative/early/missing/duplicate/mismatch accounting, refusal/idle/fresh-path failures. Audit found interrupted first process cleanup can skip second; bounded real-child TDD correction is recorded separately at new hash. |
| simulator/src/test/java/kg/aidarbek/simulator/ReceiverGraceTest.java | `6f7fcf8bf8e3c6cad272c5257f33dc7cffbf0453bf036ac34ed1e5caf3ae0ee2` | test Java | Controlled original drain deadlines (wrap, already expired,1ns,interruption) and real both-profile independent remote request after local terminal drain; substitution-correct pending fixture and bounded dual endpoint cleanup. |
| simulator/src/test/java/kg/aidarbek/simulator/ReplyControllerTest.java | `63ddf3938ce56d55bc8e30b77e777751783509f4d4724f3b5d0903a4fb6fd2ae` | test Java | Real endpoints prove controlled slow-consumer timing and distinct-generation SAR retention; fixture performs both shutdown owners in nested finally and bounded real waits. |
| simulator/src/test/java/kg/aidarbek/simulator/ReportWriterTest.java | `db9e5258ecf71978a2722d26943c14729604588f11ef8891fe576d028df48c9d` | test Java | Independent escaped/control JSON expectations, unsupported/nonfinite failures, exact CSV rows and fresh-output overwrite protection; does not pretend to simulate filesystem cleanup failures. |
| simulator/src/test/java/kg/aidarbek/simulator/ResourceCriteriaTest.java | `2529046fc35e2a6a28155aaf420707caae9cd948efb6deb73784db7e90b02ac4` | test Java | Supplied process snapshots and real temporary ReportWriter prove unavailable RSS, post-baseline peaks, returned-low RSS with historical high water, FD/thread growth and disabled gates. No forced GC, invented OS observations or real timing dependency. |
| simulator/src/test/java/kg/aidarbek/simulator/ResourceSamplerTest.java | `675358333e2b3c55211e7edaae91bd5c1b9e79131266b42a36dc1244d1188dbd` | test Java | Controlled observations distinguish baseline/final from whole-run peaks and preserve unknown descriptor semantics across sample/tick. Real temporary writer receives samples; no OS timing or GC dependency. Exact counter expectations independently specify observation contract. |
| simulator/src/test/java/kg/aidarbek/simulator/RunCriteriaTest.java | `f431e6eedefdcbd2863f4d858a3786aa9e35ee986fd01f5805ca29ee2687a88c` | test Java | Truthful explicit one-second duration fixtures distinguish eventual from in-phase success and test 1.02s overrun, half-duration, zero and empty observations. Separate tests prove expected failures cannot excuse cleanup/content/unfinished accounting. Pure value fixtures avoid wall-clock flakiness; no weakened threshold. |
| simulator/src/test/java/kg/aidarbek/simulator/RunReportTest.java | `df8e364ae89b016145158077d32356702dc30442c3e56fdd30a73ee1d92ad6d7` | test Java | Constructed cohort independently distinguishes one in-phase and one drain success, checks achieved vs offered rates and absence of invented receive/concurrency rates; does not derive expectations by copying serialization output. |
| simulator/src/test/java/kg/aidarbek/simulator/SamplingLoopTest.java | `3a62ea3d84b273bc39dd1748dd58a27caaac584d049f4091f7ea78c2169a26f1` | test Java | Latched blocking sample proves caller progress, physically retained worker during bounded stop, no queued sample accumulation and final worker retirement after release. Owner startup thread cleanup is explicit. The callback deliberately blocks by contract and does not claim cancellation ends physical work. |
| simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressProbe.java | `bbb5b427394d25c0dff7ec9211a296d66aa8edc2b24c78e6c262cf6adb2de3f5` | test Java | Whole probe and Gate reviewed. Child JVM uses a real sampler intrinsic guard with one legal blocked observation and a virtual releaser, checks exact calls, skipped slots, platform thread accounting and worker retirement. Captures no private runtime data. Currently covers worker sample only; new synchronous test-owner baseline scenarios will cover the independently identified test-only path. |
| simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressTest.java | `0bd2239dab96a0fe1fb579cc2d9eda589bcfbfa0a8eb1e627e7b336b2764dab4` | test Java | Fresh bounded one-carrier JVMs validate resource and pressure worker progress, stdout result, process exit and forced final reaping. Constructs exact classpath from current code-source locations; no background benchmark or enlarged scheduler pool. Two baseline regression cases are planned in isolated checkout. |
| simulator/src/test/java/kg/aidarbek/simulator/SessionTrafficSourceTest.java | `39c2cfd885281e104a4363b4c86d70d77296cec92b45015d91342c8a3ed61386` | test Java | Whole test plus ControlledCall and Emission reviewed: asymmetric completions, no replay of rejection, slot retention after cancellation until terminal poll, idempotent release, independent arrival routing, transmission forwarding and real SAR ordinal composition. ControlledCall is intentionally single-owner and terminal-only-by-explicit-completion, preserving PendingCall assumptions. |
| simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java | `0fb391c9b1077a895784f7c3bf6a337014512ae6ac9ed56f1b2dec41e71cadd9` | test Java | Production bytecode boundary permits only declared public library values/capabilities, including TLS config, and rejects second RequestWindow or TcpTransport ownership using meaningful negative fixtures. It does not claim all nonlibrary dependencies are architecture-audited automatically. |
| simulator/src/test/java/kg/aidarbek/simulator/SimulatorArgumentsTest.java | `11db384f7e01cab22f19e9579dd4787fbfbfc486416e4d9a5925f8708705b4c0` | test Java | Seven focused parser/config tests cover independent rate+hold units, bounded byte/count inputs, typo/duplicate/secret rejection, missing revision, ownership conflicts, disabled/default models, finite controls and fixed-concurrency rate-threshold refusal. Assertions inspect constructed config directly and malformed inputs before resource creation. |
| simulator/src/test/java/kg/aidarbek/simulator/SimulatorExchangeTest.java | `dd960699d521ae21630d05128bfd13974f916fce78361b5b442177386266614e` | test Java | Real raw5.0 bind/submit with65unknownTLVs proves extraction failure is counted before negative response; separate receiver rejection and interrupted startup ownership tests. Raw read/write bounds and peer timeout remain finite. |
| simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java | `d1d13ad6e2e26e308fbaad68864971d881dd6c4322664f98c0d2af9340534166` | test Java | Actual separate JVM coverage for both profiles, duplex data, finite fault decisions and common/broadcast CLI registry with fresh report assertions. Audit found two pair-finally paths skip server after interrupted client cleanup; new bounded regression and fix recorded separately. |
| simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java | `475ae017aca05ae488cba7f9abb099f30f7e6cf76f3800d1c4264fa3b6e72aab` | test Java | Whole class and FakeCall/three anonymous PendingCall fixtures inspected. Controlled clocks verify original rate-step attribution, separate warmup, local rejection, delayed generation without backlog, finite concurrency, exact cancellation on abort, unfinished warmup and lifecycle order. Fake polls represent terminal observation faithfully and no-op cancellation is used only where no physical resource is claimed. Receiver-grace regressions are in another assigned file. |
| simulator/src/test/resources/lifecycle/development-only.p12 | `16d5cc34fde38f890586e82c58218604ce9f53652e2890aaf7ed1393bbc6068b` | binary development TLS fixture | 2644-byte PKCS12 inspected read-only using OpenSSL, not treated as source text. Valid MAC and one certificate/shrouded keybag; PBES2/PBKDF2/AES256-CBC, sha256 MAC, 2048 iterations. Self-issued CN localhost with DNS localhost/IP127.0.0.1 SAN; valid Sep9 2026 to Aug16 2126; certificate SHA256 05212bd96b9dac866fcc4f381cce6a7353ee6f02622db3d9ba1687a814b46a7f. Public development-only password; no private key exported. Test-resource path is excluded from application runtime/library archives by normal source sets. |

## Additional current-document coverage

These four root-owned guides were read in full as an explicitly added scope,
separate from the original 94paths. Hashes identify the final guide versions in
this handoff; the receipt guide and all historical result sections are unchanged.

| Path | Final SHA-256 | Review outcome |
| --- | --- | --- |
| docs/SIMULATORS.md | `7ee047d3899534bf32fed954720ddaf8c6cffe475afe6cbd5f4b080a2a1fb9ff` | Corrected stale later-work statements, actual successful-rate gate and completed-bind spacing; current operation/content/bounds/report descriptions checked against sources. Historical evidence remains linked. |
| docs/LOAD_TESTING.md | `78673fb0204c224303660d87439ba54a0eb1ad6969d0ffe730d5f157cf25e0d9` | Clarified periodic platform worker versus synchronous platform-owner boundaries and40 ms delay after completed initial binds. Finite presets, retention, recovery, unavailable counters and candidate evidence remain unchanged. |
| docs/FAULT_PEER.md | `cc252a887bd9f71d24f5ad712031377c1916ef7a0f49d5b056b51b5b36c61881` | Corrected RX/3.4data compatibility; checked finite configuration, independent bytes, deadlines, injection accounting and opt-in 56-pair interpretation. Historical artifact/measurement sections preserved. |
| docs/RECEIPT_SCENARIO.md | `92df2ec87c0d01e6c2fd7c18ff01d857c6f64d8a1ffec63ff8cfa04075f358ca` | Unchanged: finite opaque-ID correlation, early receipt decisions versus actual responses, count/window/three-times-timeout policy, loopback ownership and intentionally failing fault outcomes match inspected sources. |

## Final changed source identities

Only these test sources change. New Java files are the test owner starter and
process cleanup regression; the production implementation is unchanged.

| Source | Final whole-file SHA-256 |
| --- | --- |
| simulator/scripts/tests/test_fault_peer.py | `3ef7f794106cfb8a1cc6a282edb67e6919996c1828aee7c4f62fc99ecdcf5d6b` |
| simulator/src/test/java/kg/aidarbek/simulator/LoadRunTest.java | `242dece4130aa2c702b5f4da550bc225bd2db2a5c20a4a6aff03e7c680b1e567` |
| simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioProcessTest.java | `4b1975929220c704c441a0244ec43b89ada12a93f3a2b650943276a0c60d0a58` |
| simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressProbe.java | `b72b7d41874021aac418ddb77b0831b64a2d1a3515d52dcf6d9c6cef265d238a` |
| simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressTest.java | `3b57eb3cd8366de0217c617d1987378c0b2643e1d63b4dad094146aea67c8488` |
| simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessCleanupTest.java | `1ba13f16385f710fe1085c2f293bad2a5f384e376f1bc69cfaca3fa450eb7717` |
| simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java | `0dad21780cb0c4179aed163ca60b47a02287b7b52824c328c7d681246d4e2b89` |
| simulator/src/test/java/kg/aidarbek/simulator/SimulatorTestOwner.java | `35d442759601a4692a57245090404e9da778fcb9b1ac1b5fbc74cebe38bd1b16` |

The Python change has one cohesive helper for exact malformed-header reading and
one bounded real-EOF regression. It reuses actual socket/subprocess contracts,
keeps parsing expectations independent, and introduces no runtime dependency or
new fake class. Existing optional process matrices remain unchanged. All seven
affected Java files, including both unchanged-in-content Pair records and Gate,
were reread at their post-format hashes; their ten current types follow.

## Whole-type SOLID reviews for corrections

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/LoadRunTest.java
type: kg.aidarbek.simulator.LoadRunTest
sha256: 242dece4130aa2c702b5f4da550bc225bd2db2a5c20a4a6aff03e7c680b1e567
responsibility: Exercises complete simulator role composition, resource baselines, finite churn and reported shutdown.
consumers: JUnit invokes two real endpoint runs; SimulatorTestOwner owns the server thread and this test always joins it.
S: pass | Only complete-run composition and ownership observations drive this fixture; scheduling algorithms and protocol values remain separately tested.
O: pass | Configuration selects the two supported scenarios; replacing production samplers or changing lifecycle policies needs no test-only subclass extension.
L: pass | The server now uses the same platform owner kind as SimulatorMain; actual endpoint and Thread contracts are exercised without permissive mocks. Failure futures and bounded join preserve test ownership.
I: pass | No custom consumer interface is imposed; the single startup helper returns the Thread needed for bounded cleanup.
D: pass | Uses public simulator composition and JDK futures/files rather than hidden endpoint state; only owner creation is shared with the independent contention regression.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioProcessTest.java
type: kg.aidarbek.simulator.ReceiptScenarioProcessTest
sha256: 4b1975929220c704c441a0244ec43b89ada12a93f3a2b650943276a0c60d0a58
responsibility: Verifies the separate receipt executable across real client/server JVMs and finite fault profiles.
consumers: JUnit runs healthy, rejected, malformed-correlation, idle, refusal and fresh-path scenarios; SimulatorProcessCleanupTest calls the same pair-cleanup method.
S: pass | All helpers support one executable boundary and its report assertions; receipt correlation logic is tested independently by the ledger tests.
O: pass | Existing profile/fault parameters extend scenario coverage without altering the process lifecycle contract; the bounded cleanup method is reusable only within tests.
L: pass | Uses real finite child JVMs, actual exits and summaries. Nested finally attempts both process owners even if the first wait is interrupted, and preserves nonzero failing outcomes instead of manufacturing cleanup.
I: pass | Test-only stopBoth exposes exactly the shared cleanup operation needed by the interruption regression; production callers acquire no extra API.
D: pass | ProcessBuilder, explicit entry-point classpath and files supply real collaborators; no fake Process implementation or private endpoint internals are introduced.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioProcessTest.java
type: kg.aidarbek.simulator.ReceiptScenarioProcessTest.Pair
sha256: 4b1975929220c704c441a0244ec43b89ada12a93f3a2b650943276a0c60d0a58
responsibility: Carries one completed receipt pair's exit codes, JSON texts and process logs.
consumers: The parent process test reads immutable values to reconcile role-specific outcomes and explain failures.
S: pass | Only completed pair evidence is stored; the record starts no process and decides no pass criterion.
O: pass | A fixed evidence tuple has no supported polymorphic variation; new observations require an explicit field and consumer change.
L: pass | Record equality/accessors preserve integer and immutable String values, with no mutable process reference or hidden ownership transfer.
I: pass | No custom interface exists; the six accessors correspond to the independently compared role evidence.
D: pass | Uses only scalar/String values, leaving process and report parsing collaborators in the owning fixture.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressProbe.java
type: kg.aidarbek.simulator.SamplingProgressProbe
sha256: b72b7d41874021aac418ddb77b0831b64a2d1a3515d52dcf6d9c6cef265d238a
responsibility: Runs finite one-carrier subprocess experiments for periodic observations and synchronous simulator baselines.
consumers: SamplingProgressTest launches its four scenarios; real ResourceSampler and PressureSampler guards call Gate, while the platform driver verifies progress and reaps every child thread.
S: pass | One concern is observable sampling progress and retirement; scenario selection does not introduce a scheduler or alternate sampler implementation.
O: pass | Controlled observation values and the shared owner starter are purposeful seams; existing periodic worker cases remain unchanged while two baseline cases reuse the same guard experiment.
L: pass | Uses genuine Java 21 virtual/platform Thread and sampler implementations. A legal blocking observation retains the real monitor; the independent virtual releaser must progress. The driver releases latches in finally even on timeout and requires bounded joins.
I: pass | The public main is the subprocess entry; private baseline/require helpers expose no production capability or multipurpose interface.
D: pass | Samplers receive narrow observation/clock functions; the probe uses real management accounting and threads rather than relying on guessed carrier counts or production lock reflection.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressProbe.java
type: kg.aidarbek.simulator.SamplingProgressProbe.Gate
sha256: b72b7d41874021aac418ddb77b0831b64a2d1a3515d52dcf6d9c6cef265d238a
responsibility: Coordinates the second real sampler observation and records the executing thread for progress assertions.
consumers: The probe passes observe as its resource or pressure observation function and releases its latch from a virtual releaser or final platform cleanup.
S: pass | Only controlled observation blocking and thread evidence are retained; no sampler scheduling, output or resource accounting policy is duplicated.
O: pass | The generic identity observation handles both actual sampler snapshot values without a hierarchy or extra policy parameter.
L: pass | It returns the original supplied observation and blocks only at an explicit coordinated point, which is legal for these observation collaborators. Interruption restores the flag and surfaces failure; CountDownLatch/future completion publish recorded evidence.
I: pass | One generic observe operation supplies the needed test seam; fixture state stays private to the probe family.
D: pass | JDK latches and actual ThreadMXBean observations control and inspect the real guard path; it neither impersonates a Process nor substitutes an invalid endpoint contract.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressTest.java
type: kg.aidarbek.simulator.SamplingProgressTest
sha256: 3b57eb3cd8366de0217c617d1987378c0b2643e1d63b4dad094146aea67c8488
responsibility: Owns and verifies four bounded fresh-JVM sampling-progress probes.
consumers: JUnit selects resource/pressure periodic and synchronous-baseline scenarios and checks exit, progress marker and process retirement.
S: pass | Only subprocess execution and observable progress evidence belong here; the probe owns latch sequencing and sampler checks.
O: pass | One scenario argument and marker distinction extend the finite matrix without changing child ownership or creating alternative scheduling policy.
L: pass | Real JVMs run with exactly one configured carrier and a fixed heap; all executions have finite waits and forced reaping. Tests require successful process status and the correct scenario evidence, not merely absence of an exception.
I: pass | No custom interface is present; shared private check and location methods serve only the child execution contract.
D: pass | Uses the actual runtime entry points and code-source locations, including public library and HDR dependencies, rather than loading internal implementations into the parent test.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessCleanupTest.java
type: kg.aidarbek.simulator.SimulatorProcessCleanupTest
sha256: 1ba13f16385f710fe1085c2f293bad2a5f384e376f1bc69cfaca3fa450eb7717
responsibility: Proves that interruption of the first cleanup still attempts and reaps both real process owners.
consumers: JUnit applies the same independently observed child-liveness contract to receipt and traffic process fixtures.
S: pass | Only two-owner cleanup is asserted; existing WaitingChild supplies finite actual JVM behavior without testing protocol/report logic again.
O: pass | The small named fixture matrix selects the two existing cleanup contracts; no general lifecycle framework or process subclass is added.
L: pass | Real child JVMs announce readiness and wait finitely; no permissive Process double can hide missed cleanup. The parent clears only its deliberately injected interrupt, forcibly retires both children in its own nested finally, and rejects an alive second child.
I: pass | No custom interface or public production method is introduced; only the two test cleanup entry points are called.
D: pass | Depends on ProcessBuilder/Process and a shared immutable child entry-point name, not endpoint implementation details or arbitrary sleeps as the success oracle.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java
type: kg.aidarbek.simulator.SimulatorProcessTest
sha256: 0dad21780cb0c4179aed163ca60b47a02287b7b52824c328c7d681246d4e2b89
responsibility: Verifies the installed-style simulator CLI, paired process lifecycle and reports for message/common/broadcast scenarios.
consumers: JUnit executes role/profile/fault cases; SimulatorProcessCleanupTest invokes its shared pair cleanup to verify interrupted shutdown.
S: pass | Configuration, network process boundary and resulting reports form one integration fixture concern; protocol construction and counter algorithms remain in focused tests.
O: pass | Existing option/profile lists add supported scenarios without changing process ownership. Both pair paths now use one bounded cleanup helper.
L: pass | Real processes, finite readiness/completion bounds and expected report counts support substitution. Nested finally attempts the server after interrupted client cleanup; stop now also asserts the bounded reap actually completed.
I: pass | The package-private test cleanup method exposes only a two-owner operation; Pair offers only immutable evidence accessors.
D: pass | Uses public launcher/library locations and real subprocesses, not internal request engines or synthetic success callbacks. Development credentials are supplied through the child environment.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java
type: kg.aidarbek.simulator.SimulatorProcessTest.Pair
sha256: 0dad21780cb0c4179aed163ca60b47a02287b7b52824c328c7d681246d4e2b89
responsibility: Carries completed client and server report text for scenario assertions.
consumers: The parent process fixture compares explicit outcomes and receiver observations across the two role reports.
S: pass | Stores evidence only; it neither parses JSON nor owns process lifecycle.
O: pass | No supported extension exists for this two-value result; additional evidence would be a deliberate record contract change.
L: pass | Generated value semantics preserve the two immutable Strings; no mutable process, reader or array reference escapes.
I: pass | No custom interface exists; its two accessors are the exact compared role evidence.
D: pass | Only String values are stored, independent of networking or executable selection.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorTestOwner.java
type: kg.aidarbek.simulator.SimulatorTestOwner
sha256: 35d442759601a4692a57245090404e9da778fcb9b1ac1b5fbc74cebe38bd1b16
responsibility: Starts a complete simulator test run on the same platform owner kind as the standalone CLI.
consumers: LoadRunTest and the synchronous-baseline probe receive the started Thread and retain responsibility for bounded interruption/join.
S: pass | Only complete-run owner creation belongs here; periodic sampler workers and library virtual threads retain their existing owners.
O: pass | The launcher owner kind is a fixed contract, not a configurable execution policy; a new executor hierarchy would add unsupported variation.
L: pass | Returns an actual started platform Thread executing the supplied Runnable under normal JDK null/failure semantics. It does not claim to own cleanup or return a substitute that hides physical liveness.
I: pass | A single start operation returns precisely the Thread needed by consumers; there is no custom executor interface.
D: pass | Depends only on Runnable and Thread; it introduces no library, filesystem or report dependency.
findings: none
```
