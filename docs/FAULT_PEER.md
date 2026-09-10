# Independent raw fault peer

[`fault_peer.py`](../simulator/scripts/fault_peer.py) is a finite, standard-library
Python tool for checking the public simulator across a real TCP connection. It
constructs and inspects SMPP bytes independently of the Java library. It does not
import another SMPP implementation. Python 3.10 or newer is required; the recorded
runs used Python 3.12.3 on Linux.

## Supported directions

| Raw peer mode | Project simulator mode | Selected operation | Bind behavior |
| --- | --- | --- | --- |
| `server` | `client` | `submit` or `data` | Accepts one configured, finite bind cohort; submit and 5.0 data require TX/TRX, while 3.4 data also permits RX |
| `client` | `server` | `deliver` or `data` | Opens the configured number of connections and sends TRX bind once per connection |

Both modes support `--version=3.4` and `--version=5.0`. The raw server accepts RX
binds for controls and SMPP 3.4 `data_sm`; RX cannot originate `submit_sm` or
SMPP 5.0 `data_sm`. The raw client requires a successful bind response that
advertises exactly the requested `sc_interface_version`; this is an explicit
fixture requirement, including for 3.4, where that TLV is optional in the protocol.

The raw MC accepts syntactically valid credentials without an authentication
backend. The raw ESME takes `SMPP_SYSTEM_ID` and `SMPP_PASSWORD` from its environment,
defaulting to `sim`/`sim`. Credentials are excluded from its report and header
samples. Connections are plain TCP. There is no TLS, reconnect, message retry,
storage, text decoding, receipt interpretation, or handset delivery simulation.

`submit_sm_resp` and `data_sm_resp` use the fixed message ID `id`; `deliver_sm_resp`
uses its required empty ID. Every normal response has status zero, the paired
command ID, and the original sequence. That response means the fixture wrote an
SMPP acknowledgement. It does not establish durable acceptance or delivery.

## Run a finite pair

Start the raw MC first. It prints `READY port=N` after binding its listener:

```sh
python3 simulator/scripts/fault_peer.py server \
  --port=0 --version=5.0 --operation=submit --fault=duplicate \
  --connections=1 --count=1 --timeout=10 --duration=5 --drain=1 \
  --report=/tmp/raw-submit-unique
```

Point the installed project simulator client at that printed port, using a fresh
report directory and the source revision for its installed artifact:

```sh
simulator client --host=127.0.0.1 --port=PORT_FROM_READY \
  --version=5.0 --bind=trx --operation=submit --connections=1 --window=8 \
  --count=4 --rates=8 --duration=PT0.8S --warmup=PT0S \
  --timeout=PT0.12S --drain=PT0.5S --payload=16 \
  --expect-failures=true --minimum-rate-ratio=0 \
  --revision=INSTALLED_SOURCE_REVISION --report=/tmp/simulator-client-unique
```

For the reverse direction, start `simulator server` with `--operation=deliver`
and `--port=2775`, retaining the other finite workload options. Then run:

```sh
python3 simulator/scripts/fault_peer.py client \
  --host=127.0.0.1 --port=2775 --version=5.0 --operation=deliver \
  --fault=missing --connections=1 --count=1 \
  --timeout=3 --duration=2 --drain=.5 --report=/tmp/raw-deliver-unique
```

Use `data` on both processes for `data_sm`. Choose the same profile on both
processes. The raw tool accepts literal IPv4/IPv6 addresses; it performs no DNS
lookup. `--port=0` is available only in server mode. Each `--report` directory must
not exist. Invalid configuration or an occupied report directory returns exit 2
before network startup. A completed scenario returns 0; an incomplete scenario,
protocol/IO failure, or signal cancellation returns 1 with a final report when
the output path remains writable.

## Fault selection and accounting

`--count` is the number of selected incoming message PDUs **per connection**.
It is not a request window or a total connection count. Messages beyond this
selection are acknowledged normally. The fixture does not deduplicate incoming
request identities or infer replay semantics.

