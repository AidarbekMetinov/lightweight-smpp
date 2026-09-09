#!/usr/bin/env python3
"""Finite raw SMPP fault peer; no project or third-party protocol dependency."""

import argparse
from collections import deque
from dataclasses import asdict, dataclass
import errno
import hashlib
import ipaddress
import json
import math
import os
from pathlib import Path
import platform
import selectors
import signal
import socket
import struct
import sys
import time


class ProtocolError(ValueError):
    """Malformed wire data or unsupported peer operation."""


@dataclass(frozen=True)
class Frame:
    command: int
    status: int
    sequence: int
    body: bytes


def encode_pdu(command, status, sequence, body=b""):
    """Encode an owned PDU with unsigned header values."""
    _header(command, status, sequence)
    body = bytes(body)
    return struct.pack("!IIII", 16 + len(body), command, status, sequence) + body


def _header(command, status, sequence):
    if not all(
        isinstance(value, int) and 0 <= value <= 0xFFFFFFFF
        for value in (command, status, sequence)
    ):
        raise ProtocolError("Header values must be unsigned 32-bit integers")
    if command < 0x80000000 and status != 0:
        raise ProtocolError("Request status must be zero")
    if not 1 <= sequence <= 0x7FFFFFFF and not (
        command == 0x80000000 and sequence == 0
    ):
        raise ProtocolError("Invalid SMPP sequence")


class FrameReader:
    """Incremental bounded header-first frame reader."""

    def __init__(self, maximum):
        if not 16 <= maximum <= 0xFFFFFFFF:
            raise ValueError("Frame bound must include the 16-byte header")
        self.maximum = maximum
        self.buffer = bytearray()
        self.peak = 0

    def feed(self, data):
        offset = 0
        while offset < len(data):
            size = (
                4 if len(self.buffer) < 4 else struct.unpack_from("!I", self.buffer)[0]
            )
            if len(self.buffer) >= 4 and not 16 <= size <= self.maximum:
                raise ProtocolError("Declared frame length exceeds bounds")
            take = min(size - len(self.buffer), len(data) - offset)
            self.buffer.extend(data[offset : offset + take])
            offset += take
            self.peak = max(self.peak, len(self.buffer))
            if len(self.buffer) == 4:
                size = struct.unpack_from("!I", self.buffer)[0]
                if not 16 <= size <= self.maximum:
                    raise ProtocolError("Declared frame length exceeds bounds")
            if len(self.buffer) == size and size >= 16:
                _, command, status, sequence = struct.unpack_from("!IIII", self.buffer)
                _header(command, status, sequence)
                body = bytes(self.buffer[16:])
                self.buffer.clear()
                yield Frame(command, status, sequence, body)

    def eof(self):
        if self.buffer:
            raise ProtocolError("EOF inside a frame")


def bind_version(body):
    """Validate the supported bind body and return the raw interface version."""
    offset = 0
    for width in (16, 9, 13):
        offset = _cstring(body, offset, width)
    _need(body, offset, 3)
    version = body[offset]
    offset = _cstring(body, offset + 3, 41)
    list(_tlvs(body, offset))
    return version


def _need(body, offset, size):
    if offset + size > len(body):
        raise ProtocolError("Truncated standard field")


def _cstring(body, offset, width):
    end = body.find(b"\0", offset, offset + width)
    if end < 0 or any(value > 127 for value in body[offset:end]):
        raise ProtocolError("Malformed bounded ASCII C-octet string")
    return end + 1


def _tlvs(body, offset):
    count = 0
    while offset < len(body):
        _need(body, offset, 4)
        tag, length = struct.unpack_from("!HH", body, offset)
        offset += 4
        _need(body, offset, length)
        count += 1
        if count > 256:
            raise ProtocolError("More than 256 TLVs in supported fixture")
        yield tag, offset, length
        offset += length


