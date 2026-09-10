# Allocation diagnostics

This is the historical JFR record. Later bounded before/after
[codec](reviews/0020-codec-efficiency.md) and
[endpoint](reviews/0020-endpoint-efficiency.md) probes measure the subsequent
allocation refactors. Their counters, fixtures and denominators differ from
JFR sampling and cannot be combined with these estimates. Current end-to-end
workloads are recorded in [performance qualification](PERFORMANCE.md).

Two fresh, short JFR diagnostics of the corrected candidate completed on 2026-09-09. Their whole-recording allocation-weight estimates were **3.24–7.85 MiB/s per JVM**. Both workload campaigns failed the unchanged healthy-work criteria under substantial shared-host contention; these recordings establish neither capacity nor latency acceptance. All four endpoint owners, sampling workers and child JVMs finished cleanup.

## Scope and provenance

Artifact filenames in this document are relative to the retained local directory
`build/runs/step19-corrected-jfr/`; generated recordings and reports are outside Git.

The immutable checkout was `/tmp/lightweight-smpp-candidate2`, with candidate version `0.1.0-rc.1`. It includes the virtual-thread lock/deadline correction, the single platform observation worker and the per-session fixed-concurrency selector. These diagnostics used arrival-rate mode. They did not modify Java, simulator tools, installed artifacts, global JVM settings, heap options or carrier counts; no forced GC was requested. The existing external wrapper added only a bounded JFR profile recording to each newly launched child.

| Identity | SHA-256 |
| --- | --- |
| Base Git revision | `387aed9ef523c85fb3cfa0f8f245438759deb6fc` (Git object ID) |
| Declared production/build-input digest | `fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c` |
| `simulator/build/install/simulator/lib/HdrHistogram-2.2.2.jar` | `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8` |
| `simulator/build/install/simulator/lib/lightweight-smpp-0.1.0-rc.1.jar` | `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` |
| `simulator/build/install/simulator/lib/simulator-0.1.0-rc.1.jar` | `47705647225df458d2f4fc8cb18c12a37198d82b72c25c8fbace72d308649161` |
| `simulator/build/install/simulator/bin/simulator` | `03fbbcfad916ff2b1a59b3a0e604b902bdeb9a1b195a6dfbaa66ea8d5c71abf5` |
| `simulator/scripts/load_runs.py` | `91834a0dc7c987441b8ec89d2551aa277933e53c8219f4678e8d6375efeb9c45` |
| External `/tmp/step19-jfr-launcher.py` | `2ce6505c9ffd01bab114056fcd5b6b13457893af723b9bb601831fadfec36967` |
| Installed JDK `profile.jfc` | `485fb90dbecee9a950c45247464351162b1eb0c35387fbb929f002c600807251` |

The reported revision is the base Git object ID, `+`, and the full input digest. This distinguishes the frozen working-source candidate from the base commit. The fingerprint contains 227 explicitly listed files: library and simulator production Java, direct simulator Python tools, root/simulator Gradle build and settings, available Gradle properties, and wrapper scripts/configuration/JAR. Each file is SHA-256 hashed; the aggregate hashes UTF-8 `smpp-simulator-inputs-v1` plus newline, then sorted relative path, NUL, lowercase ASCII file digest and newline for each file. Tests, documentation, outputs and caches are excluded. The observed input manifest (`observed-inputs.json`) retains every path, byte count and hash. Post-run verification (`post-run-integrity.json`) found the input digest and installed binaries unchanged.

The manifests identify source and binary inputs independently; supplying a source path alone does not prove a build relationship. The parent candidate record preserves the exact source archive, build log and artifact inventory separately in `build/runs/step19-corrected-inputs/`. No Gradle or test-cache result is used as a measurement.

## Environment and run commands

The host was Ubuntu Linux `7.0.0-31-generic`, x86_64, Intel Core i5-11400F at 2.60 GHz, 12 logical processors and 32,716,916 KiB physical memory. Both roles used Ubuntu OpenJDK `21.0.12+8-1-24.04-Ubuntu`, the installed `-Xms256m -Xmx512m`, and the existing default collector configuration. Python was 3.12.3. `JAVA_OPTS`, `JDK_JAVA_OPTIONS` and `JAVA_TOOL_OPTIONS` were unset before the wrapper added its child-specific recording argument. JDK executable and profile hashes are retained in the allocation summary (`allocation-summary.json`).