| `--fault` | Action | One completed injection means |
| --- | --- | --- |
| `none` | Acknowledge each supported request | No injection; expected count is zero |
| `duplicate` | Write the normal response and one identical extra response for each selected request | All bytes of the extra response were accepted by the local socket |
| `late` | Queue each selected response until `--delay` after its request is observed | All bytes of that delayed response were accepted by the local socket |
| `missing` | Discard each selected acknowledgement decision without retaining its frame | The selected acknowledgement was deliberately withheld |
| `malformed` | Acknowledge the first `count - 1` requests; on request `count`, send a 16-byte response header declaring length 15 | All 16 deliberately malformed bytes were written |
| `fragmented` | Split selected responses into writes of at most `--fragment-bytes`, separated by `--fragment-gap` | The entire response was written across the bounded fragments |
| `disconnect` | Acknowledge the first `count - 1` requests and close on request `count` | The local socket was closed after observing that request |
| `slow-reader` | After bind, pause for `--delay`, then read at most `--read-bytes` once per `--read-interval` | The configured read-throttling policy was activated for that connection |

Late mode requires `--delay > --peer-timeout`. `--peer-timeout` is the explicitly
assumed timeout of the target, not a negotiated or observed value. Ordinary
responses remain FIFO, so one late reply can also delay subsequent replies.
Control responses may pass ordinary frames that have not started, but no bytes
can interleave with an active fragmented frame. TCP may combine the separate
application writes; the tool does not promise TCP packet boundaries.

Missing mode proves a withholding decision, not that a peer timeout occurred.
Slow-reader mode can run with zero complete messages; its expected message count
is zero and its expected injection count is one per bound connection. It reports
actual received bytes and complete messages separately. Activation alone does
not prove sender backpressure, a blocked kernel write, or a throughput limit.

## Bounds and termination

The runtime owns one selector loop and no worker threads. It never replenishes
its connection cohort or creates a retry loop. All sockets are nonblocking;
read, write, connection, drain, and signal decisions return to that same owner.

| Resource or deadline | Default | Accepted bound / contract |
| --- | --- | --- |
| `connections` | 1 | 1–64 total connection attempts/acceptances; extra accepted sockets are closed |
| `count` | 1 | 1–1,000,000 selected PDUs per connection |
| `max-frame` | 65,536 | 16–1,048,576 incoming PDU bytes, checked from the first four octets |
| `held-count` / `held-bytes` | 64 / 1,048,576 | 1–4,096 frames / 16–67,108,864 bytes, shared across all ordinary queues and active writes |
| Control storage | Separate | At most `connections * 8` frames and `connections * 1,024` bytes, also including active writes |
| `sample-count` | 32 | 0–256 header/event samples; failure details are limited to 16 strings of 200 characters |
| `timeout` | 2 seconds | 0.001–3,600 seconds for the entire startup cohort, each incomplete frame, and physical writes |
| `duration` | 2 seconds | 0.001–3,600 seconds after the configured cohort has bound |
| `drain` | 0.5 seconds | 0–60 seconds after duration; then sockets close and retained frames settle |
| `delay` / `peer-timeout` | 0.2 / 0.1 seconds | Each 0.001–3,600 seconds |
| `fragment-bytes` / `fragment-gap` | 3 / 0.005 seconds | 1–`max-frame` bytes / 0–60 seconds |
| `read-bytes` / `read-interval` | 16 / 0.05 seconds | 1–`max-frame` bytes / 0.001–3,600 seconds |

The decoder retains at most one configured frame per connection. A receive call
adds a temporary chunk of at most 16,384 bytes; completed immutable frame bodies
and bounded field/TLV inspection create temporary copies. These decoder/output
limits describe explicit wire storage, not total interpreter memory or kernel
socket buffers. No message history or payload log grows with traffic count.

