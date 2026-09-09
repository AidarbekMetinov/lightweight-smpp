# Review: independent finite SMPP fault peer

## Scope and source identity

Baseline: `eee514218ee08eee0481d1edb8d8395d0a2b1b6e`, isolated worktree
`/tmp/lightweight-smpp-step18-fault-peer`. This change owns exactly two Python
files and two documents. It changes no Java, Gradle logic, dependency declaration,
existing simulator source, or library artifact. There is no imported third-party
SMPP implementation. The Python runtime and tests use only the standard library.

| Final formatted source | SHA-256 |
| --- | --- |
| [`simulator/scripts/fault_peer.py`](../../simulator/scripts/fault_peer.py) | `96bd4be6d122077df1ac9e3de35445bb03e8238f004aa16396f0afe2e9c28e43` |
| [`simulator/scripts/tests/test_fault_peer.py`](../../simulator/scripts/tests/test_fault_peer.py) | `43c4ba86e6359f387ab19861bbb93b4cd447424f8e7e7e4c1ac3f22f665aabc1` |

The complete Python inventory has **16 types and 83 functions/methods**: nine
runtime types with 46 functions/methods, and seven test/fixture types with 37
functions/methods. There are no nested/local type declarations, asynchronous
functions, or lambdas in either source. Generated dataclass operations are
covered with their declaring types. The external AST inventory identifies source
lines and repeats each whole-file hash. Every declared identity is reviewed below.
The automatic Java inventory is unchanged; these manual Python reviews do not
claim coverage by the Java review parser or ArchUnit.

The public surface is the documented finite CLI and fresh JSON report. Internal
Python values and helpers are deliberately not a general-purpose protocol SDK.
The callers of those helpers construct immutable `bytes`, use validated `Config`
instances, and run the connection owner on one thread. See the
[guide](../FAULT_PEER.md) for the supported operation/profile subset and limits.

## Type review

Each S/O/L/I/D cell below is a **pass** with the stated scope and reasoning. A
missing polymorphic extension is identified explicitly; it does not excuse
ownership, exception, equality, or framework contracts. No known finding remains.

| Type | S | O | L | I | D |
| --- | --- | --- | --- | --- | --- |
| `ProtocolError` | Identifies malformed or unsupported fixture input. | Fixed error category; no extensible error hierarchy is needed. | Preserves `ValueError` construction and exception behavior without overrides. | Callers need only ordinary exception handling and bounded messages. | Depends only on the language exception type. |
| `Frame` | Carries one decoded header and owned body. | Profile rules remain in parsers, not the record. | Frozen fields and generated equality work for the immutable byte values constructed by its consumers; decoded bytes outlive caller buffers. | Only four wire components are exposed internally. | No socket, CLI, Java or storage dependency. |
| `FrameReader` | Recognizes bounded complete frames from a byte stream. | Fragmentation/coalescing change inputs, not its algorithm. | Header-first bounds precede full allocation; EOF in a frame fails; each yielded body owns its bytes. No custom subtype obligation exists. | Event loop needs feed/EOF and bounded occupancy observation only. | Uses primitive struct/header validation, independent of sessions or Java codecs. |
| `Budget` | Owns aggregate frame count and byte reservations. | The same object serves multiple connections and either queue class through limits. | Reservation failure leaves occupancy unchanged; releases and peaks are checked by active/drop tests. No subtype contract is invented. | Outbox needs only reserve/release and bounded counters. | Knows neither sockets, frame contents, nor fault names. |
| `Pending` | Records one owner-held physical write, schedule and completion action. | Data fields represent the finite supported fault variants without subclass families. | Internal mutation is confined to the selector owner; immutable frame bytes retain their full reservation until settlement. | Writer/outbox access exactly the scheduling, progress and classification fields they need. | Plain values, with no executor or callback dependencies. |
| `Outbox` | Orders and settles bounded output ownership. | Shared ordinary/control budgets supply configurable capacity independently of fault selection. | FIFO ordinary order, control bypass before activation, no active-frame interleaving, and exactly-once release are tested. | Engine gets admission, ready head, complete, drop and bounded inspection. | Uses `Budget` and `Pending`; contains no socket or protocol encoding. |
| `Config` | Captures the validated finite CLI plan. | Profiles, roles and faults are explicit finite choices. | Frozen values do not normalize profile or late-timeout assumptions after validation. Internal direct construction is not an untrusted public API. | Engine/report consume configuration without parsing argument strings. | Holds only primitive values and the report path. |
| `Connection` | Holds one socket's protocol and physical ownership state. | One state record serves incoming/outgoing TCP origins. | One owner mutates it; one close flag protects settlement; no shared worker or substitutable session implementation is promised. | Engine sees only the fields necessary for bind, messages, framing, writes and termination. | Composes the raw reader and outbox; does not reference Java sessions or request windows. |
| `Engine` | Executes one finite cohort and its selected raw-wire scenario. Parsing, budgets and file reporting have separate collaborators. | Supported variation is centralized in config, message selection and bind/read policy; extending a fault does not alter library code or the wire reader. | Nonblocking sockets, total deadlines, FIFO physical writes, queue failure settlement and all-owner cleanup are exercised with real peers. It exposes no application callback stage or thread-affinity promise beyond its own loop. | CLI supplies one config and one readiness emitter; there are no meaningless service methods for unsupported SMPP operations. | This outer tooling adapter may construct JDK-independent Python sockets; lower wire/budget helpers remain infrastructure-free. |
| `WireTests` | Specifies independent header/framing bytes. | More byte fixtures fit its tables and stream chunk inputs. | Inherits ordinary `unittest.TestCase`; failures remain assertions/exceptions and retain no resources. | Uses only frame encoder/reader contracts. | Expected bytes are literal specification fixtures, not encoder output. |
| `BodyTests` | Specifies the supported message and bind layouts. | Command, profile, role and boundary cases are explicit data. | TestCase behavior is unchanged; immutable fixtures prevent mutations masking failures. | Uses body inspection and acknowledgement construction only. | Expected wire/body constraints are derived independently from primary tables. |
| `QueueTests` | Specifies capacity, ownership and physical ordering. | Shared-budget and partial-write scenarios vary without a transport fake. | Framework contract is unchanged; assertions cover active reservations and exact final release. | Depends only on Budget/Outbox/Pending. | Does not rely on network scheduling to prove queue invariants. |
| `ConfigurationTests` | Specifies admission/report policy and startup/cleanup failure boundaries. | CLI options and focused standard-library mocks express the supported variations. | TestCase cleanup closes real descriptors even when a deliberate failure occurs. Mocks forward all unmodified socket behavior. | Tests only the relevant CLI/report or owned-cleanup entry point per method. | Fresh temporary paths and wrapped real sockets keep expectations independent of other simulators. |
| `ProcessHarness` | Supervises finite subprocess/socket test resources and fresh results. | Both raw-socket and installed-simulator suites reuse the same supervision. | As a TestCase mixin it uses documented assertions/addCleanup; termination has finite terminate/kill waits and logs are closed after the process. | Provides only start, readiness and result helpers; it contains no test scenarios. | Uses `subprocess`, paths and JSON, not private Java APIs. |
| `SocketProcessTests` | Exercises the CLI against controlled independent TCP peers. | Profiles, roles and faults form bounded scenario tables. | Inherits the harness/TestCase contract, explicitly bounds socket operations and drains/cleans every process, including failing cases. | Raw helpers expose complete frame reads and owned subprocess results only. | Fixtures construct their own struct-packed bytes and inspect actual socket observations. |
| `SimulatorProcessTests` | Validates the raw peer through the project's installed public CLI. | Explicit launcher/revision inputs support a new installed artifact without source coupling. | Missing launcher deliberately skips only the opt-in suites; supplied inputs execute all finite pairs with process deadlines and cleanup. | Invokes only public CLI arguments, READY and JSON reports. | No private endpoint/request code or Java dependency is imported; exact external binaries are fingerprinted. |

