# Request tracking

Step 9 adds `kg.aidarbek.smpp.request` without changing protocol values, codecs or
pure session policies. `RequestWindow` owns outgoing correlation, pending count
and byte capacity, sequence allocation and terminal outcomes. It does not own a
socket, encode PDUs, enforce session permissions, schedule timers or replay work.
The [API contracts](API.md) remain the endpoint design baseline; this guide gives
the compiled request API. [The review](reviews/0008-request-tracking.md) records
TDD and complete per-type SOLID evidence. [Step 11 endpoints](ENDPOINTS.md) now
compose this owner with the pure policies and frame transport.

## Admission and identity

Create one window for the locally initiated requests on a connection. Supply a
fresh immutable `UUID` generation for every connection, including reconnects.
Never create replacement windows with the same generation. Peer-initiated
requests occupy a separate namespace: receiving a request with the same sequence
as a local pending request does not consume that local request.

A window admits with a positive request limit and a pending frame-byte limit of
at least 16. Each admission also reserves one completion-notification permit.
The default policy rejects immediately if any bound is full. There is no waiting
admission queue. Pending byte accounting uses the exact encoded request size,
including its header, supplied by the endpoint. Each size must fit the unsigned
SMPP length range, 16..0xffffffff. Encoding and inbound frame bounds remain the
codec/endpoint owner's responsibility.

Sequences begin at 1 and increase to `0x7fffffff`, without wrapping or reuse after
completion. Exhaustion produces `RequestFailure.Reason.SEQUENCE_EXHAUSTED`;
start a new connection and generation before more requests can be admitted. No
failed admission consumes a sequence, byte slot or notification permit. A
package-private starting-sequence constructor exists for deterministic boundary
verification; ordinary callers cannot reset a window's allocator.

Admission is generic over the expected decoded command type. Supply the exact
response command ID, equal to the request ID with bit 31 set. Adding another
request/response codec needs no command-specific branch in the window. Session
permission and validation must happen before network work is reserved.

```java
LongSupplier clock = System::nanoTime;
BoundedNotifications notifications = new BoundedNotifications(128, 2);
RequestWindow window = new RequestWindow(
        UUID.randomUUID(), 32, 1_048_576, notifications, clock);

long invokedAt = clock.getAsLong();
// Validate the operation and determine the encoded size within this same timeout.
RequestHandle<ControlCommand> request = window.admit(
        0x15, 0x80000015L, ControlCommand.class, 16,
        RequestOptions.timeout(Duration.ofSeconds(10)), invokedAt);
PduHeader originalHeader = request.requestHeader();
CompletionStage<Pdu<ControlCommand>> result = request.result();
```

The endpoint owns the shared dispatcher in this example. Close windows first,
then close the dispatcher and await it within the owner's shutdown bound. The
alternate constructor taking `maxNotifications` creates one virtual worker
owned by that window. Closing that window closes its dispatcher; its
`awaitNotifications(Duration)` and `notificationsOutstanding()` expose bounded
cleanup reporting. With a supplied dispatcher these methods report the entire
shared dispatcher, which the caller must close.

## Time and transmission

`RequestOptions.timeout(Duration)` is a positive total invocation budget,
representable in signed nanoseconds. It includes validation, admission, writing
and response waiting. Use the overload accepting `invocationStartNanos` when
endpoint work precedes admission; capture that tick before validation from the
same clock supplied to the window. The convenience overload captures a tick at
its own invocation. Rejected arguments throw `IllegalArgumentException` or
`NullPointerException`; otherwise rejection uses structured `RequestFailure`
with sequence zero and `NOT_SENT`.

Clocks follow `System.nanoTime()` semantics. Negative ticks and signed wraparound
are valid. Deadline checks use subtraction, and observed elapsed intervals must
remain below half the unsigned long range. `RequestHandle.deadlineNanos()` can
be passed to a transport using the same clock domain. No wall clock is used.

The endpoint must call `expire()` regularly. The window has no timer thread;
without a pump or another terminal attempt, the passage of time alone does not
publish a timeout. A due deadline wins at matching response acceptance,
`beginWrite`, cancellation, write failure or disconnect even when no scheduled
expiry callback has run. Once another outcome has settled, later clock changes
cannot replace it.

The transport must call `window.beginWrite(handle)` immediately before its first
physical write. A `false` result prohibits that write. The first successful
call atomically sets `MAY_HAVE_BEEN_SENT`; repeated calls return false. The
transition is deliberately conservative: a failure immediately afterward may
still have sent zero bytes, but tracking can no longer prove that. Partial and
complete local writes cannot prove acceptance by the peer.

If cancellation or a deadline wins before the guard, the failure carries
`NOT_SENT`, and the guard will reject any later attempt. Removing the transport's
queued frame can release transport capacity earlier, but correctness does not
require queued cancellation to win: the guard still prevents writing. Immediate
transport rejection and asynchronous write failure both call
`failWrite(handle, cause)`; certainty comes from the guard, not exception text.
A completed local write leaves the request pending for its peer response.

