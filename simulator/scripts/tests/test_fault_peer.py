"""Independent bytes and finite local-process contracts for the raw fault peer."""

import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fault_peer as peer


class WireTests(unittest.TestCase):
    def test_independent_header_bytes_fragmentation_coalescing_and_ownership(self):
        expected = bytes.fromhex("00000013800000040000000000000007696400")
        self.assertEqual(expected, peer.encode_pdu(0x80000004, 0, 7, b"id\0"))
        reader = peer.FrameReader(64)
        source = bytearray(expected + bytes.fromhex("00000010000000150000000000000008"))
        frames = []
        for octet in source:
            frames.extend(reader.feed(bytes([octet])))
        source[:] = b"x" * len(source)
        self.assertEqual(
            [peer.Frame(0x80000004, 0, 7, b"id\0"), peer.Frame(0x15, 0, 8, b"")], frames
        )
        self.assertLessEqual(reader.peak, 64)
        coalesced = list(peer.FrameReader(64).feed(expected * 2))
        self.assertEqual([frames[0], frames[0]], coalesced)

    def test_header_limits_status_sequence_and_midframe_eof_are_checked(self):
        for header in ("0000000f", "00000041", "ffffffff"):
            with self.assertRaises(peer.ProtocolError):
                list(peer.FrameReader(64).feed(bytes.fromhex(header)))
        for command, status, sequence in ((4, 1, 1), (4, 0, 0), (4, 0, 0x80000000)):
            with self.assertRaises(ValueError):
                peer.encode_pdu(command, status, sequence)
        reader = peer.FrameReader(64)
        list(reader.feed(bytes.fromhex("00000013000000040000000000000001")))
        with self.assertRaises(peer.ProtocolError):
            reader.eof()


class BodyTests(unittest.TestCase):
    def test_data_and_short_message_addresses_use_their_own_wire_bounds(self):
        for version in (0x34, 0x50):
            for command, role, maximum in (
                (4, "server", 20),
                (5, "client", 20),
                (0x103, "server", 64),
                (0x103, "client", 64),
            ):
                for size in (maximum, maximum + 1):
                    with self.subTest(
                        version=version, command=command, role=role, size=size
                    ):
                        addresses = (
                            b"\0\0\0" + b"s" * size + b"\0\0\0" + b"d" * size + b"\0"
                        )
                        tail = (
                            bytes.fromhex("000004")
                            if command == 0x103
                            else bytes.fromhex("00000000000000040000")
                        )
                        frame = peer.Frame(command, 0, 7, addresses + tail)
                        if size == maximum:
                            self.assertEqual(
                                0, peer.inspect_message(frame, role, version)
                            )
                        else:
                            with self.assertRaises(peer.ProtocolError):
                                peer.inspect_message(frame, role, version)

    def test_bind_and_both_message_layouts_have_independent_response_bytes(self):
        for version in (0x34, 0x50):
            self.assertEqual(
                version, peer.bind_version(b"sim\0sim\0\0" + bytes([version, 0, 0, 0]))
            )
            short = bytes.fromhex("0000000000000000000000000000040003") + b"\0\xffA"
            data = bytes.fromhex("000000000000000000040424000300ff41")
            for command, role, body, response in (
                (4, "server", short, "00000013800000040000000000000007696400"),
                (5, "client", short, "0000001180000005000000000000000700"),
                (0x103, "server", data, "00000013800001030000000000000007696400"),
                (0x103, "client", data, "00000013800001030000000000000007696400"),
            ):
                frame = peer.Frame(command, 0, 7, body)
                self.assertEqual(3, peer.inspect_message(frame, role, version))
                self.assertEqual(bytes.fromhex(response), peer.acknowledge(frame))
                with self.assertRaises(peer.ProtocolError):
                    peer.inspect_message(
                        peer.Frame(command, 0, 7, body[:-1]), role, version
                    )

    def test_wrong_role_malformed_strings_tlvs_and_34_length_are_rejected(self):
        for body in (b"x" * 16, b"\xff\0sim\0\0\x34\0\0\0"):
            with self.assertRaises(peer.ProtocolError):
                peer.bind_version(body)
        short = bytes.fromhex("0000000000000000000000000000040000")
        with self.assertRaises(peer.ProtocolError):
            peer.inspect_message(peer.Frame(5, 0, 1, short), "server", 0x34)
        with self.assertRaises(peer.ProtocolError):
            peer.inspect_message(
                peer.Frame(4, 0, 1, short + b"\x04\x24\x00"), "server", 0x50
            )
        payload255 = short[:-1] + b"\xff" + bytes(255)
        with self.assertRaises(peer.ProtocolError):
            peer.inspect_message(peer.Frame(4, 0, 1, payload255), "server", 0x34)
        self.assertEqual(
            255, peer.inspect_message(peer.Frame(4, 0, 1, payload255), "server", 0x50)
        )