The write deadline is admission time plus deliberate delay plus `timeout`.
The read deadline is measured from the first byte of each frame and does not
reset merely because another fragment arrives. On graceful drain, the tool sends
one unbind when its output queue is empty; it also answers peer enquiries and
unbinds. A peer that does not cooperate is closed at the drain deadline. A run
ends by `timeout + duration + drain`, subject to OS scheduling and the bounded
work around its 5 ms selector poll. No real-time scheduling guarantee is made.

`SIGINT` and `SIGTERM` request cleanup on the owner loop, abandon retained writes,
and produce a failing final report. `SIGKILL` cannot produce a report. A close
error is counted and makes cleanup fail; cleanup still attempts the remaining
owned connections. A fresh-directory or final filesystem write failure can
prevent a report from being written.

## Read the report

`report.json` contains the full finite configuration, Python/platform identity,
the script file SHA-256, elapsed time, and a `result` object. Freeze the input
script during a run: its hash is read when the report is written. The paired test
harness also records the exact launcher command and all installed JAR hashes in
`input-*.json`; a supplied source revision alone does not identify working-tree
artifacts.

`expected_injections` is the configured plan. `injections_attempted` counts
selected actions, including rejected queue admissions. `injections_completed`
uses the fault-specific meanings above. `injections_abandoned` includes rejected,
expired, interrupted, or closed-before-complete injections. At termination:

```text
injections_attempted = injections_completed + injections_abandoned
```

`ack_frames_written` counts complete normal/duplicate acknowledgements;
`malformed_frames_written` counts complete malformed injections. `sent_bytes`
also includes partial writes and control frames. Header samples include the exact
16 raw octets, status and sequence encoded therein, and connection identity;
bodies and credentials are excluded. Received response, negative-response,
generic-nack, and unmatched-response counts are separate. `invalid_frames`
includes malformed wire data and violations of this fixture's expected
operation/correlation contract, rather than claiming full SMPP conformance.

Per-connection close reasons distinguish observed peer EOF/unbind from deliberate
disconnect, timeout, signal, and local drain closure. `cleanup_complete` requires
all retained frame reservations released, every owned connection socket closed,
and no reported close error. It does not imply a graceful unbind exchange.

`passed` requires the configured bind cohort, selected message target, completed
injection target, no abandoned injection, and successful local cleanup without
protocol/IO errors. Read the target's report to establish its resulting request
outcomes; the raw peer cannot infer them from a successful local socket write.

## Verification and independent wire basis

Run the independent standard-library suite without an installed Java target:

```sh
PYTHONPYCACHEPREFIX=/tmp/smpp-fault-pycache \
  python3 -m unittest discover -s simulator/scripts/tests -p test_fault_peer.py -v
```

For the full public-CLI matrix, additionally supply `SMPP_SIMULATOR_LAUNCHER` and
`SMPP_SIMULATOR_REVISION`. Set `SMPP_FAULT_RESULTS` to an external results directory
to retain fresh per-case logs/reports; without it, temporary outputs are removed.
The two optional matrix test methods expand into 56 independent process pairs:
seven faults × two profiles × four operation/origin combinations. These are small
functional checks, not a load or capacity measurement.