## Runtime function and method review

All five columns are **pass**, for the specific reason in each cell. Member names
are exhaustive, including constructors and private methods.

| Function/method | S | O | L | I | D |
| --- | --- | --- | --- | --- | --- |
| `encode_pdu` | Encodes one owned frame. | Stable header shape accepts different commands as data. | Validates unsigned header inputs and copies mutable body input before returning bytes. | Callers pass only header fields and body. | Uses struct plus primitive validation. |
| `_header` | Checks numeric header invariants. | Both profiles share this fixed primitive contract. | Rejects request status/nonpermitted sequence without mutating input; generic-nack zero sequence is explicit. | Encoder and reader need no body knowledge. | Language values only. |
| `FrameReader.__init__` | Creates a bounded decoder. | Bound varies by argument. | Rejects bounds below a header and starts empty. | One bound is sufficient. | No socket construction. |
| `FrameReader.feed` | Advances one stream decoder. | Any fragmentation/coalescing uses the same loop. | Rejects bad length at four bytes, owns yielded bodies and never retains multiple full frames. | Exposes a frame iterator rather than a growing history. | Depends only on bytes and header validation. |
| `FrameReader.eof` | Checks whether stream termination is complete. | No profile-specific extension belongs here. | Empty succeeds; partial data fails predictably. | One EOF signal is sufficient. | Uses only reader state. |
| `bind_version` | Parses the supported bind body. | Returns the raw version for outer profile policy. | Bounded ASCII fields, mandatory octets and complete TLV tail are checked before returning. | Caller receives one version, without credentials or account objects. | Uses independent bounded field helpers. |
| `_need` | Checks an exact mandatory byte extent. | Shared by body and TLV parsing. | Throws before an out-of-range field read; does not alter bytes. | Takes only body/offset/size. | Pure arithmetic and length. |
| `_cstring` | Checks bounded ASCII NUL termination. | Width is a caller-supplied field contract. | Returns the next offset; rejects missing terminator/non-ASCII within the field. | No string decoding or text helper API. | Uses raw bytes only. |
| `_tlvs` | Walks a structurally valid optional tail. | Unknown tags do not require registry changes. | Rejects truncation and count above 256; yields offsets instead of retaining value copies. | Consumers select the few fixture tags they understand. | No library TLV registry or vendor dependency. |
| `inspect_message` | Validates supported message layout and counts carried payload bytes. | Explicit command/profile/role parameters select the existing contracts. | Uses data-specific 65 versus short-message 21 address widths, exact short lengths and profile payload precedence; it does not claim full semantic validation. | Returns byte count only; no receipt or delivery service. | Independent wire helpers, not Java codecs. |
| `acknowledge` | Produces the correct positive message response. | Three supported command IDs determine the finite response shape. | Echoes sequence and paired command; deliver has an empty ID, submit/data use `id`. | One decoded request is sufficient. | Calls primitive encoder only. |
| `Budget.__init__` | Establishes one aggregate cap. | Count and byte limits are injected. | Requires positive caps and initializes all occupancy/peak values to zero. | No irrelevant queue or transport parameters. | Pure counters. |
| `Budget.reserve` | Atomically admits one owner-thread reservation. | The same cap applies across multiple outboxes. | Rejects without state change when either limit is exceeded; successful admission updates both peaks. | Size is the only per-frame input. | No contents or fault policy. |
| `Budget.release` | Releases settled occupancy. | Agnostic to success, cancellation or close. | Checks nonnegative extent and existing count; Outbox supplies the full immutable frame size. | No terminal-result callback is required. | Pure counter operation. |
| `Outbox.__init__` | Composes two finite capacity classes. | Budgets can be shared between connections. | Starts with no active/queued writes. | Only the two budgets are required. | Does not construct transport infrastructure. |
| `Outbox.add` | Reserves before enqueuing. | Control classification selects an existing finite budget. | A failed reservation leaves the queue unchanged; accepted bytes stay charged through active writing. | Pending frame and classification are the needed inputs. | Delegates capacity to Budget. |
| `Outbox.head` | Selects ready physical output. | Due time and control class vary without changing ordering rules. | Ordinary FIFO is preserved; an active fragment cannot be interleaved by control bytes. | Writer receives at most one ready head. | Uses owner state and supplied monotonic time. |
| `Outbox.complete` | Settles the active frame. | Independent of response/fault semantics. | Releases the whole active reservation exactly once and clears it. | No application result or socket dependency. | Uses Budget release. |
| `Outbox.drop` | Settles every outstanding frame on closure. | Applies uniformly to active/control/ordinary storage. | Clears queues and reports exact discarded frame/byte/injection counts with no double release. | Owner receives three bounded summary counters. | No protocol interpretation. |
| `Outbox.entries` | Supplies bounded deadline/emptiness inspection. | Covers the existing three storage locations. | Does not remove or duplicate reservations. | Returns an iterator, not a second persistent queue. | Plain owned state. |
| `parse_config` | Admits a finite executable plan. | Roles/profiles/fault choices are a deliberate finite catalogue. | Rejects nonfinite times, invalid bounds, DNS names, wrong origins and invalid late assumptions before sockets/report creation. | CLI arguments produce one config; no connection objects escape. | Uses argparse, IP validation and primitive values. |
| `initialize_report` | Reserves a fresh output location. | Caller chooses its path. | Existing paths fail; no old report is overwritten. | Needs only config.report. | Local filesystem boundary only. |
| `write_report` | Writes bounded nonsecret final JSON. | Schema composes config and observed result independently of transport logic. | Exclusive file creation prevents overwrite; documented file SHA is measured at report time. | Does not inspect arbitrary sockets or credentials. | Uses standard JSON/hash/platform/filesystem functions. |
| `Engine.__init__` | Creates one bounded owner composition. | Config supplies capacities and scenario. | Creates no worker; establishes shared budgets, monotonic origin and fixed counter keys. | Requires config and optional readiness emitter only. | Composes independent reader/output helpers at the outer adapter. |
| `Engine.stop` | Requests cancellation from a signal. | SIGINT/SIGTERM share this small hook. | Sets one owner flag and records one interruption; it does not close concurrently or promise instantaneous cessation of an in-progress loop action. | Standard signal signature is retained. | No executor or external callbacks. |
| `Engine.run` | Owns finite readiness dispatch and final cleanup. | Per-event behavior delegates to focused methods. | Catches supported wire/IO failures, exits on cohort/deadline/cancel and always attempts owned cleanup. | Main receives one final result; no live handles escape. | Standard selector is confined to this adapter. |
| `Engine._start` | Opens one listener or finite outbound cohort. | Literal IP family and role are config choices. | Connect is nonblocking, attempts are bounded, and sockets are owned before option/connect failure can escape. | No DNS, retry or authentication-service surface. | Constructs concrete Python sockets only at the adapter boundary. |
| `Engine._new_connection` | Establishes ownership of a newly created/accepted socket. | Initial protocol state is supplied. | Adds the owner before socket configuration; the failure regression proves descriptor cleanup. | Socket plus initial state are sufficient. | Composes FrameReader/Outbox without library sessions. |
| `Engine._accept` | Admits a listener connection. | Configured cohort cap determines acceptance. | Extra sockets close immediately and are counted; no replenishment loop exists. | No application accept callback or retained waiting queue. | Uses listener and owner creation. |
| `Engine._connected` | Starts one raw ESME bind. | Requested profile and bounded environment credentials are explicit. | Emits one TRX bind with sequence 1 and records the paired response before writing. | Does not expose credentials to report or logs. | Builds bytes through the primitive encoder. |
| `Engine._interest` | Projects owner state onto readiness interest. | Connecting, paused read and due write states share one selector. | Registers/modifies/unregisters consistently; an active fragment's due time is honored. | Selector sees only read/write interests and connection identity. | Knows no message body policy. |
| `Engine._read` | Reads one bounded chunk and advances framing. | Slow-read settings change pacing, not frame parsing. | Pre-bind reads cannot consume post-bind bytes early; total frame deadlines reset only for a new frame, with real coalescing regression. | Passes complete owned frames to dispatch. | Uses socket plus independent FrameReader. |
| `Engine._frame` | Dispatches the supported control/message catalogue. | Profile rejection and paired operation routing are explicit branches. | Bind status/sequence are preserved; mismatched profile produces a header-only negative and a local close reason. | Unsupported operations do not invent service responses. | Delegates body parsing and encoding to raw helpers. |
| `Engine._response` | Correlates pending bind/unbind responses. | Exact expected pair is connection state, not a global history. | Checks command, sequence, success and profile advertisement; unsolicited generic_nack is separately counted, not a fabricated message result. | Needs one frame and connection context. | No request tracker or Java internals. |
| `Engine._bound` | Marks completed bind and selects activation deadlines. | Slow-reader activation is explicit scenario policy. | Counts each accepted transition, begins duration only when the planned cohort has bound and records policy activation separately from received messages. | No application readiness stage. | Uses monotonic time and owner counters. |
| `Engine._message` | Selects a supported request's finite fault action. | Fault-specific branches are localized; all commands reuse independent layout checks. | Enforces bound/origin, per-connection selection, exact injection admission/withholding/close accounting, and no replay claim. | Requires only decoded request and owner state. | Uses Outbox/encoder contracts; no carrier or persistence collaborator. |
| `Engine._queue` | Attempts bounded output ownership. | Classification/delay/fragment fields describe supported write variants. | Reject/closed paths count abandonment; failed admission closes and settles all previously owned writes. | Returns admission success, with no user future execution. | Capacity is delegated to Outbox. |
| `Engine._write` | Performs one nonblocking partial write. | A fragment limit/gap changes chunking only. | Charges full bytes until complete, records actual partial sends, completes injections only after full write, and uses distinct unbind/rejection close causes. | Consumes one active Pending. | Depends on socket.send and Outbox settlement. |
| `Engine._tick` | Advances startup/read/write/drain deadlines. | Config controls finite durations. | No-peer startup fails once; incomplete frames and stalled writes expire; draining sends one unbind then force-closes within budget. | No scheduled task list or timer thread. | Reads monotonic time supplied by the owner. |
| `Engine._close_listener` | Retires listener ownership. | Independent of the selected fault. | Unregisters before close, records close failure and allows remaining connection cleanup; no retry can retain an unbounded owner. | No session result is synthesized. | Uses selector/socket closure only. |
| `Engine._close` | Settles one connection. | Reason is explicit caller context. | Idempotent close releases active and queued reservations, records abandoned injections, clears decoder storage and distinguishes peer/local closure. | Reports counters/reason rather than arbitrary exception objects. | Coordinates socket and Outbox ownership. |
| `Engine._error` | Retains bounded failure diagnostics. | Counter continues when sample storage is full. | Limits details to 16 × 200 characters; no unbounded exception history. | Accepts one nonsecret description. | Plain counters/list. |
| `Engine._sample` | Retains bounded raw-header/event evidence. | Frame or raw16-byte header gives the same schema. | Caps sample count and excludes bodies/credentials. | Consumers need event, connection and header hex only. | Uses primitive struct formatting. |
| `Engine._result` | Reconciles observed scenario and cleanup against plan. | Expected counts distinguish message, terminal and read-policy modes. | Requires exact completed target/no abandoned actions, bound cohort and clean ownership; does not infer peer acceptance or graceful unbind. | Returns bounded serializable observations. | No filesystem write or application callback. |
| `emit` | Flushes the two-line CLI protocol. | Readiness and result share one emitter. | Uses ordinary print behavior without hidden buffering promises. | One line per call. | Standard output only. |
| `main` | Composes CLI admission, signal lifecycle, engine and report. | Optional argument list supports controlled invocation; runtime variants stay in config. | Fresh report admission precedes sockets, signals are restored, exit codes reflect observed result, and final write failure is not reported as success. | Exposes the executable CLI rather than internal service APIs. | All concrete filesystem/socket composition stays at the tooling entry point. |

