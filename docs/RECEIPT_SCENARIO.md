# Finite submission and receipt round trip

`kg.aidarbek.simulator.ReceiptScenario` is a separate executable fixture for the
receipt workload in [WORKLOADS.md](WORKLOADS.md). It runs one ESME client JVM and
one MC server JVM over loopback TCP, using a transceiver bind under SMPP 3.4 or
5.0. It exercises `submit_sm` / `submit_sm_resp` and `deliver_sm` /
`deliver_sm_resp` through public endpoint APIs and the public `ReceiptTlvs`
helper. It has no persistence, message replay, external SMS delivery, or
throughput claim.

Each accepted submission gets an opaque message ID. The server creates exactly
one synthetic `DELIVERED` receipt with that same ID in the healthy scenario.
The client correlates against the ID actually returned in `submit_sm_resp`;
it does not predict the server's ID scheme, normalize case, or remove leading
zeros. Rejected submissions get status `0x58` and no receipt. The synthetic
state is fixture input; neither a successful submission nor a receipt proves
handset delivery.

## Run two finite processes

Build the tooling distribution normally, preserving Gradle caches:

```sh
./gradlew :simulator:installDist --console=plain
```

In one terminal, use a report path that does not exist (its parent must exist):

```sh
java -Xms32m -Xmx128m -cp 'simulator/build/install/simulator/lib/*' \
  kg.aidarbek.simulator.ReceiptScenario server \
  --version=3.4 --port=0 --count=8 --window=4 \
  --timeout=PT2S --duration=PT15S --drain=PT2S \
  --report=/tmp/receipt-server-new
```

The server prints `READY port=N` after binding `127.0.0.1`. Start the client in
another terminal using that actual port:

```sh
java -Xms32m -Xmx128m -cp 'simulator/build/install/simulator/lib/*' \
  kg.aidarbek.simulator.ReceiptScenario client \
  --version=3.4 --port=N --count=8 --window=4 \
  --timeout=PT2S --duration=PT12S --drain=PT2S \
  --report=/tmp/receipt-client-new
```

Repeat with `--version=5.0` and new report directories. This entry point adds no
commands to the default throughput simulator launcher. It uses fixed test
credentials `receipt` / `receipt`, listens only on loopback, and makes one
connection attempt. Both healthy processes must exit zero and both summaries
must say `passed: true` and `cleanup: true`.

| Option | Default and finite domain |
| --- | --- |
| Role | Required `server` or `client` |
| `--version` | `3.4`; alternatively `5.0` |
| `--port` | Server default `0` chooses an available port; client requires 1..65535 |
| `--count` | 8; 1..10000 total submissions |
| `--window` | 4; 1..32 outstanding round trips and local request handles |
| `--reject-every` | 0 disables rejection; otherwise every Nth submission is rejected, 1..count; configure both roles equally |
| `--fault` | Server only: `none`, `missing`, `duplicate`, `mismatch`; one selected fault applies to its first accepted submission |
| `--timeout` | `PT2S`; positive ISO-8601 duration, at most 30 seconds |
| `--duration` | `PT15S`; positive overall role deadline, at most 120 seconds, at least twice timeout |
| `--drain` | `PT2S`; positive shutdown grace/cleanup policy, at most 30 seconds |
| `--report` | Required fresh directory; existing paths are rejected before opening sockets |

The timeout is the receipt request's budget. A submission response and its
client correlation deadline each have three times that budget, permitting the
server's deliberately early receipt and the duplicate fault's second request.
The independent overall duration may finish a run earlier; configure enough
time for the intended finite count. Connect uses timeout, bind uses twice
timeout, handlers use three times timeout, and final shutdown observation is
bounded by drain plus three seconds. The process owner cancels retained handles
and resolves its own handler stages before reporting cleanup. Run supervision
should allow the overall duration plus these documented startup/cleanup margins.

## Early receipt and fault semantics

The server deliberately waits for its receipt request's terminal outcome before
releasing the positive submission handler decision. This produces an early
receipt at the client, independently of whether the client thread has observed
the submission response. The client immediately acknowledges a structurally
valid early receipt and holds its opaque ID in bounded memory. That
`deliver_sm_resp` means the receipt PDU was accepted for processing; final
submission-to-receipt correlation is a later, separate observation. An unknown
ID becomes a mismatch once all already admitted submission responses are known.
The client cannot retroactively change an earlier receipt acknowledgement.

| Server fault | Injection and expected observation |
| --- | --- |
| `missing` | Omit one receipt, still positively accept its submission; client records one missing receipt and fails |
| `duplicate` | Send a second receipt with the same ID after the first settles; client rejects the duplicate with nonzero status, records it, and fails |
| `mismatch` | Send one receipt with a different opaque ID; an early PDU may get a positive acknowledgement, but client records an unmatched ID plus the missing correct receipt and fails |

Fault runs intentionally violate the healthy criterion and the client exits
one. For a mismatch, the server can still pass its own receipt-request counts:
it observed positive receipt-PDU acknowledgements. This is precisely why both
reports and the client's final correlation result are required. A negative
client exit is not by itself proof of injection; inspect expected, selected and
applied fault counts plus actual receipt request/response observations. Faults
are rejected as client options or when every submission would be rejected.

