# Step 18–19 measurement record

These are fresh development measurements on one shared workstation. The
throughput goals in [WORKLOADS.md](WORKLOADS.md) remain provisional. A missed
arrival invalidates a target-capacity comparison even when every admitted
request succeeds. Source fingerprints, executable hashes and recorded argument
vectors identify each run; compilation-cache results are never measurements.

## Earlier integrated steady diagnostics

Before the final lifecycle and scenario integration, an isolated checkout at
Step 16 commit `eee514218ee08eee0481d1edb8d8395d0a2b1b6e` used an early Step 18
steady-workload snapshot. It included the complete SMPP 5.0 broadcast library,
but not Step 17 TLS/keepalive/reconnect or the final Step 18 ownership/recovery
reporting. These results identify that snapshot, not the release candidate.

The exact declared production/build/tool source digest is
`17d3615dbe9c1f4e74eb93efa4d874e5c7b7c35145e50b453b1341e369bfecd5`.
The source algorithm hashes sorted relative paths and per-file SHA-256 values,
with the `smpp-simulator-inputs-v1` prefix; each campaign preserves its complete
input manifest. Separate installed client/server JVMs ran on loopback with Java
21.0.12+8, Linux 7.0.0-31-generic amd64, 12 logical processors, a 160-octet binary
payload, TRX binds, window 32, 2-second request deadlines, seed 1 and
`-Xms256m -Xmx512m`. Development processes shared this host.

| Run | Measurement | Planned | Successful outcomes | Skipped | Successful/s in phase | Scheduled p99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| W-BASE, 3.4 submit, repeat 1 | 120 s after 30 s warmup | 120000 | 119985 | 15 | 999.866 | 1.003 ms |
| W-BASE, 3.4 submit, repeat 2 | 120 s after 30 s warmup | 120000 | 119999 | 1 | 999.983 | 1.002 ms |
| W-BASE, 3.4 submit, repeat 3 | 120 s after 30 s warmup | 120000 | 119966 | 34 | 999.708 | 1.004 ms |
| W-TARGET, 5.0 deliver | 300 s after 30 s warmup | 3000000 | 2990939 | 9061 | 9969.793 | 1.201 ms |

All four runs failed their zero-skip criteria. Every admitted measurement request
had a successful response; no other terminal outcome occurred, and both
endpoints reported complete cleanup. The three baseline repeats had a rate
spread of 0.275 requests/s and p99 spread of 1.002–1.004 ms. These are individual
percentiles, not averaged values. The target delivery run sampled peak RSS of
288.23 MiB for the originating server and 253.57 MiB for the receiving client.

Raw reports, process logs, resource series, exact commands and source manifests
remain under `build/runs/step18-steady-diagnostics/`. Campaign SHA-256 values:

- `base34-submit-corrected/campaign.json`:
  `5e5069d19a944484a689250cc2fb8f8545904be09af16361bfdb109edba811d5`.
- `target50-deliver/campaign.json`:
  `ece75db8337238e0a0312db7750525e41667078c4d8a42497cf8ccf4f3fd1b15`.

An earlier `base34-submit` attempt supplied an incorrect Git revision string.
It was interrupted as soon as the provenance error was noticed; its original
artifacts were retained and it is excluded from valid measurement claims.
Subsequent commands obtained the revision directly with `git rev-parse HEAD`.
No failed report was deleted or silently replaced by a passing repeat.

## First candidate: carrier-starvation failure

The first integrated candidate retained base revision
`387aed9ef523c85fb3cfa0f8f245438759deb6fc` plus declared-input digest
`b31f25ac069814c7dc17a148ffda671f985cb7f925cc571a9dd22d16bfe20cf2`.
The 221-path manifest, complete declared-input archive, additional licensing/TLS
fixture inputs and build/artifact evidence are retained under
`build/runs/step19-candidate-inputs/`. Its library SHA-256 is
`d7ed9830384a5acdf13475f4dba6e61c3ef061dd117134527a2a816ae98c89ea`;
its simulator SHA-256 is
`c7122a06eef4550f9168831b04955eaaa6f2ca5852dfcca492c6ecf6e0112623`.
All six protocol/direction W-SMOKE pairs and a separate 5.0 bidirectional TLS
smoke passed, but those short checks did not establish sustained liveness.

