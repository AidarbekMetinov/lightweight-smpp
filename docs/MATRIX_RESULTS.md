# Candidate workload matrix evidence

The completed carrier-corrected campaign finished all 54 first-attempt pairs: 16
passed and 38 failed their unchanged criteria. Forty-eight pairs completed their
full measurement phases; six failed during connection establishment. All 108
endpoint reports reconcile accounting and show complete cleanup and zero final
owned resources. These results precede the later simulator receiver-grace fix,
whose separate evidence is identified below. They are shared-host development
diagnostics, with no isolated capacity or production SLA claim.

The first candidate failed this campaign. Execution was stopped after a confirmed
Java 21 carrier-progress defect: five pairs finished with failed criteria, four
pairs were interrupted, and 45 pairs were never launched. No pair passed, and no
failure was replaced by a retry. This report preserves that first attempt; a
corrected candidate requires its own immutable inputs and fresh output directory.

## Frozen first candidate and execution

The candidate was `/tmp/lightweight-smpp-candidate`, based on commit
`387aed9ef523c85fb3cfa0f8f245438759deb6fc`. Its declared source SHA-256 was
`b31f25ac069814c7dc17a148ffda671f985cb7f925cc571a9dd22d16bfe20cf2`
(221 declared inputs). The installed library JAR SHA-256 was
`d7ed9830384a5acdf13475f4dba6e61c3ef061dd117134527a2a816ae98c89ea`,
and the simulator JAR SHA-256 was
`c7122a06eef4550f9168831b04955eaaa6f2ca5852dfcca492c6ecf6e0112623`.
The source/archive audit remains in `build/runs/step19-candidate-inputs`.

The external supervisor ran from **2026-09-09 13:07:37.122548 UTC** through
**13:15:32.786373 UTC**. Its exact command was:

```sh
python3 -B /tmp/step19-matrix-supervisor.py \
  --candidate /tmp/lightweight-smpp-candidate \
  --output /home/aidarbek/Documents/Projects/lightweight-smpp/build/runs/step19-candidate-matrix \
  --revision 387aed9ef523c85fb3cfa0f8f245438759deb6fc+b31f25ac069814c7dc17a148ffda671f985cb7f925cc571a9dd22d16bfe20cf2 \
  --expected-source-sha256 b31f25ac069814c7dc17a148ffda671f985cb7f925cc571a9dd22d16bfe20cf2 \
  --execute
```

The planned matrix contains 54 fresh pairs: W-RAMP, W-BURST, W-WINDOW with windows
1/8/32, W-CONNECTIONS, W-CHURN, W-FAULT and W-SLOW-CONSUMER, separately for both
SMPP versions and submission/delivery/data traffic. At most four pairs run
concurrently. Each helper uses `--repeats 1`, the complete published warmup and
measurement duration, and unchanged deadlines and criteria. W-WINDOW 1/8 retain
the helper's `-diagnostic` label because the window was overridden; both still
use the full 30-second warmup and 120-second measurement. No abbreviated duration
or offered-rate override was supplied. All nine started pairs were SMPP 3.4
submission; the other version/direction combinations remain unmeasured here.

The raw evidence is retained in
`build/runs/step19-candidate-matrix`: all helper commands, configuration and
artifact hashes, reports, logs, process cleanup records, `events.jsonl`,
`host-samples.jsonl`, and thread dumps under `diagnostics/`. `matrix.json` has
SHA-256 `82eb8715c3b72e4a498072b76701377797f1019709cdea0d2eb2549bb6727ae9`;
`matrix-analysis.json` has SHA-256
`7e0fc597d3f36a220c7a2e2ed33db533ad53db3091d79693c0f7f4bba90d9486`.
The supervisor SHA-256 was
`c1ba27ca669c1dfdee069e5b2b763342b72f220b8f4a16ffe102ae1187630fe3`;
the analysis script SHA-256 was
`ff82264a222b9c9905565fb9c6934f8ff2ab30574c4c1253b2b546215bdb4902`.
Source and installed-artifact checks remained unchanged during execution.

