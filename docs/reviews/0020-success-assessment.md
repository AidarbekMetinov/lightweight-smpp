# Additive healthy planned-success assessment

Base: `4da2aecd6394f9ff18d700bb8b47ae1422451002` (`Prepare Maven publishing`).
Implementation and checks ran in `/tmp/lightweight-smpp-success-assessment`.
This change adds report analysis and Python tests. It changes no Java production,
load generation, protocol behavior, published workload, or existing pass/fail
criterion. No JVM, Gradle build, or load campaign was launched for this change.

## Contract

`assess_planned_success(report)` adds an explicit per-report assessment named
`healthyPlannedSuccess` to `campaign.runs[].reports[role]` and
`aggregate.individualRuns[]`. Its `scope` is `originating-report`. Existing
`passed`, process deadlines/reaping, exit statuses, recovery assessment and
`expectFailures` remain unchanged. A new assessment passing does not establish
that a whole process pair or campaign passed; callers must still inspect their
process cleanup, completeness and peer-role evidence. The CLI continues to
return its original strict exit status.

The assessment applies to originating `ARRIVAL_RATE` reports named `W-BASE`,
`W-TARGET` or `W-SOAK`, including the explicitly marked `-diagnostic` variants.
It returns `applicable=false`, `passed=null` and a reason for receive-only,
expected-fault, fixed-concurrency and other profiles. A null verdict is never a
successful assessment. Missing or malformed applicability evidence fails closed.
Expected injected faults retain their existing scenario semantics.

Let `P` be the original measurement plan, `S` its eventual `SUCCESS` count,
`I` successes observed during measurement, `C` configured measurement duration,
and `E` observed measurement duration. The assessment exposes:

| Field | Definition |
| --- | --- |
| `eventualSuccessPerPlanned` | `S / P`, including successes observed during drain |
| `inMeasurementSuccessPerPlanned` | `I / P`, excluding drain successes |
| `inMeasurementConfiguredRateRatio` | `(I / E) / (P / C)` |
| `warmupSkippedArrivals` | Original warmup skips, separately disclosed |
| `measurementSkippedArrivals` | Original measurement skips |
| `guardThresholds` | Validated configured latency, RSS, heap and final-growth guards |

Acceptance requires both `S/P >= 0.99` and `(I/E)/(P/C) >= 0.99`, with `P,C,E > 0`
and `E >= C`. Comparisons use integer cross-products at the exact 99% boundary;
reported fractions are floating-point observations. For example, 99 successes
and one skipped arrival out of 100, over the configured full phase, pass this
new floor while the legacy verdict stays false. Even 100/100 successes over an
observed 1.02s instead of configured 1s fail the throughput floor: `1/1.02` is
about 0.980392. Drain completions cannot repair the in-phase floor.

Only measurement skips can consume this 1% budget. Local rejections and any
non-success admitted outcome are hard failures in both warmup and measurement.
Warmup must complete its original configured phase with exact accounting, but
has no throughput percentage floor: JVM warmup skips remain visible and continue
to fail the legacy policy. This distinction was explicitly agreed with the
coordinating agent.

The original plan is independently reconstructed from 1..64 bounded rates and
holds, summing the ceiling-rounded arrivals in each step and applying the
configured count cap. Holds must sum to the configured measurement duration.
Warmup uses its original first-rate schedule. Both declared warmup and receiver
drain must complete; neither deadline is inferred from final success counts.

Direct guards require balanced bounded counters and histogram populations,
zero pending/unfinished work, matching successful peer statuses, no local
rejections or non-success outcomes, no configured faults/churn/slow consumer,
complete initial binds, no replacement/retained connection work, receiver
acceptance reconciliation, and explicit endpoint/sampler cleanup. Sampled
request/reply counts, bytes, decisions and streams must respect the declared
bounds and retire to zero; historical observed-session facade count may remain.
These snapshots do not claim atomic transport-queue occupancy.

