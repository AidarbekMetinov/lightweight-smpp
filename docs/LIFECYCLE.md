# Connection lifecycle

TLS, automatic idle enquiries and reconnect are explicit opt-ins. Existing
endpoint constructors retain plain TCP, application-initiated enquiry and one
connection attempt. These policies apply to SMPP 3.4 and 5.0 without changing
bind permissions, negotiated capabilities, application message completion or
the no-replay contract.

## TLS ownership and identity

`TlsConfig` belongs to the concrete transport. Supply an initialized JDK
`SSLContext` with the intended key and trust managers. The configuration retains
that context; the caller owns credential/trust selection and must not
reinitialize it or mutate its managers concurrently with transport use.
Certificate rotation uses a newly configured endpoint for new connections.

```java
ConnectionLifecycle lifecycle = ConnectionLifecycle.defaults()
        .withTls(TlsConfig.client(clientContext, "smsc.example", Duration.ofSeconds(3)))
        .withKeepalive(new KeepalivePolicy(Duration.ofSeconds(30), Duration.ofSeconds(5)));
SmppClient client = new SmppClient(EndpointOptions.defaults(), ExchangeConfig.defaults(), lifecycle);
```

Here `clientContext` is caller-initialized trust/key configuration. The explicit
certificate DNS name or IP identity is independent of the resolved TCP target.
The transport enables the JDK HTTPS endpoint-identification algorithm and TLS
1.3/1.2; trust validation remains enabled. Neither reverse DNS nor a permissive
trust manager supplies an implicit identity. See the JDK contracts for
[SSLParameters](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLParameters.html),
[SSLSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLSocket.html)
and [layered socket creation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLSocketFactory.html).

For listeners use `TlsConfig.server(serverContext, handshakeTimeout)`; its
three-argument overload can require a trusted TLS client certificate. Ordinary
SMPP bind authentication still runs after TLS readiness. A TLS certificate does
not itself authorize a system identifier, bind mode or command.

The longest constructor of each endpoint accepts `ConnectionLifecycle` after
its existing `ExchangeConfig`. TLS role follows the TCP connection origin:

| Endpoint owner | TLS policy |
| --- | --- |
| `SmppClient` | Client with explicit expected certificate identity |
| `SmppServer` | Server, optionally requiring a client certificate |
| `OutbindConnector` | Client, although its SMPP role is message center |
| `OutbindListener` | Server, although its SMPP role is ESME |

The concrete `TcpTransport.connect/adopt` and `TcpListener.bind` overloads also
accept TLS configuration. The frame port and the per-connection coordinator
continue to exchange owned complete SMPP frames; they expose no TLS socket or
cryptographic context. TLS negotiation does not invoke SMPP authentication,
readiness or message callbacks. Caller-provided TLS providers and key/trust
managers can execute inside JSSE negotiation and must return cooperatively.
Java cannot forcibly stop arbitrary provider code: raw socket abort can proceed
while a blocked provider retains its worker and connection permit, which bounded
endpoint termination reports as retained ownership.

## Deadlines and termination

TCP connection establishment has its original absolute deadline. A connecting
transport starts its separate handshake budget after TCP succeeds. An adopted
transport starts that budget at adoption, including time before `start` installs
its listener. Queued frame deadlines continue running during negotiation; their
transmission guards cannot start before TLS readiness.

For initiating endpoints, the outer startup allowance includes TCP plus TLS,
and bind time starts at transport readiness. For accepted connections, the
existing total bind deadline starts at accepted arrival and includes TLS,
authentication, outbind decisions and follow-up binding. A more restrictive
accepted-bind deadline can therefore expire before the transport's handshake
deadline. No timer resets extend a received unauthenticated connection's life.

Trust, identity, provider setup and negotiation failures retain
`TransportFailure.Kind.TLS_HANDSHAKE_FAILED`; its cause remains available.
Handshake expiry reports `TLS_HANDSHAKE_TIMEOUT`. These are local transport
outcomes, distinct from a negative SMPP bind. Neither failure can publish a
bound session or dispatch credentials to authentication.

Transport close aborts the raw socket before closing a layered TLS wrapper, so
a slow peer cannot force an unbounded TLS close-notify exchange. Graceful SMPP
shutdown first drains admitted work and attempts unbind within its original
bound. Explicit close aborts immediately. Idempotent close and late handshake
completion cannot restore readiness. Physical termination still waits for the
winning close, accepted write notifications and owned I/O workers; cleanup
failures remain exceptional.

`EndpointTermination` is an immutable snapshot at the shutdown bound. An
uncooperative handler or completion callback may remain counted after logical
cancellation. A graceful drain that consumes its entire budget can also report
physical connection cleanup still in progress after forced abort. Inspect the
reported counts/failures; a completed observation alone does not assert complete
resource cleanup. User callbacks never delay the raw socket abort.

## Optional idle enquiries

`KeepalivePolicy(idleInterval, responseTimeout)` counts time without received
complete PDUs while bound. At most one automatic `enquire_link` is outstanding.
A matching success clears it; a missing reply, negative response or generic
nack closes that generation. `BoundSession.closeReason()` retains the winning
local failure, including the original `RequestFailure` certainty and cause or
the peer nack. Explicit close and a successful unbind normally have no failure.

Both manual and automatic enquiries use the existing request window and its
notification capacity. They use the transport's bounded CONTROL queue alongside
unbind and control replies, so ordinary message write saturation leaves that
reserve available. This reserves no additional request-window slot and cannot
preempt a frame already being written. A peer that stops reading is handled by
the active write's deadline and connection cleanup.