## Test function and fixture-method review

Each cell is a **pass**. Every test retains ordinary unittest failure semantics;
network tests additionally inherit bounded process cleanup from ProcessHarness.
The literal wire fixtures are independent of production encoding output.

| Function/method | S | O | L | I | D |
| --- | --- | --- | --- | --- | --- |
| `WireTests.test_independent_header_bytes_fragmentation_coalescing_and_ownership` | Specifies exact header bytes and owned decoding. | Bytewise/coalesced inputs vary chunking. | Mutates caller input and checks retained body/equality. | Encoder/reader only. | Literal header expectations. |
| `WireTests.test_header_limits_status_sequence_and_midframe_eof_are_checked` | Specifies malformed envelope rejection. | Boundary tuples add cases without new fixtures. | Checks actual exceptions for short/huge length, status/sequence and partial EOF. | Header/framer contract only. | Independent malformed bytes. |
| `BodyTests.test_data_and_short_message_addresses_use_their_own_wire_bounds` | Specifies 20/21 and 64/65 address boundaries. | Both profiles and all four origin/command pairs are table inputs. | Valid maximum succeeds and next character fails; real regression caught the wrong shared width. | Body inspection only. | Primary 3.4 Table 4-20 and 5.0 Table 4-16 determine data width. |
| `BodyTests.test_bind_and_both_message_layouts_have_independent_response_bytes` | Specifies bind versions, message bodies and ACK bytes. | Covers both profiles and supported directions as data. | Checks payload count and rejects truncation. | Inspection/ACK helpers only. | Responses are fixed hex, not re-encoded expectations. |
| `BodyTests.test_wrong_role_malformed_strings_tlvs_and_34_length_are_rejected` | Specifies structural/profile boundaries. | Cases vary role, encoding, optional extent and length. | Verifies exact fail/pass behavior at 255. | Only bind/body validators are used. | Hand-built malformed input. |
| `QueueTests.test_shared_bounds_include_inflight_and_control_has_separate_capacity` | Specifies aggregate occupancy and release. | Two outboxes share finite budgets. | Active partial writes remain charged; drop totals/final zero are asserted. | No socket interface is required. | Real Budget/Outbox objects, independent sizes. |
| `QueueTests.test_active_fragments_cannot_interleave_control_bytes_or_reorder_ordinary` | Specifies physical write ordering. | Due-time and class differences exercise stable rules. | Exact head identity/order and terminal release are asserted. | Active/head/complete contract only. | Controlled time values avoid network timing assumptions. |
| `ConfigurationTests.test_cli_bounds_profile_role_and_lateness_are_explicit` | Specifies pre-start plan validation. | Invalid option table covers finite policy. | Invalid cases produce argparse exit 2. | No sockets or report writes. | CLI-only inputs. |
| `ConfigurationTests.test_reports_are_fresh_and_include_reproducible_inputs` | Specifies fresh JSON persistence. | Different result/config values compose without Engine. | Exclusive directory/file creation and source identity are checked. | Report helpers only. | Actual temporary filesystem and JSON. |
| `ConfigurationTests.test_failed_socket_configuration_still_closes_the_owned_socket` | Specifies ownership before option failure. | One wrapped socket supplies an actual failing boundary. | Real fileno reaches-1 despite injected setsockopt failure; cleanup is asserted. | Only the failing method is overridden. | Standard Mock forwards all other real socket behavior. |
| `ConfigurationTests.test_listener_close_failure_does_not_skip_other_owned_cleanup` | Specifies all-owner cleanup after listener failure. | A controlled close error selects the exceptional path. | Other real socket and queued bytes settle; report correctly says cleanup failed. | The mock changes only listener.close and bypasses startup already represented by owned resources. | Real descriptors and Outbox remain active; no fake successful cleanup. |
| `raw_frame` | Creates independent fixture bytes. | Arbitrary command/status/sequence/body support negative cases. | Does not reuse production validation or encoding. | Four wire arguments only. | Standard struct implementation. |
| `read_raw` | Reads one complete fixture frame. | Supported body length is data. | Socket timeouts bound blocking calls, lengths cap at 1 MiB and EOF fails; no silent truncation. | Returns the four components needed by assertions. | Independent struct parsing. |
| `stop_process` | Terminates test resources. | Already-complete, terminate and kill paths are explicit. | Waits are bounded and output stream closes after process settlement. | Needs only process and stream. | Standard subprocess lifecycle. |
| `ProcessHarness.setUp` | Creates each scenario's result owner. | Optional external results root retains evidence. | Fresh unique child paths prevent overwrite; default temporary paths register cleanup. | Other helpers see only directory and serial. | Standard temp/path cleanup. |
| `ProcessHarness.start_tool` | Launches one controlled peer process. | CLI options vary scenario. | Registers finite process cleanup, fixes fixture credentials and writes only external fresh paths. | Returns process/log/report tuple. | Runs the actual Python script, not an Engine fake. |
| `ProcessHarness.ready_port` | Waits for concrete listener readiness. | Works for both READY-compatible CLIs. | Has a fixed deadline, detects early exit and includes the bounded process log in failure. | Needs process/log only. | File/monotonic observation. |
| `ProcessHarness.result` | Checks terminal status and report conservation. | Expected exit selects positive/negative scenario. | Requires attempted=completed+abandoned and zero final retained count/bytes for each raw process result. | Reads public JSON only. | No private runtime inspection. |
| `SocketProcessTests.test_profile_rejection_has_header_only_reply_and_a_local_close_reason` | Specifies mismatched-profile wire/error accounting. | Both configured/requested version reversals are exercised. | Verifies status 13/header-only response, EOF and local bind_rejected cause. | Raw bind and final counters only. | Independent request/response tuple. |
| `SocketProcessTests.test_connection_cohort_and_shared_retention_are_bounded` | Specifies finite real multi-connection ownership. | Two accepted connections and one excess connection exercise the cap. | Exact late injection/retention totals and excess EOF are asserted. | Public sockets and reports only. | No simulated selector or copied queue result. |
| `SocketProcessTests.test_no_peer_expires_once_at_the_startup_deadline` | Specifies finite no-peer startup. | A short explicit timeout selects the boundary. | Elapsed report must remain below a generous bound and error count must be one. | CLI/report only. | Real process with no connecting peer. |
| `SocketProcessTests.test_server_binds_acknowledges_controls_and_closes_for_both_profiles` | Specifies raw MC positive flow. | Both profiles use independent version octets. | Checks bind/submit/enquiry/unbind sequence/status/body and observed EOF. | Only supported public wire commands. | Controlled real ESME socket. |
| `SocketProcessTests.test_client_connects_once_and_acknowledges_deliveries_for_both_profiles` | Specifies raw ESME positive flow. | Two profiles share a controlled MC fixture. | Checks one TRX bind, exact delivery ACK and clean unbind. | No server library dependency. | Independent listener and bytes. |
| `SocketProcessTests.test_duplicate_late_and_fragmented_acknowledgements_are_exactly_accounted` | Specifies nonterminal physical injections. | Three fault variants use the same bounded flow. | Duplicate bytes, late lower-bound timing, control bypass, fragment count and exact settlement are asserted. | Uses wire/report observations only. | Real sockets, independent ACK expectations. |
| `SocketProcessTests.test_missing_ack_still_services_controls_and_retains_no_message` | Specifies deliberate withholding. | Control command is independent of selected message action. | ACK times out while enquiry/unbind work and ordinary retention remains zero. | No response-window emulation. | Real finite read timeout. |
| `SocketProcessTests.test_terminal_faults_act_after_the_configured_message_count` | Specifies disconnect/malformed threshold. | Both terminal modes use count 2. | First request gets normal ACK; second gets exact invalid header or EOF; counters match. | Header and close observation only. | Literal length 15 header. |
| `SocketProcessTests.test_slow_reader_paces_post_bind_bytes_without_a_worker_queue` | Specifies read pacing. | Explicit small chunks/intervals select observable delay. | Initial read timeout precedes eventual exact response and activation is counted separately. | Does not infer kernel backpressure. | Real TCP timing with bounded waits. |
| `SocketProcessTests.test_exhausted_count_or_byte_capacity_releases_every_attempted_fault` | Specifies rejected admission settlement. | Count and byte caps independently exhaust. | Two attempts become two abandoned injections, one rejection, bounded peak and final zero. | Public wire/report only. | Real batched requests trigger the capacity path. |
| `SocketProcessTests.test_incomplete_frames_expire_or_fail_at_eof_with_bounded_cleanup` | Specifies partial-read termination. | EOF versus deadline selects two actual failures. | Distinct error counters and closed socket are asserted. | No internal clock override. | Controlled real half-close and stalled input. |
| `SocketProcessTests.test_inflight_fragment_deadline_keeps_bytes_reserved_until_close` | Specifies physical write expiry. | Tiny delayed fragments exceed explicit timeout. | Observes some but fewer than 19 bytes; full reservation peaks and abandonment are checked. | No aggregate load claim. | Actual socket bytes and final JSON. |
| `SocketProcessTests.test_connect_refusal_and_wrong_bind_status_or_sequence_are_reported` | Specifies startup/correlation failures. | Refused port, wrong sequence and negative status are separate cases. | Each terminates and reports cleanup; no repeated connect is inferred. | Only raw bind/control surface. | Real refusal/listener fixtures. |
| `SocketProcessTests.test_signal_shutdown_settles_queued_faults_and_writes_a_final_report` | Specifies requested process termination. | SIGTERM selects the same documented owner-stop path. | Ensures a delayed frame exists, then checks EOF, one abandoned injection and a final report. | Uses public process signal and JSON. | No direct Engine.stop invocation substitutes for the signal. |
| `SocketProcessTests.test_a_new_partial_frame_gets_its_own_total_read_deadline` | Specifies coalesced frame deadline ownership. | Old remainder and new prefix share a receive opportunity. | Second frame succeeds after old deadline but before its own, proving the regression fix. | Public socket behavior only. | Independent two-frame timing fixture. |
| `SimulatorProcessTests.start_simulator` | Launches/fingerprints one external target. | Explicit launcher/revision inputs select the artifact. | Finite CLI bounds, fresh paths, fixed credentials and cleanup are registered before assertions. | Public CLI only. | Records launcher/JAR hashes and does not import target code. |
| `SimulatorProcessTests.exercise_pair` | Checks one concrete fault pair end to end. | Role/profile/operation/fault are data. | Bounded waits verify exact injection completion, target terminal results, no pending work and physical cleanup. | Uses READY/reports rather than private endpoint fields. | Independent peer process against installed project CLI. |
| `SimulatorProcessTests.test_installed_simulator_all_faults_in_both_roles_and_profiles` | Covers submit/deliver matrix. | Two profiles×two roles×seven faults are explicit finite loops. | Every subtest delegates the same process/cleanup contract; failures are not converted to skips. | Only supported originating operations. | Public executable seam. |
| `SimulatorProcessTests.test_installed_simulator_data_in_both_roles_and_profiles` | Covers data matrix independently. | Both origins/profiles exercise all seven faults. | Same exact settlement/cleanup assertions apply to every pair. | No short-message-only helper is required. | Public executable plus independent data layout. |