def inspect_message(frame, role, version):
    """Validate supported message layouts and return carried payload octets."""
    _header(frame.command, frame.status, frame.sequence)
    allowed = (4, 0x103) if role == "server" else (5, 0x103)
    if frame.command not in allowed or version not in (0x34, 0x50):
        raise ProtocolError("Unsupported message origin or profile")
    body = frame.body
    offset = _cstring(body, 0, 6)
    for _ in range(2):
        _need(body, offset, 2)
        offset = _cstring(body, offset + 2, 65 if frame.command == 0x103 else 21)
    _need(body, offset, 3)
    offset += 3
    payload_length = 0
    if frame.command != 0x103:
        offset = _cstring(body, offset, 17)
        offset = _cstring(body, offset, 17)
        _need(body, offset, 5)
        payload_length = body[offset + 4]
        offset += 5
        if version == 0x34 and payload_length == 255:
            raise ProtocolError("SMPP 3.4 short_message exceeds 254 octets")
        _need(body, offset, payload_length)
        offset += payload_length
    payload_seen = False
    for tag, _, length in _tlvs(body, offset):
        if tag == 0x0424:
            if payload_seen or (version == 0x34 and payload_length):
                raise ProtocolError("Ambiguous message payload")
            payload_seen = True
            payload_length = length
    return payload_length


def acknowledge(frame):
    """Build the exact positive response for a supported incoming message."""
    if frame.command not in (4, 5, 0x103):
        raise ProtocolError("Unsupported acknowledgement")
    return encode_pdu(
        frame.command | 0x80000000,
        0,
        frame.sequence,
        b"\0" if frame.command == 5 else b"id\0",
    )


class Budget:
    """Shared count/byte reservation including partially written frames."""

    def __init__(self, count, size):
        if count < 1 or size < 1:
            raise ValueError("Budget limits must be positive")
        self.maximum_count, self.maximum_bytes = count, size
        self.count = self.size = self.peak_count = self.peak_bytes = 0

    def reserve(self, size):
        if size < 0:
            raise ValueError("Negative reservation")
        if self.count == self.maximum_count or self.size + size > self.maximum_bytes:
            return False
        self.count += 1
        self.size += size
        self.peak_count = max(self.peak_count, self.count)
        self.peak_bytes = max(self.peak_bytes, self.size)
        return True

    def release(self, size):
        if self.count < 1 or not 0 <= size <= self.size:
            raise RuntimeError("Invalid reservation release")
        self.count -= 1
        self.size -= size


@dataclass
class Pending:
    data: bytes
    due: float
    deadline: float
    injection: bool = False
    fragment: int = 0
    action: str = ""
    position: int = 0
    control: bool = False


class Outbox:
    """FIFO ordinary writes and separately reserved control writes."""

    def __init__(self, ordinary, control):
        self.ordinary_budget, self.control_budget = ordinary, control
        self.ordinary, self.controls = deque(), deque()
        self.active = None

    def add(self, pending, control=False):
        budget = self.control_budget if control else self.ordinary_budget
        if not budget.reserve(len(pending.data)):
            return False
        pending.control = control
        (self.controls if control else self.ordinary).append(pending)
        return True

    def head(self, now):
        if self.active is None:
            queue = self.controls if self.controls else self.ordinary
            if queue and queue[0].due <= now:
                self.active = queue.popleft()
        return self.active if self.active and self.active.due <= now else None

    def complete(self):
        pending, self.active = self.active, None
        if pending is None:
            raise RuntimeError("No active write to complete")
        budget = self.control_budget if pending.control else self.ordinary_budget
        budget.release(len(pending.data))

    def drop(self):
        entries = list(self.controls) + list(self.ordinary)
        if self.active:
            entries.append(self.active)
        self.controls.clear()
        self.ordinary.clear()
        self.active = None
        for pending in entries:
            budget = self.control_budget if pending.control else self.ordinary_budget
            budget.release(len(pending.data))
        return (
            len(entries),
            sum(len(item.data) for item in entries),
            sum(item.injection for item in entries),
        )

    def entries(self):
        """Iterate only currently reserved writes, including the active one."""
        if self.active:
            yield self.active
        yield from self.controls
        yield from self.ordinary