## Outcomes and original criteria

These counts describe the originating measurement cohort only. Warmup is
separate. Missing client reports are marked unavailable rather than treated as
zero traffic; completed response counts do not include locally rejected calls.
Latency percentiles from different processes or phases are not combined.

| SMPP 3.4 submission cell | Measurement seconds | Planned calls | Generator skips | Admitted / successful | Local rejections | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| W-RAMP | 300 | 2,220,000 | 667,345 | 1,552,655 / 1,552,655 | 0 | Failed recovery criteria |
| W-BURST | 180 | 750,000 | 287,112 | 462,888 / 462,888 | 0 | Failed recovery criteria |
| W-WINDOW 1 | Client unavailable; server 120 | Unavailable | Unavailable | Unavailable | Unavailable | Client exceeded the helper's 390-second process deadline |
| W-WINDOW 8 | Client unavailable; server 120 | Unavailable | Unavailable | Unavailable | Unavailable | Client exceeded the helper's 390-second process deadline |
| W-WINDOW 32 | 120 | 3,891,812 | 0 | 3,891,463 / 3,891,463 | 349 `WINDOW_FULL` | Failed `unsuccessful-work` |

W-RAMP and W-BURST each exited successfully in both endpoint processes, but the
frozen campaign helper correctly rejected recovery. The first exact 30-second
baseline cohort after the final overload had generator skips and a successful
rate below 99% of the configured rate. The initial baseline had already failed,
so recovery also reported `pre-fault-baseline-not-established`. For example, the
initial 1,000/s levels skipped 6,498 arrivals over 60 seconds in W-RAMP and 3,906
over 30 seconds in W-BURST. These runs do not establish successful recovery or an
isolated target capacity; generator misses cannot be attributed solely to the
library under this host contention.

W-WINDOW 32 also rejected two warmup calls with `WINDOW_FULL`; its admitted
warmup and measurement calls all succeeded. The tool used aggregate fixed
concurrency with a round-robin connection selection. An individual session could
remain full while another had spare capacity, so the next selected session
truthfully rejected admission. Measurement peak pending was 2,897 against the
aggregate bound of 3,200. This exposed a workload selection defect, recorded for
its own deterministic asymmetric-completion regression and correction. It is
separate from the carrier-progress defect and does not justify relaxing criteria.

W-CONNECTIONS, W-CHURN, W-FAULT and W-SLOW-CONSUMER were explicitly interrupted
when the campaign was stopped. Neither endpoint report was available for those
four pairs. Their logs and process status remain retained, with no completion,
recovery, duration or capacity claim. The remaining 45 cells were never launched.

## Confirmed carrier-progress defect

The two stalled W-WINDOW clients were inspected with bounded, read-only JDK
thread-dump commands at 13:13:26.765632221 UTC and 13:13:27.912082087 UTC. Each had
12 virtual transport-reader threads pinned while an endpoint coordinator's
intrinsic monitor enclosed a contended notification lock. Their stack path was
`TcpTransport.readLoop` → `EndpointConnection.frame/response` →
`RequestWindow.accept` → `RequestHandle.dispatchCompletion` →
`BoundedNotifications.Reservation.dispatch` → `ReentrantLock.lock` →
`VirtualThread.parkOnCarrierThread`. All 12 carriers were exhausted; queued
notification work could not mount. The main and endpoint deadline threads also
waited for these coordinators.

The dumps are `diagnostics/34-submit-window1-client-virtual-threads.json`
(SHA-256 `b1ff34aed2d76cf4639f849ae9fabf7d53a4f955f51ba26eb2960b195edf3188`)
and `diagnostics/34-submit-window8-client-virtual-threads.json`
(SHA-256 `09fc9ad2361ce3b5ef182580e30fd488902f2d1ba33635f724395cee9668ebc9`).
`diagnostics/actions.jsonl` records the exact commands, bounded waits and successful
exit statuses. JVM attachment can briefly pause execution; this perturbation is
recorded and affected only already-stalled clients.

