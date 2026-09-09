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

The corrected candidate requires a genuine concurrency regression and fresh
full-duration runs after the library fix. Final source/artifact identities and
outcomes are recorded here after execution. A W-SOAK result requires the full
60-minute measurement interval; a shortened diagnostic is never labeled a full
soak.
