# TCP frame transport

Step 10 supplies complete-frame I/O over direct TCP, with JDK 21 sockets and
virtual threads. `spi` contains the network-independent session ports;
`transport` implements them and owns listening sockets. Both use only the JDK.
Request tracking, authentication and endpoint state are separate consumers.
The transport checks framing and allocation bounds; command, profile and body
validation remain with the codecs.

## Ownership and lifecycle

`TcpTransport.connect(resolvedAddress, config, absoluteDeadlineNanos)` returns
an owned pending connection. `start(listener)` installs the sole internal
listener before connecting or reading. Starting twice, or after closure, fails.
Closing before start closes the pending socket; closing during connect cancels
that operation and any late success. The deadline includes delay before start.
The address must already be resolved: DNS resolution is outside this bound.
Connections use `Proxy.NO_PROXY`.

`TcpTransport.adopt(socket, config)` accepts an open, connected blocking socket
with neither side shut down. It transfers ownership only after validation and
option configuration succeed. On failure the original owner must close the
socket; options may already be partly changed. Adoption and connection disable
linger and socket read timeout, enable TCP_NODELAY, and optionally request a
send-buffer size. The requested size is an OS hint.

A started transport owns virtual reader, writer and deadline workers. A fourth
virtual thread waits for cleanup and publishes its result. The reader performs
connect and delivers `connected`, ordered complete frames, then one `closed`
notification. `PduFramer` handles fragments and coalesced input with a bounded
frame allocation; each delivered array belongs to the listener. A fixed read
buffer is at most 8 KiB. EOF between frames is `EOF`; truncated or oversized
frames are `MALFORMED_FRAME`.

`close()` aborts pending and active work, closes the socket and interrupts owned
workers. It is idempotent. `termination()` completes after the winning physical
close attempt, all three workers, all accepted writes and internal terminal
callbacks finish. Derived futures cannot complete or cancel the transport's
private result. User dependent actions run only after cleanup and may occupy
the completion thread; they cannot postpone socket closure or the other workers.

Connection failure is supplied through `FrameListener.closed`; ordinary failure
with successful cleanup still has normally completed termination. Physical close
or terminal-callback failure makes termination exceptional with
`TransportFailure.Kind.CLEANUP_FAILED`. Only the first cleanup cause is retained,
so diagnostics remain bounded. The original connection reason is preserved.
A failed physical close is reported as a failed cleanup attempt, without claiming
that the socket has closed successfully.

## Bounded writes and request certainty

`write(frame, writeClass, deadlineNanos, observer)` does not wait for capacity or
network I/O. It checks the complete frame length, the unsigned prefix, the maximum
frame size, lifecycle, deadline and selected capacity before copying the array.
The caller keeps its array. Malformed input throws `IllegalArgumentException`;
closed/not-started, expired and full admission throw `TransportFailure` without
calling the observer. Every admitted write has exactly one `written` or `failed`
notification. Notifications and guards never run under transport locks.

`ORDINARY` and `CONTROL` each have finite, separate count and byte bounds.
Reservations include queued and active writes, and are released once on terminal
selection. Ordinary writes remain FIFO. Control traffic takes priority over
queued ordinary frames; it cannot interrupt a physical TCP write already in
progress. Consequently, an active slow write can delay control traffic until
that write's deadline closes the connection. Priority does not guarantee fairness
under continuously replenished control traffic.

The absolute deadline uses the same `System.nanoTime()` origin as request
tracking. The positive future offset must be less than 2^63 nanoseconds;
subtraction handles absolute timestamp wraparound. The transport cannot infer
whether an arbitrary caller-supplied timestamp violated that offset contract.
The deadline worker scans only bounded queues plus one active write and connect
attempt; it owns no external timer tasks. Expired queued writes fail without
sending bytes. Active write expiry closes the stream, since Java sockets have
no general write-timeout option. No automatic retries occur.