class QueueTests(unittest.TestCase):
    def test_shared_bounds_include_inflight_and_control_has_separate_capacity(self):
        ordinary, control = peer.Budget(2, 8), peer.Budget(1, 4)
        first, second = peer.Outbox(ordinary, control), peer.Outbox(ordinary, control)
        delayed = peer.Pending(b"1234", 10, 20, True)
        self.assertTrue(first.add(delayed))
        self.assertTrue(second.add(peer.Pending(b"5678", 0, 20)))
        self.assertFalse(first.add(peer.Pending(b"x", 0, 20)))
        self.assertTrue(first.add(peer.Pending(b"ctrl", 0, 20), control=True))
        self.assertEqual(b"ctrl", first.head(0).data)
        first.complete()
        self.assertEqual((2, 8), (ordinary.count, ordinary.size))
        self.assertIsNone(first.head(9))
        self.assertIs(delayed, first.head(10))
        delayed.position = 2
        self.assertEqual((2, 8), (ordinary.count, ordinary.size))
        self.assertEqual((1, 4, 1), first.drop())
        self.assertEqual((1, 4), (ordinary.count, ordinary.size))
        second.drop()
        self.assertEqual(
            (0, 0, 2, 8),
            (ordinary.count, ordinary.size, ordinary.peak_count, ordinary.peak_bytes),
        )

    def test_active_fragments_cannot_interleave_control_bytes_or_reorder_ordinary(self):
        outbox = peer.Outbox(peer.Budget(3, 20), peer.Budget(1, 10))
        first = peer.Pending(b"first", 0, 10, fragment=1)
        second = peer.Pending(b"next", 0, 10)
        self.assertTrue(outbox.add(first))
        self.assertTrue(outbox.add(second))
        self.assertIs(first, outbox.head(0))
        first.position = 1
        first.due = 5
        self.assertTrue(outbox.add(peer.Pending(b"control", 0, 10), control=True))
        self.assertIsNone(outbox.head(4))
        self.assertIs(first, outbox.head(5))
        outbox.complete()
        self.assertEqual(b"control", outbox.head(5).data)
        outbox.complete()
        self.assertIs(second, outbox.head(5))
        outbox.complete()
        self.assertEqual((0, 0, 0), outbox.drop())


class ConfigurationTests(unittest.TestCase):
    def test_cli_bounds_profile_role_and_lateness_are_explicit(self):
        config = peer.parse_config(
            [
                "server",
                "--port=0",
                "--version=5.0",
                "--report=/tmp/uncreated-fault-test",
            ]
        )
        self.assertEqual(
            ("server", 0, "5.0", "submit"),
            (config.role, config.port, config.version, config.operation),
        )
        for options in (
            "--connections=0",
            "--duration=nan",
            "--timeout=inf",
            "--max-frame=15",
            "--held-count=0",
            "--held-bytes=0",
            "--host=example.org",
            "--operation=deliver",
            "--version=3.3",
            "--fault=late --delay=.01 --peer-timeout=.1",
        ):
            with self.subTest(options=options), contextlib.redirect_stderr(
                io.StringIO()
            ):
                with self.assertRaises(SystemExit) as raised:
                    peer.parse_config(
                        [
                            "server",
                            "--report=/tmp/uncreated-fault-test",
                            *options.split(),
                        ]
                    )
                self.assertEqual(2, raised.exception.code)

    def test_reports_are_fresh_and_include_reproducible_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "fresh"
            config = peer.parse_config(
                ["client", "--report=" + str(path), "--fault=missing", "--count=3"]
            )
            peer.initialize_report(config)
            peer.write_report(config, {"passed": True, "observed": 3})
            report = json.loads((path / "report.json").read_text())
            self.assertEqual("smpp-raw-fault-peer-v1", report["schema"])
            self.assertEqual(3, report["config"]["count"])
            self.assertEqual("missing", report["config"]["fault"])
            self.assertEqual(64, len(report["sourceSha256"]))
            self.assertTrue(report["result"]["passed"])
            with self.assertRaises(FileExistsError):
                peer.initialize_report(config)
            with self.assertRaises(FileExistsError):
                peer.write_report(config, {})

    def test_failed_socket_configuration_still_closes_the_owned_socket(self):
        config = peer.parse_config(["client", "--report=/tmp/uncreated-fault-test"])
        engine = peer.Engine(config)
        sock = socket.socket()
        self.addCleanup(sock.close)
        failing = mock.Mock(wraps=sock)
        failing.setsockopt.side_effect = OSError("controlled socket option failure")
        with mock.patch.object(peer.socket, "socket", return_value=failing):
            result = engine.run()
        self.assertEqual(-1, sock.fileno())
        self.assertFalse(result["passed"])
        self.assertTrue(result["cleanup_complete"])

    def test_listener_close_failure_does_not_skip_other_owned_cleanup(self):
        config = peer.parse_config(["server", "--report=/tmp/uncreated-fault-test"])
        engine = peer.Engine(config)
        listener, connection_socket = socket.socket(), socket.socket()
        self.addCleanup(listener.close)
        self.addCleanup(connection_socket.close)
        failing = mock.Mock(wraps=listener)
        failing.close.side_effect = OSError("controlled listener close failure")
        engine.listener = failing
        connection = engine._new_connection(connection_socket, "bound")
        engine._queue(
            connection,
            bytes.fromhex("00000013800000040000000000000007696400"),
            time.monotonic(),
        )
        engine.stopped = True
        with mock.patch.object(engine, "_start"):
            result = engine.run()
        self.assertEqual(-1, connection_socket.fileno())
        self.assertEqual(0, result["retention"]["final_held_count"])
        self.assertEqual(1, result["counts"]["cleanup_errors"])
        self.assertFalse(result["cleanup_complete"])
        self.assertFalse(result["passed"])