The full campaigns started at 13:07 UTC on 9 September 2026. Six soak pairs,
three healthy-profile campaign slots and four scenario slots shared the host.
The first completed burst retained complete accounting and cleanup but failed
its strict recovery criteria. Both fixed-window clients (windows 1 and 8) then
remained blocked beyond their configured run/drain interval and reached their
helper deadlines without final client reports.

Platform and virtual-thread dumps identify a Java 21 carrier-starvation defect.
Each affected client had all 12 default carrier threads occupied by TCP-reader
virtual threads holding intrinsic connection monitors while waiting for the
notification dispatch lock. Notification workers needed a carrier to progress;
the main and endpoint-deadline threads were also blocked on connection monitors.
This is a liveness failure, separate from generator skips or capacity thresholds.
The exact dumps remain under `step19-candidate-matrix/diagnostics/`.

Both supervisors were stopped after the defect was established. Matrix cleanup
finished at 13:15:32 UTC; the healthy/soak supervisor finished at 13:15:35 UTC.
All owned process groups were reaped. That process cleanup is not evidence of
successful endpoint shutdown: interrupted or timed-out JVMs can have missing
reports and were retained as incomplete failures. None of these interrupted
soaks is a full 60-minute result. Completed, interrupted and never-started cells
remain distinct in the original supervisor manifests, with no erased failures
or substituted passing attempts.

## Corrected candidate campaigns

The corrected candidate retains base revision
`387aed9ef523c85fb3cfa0f8f245438759deb6fc` plus declared-input digest
`fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c`.
The immutable 227-path source manifest, input archive and artifact fingerprints
are retained under `build/runs/step19-corrected-inputs/`. This is the snapshot
before the final simulator receiver-drain correction. The library binary is
`609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f`;
the simulator binary is
`47705647225df458d2f4fc8cb18c12a37198d82b72c25c8fbace72d308649161`.
Real two-carrier regressions demonstrate the failure and correction; additional
listener, reconnect and admission-deadline cases protect related lock boundaries.
See the [whole-type review and red/green record](reviews/0018-carrier-progress.md).

Fresh campaigns started at 13:45:35 UTC on 9 September 2026. Six complete W-SOAK
pairs run alongside at most four healthy-profile campaigns and four separately
supervised scenario pairs. Each protocol/direction combination has three
sequential W-BASE, W-BASE-with-TLS and W-TARGET repeats. The same source and
executable hashes identify each role in every pair. The separate
[54-cell matrix](MATRIX_RESULTS.md) records ramps, bursts, three windows,
connection establishment, churn, selected faults and slow consumers. No failing
attempt is filtered, retried silently or given weaker criteria.

Each simulator runs as its own JVM on the same 12-logical-processor Linux host,
using Java 21.0.12+8, `-Xms256m -Xmx512m`, the default collector, loopback, TRX
binds, binary DCS 4, seed 1 and 160-octet payloads. TLS baseline runs explicitly
trust the public development certificate and verify the `localhost` peer name.
The six soaks use 100 connections, window 32, a 60-second warmup and a full
3,600-second measurement interval. These overlapping runs exercise sustained
progress and bounded ownership; they do not isolate the target's capacity from
generator and host contention. The JFR and finite wire checks are separately
identified in their evidence records.

The healthy/soak controller, per-repeat command vectors, reports, histogram
aggregates and resource/ownership CSVs are retained under
`build/runs/step19-corrected-healthy/`. The completed outcomes follow. A W-SOAK
result requires the complete 60-minute measurement interval; a shortened or
interrupted diagnostic is never labeled a full soak.

