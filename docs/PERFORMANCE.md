# Performance qualification

The required minimum is **99% of all planned originating requests succeeding**.
Generator skips remain in that denominator. Each originating role must also
complete successful requests within the measured phase at no less than 99% of
its configured offered rate. Unexpected request failures, unfinished work,
incomplete cleanup, and violated latency/resource limits still fail a healthy run.
See [workload criteria](WORKLOADS.md) and [report interpretation](LOAD_TESTING.md).

## Current qualification

All six full five-minute `W-TARGET` runs passed the 99% floor on 2026-09-10.
Each used 100 sessions and 10,000 aggregate scheduled requests/s. There were
17,988,550 successful measured requests from 18,000,000 planned requests.
Every originating role passed individually; the lowest role success was
99.7257%. All admitted warmup and measured requests succeeded, matched their
peer's received/accepted totals, and completed with zero final active ownership.

| Profile / traffic | Successful / planned | Lowest role success | Lowest role in-phase rate | Highest scheduled p99 |
| --- | ---: | ---: | ---: | ---: |
| 3.4 submission | 2,998,524 / 3,000,000 | 99.9508% | 99.9508% | 0.102 ms |
| 3.4 delivery | 2,999,472 / 3,000,000 | 99.9824% | 99.9824% | 0.101 ms |
| 3.4 bidirectional data | 2,991,925 / 3,000,000 | 99.7257% | 99.7257% | 0.404 ms |
| 5.0 submission | 2,999,533 / 3,000,000 | 99.9844% | 99.9844% | 0.102 ms |
| 5.0 delivery | 2,999,512 / 3,000,000 | 99.9837% | 99.9837% | 0.102 ms |
| 5.0 bidirectional data | 2,999,584 / 3,000,000 | 99.9850% | 99.9849% | 0.203 ms |

Success is eventual measurement-cohort success divided by all planned requests.
The in-phase rate additionally uses actual elapsed measurement time and excludes
successes completed during drain. Rounded percentages are for display; the
assessment uses exact integer comparisons. Bidirectional totals combine two
1,500,000-request plans, but each role must independently pass. These are one
fresh pair per profile/direction, not statistical comparisons between protocols.
The highest sampled peak RSS was 294.49 MiB per JVM against a 1024 MiB limit.
All originating roles still failed the separate original zero-skip verdict.

The full five-minute SMPP 5.0 bidirectional diagnostic at **20,000 aggregate
requests/s** also passed. Each role planned 3,000,000 requests: the client
completed 2,997,337 (99.9112%) and the server completed 2,998,119 (99.9373%).
In-phase successful-rate ratios were 99.9112% and 99.9373%, respectively;
scheduled p99 was 0.105–0.106 ms. All admitted requests succeeded, both peers'
received totals reconciled, and cleanup completed. The original zero-skip
verdicts remain failed. This is a separately labelled rate-override diagnostic,
not a change to the standard target.

Both full `W-CONNECTIONS` profiles also passed their independently checked
99% floor. Each established all 1000 sessions using the original 40 ms spacing
and startup budget, then measured 1000 scheduled requests/s for 300 s without
warmup. All admitted requests succeeded, peer receive counts matched, and every
connection closed with complete endpoint/sampler cleanup and no replacement.

| Profile | Successful / planned | Success | In-phase rate | Scheduled p99 | Highest sampled RSS per JVM |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3.4 | 299,977 / 300,000 | 99.9923% | 99.9920% | 1.009 ms | 296.60 MiB |
| 5.0 | 299,665 / 300,000 | 99.8883% | 99.8880% | 1.027 ms | 286.29 MiB |

`W-CONNECTIONS` is outside the helper's named healthy-throughput assessment,
so its `healthyPlannedSuccess` field remains not applicable. The retained
independent campaign driver enforces both 99% floors, full profile establishment,
100 ms scheduled p99 and 1024 MiB RSS for these checks. Cross-peer reconciliation
additionally verifies all 1000 initial binds, exact receive totals and cleanup.
The original zero-skip originating verdicts remain failed.

Both full-hour endurance qualifications passed. They ran together from
05:29:11 to 06:30:44 UTC, following the successful 20,000/s diagnostic. Each
used 100 sessions, a 60 s warmup, the full 3600 s measurement at 5000 aggregate
requests/s, and 30 s drain. Their four originating JVMs each scheduled 2500/s,
for an explicit combined 10,000/s workload over one measured wall-clock hour.

| Profile / originating role | Successful / planned | Success | In-phase rate | Scheduled p99 | Peak sampled RSS |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3.4 client | 8,981,271 / 9,000,000 | 99.7919% | 99.7919% | 0.751 ms | 286.85 MiB |
| 3.4 server | 8,980,984 / 9,000,000 | 99.7887% | 99.7887% | 0.705 ms | 295.83 MiB |
| 5.0 client | 8,982,843 / 9,000,000 | 99.8094% | 99.8094% | 0.472 ms | 285.66 MiB |
| 5.0 server | 8,982,165 / 9,000,000 | 99.8018% | 99.8018% | 0.545 ms | 289.01 MiB |

The four roles completed 35,927,263 of 36,000,000 planned measurement requests;
72,737 generator skips remain in the denominator. Each role had one success
during drain, excluded from its in-phase numerator. Every admitted warmup and
measurement request succeeded. Exact peer receive counts matched, all initial
sessions bound without replacements, and endpoint/sampler cleanup completed
with zero final active ownership. The largest recorded RSS, including reported
process high-water marks, was 296.05 MiB. All four original zero-skip verdicts
remain failed; all four separate 99% assessments passed.