Fresh two-carrier JVM regressions reproduced the defect without the load campaign.
The [carrier-progress review](reviews/0018-carrier-progress.md) records actual
sequential reds and greens, ownership-preserving reentrant guards, the related
total-admission-deadline fix, all affected type reviews and full verification.
The independent simulator sampler correction and per-session generator selection
have separate evidence. The failed candidate remains unchanged.

## Duration, accounting and cleanup limits

All eight available endpoint reports observed their complete configured
measurement phase, reconciled warmup and measurement accounting, and reported
zero pending/unfinished calls and zero final physical connection/request/reply/
decision/stream ownership. All eight reported successful cleanup and sampler
termination. The two timed-out clients and eight interrupted endpoint processes
have no final report; their cooperative library cleanup is unobserved.

Both timed-out clients were killed by the helper's own deadline and had exit code
-9; their servers exited 0. All four interrupted helpers retained their interrupt
and report-missing errors. The external supervisor stopped its owned process
groups, escalated after a bounded wait where necessary, and reaped them with no
cleanup error. All nine started helper records report OS-process cleanup complete.
That record means process ownership was retired, and does not turn missing or
killed endpoint reports into evidence of successful library shutdown.

## Shared-host interpretation

Six full soaks and up to three healthy baseline/target campaign slots overlapped
the matrix's maximum four pairs. Development build activity also overlapped.
The 94 five-second host samples observed maxima of four matrix pairs, eight matrix
simulator JVMs, 18 other candidate simulator JVMs and 13 campaign helpers. Up to
five build processes were present. Peak one-minute load average was 118.99;
minimum available memory was 17,572,380 KiB. This evidence is a contention
diagnostic, not an isolated capacity comparison or a production SLA.

Exact matrix start/finish intervals use UTC and monotonic time in `events.jsonl`.
External process presence and CPU deltas are sampled, so external exit times lie
between observations; a resident build daemon alone does not prove compilation.
Transport queued frames/bytes, control-PDU write counts and downstream radio
delivery are unavailable through the public facade and are not inferred from
request completions. Resource-growth or capacity claims require a completed,
corrected campaign with its own retained inputs and reports.

## Carrier-corrected candidate: completed 54-pair campaign

This separate immutable checkout was `/tmp/lightweight-smpp-candidate2`, based on
`387aed9ef523c85fb3cfa0f8f245438759deb6fc` plus declared source SHA-256
`fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c`
(227 inputs). Its library JAR SHA-256 was
`609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f`,
and its simulator JAR SHA-256 was
`47705647225df458d2f4fc8cb18c12a37198d82b72c25c8fbace72d308649161`.
The source/archive audit remains in `build/runs/step19-corrected-inputs`.

The same supervisor and full profile settings ran from **2026-09-09
13:46:00.559429 UTC to 14:45:16.433661 UTC**, with a maximum of four concurrent
matrix pairs. The command above was repeated only for this new frozen candidate,
using `--candidate /tmp/lightweight-smpp-candidate2`, output
`build/runs/step19-corrected-matrix`, the same base revision plus this new source
digest, and this exact `--expected-source-sha256`. Each individual command,
configuration and pair interval remains in `matrix.json`; every cell used
`--repeats 1`. The first candidate's failed attempts were retained unchanged.

All 54 helpers finished without process deadlines, supervisor cleanup errors or
source/installation integrity changes. The supervisor exited 1 because the
campaign criteria failed in 38 cells. All 594 captured per-cell JSON/CSV/log
artifact hashes were checked again after completion. The final matrix manifest
SHA-256 is
`806c35a39db883964465c0b028858d5df51f4522b71f6dcff7184e9d7efd1aab`;
`matrix-analysis.json` SHA-256 is
`4ef538425b70206166b8a934ee2523074d8d9ca3ad046376b195041ac5130f17`.