## Correlation and outcomes

`pending(sequence)` returns immutable request metadata for constructing the
existing session `ResponseContext`. The snapshot alone is not proof that the
request will still be pending later. After decoding and applying session response
policy, `accept(generation, pdu)` atomically checks generation, sequence, exact
response command and response representation, then competes for settlement.
Out-of-order responses are accepted. Wrong commands, wrong generations, unknown
keys, duplicates and late responses return false and cannot complete another
request. A due deadline can also make acceptance return false while settling
the handle as expired.

The Step 11 coordinator additionally ignores peer responses while the matching
handle is still `NOT_SENT`, before passing correlation context to session policy
or calling `accept`. An admitted request is not proof that its write guard has
run; a guessed early response cannot settle a queued bind or control request and
thereby suppress its actual output. This physical-write precondition belongs to
endpoint composition; the generic window remains the single terminal owner.

Normal operation responses, including nonzero and unknown numeric status values,
complete `CompletionStage<Pdu<R>>` normally. `generic_nack` has a distinct wire
command and can acknowledge any pending local operation. A matching negative
nack settles exceptionally with `PeerNackException`, which retains the generation,
sequence, raw status and ordered binary TLVs. This exception is a known peer
outcome and does not imply local transmission ambiguity. A zero-status nack is
not accepted, and sequence-zero nacks cannot match locally assigned requests.

`RequestFailure` describes a local outcome with reason, operation, generation,
assigned sequence when present, transmission certainty and an optional cause.
Local terminal reasons are `CANCELLED`, `DEADLINE_EXPIRED`, `DISCONNECTED` and
`WRITE_FAILED`. Admission additionally reports `CLOSED`, `WINDOW_FULL`,
`BYTE_LIMIT`, `NOTIFICATION_BACKLOG` or `SEQUENCE_EXHAUSTED`. These structured
exceptions preserve their fields through Java exception serialization. The nack
exception stores owned primitive TLV snapshots so serialization does not require
protocol values to implement `Serializable`.

`RequestHandle.cancel()` is local competition for the terminal outcome. It never
sends `cancel_sm`, withdraws bytes, or causes replay. It returns true only when
cancellation wins; it returns false after another outcome or a due deadline.
Cancellation is a structured exceptional result, not mutation of the internal
future's cancellation flag. `failWrite` follows the same winning-reason boolean
rule. `disconnect(cause)` closes admission and settles every pending request;
it returns the number newly settled, including deadlines already due. Repeated
disconnect and close are harmless. Socket closure remains the endpoint's job.

Exactly one terminal snapshot becomes visible via `isDone()` and
`terminalOutcome()`. It contains either an operation response or a failure.
Pending count and bytes are released at that transition, before asynchronous
notification. No terminal path re-admits or resends a request.

## Completion delivery and cleanup

`result()` returns a minimal `CompletionStage`. Completing, cancelling or
obtruding a derived `toCompletableFuture()` result cannot mutate the library's
internal outcome. Waiting or interrupting a caller's wait never cancels the
protocol request. Applications use `RequestHandle.cancel()` explicitly.

`BoundedNotifications` reserves capacity before accepting a request. Its queue
and running callbacks together cannot exceed those reservations. Fixed virtual
workers perform notifications outside transport progress and all window and
dispatcher locks. Idle workers use `ReentrantLock`/`Condition` so Java 21 virtual
threads can unmount while waiting. One worker begins queued notifications in
dispatch order; multiple workers permit concurrent and potentially reordered
callback starts and completions.

A non-async future dependent may run on the completing worker and block it.
Its notification permit remains occupied until the callback returns. Internal
settlement, count/byte release, other transport guards, expiry and disconnects
continue. New admission can reject with `NOTIFICATION_BACKLOG` even while the
pending request count is zero. Use an endpoint-wide shared dispatcher to bound
this retained work across disconnected or replaced sessions as well.

The dispatcher also exposes `tryReserve()` with a single-use `Reservation`, and
`tryDispatch(Runnable)` for endpoint result publication. Every issued reservation
must eventually be dispatched or released. Closing admission does not revoke
existing permits: already reserved work can still be queued and delivered.
`outstandingCount()` counts reserved, queued and currently executing work.
`awaitTermination(Duration)` returns false when a callback or unconsumed permit
outlives its bound; it never silently discards notifications. Unchecked Runnable
failures are retained by `lastFailure()` for owner reporting, capacity is released
and subsequent queued notifications continue. Only the most recent failure is
retained, bounding diagnostic history.

Application code must use an appropriate executor for expensive dependent
stages and cooperate with shutdown. Neither virtual threads nor a timeout can
forcibly finish arbitrary application code. Endpoint cleanup can still close
sockets and settle internal requests, while accurately reporting remaining
notification work.