SCRIPT = Path(__file__).resolve().parents[1] / "fault_peer.py"
SHORT_BODY = bytes.fromhex("0000000000000000000000000000040003") + b"\0\xffA"


def raw_frame(command, sequence, body=b"", status=0):
    return struct.pack("!IIII", 16 + len(body), command, status, sequence) + body


def read_raw(sock):
    header = b""
    while len(header) < 16:
        part = sock.recv(16 - len(header))
        if not part:
            raise EOFError("Peer closed before complete header")
        header += part
    size, command, status, sequence = struct.unpack("!IIII", header)
    if not 16 <= size <= 1048576:
        raise ValueError("Unbounded fixture read")
    body = b""
    while len(body) < size - 16:
        part = sock.recv(size - 16 - len(body))
        if not part:
            raise EOFError("Peer closed during body")
        body += part
    return command, status, sequence, body


def read_malformed_header(sock):
    header = b""
    while len(header) < 16:
        part = sock.recv(16 - len(header))
        if not part:
            raise EOFError("Peer closed before the complete malformed header")
        header += part
    return header


def stop_process(process, stream):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(1)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(2)
    stream.close()


class ProcessHarness:
    def setUp(self):
        root = os.environ.get("SMPP_FAULT_RESULTS")
        if root:
            Path(root).mkdir(parents=True, exist_ok=True)
        self.directory = Path(tempfile.mkdtemp(prefix="fault-peer-", dir=root))
        if not root:
            self.addCleanup(shutil.rmtree, self.directory)
        self.serial = 0

    def start_tool(self, role, *options):
        self.serial += 1
        directory = self.directory
        report = directory / ("report-" + str(self.serial))
        log = directory / ("peer-" + str(self.serial) + ".log")
        output = log.open("w")
        command = [
            sys.executable,
            str(SCRIPT),
            role,
            "--report=" + str(report),
            "--timeout=1",
            "--duration=.5",
            "--drain=.2",
            *options,
        ]
        process = subprocess.Popen(
            command,
            stdout=output,
            stderr=subprocess.STDOUT,
            env=dict(
                os.environ,
                PYTHONPYCACHEPREFIX="/tmp/lightweight-smpp-fault-pycache",
                SMPP_SYSTEM_ID="sim",
                SMPP_PASSWORD="sim",
            ),
        )
        self.addCleanup(stop_process, process, output)
        return process, log, report

    def ready_port(self, process, log):
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            for line in log.read_text().splitlines():
                if line.startswith("READY port="):
                    return int(line[11:])
            if process.poll() is not None:
                break
            time.sleep(0.005)
        self.fail("Raw listener did not become ready: " + log.read_text())

    def result(self, process, log, report, expected=0):
        self.assertEqual(expected, process.wait(3), log.read_text())
        result = json.loads((report / "report.json").read_text())["result"]
        counts = result["counts"]
        self.assertEqual(
            counts["injections_attempted"],
            counts["injections_completed"] + counts["injections_abandoned"],
        )
        self.assertEqual(0, result["retention"]["final_held_count"])
        self.assertEqual(0, result["retention"]["final_held_bytes"])
        return result