Enabled scheduled-p99, RSS, heap, descriptor-growth and platform-thread-growth
limits are recomputed from reported observations. Missing guard inputs fail
closed. Memory peaks must cover all recorded initial, baseline and final samples.
The maximum RSS high-water value from every recorded snapshot also contributes
to the RSS budget, including when a later Linux observation is unavailable.
Baseline and final snapshots remain required; the existing optional initial
snapshot is included whenever recorded. Process high-water values can exceed
RSS observed at sampling instants, so they are not required to fit the sampled
RSS peak. A disabled limit
remains explicitly disabled in `guardThresholds`: memory/p99 zero, final growth
minus one. Passing these sampled bounds is not a proof of absence of leaks or a
production-capacity claim.

The legacy verdict and failure list must be explicit and consistent. The two
legacy work/rate diagnostic labels that can differ under the new policy must
be explained by direct counts and the configured legacy threshold. Every other
legacy failure, every traffic execution failure, and every direct guard failure
prevents acceptance. This is not an arbitrary string filter granting success.
Malformed values, including booleans as numbers and very large JSON integers,
return a failed assessment with an explicit `invalid-evidence` reason; invalid
numeric threshold values are not copied into output JSON.

## Exact owned files and final identities

Hashes below are SHA-256 of complete UTF-8 file bytes after the final separate
format-normalization step. There are no copied prerequisites in this patch.

| Path | Final SHA-256 | Inspection |
| --- | --- | --- |
| `simulator/scripts/load_runs.py` | `cce171fc6e2388e0f432553d2d4f9969b22dd98a72a7948215a8b2397fefa3d0` | Whole script: 24 named functions, no classes; four new assessment functions and two additive call sites |
| `simulator/scripts/tests/test_success_assessment.py` | `97c2d8791922d24a27f787a9d981cbc432767978b18d2e9b7375f4eadb2e949a` | Whole module: two fixture functions, one test class with 21 methods, one embedded finite Python report-producing process fixture |
| `docs/reviews/0020-success-assessment.md` | Recorded in the handoff hash manifest, avoiding a self-referential hash | Contract, real evidence and whole-file review |

No Java type was created or changed. The Java-only `solid-review` evidence format
and Gradle inventory do not claim coverage of these Python functions. The
project's configured Spotless target covers Java only. Per the explicit no-JVM
scope, Python formatting was a separate UTF-8/LF, trailing-space and final-newline
normalization followed by AST parsing; no formatter task or Java check is claimed.

## Actual sequential TDD evidence