The wire fixtures use the [SMPP 3.4 issue 1.2 specification](https://smpp.org/SMPP_v3_4_Issue1_2.pdf),
§§3.2, 4.1, 4.4, 4.6, 4.7 and 5.1, and the
[SMPP 5.0 specification](https://smpp.org/SMPP_v5.pdf), §§3.2, 4.1, 4.2.1,
4.2.2 and 4.3.1. In particular, `data_sm` addresses allow 65 C-octets including
the terminator; submit/delivery addresses allow 21. SMPP 3.4 short messages allow
254 octets and cannot coexist with `message_payload`; 5.0 allows 255 and a present
payload TLV supersedes the short-message bytes. The peer validates those layouts,
bounded ASCII C-strings, exact standard-field lengths, and at most 256 well-formed
TLVs. Other optional values remain uninterpreted; this tool is not a replacement
for the library's complete field, semantic, and optional-parameter validation.

Python's [selector contracts](https://docs.python.org/3/library/selectors.html)
and [nonblocking socket contracts](https://docs.python.org/3/library/socket.html)
provide the readiness, partial-write and unregister-before-close basis. Actual
red/green logs, final artifact identities, and every Python type/function review
are recorded in the [fault-peer review](reviews/0017-fault-peer.md).

## Corrected release candidate replay

The complete 56-pair functional matrix was executed freshly against the immutable
`/tmp/lightweight-smpp-candidate2` installed distribution for `0.1.0-rc.1`.
Both original opt-in test methods passed in 87.506 seconds without changing
assertions, timeout settings, runtime sources or test sources. This covers seven
faults, both profiles, both roles, `submit`/`deliver`, and `data` in both directions.
All target/raw cleanup checks passed, final target pending and raw held values
were zero, and all 56 planned injections were attempted and completed with zero
abandoned injections. The new measurement is distinct from the earlier Step18
artifact results.

| Executing candidate input | SHA-256 |
| --- | --- |
| `bin/simulator` | `03fbbcfad916ff2b1a59b3a0e604b902bdeb9a1b195a6dfbaa66ea8d5c71abf5` |
| `lightweight-smpp-0.1.0-rc.1.jar` | `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` |
| `simulator-0.1.0-rc.1.jar` | `47705647225df458d2f4fc8cb18c12a37198d82b72c25c8fbace72d308649161` |
| Existing `HdrHistogram-2.2.2.jar` | `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8` |

The declared source was `387aed9ef523c85fb3cfa0f8f245438759deb6fc+fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c`. All installed files matched their
before-run hashes after both functional matrices finished. Exact commands,
per-pair reports, raw logs, target artifact identities and reconciliation are
retained under `build/runs/step19-corrected-wire-checks`; `summary.json` has
SHA-256 `321e3d037f51ae0e60ac0ca813f27193a025b463c7da80dd978692fd3496d366`.
The [review](reviews/0017-fault-peer.md) records actual target outcomes, including
17 successes / 15 timeouts for this run's slow-reader cases. These finite
observations establish fault behavior, not a throughput or backpressure limit.

## Final candidate 3 replay

All 56 raw-fault process pairs passed the original assertions again against
`/tmp/lightweight-smpp-candidate3`: seven faults × two profiles × submit/delivery
and data in both directions. The two original opt-in unittest methods completed
in 113.172 seconds with zero failures/skips; runtime source, test source,
assertions and timeout settings were unchanged. All 56 selected injections were
attempted/completed once, none were abandoned, and every raw/target cleanup
check passed with zero final held/pending work.

| Final installed input | SHA-256 |
| --- | --- |
| `bin/simulator` | `03fbbcfad916ff2b1a59b3a0e604b902bdeb9a1b195a6dfbaa66ea8d5c71abf5` |
| `lib/lightweight-smpp-0.1.0-rc.1.jar` | `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` |
| `lib/simulator-0.1.0-rc.1.jar` | `a59d553d3ec84de5d953a927696565f2c498384386a241a74a113426011d1d9e` |
| `lib/HdrHistogram-2.2.2.jar` | `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8` |

Declared source: `387aed9ef523c85fb3cfa0f8f245438759deb6fc+2d63cd99c32ca3b197cbc973a100c9280ef00a05d4c5311b0cb7c011bb823a00`. The replay was held until the older heavy campaigns
finished, then ran sequentially before the receipt matrix while four final soak
JVMs and a remaining target campaign shared the host. It is a functional check,
not an isolated capacity/latency measurement. All installed files remained
byte-identical before and after both matrices. Original reports, exact commands,
artifact/source manifests and reconciliation are retained in `build/runs/step19-final-wire-checks`;
`summary.json` SHA-256 is `cd48951751a55b297d65715d52904039fe466bf08708a4bfb61e9e44624022cc`. The preceding candidate2 evidence remains
unchanged and distinct. See the [review](reviews/0017-fault-peer.md) for actual
fault outcome counts and retention peaks.