### Completed corrected-library results

All 24 campaigns finished by 14:48:02 UTC: 54 repeated baseline/TLS/target
pairs and six full soak pairs. All 60 pairs failed their original criteria.
All 120 endpoint reports reconcile their counters, record completed endpoint
and sampler cleanup, and retain zero final connections, requests, replies,
decisions and streams. Every process pair was reaped; no helper timed out or
final report was missing. Receiving-only phase durations have the qualification
below. Sampled RSS across all roles peaked at 325.309 MiB.

Across all measurement cohorts, 166320000 arrivals were planned and 79711782
were skipped. Of 86608218 attempts, 70 were rejected locally and 86608148 were
admitted: 86608024 SUCCESS, 62 PEER_NEGATIVE and 62 LOCAL_FAILURE, with zero
pending. The separate warmup cohorts planned 8280000, skipped 4777256 and
admitted 3502744, all successful. Generator skips are failures under the original
criteria; a successful admitted request does not establish the offered rate.
Earlier bidirectional failures precede the receiver-drain correction; the
regression does not identify every bulk failure's cause.

The table retains all three repeats of each baseline/target combination. A
bidirectional `data` profile splits the aggregate configured rate equally
between client and server. Baseline and TLS baseline use 10 connections,
1000 aggregate arrivals/s, 30 s warmup and 120 s measurement. Target uses
100 connections, 10000 aggregate arrivals/s, 30 s warmup and 300 s measurement.
All use window 32, 2 s request deadlines and a configured 30 s drain.
Rates below count successes completed inside each observed measurement phase;
SUCCESS totals also include any completion during drain. Scheduled p99 ranges
are the individual repeat values, including admitted negative outcomes. The
merged p99 is recomputed from compatible histogram bucket counts, never averaged.

| Campaign | Originator | SUCCESS total | Skipped total | Phase successful/s range | Scheduled p99 range, ms | Merged p99, ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `w-base-34-data` | client | 172521 | 7468 | 469.275–488.965 | 7.183–11.015 | 9.399 |
| `w-base-34-data` | server | 172407 | 7593 | 470.516–488.874 | 7.139–10.487 | 9.223 |
| `w-base-34-data-tls` | client | 171208 | 8783 | 466.940–489.429 | 7.335–11.183 | 9.983 |
| `w-base-34-data-tls` | server | 171651 | 8349 | 466.721–491.206 | 7.339–11.815 | 10.071 |
| `w-base-34-deliver` | server | 316110 | 43890 | 824.241–943.169 | 6.719–10.439 | 9.087 |
| `w-base-34-deliver-tls` | server | 321802 | 38198 | 825.382–932.312 | 6.951–11.223 | 8.903 |
| `w-base-34-submit` | client | 314983 | 45017 | 806.966–952.465 | 6.399–10.367 | 9.167 |
| `w-base-34-submit-tls` | client | 323750 | 36250 | 836.271–937.647 | 7.015–11.015 | 8.767 |
| `w-base-50-data` | client | 173688 | 6312 | 473.948–487.606 | 7.283–9.983 | 8.423 |
| `w-base-50-data` | server | 174078 | 5922 | 475.891–487.621 | 7.395–9.935 | 8.367 |
| `w-base-50-data-tls` | client | 170547 | 9453 | 451.141–485.603 | 8.007–13.671 | 10.839 |
| `w-base-50-data-tls` | server | 169478 | 10510 | 445.085–483.652 | 8.039–13.743 | 10.855 |
| `w-base-50-deliver` | server | 325472 | 34528 | 881.466–933.840 | 6.679–9.007 | 8.083 |
| `w-base-50-deliver-tls` | server | 310744 | 49256 | 765.385–913.683 | 7.519–12.479 | 9.823 |
| `w-base-50-submit` | client | 315769 | 44231 | 829.181–945.523 | 6.419–11.191 | 9.487 |
| `w-base-50-submit-tls` | client | 309778 | 50222 | 777.364–927.285 | 7.003–12.991 | 10.055 |
| `w-target-34-data` | client | 1917992 | 2581967 | 1986.116–2320.153 | 8.863–11.287 | 10.335 |
| `w-target-34-data` | server | 1903279 | 2596718 | 1956.613–2207.913 | 8.951–10.343 | 9.935 |
| `w-target-34-deliver` | server | 4279764 | 4720236 | 4500.107–4975.627 | 15.703–22.319 | 19.151 |
| `w-target-34-submit` | client | 4479096 | 4520904 | 4750.254–5141.470 | 15.279–19.951 | 17.007 |
| `w-target-50-data` | client | 1657050 | 2842929 | 1302.923–2728.250 | 8.031–16.231 | 13.087 |
| `w-target-50-data` | server | 1541319 | 2958672 | 1349.775–2264.930 | 8.759–16.183 | 13.679 |
| `w-target-50-deliver` | server | 4384170 | 4615830 | 3680.878–7153.223 | 12.527–38.879 | 29.743 |
| `w-target-50-submit` | client | 3926695 | 5073305 | 3563.635–4892.580 | 14.503–37.375 | 24.111 |