### Complete result matrix

`PASS` means the unchanged helper criteria passed. Every other code denotes a
failed cell: `RECOVERY` is the recovery gate, `WORK` is an originating outcome or
local rejection, `START` means the required bound cohort did not arrive and no
measurement began, and `CHURN` means replacement binding failures. These codes
summarize retained original failures; they are not revised acceptance criteria.
All cells other than the six `START` entries completed their full measurement.

| Profile | 3.4 submit | 3.4 deliver | 3.4 data | 5.0 submit | 5.0 deliver | 5.0 data |
| --- | --- | --- | --- | --- | --- | --- |
| W-RAMP | RECOVERY | RECOVERY | RECOVERY | RECOVERY | RECOVERY | RECOVERY |
| W-BURST | RECOVERY | RECOVERY | RECOVERY | RECOVERY | RECOVERY | RECOVERY |
| W-WINDOW 1 | WORK | WORK | WORK | WORK | WORK | WORK |
| W-WINDOW 8 | WORK | WORK | WORK | WORK | WORK | WORK |
| W-WINDOW 32 | PASS | PASS | WORK | PASS | PASS | WORK |
| W-CONNECTIONS | START | START | START | START | START | START |
| W-CHURN | CHURN | CHURN | CHURN | CHURN | CHURN | CHURN |
| W-FAULT | PASS | PASS | PASS | PASS | PASS | PASS |
| W-SLOW-CONSUMER | PASS | PASS | PASS | PASS | PASS | PASS |

There are 16 passes, 12 recovery failures, 14 work failures, six startup failures
and six churn failures. The 72 per-originator rows in
`final-tables/originating-measurements.csv` retain planned/skipped/attempted/
rejected/admitted counts, every terminal category and status, exact duration,
individual latency populations, sampled resources and each source report hash.
Bidirectional cells contribute one row for each originator. The CSV SHA-256 is
`c5513fcadf9144eaccad5434fd4c8873860a550d535cc320c57e42d66a5ae02c`.
`final-tables/export.json` records its lineage and validation. No latency
percentiles are averaged or converted into an unsupported aggregate population.

### Windows, recovery and bounded rejection

The four single-direction window-32 cells passed with the following measurement
cohorts. Each ran its full 120 seconds with zero skips, rejections or non-success
outcomes. Counts include eventual completion during drain; this table does not
convert them into an offered-rate guarantee.

| Window-32 originator | Successful measurement requests | Individual scheduled p99, ms |
| --- | ---: | ---: |
| 3.4 submit | 2,694,332 | 39.135 |
| 3.4 deliver | 2,414,193 | 42.623 |
| 5.0 submit | 1,714,021 | 46.047 |
| 5.0 deliver | 1,704,506 | 46.655 |

All smaller windows and both bidirectional window-32 cells failed their original
work criteria. The reports preserve local admission/notification pressure and
local write failures. Bidirectional work additionally exposed the simulator
receiver-closure defect described below. A request-window slot and a physical
transport-write reservation have independent lifetimes; window size alone does
not promise that every newly admitted request can immediately enter the writer.

All 12 ramp/burst pairs completed, but every originating endpoint first missed
criteria at level 0. The initial baseline was therefore not established, and
none passed the frozen helper's exact 30-second post-overload recovery check.
Generator skips and insufficient successful rate are preserved in each interval
assessment. Those misses prevent a target-capacity or successful-recovery claim
on this shared host, even where every admitted request succeeded.

### Startup, churn and injected pressure

All W-CONNECTIONS cells failed before measurement; both endpoints reported the
same initial bound count. The requested 1,000-session target was not achieved.

| Profile / direction | Bound before failure | Measured seconds |
| --- | ---: | ---: |
| 3.4 submit | 952 | 0 |
| 3.4 deliver | 942 | 0 |
| 3.4 data | 895 | 0 |
| 5.0 submit | 986 | 0 |
| 5.0 deliver | 978 | 0 |
| 5.0 data | 830 | 0 |