These two pairs ran sequentially while the parent exercised six full W-SOAK pairs, four healthy-campaign workers and four matrix workers, plus build/review work. Their individual workload levels changed during the diagnostics. At 13:49:16 UTC, `/proc` showed 36 Java processes, including 30 simulator JVMs, and a one-minute load average of 123.55. At 13:51:09 UTC it showed 31 Java processes, including 28 simulator JVMs, and load average 145.44. The before (`environment-before.json`), after (`environment-after.json`) and per-pair snapshots preserve process commands and OS observations. This is a shared-host diagnostic, with no isolated-host performance claim.

Both pairs used the complete `load_runs.py run` workflow, one repeat, explicit `W-BASE-diagnostic` overrides, two transceiver connections, window 16 per connection, raw 160-byte payload with binary DCS 4, seed 1, 2-second request timeout, 5-second warmup, 30-second measurement and a 5-second drain bound. The ordinary workload did not enable TLS, keepalive, reconnect, churn or faults. The existing 100-microsecond final spin and 1-second resource sample interval remained configured. Submission offered 500 requests/s from the client. Data offered 250 requests/s from each role, 500 in aggregate.

Each child received `-XX:StartFlightRecording=filename=<scenario>/<role>.jfr,settings=profile,dumponexit=true,maxsize=256m`. The retained profile enables `jdk.ObjectAllocationSample` with its existing `300/s` throttle setting and stack traces; this setting is not a promise of exactly 300 samples/s. The four final files were 0.95–1.39 MiB, below the 256 MiB limit.

The exact invocations are preserved in invocations.json (`invocations.json`); the campaign files also retain both expanded Java application command lines and actual ephemeral ports. The commands executed were:

```sh
env SMPP_MEASURE_CANDIDATE=/tmp/lightweight-smpp-candidate2 \
  SMPP_MEASURE_JFR=/home/aidarbek/Documents/Projects/lightweight-smpp/build/runs/step19-corrected-jfr/34-submit/jfr \
  PYTHONDONTWRITEBYTECODE=1 \
  python3 /tmp/lightweight-smpp-candidate2/simulator/scripts/load_runs.py run --profile W-BASE --version 3.4 --direction submit --revision 387aed9ef523c85fb3cfa0f8f245438759deb6fc+fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c --source-root /tmp/lightweight-smpp-candidate2 --launcher /tmp/step19-jfr-launcher.py --output /home/aidarbek/Documents/Projects/lightweight-smpp/build/runs/step19-corrected-jfr/34-submit/campaign --repeats 1 --diagnostic-seconds 30 --warmup-seconds 5 --drain-seconds 5 --rate 500 --connections 2 --window 16 --payload 160
env SMPP_MEASURE_CANDIDATE=/tmp/lightweight-smpp-candidate2 \
  SMPP_MEASURE_JFR=/home/aidarbek/Documents/Projects/lightweight-smpp/build/runs/step19-corrected-jfr/50-data-bidirectional/jfr \
  PYTHONDONTWRITEBYTECODE=1 \
  python3 /tmp/lightweight-smpp-candidate2/simulator/scripts/load_runs.py run --profile W-BASE --version 5.0 --direction data --revision 387aed9ef523c85fb3cfa0f8f245438759deb6fc+fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c --source-root /tmp/lightweight-smpp-candidate2 --launcher /tmp/step19-jfr-launcher.py --output /home/aidarbek/Documents/Projects/lightweight-smpp/build/runs/step19-corrected-jfr/50-data-bidirectional/campaign --repeats 1 --diagnostic-seconds 30 --warmup-seconds 5 --drain-seconds 5 --rate 500 --connections 2 --window 16 --payload 160
```

Fresh repetitions require new output and recording directories: both the campaign runner and wrapper reject existing outputs. The one-off driver, wrapper snapshot and command logs are retained under `build/runs/step19-corrected-jfr/`; they are investigation artifacts, not a new public tool feature.

## Actual traffic and cleanup

The 3.4 pair ran from 13:49:16.722 to 13:50:13.924 UTC; the 5.0 pair ran from 13:50:14.536 to 13:51:08.376 UTC. Every originating role established its 30-second measurement phase. Actual phase durations were 30.000003198 s for submission, 30.000727486 s for data client and 30.000071823 s for data server. Recordings extend beyond these phases.

| Originating role / phase | Planned | Skipped | Attempted = admitted | Eventual SUCCESS | Other outcome | In-phase successful/s |
| --- | ---: | ---: | ---: | ---: | --- | ---: |
| 3.4 submit client / warmup | 2,500 | 1,256 | 1,244 | 1,244 | 0 | — |
| 3.4 submit client / measurement | 15,000 | 1,281 | 13,719 | 13,719 | 0 | 457.300 |
| 5.0 data client / warmup | 1,250 | 423 | 827 | 827 | 0 | — |
| 5.0 data client / measurement | 7,500 | 97 | 7,403 | 7,403 | 0 | 246.727 |
| 5.0 data server / warmup | 1,250 | 448 | 802 | 802 | 0 | — |
| 5.0 data server / measurement | 7,500 | 112 | 7,388 | 7,387 | LOCAL_FAILURE=1 | 246.233 |