### Six complete corrected-library soaks

The following eight originating JVMs all observed at least 3600 s of
measurement. Each unidirectional originator planned 18000000 arrivals at
5000/s; each bidirectional originator planned 9000000 at 2500/s. All six
pairs failed the original zero-skip/rate criteria. Only the 3.4 data client
also recorded non-success outcomes: 65 admission rejections, 1 PEER_NEGATIVE
and 22 LOCAL_FAILURE; these remain distinct from its successful responses.
No other originating soak cohort had a non-success outcome or rejection.

| Version / direction | Originator | Observed measurement, s | Skipped | SUCCESS | Phase successful/s | Scheduled p99, ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 3.4 / data | client | 3600.000002017 | 3321065 | 5678847 | 1577.457 | 8.831 |
| 3.4 / data | server | 3600.000000348 | 3297429 | 5702571 | 1584.046 | 8.775 |
| 3.4 / deliver | server | 3600.000006250 | 8761161 | 9238839 | 2566.340 | 9.007 |
| 3.4 / submit | client | 3600.000635993 | 9153497 | 8846503 | 2457.360 | 9.015 |
| 5.0 / data | client | 3600.023074790 | 3359759 | 5640241 | 1566.723 | 8.815 |
| 5.0 / data | server | 3600.000000347 | 3304110 | 5695890 | 1582.187 | 8.711 |
| 5.0 / deliver | server | 3600.000299888 | 9104866 | 8895134 | 2470.870 | 8.951 |
| 5.0 / submit | client | 3600.000005743 | 9093352 | 8906648 | 2474.067 | 9.119 |

The four receive-only JVMs (submission servers and delivery clients) report the
configured 3600 s measurement and 30 s drain. Those fields are not independent
monotonic measurements of the receiving phase. Their resource CSVs span
3630.017–3630.030 s from the recorded baseline to final observation. Receiver
counters cover their full listening lifetime and cannot be assigned to the
originator's measurement cohort without independent wire evidence.

The following resource values are **post-warmup baseline / sampled peak / final**.
Peaks cover the complete sampling lifetime, including startup and warmup.