Window/byte/notification admission saturation defers automatic enquiry within
the original idle-plus-response budget. It cannot restart that budget on each
tick. Exhausted transport CONTROL capacity fails the one unsent enquiry and
closes the connection with its local write failure; there is no hidden work
queue. Shutdown stops automatic admission, then drains the already-admitted
request according to the ordinary shutdown contract.

## Explicit reconnect without replay

`SmppClient.reconnect(config, new ReconnectPolicy(maximumAttempts, retryDelay),
observer)` owns a finite sequence of fresh connections. `maximumAttempts`
includes the initial attempt and pre-connect admission failures. The analogous
`OutbindConnector.reconnect(remoteAddress, outbind, policy, observer)` repeats
connection authentication on new sockets; accepting owners continue to accept
explicit new peers.

Each replacement gets a fresh generation and sequence namespace. Only connection
and bind configuration crosses generations. Submitted message objects, request
handles, message identifiers and pending results are never retained for replay.
An actually written submission that disconnects remains
`MAY_HAVE_BEEN_SENT` in its original result even when a replacement binds.
Applications must make any reconciliation or deduplication decision themselves.

`ReconnectHandle.cancel()` selects cancellation once, aborts a connecting or
bound generation, and stops later attempts. `close()` delegates to cancellation.
Endpoint shutdown stops all its reconnect controllers. A losing cancellation
cannot change an already-selected graceful endpoint stop. Late authentication
or transport readiness cannot create a new readiness offer after cancellation.
An offer already reserved for notification is not retracted: its callback may
still run with a now-closed session, retaining capacity until it returns.

`currentSession()` is a momentary optional bound-generation observation.
`termination()` separately returns a protected `ReconnectResult` with the reason,
attempt/publication counts and latest failure. It waits for physical retirement
and any earlier ready-observer invocation. This stage can remain incomplete
while an observer is blocked; endpoint shutdown still returns its bounded
resource snapshot. Observation through a derived future does not confer
cancellation ownership.

Controllers share the endpoint timer and are capped by `maximumConnections`.
Each has at most one current attempt and a reserved terminal notification. A
replacement waits for physical connection retirement, the configured backoff,
and return of the preceding ready observer. Observers use the endpoint's
existing bounded notification dispatcher. Full notification capacity closes an
unpublishable generation; a throwing observer ends the controller and retains
its cause. Blocked observer invocations and result dependents continue to occupy
their physical notification permits. Repeated close/connect cannot accumulate
unbounded worker threads or an undisclosed notification queue.

## Resource observations and standalone exercise

Connection, request-window, reconnect and listener coordination use explicit
reentrant locks on Java 21. Callbacks that share connection state use the same
guard, allowing a virtual thread waiting on another lock to release its carrier.
This preserves the existing critical sections and deadline/ownership rules.
Fresh two-carrier JVM regressions cover responses, request admission, reconnect
cancellation, and both listener owners' start/shutdown/close paths. See the
[load-discovered defect and fix](reviews/0018-carrier-progress.md).

`BoundSession.resources()` returns `SessionResources` with pending request
count/bytes and paired application reply count/retained bytes. Requests include
controls and all implemented paired operations. Reply reservations include the
received frame and fallback/ready response through handler, ordering and active
write settlement. Samples exclude control replies, one-way notifications and
transport buffering; they are not endpoint-wide totals. Independent request
settlement may occur between reads, so this is diagnostic sampled data rather
than a transactional admission API.

The separate simulator `LifecycleSettings` adapter supplies the lifecycle option
schema and context loading for the Step 18 CLI composition. It accepts explicit
`tls=off|on`, PKCS12 key/trust paths, a client peer name, handshake timeout,
optional client-certificate requirement, keepalive intervals and finite reconnect
attempt/delay settings. Password options name environment variables, defaulting
to `SMPP_TLS_KEYSTORE_PASSWORD` and `SMPP_TLS_TRUSTSTORE_PASSWORD`; reports retain
the configured nonsecret paths and environment-variable names for reproducibility,
while secret values never enter settings or reports. Temporary password arrays are erased after
context initialization. Without a configured trust store the JDK's default
trust managers apply. The adapter rejects missing secrets and contradictory or
unbounded inputs before creating endpoints. Deliberate workload churn remains a
separate tool policy.

Compiled tests exercise this adapter over real TLS with both profiles, as well
as raw-peer trust/identity/mutual-authentication faults, all valid bind and unbind
origins, stalled TLS handshakes, an 8 MiB write to a nonreading peer, blocked
application handlers, saturated callback queues and ambiguous submissions across
reconnect. These are finite correctness exercises, not a production throughput
claim. The shared `FrameTransportContract` runs unchanged against both TLS roles.

```shell
./gradlew :test --tests 'kg.aidarbek.smpp.endpoint.Tls*' --tests 'kg.aidarbek.smpp.endpoint.Keepalive*' --tests 'kg.aidarbek.smpp.endpoint.Reconnect*' --tests 'kg.aidarbek.smpp.transport.Tls*' --console=plain
./gradlew :simulator:test --tests 'kg.aidarbek.simulator.Lifecycle*' --console=plain
```

The PEM and PKCS12 material under the two test-resource trees is intentionally
public development-only material, generated for these finite local fixtures.
The self-signed certificate has DNS `localhost` and IP `127.0.0.1` SANs. It is
never loaded by library defaults. See the [Step 17 review](reviews/0016-connection-lifecycle.md)
for exact TDD evidence, fixture corrections, inventory and final verification.