All Python test commands used the prefix below; `-k` selections appear in the
table. Outputs were redirected to the stated `/tmp/success-assessment-...log`
files, preserving actual failures. The initial minimal implementation delegated
to the legacy verdict; its relevant failure was `False is not true` at 99/100,
not an import or missing-symbol error.

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s simulator/scripts/tests -p test_success_assessment.py -v
```

| Log suffix | Selection / operation | Actual outcome |
| --- | --- | --- |
| `01-boundary-red` | `-k one_percent` | 1 relevant assertion failure: legacy delegation rejects the new exact 99% boundary |
| `02-boundary-green` | Full new test module | 3 tests passed |
| `03-outcome-red` | `-k hidden` | 4 assertions fail: hidden local failure, peer negative, peer nack, local rejection accepted by percentage alone |
| `03b-guards-red` | Full new module | 6 tests, 29 failing subcases across outcome, cleanup, missing evidence and warmup |
| `04-guards-green` | Full new module | 6 tests passed |
| `05-schedule-red` | Full new module | 9 tests, 4 failures: balanced wrong plan and incomplete original warmup/drain accepted |
| `06-schedule-green` | Full new module | 9 tests passed; includes exact count caps and two independent 1.001ms schedule steps |
| `07-resources-red` | `-k enabled_latency` | 24 failing subcases: declared guard violations or missing observations accepted |
| `08-resources-green` | Full new module | 11 tests passed |
| `09-integration-red` | `-k aggregation` | Missing additive per-run assessment fails the assertion |
| `09b-campaign-red` | `-k campaign` | Missing additive role assessment fails after two finite Python report fixtures are reaped |
| `10-integration-green` | Full new module | 13 tests passed; legacy aggregate/campaign verdict and exit status remain false for skipped work |
| `11-retained-soak-check` | Read-only analysis of four retained reports | Existing reports fail both explicit 99% floors; no measurement or report rewrite |
| `12-existing-python-green` | All tests, omit `-p` | 51 discovered: 49 passed, 2 optional installed-JVM skips |
| `13-disclosure-red` | Full new module | 2 failures: missing warmup-skip disclosure and invalid NaN threshold copied to output |
| `14-disclosure-green` | Full new module | 15 tests passed; invalid cancellation-category count also characterized |
| `15-large-integer-red` | `-k out_of_range` | Relevant `OverflowError`: a 401-digit JSON integer reached floating conversion |
| `16-large-integer-green` | Full new module | 16 tests passed after range validation before floating conversion |
| `17-source-format` / `18-final-python` | Separate normalization / all Python tests | Intermediate source checkpoint; 54 discovered, 52 passed, 2 skips |
| `19-role-red` / `20-role-green` | `-k invalid_operation` / full new module | 3 invalid-role assertions fail, then 17 tests pass |
| `21-final-format` / `22-final-python` | Separate normalization / all Python tests | Intermediate checkpoint; 55 discovered, 53 passed, 2 skips |
| `23-legacy-label-red` / `24-legacy-label-green` | `-k legacy_skip` / full new module | 3 unexplained/missing legacy-label assertions fail, then 18 tests pass |
| `25-final-format` / `26-final-python` | Separate normalization / all Python tests | Intermediate checkpoint; 56 discovered, 54 passed, 2 skips |
| `27-retained-soak-check` | Read-only analysis, exact input hashes retained | Four report results and independent combined count calculation confirmed |
| `28-observed-budget-red` | `-k inconsistent_resource` | 5 failures: reported snapshots exceed budgets or connection bound while a smaller peak hides the violation |
| `29-observed-budget-green` | Full new module; filename records attempted green | Still failed 1 pre-existing guard subcase: negative heap peak was masked by a valid snapshot. This was not a passing run |
| `30-observed-budget-green` | Full new module | 19 tests passed after requiring peak coverage of baseline/final observations |
| `31-final-format` | Separate Python normalization and AST parse | Passed for the initial handoff; its hashes remain in the log |
| `32-final-python` | All tests, omit `-p` | 57 discovered: 55 passed, 2 explicit optional installed-JVM skips; 4.125s, fresh execution |
| `33-final-retained-check` | Read-only application of final script to the same four reports | Exact input hashes unchanged; only the two named 99% floor failures remain |

Final tests freshly execute Python behavior and bounded subprocess/socket
fixtures. The new campaign fixture writes independently constructed report JSON;
it does not generate SMPP traffic or represent a timing measurement. The two
existing installed-target methods remain explicitly skipped because no target
JVM execution was authorized. No test-cache result is presented as a measurement.

## Whole-file SOLID and contract review

All five principles were considered for the complete updated script and new
fixture/test module, not only the inserted lines. There are no unresolved
findings in this bounded change; this is not an absolute correctness guarantee.

| Unit | S | O | L | I | D |
| --- | --- | --- | --- | --- | --- |
| `load_runs.py`, whole script and existing consumers | The standalone workflow remains decomposed into manifest, profile, process, bounded-input, assessment and aggregate functions; no assessment work runs in the traffic loop. | Adding a named assessment requires no replacement of profile, legacy verdict, recovery or process machinery. No unused strategy hierarchy introduced. | Existing CLI, file freshness, count weighting and exit-status contracts remain unchanged; new assessment reads rather than mutates input. | Callers can use `assess_planned_success(report)` without invoking a process or CLI; campaign users receive one additive field. | Pure numeric analysis consumes report values; existing launcher/filesystem ownership remains at the script composition boundary. |
| `assess_planned_success` | Decides applicability and composes the named measurement-only policy. | Explicit healthy profile allow-list and separate helpers keep unrelated fault/recovery policies intact. | Positive/negative/null applicability, immutable input and exact floors have boundary regressions; malformed evidence cannot produce a successful verdict. | One report argument and one explicit result; no callback, executor or global state required. | Depends on report schema and bounded analysis functions, not endpoint or transport internals. |
| `healthy_phase_failures` | Verifies original finite phase and count-capped schedule population. | Reuses the declared list of at most 64 rate steps without special-casing fixture counts. | Ceilings, count caps, zero/invalid populations and incomplete original phases are independently tested. | Returns only phase failures; owns no result serialization or process state. | Uses integers and supplied configuration/counters; no clock or scheduler implementation invocation. |
| `healthy_completion_failures` | Verifies direct outcome, receiver, lifecycle and cleanup obligations for healthy reports. | Fault scenarios stay outside this healthy-only policy; counters/status categories keep their existing representation. | Zero pending/unfinished, no rejected or non-success calls and explicit cleanup are tested even when the legacy summary claims success. | Reads the relevant existing report sections; adds no endpoint observer API. | Uses immutable observations and existing cohort validation rather than invoking owners or fabricating their outcomes. |
| `healthy_observation_failures` | Evaluates quantitative guard evidence, including consistency of the two tolerated legacy diagnostics. | Configured disabled/enabled thresholds remain explicit; pressure bounds derive from existing configured capacities. | Exact limits, overflow/population checks, missing evidence, large integers, initial sample coverage and all recorded RSS high-water values are tested, including a later unavailable read. | Guard thresholds are a small value mapping; no configurable validation framework is imposed. | Depends on finite report observations and histogram utilities, not platform measurements or a live JVM. |
| Fixture functions `cohort` and `healthy_report` | Construct finite independent report inputs for observable policy cases. | Tests change specific copies, so new cases do not alter a shared mutable baseline. | Histograms and counters reconcile independently; malformed cases deliberately violate named contracts. | Fixtures expose only the counts/elapsed times needed by tests; no permissive endpoint/process double. | Python dictionaries and `deepcopy` provide isolation; no production helper generates expected values. |
| `PlannedSuccessTests`, all 21 methods | Exercises the public assessment and its two persistence consumers. | Table/subtest cases extend malformed/guard coverage without changing application behavior or assumptions. | Preserves `unittest.TestCase` setup/assertion/subtest contracts; actual finite Python children have explicit exit behavior and are reaped by the real wrapper. | Tests call only analysis/aggregate/CLI seams; no reflection into Java or installed-target requirement. | Uses independent report literals, temporary directories and standard Python process/file behavior; no external SDK, library dependency or Cloudhopper code. |

The complete existing script function inventory inspected is `source_manifest`,
`profile`, `assess_planned_success`, `healthy_phase_failures`,
`healthy_completion_failures`, `healthy_observation_failures`, `assess_intervals`,
`merge_histograms`, `bounded_int`, `aggregate_reports`, `resource_summary` and its
nested `selected`, `validate_cohort`, `merge_cohort`, `read_json`, `build_options`,
`nanos`, `iso_duration`, `run_pair`, `command`, `main`, `run_campaign`,
`extra_options`, and `write_json`. Their finite input bounds, histogram weighting,
non-finite JSON rejection, fresh output policy, argument-list subprocess launch,
per-process cleanup and input/binary fingerprint distinction remain unchanged.
The new module has 23 named functions in total: its two fixture functions and
21 test methods. Its embedded process fixture has no function/class declaration.

## Independent initial-observation finding and incremental correction

A subsequent independent review found that the initially delivered script at
`3242167e0d5d0e2f158b0bc36f9b0def6bc0def3743d3d45b1d19b000b624e68`
could pass despite an over-budget recorded initial heap/RSS sample or an earlier
RSS high-water value. That version checked baseline/final actual samples and
only the latest high-water value. Its own peak counters could therefore hide
contradictory earlier evidence. The finding was concrete and accepted; the prior
whole-file review did not establish correctness for these omitted cases.

The correction collects at most three recorded snapshots. Actual heap/RSS
samples participate in sampled-peak consistency and the whole-run budget.
Every recorded RSS high-water value participates in the RSS budget. A valid
high-water value above the sampled RSS peak is permitted because an unsampled
process peak is a different observation; a later `-1` reading never erases known
evidence. The existing optional-initial schema and required baseline/final
snapshots remain unchanged. No timing, JVM, resource sampler, legacy acceptance
or campaign-execution behavior changed.

The two new tests independently construct the reported examples, preserve a
valid control, check exact budget limits, and exercise known earlier high-water
values followed by unavailable Linux observations. They also verify that a
correctly covered initial sample at the exact limit passes and a process
high-water at the limit need not appear in the sampled RSS maximum. The complete
changed script, helper contracts, fixture module and 21 test methods were
reviewed again at the final hashes in the inventory above. The five-principle
review table applies to this final version; the change adds no class, dependency,
mutable shared state or unbounded collection.

| Follow-up log | Exact selection / check | Actual outcome |
| --- | --- | --- |
| `/tmp/success-assessment-35-initial-observations-red.log` | Standard new-module command with `-k initial_samples` | Two tests, six relevant assertion failures on the delivered script; latest-HWM and valid-control characterization remained green |
| `/tmp/success-assessment-36-initial-observations-green.log` | Standard full new-module command | All 21 tests passed |
| `/tmp/success-assessment-37-initial-observations-format.log` | Separate UTF-8/LF/trailing-space/final-newline normalization and AST parse | Passed; current script/test hashes recorded above |
| `/tmp/success-assessment-38-initial-observations-full-python.log` | Standard Python discover command without `-p` | 59 discovered: 57 passed, 2 optional installed-JVM skips; 4.096s, fresh Python execution |

The independent reviewer replayed all five exact inputs against final script
`cce171fc6e2388e0f432553d2d4f9969b22dd98a72a7948215a8b2397fefa3d0`:
the valid control passed and every over-budget example failed with the relevant
budget/peak reason. The original four-input counterexample artifact has SHA-256
`f61effae5c18d1d40ace4aaa52acaac6c54b4690dbe839c2c8f15d9d14588c9a`;
the reviewer also checked the later-unavailable variant. That separate replay is retained at
`/tmp/success-assessment-independent-fixed-replay.json`, SHA-256
`116e8766bbe261e997d7dae2927e79981a972bfa00728593622e0dae0d2650b7`.
The reviewer reported the bounded finding closed; no extra scope, source edits,
JVM execution or load measurements were involved in that independent replay.

The incremental patch is based on the exact previously delivered three files:
script `3242167e...e68`, test `08914855...72e`, and this review
`8ceef0e228b356d3d1941bbcb96e79b77e28a8814d252f992dcb8e0c9e39e91d`.
The incremental manifest records their complete before/after hashes; historical
TDD logs and failed measurements remain retained.

## Retained soak interpretation, not a new measurement

The four original final bidirectional data-soak reports each plan 9,000,000
requests. They contain 31,891,984 eventual successes in total out of 36,000,000
planned: **88.588844%**. All 31,891,984 admitted calls succeeded, which is a
separate **100% success/admitted** statistic. The 4,108,016 skipped planned
arrivals must remain in the new denominator. Four successes were first observed
during drain, leaving 31,891,980 in-phase successes. The 99% eventual floor would
require 3,748,016 additional successful planned requests at the same total plan.
This retrospective arithmetic neither meets the new goal nor attributes losses
to a particular bottleneck.

Files are under the root workspace's
`build/runs/step19-final-bidirectional/`; they were read and never changed.

| Relative report path | SHA-256 | Eventual successes / planned |
| --- | --- | --- |
| `w-soak-34-data/repeat-001/client/report.json` | `9b40c5a7e6ea29e85e22db24675c0c9705581b665d70a3b500290fba66135346` | 7,991,349 / 9,000,000 |
| `w-soak-34-data/repeat-001/server/report.json` | `56e9679c04429d39a4ce3bd23e3a11671db0b0861e47009f3f72da869b3de455` | 7,966,080 / 9,000,000 |
| `w-soak-50-data/repeat-001/client/report.json` | `06afd798611fa4fa2c7ef5a8d093c962572a92c11e0ffefc45cd731909b8f1de` | 7,944,884 / 9,000,000 |
| `w-soak-50-data/repeat-001/server/report.json` | `6e1eb52901285b75c600c3bcce86440aa1dcae742bfe22fe02b5453d719b0e91` | 7,989,671 / 9,000,000 |

A separate read-only pacing review at the base revision identified repeated
active-call observation and whole-session maintenance scans per issued request,
plus `ArrayList` tail shifting when a completed batch is removed one entry at a
time. These are concrete work-shape costs, not measured causal attribution for
the missed target. Root owns performance changes and fresh before/after runs.
The reporting patch neither changes pacing nor relaxes any prior failed result.