The receiver allows 16 seconds plus the configured connection spacing times the
requested cohort (56 seconds here). Client spacing is a minimum wait after each
completed establishment, so bind work adds to elapsed startup. Code and timings
are consistent with this allowance expiring under contention; logs retain only
exception classes, so the specific cause attribution remains an inference.
No failed startup was presented as a completed 300-second resource measurement.

Every churn cell completed 300 seconds and initiated all 3,000 scheduled
replacements with no skipped or busy replacement slot. The 144 failed replacement
bindings remain criterion failures; all replacement and request ownership retired.

| Profile / direction | Replacements initiated | Bound | Failed |
| --- | ---: | ---: | ---: |
| 3.4 submit | 3,000 | 2,976 | 24 |
| 3.4 deliver | 3,000 | 2,976 | 24 |
| 3.4 data | 3,000 | 2,975 | 25 |
| 5.0 submit | 3,000 | 2,976 | 24 |
| 5.0 deliver | 3,000 | 2,979 | 21 |
| 5.0 data | 3,000 | 2,974 | 26 |

All six fault cells and six slow-consumer cells passed their declared
fault/overload criteria. The following sums cover disjoint measurement requests
across those six pairs, including both data originators; they do not merge
latency percentiles or imply healthy throughput.

| Scenario | Planned | Skipped | Admitted | Success | Peer negative | Timeout |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| W-FAULT | 1,800,000 | 307,791 | 1,492,209 | 1,290,924 | 75,431 | 125,854 |
| W-SLOW-CONSUMER | 1,800,000 | 707,475 | 1,092,525 | 26,314 | 0 | 1,066,211 |

Injected decisions and terminal outcomes remain different populations. Ordered
replies behind a stalled decision can make later requests time out; a 1% stall
injection is not a claim of exactly 1% request timeouts. Across warmup and
measurement, the fault receivers released 147,464 deferred decisions and
cancelled 20,090; slow-consumer receivers released 282,916 and cancelled 907,119.
Neither group retained a deferred decision after cleanup. The very low
slow-consumer success count is stated explicitly and is not a healthy-load pass.

### Accounting and resource retirement

All 108 endpoint reports reconciled both cohorts, reported zero pending or
unfinished requests, completed sampling and reported successful endpoint cleanup.
Every report's final public physical connection/request/reply/decision/stream
ownership counters were zero. The 96 reports from 48 non-startup-failed pairs
observed their full measurement phases; the other 12 correctly recorded no
measurement. Every helper also reaped its owned processes.

The largest sampled per-process RSS was 334,262,272 bytes (about 318.8 MiB), in the
5.0 data churn client, below the unchanged 1 GiB target. This establishes the
sampled bound for these finite runs, not a general absence of leaks or a sustained
capacity. Public queue observations are point-in-time samples and do not expose
transport queue bytes, exact control-PDU write counts or radio delivery. No such
unavailable measurement is inferred from request outcomes. These matrix results
complement the separately recorded [healthy/soak measurements](MEASUREMENTS.md).

### Exact bounded-admission diagnostic

A separate finite cause diagnostic ran during 13:48:13.495644–13:48:38.871030 UTC
against this exact library JAR. It used one extra two-carrier JVM, both real
endpoints, 100 sessions and one pending request per session for a 20-second work
interval. Of 84,958 original calls, 84,408 succeeded and the peer handler received
exactly 84,408. All 550 failures had exact structured outcome
`RequestFailure.WRITE_FAILED / NOT_SENT`, with cause
`TransportFailure.FULL / writeStarted=false`. No failed call was replayed.
A later enquiry succeeded, no pending request remained, and both endpoints
reported complete cleanup.

