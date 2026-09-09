# Fresh heavy-load campaigns

The simulator launcher and `simulator/scripts/load_runs.py` run finite client and
server JVMs and retain every attempt, including failed criteria and incomplete
measurements. The library has no load-tool runtime dependency. Build once, then
execute campaigns outside Gradle; a cached test or compilation is never a load
measurement.

```sh
./gradlew :simulator:installDist --console=plain
python3 simulator/scripts/load_runs.py profiles
python3 simulator/scripts/load_runs.py run \
  --profile W-SOAK --version 3.4 --direction submit \
  --revision <full-40-character-Git-SHA> --source-root . \
  --launcher simulator/build/install/simulator/bin/simulator \
  --output /tmp/smpp-soak-34 --repeats 1
```

Use a new output directory for every campaign. The full W-SOAK profile retains
its 60-second warmup, 3,600-second measurement, 100 sessions, 5,000 aggregate
originating requests per second, 160-byte payload and window 32. Run the 5.0
profile separately. A diagnostic override such as `--diagnostic-seconds 5
--warmup-seconds 1 --drain-seconds 1` changes the scenario label to
`W-SOAK-diagnostic`; it cannot establish the full soak target. Overrides are
recorded explicitly. `--direction data` divides each even aggregate rate equally
between the two JVMs; `submit` originates only on the client and `deliver` only
on the server. Requests, responses, binds, keepalives, fixture descriptors and
actual downstream deliveries are different populations.

The campaign owns at most one server/client pair at a time and 1–10 sequential
repeats. Both processes share a finite deadline derived from the configured
phases, connection ramp and cleanup allowance. On timeout or interruption it
attempts to stop both children before bounded reaping. Every process command,
exit code, log and report remains in its repeat directory; cleanup failure stops
subsequent repeats. No workload is replayed to turn a failed result into a pass.

`campaign.json` identifies source inputs and executable artifacts independently.
The exact source/build/script path list accompanies per-file SHA-256 hashes. Its
aggregate SHA-256 starts with the UTF-8 bytes `smpp-simulator-inputs-v1` and a
newline; each lexicographically sorted relative path contributes UTF-8 path,
NUL, lowercase ASCII file SHA-256 and newline. Inputs comprise library and
simulator production Java, explicit root/simulator Gradle build files, wrapper
files and direct simulator Python tools. Tests, reports, caches and generated
outputs are excluded. A supplied checkout and launcher do not by themselves
prove that one built the other: keep the frozen inputs, build log and reported
executable hashes together. Password values are neither command arguments nor
report inputs; lifecycle password options name environment variables.

## Scheduling and cohorts

Arrival-rate scheduling is independent of completion. Each step has a rate and
an explicit hold (`--rates=1000,20000,1000
--holds=PT30S,PT10S,PT20S`); holds sum to the measurement duration. At each poll
the generator attempts at most the newest due arrival and counts earlier due
arrivals as skipped. It never queues a catch-up burst. The deadline pacer parks
until the configured final spin allowance, then checks the monotonic deadline;
the default allowance is 100 microseconds and its maximum is one millisecond.
This addresses repeated full-interval park overshoot without claiming a target
rate that the actual generator did not establish.

Concurrency mode refills a finite window after completion and has no independent
offered rate. Its report therefore uses a null offered-rate value. The combined
tool window is at most 65,536 and sessions at most 4,096. Both modes stop
scheduling before a bounded drain. Late outcomes remain in the original
scheduled cohort; interval throughput counts only completions observed before
that interval ended. Warmup, measurement and drain remain explicit. A failed
warmup or execution abort preserves observed partial accounting.

`--minimum-rate-ratio` is supported only for independent arrival plans. Its gate
compares actual in-phase successful completions per observed phase second with
planned arrivals per configured phase second; a delayed phase exit can therefore
fail the gate even when every planned request succeeded. Empty, unstarted or
shortened measurements cannot pass an enabled originating-rate gate. A
receive-only peer has no originating-rate gate.

Schema 2 reports distinguish planned rate, attempted and admitted requests,
successful and all terminal in-phase completions, generator skips and eventual
outcomes. `offeredLoadEstablished` requires the complete configured arrival
phase, zero skipped arrivals and all planned attempts. Healthy provisional
criteria keep strict zero skipped arrivals; achieved throughput cannot prove
that offered traffic was generated. Invocation latency includes tool payload
generation and terminal polling; no request write-completion latency is
invented from transmission certainty.

## Long scenarios and recovery

`profiles` prints every exact rate, hold, count, connection and fault setting.
The presets are development targets from [WORKLOADS.md](WORKLOADS.md), not claims
that this machine or either endpoint sustains them. The principal schedules are:

| Profile | Measurement and offered work |
| --- | --- |
| W-BASE | 10 sessions, 1,000/s for 120 seconds, 30-second warmup |
| W-TARGET | 100 sessions, 10,000/s for 300 seconds, 30-second warmup |
| W-RAMP | 100 sessions; 1k, 5k, 10k, 20k/s for 60 seconds each, then 1k/s for 60 seconds |
| W-WINDOW | 100 sessions, window 32, fixed concurrency for 120 seconds |
| W-BURST | 100 sessions; baseline 1k/s, three 10-second 20k/s bursts starting at 30, 60 and 90 seconds; 180 seconds total |
| W-CONNECTIONS | 1,000 sessions opened 40ms apart, then 1,000/s for 300 seconds |
| W-CHURN | 100 slots, 1,000/s for 300 seconds; at most 3,000 replacement events at 10/s |
| W-FAULT | 100 sessions, 1,000/s for 300 seconds; selected mix 5% reject, 10% delay, 1% stall |
| W-SLOW-CONSUMER | 100 sessions, 1,000/s for 300 seconds; all ordinary decisions delayed 200ms |
| W-SOAK | 100 sessions, 5,000/s for 3,600 seconds after 60-second warmup |

All these presets use window 32, a 160-byte payload, a two-second request
deadline and a 30-second drain. W-SMOKE supplies a small ten-second functional
run. Healthy W-BASE/W-TARGET/W-SOAK require 99% successful rate, scheduled p99 at
most 100ms, no skipped or unsuccessful work, and complete cleanup. The preset
RSS ceiling is 1GiB per process. Expected-failure profiles still require balanced
ownership, no unfinished requests, valid content and complete cleanup.

For RAMP and BURST, the final return to baseline has a separate first 30-second
cohort; a same-rate remainder preserves the original total duration and count.
Aggregation reports each original scheduled level and the first one missing the
provisional healthy criteria. Recovery requires an established initial baseline,
zero skips, local rejections or non-success outcomes, at least 99% of the
configured baseline rate and 99% of its observed pre-fault successful rate, and
scheduled p99 no more than both 100ms and twice its pre-fault value. A missing
exact 30-second recovery cohort fails this assessment. Shorter gaps between
bursts do not establish a 30-second recovery result. Failed recovery makes the
campaign fail, even when intentional overload outcomes were allowed. The first
unmet level identifies a combined generator/target/host limit; it cannot isolate
the bottleneck without independent process and host observations.

## Replacement and receiver ownership

`--churn-rate` and `--churn-count` belong to the TCP client. Churn starts after
warmup drain, stops admitting replacement events before measurement drain, and
uses the same finite newest-due policy as message arrivals. A busy slot or skipped
event is counted and fails the requested churn-rate gate. A replacement closes
the prior generation and waits for its physical termination before admitting one
new connection attempt. Failure does not enqueue another attempt. Each new
generation resets its content ordinal; pending messages are never replayed.

The report distinguishes initial binds observed by the tool, deliberate
replacement starts/failures/aborts, and fresh replacement binds observed in either
role. `replacementBound` also includes peer-initiated replacements and explicit
reconnect generations. These are lifecycle observations, not counts of bind PDU
writes. `currentConnections` and its observed peak come from the endpoint's
physical connection ownership counter, including connecting or closing owners.

The receiver retains at most one content plan per configured physical slot and
prunes it only after that generation terminates successfully. It adds unfinished
assemblies to a scalar retired count before dropping the plan. SAR fragments from
different generations cannot merge, and churn cannot hide incomplete fixtures.

`--consumer-delay` defers all selected ordinary responses; a selected fault delay
adds `--delay-duration`. Deferred responses share one count bound equal to
connections times window, held until the completion callback returns. Admission
failure selects the explicit capacity response and is reported separately. A
cancelled or expired incoming request releases its stalled/deferred decision on
the next owner advance. The selected accept/reject/delay/stall counters and the
deferred admitted/released/cancelled/rejected counters are distinct and reconcile
at shutdown. A released handler stage does not prove a response was written.

`--verify-fault-mix=true` requires at least 100 selected decisions and checks each
of the four observed fractions against `--fault-tolerance` (an absolute fraction,
default 0.05). Any queue or stream capacity fallback fails the requested mix.
Selections use the configured seed and incoming sequence; identical sequences
across connections select identical decisions. A stalled application decision
remains subject to the endpoint's request deadline and ordered response lane;
the selected mixture is not a guarantee of identical wire outcome percentages.
Use the separate [bounded raw fault peer](FAULT_PEER.md) for actual missing,
duplicate, late, malformed, fragmented or unread wire responses. Its finite
injection and peer observations are reported independently.

## TLS, keepalive and reconnect options

The [lifecycle API](LIFECYCLE.md) remains explicitly opt-in. The simulator accepts
`--tls=on`, PKCS12 `--tls-key-store`/`--tls-trust-store`, client
`--tls-peer-name`, `--tls-handshake-timeout` and server
`--tls-client-certificate`. Key/trust passwords come only from the environment
names specified by `--tls-key-password-env`/`--tls-trust-password-env` (defaults
`SMPP_TLS_KEYSTORE_PASSWORD`/`SMPP_TLS_TRUSTSTORE_PASSWORD`). Configured paths and
environment-variable names are reported; resolved secrets are excluded. TLS
material is validated before report files or endpoint resources are created.