The temporary sockets, paths, byte arrays and subprocesses are standard-library
objects whose ownership is covered in their creating tests. The two Mock-based
failure fixtures override only the specified failing socket operation; the real
descriptor, close and queue settlement behavior remains exercised. No additional
project-defined fixture type is hidden behind those mocks.

## TDD and verification evidence

Every listed red was an actual behavioral run before its corresponding change.
Logs are retained at `/tmp/step18-fault-<name>.log`. The log wrapper records the
exact Python command, working directory, `PYTHONPYCACHEPREFIX` and exit status.
Commands below ran in `/tmp/lightweight-smpp-step18-fault-peer`.

| Cycle / log names | Actual relevant red | Green result |
| --- | --- | --- |
| `01-wire-red` / `01-wire-green` | Encoder returned empty bytes and reader did not reject oversized length. | Two independent framing methods passed. |
| `02-body-red` / `02-body-green` | Bind parser returned the wrong version and malformed body was accepted. | Four methods passed with both layouts/profiles. |
| `03-queue-red` / `03-queue-green` | Admission exceeded capacity and an active write was not exposed as expected. | Six methods passed, including shared count/byte and active-order contracts. |
| `04-config-report-red` / `04-config-report-green` | Config returned no plan and report file was not produced. | Eight methods passed with validation and exclusive report creation. |
| `05-socket-cli-red` / `05-socket-cli-green` | Raw server never bound/printed READY; raw client never connected to the controlled listener. | Ten methods passed, with both modes/profiles using actual processes/sockets. |
| `06-faults-red` / `06-faults-green` | Seven fault-specific assertions failed: absent duplicate, early late ACK, missing fragments, unwanted missing ACK, no read pause, absent disconnect and length 19 instead of 15. | Four new scenario methods passed; 14 total. |
| `07-cleanup-red` / `07-cleanup-green` | Option failure left a real descriptor open; no-peer run lasted 0.753 s despite 0.05 s startup timeout. | Both regressions passed; 16 total. |
| `08-signal-red` / `08-signal-green` | SIGTERM killed the process with -15 before a final report or settlement. | Signal regression and added bounded failure characterizations passed; 21 total. |
| `09-frame-deadline-red` / `09-frame-deadline-green` | Second partial frame was closed at the first frame's deadline. | Actual coalesced two-frame regression passed; 22 total. |
| `13-close-failure-red` / `13-close-failure-green` | Listener.close exception escaped before other owned cleanup. | Other descriptor/queue settled and cleanup failure remained visible;23 executed methods plus two opt-in skips. |
| `17-address-bound-red` / `17-address-bound-green` | Four data 64-character cases failed under the incorrect 21-octet shared address limit. | Data 64/65 and short 20/21 boundaries passed in both profiles/directions. |
| `19-close-reason-red` / `19-close-reason-green` | Both negative bind profiles reported `peer_closes=1` / `local_closes=0`. | Header-only rejection and `bind_rejected` local cause passed; 26 executed methods plus two opt-in skips. |