| Version / direction / JVM | Used heap, MiB | RSS, MiB | File descriptors | Platform threads |
| --- | ---: | ---: | ---: | ---: |
| 3.4 / data / client | 33.658 / 159.088 / 82.090 | 277.234 / 294.168 / 286.008 | 116 / 116 / 16 | 89 / 89 / 23 |
| 3.4 / data / server | 37.753 / 159.365 / 56.533 | 279.891 / 290.230 / 288.203 | 117 / 117 / 16 | 93 / 93 / 23 |
| 3.4 / deliver / client | 154.416 / 156.487 / 80.478 | 263.449 / 275.043 / 269.582 | 116 / 116 / 16 | 89 / 89 / 23 |
| 3.4 / deliver / server | 55.521 / 159.155 / 88.171 | 267.688 / 281.922 / 278.336 | 117 / 117 / 16 | 29 / 29 / 23 |
| 3.4 / submit / client | 10.301 / 158.860 / 114.915 | 268.320 / 282.770 / 275.094 | 116 / 116 / 16 | 25 / 25 / 23 |
| 3.4 / submit / server | 138.453 / 156.480 / 151.470 | 273.105 / 285.992 / 278.992 | 117 / 117 / 16 | 93 / 93 / 23 |
| 5.0 / data / client | 140.551 / 159.569 / 12.633 | 282.055 / 295.875 / 285.352 | 116 / 116 / 16 | 89 / 89 / 23 |
| 5.0 / data / server | 89.538 / 159.119 / 12.152 | 280.000 / 294.469 / 286.004 | 117 / 117 / 16 | 93 / 93 / 23 |
| 5.0 / deliver / client | 18.490 / 156.595 / 103.564 | 253.641 / 287.516 / 281.484 | 116 / 116 / 16 | 89 / 89 / 23 |
| 5.0 / deliver / server | 142.403 / 159.323 / 9.379 | 277.871 / 285.000 / 285.000 | 117 / 117 / 16 | 29 / 29 / 23 |
| 5.0 / submit / client | 72.427 / 159.150 / 68.167 | 270.965 / 286.766 / 278.668 | 116 / 116 / 16 | 25 / 25 / 23 |
| 5.0 / submit / server | 65.457 / 156.572 / 15.558 | 266.535 / 275.539 / 268.562 | 117 / 117 / 16 | 93 / 93 / 23 |

All twelve JVMs show repeated used-heap reductions accompanied by increasing
cumulative GC time; sampled post-baseline used heap ranges collectively from
5.366 to 159.569 MiB. Initial, baseline and final committed-heap snapshots are
258 MiB. Commitment is absent from the CSV, so these three snapshots cannot
establish its continuous history. GC observations contain cumulative collection
time without collection counts or collector-phase identification. No GC was
forced for these observations.

The largest approximately final-ten-minute RSS increase, +15.562 MiB, belongs
to the receive-only 5.0 delivery client. Its traffic interval from baseline-relative
3000.974 to 3599.973 s stays within 265.711–265.945 MiB. At relative
3599.973 → 3600.972 s, RSS changes 265.742 → 287.516 MiB while descriptors
drop 116 → 16. Used heap changes 95.564 → 97.564 MiB and cumulative GC time
stays 5722 ms. Final RSS is 281.484 MiB after receive drain. This localizes the
increase to connection retirement; the available data cannot identify its
memory component or cause. The other eleven JVMs' approximate tail RSS changes
range from −3.477 to +2.105 MiB. Sampled peaks differ from Linux `VmHWM`;
the highlighted client's final high-water reading is 294.383 MiB.

Final descriptors and platform threads remain 16 and 23, compared with initial
13 and 6. Their sampled peaks do not exceed the post-warmup baselines. The
historical `observedSessions` field remains 100 even after live ownership reaches
zero. These runs demonstrate retirement of reported ownership and bounded
observed resource use; they do not prove leak freedom.

The independent read-only analysis reconciles raw cohorts, status populations,
latency histogram counts, per-repeat rates, source manifests and artifact hashes.
It reports no integrity issues. Full JSON/CSV records and hashes remain under
`build/runs/step19-corrected-healthy/`. The finalized `analysis-final.json`
SHA-256 is
`a63dc1cd82f402daf49a921139afdb5e511a19d377911a7f7bdd8d6bd9dfc1c9`.
The analyzer SHA-256 is
`d8d90e02ed530e999a8cd165c37692d9245fe8deaf97b29bb819112a7617b825`;
the completed supervisor SHA-256 is
`35db2eff3a188142f43876a8c7c0602a0a3b0866fc38ff895ac53330e94da7f7`.