All 12 final campaign pairs passed the retained driver's checks and independent
cross-peer reconciliation. The lowest originating-role success across these
campaigns was 99.7257%; the lowest normalized in-phase successful rate was
99.7257%. These include the separately labelled initial and higher-rate diagnostics.
The reviewed implementation, frozen source/artifact identities and full test
verification are recorded in the
[load-efficiency review](reviews/0020-load-efficiency.md). The initial diagnostic
below is not a capacity or endurance result.

## Efficiency changes

The library avoids temporary stream pipelines in repeated TLV scans, reuses a
fresh validated outbound frame when assigning its reserved request sequence,
and shares two immutable default codec catalogues across connections. Connection
limits and dispatch maps remain local, and buffers remain owned. There are no
new runtime dependencies or changes to validation, request bounds or deadlines.

Separate before/after probes observed:

| Experiment | Observed reduction | Scope |
| --- | ---: | --- |
| Complete codec encode/decode pairs | 45.59–74.51% allocated bytes; 34.01–67.79% calling-thread CPU | Eight SMPP 3.4/5.0 fixtures, three fresh JVMs per variant/fixture |
| Endpoint submit/data send-response cycles | 34.2–36.9% allocated bytes | Controlled transport, 160-byte payload, both profiles, three fresh JVMs per variant/fixture |
| Warm endpoint codec construction | 64.35% allocated bytes | 18,200.7 to 6,489.1 bytes per instance; three fresh JVMs per variant |

These percentages describe separate experiments and cannot be added. Allocated
bytes are not retained heap. Calling-thread CPU excludes other JVM threads.
Protocol validation and independently assembled complete wire expectations pass
for both variants. The [codec review](reviews/0020-codec-efficiency.md) and
[endpoint review](reviews/0020-endpoint-efficiency.md) contain exact fixtures,
commands, counters, hashes, spread and whole-type SOLID reviews.

## Initial controlled comparison

One SMPP 3.4 submission pair at a time used 100 sessions, window 32, a 160-byte
binary payload, 10,000 scheduled requests/s, 30 s warmup, 60 s measurement and
30 s drain. Both use Java 21 and the same `-Xms256m -Xmx512m` settings.

| Diagnostic | Planned | Successful | Skipped | Success/planned | Scheduled p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Unchanged runtime before this work | 600,000 | 599,754 | 246 | 99.9590% | 0.102 ms |
| Combined optimized runtime | 600,000 | 599,881 | 119 | 99.9802% | 0.102 ms |

Both runs had zero unexpected admitted-request failures, no local rejections,
complete measured phases and cleanup, and zero final active ownership. Both
passed the separate 99% floor and failed their original strict zero-skip verdict.
The before-change result already exceeded 99%; the improvement over older
oversubscribed-host measurements cannot be attributed entirely to the code.
These are single diagnostics, without statistical capacity claims.

## Evidence and reproduction

The host is an Intel Core i5-11400F with 12 logical processors and about
31.2 GiB RAM, running Linux `7.0.0-31-generic` and Ubuntu OpenJDK
`21.0.12+8-1-24.04-Ubuntu`. Every load JVM uses the original
`-Xms256m -Xmx512m` settings, window 32 and 160-byte binary payload. No collector,
carrier count, global JVM setting or OS limit was changed. The initial comparison,
standard targets, higher-rate diagnostic and connection checks ran one pair at a
time. The two soaks deliberately share the host as described above. No agent
benchmarks or build/test JVMs overlap these runs; ordinary desktop/IDE activity
remains. This is not an isolated production machine.

All current attempts, exact arguments, original verdicts, complete reports,
resource/pressure series, source inputs and executable hashes are retained in
`build/runs/efficiency-20260910/`. These generated local artifacts are ignored
by Git; this document preserves their interpretation and key identities. The
retained finite campaign and qualification drivers record every attempt and
check source/artifact stability and per-role floor guards.

`qualification-execution.json` records the finite campaign execution;
`qualification-summary.json` contains all 17 originating-role results and
confirms the frozen inputs remained unchanged. `cross-peer-reconciliation.json`
checks all 12 completed pairs. The final library JAR SHA-256 is
`8426f15563a44efe4951c3ec61267cb1eccd431b632eed0e54a1bbbcaacb6879`;
the declared production/tooling input digest is
`7021cf660bc306e158fd4ae42a5bf374438ce69a8a170f7c45db22b5e6ca6bb4`.

Use the [load runner commands](LOAD_TESTING.md) with full named profiles.
`W-TARGET` measures for 300 s at 10,000 aggregate requests/s with 100 sessions;
`W-SOAK` measures for 3600 s at 5,000 aggregate requests/s with 100 sessions.
Bidirectional data splits the aggregate rate equally between the two originating
roles. `W-CONNECTIONS` uses 1000 sessions at 1000/s for 300 s, with 40 ms between
initial binds. Shortened or rate-overridden invocations remain diagnostic-labelled.
Run fresh processes for measurements; cached tests are never load evidence.

The original zero-skip verdict, helper exit code and historical failed attempts
remain intact. A successful `healthyPlannedSuccess` entry requires an applicable
originating role and all its guards; receiving-only roles must additionally be
checked with their peer's receive accounting and pair cleanup.

## Limits

These are plain TCP loopback measurements on the local workstation, with both library
endpoints implemented by this project. They do not establish external provider
interoperability, TLS load capacity, production capacity, arbitrary connection or offered-rate
limits, or leak freedom. The earlier [measurement record](MEASUREMENTS.md) and
[matrix](MATRIX_RESULTS.md) retain the failed shared-host campaigns and their
different source identities. External SMPP 5.0 peer verification remains open.