Immediately before physical output, the writer calls `beforeWrite()`. Request
tracking adapts this to its atomic `beginWrite(handle)`: true marks possible
transmission; false prevents every byte if timeout/cancellation already won.
Closure or expiry after true may still prevent output. `WriteHandle.cancel()`
returns true only while the frame is queued, guaranteeing no bytes and one
`CANCELLED` notification. False is not proof of peer receipt. Cancellation of an
owning request can still prevent a claimed transport write through the guard.

`TransportFailure.writeStarted` conservatively identifies writer claim, which
may precede guard execution. The request window remains the authority for
`NOT_SENT` versus `MAY_HAVE_BEEN_SENT`. A successful local output call proves
neither peer receipt nor an SMPP response.

The observer and listener interfaces are **internal, fast, nonblocking** ports.
Their methods may be concurrent across read, write, deadline and close paths.
Callers must dispatch application work and user-facing future completion through
separately bounded facilities. Blocking an internal observer violates the port
contract and can delay shutdown; the race tests use bounded gates deliberately
to establish the cleanup barrier. Guard or frame callback exceptions close the
connection with `OBSERVER_FAILED`. Terminal callback exceptions additionally
make cleanup fail; they do not strand other accepted work.

## Listening

`TcpListener.bind(resolvedLocalAddress, backlog, config, acceptListener)` binds
and starts one virtual acceptor, plus a cleanup waiter. Port zero selects an OS
port, exposed by `localAddress()`. Backlog is an OS pending-connect hint, not a
library connection-limit promise.

`AcceptListener.accept(unstartedTransport, peerAddress)` reserves connection
capacity synchronously and returns true to take ownership while the listener
remains open. The endpoint owns its connection count and supplies this decision.
There is no accepted-connection queue. False or a callback exception rejects the
connection, closes it and waits for its internal cleanup before the next accept.
This includes a callback that started the transport and then rejected it.
Concurrent listener closure also cleans a provisional handoff. Transports whose
ownership was already transferred remain independent of listener shutdown.

Listener termination waits for physical listener close, accept-loop exit and
all listener-owned handoff cleanup. It uses the child's internal cleanup barrier,
so application dependents on the child's public stage cannot block the acceptor.
Unexpected acceptance failure reports `LISTEN_FAILED`; failed physical or
owned-child cleanup reports `CLEANUP_FAILED`.

## Java 21 choices

The [Java 21 Socket API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html)
documents timeout-bounded connect, close waking blocked I/O, and virtual-thread
interrupt closing a system-default socket during blocking connect/read/write.
`SO_TIMEOUT` bounds reads, so active-write expiry explicitly closes the socket.
The [ServerSocket API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html)
also documents close and virtual-thread interruption of blocking accept.

In Java 21, blocking while holding a monitor can pin a virtual thread to a
carrier; native calls can also pin. Short `ReentrantLock` sections protect the
transport's state, and socket I/O/callbacks occur outside those sections.
Virtual threads are created per owned task and are not pooled. These choices
follow the [Java 21 virtual-thread guide](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html),
which describes JFR pinning events and `jdk.tracePinnedThreads`. They do not
establish scalability for arbitrary callback code or different JDK versions.

## Fresh development experiment

`TransportExperiment` is test-source tooling, excluded from every runtime/source/
Javadoc library archive and the runtime classpath. It explicitly starts a raw
frame echo server or client. It is separate from the planned message simulators
and the `W-*` workloads: there is no bind, message, profile, request tracker or
application handler. The echoed 16-byte frames are compared independently by the
client. This checks framing, write accounting and cleanup, not SMPP request/
response interoperability.

The bounded accounting test first failed with zero counts and accepted invalid
bounds, then passed with real sockets. The launcher accepts 1..64 connections
and 1..10,000 rounds; each connection has at most one outstanding echo. Each
operation has a five-second deadline, and the server cohort has a 30-second
wait bound. A missing/wrong frame, terminal failure or unfinished cohort gives
an exceptional/nonzero process result. No default Gradle task runs this load.