## Final simulator: complete receiver drain

The corrected library continued to make progress, but bidirectional runs exposed
a separate simulator lifecycle issue. Once an originator's own pending calls
settled, its runner could return early and close the receiver while the peer was
still sending. A coordinated real-peer regression reproduced status 8 under
both profiles with no counted receiver acceptance. The correction keeps receiver
maintenance active through the original measured drain deadline, without
resetting it or changing warmup, request deadlines, rates or response criteria.
The [review](reviews/0018-receiver-grace.md) records actual red/green results and
all affected types. Earlier bulk negative responses remain observations; the
regression does not retrospectively establish the cause of every local failure.

The final simulator snapshot was frozen at 14:29:54 UTC. Its declared-input
digest is `2d63cd99c32ca3b197cbc973a100c9280ef00a05d4c5311b0cb7c011bb823a00`,
with the same Git base. Only `TrafficRunner.java` differs in the production/build
manifest. The library, source/Javadoc archives and publication metadata remain
byte-identical. The new simulator JAR is 206843 bytes, SHA-256
`a59d553d3ec84de5d953a927696565f2c498384386a241a74a113426011d1d9e`.
Its separate input archive and manifest are retained under
`build/runs/step19-final-inputs/`; archive SHA-256 is
`c090e3ade02a73f5f10e9d2a2ca1da07cfb6f8899fee11768ce765871de7bf4e`.

At 14:30:11 UTC, fresh bidirectional W-WINDOW32 campaigns started with three full
repeats per profile, alongside one complete W-SOAK per profile. These four pairs
initially overlapped the earlier candidate's campaigns and finite wire checks;
they are separately identified in host observations. The original candidate2
results remain before the receiver-grace correction and are never relabeled as
measurements of this final simulator. The new command vectors and per-repeat
reports are under `build/runs/step19-final-bidirectional/`. The complete fixed-window
and full-hour results follow.

### Final bidirectional fixed-window repeats

All six fresh pairs (three per profile) passed their original W-WINDOW32 criteria
and were reaped by 14:40:15 UTC. All twelve role reports completed the full
120 s measured phase and original 30 s receiver drain, with successful endpoint
and sampler cleanup and zero final live ownership. Measurement cohorts contain
10913898 SUCCESS outcomes and no skipped work, admission rejection, other
terminal outcome or remaining pending request. Warmup remains a separate cohort.
All sampled resources remain within the configured 1024 MiB RSS bound.

Each pair uses 100 connections, window 32, 30 s warmup, two-second request
deadlines and 160-octet opaque data messages in both directions. This is a
fixed-concurrency workload: it has no independent offered arrival rate and its
profile declares no scheduled-p99 gate. One 3.4 client repeat exceeds the
separate healthy-profile 100 ms p99 target; the W-WINDOW pass does not imply
that different target passed. These observations do establish progress and
successful replies for all admitted measurement requests at this exact load.

| Profile / originator | SUCCESS total | Phase successful/s range | Scheduled p99 range, ms | Merged p99, ms |
| --- | ---: | ---: | ---: | ---: |
| 3.4 / client | 2560822 | 7029.552–7252.064 | 74.367–105.215 | 89.663 |
| 3.4 / server | 2731120 | 6921.352–8618.365 | 83.711–95.871 | 88.447 |
| 5.0 / client | 2768185 | 7278.699–8499.170 | 72.447–92.671 | 82.367 |
| 5.0 / server | 2853771 | 7639.832–8480.723 | 76.671–84.991 | 81.215 |