`--keepalive-idle` and `--keepalive-timeout` configure endpoint control progress.
Client `--reconnect-attempts` and `--reconnect-delay` allocate one bounded loop per
fixed slot. The attempt budget includes the initial connection and is finite for
the loop lifetime. Reconnect cannot be combined with deliberate churn or owned
by the listening server. Fresh generations replace slot facades without replay;
loops are cancelled before endpoint shutdown. Reports preserve actual attempt
counts and terminal reasons. `publishedSessions` sums completed loop results;
unfinished loops stay explicit instead of being treated as successful cleanup.

Campaign overrides use repeatable `--client-option name=value` and
`--server-option name=value` arguments. Only the documented lifecycle, content,
pacing and resource-budget settings are accepted; report paths, ports and revision
ownership cannot be overridden through this escape hatch.

## Observations and their limits

Fixed-concurrency generation tracks outstanding tool calls per connection slot.
It chooses a slot with room and releases that local reservation once a terminal
poll is observed. Cancellation alone does not free it. Arrival-rate routing
keeps its original round-robin schedule; actual library refusals are counted
once and are never retried on another connection. See the
[asymmetric-completion regression](reviews/0018-concurrency-selection.md).

Resource observations run on one owned daemon platform thread with no sample
backlog. Filesystem and management observations therefore do not occupy a Java
21 virtual-thread carrier. A slow sample skips observation periods and cannot
directly park the generation owner. The thread is included in the measured
platform-thread count; its termination is part of cleanup.
Reports retain the initial, post-warmup, final and peak observations plus sample
and skipped-sample counts. Linux-only readings use `-1` when unavailable.
Enabled RSS/descriptor criteria fail if their observation is unavailable.
Memory ceilings compare peaks; descriptor and platform-thread growth compare
the post-warmup baseline with final cleanup. Disabled memory ceilings are zero;
disabled growth thresholds are `-1`. A blocked observation still owns its
worker and makes cleanup incomplete after the bounded join.

`pressure.csv` and the JSON `pressure` summary sample physical connections,
last observed session facades, pending request count/bytes, pending application
reply count/retained bytes, deferred decisions and retained content streams.
Request reservations include control requests. Application reply reservations
include received frames and fallback/ready response storage. The public endpoint
snapshot excludes transport queues, one-way operations and control replies.
Independent fields may change between reads; per-field peaks need not have
occurred simultaneously. Closed last-generation facades can remain in the fixed
slot list, so `observedSessions` is not a count of active binds.

Sampled ceilings follow the actual endpoint configuration: request count
connections × window; application reply count connections × (window + 8); each
request/reply byte allowance is connections × max(1MiB, window × (payload + 1024)).
Deferred decisions are bounded by connections × window and retained streams by
connections. Final physical/request/reply/decision/stream ownership must be zero.
Sampling can miss transient peaks; the library's admission limits remain the
actual enforcement mechanism. Physical transport queue occupancy and actual
bind/enquire/unbind/application-response write counts are explicitly unavailable,
not reconstructed from request invocations or callbacks. Broadcast reports count
originating PDUs containing two fixture area descriptors; actual radio broadcasts
and recipient deliveries remain unknown.

## Repeat aggregation

The runner writes separate client and server aggregate reports. Manual merging
accepts 1–128 distinct reports from compatible sequential runs:

```sh
python3 simulator/scripts/load_runs.py aggregate \
  /tmp/run-a/client/report.json /tmp/run-b/client/report.json \
  --output /tmp/client-aggregate.json
```

Schema, configuration (apart from ephemeral port), source identity, executable
hashes and histogram settings must agree. Cohort and histogram populations must
reconcile. Inputs are bounded to 32 MiB each and merged histograms to 65,536
buckets. Percentiles come from combined bucket counts; they are never averages
of individual percentiles. This follows the pinned
[HDR 2.2.2 percentile implementation](https://raw.githubusercontent.com/HdrHistogram/HdrHistogram/HdrHistogram-2.2.2/src/main/java/org/HdrHistogram/AbstractHistogram.java).
Overflow samples stay separate from recorded buckets and preserve their raw
maximum. Aggregate throughput divides total in-phase successes by summed phase
time; individual rate and p99 spread remain visible. That number is not a
distributed wall-clock rate for overlapping processes.

Each cohort also checks that in-phase successes are bounded by its successful
terminal outcomes and completions, and completions by all observed terminal
outcomes. Per-run resource retention uses only the fixed numeric schema; unknown
report extensions do not accumulate in the aggregate. Histogram overflow makes
enabled latency criteria fail. Extreme percentiles from small populations remain
sparse observations; the recorded sample count is required to interpret them.

The isolated functional evidence is in the [Step 18 review](reviews/0017-load-scenarios.md).
Fresh load/soak claims belong to the measured campaign artifacts and the release
measurement report, with machine details, binary/source hashes and concurrent
host activity. The initial shared-host diagnostics missed strict offered-load
criteria despite successful admitted requests; neither this implementation nor
its unit tests establishes the provisional targets.