On 9 September 2026, three fresh runs used separate client and server JVMs on
loopback, eight connections, 200 rounds, no warmup, no arrival-rate target,
16-byte frames and the transport defaults. Each run completed 1,600 originating
writes, 1,600 matching echoed frames and 1,600 server echo writes. All owned
cleanup stages completed; all six processes exited zero. The exchange interval
starts after connection establishment and ends after the expected echoes and
local write completions; JVM startup, connect, cleanup and diagnostic shutdown
are outside that interval.

| Run | Exchange interval (ms) | Client peak RSS (KiB) | Server peak RSS (KiB) | Client heap used after cleanup (bytes) | Server heap used after cleanup (bytes) |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 117.029 | 114,080 | 110,184 | 8,441,744 | 8,210,096 |
| 2 | 101.361 | 100,404 | 103,464 | 8,247,520 | 8,121,168 |
| 3 | 104.671 | 105,984 | 102,952 | 8,425,576 | 7,941,224 |

Environment: Ubuntu OpenJDK `21.0.12+8-1-24.04-Ubuntu`, Linux
`7.0.0-31-generic` x86_64, Intel i5-11400F with 12 logical CPUs, approximately
31.2 GiB visible RAM and 8 GiB unused swap. Each process had `-Xms256m -Xmx512m`,
the default G1 collector, native memory tracking and JFR profile instrumentation.
The workstation was shared with development processes. There was no forced GC.
Heap-used snapshots include uncollected objects. Peak RSS is whole-process
resident memory and is a different measurement from heap occupancy.

NMT committed non-heap memory at VM exit (total committed minus the Java Heap
category) was 131,755,226 / 123,973,388 / 125,122,469 bytes for clients and
130,331,400 / 123,372,056 / 124,779,864 bytes for servers. NMT commitment is not
RSS and includes diagnostic/JVM overhead; it does not measure every native
allocation. The [NMT guide](https://docs.oracle.com/en/java/javase/21/vm/native-memory-tracking.html)
documents its limits and exit-report options.

All six JFR summaries recorded zero `jdk.VirtualThreadPinned` events at the
profile's default 20 ms threshold, and no pinning trace appeared. These brief
instrumented runs do not rule out shorter pinning or establish heavy-load,
latency-percentile, WAN, TLS or production-capacity results.

To reproduce, compile with normal caches, then run server and client in separate
terminals. Replace `PORT` with the server's printed port, and give each repetition
fresh output paths. The exact commands below reproduce one recorded configuration:

```sh
./gradlew testClasses --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step10'
/usr/bin/time -v -o /tmp/echo-server.time java -Xms256m -Xmx512m \
  -XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics \
  -Djdk.tracePinnedThreads=full -Xlog:gc:file=/tmp/echo-server.gc \
  -XX:StartFlightRecording=settings=profile,maxsize=16m,filename=/tmp/echo-server.jfr,dumponexit=true \
  -cp build/classes/java/main:build/classes/java/test \
  kg.aidarbek.smpp.transport.TransportExperiment server 8 200
/usr/bin/time -v -o /tmp/echo-client.time java -Xms256m -Xmx512m \
  -XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics \
  -Djdk.tracePinnedThreads=full -Xlog:gc:file=/tmp/echo-client.gc \
  -XX:StartFlightRecording=settings=profile,maxsize=16m,filename=/tmp/echo-client.jfr,dumponexit=true \
  -cp build/classes/java/main:build/classes/java/test \
  kg.aidarbek.smpp.transport.TransportExperiment client PORT 8 200
jfr summary /tmp/echo-server.jfr
jfr summary /tmp/echo-client.jfr
```

The actual orchestration and per-process command arrays are preserved in
`/tmp/step10-run-experiment.py` and `/tmp/step10-experiment-results.json`;
`/tmp/step10-experiment-environment.json` holds fresh environment output.
Each `/tmp/step10-experiment-{1,2,3}-{client,server}` prefix has `.log`, `.time`,
`.gc`, `.jfr` and `.jfr-summary` outputs. These executions were outside Gradle
and were never reused from a build cache. Source identities, TDD, shared real/
fake contract results and validation are in the [transport review](reviews/0009-tcp-transport.md).