The independently recomputed analysis reports no accounting, histogram,
source-identity or artifact-integrity issues. It includes only finished
campaigns; at that checkpoint its supervisor was still running the two soaks.
The exact campaign SHA-256 values are:

- `w-window-34-data/campaign.json`: `a8d1b1ea62a27b4377f25cc3d3dfb1fab6540e0a9b6ba0be5d8368ad352b6a7e`.
- `w-window-50-data/campaign.json`: `4ddd0109db22ef427118b1f70c5dc38d25f2d338700316348c77aa1d2525f798`.

### Final two complete bidirectional soaks

Both final-candidate W-SOAK pairs completed their original full hour and drain
on 9 September 2026. The 3.4 helper finished at 15:31:55.868791 UTC and the 5.0
helper at 15:31:56.556641 UTC. Both exited 1 for failed workload criteria, with
no timeout, interruption or process-cleanup error. Each of the four originating
JVMs actually measured at least 3600 s and then maintained the receiver for the
full original 30 s drain; these are observed monotonic durations.

Across the four measurement cohorts, 36000000 arrivals were planned,
4108016 were skipped and 31891984 were attempted and admitted. **All 31891984
admitted requests ended in SUCCESS**, with no admission rejection, other terminal
outcome, histogram overflow or pending request. Each role completed exactly one
of those successes during drain; the other 31891980 completed inside their
respective measurement phases. Separate warmup cohorts planned 600000,
skipped 432379 and admitted 167621, all successful. Every receiver's full-lifetime
accepted count equals the opposite originator's warmup-plus-measurement admitted
count. No negative outcome or receiver-count discrepancy was recorded in these
final runs.

The original 99% successful-rate and zero-skipped-work criteria failed for every
originator. Reports contain `unsuccessful-work` for both cohorts and
`successful-rate-below-threshold`; the configured 2500/s per-role offered load
was not established. The 100 ms scheduled-p99 and 1024 MiB sampled-RSS ceilings
were not the failing criteria. Initial host overlap is recorded below; this
experiment does not isolate the target's capacity or prove the cause of every
missed arrival. Successful replies for admitted requests do not change the
campaigns' failed status.

| Profile / originator | Observed measurement, s | Observed drain, s | Skipped | SUCCESS | Phase successful/s | Scheduled p99, ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 3.4 / client | 3600.000002679 | 30.000017658 | 1008651 | 7991349 | 2219.819 | 5.335 |
| 3.4 / server | 3600.000002934 | 30.000014988 | 1033920 | 7966080 | 2212.800 | 5.339 |
| 5.0 / client | 3600.000000168 | 30.000000546 | 1055116 | 7944884 | 2206.912 | 5.283 |
| 5.0 / server | 3600.000000112 | 30.000000119 | 1010329 | 7989671 | 2219.353 | 5.499 |

All four reports confirm endpoint and sampler cleanup. Final physical
connections, pending requests/bytes, pending replies/bytes, pending decisions and
retained streams are zero; all 100 receiver streams per role retired. The
historical session count remains 100. Resource values below are post-warmup
baseline / full-lifetime sampled peak / final, with no forced GC.

| Profile / JVM | Used heap, MiB | RSS, MiB | Descriptors | Platform threads | Approximate last-ten-minute RSS change, MiB |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3.4 / client | 63.575 / 160.080 / 131.085 | 278.094 / 288.375 / 285.020 | 116 / 116 / 16 | 89 / 89 / 23 | -1.613 |
| 3.4 / server | 61.514 / 160.697 / 129.691 | 281.203 / 293.488 / 290.039 | 117 / 117 / 16 | 93 / 93 / 23 | -1.055 |
| 5.0 / client | 118.549 / 159.418 / 99.974 | 279.750 / 304.883 / 301.023 | 116 / 116 / 16 | 89 / 89 / 23 | +4.086 |
| 5.0 / server | 104.439 / 161.064 / 80.160 | 279.309 / 298.273 / 290.645 | 117 / 117 / 16 | 93 / 93 / 23 | -0.148 |

