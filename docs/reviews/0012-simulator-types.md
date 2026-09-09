# Step 13 simulator type reviews

The following 65 current type identities were reviewed against the final formatted
source. [The change report](0012-simulators.md) records TDD, corrections, packaging
and fresh measurement evidence. Whole-file hashes cover all nested/test types.

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ArrivalSchedule.java
type: kg.aidarbek.simulator.ArrivalSchedule
sha256: 6c5b29cc5c5e3345ad00a4f5d37f4e88d792f627ed0c7263d00b5fa592a8b0cd
responsibility: Owns the finite intended arrival sequence for one rate phase, including skipped arrivals when its caller falls behind.
consumers: TrafficRunner polls due work and reads the next deadline and final skipped count; ArrivalScheduleTest supplies controlled monotonic times.
S: pass | Rate arithmetic, cursor advancement and original planned times belong to one scheduling policy; message creation, request tracking and reporting remain outside it.
O: pass | LoadPlan supplies bounded rate steps and count limits, so changing a workload does not change this cursor algorithm or endpoint code.
L: pass | A single owner gets at most the newest due arrival, never an early fractional arrival or a catch-up burst; finish is idempotent and subtraction survives signed nanoTime wrap. ArrivalScheduleTest verifies those contracts.
I: pass | TrafficRunner needs only poll, nextNanos, plannedCount and finish; no socket, callback or metric methods are imposed on the schedule.
D: pass | The schedule uses immutable Java time/list inputs and validated LoadPlan values, with no runtime clock, executor or endpoint dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ArrivalSchedule.java
type: kg.aidarbek.simulator.ArrivalSchedule.Arrival
sha256: 6c5b29cc5c5e3345ad00a4f5d37f4e88d792f627ed0c7263d00b5fa592a8b0cd
responsibility: Carries one selected arrival's index, original planned monotonic time and immediately preceding skipped count.
consumers: ArrivalSchedule constructs it; TrafficRunner uses its three values for issue selection and accounting.
S: pass | All three scalars describe a single scheduling observation; the record neither advances the schedule nor classifies request outcomes.
O: pass | Different rates produce the same observation shape; supporting another operation or content plan does not extend this value.
L: pass | Generated record equality and hashCode operate on immutable scalar values, including signed monotonic timestamps; ArrivalScheduleTest compares independently calculated records.
I: pass | The runner consumes the three accessors together, and no mutable cursor or lifecycle capability is exposed.
D: pass | The value depends only on Java record/scalar contracts and cannot construct timing or network infrastructure.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/CohortMetrics.java
type: kg.aidarbek.simulator.CohortMetrics
sha256: 4284f8756e11c2675d4f5f71e5a6258c7664c721f89650d557f23b1571061693
responsibility: Maintains bounded conservation counters and latency populations for one warmup or measurement cohort.
consumers: TrafficRunner records planned, skipped, rejected, admitted and terminal events; RunCriteria and RunReport consume immutable snapshots.
S: pass | Its counters, state checks and three histograms answer one requirement: account for the same cohort without mixing phases or inventing terminal samples.
O: pass | Operations supply the existing outcome/status data through RequestObservation, while histogram representation remains isolated in Latencies; adding an operation does not change conservation equations.
L: pass | Synchronized transitions reject impossible counts, retain at most 256 distinct peer statuses, bound rejection categories, and omit UNFINISHED from terminal latency samples. CohortMetricsTest verifies conservation, immutable snapshots, overflow and invalid transitions.
I: pass | Mutation methods serve the runner; Snapshot supplies the separate read capability required by criteria and reporting, without exposing mutable maps or histograms.
D: pass | This accounting model depends on local measurement values and Latencies, not endpoint internals, sockets, clocks or report-file ownership.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/CohortMetrics.java
type: kg.aidarbek.simulator.CohortMetrics.Outcome
sha256: 4284f8756e11c2675d4f5f71e5a6258c7664c721f89650d557f23b1571061693
responsibility: Names the finite terminal accounting categories, keeping peer responses, local failures and unfinished work distinct.
consumers: RequestObservation selects a category; TrafficRunner, CohortMetrics and RunCriteria count or evaluate it.
S: pass | The enum owns only classification vocabulary and has no retry, transport or reporting behavior.
O: pass | A genuinely new accounting category belongs here and in explicit classifier/criterion decisions; operation-specific statuses remain data rather than new enum constants.
L: pass | Enum identity and names are stable values; consumers use equality and exhaustive classification rather than ordinal ordering. Mixed-outcome tests verify that UNFINISHED is distinct from timeout and success.
I: pass | Consumers need only named categories; no category is required to implement meaningless request or resource methods.
D: pass | The enum has no infrastructure or implementation dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/CohortMetrics.java
type: kg.aidarbek.simulator.CohortMetrics.Snapshot
sha256: 4284f8756e11c2675d4f5f71e5a6258c7664c721f89650d557f23b1571061693
responsibility: Publishes a cohort's counters, category totals and immutable latency summaries, with an explicit conservation check.
consumers: RunCriteria evaluates balanced accounting; RunReport serializes the same snapshot; tests inspect counts and ownership.
S: pass | Its accessors and balanced method describe one accounting snapshot, without modifying the live collector or deciding pass/fail thresholds.
O: pass | Additional operation statuses fit its maps; policy thresholds remain in RunCriteria and JSON encoding remains in Json.
L: pass | Map.copyOf prevents mutation of outcomes, rejections and statuses, and nested Latencies snapshots own their lists. Record equality is structural; balanced checks planned=skipped+attempted and admitted=pending+terminal totals, as exercised by CohortMetricsTest.
I: pass | Readers receive values and a conservation query, with no mutation or resource lifecycle methods.
D: pass | It depends only on Java collections, the accounting enum and local histogram snapshots.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ContentPlan.java
type: kg.aidarbek.simulator.ContentPlan
sha256: a59b0ed3f3fdd52e831a75c79638d8124298bf76bc2aa77eb52cc63664b9b6c3
responsibility: Defines bounded deterministic content generation and receive-side validation for one configured traffic variant.
consumers: SimulatorRun requests outgoing bodies; ReplyController owns one implementation per retained connection stream; RawContent implements the initial variant.
S: pass | Generation, validation and incomplete-assembly reporting form the contract of one content scenario, independently of its operation and transport.
O: pass | New payload or receipt scenarios implement this interface without changing ArrivalSchedule, TrafficRunner or report accounting.
L: pass | Implementations must retain bounded state, return TrafficContent values and reject unsupported received content explicitly. RawContentTest checks deterministic generation and shape rejection; the default zero incomplete count accurately serves stateless variants.
I: pass | The two content operations and optional incomplete count are the exact needs of the sender and stream validator; no bind, wire correlation or scheduler methods are required.
D: pass | Consumers depend on this scenario contract and a Supplier for stream creation, rather than a specific encoder or receiver implementation.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/DecisionQueue.java
type: kg.aidarbek.simulator.DecisionQueue
sha256: 84f51f4f124f197184a2bfee715136b66e31c38561139ab8b9d8a74390f5591e
responsibility: Owns finite delayed or deliberately stalled handler decisions until physical completion or cancellation releases their retained slots.
consumers: ReplyController admits decisions, the simulator owner advances time, and shutdown closes admission and cancels waiting futures.
S: pass | Admission, due selection, retention accounting and close all govern the same bounded decision lifecycle; fault selection and network writing remain separate.
O: pass | Generic result values and explicit deadlines support different reply operations; FaultPolicy selects delay or stall without changing the queue.
L: pass | Admission and close are synchronized, callbacks run outside the queue monitor, and claimed entries retain capacity until completion returns. Close is idempotent and rejects later admission; caller-cancelled futures remain counted until owner advancement. DecisionQueueTest covers bounds, deadlines, signed wrap and cancellation.
I: pass | ReplyController requires only defer, advance, pending and close; there is no scheduler/executor API or request-correlation capability.
D: pass | The queue depends on Java futures and caller-provided monotonic times; it creates no threads, sockets or autonomous scheduling backlog.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/DecisionQueue.java
type: kg.aidarbek.simulator.DecisionQueue.Deferred
sha256: 84f51f4f124f197184a2bfee715136b66e31c38561139ab8b9d8a74390f5591e
responsibility: Holds one retained result, due time, stall flag and completion future for DecisionQueue.
consumers: Only DecisionQueue constructs, selects and completes these private entries.
S: pass | All fields describe the one deferred completion; complete delegates settlement without making fault or admission decisions.
O: pass | Its generic value supports existing reply types without operation-specific branches or new entry subclasses.
L: pass | The future is deliberately a shared mutable completion identity, not deep immutable value data; the private record is not used as a map key. CompletableFuture's idempotent settlement preserves cancellation, as DecisionQueueTest exercises.
I: pass | The enclosing queue needs the entry accessors and complete only; no general task or executor interface is introduced.
D: pass | The entry depends only on its generic result and Java CompletableFuture, leaving timing and capacity policy to the queue.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/FaultPolicy.java
type: kg.aidarbek.simulator.FaultPolicy
sha256: 32f0ae3a9c972a2ec9f55587fdfda6650b482ef6dc14ea67d669313e036adede
responsibility: Stores validated fault proportions and selects a deterministic application decision from a seed and request identity.
consumers: SimulatorArguments creates the policy; ReplyController selects outcomes and reads delay, status and disconnect thresholds; reports preserve its inputs.
S: pass | The record owns the configured receiver fault policy, while queues, session closure and measurements execute its decisions elsewhere.
O: pass | Ordinary workload changes are immutable configuration changes; a new fault kind belongs in this policy and ReplyController's explicit execution switch, not the scheduler or library.
L: pass | Percentages form disjoint buckets within 0..100, durations are finite, and rejection status is a nonzero uint32. Stateless selection is independent of call order; FaultPolicyTest exhaustively checks all buckets and reverses identity traversal.
I: pass | The receiver reads concrete policy values and a decision; it need not implement or inspect a generic fault engine.
D: pass | The policy depends only on primitive/time values and LoadPlan's shared duration validation, not endpoint resources or random global state.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/FaultPolicy.java
type: kg.aidarbek.simulator.FaultPolicy.Decision
sha256: 32f0ae3a9c972a2ec9f55587fdfda6650b482ef6dc14ea67d669313e036adede
responsibility: Names the four supported receiver response decisions: accept, reject, delay and stall.
consumers: FaultPolicy returns it and ReplyController executes an exhaustive decision switch.
S: pass | The enum describes a decision without owning timers, queues, response bodies or session closure.
O: pass | A new response behavior requires an explicit domain addition and matching controller execution; variable percentages remain FaultPolicy data.
L: pass | Enum identity is immutable and no ordinal-based protocol mapping is used. FaultPolicyTest checks all 100 selection buckets against the declared categories.
I: pass | Consumers only need named choices; no default methods pretend that every choice can write, cancel or schedule work.
D: pass | The enum has no dependency on infrastructure or library internals.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/Json.java
type: kg.aidarbek.simulator.Json
sha256: ad5a3e9deca15b04bba395c2afad66a2fbe71689f1b1a5f65bd9ff23837aca98
responsibility: Encodes the simulator's bounded report value graph into JSON without serializing arbitrary diagnostics.
consumers: ReportWriter serializes the report maps and local snapshot records; ReportWriterTest verifies escaping and rejected value kinds.
S: pass | String escaping, supported value projection and JSON syntax are one serialization responsibility, distinct from metric collection and file lifecycle.
O: pass | New report fields compose existing scalar, enum, list, map and record shapes; an unsupported value kind is added here explicitly rather than through arbitrary object toString output.
L: pass | It returns an immutable string, escapes controls and surrogate code units, rejects nonfinite or unsupported numeric/object values, and surfaces inaccessible record access. Its package-private callers provide bounded acyclic graphs; tests cover exact output and failure cases.
I: pass | The sole encode operation is sufficient for the report writer; no streaming resource ownership or mutable serializer configuration leaks to consumers.
D: pass | Encoding uses Java reflection and collection contracts only, with no endpoint, logging framework or external JSON dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/Latencies.java
type: kg.aidarbek.simulator.Latencies
sha256: 08ab7984604b95e10d0de602f74ce8512c0bb28c8fe8b6184c416310bd3da945
responsibility: Collects fixed-range latency samples in a bounded HDR histogram with explicit overflow and maximum tracking.
consumers: CohortMetrics owns invocation, scheduled and lag collectors; reports consume immutable snapshots; CohortMetricsTest supplies known sample populations.
S: pass | Unit conversion, histogram recording and distribution snapshots all serve the same measurement representation; cohort membership is decided outside this type.
O: pass | Additional measurement populations instantiate the same collector; changing representation remains local to this tool type and does not affect library requests.
L: pass | Synchronized recording rejects negative durations, rounds nanoseconds upward to microseconds, counts values above one hour separately and excludes overflow from percentiles. Tests verify counts, percentile ranges, maximum and mergeable bucket totals.
I: pass | Collectors expose only record and snapshot, while readers receive plain values rather than the mutable HDR Histogram.
D: pass | HdrHistogram is confined to this simulator measurement adapter; the runtime library neither imports it nor exposes its types.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/Latencies.java
type: kg.aidarbek.simulator.Latencies.Bucket
sha256: 08ab7984604b95e10d0de602f74ce8512c0bb28c8fe8b6184c416310bd3da945
responsibility: Carries one HDR bucket's upper microsecond value and sample count for report aggregation.
consumers: Latencies creates buckets; JSON reporting and tests inspect or sum them.
S: pass | The two scalars describe one histogram bucket without collecting samples or calculating percentiles.
O: pass | More populations reuse this representation; aggregation or visualization belongs to consumers rather than bucket subclasses.
L: pass | Generated equality and hashCode use immutable long values; buckets are produced from the histogram's recorded values and tests independently sum the expected population.
I: pass | Readers need exactly the upperMicros and count accessors, with no mutable histogram API.
D: pass | The record depends only on Java scalars, so exported measurements do not expose the third-party histogram implementation.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/Latencies.java
type: kg.aidarbek.simulator.Latencies.Snapshot
sha256: 08ab7984604b95e10d0de602f74ce8512c0bb28c8fe8b6184c416310bd3da945
responsibility: Publishes one latency population's count, overflow, maximum, percentiles and mergeable buckets.
consumers: CohortMetrics.Snapshot, RunCriteria and JSON reporting consume it.
S: pass | All fields describe one sampled distribution; this value does not choose cohorts, thresholds or resource ownership.
O: pass | Different latency populations share this immutable schema; threshold changes stay in RunCriteria and new output formats consume the same values.
L: pass | List.copyOf owns the bucket list; immutable bucket records and scalars provide structural record equality. Count excludes overflow and maximum retains it, matching CohortMetricsTest's known samples.
I: pass | Consumers receive distribution accessors without mutable collector or HDR methods.
D: pass | The snapshot depends on local immutable Bucket values and Java lists only.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/LoadPlan.java
type: kg.aidarbek.simulator.LoadPlan
sha256: 5384b25891a632979b7b1412d1edc28e87cf71287d571fbf850154f8f21a118d
responsibility: Validates and owns the finite generating model, rate steps, count and phase/deadline durations.
consumers: SimulatorArguments constructs it; ArrivalSchedule, TrafficRunner, endpoint composition and reporting consume its values.
S: pass | Its validation and fields define one executable traffic plan; networking, fault policy and output selection are separate configuration concerns.
O: pass | Supported rates, counts and durations vary as data, while a new generating model requires an explicit enum/runner change without altering message codecs.
L: pass | List.copyOf isolates rates, all counts and durations have finite bounds, model/rate contradictions are rejected, and arrival warmup or rate steps shorter than one millisecond fail preflight. LoadPlanTest checks ownership and the previously unexecutable warmup.
I: pass | Consumers read only immutable plan values; the record exposes no scheduler cursor, request handle or lifecycle controls.
D: pass | The plan depends only on Java time, collections and null checks, so validation runs before infrastructure allocation.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/LoadPlan.java
type: kg.aidarbek.simulator.LoadPlan.Model
sha256: 5384b25891a632979b7b1412d1edc28e87cf71287d571fbf850154f8f21a118d
responsibility: Names the two supported generating policies: independent arrival rate and fixed concurrency.
consumers: CLI parsing, LoadPlan validation, TrafficRunner and report output use the selection.
S: pass | The enum only identifies a generation model, leaving scheduling and pending-call ownership to their concrete owners.
O: pass | Adding a genuinely different model is an explicit domain extension; workload rate/count variation does not require new model types.
L: pass | Immutable enum identity is used directly, never serialized as an ordinal; validation and runner tests cover each model's distinct rate-input and refill contracts.
I: pass | Each consumer needs the selection alone, with no artificial common scheduler methods.
D: pass | The enum has no infrastructure dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/MessageTraffic.java
type: kg.aidarbek.simulator.MessageTraffic
sha256: 3e66490591c90d9718207b95a1f05ae5124940c6fac3cde73bf700a6336a4bd3
responsibility: Adapts the initial submit, deliver and data operations between public endpoint capabilities and operation-independent TrafficContent.
consumers: SimulatorRun discovers outgoing operations and registers handlers; ReplyController validates received content; MessageTrafficTest and SimulatorExchangeTest exercise the adapters.
S: pass | Builders, capability preflight and body extraction belong to one operation-adapter boundary; pacing, fault decisions and request correlation remain elsewhere.
O: pass | The TrafficOperation and ContentPlan seams let operation or content scenarios extend independently; public MessageOperations registrations avoid changes to scheduler/accounting code.
L: pass | Role/version checks reject impossible sends before networking; payload placement is unique and uses the common 254-byte short-field boundary. Extraction rejects ambiguous carriers and passes a supplier into counted validation, preserving valid-wire rejection metrics. Unit and raw-peer regressions verify these outcomes.
I: pass | Each operation uses only its typed submission, delivery or data capability and handler registration; no monolithic session implementation or request-window API is required.
D: pass | Dependencies point to public endpoint/protocol values and RequestOptions; wire encoding, sequences, timeouts and transport remain library-owned.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/PendingCall.java
type: kg.aidarbek.simulator.PendingCall
sha256: 4f25cb49d5934574d4bfefae8dce179cff4134c0920d6205d6a84418dbe235ff
responsibility: Provides the traffic runner with terminal observation, transmission certainty and cancellation of one admitted library request.
consumers: TrafficRunner consumes it; RequestObservation adapts real RequestHandle values and focused test fixtures simulate controlled outcomes.
S: pass | The three operations describe observation and cancellation of one call, without becoming a second request engine.
O: pass | Different public request types share this observation port; deterministic tests substitute it without constructing sockets or allocating sequences.
L: pass | poll reports absence while pending and a classified value when terminal; certainty is observed separately and cancellation requests settlement without promising immediate physical cleanup. Real and fixture implementations preserve this narrow contract.
I: pass | The runner needs no CompletionStage mutation, command encoding, response routing or endpoint lifecycle methods.
D: pass | The scheduling policy depends on this port and injected functions; the concrete public-library adapter is outside the runner.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/PendingCall.java
type: kg.aidarbek.simulator.PendingCall.Completion
sha256: 4f25cb49d5934574d4bfefae8dce179cff4134c0920d6205d6a84418dbe235ff
responsibility: Carries the terminal accounting category and preserved peer status, or the local-failure sentinel.
consumers: RequestObservation and controlled PendingCall fixtures construct it; TrafficRunner records it in CohortMetrics.
S: pass | Both fields describe one observed terminal outcome without selecting latency populations or settling requests.
O: pass | Operation-specific status values remain data; new outcome semantics belong to the explicit accounting enum and adapter.
L: pass | The immutable enum/long record has structural equality; adapters retain actual peer statuses and use -1 for outcomes without a peer status, without fabricating protocol responses.
I: pass | TrafficRunner requires both accessors and no request mutation or throwable serialization capability.
D: pass | The record depends only on CohortMetrics.Outcome and a scalar status.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RawContent.java
type: kg.aidarbek.simulator.RawContent
sha256: 61c92d1b81130b1407aa70331dce0d29f80fb5bca141e1b19972248f643eec4d
responsibility: Generates bounded deterministic binary octets and validates the configured raw-content shape without inferring a text alphabet.
consumers: SimulatorRun requests bodies by planned identity and ReplyController uses one ContentPlan per stream; RawContentTest verifies generation, metadata and bounds.
S: pass | Generation and shape validation describe one raw scenario; operation-specific payload placement, wire encoding and request tracking remain separate.
O: pass | The ContentPlan interface supports other text, segmented or receipt variants without adding branches to this binary implementation.
L: pass | Every long identity deterministically derives a fresh immutable body from seed and configured length in 0..65535. The implementation explicitly emits and requires esm_class 0 and data_coding 4 with no extra tags; receive validation checks shape, not seed-derived byte equality. Tests verify the DCS correction, repeatability, distinct identities and invalid lengths.
I: pass | The implementation supplies only generation/validation and correctly inherits zero incomplete assemblies for a stateless scenario; it requires no endpoint or scheduler callbacks.
D: pass | Dependencies are the ContentPlan boundary, public immutable protocol values and a locally seeded Java random generator, with no global random state or transport infrastructure.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReplyController.java
type: kg.aidarbek.simulator.ReplyController
sha256: f49bdaa992ae838770474fb3b927e39e1593f668378156cee04e4be0bc784d6b
responsibility: Applies bounded receive-side content checks and configured faults while accounting for requests entering application handlers.
consumers: MessageTraffic supplies request/body adapters; SimulatorRun advances decisions and closes the controller; RunReport and RunCriteria consume its snapshot.
S: pass | Validation, per-stream receive identity and selected reply policy form one simulator receiver responsibility; typed body construction, delay storage and endpoint cleanup have separate owners.
O: pass | A Supplier<ContentPlan> creates bounded stream-local variants, and generic success/negative bodies support operation adapters without editing generation or metric code.
L: pass | Synchronized counters and retained stream slots are bounded by configured connections, including retired streams. Content extraction now occurs inside counted validation; disconnect and future completion occur outside the controller monitor. Real endpoint regressions verify rejection counts, invalid-body counts and cleanup.
I: pass | Handlers need reply only; the owner needs advance/close and reporting needs snapshot. No request-window, write-observer or scheduler implementation is exposed.
D: pass | The controller consumes public IncomingRequest/HandlerResponse capabilities, ContentPlan and a local bounded DecisionQueue; protocol tracking and physical transport stay in the library.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReplyController.java
type: kg.aidarbek.simulator.ReplyController.Snapshot
sha256: f49bdaa992ae838770474fb3b927e39e1593f668378156cee04e4be0bc784d6b
responsibility: Publishes received-request categories and current retained receiver work as immutable scalar observations.
consumers: RunReport serializes it, RunCriteria consumes invalid-content and incomplete-assembly counts, and receiver tests assert known outcomes.
S: pass | Every component describes one receiver accounting snapshot; it does not reinterpret observations as peer receipt or handset delivery.
O: pass | New operation handlers reuse the same receiver metrics; configured fault proportions vary independently of this value.
L: pass | Generated record equality uses immutable scalar values. Counts cover handlers entering before controller closure, selected policy decisions and separately reported capacity rejection; they do not claim a wire-wide disjoint conservation equation.
I: pass | Consumers receive only metric accessors, not stream maps, mutable validators or deferred futures.
D: pass | The record contains no infrastructure or library implementation dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReplyController.java
type: kg.aidarbek.simulator.ReplyController.Stream
sha256: f49bdaa992ae838770474fb3b927e39e1593f668378156cee04e4be0bc784d6b
responsibility: Retains the content validator and received count for one connection generation.
consumers: ReplyController owns each private Stream in its bounded UUID-keyed map and queries the content plan for incomplete assemblies.
S: pass | The two fields describe per-stream receiver state; global faults, scheduling and snapshots remain with the enclosing controller.
O: pass | Different payload scenarios supply ContentPlan implementations without modifying this holder or storing scenario-specific fields here.
L: pass | The ContentPlan reference is explicitly stateful and exclusively used under the controller monitor; the private count is not exposed. Stream retention is bounded by configured connections, and the holder makes no immutable-value equality promise.
I: pass | Only the enclosing controller reads or updates the two fields, so no public stream lifecycle or generic session API is needed.
D: pass | Its variable behavior is supplied through ContentPlan rather than a concrete payload helper or network implementation.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReportWriter.java
type: kg.aidarbek.simulator.ReportWriter
sha256: e62e07407420cda7227295824748ecd6d3a37b32e0fcd229aa4d25bc2bb67446
responsibility: Owns fresh report files and streams the numeric resource series for one simulator run.
consumers: ResourceSampler writes samples, SimulatorRun writes the final report, and try-with-resources closes the writer.
S: pass | Directory/file creation, CSV writes, final JSON output and stream cleanup belong to one report persistence lifecycle; data collection and schema construction are separate.
O: pass | Report schema changes arrive as data from RunReport and serialization remains in Json, so workload or operation additions do not change file ownership.
L: pass | CREATE_NEW prevents overwriting prior output, construction failure closes an opened writer and preserves suppressed cleanup errors, and close follows AutoCloseable's IOException contract. ReportWriterTest verifies exact streamed rows, JSON data and overwrite refusal.
I: pass | The owner needs sample, finish and close; no filesystem management, scheduler or endpoint API is exposed.
D: pass | This concrete filesystem adapter uses Java I/O and Json at the application boundary; accounting and request policy do not depend on paths or writers.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RequestObservation.java
type: kg.aidarbek.simulator.RequestObservation
sha256: 029db4ddac1a433865f59808c74590a3438828ddc2ac79f3d172899145b87102
responsibility: Adapts one public RequestHandle's atomic terminal state into the simulator's narrow PendingCall contract.
consumers: SimulatorRun creates adapters and TrafficRunner polls, checks certainty and requests cancellation.
S: pass | Mapping a library result to accounting data is its sole responsibility; it neither reserves pending capacity nor allocates sequences or deadlines.
O: pass | Any typed library request can use the same wildcard handle adapter; adding an operation does not add a second classifier or completion queue.
L: pass | poll remains independent of application CompletionStage callbacks, preserves nonzero command-specific peer responses and generic_nack statuses, and maps deadline/cancellation/local failure distinctly. Certainty and cancellation delegate to the handle; real rejection, timeout and disconnect process tests exercise the mapping.
I: pass | The adapter implements only poll, mayHaveBeenSent and cancel required by TrafficRunner, with no endpoint ownership or wire methods.
D: pass | It depends exclusively on public request outcome/handle/failure contracts; SimulatorArchitectureTest rejects direct RequestWindow ownership from production simulator code.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ResourceSampler.java
type: kg.aidarbek.simulator.ResourceSampler
sha256: 66bc7ebf69980528210b718d515aa1804fe3ebf4efbb9115e77509da0cfcd8b3
responsibility: Samples and streams process resource observations with bounded summary state and a one-second periodic cadence.
consumers: SimulatorRun invokes tick/sample and reads Summary; tests inject a clock and observation supplier.
S: pass | Sampling cadence, first/latest observations and sampled peaks are one resource-series responsibility; platform probing and file lifecycle remain separate collaborators.
O: pass | Clock and observation Supplier vary independently for controlled tests or alternative observations without changing workload generation.
L: pass | The single owner retains only initial/latest samples and scalar peaks; signed monotonic differences gate ticks. Unknown descriptor readings remain -1 until a known sample, and I/O failures surface as UncheckedIOException. ResourceSamplerTest verifies missing-to-known-to-missing transitions.
I: pass | The run needs tick, final sample and summary; platform-specific fields and file-close controls are not added to the sampling API.
D: pass | Variable time and observations are injected; RunEnvironment and ReportWriter are concrete tool adapters wired at the outer constructor, not dependencies of the scheduler policy.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ResourceSampler.java
type: kg.aidarbek.simulator.ResourceSampler.Summary
sha256: 66bc7ebf69980528210b718d515aa1804fe3ebf4efbb9115e77509da0cfcd8b3
responsibility: Carries the initial/latest resource readings and the peaks observed by periodic sampling.
consumers: RunReport serializes it and ResourceSamplerTest checks descriptor-peak semantics.
S: pass | The fields summarize one sampled series and do not claim continuous maxima or collect platform measurements themselves.
O: pass | Additional sample sources can produce the same RunEnvironment.Snapshot values; reporting remains separate from summary construction.
L: pass | Nested snapshots and scalars are immutable, preserving record equality. The descriptor peak can remain -1 when all readings are unavailable; it does not fabricate zero usage.
I: pass | Readers need only the four accessors, with no sampler mutation, clock or writer capability.
D: pass | The record depends only on the local immutable observation record and scalar values.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunCriteria.java
type: kg.aidarbek.simulator.RunCriteria
sha256: 00e78031dd34520fca18fa1eabc1b4d81011a78a1b21cf2d232cc47ab1fed3fb
responsibility: Evaluates declared acceptance thresholds and unconditional accounting, content and cleanup requirements.
consumers: SimulatorRun combines its Verdict with execution failures; RunCriteriaTest supplies controlled cohort snapshots and configurations.
S: pass | All checks answer whether the reported run meets its declared criteria; this type neither drives work nor edits measurements.
O: pass | Thresholds and expected-fault allowance are configuration data; operation-specific status classification stays in RequestObservation and receiver validation stays in ContentPlan.
L: pass | It preserves execution-abort reasons, rejects unstarted measurement, unbalanced or unfinished work, invalid content and incomplete cleanup even when faults are expected. Throughput uses measurement-interval successes and latency uses scheduled samples including overflow; tests exercise these distinctions.
I: pass | The evaluator consumes snapshots and explicit flags, without requiring mutable metrics, session objects or resource-closing methods.
D: pass | It depends on immutable local report inputs and Java collections, not transport, wall-clock time or filesystem state.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunCriteria.java
type: kg.aidarbek.simulator.RunCriteria.Verdict
sha256: 00e78031dd34520fca18fa1eabc1b4d81011a78a1b21cf2d232cc47ab1fed3fb
responsibility: Publishes immutable failure reasons and whether the criterion evaluation passed.
consumers: SimulatorRun and tests inspect passed and failures, and the report retains the reasons.
S: pass | Failure-list ownership and the empty-list pass predicate describe one evaluation result.
O: pass | New criteria contribute reason strings through the evaluator; the result shape needs no criterion-specific subclasses.
L: pass | List.copyOf prevents callers from changing the verdict after evaluation, and passed is true exactly when that owned list is empty. Record equality reflects immutable reason values.
I: pass | The two read operations cover the run owner's needs and expose no metric mutation or rerun capability.
D: pass | The record depends only on Java lists and strings.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunEnvironment.java
type: kg.aidarbek.simulator.RunEnvironment
sha256: 63df8d337290287450fc50552500e114ce8e244dde82a3de25e050306d9deae0
responsibility: Collects reproducibility metadata, executable fingerprints and optional process resource observations for the simulator.
consumers: SimulatorRun requests the initial environment description and ResourceSampler uses sample; process tests inspect the resulting report.
S: pass | All methods establish what executable/environment ran and what resources it observed; workload scheduling and report formatting remain separate.
O: pass | Additional optional platform probes belong to this observation adapter; existing metrics and scheduling policies consume snapshots without platform branches.
L: pass | Executable hashing closes directory/file streams and propagates fingerprint failures; runtime argument reporting whitelists heap/collector settings rather than secrets. Optional Linux descriptors/RSS and CPU use explicit -1 when unavailable, and snapshots retain their observed units.
I: pass | Consumers need describe or sample, not direct filesystem handles or management-bean implementations.
D: pass | Concrete management, process, filesystem and digest APIs are confined to this outer tool adapter; HdrHistogram and SmppClient are referenced only to fingerprint the executing artifacts.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunEnvironment.java
type: kg.aidarbek.simulator.RunEnvironment.Snapshot
sha256: 63df8d337290287450fc50552500e114ce8e244dde82a3de25e050306d9deae0
responsibility: Carries one resource observation with explicit byte, nanosecond, millisecond, descriptor and platform-thread units.
consumers: RunEnvironment creates it; ResourceSampler retains initial/latest readings and ReportWriter/RunReport export selected values.
S: pass | Every scalar belongs to one process-resource observation, without deciding cadence, peaks or success criteria.
O: pass | Optional platform observations fit the established missing-value convention, and consumers do not need platform-specific subclasses.
L: pass | Generated record equality is immutable scalar equality; -1 preserves unavailable readings and platformThreads does not purport to count virtual threads. ResourceSamplerTest uses explicit unknown samples.
I: pass | Readers receive only observation accessors; no live management objects or collection controls escape.
D: pass | The value has no platform or measurement-library dependency despite describing observations taken by concrete adapters.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunReport.java
type: kg.aidarbek.simulator.RunReport
sha256: 9cab9ed4ce4744b688dc63a714a510c741259e3babcdf956bb42cc180f343dec
responsibility: Assembles the explicit machine-readable report schema from configuration, outcomes, resource snapshots and failure reasons.
consumers: SimulatorRun supplies observations; ReportWriter and Json persist the returned map; process tests inspect important contract fields.
S: pass | Field selection, units, population descriptions and safety of reported values are one schema responsibility, distinct from collection and serialization.
O: pass | New scenarios add report data at this projection boundary while scheduling, library correlation and file lifecycle remain unchanged.
L: pass | The report labels invocation versus planned latency, excluded unfinished samples, selected receiver population and absent write-completion measurement. It copies failure reasons, includes exact supplied revision/executable hashes and excludes credentials and arbitrary endpoint diagnostics; process tests verify cleanup and secret exclusion.
I: pass | The writer receives a report value graph, not live endpoint or metric collectors; criteria are supplied rather than recomputed here.
D: pass | Dependencies are local immutable result/configuration types and Java maps/time values, with no network or external serialization framework.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorArguments.java
type: kg.aidarbek.simulator.SimulatorArguments
sha256: fa50ea6e7368251a60f564bc922f361e2813b4a4704d4dcfb1d37efcc0f24b0d
responsibility: Parses explicit command-line options into a validated non-secret simulator configuration before side effects.
consumers: SimulatorMain calls parse and SimulatorArgumentsTest verifies units, defaults, modes and rejected inputs.
S: pass | Option syntax, duplicate/unknown detection and conversion to configuration values are one CLI boundary responsibility; no resources are opened here.
O: pass | Supported option additions stay at the CLI/configuration boundary; LoadPlan and FaultPolicy retain their own invariants instead of duplicating them in the parser.
L: pass | Parsing rejects typos, duplicates, unsupported profiles/modes, nonliteral booleans, absent revision and secret options. It preserves exact durations, rates and signed seeds; tests verify both arrival and fixed-concurrency cases before allocation.
I: pass | Callers need one parse operation returning a usable immutable value, without mutation setters or endpoint startup methods.
D: pass | The parser depends on Java values and public profile/bind enums; clocks, sockets, filesystem writes and environment credential lookup remain outside it.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorConfig.java
type: kg.aidarbek.simulator.SimulatorConfig
sha256: 81f41ea132513f9b14e0d3df9f5d6e8f382229cf4c7a931b8479c829dbf23960
responsibility: Owns validated, non-secret inputs for a finite independent client or server run.
consumers: CLI parsing constructs it; endpoint composition, traffic adapters, reply policy, criteria and report projection read their relevant values.
S: pass | All fields are the reproducible run specification; credentials, live resources and mutable counters are deliberately owned elsewhere.
O: pass | Workload, operation and threshold variation is data; meaningful behavior extensions use the content/operation seams rather than configuration subclasses.
L: pass | The record retains immutable collaborators and validates connection/window products, estimated outstanding bytes, bounded payloads/durations, role-specific ports, revision/run identifiers and finite thresholds. Address and host inputs are bounded; argument and process tests verify rejection before side effects.
I: pass | Consumers use focused accessors from one immutable value and are not forced to implement unrelated endpoint or metric interfaces.
D: pass | Dependencies point to Java values, local LoadPlan/FaultPolicy and public protocol/profile enums, with no concrete transport or executor construction.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorConfig.java
type: kg.aidarbek.simulator.SimulatorConfig.Mode
sha256: 81f41ea132513f9b14e0d3df9f5d6e8f382229cf4c7a931b8479c829dbf23960
responsibility: Names whether the standalone application originates as a client or owns a listening server.
consumers: SimulatorArguments, SimulatorConfig, MessageTraffic, SimulatorEndpoint and report projection branch on this role.
S: pass | The enum only identifies the application endpoint role; it does not combine transport ownership with protocol bind permissions.
O: pass | Client/server configuration varies without adding subclasses; future endpoint roles would require an explicit domain decision.
L: pass | Immutable enum values are compared directly and serialized by name, with no ordinal wire mapping. CLI/process tests exercise both roles and server port-zero allowance.
I: pass | Consumers need the role selection alone and no shared endpoint methods.
D: pass | The enum has no dependency on library implementation or runtime infrastructure.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorEndpoint.java
type: kg.aidarbek.simulator.SimulatorEndpoint
sha256: 9c499dd9fc27fd9cec1d6e038bb5e7f41b6ee1610bc557925c44f0ecb9599e5b
responsibility: Owns one finite cohort of public library client sessions or one listening server, including startup and bounded shutdown reporting.
consumers: SimulatorRun starts it, reads an immutable session-list snapshot and requests shutdown; exchange/process tests exercise real lifecycle paths.
S: pass | Endpoint allocation, readiness collection, connection ramp and cleanup share one resource owner; workload generation, content decisions and report construction remain outside this type.
O: pass | Typed EndpointHandlers, event Consumer and maintenance Runnable are supplied collaborators; the library's public client/server configuration controls transport without embedding a second protocol implementation.
L: pass | Startup is single-use, admission/ready collections and waits are bounded, excess ready sessions close, and startup failures close owned endpoints. Interrupted startup/shutdown restores the owner's interrupt; shutdown reports library cleanup completion instead of claiming close is a join. Real interruption and process tests verify those contracts.
I: pass | The run owner needs start, sessions, shutdown and AutoCloseable close; consumers receive public BoundSession capabilities rather than transport internals.
D: pass | This is the concrete composition boundary for SmppClient/SmppServer; scheduler and receiver policies depend on ports/values and never create these resources themselves.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorMain.java
type: kg.aidarbek.simulator.SimulatorMain
sha256: 287284bd69c5db3ebddcc8e8b8225d6fe20677d051bab60014e325b19d79cd67
responsibility: Provides the independent command-line entry point, environment credential lookup and process exit status.
consumers: The application launcher and separate-process tests invoke main.
S: pass | Help, top-level configuration/error routing and exit-code publication are one process-boundary responsibility; SimulatorRun owns execution and reports.
O: pass | New scenarios extend configuration and operation/content composition; main remains a thin launcher rather than a command implementation registry.
L: pass | Help exits without starting a run, invalid configuration yields status 2, execution/criterion failure yields nonzero status, and diagnostics avoid exception messages or secret values. Process tests verify invalid inputs, independent roles and successful cleanup.
I: pass | The Java launcher needs only main; no service lifecycle or application-specific interfaces are exposed.
D: pass | Global System streams/environment and System.exit are confined to the process entry point; implementation details are delegated to parser and run composition.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorRun.java
type: kg.aidarbek.simulator.SimulatorRun
sha256: af3f934af8b64fd402efbe115162ec2ec90e4f131adb34c6ee5e8a7085b47a36
responsibility: Composes one finite workload with receiver maintenance, resource sampling, cleanup, criteria and final output.
consumers: SimulatorMain invokes execute; process tests exercise real client/server runs and machine-readable results.
S: pass | Its control flow coordinates one run lifecycle while dedicated collaborators own scheduling, validation, measurements, endpoint resources and serialization.
O: pass | TrafficOperation and ContentPlan separate scenario behavior from TrafficRunner, and the receiver uses a plan supplier per stream; adding scenarios does not require a new request tracker.
L: pass | Operation preflight precedes endpoint resources; each admitted call is observed through RequestObservation, aborted phase results remain available, and receiver closure precedes bounded endpoint shutdown. Cleanup failures and unstarted measurement enter criteria rather than silently passing; process tests cover faults and fresh reports.
I: pass | Composition consumes each collaborator's focused public operations, without exposing the aggregate run as a monolithic library session API.
D: pass | Concrete clocks, pause behavior, report files and public endpoints are wired here; TrafficRunner receives observation/source/time ports and does not depend on this composition root.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficContent.java
type: kg.aidarbek.simulator.TrafficContent
sha256: d935b40af8c350718b7eadc2aa35a8150ad09d0b0fd179c22a352734ec5e2fbc
responsibility: Carries an immutable operation-independent payload with explicit esm_class, data_coding and non-payload parameters.
consumers: ContentPlan implementations create it, MessageTraffic chooses short_message/message_payload placement, and receive validators inspect it.
S: pass | Its validation describes one content body and preserves operation ownership of the wire payload carrier.
O: pass | Different text, binary or receipt plans use the same explicit values; operation-specific placement changes do not require encoding logic inside this record.
L: pass | Immutable OctetString and OptionalParameters provide owned contents and structural equality. Flags are uint8, payload is bounded to 65535 octets, parameters to 64 tags, and tag 0x0424 is rejected here to prevent duplicate placement; content and adapter tests exercise these invariants.
I: pass | Consumers need four value accessors and no session, codec, scheduler or automatic encoding methods.
D: pass | The record depends only on public immutable protocol values and Java validation, remaining usable by scenario adapters independently of endpoint state.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficOperation.java
type: kg.aidarbek.simulator.TrafficOperation
sha256: 7eaf60cc6fdeab2be2b6de5d3bbf447c782f043ffe71c463abdbb29bb6735ea8
responsibility: Defines one library-backed send operation using a public bound session, explicit content and planned identity.
consumers: MessageTraffic supplies operation lambdas; SimulatorRun invokes the selected operation and wraps its RequestHandle.
S: pass | The single method describes originating one request, without taking over polling, metrics or receiver behavior.
O: pass | Additional supported operation adapters implement this functional interface without changes to TrafficRunner or PendingCall.
L: pass | Implementations return the actual library RequestHandle or propagate local admission failure; sequence allocation, deadlines, cancellation and peer correlation remain with the library. Message adapter and real endpoint tests exercise admitted and rejected paths.
I: pass | A single send capability fits the composition caller; receiver registration and endpoint cleanup are not forced onto operation implementations.
D: pass | The port exposes public BoundSession, Command and RequestHandle contracts plus TrafficContent, never concrete request-window or socket types.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner
sha256: ebd4aa6e7514f00baa85101d62a0f2703e5c6df4084558be9e198fe8d0b47a68
responsibility: Executes finite warmup/measurement phases and bounded drain while preserving planned, admitted and terminal accounting.
consumers: SimulatorRun injects a source, clock, pause and maintenance functions; TrafficRunnerTest supplies controlled PendingCall fixtures and time.
S: pass | Generation, bounded active-call observation and phase finalization belong to one run algorithm; encoding, endpoint ownership, resource collection and JSON remain collaborators.
O: pass | Arrival versus concurrency policy is explicit, while operation sources and observation implementations vary through small injected functions/ports without changing the algorithm.
L: pass | At most maximumPending calls are retained and delayed arrivals become skipped counts rather than a backlog. Warmup cannot silently disappear if undrained, and runtime maintenance/pause aborts preserve admissions, mark remaining calls UNFINISHED and request cancellation once. Controlled tests verify exact counts, phase durations and failure outcomes.
I: pass | The runner needs only LoadPlan, PendingCall and small source/time/maintenance functions; it does not demand a complete endpoint implementation from tests or adapters.
D: pass | Variable infrastructure is injected, and the sole public request dependency is RequestFailure classification for synchronous rejection; protocol ownership remains outside the runner.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Active
sha256: ebd4aa6e7514f00baa85101d62a0f2703e5c6df4084558be9e198fe8d0b47a68
responsibility: Retains one admitted PendingCall and its invocation/planned timestamps during a phase.
consumers: TrafficRunner alone creates, polls, finalizes and removes these private entries.
S: pass | All fields describe one active observation, with no independent scheduling or terminal-state machine.
O: pass | Any PendingCall implementation fits the holder; operation-specific types do not introduce entry subclasses.
L: pass | The call is intentionally a live identity reference while timestamps are immutable scalars. The private record is not exposed or used as an equality-based request index, and its retention is bounded by maximumPending.
I: pass | Only the runner requires these three accessors; no public handle or lifecycle abstraction is added.
D: pass | The holder depends on the narrow PendingCall port and scalars, not a concrete library request engine.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Phase
sha256: ebd4aa6e7514f00baa85101d62a0f2703e5c6df4084558be9e198fe8d0b47a68
responsibility: Transfers the completed accounting snapshot, actual phase/drain durations and bounded abort reasons to run composition.
consumers: Only TrafficRunner.phase constructs it and TrafficRunner.run combines warmup and measurement values.
S: pass | The fields describe one finished phase rather than owning generation or merging separate cohorts.
O: pass | Both supported generating models return the same phase shape; behavior changes remain in the explicit phase algorithm.
L: pass | Its snapshot is immutable and its private construction sites supply List.of failure values, so record fields retain immutable ownership despite no redundant list copy here. Tests verify undrained warmup and abort counts survive phase transfer.
I: pass | The enclosing runner consumes all phase accessors and no mutation or cancellation operations are exposed.
D: pass | The record depends only on local snapshots, scalar durations and Java lists.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/TrafficRunner.java
type: kg.aidarbek.simulator.TrafficRunner.Result
sha256: ebd4aa6e7514f00baa85101d62a0f2703e5c6df4084558be9e198fe8d0b47a68
responsibility: Publishes separate warmup/measurement snapshots, observed durations, measurement-start status and retained run-abort reasons.
consumers: SimulatorRun, RunCriteria, RunReport and controlled runner tests consume it.
S: pass | All fields describe one completed or explicitly aborted traffic run; the record does not infer outcomes from endpoint state or rerun work.
O: pass | New operation sources reuse the same result shape; criteria and output schema vary independently of the phase executor.
L: pass | Failures are copied immutably and nested snapshots own their data. measurementStarted explicitly distinguishes an unexecuted measurement from a real zero-result cohort; the convenience constructor derives it from a positive measurement duration. Warmup/abort tests verify preserved accounting.
I: pass | Consumers need result accessors and no mutable metric collector, pending-call list or infrastructure lifecycle.
D: pass | The record depends only on local immutable snapshots and Java values.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ArrivalScheduleTest.java
type: kg.aidarbek.simulator.ArrivalScheduleTest
sha256: 736a643538ef03ec229783a0ddf23b266555863bdeef0d52ba78b3760de189a3
responsibility: Specifies finite arrival timing, missed-work accounting and signed monotonic-wrap behavior with controlled timestamps.
consumers: JUnit runs the three scenarios against ArrivalSchedule and its immutable Arrival results.
S: pass | Every test concerns the same cursor/timing contract; it does not mix endpoint startup or performance measurement into schedule verification.
O: pass | Additional rate profiles add independent input/expectation cases without changing the scheduler's collaborators or introducing a test-only scheduling abstraction.
L: pass | The class has no custom supertype contract; isolated local schedules and exact hand-calculated times verify no early emission, no duplicate poll, capped totals and idempotent finish without wall-clock waits.
I: pass | JUnit needs only test methods; each fixture constructs the small scheduling API it actually exercises.
D: pass | The tests depend on pure production values and explicit times, not sockets, executors or a real clock that could obscure timing defects.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/CohortMetricsTest.java
type: kg.aidarbek.simulator.CohortMetricsTest
sha256: 2d187a2c355773ca9390bac855cb6f93ed55c78d077b68b7ccf5d25a1e270ae6
responsibility: Specifies conservation equations, latency populations and bounded category retention using known event sequences.
consumers: JUnit executes metrics transitions and inspects immutable CohortMetrics/Latencies snapshots.
S: pass | The three cases verify one measurement contract: every planned/admitted outcome remains accounted for with honest histogram populations.
O: pass | New outcome or boundary examples can be added as data/transition cases while networking and report persistence remain outside the fixture.
L: pass | There is no custom subtype contract; explicit counts, nonzero status values and a known overflow sample provide independent expected values. Snapshot mutation is rejected and impossible transitions are tested directly.
I: pass | The fixture uses only metric transitions and snapshot accessors; it needs neither request tracking nor endpoint lifecycle methods.
D: pass | Dependencies are JUnit and the bounded measurement model; the histogram adapter is tested through observable distributions rather than copied internal buckets.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/DecisionQueueTest.java
type: kg.aidarbek.simulator.DecisionQueueTest
sha256: 2632a762a463c2025a992278882d707ace5adc3837a81a8fde815ee1b2c5d739
responsibility: Specifies delayed/stalled decision admission, deadline release, close and caller-cancellation retention.
consumers: JUnit constructs finite DecisionQueue instances and explicitly advances their supplied times.
S: pass | All cases concern the same deferred-decision ownership contract, including queued cancellation and idempotent cleanup.
O: pass | Further result types or due-time cases reuse the generic queue API; no real scheduler is required to extend coverage.
L: pass | The class introduces no custom subtype and closes each queue in finally or try-with-resources. It verifies exact retained counts, rejection after full/close and overflow-safe deadlines without sleeps or autonomous completion assumptions.
I: pass | Tests consume only defer, advance, pending and close, matching the production owner rather than bypassing private state.
D: pass | Controlled time and Java futures keep tests independent of transport, endpoint workers and machine timing.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/FaultPolicyTest.java
type: kg.aidarbek.simulator.FaultPolicyTest
sha256: 8c54af98a329dd0413abc500a83da6b1d063d40172987c6db789490b7084e80d
responsibility: Specifies the configured disjoint fault mix and identity/seed determinism.
consumers: JUnit runs exhaustive bucket checks and constructor-boundary scenarios against FaultPolicy.
S: pass | All assertions concern fault selection and admissible policy values, not execution queues or network symptoms.
O: pass | Additional mixes and identity examples extend the table of expectations without modifying receiver or scheduling fixtures.
L: pass | No custom subtype contract is introduced; all 100 buckets are checked against explicit thresholds, and reverse traversal confirms selection is independent of call order without claiming finite-sample statistical percentages.
I: pass | The tests use constructor, bucket and decision behavior only; fault execution methods are not invented for the value type.
D: pass | Dependencies are pure policy/time values and JUnit, so randomness or infrastructure cannot mask selection defects.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/LoadPlanTest.java
type: kg.aidarbek.simulator.LoadPlanTest
sha256: 268944c245221899bfca8e33510bfeae2a964927eb6105a0289976ea0c2bb0d5
responsibility: Specifies finite plan validation, immutable rate ownership and early rejection of unexecutable arrival warmup.
consumers: JUnit creates LoadPlan values directly and mutates only the caller-owned rate list to test isolation.
S: pass | Each case verifies an invariant required to execute one plan safely; no endpoint allocation or CLI formatting is tested here.
O: pass | New plan bounds or generating modes add focused constructor cases, leaving the fixture's minimal plan helper reusable.
L: pass | The class has no custom subtype and no resources. Assertions verify exact copied rates, rejected list mutation, contradictory modes and the one-nanosecond warmup regression before scheduler construction.
I: pass | Tests need only constructors and immutable accessors; they do not require starting TrafficRunner or a live endpoint.
D: pass | Validation evidence uses Java values and the production plan rather than a mock parser or infrastructure-dependent configuration path.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/MessageTrafficTest.java
type: kg.aidarbek.simulator.MessageTrafficTest
sha256: 75fc9fab136b03c7c8f19e1f82fc36887ca90aa7b05aada833a92225210b2a00
responsibility: Specifies public operation capability preflight and exact payload placement/extraction at common field boundaries.
consumers: JUnit creates validated simulator configurations and generated content, then inspects MessageTraffic's public protocol values.
S: pass | The two cases concern the operation adapter contract: represent content once and reject impossible role/version sends early.
O: pass | Additional operations add their own capability/body cases; the tests do not duplicate endpoint coordination or scheduler implementations.
L: pass | No custom subtype contract applies. Explicit lengths 0,160,254,255,4096,65535 verify unique carrier placement and roundtrip ownership, while profile-specific data rules are asserted without opening sockets.
I: pass | Only configuration values and adapter builders/extractors are needed; tests do not require a monolithic fake session.
D: pass | The fixture depends on public library values and the simulator adapter; real codec/transport behavior remains covered by separate exchange/process tests.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/RawContentTest.java
type: kg.aidarbek.simulator.RawContentTest
sha256: f557da8bf6ed83443c68362757956ef93d82260b329118299e56150a0b5fc8a0
responsibility: Specifies the bounded deterministic raw scenario and its explicit 8-bit binary data coding.
consumers: JUnit invokes RawContent generation/validation and TrafficContent construction with known sizes, seeds and metadata.
S: pass | All cases verify binary content invariants and repeatability rather than operation placement, network transport or text decoding.
O: pass | Additional binary boundary cases add direct inputs and independent expectations; other variants belong in their own ContentPlan contract tests.
L: pass | The class has no custom subtype or resource ownership. Exact DCS4 and size expectations, repeated seed/identity comparisons, distinct-input comparisons and invalid-bound failures establish observable behavior without copying generated bytes into golden fixtures.
I: pass | Only the content plan and immutable value APIs are exercised; no fake session or codec interface is needed.
D: pass | The fixture depends on pure scenario/protocol values and JUnit, remaining independent of real clocks, sockets and provider encoding defaults.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReportWriterTest.java
type: kg.aidarbek.simulator.ReportWriterTest
sha256: db9e5258ecf71978a2722d26943c14729604588f11ef8891fe576d028df48c9d
responsibility: Specifies bounded report-value JSON encoding and fresh streamed report-file ownership.
consumers: JUnit supplies a temporary directory, known scalar/record values and exact expected JSON/CSV strings.
S: pass | Serialization cases and file cases jointly verify the persistence boundary, without collecting live metrics or starting workloads.
O: pass | New report fields reuse the existing value-encoding contract; new unsupported kinds or escaping cases add focused expectations without endpoint fixtures.
L: pass | The class has no custom subtype; JUnit owns the temporary directory and try-with-resources closes the writer. Tests assert exact escapes/rows, unsupported-value failures and refusal to overwrite an existing run.
I: pass | The fixture uses encode, sample, finish and close, with no file-management capability imposed on the measurement types.
D: pass | It intentionally tests the real Java filesystem adapter in a temporary location; protocol, networking and wall-clock timing are absent.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ResourceSamplerTest.java
type: kg.aidarbek.simulator.ResourceSamplerTest
sha256: 461efbc7fa48cb381bda1ef1044cd24ff90dcde445c80c9b168837dbc1e81739
responsibility: Specifies that unavailable descriptor observations remain explicitly unknown until a real measurement establishes a peak.
consumers: JUnit injects an AtomicLong clock and AtomicReference observation source into ResourceSampler.
S: pass | The test isolates sampled-peak semantics and cadence from platform probing and workload execution.
O: pass | Other missing/known resource sequences can reuse the injected suppliers without changing sampler or creating a platform abstraction hierarchy.
L: pass | No custom subtype contract applies. A temporary writer is closed, known snapshot values are immutable, and the sequence -1,-1,9,-1 independently verifies that unknown readings do not become zero or erase a known peak.
I: pass | The fixture uses the purposeful clock/observation constructor plus sample, tick and summary; no endpoint API is required.
D: pass | Variable time and operating-system data are replaced by simple supplied values while real report-stream ownership remains confined to the temporary writer.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/RunCriteriaTest.java
type: kg.aidarbek.simulator.RunCriteriaTest
sha256: cc0a845dc04888fd4626706f5b156bc453e65510e06e338c9afe85f059db10c6
responsibility: Specifies declared fault allowances, throughput/latency populations and unconditional incomplete-work/content/cleanup failures.
consumers: JUnit evaluates RunCriteria with deliberately constructed cohort snapshots and CLI-validated threshold inputs.
S: pass | Each test concerns interpretation of known measurements rather than generating load or recalculating the measurements under test.
O: pass | Additional gates add controlled result/configuration combinations; endpoint behavior and report writing remain independent test concerns.
L: pass | The class has no custom subtype; explicit success times before/after the measurement boundary prove late completions cannot inflate throughput, and expected-failure mode cannot excuse unfinished requests or bad cleanup.
I: pass | The evaluator is exercised through snapshots and flags only, without mutable endpoint or asynchronous callback fixtures.
D: pass | Tests depend on pure criteria/configuration/accounting types, with no hardware throughput threshold or timing-sensitive network run.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest
sha256: 88b9469601bde3b75fe498e21ba4ac57c6047611faa8952aeb437dbe5f0e674a
responsibility: Enforces the simulator's public-library dependency boundary and proves the rule detects forbidden request-engine ownership.
consumers: JUnit runs ArchUnit against imported production simulator classes and an intentionally forbidden isolated fixture.
S: pass | The positive scan and negative probe serve one architecture constraint rather than asserting unrelated naming or package style rules.
O: pass | Public operation capability additions can extend adapters without importing request internals; any deliberate boundary change remains explicit in the allow-list predicate and its probe.
L: pass | The class has no custom subtype. The production import excludes test classes, while the separate forbidden fixture must produce a violation naming RequestWindow, preventing a vacuous success from masquerading as boundary evidence.
I: pass | The test uses ArchUnit's import/evaluate/check capabilities and JUnit assertions only; production code is not required to expose test hooks.
D: pass | The rule requires public endpoint/protocol types and an exact public profile/limits/request observation allow-list; the forbidden implementation dependency exists only in the negative test fixture.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest#boundary/<anonymous>@20:109
sha256: 88b9469601bde3b75fe498e21ba4ac57c6047611faa8952aeb437dbe5f0e674a
responsibility: Classifies each dependency target against the simulator's allowed public library surface.
consumers: ArchUnit's onlyDependOnClassesThat rule invokes the described predicate for imported JavaClass targets.
S: pass | Its test method implements one dependency-membership decision; importing classes and asserting violations remain in the enclosing test.
O: pass | Public endpoint/protocol types are admitted by role and modifiers, with other necessary types explicitly named; adding an operation does not require blanket access to implementation packages.
L: pass | It satisfies DescribedPredicate<JavaClass> by returning a deterministic boolean for every imported target without side effects or resource acquisition, and preserves the meaningful description used in violations.
I: pass | The single predicate method is exactly the ArchUnit extension contract; no unrelated callback or lifecycle methods are implemented.
D: pass | Dependencies are ArchUnit metadata and immutable Java names/sets, and the predicate never constructs library runtime objects.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest.ForbiddenRequestEngine
sha256: 88b9469601bde3b75fe498e21ba4ac57c6047611faa8952aeb437dbe5f0e674a
responsibility: Provides a minimal forbidden RequestWindow dependency for a negative architecture assertion.
consumers: SimulatorArchitectureTest imports this class separately and requires the boundary rule to report its field dependency.
S: pass | The lone field supplies one deliberate structural counterexample; the fixture is never a working simulator or request implementation.
O: pass | Other prohibited edges belong in separate targeted probes rather than expanding this fixture into a reusable engine.
L: pass | The final class has no custom supertype, behavior or allocated RequestWindow resource; its default-null field is inspected as metadata and never presented as a substitutable pending-call implementation.
I: pass | The fixture exposes no operational interface, so consumers cannot accidentally depend on request-engine behavior during a positive test.
D: pass | The concrete RequestWindow edge is intentional test evidence for rejecting that dependency in production; no runtime composition uses the fixture.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArgumentsTest.java
type: kg.aidarbek.simulator.SimulatorArgumentsTest
sha256: 5368bfdab2f9b1678243036b5deaefd94e4ccf2255155bd4e1402636eda7c79e
responsibility: Specifies reproducible CLI parsing, role/default selection and early rejection of invalid or secret options.
consumers: JUnit calls SimulatorArguments.parse with explicit option arrays and inspects the resulting immutable values.
S: pass | All cases concern configuration syntax and semantics before side effects, including modes, profile, units, rates and allocation bounds.
O: pass | Additional supported options extend direct input/output examples, without requiring process or socket fixtures for parser behavior.
L: pass | No custom subtype or resource contract is introduced. Assertions retain exact duration, signed seed, profile and rate values, and reject typos, duplicates, invalid booleans, missing revision and credential options.
I: pass | The test needs the parser and configuration accessors only; endpoint startup is unnecessary for these preflight guarantees.
D: pass | Dependencies are JUnit, Java values and public enums, keeping parser evidence independent of environment credentials and network state.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorExchangeTest.java
type: kg.aidarbek.simulator.SimulatorExchangeTest
sha256: dd960699d521ae21630d05128bfd13974f916fce78361b5b442177386266614e
responsibility: Specifies simulator receiver accounting and endpoint cleanup over real local public-library connections, including interrupted startup.
consumers: JUnit runs bounded local endpoint/raw-peer scenarios with explicit request and response assertions.
S: pass | All scenarios test composition behavior that pure value tests cannot establish: receiver counts, negative acknowledgements and real endpoint ownership.
O: pass | A small raw frame helper supplies independently constructed wire fixtures; new network-specific regressions can reuse it without modifying production codec or transport internals.
L: pass | The timeout-bounded fixtures close sockets/endpoints with try-with-resources and verify shutdown completion. Startup interruption is asserted then cleared only in test cleanup; a valid 5.0 request with 65 unknown TLVs proves extraction failure still counts one received/invalid request.
I: pass | The tests consume public client/server/handler APIs plus a minimal raw peer, without requiring access to EndpointConnection or a replacement request tracker.
D: pass | Real transport is intentional integration evidence, while raw bytes independently cross the decoder boundary and avoid outgoing-validation artifacts that cannot reproduce the accounting defect.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java
type: kg.aidarbek.simulator.SimulatorProcessTest
sha256: edd7a388ff0c339d10938d9f23cc27a8c0590120c9ba7a8577eb1ad195c5d7d2
responsibility: Verifies independently launched client/server applications, their report contracts and selected receiver faults in separate JVMs.
consumers: JUnit starts bounded child processes and inspects exit status, READY events and fresh JSON/CSV outputs.
S: pass | Both-profile smoke, invalid preflight and fault/duplex cases serve the standalone-tool contract; they do not claim production capacity or machine-independent performance.
O: pass | Additional process scenarios supply option lists through the bounded pair helper while scheduler/accounting tests remain separate and deterministic.
L: pass | The class has no custom subtype. Each child has bounded waits, redirected output and forced cleanup in finally; temporary reports prevent overwrites, and tests verify cleanup, missing secret values and expected success/rejection/timeout/disconnect counts.
I: pass | The fixture uses only the executable entry point and machine-readable outputs, not internal simulator state or test-only endpoint APIs.
D: pass | ProcessBuilder, filesystem and actual compiled artifact locations are appropriate at this outer integration boundary; production scheduling still depends on narrow ports.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java
type: kg.aidarbek.simulator.SimulatorProcessTest.Pair
sha256: edd7a388ff0c339d10938d9f23cc27a8c0590120c9ba7a8577eb1ad195c5d7d2
responsibility: Carries the immutable client and server report text returned by one completed process-pair fixture.
consumers: SimulatorProcessTest's fault/duplex assertions inspect the two report strings.
S: pass | Both fields identify the outputs of the same already-finished pair; the record does not retain live processes or filesystem ownership.
O: pass | New paired scenarios reuse these two report values, with process lifecycle remaining in the enclosing helper.
L: pass | Strings provide immutable structural record equality; no process handles, mutable buffers or arrays escape through the result.
I: pass | The test needs exactly client and server accessors, with no close or wait method after the pair helper has cleaned up.
D: pass | The record depends only on Java strings and does not expose process or library implementation types.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest
sha256: dcc6338ba6e9d11dba90a15a5b2b64c02388b064f7c5f9bc63961fbc84c6a0c1
responsibility: Specifies finite generation, cohort separation, drain ownership and retained accounting after controlled aborts.
consumers: JUnit injects monotonic clocks, pause/maintenance behavior and narrow PendingCall fixtures into TrafficRunner.
S: pass | Every case validates the runner's observable schedule/admission/terminal conservation, without mixing protocol encoding or filesystem failures beyond an injected abort signal.
O: pass | New timing or abort scenarios substitute small source/time functions rather than requiring changes to production request tracking.
L: pass | The class has no custom subtype and uses no real sleeps. Exact offered/skipped/admitted counts, phase durations, cancellation counts and measurementStarted assertions verify that stalls or maintenance/pause failures cannot erase a cohort.
I: pass | Fixtures implement only PendingCall and injected functions; no heavyweight fake endpoint or transport API is required.
D: pass | Controlled dependencies isolate generation policy from clock speed, network behavior and application callback scheduling.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest#concurrencyRefillsOnlyFiniteSlotsAndAccountsForUnfinishedDrain/<anonymous>@137:54
sha256: dcc6338ba6e9d11dba90a15a5b2b64c02388b064f7c5f9bc63961fbc84c6a0c1
responsibility: Models one admitted call that stays pending until the runner reaches its bounded drain and requests cancellation.
consumers: TrafficRunner polls it and the enclosing test observes the shared cancellation counter.
S: pass | The three methods describe this single stalled observation; queue capacity and timeout progression remain with the tested runner.
O: pass | Other terminal patterns use separate small PendingCall fixtures without changing this intentionally stalled case.
L: pass | It satisfies the observation port by returning Optional.empty, reporting possible transmission and recording every cancellation request. It makes no promise that cancellation immediately ends physical work, allowing the test to assert UNFINISHED and exactly one cancel per call.
I: pass | Only PendingCall's three methods are implemented; no simulated sequence, writer or endpoint lifecycle is invented.
D: pass | The fixture depends on the narrow port and a test-owned AtomicInteger, with no library request engine or runtime resources.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest#maintenanceAndPauseFailuresKeepAdmissionsAndCancelPendingCallsExactlyOnce/<anonymous>@51:56
sha256: dcc6338ba6e9d11dba90a15a5b2b64c02388b064f7c5f9bc63961fbc84c6a0c1
responsibility: Provides an admitted nonterminal observation while a separately injected maintenance or pause operation aborts generation.
consumers: TrafficRunner observes the fixture and the enclosing test counts cancellation calls after the controlled abort.
S: pass | It models the pending call only; selection of abort point and advancement of time stay with the test's independent functions.
O: pass | The same fixture supports both maintenance and pause failures without conditional production behavior or a general mocking framework.
L: pass | poll remains empty and certainty remains possible transmission; cancel increments a counter without fabricating a terminal result. This preserves the port's cancellation-request contract and lets the test independently verify retained UNFINISHED accounting and one cancellation.
I: pass | The narrow three-method port is sufficient; no unused response callbacks, resources or request options are implemented.
D: pass | Dependencies are PendingCall and a test-owned counter, keeping the regression independent of real threads or sockets.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/TrafficRunnerTest.java
type: kg.aidarbek.simulator.TrafficRunnerTest.FakeCall
sha256: dcc6338ba6e9d11dba90a15a5b2b64c02388b064f7c5f9bc63961fbc84c6a0c1
responsibility: Provides a fixed immediate-success or persistent-pending observation for deterministic runner tests.
consumers: Warmup, pacing and rejection tests construct the private record through PendingCall.
S: pass | Its boolean and three methods describe one fixed observation pattern, without owning clock advancement or generation policy.
O: pass | Distinct lifecycle scenarios use other small port fixtures; this record remains a simple reusable fixed-result case.
L: pass | poll consistently returns a success value only when done is true, certainty reports possible transmission, and cancel is an allowed no-op request for a fixture that may remain physically unfinished. Immutable boolean equality does not stand in for real request identity.
I: pass | The record implements only the three PendingCall methods required by TrafficRunner.
D: pass | It depends on the observation contract, outcome value and Java Optional, with no concrete protocol request engine.
findings: none
```