@dataclass(frozen=True)
class Config:
    role: str
    host: str
    port: int
    version: str
    operation: str
    fault: str
    duration: float
    timeout: float
    drain: float
    connections: int
    count: int
    max_frame: int
    held_count: int
    held_bytes: int
    delay: float
    peer_timeout: float
    fragment_bytes: int
    fragment_gap: float
    read_bytes: int
    read_interval: float
    sample_count: int
    report: Path


def parse_config(arguments):
    """Parse and validate finite inputs before allocating network resources."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("role", choices=("server", "client"))
    parser.add_argument(
        "--host", default="127.0.0.1", help="literal IPv4/IPv6 address; no DNS"
    )
    parser.add_argument("--port", type=int, default=2775)
    parser.add_argument("--version", choices=("3.4", "5.0"), default="3.4")
    parser.add_argument("--operation", choices=("submit", "deliver", "data"))
    parser.add_argument(
        "--fault",
        default="none",
        choices=(
            "none",
            "duplicate",
            "late",
            "missing",
            "malformed",
            "fragmented",
            "disconnect",
            "slow-reader",
        ),
    )
    for name, default in (
        ("duration", 2.0),
        ("timeout", 2.0),
        ("drain", 0.5),
        ("delay", 0.2),
        ("peer-timeout", 0.1),
        ("fragment-gap", 0.005),
        ("read-interval", 0.05),
    ):
        parser.add_argument("--" + name, type=float, default=default)
    for name, default in (
        ("connections", 1),
        ("count", 1),
        ("max-frame", 65536),
        ("held-count", 64),
        ("held-bytes", 1048576),
        ("fragment-bytes", 3),
        ("read-bytes", 16),
        ("sample-count", 32),
    ):
        parser.add_argument("--" + name, type=int, default=default)
    parser.add_argument(
        "--report", type=Path, required=True, help="fresh report directory"
    )
    values = vars(parser.parse_args(arguments))
    values["operation"] = values["operation"] or (
        "submit" if values["role"] == "server" else "deliver"
    )
    config = Config(**values)
    try:
        ipaddress.ip_address(config.host)
        if not (0 if config.role == "server" else 1) <= config.port <= 65535:
            raise ValueError("Invalid port")
        if (config.role == "server" and config.operation == "deliver") or (
            config.role == "client" and config.operation == "submit"
        ):
            raise ValueError("Operation has the wrong SMPP origin")
        for name in ("duration", "timeout", "delay", "peer_timeout", "read_interval"):
            if not math.isfinite(values[name]) or not 0.001 <= values[name] <= 3600:
                raise ValueError("Finite positive duration required: " + name)
        for name in ("drain", "fragment_gap"):
            if not math.isfinite(values[name]) or not 0 <= values[name] <= 60:
                raise ValueError("Invalid bounded duration: " + name)
        for name, minimum, maximum in (
            ("connections", 1, 64),
            ("count", 1, 1000000),
            ("max_frame", 16, 1048576),
            ("held_count", 1, 4096),
            ("held_bytes", 16, 67108864),
            ("fragment_bytes", 1, config.max_frame),
            ("read_bytes", 1, config.max_frame),
            ("sample_count", 0, 256),
        ):
            if not minimum <= values[name] <= maximum:
                raise ValueError("Invalid resource bound: " + name)
        if config.fault == "late" and config.delay <= config.peer_timeout:
            raise ValueError(
                "Late delay must exceed the explicitly assumed peer timeout"
            )
    except ValueError as error:
        parser.error(str(error))
    return config


def initialize_report(config):
    """Reserve a fresh report directory before opening sockets."""
    config.report.mkdir(parents=True, exist_ok=False)


def write_report(config, result):
    """Write bounded non-secret JSON with exact source and environment identity."""
    report = {
        "schema": "smpp-raw-fault-peer-v1",
        "config": asdict(config),
        "sourceSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "python": platform.python_version(),
        "platform": platform.platform(),
        "result": result,
    }
    with (config.report / "report.json").open("x", encoding="utf-8") as output:
        json.dump(report, output, default=str, sort_keys=True, indent=2)
        output.write("\n")


@dataclass
class Connection:
    socket: object
    number: int
    reader: FrameReader
    outbox: Outbox
    deadline: float
    state: str = "open"
    mode: int = 9
    messages: int = 0
    selected: int = 0
    expected: object = None
    next_sequence: int = 1
    read_due: float = 0
    frame_deadline: object = None
    closed: bool = False
    reason: str = ""
    unbind_sent: bool = False


class Engine:
    """One finite nonblocking connection cohort; owns no worker threads."""

    def __init__(self, config, ready=print):
        self.config, self.ready = config, ready
        self.version = {"3.4": 0x34, "5.0": 0x50}[config.version]
        self.selector = selectors.DefaultSelector()
        self.listener = None
        self.connections = []
        self.ordinary = Budget(config.held_count, config.held_bytes)
        self.control = Budget(config.connections * 8, config.connections * 1024)
        self.started = time.monotonic()
        self.startup_end = self.started + config.timeout
        self.active_end = None
        self.stopped = False
        self.samples, self.failures = [], []
        self.counts = dict.fromkeys(
            (
                "accepted_connections",
                "connected_connections",
                "bound_connections",
                "closed_connections",
                "refused_connections",
                "peer_closes",
                "local_closes",
                "messages_observed",
                "selected_messages",
                "payload_octets",
                "responses_observed",
                "nacks_observed",
                "negative_responses",
                "unexpected_responses",
                "invalid_frames",
                "injections_attempted",
                "injections_completed",
                "injections_abandoned",
                "ack_frames_queued",
                "ack_frames_written",
                "malformed_frames_queued",
                "malformed_frames_written",
                "fragments_written",
                "missing_acknowledgements",
                "slow_read_sessions",
                "received_bytes",
                "sent_bytes",
                "write_timeouts",
                "frame_timeouts",
                "connect_failures",
                "queue_rejections",
                "discarded_frames",
                "discarded_bytes",
                "cleanup_errors",
                "interruptions",
                "errors",
            ),
            0,
        )

    def stop(self, signum, frame):
        """Signal hook: request bounded cleanup on the owning event loop."""
        if not self.stopped:
            self.stopped = True
            self.counts["interruptions"] += 1
            self._error("Termination requested")

    def run(self):
        try:
            self._start()
            while not self.stopped:
                now = time.monotonic()
                self._tick(now)
                if self.stopped:
                    break
                if self.connections and all(item.closed for item in self.connections):
                    if len(self.connections) == self.config.connections:
                        break
                if (
                    now
                    >= self.started
                    + self.config.timeout
                    + self.config.duration
                    + self.config.drain
                ):
                    break
                for connection in self.connections:
                    self._interest(connection, now)
                for key, events in self.selector.select(0.005):
                    if key.data is None:
                        self._accept()
                        continue
                    connection = key.data
                    try:
                        if connection.state == "connecting":
                            error = connection.socket.getsockopt(
                                socket.SOL_SOCKET, socket.SO_ERROR
                            )
                            if error:
                                raise OSError(error, "Nonblocking connect failed")
                            self._connected(connection, time.monotonic())
                        else:
                            if events & selectors.EVENT_READ:
                                self._read(connection)
                            if not connection.closed and events & selectors.EVENT_WRITE:
                                self._write(connection)
                    except ProtocolError as error:
                        self.counts["invalid_frames"] += 1
                        self._error(str(error))
                        self._close(connection, "protocol_error")
                    except OSError:
                        if connection.state == "connecting":
                            self.counts["connect_failures"] += 1
                        self._error("Socket operation failed")
                        self._close(connection, "socket_error")
        except (OSError, ValueError) as error:
            self._error(type(error).__name__ + " during startup or event dispatch")
        finally:
            self._close_listener()
            for connection in self.connections:
                self._close(
                    connection,
                    "interrupted" if self.counts["interruptions"] else "run_deadline",
                )
            self.selector.close()
        return self._result()

    def _start(self):
        family = (
            socket.AF_INET6
            if ipaddress.ip_address(self.config.host).version == 6
            else socket.AF_INET
        )
        if self.config.role == "server":
            self.listener = socket.socket(family, socket.SOCK_STREAM)
            self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.listener.setblocking(False)
            self.listener.bind((self.config.host, self.config.port))
            self.listener.listen(self.config.connections)
            self.selector.register(self.listener, selectors.EVENT_READ, None)
            self.ready("READY port=" + str(self.listener.getsockname()[1]))
        else:
            for _ in range(self.config.connections):
                sock = socket.socket(family, socket.SOCK_STREAM)
                connection = self._new_connection(sock, "connecting")
                error = sock.connect_ex((self.config.host, self.config.port))
                if error == 0:
                    self._connected(connection, time.monotonic())
                elif error not in (
                    errno.EINPROGRESS,
                    errno.EWOULDBLOCK,
                    errno.EALREADY,
                ):
                    self.counts["connect_failures"] += 1
                    self._error("Connect failed")
                    self._close(connection, "connect_failure")

    def _new_connection(self, sock, state):
        connection = Connection(
            sock,
            len(self.connections) + 1,
            FrameReader(self.config.max_frame),
            Outbox(self.ordinary, self.control),
            self.startup_end,
            state,
        )
        self.connections.append(connection)
        sock.setblocking(False)
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4096)
        return connection

    def _accept(self):
        try:
            sock, _ = self.listener.accept()
        except BlockingIOError:
            return
        if len(self.connections) >= self.config.connections:
            self.counts["refused_connections"] += 1
            sock.close()
            return
        self._new_connection(sock, "open")
        self.counts["accepted_connections"] += 1

    def _connected(self, connection, now):
        self.counts["connected_connections"] += 1
        connection.state = "binding"
        system_id = os.environ.get("SMPP_SYSTEM_ID", "sim").encode("ascii")
        password = os.environ.get("SMPP_PASSWORD", "sim").encode("ascii")
        if len(system_id) > 15 or len(password) > 8 or b"\0" in system_id + password:
            raise ProtocolError("Invalid bounded bind credentials")
        body = system_id + b"\0" + password + b"\0\0" + bytes([self.version, 0, 0, 0])
        connection.expected = (0x80000009, 1)
        connection.next_sequence = 2
        self._queue(connection, encode_pdu(9, 0, 1, body), now, control=True)

    def _interest(self, connection, now):
        if connection.closed:
            return
        events = 0
        if connection.state == "connecting":
            events = selectors.EVENT_WRITE
        else:
            if now >= connection.read_due:
                events |= selectors.EVENT_READ
            if connection.outbox.head(now):
                events |= selectors.EVENT_WRITE
        try:
            self.selector.get_key(connection.socket)
            if events:
                self.selector.modify(connection.socket, events, connection)
            else:
                self.selector.unregister(connection.socket)
        except KeyError:
            if events:
                self.selector.register(connection.socket, events, connection)

    def _read(self, connection):
        now = time.monotonic()
        limit = min(16384, self.config.max_frame)
        slow = self.config.fault == "slow-reader" and connection.state == "bound"
        if slow:
            limit = min(limit, self.config.read_bytes)
        elif connection.state != "bound":
            buffered = len(connection.reader.buffer)
            limit = min(
                limit,
                (
                    4 - buffered
                    if buffered < 4
                    else struct.unpack_from("!I", connection.reader.buffer)[0]
                    - buffered
                ),
            )
        try:
            data = connection.socket.recv(limit)
        except BlockingIOError:
            return
        if not data:
            connection.reader.eof()
            self._close(connection, "peer_eof")
            return
        now = time.monotonic()
        if slow:
            connection.read_due = now + self.config.read_interval
        self.counts["received_bytes"] += len(data)
        if not connection.reader.buffer:
            connection.frame_deadline = now + self.config.timeout
        for frame in connection.reader.feed(data):
            connection.frame_deadline = now + self.config.timeout
            self._sample("received", connection, frame)
            self._frame(connection, frame, now)
            if connection.closed:
                break
        if not connection.reader.buffer:
            connection.frame_deadline = None

    def _frame(self, connection, frame, now):
        if frame.command & 0x80000000:
            self._response(connection, frame, now)
            return
        if frame.command in (1, 2, 9):
            if self.config.role != "server" or connection.state != "open":
                raise ProtocolError("Unexpected bind request")
            version = bind_version(frame.body)
            connection.mode = frame.command
            connection.state = "binding"
            if version != self.version:
                self._error("Requested bind profile differs from configured profile")
                self._queue(
                    connection,
                    encode_pdu(frame.command | 0x80000000, 0x0D, frame.sequence),
                    now,
                    control=True,
                    action="reject",
                )
            else:
                body = b"fault-peer\0\x02\x10\x00\x01" + bytes([self.version])
                self._queue(
                    connection,
                    encode_pdu(frame.command | 0x80000000, 0, frame.sequence, body),
                    now,
                    control=True,
                    action="bound",
                )
        elif frame.command in (6, 0x15):
            list(_tlvs(frame.body, 0))
            self._queue(
                connection,
                encode_pdu(frame.command | 0x80000000, 0, frame.sequence),
                now,
                control=True,
                action="close" if frame.command == 6 else "",
            )
        else:
            self._message(connection, frame, now)

    def _response(self, connection, frame, now):
        self.counts["responses_observed"] += 1
        if frame.status:
            self.counts["negative_responses"] += 1
        if frame.command == 0x80000000:
            self.counts["nacks_observed"] += 1
        if connection.expected != (frame.command, frame.sequence):
            self.counts["unexpected_responses"] += 1
            if frame.command != 0x80000000:
                raise ProtocolError(
                    "Response command or sequence does not match pending control"
                )
            return
        connection.expected = None
        if frame.status:
            raise ProtocolError("Peer rejected bind or unbind")
        if connection.state == "binding":
            offset = _cstring(frame.body, 0, 16)
            advertised = [
                frame.body[start : start + size]
                for tag, start, size in _tlvs(frame.body, offset)
                if tag == 0x0210
            ]
            if advertised != [bytes([self.version])]:
                raise ProtocolError("Exact requested version advertisement is required")
            self._bound(connection, now)
        else:
            list(_tlvs(frame.body, 0))
            self._close(connection, "unbind_complete")

    def _bound(self, connection, now):
        connection.state = "bound"
        self.counts["bound_connections"] += 1
        if self.config.fault == "slow-reader":
            connection.read_due = now + self.config.delay
            self.counts["injections_attempted"] += 1
            self.counts["injections_completed"] += 1
            self.counts["slow_read_sessions"] += 1
        if self.counts["bound_connections"] == self.config.connections:
            self.active_end = now + self.config.duration

    def _message(self, connection, frame, now):
        if connection.state != "bound":
            raise ProtocolError("Message arrived before bind completed")
        command = {"submit": 4, "deliver": 5, "data": 0x103}[self.config.operation]
        if frame.command != command:
            raise ProtocolError("Message differs from explicitly selected operation")
        if (
            self.config.role == "server"
            and connection.mode == 1
            and (frame.command == 4 or self.version == 0x50)
        ):
            raise ProtocolError("ESME receiver bind cannot originate this message")
        self.counts["payload_octets"] += inspect_message(
            frame, self.config.role, self.version
        )
        self.counts["messages_observed"] += 1
        connection.messages += 1
        selected = connection.selected < self.config.count
        if selected:
            connection.selected += 1
            self.counts["selected_messages"] += 1
        fault = self.config.fault
        if fault in ("malformed", "disconnect"):
            selected = selected and connection.selected == self.config.count
        if not selected or fault in ("none", "slow-reader"):
            self._queue(connection, acknowledge(frame), now)
            return
        self.counts["injections_attempted"] += 1
        if fault == "missing":
            self.counts["missing_acknowledgements"] += 1
            self.counts["injections_completed"] += 1
        elif fault == "disconnect":
            self._close(connection, "fault_disconnect")
            name = (
                "injections_completed"
                if connection.socket.fileno() == -1
                else "injections_abandoned"
            )
            self.counts[name] += 1
        elif fault == "malformed":
            malformed = struct.pack(
                "!IIII", 15, frame.command | 0x80000000, 0, frame.sequence
            )
            self._queue(connection, malformed, now, injection=True, action="malformed")
        elif fault == "duplicate":
            if self._queue(connection, acknowledge(frame), now):
                self._queue(connection, acknowledge(frame), now, injection=True)
            else:
                self.counts["injections_abandoned"] += 1
        elif fault == "late":
            self._queue(
                connection,
                acknowledge(frame),
                now,
                injection=True,
                delay=self.config.delay,
            )
        elif fault == "fragmented":
            self._queue(
                connection,
                acknowledge(frame),
                now,
                injection=True,
                fragment=self.config.fragment_bytes,
            )

    def _queue(
        self,
        connection,
        data,
        now,
        control=False,
        action="",
        injection=False,
        delay=0,
        fragment=0,
    ):
        if connection.closed:
            if injection:
                self.counts["injections_abandoned"] += 1
            return False
        pending = Pending(
            data,
            now + delay,
            now + delay + self.config.timeout,
            injection,
            fragment,
            action,
        )
        if not connection.outbox.add(pending, control):
            if injection:
                self.counts["injections_abandoned"] += 1
            self.counts["queue_rejections"] += 1
            self._error("Configured write retention budget exhausted")
            self._close(connection, "queue_capacity")
            return False
        if not control:
            self.counts[
                (
                    "malformed_frames_queued"
                    if action == "malformed"
                    else "ack_frames_queued"
                )
            ] += 1
        return True

    def _write(self, connection):
        now = time.monotonic()
        pending = connection.outbox.head(now)
        if pending is None:
            return
        end = len(pending.data)
        if pending.fragment:
            end = min(end, pending.position + pending.fragment)
        try:
            count = connection.socket.send(
                memoryview(pending.data)[pending.position : end]
            )
        except BlockingIOError:
            return
        if count == 0:
            raise OSError("Zero-byte socket write")
        pending.position += count
        self.counts["sent_bytes"] += count
        if pending.fragment:
            self.counts["fragments_written"] += 1
        if pending.position != len(pending.data):
            if pending.fragment:
                pending.due = now + self.config.fragment_gap
            return
        connection.outbox.complete()
        if pending.action == "malformed":
            self.counts["malformed_frames_written"] += 1
        elif not pending.control:
            self.counts["ack_frames_written"] += 1
        self._sample("written", connection, raw=pending.data[:16])
        if pending.injection:
            self.counts["injections_completed"] += 1
        if pending.action == "bound":
            self._bound(connection, now)
        elif pending.action == "close":
            self._close(connection, "peer_unbind")
        elif pending.action == "reject":
            self._close(connection, "bind_rejected")

    def _tick(self, now):
        if self.active_end is None and now >= self.startup_end:
            self.stopped = True
            self._error(
                "Bound connection cohort did not arrive before startup deadline"
            )
            self._close_listener()
            for connection in self.connections:
                self._close(connection, "startup_timeout")
            return
        draining = self.active_end is not None and now >= self.active_end
        if draining:
            self._close_listener()
        for connection in self.connections:
            if connection.closed:
                continue
            if connection.state != "bound" and now >= connection.deadline:
                self._error("Connect, bind or unbind deadline expired")
                self._close(connection, "control_timeout")
            elif (
                connection.frame_deadline is not None
                and now >= connection.frame_deadline
            ):
                self.counts["frame_timeouts"] += 1
                self._error("Incomplete frame deadline expired")
                self._close(connection, "frame_timeout")
            elif any(now >= entry.deadline for entry in connection.outbox.entries()):
                self.counts["write_timeouts"] += 1
                self._error("Physical write deadline expired")
                self._close(connection, "write_timeout")
            elif draining and now >= self.active_end + self.config.drain:
                self._close(connection, "drain_deadline")
            elif (
                draining
                and not connection.unbind_sent
                and not any(connection.outbox.entries())
            ):
                connection.unbind_sent = True
                sequence = connection.next_sequence
                connection.next_sequence += 1
                connection.expected = (0x80000006, sequence)
                connection.state = "unbinding"
                connection.deadline = now + self.config.timeout
                self._queue(connection, encode_pdu(6, 0, sequence), now, control=True)

    def _close_listener(self):
        if self.listener is not None:
            try:
                self.selector.unregister(self.listener)
            except KeyError:
                pass
            try:
                self.listener.close()
            except OSError:
                self.counts["cleanup_errors"] += 1
            self.listener = None

    def _close(self, connection, reason):
        if connection.closed:
            return
        connection.closed, connection.reason = True, reason
        try:
            self.selector.unregister(connection.socket)
        except KeyError:
            pass
        try:
            connection.socket.close()
        except OSError:
            self.counts["cleanup_errors"] += 1
        count, size, injections = connection.outbox.drop()
        self.counts["discarded_frames"] += count
        self.counts["discarded_bytes"] += size
        self.counts["injections_abandoned"] += injections
        self.counts["closed_connections"] += 1
        self.counts[
            "peer_closes" if reason in ("peer_eof", "peer_unbind") else "local_closes"
        ] += 1
        connection.reader.buffer.clear()
        self._sample(reason, connection)

    def _error(self, detail):
        self.counts["errors"] += 1
        if len(self.failures) < 16:
            self.failures.append(detail[:200])

    def _sample(self, event, connection, frame=None, raw=None):
        if len(self.samples) >= self.config.sample_count:
            return
        sample = {"event": event, "connection": connection.number}
        if frame:
            sample["headerHex"] = struct.pack(
                "!IIII",
                16 + len(frame.body),
                frame.command,
                frame.status,
                frame.sequence,
            ).hex()
        elif raw:
            sample["headerHex"] = raw.hex()
        self.samples.append(sample)

    def _result(self):
        terminal = self.config.fault in ("disconnect", "malformed", "slow-reader")
        expected = (
            0
            if self.config.fault == "none"
            else (
                self.config.connections
                if terminal
                else self.config.connections * self.config.count
            )
        )
        messages = (
            0
            if self.config.fault == "slow-reader"
            else self.config.connections * self.config.count
        )
        cleanup = (
            not self.ordinary.count
            and not self.control.count
            and all(item.socket.fileno() == -1 for item in self.connections)
            and not self.counts["cleanup_errors"]
        )
        passed = (
            cleanup
            and not self.counts["errors"]
            and self.counts["bound_connections"] == self.config.connections
            and self.counts["selected_messages"] >= messages
            and self.counts["injections_completed"] == expected
            and not self.counts["injections_abandoned"]
        )
        return {
            "passed": passed,
            "cleanup_complete": cleanup,
            "elapsed_seconds": time.monotonic() - self.started,
            "expected_messages": messages,
            "expected_injections": expected,
            "counts": self.counts,
            "retention": {
                "ordinary_count_peak": self.ordinary.peak_count,
                "ordinary_bytes_peak": self.ordinary.peak_bytes,
                "control_count_peak": self.control.peak_count,
                "control_bytes_peak": self.control.peak_bytes,
                "final_held_count": self.ordinary.count + self.control.count,
                "final_held_bytes": self.ordinary.size + self.control.size,
            },
            "connections": [
                {
                    "number": item.number,
                    "messages": item.messages,
                    "selected": item.selected,
                    "close_reason": item.reason,
                    "decoder_buffer_peak": item.reader.peak,
                }
                for item in self.connections
            ],
            "samples": self.samples,
            "failures": self.failures,
        }


def emit(line):
    """Flush the finite CLI readiness/result protocol."""
    print(line, flush=True)


def main(arguments=None):
    config = parse_config(sys.argv[1:] if arguments is None else arguments)
    try:
        initialize_report(config)
    except OSError:
        print("A fresh writable report directory is required", file=sys.stderr)
        return 2
    engine = Engine(config, emit)
    previous = {
        value: signal.signal(value, engine.stop)
        for value in (signal.SIGINT, signal.SIGTERM)
    }
    try:
        result = engine.run()
    finally:
        for value, handler in previous.items():
            signal.signal(value, handler)
    write_report(config, result)
    emit("RESULT passed=" + str(result["passed"]).lower())
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