`faultSelections` counts the selected first positive submission. `faultActions`
counts the deliberate omission or successful local admission of the changed or
second receipt request. Local admission is not a claim of a physical write or
peer acknowledgement. `receiptAttempts`, `receiptRequests`, positive/negative
receipt responses and local receipt failures expose those later outcomes.

## Bounds and interpretation

The client reserves a round-trip slot before sending. It releases that slot
only when the submission is rejected, its matching receipt is correlated, or
the relevant deadline fails. Active slots and early unknown IDs are bounded by
window. Remembered positive IDs and seen receipt IDs are each bounded by count;
IDs contain at most 64 non-NUL ASCII characters. Completed-ID history detects
late duplicate attempts during the active run and is discarded when the
process ends. No persistent deduplication guarantee is offered across runs.

The server retains at most window decision entries, one receipt handle per
entry, and a count-sized duplicate-submission bitmap. Application handlers use
window active slots and window queued slots, with at most twice window ordered
replies. Each endpoint permits one connection, window pending requests, 65536
accounted request bytes and 65536 accounted reply bytes, 1024-byte frames,
512 TLV bytes and eight TLVs per frame. It owns two callback workers and
`4*window+16` queued/reserved notifications. These are finite policy bounds, not
an exact JVM heap estimate. Submission/receipt polling and future completion
occur outside the fixture's intrinsic guards; no application future callback
is required for network progress.

Submissions carry a four-byte big-endian index, DCS 4 and registered-delivery
value 1. Receipts reverse the fixture's source/destination addresses, set
`esm_class=0x04`, use an empty short message, and carry only
`receipted_message_id` (0x001e, ASCII plus terminating zero) and `message_state`
(0x0427, one byte 2). Malformed or unsupported fixture content is rejected;
this is not a general receipt parser. Independent test literals check the
payload and TLV bytes rather than deriving expectations from the builder.

The field basis is [SMPP 3.4 issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf)
§2.11, §4.4 / Table 4-18 and §§5.3.2.12, 5.3.2.35, and
[SMPP 5.0](https://smpp.org/SMPP_v5.pdf) §4.3.5.1 and §§4.8.4.37, 4.8.4.47.
The 3.4 narrative also describes textual user-data receipts; this fixture uses
the explicit receipt TLVs listed in its command table. It does not generate or
interpret vendor-specific receipt text.

## Reports and verification

Each fresh `summary.json` contains configuration, environment, elapsed time,
entry-point and library class hashes, whole-JAR SHA-256 when running installed
artifacts, separate role counts, remaining endpoint request/reply counts,
cleanup, a bounded exception-class diagnostic, and the final criterion.
Class-directory test launches identify that fact instead of pretending to have
a JAR hash. There are no credentials or raw message IDs in reports.

Client positives and negatives are actual observed submission responses.
Client receipt counters are incoming requests and local acknowledgement
**decisions**; server receipt counters are actual observed peer responses or
local terminal failures. Server positive submission counters are handler
**decisions**, not claimed observed wire writes. Never add submission and
receipt counts together and call the result completed messages.

The healthy criterion requires all planned submissions to receive a positive
or configured negative result, exactly one correlated receipt per positive,
zero duplicates/mismatches/missing receipts/local failures, and complete owned
cleanup with no pending requests or replies. Tests also cover all-negative
runs, wrap-safe deadlines, reused/empty positive IDs, bounded early storage,
fresh-path protection, idle-server expiry and refused connection cleanup.

Fresh installed-process evidence and the complete current-type review are
recorded in [the receipt scenario review](reviews/0017-receipt-scenario.md).
This finite workload establishes functional behavior only.

## Corrected release candidate replay

The same 12-pair functional matrix was run freshly against the immutable
`0.1.0-rc.1` distribution in `/tmp/lightweight-smpp-candidate2`, using separate
client/server JVMs. Both profiles passed all original assertions for all-positive,
mixed-rejection, all-negative, missing, duplicate and mismatched-ID scenarios.
Healthy eight-submission runs observed eight correlated early receipts; mixed
runs observed six positives, two negatives and six receipts; all-negative runs
observed zero receipts. Intentional fault failures retained their distinct
counters and nonzero client exits. Every role reported cleanup complete and
zero remaining requests/replies.

The executing library SHA-256 was
`609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f`;
the executing simulator JAR SHA-256 was
`47705647225df458d2f4fc8cb18c12a37198d82b72c25c8fbace72d308649161`.
This later artifact identity supersedes neither the observations nor the
provenance of the earlier fixture-development run; both are retained.
Fresh outputs and commands are under `build/runs/step19-corrected-wire-checks/receipt`;
its `matrix.json` SHA-256 is
`6e09a638d7748b46f870cbb763ebb0d8368722ce706d9c92c89099819f0daf8f`.
The [review](reviews/0017-receipt-scenario.md) records the complete declared
source, counts and immutable-input verification. No fixture Java source or
assertion changed for the replay.