Initial, baseline and final committed-heap snapshots are 258 MiB. These three
snapshots do not establish continuous heap commitment. Final descriptors and
platform threads are 16 and 23, versus initial 13 and 6. Resource samples include
startup and shutdown; their last-ten-minute deltas are not exact traffic-phase
windows. The measurements support retirement of reported ownership and bounded
observed resource use at this load, without establishing leak freedom.

During approximately the last ten measurement minutes, RSS rises by 0.680,
0.629, 0.535 and 0.641 MiB in the table's order. The 5.0 client's larger tail
including shutdown contains a final-sample change of 297.465 → 301.023 MiB,
with descriptors 116 → 16, used heap 96.974 → 99.974 MiB and unchanged cumulative
GC time of 4912 ms. That establishes timing, without identifying which memory
component caused the change. Used heap repeatedly falls alongside increasing
cumulative GC time. All four whole-run sampled RSS peaks occur within the first
ten measurement minutes. Baseline-to-final CSV spans are 3630.030–3630.092 s;
the underlying reports independently retain the precise traffic/drain durations.

Both complete soaks and all six final window pairs use the identical declared
source digest and executable hashes recorded above. The independent final
analysis reports no accounting, histogram, source or artifact-integrity issue:
all eight process pairs were reaped, all sixteen endpoint reports completed
their full measurement intervals, and all sixteen report endpoint/sampler
cleanup with zero final live ownership. The final campaign identities are:

- `w-soak-34-data/campaign.json`: `df1c8e9467662e738a63097d2cdcb7625cd673027f92291cd02541b419c1f54b`.
- `w-soak-50-data/campaign.json`: `f6d51fd4a145a36a8bb94a3ff86c7c043e3f8f7ea212774cd31ead9192be48f7`.

The final `analysis-final.json` SHA-256 is
`199565ed3e923dc870d9381a53faa98aeb3076e36ba057696c9e11c1d1992b47`;
the completed supervisor SHA-256 is
`929363e161524ddf40c4d32436efd93512c581dbeb169249d7c19e20ed6d3a4c`.
The 22 closed soak artifact files are individually hashed in
`soak-artifacts.json`, SHA-256
`beec7f231056a3cf51a9e43189202a4bb5f1d3887ff9ce2a382da417b2c80e60`.

### Shared-host observations

The final candidate initially overlapped the corrected-library campaigns and
finite wire checks. The broader observer recorded 35 simulator JVMs at
14:32:22 UTC; [matrix evidence](MATRIX_RESULTS.md) records its exact coverage
and the earlier observer's candidate-path filter. That observer stopped at
14:45:16.994489 UTC. The separate continuation began at 14:47:08.563879 UTC;
the intervening 111.569 seconds are an observation gap, not continuous coverage.
Its five-second process samples retain process start identities, CPU counters,
memory, host load and pressure. A resident Gradle daemon alone is not evidence
of active build work. The root and fresh-checkout verification between
14:52 and 15:01 UTC is separately timed in [release evidence](RELEASE.md).

The continuation is retained under `build/runs/step19-final-host-observer/`.
It stopped explicitly at 15:32:09.727825 UTC, after both final campaigns and
all owned child processes completed. Its final manifest SHA-256 is
`34fc8161a62788bd9854359f9bf93a876f9aba170ceee5cc8b6bae648c552e84`.
Endpoint reports and campaign journals remain authoritative for workload outcomes. Six standalone controller,
analysis, reproduction, observation and freeze helpers are preserved as source
snapshots under `build/runs/step19-investigation-tools/`, whose manifest SHA-256
is `9e450e114a1ff91e6a398ea009ff2d94afd8c84396fdeaf0f66b76c38584e56d`.
These local investigation artifacts add no library or simulator dependency.