Common command:

```sh
PYTHONPYCACHEPREFIX=/tmp/lightweight-smpp-fault-pycache \
  python3 -m unittest discover -s simulator/scripts/tests -v
```

Focused commands used the same discovery command with `-k signal_shutdown`,
`-k new_partial_frame`, `-k listener_close_failure`, `-k own_wire_bounds`, or
`-k profile_rejection`, exactly as recorded in their logs. No syntax, compiler,
dependency-installation or setup failure is counted as a TDD red.

`08-failure-characterization` added partial-frame checks already supported by the
implementation. `10-harness-refactor-green` verified the shared process harness
after the previously green suite. `14-cohort-characterization` exercised two real
connections, excess rejection and their shared late-response storage without a
behavior change. Later test-only matrix expansion covered all data fault modes.

The first installed-target attempt, `11-installed-characterization`, failed at
JVM startup because the fixture supplied `-Xmx192m` below the launcher's default
initial heap. This was a test-environment error, not a protocol red. Supplying
`JAVA_OPTS='-Xms64m -Xmx192m'` produced four passing data/profile/origin pairs in
`11-installed-data`. `12-installed-fault-matrix` then passed 28 submit/deliver pairs.
`16-final-installed` and `18-corrected-final` each passed a fresh 56-pair matrix;
the latter used the corrected data-address bound. Those runs are historical
evidence, with their exact source/JAR fingerprints retained separately.