class SocketProcessTests(ProcessHarness, unittest.TestCase):
    def test_truncated_malformed_header_reports_eof_without_spinning(self):
        program = (
            "import socket\n"
            "from test_fault_peer import read_malformed_header\n"
            "reader, writer = socket.socketpair()\n"
            "with reader, writer:\n"
            "    reader.settimeout(.2)\n"
            "    writer.sendall(b'\\0\\0\\0\\x0f')\n"
            "    writer.shutdown(socket.SHUT_WR)\n"
            "    try:\n"
            "        read_malformed_header(reader)\n"
            "    except EOFError:\n"
            "        print('expected bounded EOF')\n"
            "    else:\n"
            "        raise AssertionError('Truncated header was accepted')\n"
        )
        try:
            result = subprocess.run(
                [sys.executable, "-c", program],
                cwd=Path(__file__).parent,
                capture_output=True,
                text=True,
                timeout=2,
                check=False,
            )
        except subprocess.TimeoutExpired:
            self.fail("An early EOF left the malformed-header fixture spinning")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("expected bounded EOF", result.stdout)

    def test_profile_rejection_has_header_only_reply_and_a_local_close_reason(self):
        for version, requested in (("3.4", 0x50), ("5.0", 0x34)):
            with self.subTest(version=version):
                process, log, report = self.start_tool(
                    "server", "--port=0", "--version=" + version
                )
                with socket.create_connection(
                    ("127.0.0.1", self.ready_port(process, log)), timeout=1
                ) as sock:
                    sock.sendall(
                        raw_frame(2, 7, b"sim\0sim\0\0" + bytes([requested, 0, 0, 0]))
                    )
                    self.assertEqual((0x80000002, 13, 7, b""), read_raw(sock))
                    self.assertEqual(b"", sock.recv(1))
                result = self.result(process, log, report, expected=1)
                self.assertEqual(
                    (0, 1),
                    (result["counts"]["peer_closes"], result["counts"]["local_closes"]),
                )
                self.assertEqual(
                    "bind_rejected", result["connections"][0]["close_reason"]
                )

    def test_connection_cohort_and_shared_retention_are_bounded(self):
        process, log, report = self.start_tool(
            "server",
            "--port=0",
            "--connections=2",
            "--fault=late",
            "--held-count=2",
            "--held-bytes=38",
        )
        port = self.ready_port(process, log)
        with socket.create_connection(
            ("127.0.0.1", port), timeout=1
        ) as first, socket.create_connection(("127.0.0.1", port), timeout=1) as second:
            for sock in (first, second):
                sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
                read_raw(sock)
            with socket.create_connection(("127.0.0.1", port), timeout=1) as excess:
                self.assertEqual(b"", excess.recv(1))
            for sock in (first, second):
                sock.sendall(raw_frame(4, 2, SHORT_BODY) + raw_frame(0x15, 3))
                self.assertEqual((0x80000015, 0, 3, b""), read_raw(sock))
            for sock in (first, second):
                self.assertEqual((0x80000004, 0, 2, b"id\0"), read_raw(sock))
                sock.sendall(raw_frame(6, 4))
                read_raw(sock)
        result = self.result(process, log, report)
        self.assertEqual(
            (2, 1, 2),
            tuple(
                result["counts"][key]
                for key in (
                    "accepted_connections",
                    "refused_connections",
                    "injections_completed",
                )
            ),
        )
        self.assertEqual(
            (2, 38),
            tuple(
                result["retention"][key]
                for key in ("ordinary_count_peak", "ordinary_bytes_peak")
            ),
        )

    def test_no_peer_expires_once_at_the_startup_deadline(self):
        process, log, report = self.start_tool("server", "--port=0", "--timeout=.05")
        self.ready_port(process, log)
        result = self.result(process, log, report, expected=1)
        self.assertLess(result["elapsed_seconds"], 0.25)
        self.assertEqual(1, result["counts"]["errors"])
        self.assertTrue(result["cleanup_complete"])

    def test_server_binds_acknowledges_controls_and_closes_for_both_profiles(self):
        for version, octet in (("3.4", 0x34), ("5.0", 0x50)):
            process, log, report = self.start_tool(
                "server", "--port=0", "--version=" + version
            )
            port = self.ready_port(process, log)
            with socket.create_connection(("127.0.0.1", port), timeout=1) as sock:
                sock.sendall(
                    raw_frame(2, 17, b"sim\0sim\0\0" + bytes([octet, 0, 0, 0]))
                )
                self.assertEqual(
                    (0x80000002, 0, 17, b"fault-peer\0\x02\x10\0\x01" + bytes([octet])),
                    read_raw(sock),
                )
                sock.sendall(raw_frame(4, 18, SHORT_BODY))
                self.assertEqual((0x80000004, 0, 18, b"id\0"), read_raw(sock))
                sock.sendall(raw_frame(0x15, 19))
                self.assertEqual((0x80000015, 0, 19, b""), read_raw(sock))
                sock.sendall(raw_frame(6, 20))
                self.assertEqual((0x80000006, 0, 20, b""), read_raw(sock))
                self.assertEqual(b"", sock.recv(1))
            result = self.result(process, log, report)
            self.assertEqual(1, result["counts"]["messages_observed"])
            self.assertTrue(result["cleanup_complete"])

    def test_client_connects_once_and_acknowledges_deliveries_for_both_profiles(self):
        for version, octet in (("3.4", 0x34), ("5.0", 0x50)):
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                listener.listen(1)
                listener.settimeout(2)
                process, log, report = self.start_tool(
                    "client",
                    "--port=" + str(listener.getsockname()[1]),
                    "--version=" + version,
                )
                sock, _ = listener.accept()
                with sock:
                    sock.settimeout(1)
                    self.assertEqual(
                        (9, 0, 1, b"sim\0sim\0\0" + bytes([octet, 0, 0, 0])),
                        read_raw(sock),
                    )
                    sock.sendall(
                        raw_frame(0x80000009, 1, b"mc\0\x02\x10\0\x01" + bytes([octet]))
                    )
                    sock.sendall(raw_frame(5, 7, SHORT_BODY))
                    self.assertEqual((0x80000005, 0, 7, b"\0"), read_raw(sock))
                    sock.sendall(raw_frame(6, 8))
                    self.assertEqual((0x80000006, 0, 8, b""), read_raw(sock))
            result = self.result(process, log, report)
            self.assertEqual(1, result["counts"]["connected_connections"])
            self.assertTrue(result["cleanup_complete"])

    def test_duplicate_late_and_fragmented_acknowledgements_are_exactly_accounted(self):
        for fault in ("duplicate", "late", "fragmented"):
            with self.subTest(fault=fault):
                process, log, report = self.start_tool(
                    "server",
                    "--port=0",
                    "--fault=" + fault,
                    "--delay=.15",
                    "--peer-timeout=.05",
                )
                with socket.create_connection(
                    ("127.0.0.1", self.ready_port(process, log)), timeout=1
                ) as sock:
                    sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
                    read_raw(sock)
                    started = time.monotonic()
                    sock.sendall(raw_frame(4, 2, SHORT_BODY))
                    if fault == "late":
                        sock.sendall(raw_frame(0x15, 3))
                        self.assertEqual((0x80000015, 0, 3, b""), read_raw(sock))
                    self.assertEqual((0x80000004, 0, 2, b"id\0"), read_raw(sock))
                    if fault == "duplicate":
                        self.assertEqual((0x80000004, 0, 2, b"id\0"), read_raw(sock))
                    if fault == "late":
                        self.assertGreaterEqual(time.monotonic() - started, 0.14)
                    sock.sendall(raw_frame(6, 4))
                    read_raw(sock)
                result = self.result(process, log, report)
                self.assertEqual(
                    (1, 1, 1),
                    (
                        result["expected_injections"],
                        result["counts"]["injections_attempted"],
                        result["counts"]["injections_completed"],
                    ),
                )
                self.assertEqual(0, result["retention"]["final_held_count"])
                self.assertEqual(
                    2 if fault == "duplicate" else 1,
                    result["counts"]["ack_frames_written"],
                )
                if fault == "fragmented":
                    self.assertGreaterEqual(result["counts"]["fragments_written"], 2)

    def test_missing_ack_still_services_controls_and_retains_no_message(self):
        process, log, report = self.start_tool("server", "--port=0", "--fault=missing")
        with socket.create_connection(
            ("127.0.0.1", self.ready_port(process, log)), timeout=1
        ) as sock:
            sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
            read_raw(sock)
            sock.sendall(raw_frame(4, 2, SHORT_BODY))
            sock.settimeout(0.04)
            with self.assertRaises(TimeoutError):
                read_raw(sock)
            sock.settimeout(1)
            sock.sendall(raw_frame(0x15, 3))
            self.assertEqual((0x80000015, 0, 3, b""), read_raw(sock))
            sock.sendall(raw_frame(6, 4))
            read_raw(sock)
        result = self.result(process, log, report)
        self.assertEqual(1, result["counts"]["missing_acknowledgements"])
        self.assertEqual(0, result["retention"]["ordinary_count_peak"])

    def test_terminal_faults_act_after_the_configured_message_count(self):
        for fault in ("disconnect", "malformed"):
            with self.subTest(fault=fault):
                process, log, report = self.start_tool(
                    "server", "--port=0", "--fault=" + fault, "--count=2"
                )
                with socket.create_connection(
                    ("127.0.0.1", self.ready_port(process, log)), timeout=1
                ) as sock:
                    sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
                    read_raw(sock)
                    sock.sendall(raw_frame(4, 2, SHORT_BODY))
                    read_raw(sock)
                    sock.sendall(raw_frame(4, 3, SHORT_BODY))
                    if fault == "disconnect":
                        self.assertEqual(b"", sock.recv(1))
                    else:
                        header = read_malformed_header(sock)
                        self.assertEqual(
                            bytes.fromhex("0000000f800000040000000000000003"), header
                        )
                result = self.result(process, log, report)
                self.assertEqual(2, result["counts"]["selected_messages"])
                self.assertEqual(1, result["counts"]["injections_completed"])

    def test_slow_reader_paces_post_bind_bytes_without_a_worker_queue(self):
        process, log, report = self.start_tool(
            "server",
            "--port=0",
            "--fault=slow-reader",
            "--duration=1",
            "--delay=.1",
            "--read-bytes=8",
            "--read-interval=.02",
        )
        with socket.create_connection(
            ("127.0.0.1", self.ready_port(process, log)), timeout=1
        ) as sock:
            sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
            read_raw(sock)
            sock.sendall(raw_frame(4, 2, SHORT_BODY))
            sock.settimeout(0.04)
            with self.assertRaises(TimeoutError):
                read_raw(sock)
            sock.settimeout(1)
            self.assertEqual((0x80000004, 0, 2, b"id\0"), read_raw(sock))
            sock.sendall(raw_frame(6, 3))
            read_raw(sock)
        result = self.result(process, log, report)
        self.assertEqual(1, result["counts"]["slow_read_sessions"])
        self.assertEqual(1, result["counts"]["injections_completed"])

    def test_exhausted_count_or_byte_capacity_releases_every_attempted_fault(self):
        for bound in ("--held-count=1", "--held-bytes=19"):
            with self.subTest(bound=bound):
                process, log, report = self.start_tool(
                    "server",
                    "--port=0",
                    "--fault=late",
                    "--count=2",
                    "--delay=.3",
                    bound,
                )
                with socket.create_connection(
                    ("127.0.0.1", self.ready_port(process, log)), timeout=1
                ) as sock:
                    sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
                    read_raw(sock)
                    sock.sendall(
                        raw_frame(4, 2, SHORT_BODY) + raw_frame(4, 3, SHORT_BODY)
                    )
                    self.assertEqual(b"", sock.recv(1))
                result = self.result(process, log, report, expected=1)
                counts = result["counts"]
                self.assertEqual(
                    (2, 0, 2, 1),
                    (
                        counts["injections_attempted"],
                        counts["injections_completed"],
                        counts["injections_abandoned"],
                        counts["queue_rejections"],
                    ),
                )
                self.assertEqual(
                    (1, 19, 0, 0),
                    tuple(
                        result["retention"][key]
                        for key in (
                            "ordinary_count_peak",
                            "ordinary_bytes_peak",
                            "final_held_count",
                            "final_held_bytes",
                        )
                    ),
                )
                self.assertTrue(result["cleanup_complete"])

    def test_incomplete_frames_expire_or_fail_at_eof_with_bounded_cleanup(self):
        for eof in (False, True):
            with self.subTest(eof=eof):
                process, log, report = self.start_tool(
                    "server", "--port=0", "--timeout=.15"
                )
                with socket.create_connection(
                    ("127.0.0.1", self.ready_port(process, log)), timeout=1
                ) as sock:
                    sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
                    read_raw(sock)
                    sock.sendall(raw_frame(4, 2, SHORT_BODY)[:-1])
                    if eof:
                        sock.shutdown(socket.SHUT_WR)
                    self.assertEqual(b"", sock.recv(1))
                result = self.result(process, log, report, expected=1)
                self.assertEqual(
                    1, result["counts"]["invalid_frames" if eof else "frame_timeouts"]
                )
                self.assertTrue(result["cleanup_complete"])

    def test_inflight_fragment_deadline_keeps_bytes_reserved_until_close(self):
        process, log, report = self.start_tool(
            "server",
            "--port=0",
            "--fault=fragmented",
            "--fragment-bytes=1",
            "--fragment-gap=.05",
            "--timeout=.15",
        )
        with socket.create_connection(
            ("127.0.0.1", self.ready_port(process, log)), timeout=1
        ) as sock:
            sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
            read_raw(sock)
            sock.sendall(raw_frame(4, 2, SHORT_BODY))
            received = bytearray()
            while part := sock.recv(32):
                received.extend(part)
            self.assertGreater(len(received), 0)
            self.assertLess(len(received), 19)
        result = self.result(process, log, report, expected=1)
        self.assertEqual(
            (1, 1, 0),
            tuple(
                result["counts"][key]
                for key in (
                    "write_timeouts",
                    "injections_abandoned",
                    "injections_completed",
                )
            ),
        )
        self.assertEqual(19, result["retention"]["ordinary_bytes_peak"])
        self.assertEqual(0, result["retention"]["final_held_bytes"])

    def test_connect_refusal_and_wrong_bind_status_or_sequence_are_reported(self):
        with socket.socket() as unused:
            unused.bind(("127.0.0.1", 0))
            port = unused.getsockname()[1]
        process, log, report = self.start_tool("client", "--port=" + str(port))
        result = self.result(process, log, report, expected=1)
        self.assertEqual(1, result["counts"]["connect_failures"])
        self.assertTrue(result["cleanup_complete"])
        for status, sequence in ((0, 2), (13, 1)):
            with self.subTest(
                status=status, sequence=sequence
            ), socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                listener.listen(1)
                listener.settimeout(2)
                process, log, report = self.start_tool(
                    "client", "--port=" + str(listener.getsockname()[1])
                )
                sock, _ = listener.accept()
                with sock:
                    sock.settimeout(1)
                    read_raw(sock)
                    sock.sendall(raw_frame(0x80000009, sequence, status=status))
                    self.assertEqual(b"", sock.recv(1))
                result = self.result(process, log, report, expected=1)
                self.assertEqual(1, result["counts"]["invalid_frames"])
                self.assertTrue(result["cleanup_complete"])

    def test_signal_shutdown_settles_queued_faults_and_writes_a_final_report(self):
        process, log, report = self.start_tool(
            "server", "--port=0", "--fault=late", "--delay=.4"
        )
        with socket.create_connection(
            ("127.0.0.1", self.ready_port(process, log)), timeout=1
        ) as sock:
            sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
            read_raw(sock)
            sock.sendall(raw_frame(4, 2, SHORT_BODY) + raw_frame(0x15, 3))
            self.assertEqual((0x80000015, 0, 3, b""), read_raw(sock))
            process.terminate()
            result = self.result(process, log, report, expected=1)
            self.assertEqual(b"", sock.recv(1))
        self.assertEqual(
            (1, 0, 1),
            tuple(
                result["counts"][key]
                for key in (
                    "injections_attempted",
                    "injections_completed",
                    "injections_abandoned",
                )
            ),
        )
        self.assertEqual(1, result["counts"]["interruptions"])
        self.assertTrue(result["cleanup_complete"])

    def test_a_new_partial_frame_gets_its_own_total_read_deadline(self):
        process, log, report = self.start_tool(
            "server", "--port=0", "--timeout=.4", "--duration=1", "--count=2"
        )
        with socket.create_connection(
            ("127.0.0.1", self.ready_port(process, log)), timeout=1
        ) as sock:
            sock.sendall(raw_frame(2, 1, b"sim\0sim\0\0\x34\0\0\0"))
            read_raw(sock)
            first, second = raw_frame(4, 2, SHORT_BODY), raw_frame(4, 3, SHORT_BODY)
            sock.sendall(first[:6])
            time.sleep(0.25)
            sock.sendall(first[6:] + second[:6])
            self.assertEqual((0x80000004, 0, 2, b"id\0"), read_raw(sock))
            time.sleep(0.2)
            sock.sendall(second[6:])
            self.assertEqual((0x80000004, 0, 3, b"id\0"), read_raw(sock))
            sock.sendall(raw_frame(6, 4))
            read_raw(sock)
        result = self.result(process, log, report)
        self.assertEqual(0, result["counts"]["frame_timeouts"])