All cohorts had zero local admission rejections and zero remaining pending work. The data client completed 7,402 successes inside its own measurement phase and one more during drain; the server completed 7,387 inside the phase and recorded one eventual `LOCAL_FAILURE`. The report does not identify that local failure’s precise cause. There was no replay or substituted successful run.

Receiver totals cover each whole listen lifetime, including warmup: the submission server received 14,963 requests; data server received 8,230 and data client received 8,189. These equal the opposite role’s eventual warmup-plus-measurement successes. Both campaigns exited 1 and retained their failed status. The submission server exited 0; submission client and both data roles exited 1. Each originating report lists `unsuccessful-work` for warmup and measurement, and `successful-rate-below-threshold`. The original zero-skips/no-unsuccessful-work policy, 99% successful-rate threshold, 100 ms scheduled-p99 ceiling and 1,024 MiB sampled-RSS ceiling were unchanged. No latency result from these JFR-instrumented runs is promoted to a latency claim.

Every process was reaped without a campaign timeout or launch/cleanup error. All four reports contain `cleanupComplete=true` and `samplingTerminated=true`. Latest sampled physical connections, pending request count/bytes, pending reply count/bytes, pending decisions and retained streams were zero. Two historical session entries remain observable per endpoint report; this is not a claim that the whole JVM retained no objects.

| JVM | Sampled peak RSS MiB | Sampled peak heap MiB | Sampled request / reply count peaks |
| --- | ---: | ---: | ---: |
| 3.4 submit client | 237.19 | 108.44 | 21 / 0 |
| 3.4 submit server | 235.03 | 107.05 | 0 / 3 |
| 5.0 data client | 277.91 | 151.52 | 3 / 2 |
| 5.0 data server | 269.18 | 147.53 | 5 / 4 |

These resource peaks are sampled observations from instrumented JVMs. Request/reply observations describe endpoint reservations and exclude physical transport queues. They do not establish memory bounds or leak freedom. The report summary (`report-summary.json`) and verification record (`verification.json`) preserve the full reconciliations and cleanup evidence.

## Allocation-weight results

OpenJDK describes the `weight` field as a relative, byte-valued sampling weight whose aggregation represents allocation pressure. It is not the size of the individual sampled object. The method here sums every retained `jdk.ObjectAllocationSample.weight`, groups weights by `objectClass`, and divides the sum by the complete retained recording span. See the [OpenJDK 21 event metadata](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/hotspot/share/jfr/metadata/metadata.xml).

The denominator uses the earliest completed JFR chunk start through the latest chunk start plus duration, checked against `jfr summary` and its whole-second rounding. This avoids dividing by the 30-second application phase or the first-to-last sample interval. The completed chunk-header fields follow the [OpenJDK 21 reader](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/jdk.jfr/share/classes/jdk/jfr/internal/consumer/ChunkHeader.java). Recording began during JVM startup, after some earlier JVM initialization; the result includes recorded startup, bind/warmup, measurement, drain, shutdown and JSON report generation. There are no application phase markers for exact allocation attribution.

| Recording | Span s | Samples | Sum of weights, bytes | Weighted bytes/s | Weighted MiB/s |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3.4 submit server | 50.424 | 982 | 171,565,032 | 3,402,459 | 3.245 |
| 3.4 submit client | 40.730 | 800 | 335,216,984 | 8,230,307 | 7.849 |
| 5.0 data server | 47.562 | 1,305 | 335,352,648 | 7,050,838 | 6.724 |
| 5.0 data client | 41.173 | 1,315 | 334,413,456 | 8,122,185 | 7.746 |

All four allocation sample counts matched the independent `jfr summary` event counts. Every retained chunk was complete and every recording had zero `jdk.DataLoss` events. Absence of a data-loss event does not make sampling exact. Each scenario has only one recorded pair, so there is no variance estimate or statistical confidence interval.

The five largest classes in each recording, ordered by summed weight, were:

| Recording | Class | Weight MiB | Share of sampled weight | Samples |
| --- | --- | ---: | ---: | ---: |
| 3.4 submit server | `java.util.concurrent.ConcurrentHashMap$Node[]` | 20.146 | 12.31% | 1 |
| 3.4 submit server | `byte[]` | 19.157 | 11.71% | 135 |
| 3.4 submit server | `java.util.stream.ReferencePipeline$2` | 15.207 | 9.29% | 99 |
| 3.4 submit server | `java.util.stream.ReferencePipeline$Head` | 11.810 | 7.22% | 90 |
| 3.4 submit server | `java.util.Optional` | 8.138 | 4.97% | 66 |
| 3.4 submit client | `byte[]` | 45.446 | 14.22% | 99 |
| 3.4 submit client | `java.util.stream.ReferencePipeline$2` | 25.014 | 7.82% | 55 |
| 3.4 submit client | `java.util.concurrent.ConcurrentHashMap$Node[]` | 20.581 | 6.44% | 2 |
| 3.4 submit client | `java.util.AbstractList$RandomAccessSpliterator` | 17.869 | 5.59% | 36 |
| 3.4 submit client | `java.util.stream.ReferencePipeline$Head` | 16.996 | 5.32% | 36 |
| 5.0 data server | `byte[]` | 80.792 | 25.26% | 241 |
| 5.0 data server | `java.util.stream.ReferencePipeline$2` | 24.205 | 7.57% | 96 |
| 5.0 data server | `java.util.stream.ReferencePipeline$Head` | 23.525 | 7.36% | 92 |
| 5.0 data server | `java.util.AbstractList$RandomAccessSpliterator` | 16.086 | 5.03% | 59 |
| 5.0 data server | `java.lang.Object[]` | 11.271 | 3.52% | 32 |
| 5.0 data client | `byte[]` | 64.225 | 20.14% | 262 |
| 5.0 data client | `java.util.stream.ReferencePipeline$2` | 22.732 | 7.13% | 100 |
| 5.0 data client | `java.util.concurrent.ConcurrentHashMap$Node[]` | 20.146 | 6.32% | 1 |
| 5.0 data client | `java.util.stream.ReferencePipeline$Head` | 18.580 | 5.83% | 86 |
| 5.0 data client | `java.util.AbstractList$RandomAccessSpliterator` | 17.098 | 5.36% | 62 |

The single-sample `ConcurrentHashMap.Node[]` weights illustrate why these values must not be read as exact array sizes or reliable isolated class rankings. Byte arrays and stream pipeline/spliterator objects recur across recordings. As an additional exploratory view, the first project frame within each extracted 16-frame stack attributed 22.64–28.98% of all sampled weight to `MessageTlvSupport.count`. The three originating JVMs attributed 13.69–16.47% to simulator `Json.encode`, showing the effect of end-of-run reporting. These are sampled-stack observations and possible subjects for a later focused profile; they do not allocate those percentages exclusively to a library API or justify a source change in this frozen candidate.

Exact retained recording identities:

| File under the run directory | Bytes | SHA-256 |
| --- | ---: | --- |
| `34-submit/jfr/server.jfr` | 1,456,141 | `b45051d251264d2e8ef4e71603e2eae0a51ddbdb3b0d0267175a551981bbceab` |
| `34-submit/jfr/client.jfr` | 996,905 | `1975f6c646fd6ede7a1a18cd741c3666f857ad832c8d5dd5cc3dcfd2f6c6f30d` |
| `50-data-bidirectional/jfr/server.jfr` | 1,412,872 | `f663197f305b6ae145bfd5aaa411d71f03ad0a2c56a1f1440de2a296dcac511f` |
| `50-data-bidirectional/jfr/client.jfr` | 1,395,548 | `f58f9f50b88a7bc1e1e9fbcf7ab31ee7b96c706a7884dc50f22cfb767eeb49e9` |

## Extraction and limits

For each completed recording the analysis executed the installed JDK commands below; extraction-commands.json (`extraction-commands.json`) records exact paths. JSON event output retains sample weights, classes and up to 16 stack frames. The allocation summary (`allocation-summary.json`) keeps all class groups, sample totals, completed chunk spans, JVM arguments and recording hashes. The one-off `analyse-recordings.py` artifact records the arithmetic and validation. The [JDK 21 `jfr` command reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html) documents the supported extraction formats and event filtering.

```sh
jfr summary <role>.jfr
jfr print --json --events jdk.ObjectAllocationSample --stack-depth 16 <role>.jfr
jfr print --json --events jdk.DataLoss,jdk.JVMInformation,jdk.GCConfiguration,jdk.ActiveRecording <role>.jfr
```

These weighted totals are statistical whole-recording allocation estimates. They are **not exact bytes allocated, exact bytes/request, retained or live heap, memory-leak evidence, exactly measurement-phase allocations, or isolated library costs**. Startup, JDK/JFR activity, simulator pacing/sampling and report serialization contribute. The short samples, different per-role lifetimes, missed offered work, one local failure, and substantial changing host contention prevent generalizing these values to full healthy-load or uninstrumented latency behavior. Failed campaign reports remain intact alongside the successful extraction and cleanup checks.