Formatting used Black 25.1.0 in `/tmp/lightweight-smpp-fault-format`, outside the
project and outside runtime dependencies. The initial local venv bootstrap lacked
ensurepip; an external PyPA bootstrap supplied pip for that formatting environment.
This setup failure is not test evidence. Final formatting ran separately in
`20-final-format`; inventory and hashes were computed afterward. Python bytecode
and package caches were preserved. No Gradle build or cache setting was modified,
and no Java test result is claimed by this Python-only change.

## Final external matrix and inventory reconciliation

`21-delivery-final` executed freshly after final formatting and passed **28 test
methods, zero skips**, in 84.067 seconds. This includes 26 independent/unit/socket
methods and two opt-in methods expanding into **56 installed process pairs**.
The final command was:

```sh
SMPP_SIMULATOR_LAUNCHER=/home/aidarbek/Documents/Projects/lightweight-smpp/simulator/build/install/simulator/bin/simulator \
SMPP_SIMULATOR_REVISION=eee514218ee08eee0481d1edb8d8395d0a2b1b6e \
SMPP_FAULT_RESULTS=/tmp/step18-fault-delivery-results \
PYTHONPYCACHEPREFIX=/tmp/lightweight-smpp-fault-pycache \
  python3 -m unittest discover -s simulator/scripts/tests -v
```

The actual wrapper invocation and complete output are in
`/tmp/step18-fault-21-delivery-final.log`. Fresh per-case reports, target commands
and logs are retained under `/tmp/step18-fault-delivery-results`; the two external
matrix directories are identified by the 56 `PAIR` records in that log. Summary
reconciliation is retained in `/tmp/step18-fault-final-summary.json`.

The external launcher was supplied by the coordinating task. Its revision string
was `eee5142`, **with the final Step17 library built from the coordinator's working
tree**. The executable simulator itself was the earlier installed CLI, preceding
the later Step18 candidate. The exact artifact hashes, identical across all 56
pairs, establish this boundary; the revision string alone does not reproduce it:

| Installed input | SHA-256 |
| --- | --- |
| `bin/simulator` | `e3b7177b2648749505df1a92bb99d76a8de6d695cd32adc79192a56c8ba08750` |
| `lightweight-smpp-1.0-SNAPSHOT.jar` | `d7ed9830384a5acdf13475f4dba6e61c3ef061dd117134527a2a816ae98c89ea` |
| `simulator-1.0-SNAPSHOT.jar` | `47477cbd664c8fa3cffb7dbc4079294699bc0162a06a0aa018eace9c65b40dd8` |
| `HdrHistogram-2.2.2.jar` (existing simulator dependency) | `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8` |

Every pair planned four target attempts and one raw injection, using one TRX
connection, 16-byte binary payload, eight arrivals/second, 0.8-second measurement,
0.12-second target request timeout and 0.5-second drain. These small functional
runs make no capacity, throughput target, pinning, or handset-delivery claim.
The recorded environment was Python 3.12.3, Linux 7.0.0-31-generic/amd64,
glibc 2.39 and Ubuntu OpenJDK 21.0.12+8-1-24.04-Ubuntu. The target reported
12 logical processors and an effective 192 MiB maximum heap; the fixture explicitly
overrode the launcher's initial heap with 64 MiB as recorded in its environment.

| Fault | Pairs | Target success | Target timeout | Admitted local failure | Later local rejection | Completed raw injections |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Duplicate | 8 | 32 | 0 | 0 | 0 | 8 |
| Late | 8 | 16 | 16 | 0 | 0 | 8 |
| Missing | 8 | 24 | 8 | 0 | 0 | 8 |
| Malformed | 8 | 0 | 0 | 8 | 24 | 8 |
| Fragmented | 8 | 32 | 0 | 0 | 0 | 8 |
| Disconnect | 8 | 0 | 0 | 8 | 24 | 8 |
| Slow reader | 8 | 16 | 16 | 0 | 0 | 8 |

All 56 raw and target reports passed their explicitly configured functional
criteria, reported cleanup complete, and ended with zero pending target requests
and zero held raw frames/bytes. There were 56 attempted/completed raw injections
and zero abandoned injections. The raw ordinary retention peak across these pairs
was three frames / 57 bytes; control peak was one frame / 32 bytes. The independent
two-connection test separately reaches exactly two frames / 38 bytes under a shared
cap and rejects a third connection. Fault success here uses the documented local
completion meanings; missing and slow-reader counts do not themselves prove a
target timeout or blocked kernel write. The target outcomes in the table are
separate actual observations from its reports.

Final AST/source reconciliation found all 16 type and 83 function/method identities
in the corresponding review tables and verified both current whole-file hashes.
All relative links in the guide/review resolve. The inventory is retained in
`/tmp/step18-fault-python-inventory.json`. Its traversal is an external validation
command, not another project source or hidden fixture. No source change follows
the final hashes above. The four-file handoff contains no generated logs, reports,
bytecode, formatter environment, Java changes, or build/dependency changes.

## Fresh candidate 2 replay: unchanged assertions and corrected artifacts

This is additional execution evidence; it changes no Python or Java source,
adds no type/function identity, and leaves all prior review hashes current.
The coordinator supplied the immutable `0.1.0-rc.1` candidate at
`/tmp/lightweight-smpp-candidate2`, declared as `387aed9ef523c85fb3cfa0f8f245438759deb6fc+fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c`.
The original reviewed raw runtime SHA-256 remains
`96bd4be6d122077df1ac9e3de35445bb03e8238f004aa16396f0afe2e9c28e43`;
the original test source remains
`43c4ba86e6359f387ab19861bbb93b4cd447424f8e7e7e4c1ac3f22f665aabc1`.
The harness was replayed without changing assertions or deadlines:

```sh
SMPP_SIMULATOR_LAUNCHER=/tmp/lightweight-smpp-candidate2/simulator/build/install/simulator/bin/simulator \
SMPP_SIMULATOR_REVISION=387aed9ef523c85fb3cfa0f8f245438759deb6fc+fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c \
SMPP_FAULT_RESULTS=/home/aidarbek/Documents/Projects/lightweight-smpp/build/runs/step19-corrected-wire-checks/raw \
PYTHONPYCACHEPREFIX=/tmp/lightweight-smpp-fault-pycache \
  python3 -m unittest discover -s simulator/scripts/tests \
  -p test_fault_peer.py -k SimulatorProcessTests -v
```

The command ran in `/tmp/lightweight-smpp-step18-fault-peer`. Both selected
methods passed, with zero skips, in **87.506 seconds**, expanding into **56
fresh pairs**. The independent/unit/socket suite was not rerun for this
measurement-only task; its earlier evidence remains separate. Each pair used
its original four-attempt, one-injection policy and startup/read/write/drain
bounds. Completed role processes were cleaned up by the original bounded
harness; any timeout/assertion failure would have remained in the same output
folder. No failure was discarded, retried or relabeled.

| Executing candidate input | SHA-256 |
| --- | --- |
| `bin/simulator` | `03fbbcfad916ff2b1a59b3a0e604b902bdeb9a1b195a6dfbaa66ea8d5c71abf5` |
| `lightweight-smpp-0.1.0-rc.1.jar` | `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` |
| `simulator-0.1.0-rc.1.jar` | `47705647225df458d2f4fc8cb18c12a37198d82b72c25c8fbace72d308649161` |
| Existing `HdrHistogram-2.2.2.jar` | `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8` |

| Fault | Pairs | Target success | Target timeout | Admitted local failure | Later local rejection | Completed raw injections |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| duplicate | 8 | 32 | 0 | 0 | 0 | 8 |
| late | 8 | 16 | 16 | 0 | 0 | 8 |
| missing | 8 | 24 | 8 | 0 | 0 | 8 |
| malformed | 8 | 0 | 0 | 8 | 24 | 8 |
| fragmented | 8 | 32 | 0 | 0 | 0 | 8 |
| disconnect | 8 | 0 | 0 | 8 | 24 | 8 |
| slow-reader | 8 | 17 | 15 | 0 | 0 | 8 |

All 56 target reports passed their configured functional criteria and reported
complete cleanup with zero pending requests. All raw reports ended with zero
held frames/bytes and no abandoned injection. Ordinary retention peaked at
three frames / 57 bytes, and control retention at one frame / 32 bytes. The
slow-reader timing split differs from the earlier artifact run (17 successes
and 15 timeouts here); these are actual counts, not normalized expected values.
No new assertion or strict performance criterion was introduced.