A peer can respond while a physical writer has emitted bytes but has not retired
its reservation. Response acceptance releases the independent request reservation;
a subsequent request can be admitted while transport capacity remains occupied.
The diagnostic confirms that bounded-admission mechanism and continued progress.
It does not retroactively invent individual cause fields for earlier aggregate
reports. Source, compiled fixture, exact command, interval, output and review are
retained in `diagnostics/window1-write-admission`. The
[external whole-type review](reviews/0018-carrier-progress.md#external-bounded-admission-diagnostic-appendix)
records the fixture outside the project inventory.

### Confirmed simulator receiver-closure defect and later candidate

In 3.4 data window 32, the client recorded 188 status-8 peer negatives. The
server's 1,314,337 received/accepted handler observations equal the client's
warmup-plus-measurement successes exactly; none of the negatives entered that
receiver counter. Code inspection found that local traffic completion closed
ReplyController before endpoint shutdown, while independently timed peer traffic
could still be admitted. The closed receiver returned a failed handler stage
before incrementing its counter, causing a negative protocol reply.

A deterministic, explicitly latched real peer reproduced this premature closure
under both profiles: after local measured work succeeded, an inbound request got
status 8 and the receiver count stayed zero. The exact behavioral red is
`/tmp/step19-receiver-02-peer-red.log`, followed by green
`/tmp/step19-receiver-03-peer-green.log`. The fix retains receiver service through
the original measured drain deadline. See the
[receiver-grace type review and verification](reviews/0018-receiver-grace.md).
Individual old-run negatives are retained; their precise time of occurrence was
not logged, and this finding does not attribute every failed cell to one cause.

That later candidate has the same library JAR but TrafficRunner source SHA-256
`ad934e440cb182b9884c07c076a07b98fde6b16df55c53b5af1e586192dea6e8`,
declared source digest
`2d63cd99c32ca3b197cbc973a100c9280ef00a05d4c5311b0cb7c011bb823a00`,
and simulator JAR SHA-256
`a59d553d3ec84de5d953a927696565f2c498384386a241a74a113426011d1d9e`.
Its separate repeated bidirectional window and full-soak evidence is retained
under `build/runs/step19-final-bidirectional` and described in
[the measurement record](MEASUREMENTS.md). It does not replace or relabel any of
these 54 results, which identify the simulator before that correction.

### Shared-host concurrency evidence

The original matrix sampler captured 683 five-second host observations. It
tracked the matrix, candidate2 peers/helpers and global Gradle processes; sampled
maxima included eight matrix JVMs, 22 external candidate2 JVMs, 15 helpers and
eight build processes. Six soaks, four healthy campaign slots and an additional
JFR pair overlapped portions of this matrix. Peak one-minute host load average was
207.83, and minimum available memory was 14,580,848 KiB. A resident daemon alone
is not evidence that compilation was active; sampled CPU deltas remain recorded.

Candidate3 campaigns started at 14:30 UTC and were outside that candidate2 process
filter. A separately identified observer therefore captured all candidate paths
from 14:32:17.119048 through 14:45:16.994489 UTC, retaining 149 samples. It observed
35 simulator JVMs simultaneously at 14:32:22.209076 UTC. Its source hash, exact
interval, raw samples and summary are in `all-candidate-host-observer.json`,
`all-candidate-host-samples.jsonl` and `all-candidate-host-summary.json`. Parent
campaign journals provide the preceding exact launch interval; the observations
do not pretend that this later sampler covered earlier time.

The matrix and its observer then stopped. A separately identified continuation in
`build/runs/step19-final-host-observer` covers later final-soak and build activity;
the gap from this observer's retirement to that continuation's recorded start is
retained. Matrix events have exact UTC/monotonic timestamps. External process
exits fall between sampled presence observations. These shared-host intervals,
plus the extra one-JVM cause probe, preclude isolated comparisons between protocol
versions, directions or window sizes. No measurement duration or criterion was
relaxed, no candidate source changed during its campaign, and no failed attempt
was silently rerun.
