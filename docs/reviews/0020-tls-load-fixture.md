# Review: deterministic TLS write ordering

## Scope and observed failure

Starting revision: `4da2aecd6394f9ff18d700bb8b47ae1422451002`. Only
`TlsCleanupTest` changes; the transport, TLS policy, write deadlines and public
contracts stay the same. The complete test type, `TcpTransportTest.Writes`,
`WriteObserver`, `WriteHandle`, the transport's write/deadline/cleanup paths and
the TLS fixture were reviewed.

The codec agent's full `check` produced a real assertion failure in
`tlsSlowReaderDeadlineAbortsRawSocketAndSettlesAQueuedControlWithoutStartingIt`:
the peer returned zero decrypted bytes where the test expected the first 16.
The unchanged focused replay passed. Both results are retained in the
[codec review](0020-codec-efficiency.md) and its evidence archive; a replay does
not erase the earlier failure.

The old test started a one-second deadline for an 8 MiB TLS write into a peer
with a small receive buffer. It then waited for decrypted application bytes
before enqueueing the control frame. That prerequisite raced the very deadline
being tested and was not guaranteed by the frame transport contract.

The active write's fast, nonblocking `beforeWrite` guard now enqueues the small
control frame. The active write is already claimed, while its physical write
has not begun. Reentrant admission is supported by the transport lock and does
not wait for socket progress. The peer continues to withhold reads. The test
retains the one-second write deadline and the assertions for timeout, active
transmission uncertainty, zero control guard invocations, exactly one terminal
callback per write, and bounded transport termination. It additionally checks
exactly one active guard invocation. Complete TLS frame content remains covered
by the mutual TLS cases in the same type and the existing transport contracts.

The test checks the SPI's conservative claimed-write distinction; it does not
infer remote receipt from `writeStarted`. No runtime defect is asserted from
the test fixture failure, and no timeout was enlarged to make the test pass.

## TDD and verification

- Actual red: `/tmp/codec-efficiency-09-final-check.log`, with the failing XML
  retained in `codec-efficiency-evidence.tar.gz`. The unchanged TLS source in
  that worktree matches the starting revision here.
- An initial root command using unqualified `test --tests ...` also selected
  `:simulator:test`, which has no matching TLS class. That task-selection error
  is retained in `build/runs/efficiency-20260910/tls-fixture-green.log` and is
  explicitly not behavioral red evidence.
- Green: `./gradlew :test --tests 'kg.aidarbek.smpp.transport.TlsCleanupTest'
  --console=plain` freshly passed all six TLS cases, with zero failures/errors/
  skips. The log is `tls-fixture-focused-green.log` in the same run directory.
- `./gradlew spotlessApply --console=plain` completed before the final hash.
  Integrated verification and fresh workload results are recorded in
  [the efficiency review](0020-load-efficiency.md).

## Whole-type SOLID review

The file declares one type and introduces no nested, anonymous or local type.
Its independent cases still cover mutual identity/trust, accepted-handshake
deadline ownership, explicit close during stalled handshake and active TLS
write timeout with a queued control.

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsCleanupTest.java
type: kg.aidarbek.smpp.transport.TlsCleanupTest
sha256: c7cb10877fbd6668b166b13723fef5de01e9475604136a9ed662826422b8e6f4
responsibility: Exercises TLS identity and bounded handshake/write cleanup through actual sockets and the frame transport contract.
consumers: JUnit runs the six parameterized and ordinary cases; TcpTransportTest events/write observers and TlsTestMaterial supply shared conforming fixtures.
S: pass | Each case checks one TLS lifecycle boundary; deterministic control admission removes a peer-read prerequisite without adding runtime or workload policy.
O: pass | Existing TLS role and trust configuration remain explicit collaborators; the test adds no production hook or alternate transport implementation.
L: pass | Write observers stay fast and nonblocking, use supported reentrant frame admission, preserve conservative writeStarted semantics and exactly-once terminal checks, and every owned socket/transport retains bounded cleanup.
I: pass | Cases use only connection, frame-write, listener and termination capabilities needed for their TLS boundary; no application handlers or simulator interfaces are required.
D: pass | Concrete JDK TLS sockets intentionally compose this adapter contract test, while observer/listener interactions follow the frame ports; production code has no dependency on test ordering or fixtures.
findings: none
```