@unittest.skipUnless(
    os.environ.get("SMPP_SIMULATOR_LAUNCHER"),
    "Set SMPP_SIMULATOR_LAUNCHER to an installed project simulator",
)
class SimulatorProcessTests(ProcessHarness, unittest.TestCase):
    def start_simulator(self, role, version, operation, port):
        launcher = Path(os.environ["SMPP_SIMULATOR_LAUNCHER"]).resolve()
        revision = os.environ["SMPP_SIMULATOR_REVISION"]
        self.serial += 1
        report = self.directory / ("target-report-" + str(self.serial))
        log = self.directory / ("target-" + str(self.serial) + ".log")
        output = log.open("w")
        command = [
            str(launcher),
            role,
            "--port=" + str(port),
            "--version=" + version,
            "--bind=trx",
            "--operation=" + operation,
            "--connections=1",
            "--window=8",
            "--count=4",
            "--rates=8",
            "--duration=PT0.8S",
            "--warmup=PT0S",
            "--timeout=PT0.12S",
            "--drain=PT0.5S",
            "--payload=16",
            "--expect-failures=true",
            "--minimum-rate-ratio=0",
            "--revision=" + revision,
            "--report=" + str(report),
        ]
        identity = {
            str(path): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in [
                launcher,
                *sorted(launcher.parent.parent.joinpath("lib").glob("*.jar")),
            ]
        }
        (self.directory / ("input-" + str(self.serial) + ".json")).write_text(
            json.dumps(
                {"command": command, "sourceRevision": revision, "sha256": identity},
                indent=2,
            )
            + "\n"
        )
        process = subprocess.Popen(
            command,
            stdout=output,
            stderr=subprocess.STDOUT,
            env=dict(
                os.environ,
                SMPP_SYSTEM_ID="sim",
                SMPP_PASSWORD="sim",
                JAVA_OPTS="-Xms64m -Xmx192m",
            ),
        )
        self.addCleanup(stop_process, process, output)
        return process, log, report

    def exercise_pair(self, role, version, operation, fault):
        peer_options = [
            "--version=" + version,
            "--operation=" + operation,
            "--fault=" + fault,
            "--timeout=3",
            "--duration=2",
            "--delay=.25",
            "--peer-timeout=.12",
            "--fragment-gap=.003",
            "--read-bytes=16",
            "--read-interval=.01",
        ]
        if role == "client":
            raw = self.start_tool("server", "--port=0", *peer_options)
            port = self.ready_port(raw[0], raw[1])
            target = self.start_simulator(role, version, operation, port)
        else:
            target = self.start_simulator(role, version, operation, 0)
            port = self.ready_port(target[0], target[1])
            raw = self.start_tool("client", "--port=" + str(port), *peer_options)
        target_exit = target[0].wait(8)
        self.assertTrue((target[2] / "report.json").is_file(), target[1].read_text())
        target_report = json.loads((target[2] / "report.json").read_text())
        result = self.result(*raw)
        measurement = target_report["traffic"]["measurement"]
        observations = {
            key: measurement[key]
            for key in ("attempted", "admitted", "rejected", "pending", "outcomes")
        }
        print(
            "PAIR "
            + json.dumps(
                {
                    "role": role,
                    "version": version,
                    "operation": operation,
                    "fault": fault,
                    "directory": str(self.directory),
                    "targetExit": target_exit,
                    "targetFailures": target_report["failures"],
                    "measurement": observations,
                    "rawCounts": result["counts"],
                },
                sort_keys=True,
            ),
            flush=True,
        )
        self.assertTrue(target_report["cleanupComplete"])
        self.assertEqual(0, measurement["pending"])
        self.assertEqual(4, measurement["attempted"])
        self.assertEqual(0, target_exit, target[1].read_text())
        self.assertEqual(0, result["retention"]["final_held_count"])
        if fault in ("none", "duplicate", "fragmented"):
            self.assertEqual(4, measurement["outcomes"]["SUCCESS"])
        else:
            self.assertLess(measurement["outcomes"].get("SUCCESS", 0), 4)
        expected = 0 if fault == "none" else 1
        self.assertEqual(expected, result["counts"]["injections_completed"])
        self.assertEqual(expected, result["counts"]["injections_attempted"])
        self.assertEqual(0, result["counts"]["injections_abandoned"])

    def test_installed_simulator_all_faults_in_both_roles_and_profiles(self):
        for version in ("3.4", "5.0"):
            for role, operation in (("client", "submit"), ("server", "deliver")):
                for fault in (
                    "duplicate",
                    "late",
                    "missing",
                    "malformed",
                    "fragmented",
                    "disconnect",
                    "slow-reader",
                ):
                    with self.subTest(version=version, role=role, fault=fault):
                        self.exercise_pair(role, version, operation, fault)

    def test_installed_simulator_data_in_both_roles_and_profiles(self):
        for version in ("3.4", "5.0"):
            for role in ("client", "server"):
                for fault in (
                    "duplicate",
                    "late",
                    "missing",
                    "malformed",
                    "fragmented",
                    "disconnect",
                    "slow-reader",
                ):
                    with self.subTest(version=version, role=role, fault=fault):
                        self.exercise_pair(role, version, "data", fault)


if __name__ == "__main__":
    unittest.main()
