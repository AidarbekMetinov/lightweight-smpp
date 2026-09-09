# Independent interoperability

Cloudhopper is an external comparison tool only. Its implementation, dependencies,
peer adapters and test project live outside this repository. The library,
simulators, examples, project tests and build source sets contain no Cloudhopper
code or dependency. This is an explicit user requirement, also recorded in
[project guidance](../AGENTS.md).

## Pinned external peer

The comparison uses the published `com.fizzed:ch-smpp:5.0.9` artifact, built from
tag `v5.0.9`, commit `fb2bd78ff4a8e28145f38e4187dbc9037f70c002`. Its binary SHA-256
is `a9235c5c00270e0477bb5d32ad832f7c252ad2c8a397f48abc91486791923721`;
its source archive SHA-256 is
`d5c9e3aaf2a4ab31c9a45ef0e53ff290aa45454b35db28a854d5624cf977234d`.
These identify the peer actually executed, rather than a moving default branch.
The published [POM](https://repo.maven.apache.org/maven2/com/fizzed/ch-smpp/5.0.9/ch-smpp-5.0.9.pom)
and [tagged source](https://github.com/fizzed/cloudhopper-smpp/tree/v5.0.9)
provide the upstream identity.

Each peer runs in a separate JDK 21 JVM with its own classpath. It asserts that it
cannot load `kg.aidarbek.smpp.endpoint.SmppClient`. The comparison controller uses
our compiled library JAR and communicates with the peer through loopback TCP.
No shared codec or message helper constructs both sides' expected fields.

Configuration: SMPP 3.4, loopback IPv4, ephemeral test ports, peer request window 8,
5-second connect/bind/request deadlines, peer JVM `-Xms32m -Xmx128m`, and explicit
process cleanup. The two application identities use literal demonstration-only
credentials. Each test gives all waits a finite deadline. Test cleanup checks
both endpoint termination and the external process exit.

## Verified subset and boundaries

| Scenario | Our client / external server | Our server / external client |
| --- | --- | --- |
| Receiver, transmitter and transceiver bind | Separate cases | Separate cases |
| `enquire_link` and successful `unbind` | Status and sequence checked | External response checks and local termination |
| Binary `submit_sm`, standard user-reference TLV | Fields and returned message ID checked | Fields and returned message ID checked |
| Unknown vendor TLV reception | No vendor-specific outgoing profile claimed | Exact raw incoming octets retained |
| Negative `submit_sm_resp` | Explicit peer header-only response configuration | Peer accepts our canonical header-only response |
| `deliver_sm` | Separate RX and TRX cases accepting binary delivery | Binary delivery and empty response message ID |
| `data_sm` | Binary `message_payload` and message ID | Requests in both directions |
| `query_sm`, `cancel_sm` | Selector fields, state/result and response checked | Independent requests and matching typed handlers |
| `replace_sm` | Separate SMPP 3.4 transmitter case | Independent transmitter and matching replacement handler |
| Bad bind credentials | Documented peer incompatibility below | Independent client receives status `0x0e` |
| `submit_multi`, outbind, broadcast operations | Unsupported by this peer's typed codec inventory | Unsupported by this peer's typed codec inventory |

Full local operation/version/role coverage is recorded separately in
[the protocol inventory](PROTOCOL.md). Independent 5.0 broadcast verification
uses separately derived specification fixtures; an external 5.0 service remains
pending. The artifact version `5.0.9` is not evidence of complete SMPP 5.0 support.

## Observed compatibility details

Cloudhopper's default failed 3.4 bind response includes its `system_id` and
`sc_interface_version` despite nonzero status. A separate raw socket capture
observed this exact 33-octet frame for a transceiver rejection:

```text
00000021 80000009 0000000e 00000001
696e646570656e64656e7400 0210000134
```

The [SMPP 3.4 bind response notes](https://smpp.org/SMPP_v3_4_Issue1_2.pdf)
require the body to be absent on failure. Our strict decoder therefore reports
`EndpointException.Reason.PROTOCOL` for that frame. The comparison records this
as an incompatibility, rather than claiming normal bind-status interoperability
or weakening the decoder.

For negative submission, the external application explicitly sets the response
PDU length to 16 with a null message ID. Cloudhopper's default size calculation
otherwise reserves a trailing NUL octet. Its existing encoder supports the
explicit header-only size. Successful delivery responses use an empty message
ID as required by 3.4. These are recorded peer application settings; no upstream
source was patched. See the tagged
[response body](https://github.com/fizzed/cloudhopper-smpp/blob/v5.0.9/src/main/java/com/cloudhopper/smpp/pdu/BaseSmResp.java)
and [transcoder](https://github.com/fizzed/cloudhopper-smpp/blob/v5.0.9/src/main/java/com/cloudhopper/smpp/transcoder/DefaultPduTranscoder.java).

The peer's services validate fields and reply from memory. They provide no
persistence, handset delivery, cancellation store or billing implementation.
Successful exchanges establish protocol/application-boundary compatibility for
these fixtures, not production compatibility with every SMSC or carrier.

## Executing the external comparison

The local comparison work area is `/tmp/lightweight-smpp-external-interop`.
Its standalone Gradle build resolves the pinned peer independently and selects
our library JAR as a file input. Change that file input when selecting another
candidate, then run its tests explicitly with fresh execution. This setup is
outside the repository and is not required for ordinary project builds.

Final candidate source identities, fresh comparison counts and measurement
limitations belong in [the release evidence](RELEASE.md). A copied report from a
previous candidate is not fresh interoperability evidence.