The target reported Ubuntu OpenJDK 21.0.12+8-1-24.04-Ubuntu, Linux
7.0.0-31-generic/amd64, 12 logical processors and an effective 192 MiB maximum
heap. The original `JAVA_OPTS=-Xms64m -Xmx192m` override is retained in every
input record. Python remained 3.12.3 and its bytecode cache was preserved.
The supervisors could overlap the coordinator's other campaigns; no throughput
comparison is drawn from these short functional runs.

Evidence resides in `build/runs/step19-corrected-wire-checks`: `raw-command.json` has the exact command,
environment, UTC execution bounds and exit zero; `raw-matrix.log` retains all
56 PAIR records and original unittest assertions; `raw/` contains all 56 target
reports, 56 raw reports, exact input hashes and process logs. `inputs-before.json`
and `inputs-after.json` prove the installed candidate files did not change.
`summary.json` reconciles these records and the separate 12 receipt pairs,
with SHA-256 `321e3d037f51ae0e60ac0ca813f27193a025b463c7da80dd978692fd3496d366`.
Raw log SHA-256: `34e050d91acbb5d52e88a7e0d321cecaf916dc3a4276364c8e321bc27bafd469`.
The complete source declaration and copied coordinator source/artifact manifests
are retained alongside the results. Only these guide/review additions belong
to the documentation handoff; candidate/root Java and root documentation were
not edited by this agent.

## Final candidate 3 verification: original functional assertions

This append changes no project-owned Python/Java source or tests, adds no
reviewed identity and leaves the complete source reviews above current. The raw
runtime remains SHA-256
`96bd4be6d122077df1ac9e3de35445bb03e8238f004aa16396f0afe2e9c28e43`;
the original test remains
`43c4ba86e6359f387ab19861bbb93b4cd447424f8e7e7e4c1ac3f22f665aabc1`.
No Gradle build, recompile, cache removal, assertion change, timeout adjustment,
retry or discarded failure was used for this final replay. The original opt-in
command ran in `/tmp/lightweight-smpp-step18-fault-peer` with the candidate3
installed launcher and declared source `387aed9ef523c85fb3cfa0f8f245438759deb6fc+2d63cd99c32ca3b197cbc973a100c9280ef00a05d4c5311b0cb7c011bb823a00`:

```sh
SMPP_SIMULATOR_LAUNCHER=/tmp/lightweight-smpp-candidate3/simulator/build/install/simulator/bin/simulator \
SMPP_SIMULATOR_REVISION=387aed9ef523c85fb3cfa0f8f245438759deb6fc+2d63cd99c32ca3b197cbc973a100c9280ef00a05d4c5311b0cb7c011bb823a00 \
SMPP_FAULT_RESULTS=/home/aidarbek/Documents/Projects/lightweight-smpp/build/runs/step19-final-wire-checks/raw \
PYTHONPYCACHEPREFIX=/tmp/lightweight-smpp-fault-pycache \
  python3 -m unittest discover -s simulator/scripts/tests \
  -p test_fault_peer.py -k SimulatorProcessTests -v
```

Both methods passed in **113.172 seconds**, expanding into **56 fresh pairs**.
The independent unit/socket suite was not rerun for this measurement-only task;
its earlier behavioral TDD evidence remains separate. Every pair retained four
planned attempts, one deliberate injection and the original startup/read/write/
drain budgets. All target reports passed their configured expected-fault criteria
and recorded complete cleanup and zero pending requests. All raw reports closed
owned sockets with zero held frames/bytes and zero abandoned injection.

| Final installed input | SHA-256 |
| --- | --- |
| `bin/simulator` | `03fbbcfad916ff2b1a59b3a0e604b902bdeb9a1b195a6dfbaa66ea8d5c71abf5` |
| `lib/lightweight-smpp-0.1.0-rc.1.jar` | `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` |
| `lib/simulator-0.1.0-rc.1.jar` | `a59d553d3ec84de5d953a927696565f2c498384386a241a74a113426011d1d9e` |
| `lib/HdrHistogram-2.2.2.jar` | `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8` |

| Fault | Pairs | Target success | Target timeout | Admitted local failure | Later local rejection | Completed injections |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| duplicate | 8 | 32 | 0 | 0 | 0 | 8 |
| late | 8 | 16 | 16 | 0 | 0 | 8 |
| missing | 8 | 24 | 8 | 0 | 0 | 8 |
| malformed | 8 | 0 | 0 | 8 | 24 | 8 |
| fragmented | 8 | 32 | 0 | 0 | 0 | 8 |
| disconnect | 8 | 0 | 0 | 8 | 24 | 8 |
| slow-reader | 8 | 16 | 16 | 0 | 0 | 8 |

These are actual observed counts, including the timing-dependent slow-reader
split; they are not normalized to earlier runs. Ordinary raw retention peaked
at 3 frames / 57 bytes,
and reserved control retention at 1 frame /
32 bytes. The target JVMs used the original
`JAVA_OPTS=-Xms64m -Xmx192m`, Ubuntu OpenJDK 21.0.12, Linux amd64 on the shared
12-CPU host. Python 3.12.3 and its bytecode cache were retained. The supervisor
waited for the old heavy campaigns to end before executing these unchanged
short startup checks; four final-soak JVMs and one concluding target campaign
remained. No throughput/backpressure limit is inferred.

`build/runs/step19-final-wire-checks/raw-command.json` records the exact command/environment, UTC bounds and
exit 0. `raw-matrix.log` retains all 56 original PAIR observations and unittest
outcome, SHA-256 `3d07074fe9c6fbc99805f0997d8e8e70545d306183ea5f814ab9323c2bc170b2`. The raw subtree
contains 56 raw reports, 56 target reports, exact executing input hashes and
process logs. Shared before/after installed-file verification passed;
`summary.json` SHA-256 `cd48951751a55b297d65715d52904039fe466bf08708a4bfb61e9e44624022cc` reconciles these with the separate receipt matrix.
All 136 role processes across the matrices were bounded and cleaned up (80 Java,
56 Python). Only guide/review text is handed off; no candidate/root Java or root
documentation was edited by this agent.
