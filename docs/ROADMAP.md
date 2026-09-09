# Step-by-step roadmap

Current stage: **Step 1 — scope and API discussion**.

This is a proposed development sequence. Each implementation step should produce
a small working result with focused verification before moving to the next.

The development tooling baseline is configured: Java 21, compiler warnings as
errors, Gradle caching, editor conventions, and Git. Follow the
[development guide](DEVELOPMENT.md) throughout the steps below.

## 1. Define scope and sketch the API

Decide:

- Minimum Java version and initial SMPP version.
- Client-only or client and server support for the first release.
- What “lightweight” means for dependencies, API size, and expected connection load.
- The first supported operations and whether callers need blocking or asynchronous requests.

Result: a short list of supported use cases and a Java usage example to guide the
implementation.

## 2. Implement the PDU header and framing

Represent the command length, command ID, command status, and sequence number.
Implement binary encoding and decoding independently of network connections.

Verify known wire bytes, invalid lengths, truncated input, and handling of frames
split across reads or combined in one read.

## 3. Implement the first command bodies

Start with binding, `enquire_link`, and `unbind`, including their responses. Then
add `submit_sm`, `deliver_sm`, and optional tag-length-value (TLV) parameters as
required by the selected scope.

Verify field order, string limits, response matching fields, error responses, and
malformed bodies against protocol examples.

## 4. Add connections and session behavior

Introduce the selected transport, binding state, request/response correlation,
timeouts, and connection cleanup. Add a bounded number of outstanding requests if
asynchronous sending is in scope.

Verify using a local test peer, including failed binds, lost connections,
unanswered requests, and clean shutdown.

## 5. Complete the first messaging flow

Expose message submission and incoming delivery handling through the public API.
Choose explicit text encoding behavior and preserve access to raw message bytes.
Decide how delivery acknowledgements and delivery receipts are exposed.

Verify submission and delivery over a local connection, then document a complete
example. Record any message length and encoding limitations.

## 6. Review further features individually

Evaluate server support if deferred, additional encodings, multipart messages,
automatic keepalives, reconnect behavior, TLS, and additional SMPP commands as
separate steps driven by actual use cases.

Automatic resubmission needs an explicit policy because a missing response does
not establish whether the SMSC accepted a message.
