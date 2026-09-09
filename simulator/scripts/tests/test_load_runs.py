import importlib.util
from pathlib import Path
import unittest
import sys
import tempfile
import hashlib
import json
import copy


SPEC = importlib.util.spec_from_file_location("load_runs", Path(__file__).parents[1] / "load_runs.py")
load_runs = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(load_runs)


class LoadRunsTest(unittest.TestCase):
    def test_fresh_campaign_retains_failed_reports_and_complete_process_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            launcher = root / "peer"
            launcher.write_text("#!/usr/bin/env python3\nimport sys,pathlib,json\n"
                                "options=dict(arg[2:].split('=',1) for arg in sys.argv[2:])\n"
                                "path=pathlib.Path(options['report']);path.mkdir(parents=True)\n"
                                "path.joinpath('report.json').write_text(json.dumps({'passed':False,'cleanupComplete':True}))\n"
                                "print('READY port=12345',flush=True)\n")
            launcher.chmod(0o700)
            (root / "build.gradle").write_text("// fixture build input")
            output = root / "campaign"
            result = load_runs.main(["run", "--profile", "W-SMOKE", "--version", "3.4",
                                     "--direction", "submit", "--revision", "0" * 40,
                                     "--source-root", str(root), "--launcher", str(launcher),
                                     "--output", str(output), "--diagnostic-seconds", "0.1"])
            self.assertEqual(1, result)
            manifest = json.loads((output / "campaign.json").read_text())
            self.assertEqual("W-SMOKE-diagnostic", manifest["profile"]["name"])
            self.assertEqual(1, manifest["profile"]["count"])
            self.assertTrue(manifest["runs"][0]["cleanupComplete"])
            self.assertEqual(2, len(manifest["runs"][0]["commands"]))
            self.assertEqual(1, len(manifest["sourceInputs"]["files"]))
            self.assertTrue((output / "repeat-001/client/report.json").exists())
            self.assertFalse(manifest["passed"])

    def test_aggregation_reconciles_repeats_and_rejects_incompatible_executables(self):
        histogram = {"count": 1, "overflow": 0, "maximumNanos": 1000,
                     "buckets": [{"upperMicros": 1, "count": 1}]}
        cohort = {"planned": 1, "skipped": 0, "attempted": 1, "rejected": 0, "admitted": 1,
                  "pending": 0, "peakPending": 1, "completionsDuringMeasurement": 1,
                  "successesDuringMeasurement": 1, "mayHaveBeenSent": 1, "excessStatuses": 0,
                  "outcomes": {"SUCCESS": 1}, "rejections": {}, "statuses": {"0": 1},
                  "invocationLatency": histogram, "scheduledLatency": histogram, "schedulingLag": histogram}
        for corruption in ({"outcomes": {"PEER_NEGATIVE": 1}}, {"completionsDuringMeasurement": 2},
                           {"completionsDuringMeasurement": 0}):
            with self.subTest(corruption=corruption), self.assertRaises(ValueError):
                load_runs.validate_cohort(dict(cohort, **corruption))
        report = {"schema": 2, "runId": "first", "configuration": {"mode": "CLIENT", "port": 1},
                  "suppliedSourceRevision": "0" * 40, "histogramSettings": {"unit": "microseconds"},
                  "environment": {"executableSha256": {"tool": "0" * 64}},
                  "resources": {"sampledPeakHeapBytes": 42, "unknownBlob": "x" * 10_000},
                  "traffic": {"measurement": cohort, "warmup": cohort, "measurementNanos": 1000000000,
                              "measurementStarted": True}, "cleanupComplete": True, "passed": True}
        with tempfile.TemporaryDirectory() as temporary:
            paths = [Path(temporary) / name for name in ("one.json", "two.json")]
            paths[0].write_text(json.dumps(report))
            second = copy.deepcopy(report)
            second["runId"], second["configuration"]["port"] = "second", 2
            paths[1].write_text(json.dumps(second))
            merged = load_runs.aggregate_reports(paths)
            self.assertEqual(2, merged["runs"])
            self.assertEqual(2, merged["measurement"]["admitted"])
            self.assertEqual(2, merged["measurement"]["scheduledLatency"]["count"])
            self.assertEqual(1, merged["successfulCompletionsPerSecond"])
            self.assertEqual(False, merged["individualRuns"][0].get("intervalAssessment", {}).get("applicable"))
            self.assertEqual({"sampledPeakHeapBytes": 42}, merged["individualRuns"][0]["resources"])
            second["environment"]["executableSha256"]["tool"] = "1" * 64
            paths[1].write_text(json.dumps(second))
            with self.assertRaisesRegex(ValueError, "compatible"):
                load_runs.aggregate_reports(paths)

    def test_recovery_uses_the_first_exact_thirty_second_cohort_and_original_counts(self):
        def interval(rate, offset, seconds, successes, p99):
            planned = rate * seconds
            histogram = {"count": successes, "overflow": 0, "maximumNanos": p99 * 1000,
                         "buckets": [{"upperMicros": p99, "count": successes}]}
            metrics = {"planned": planned, "skipped": planned - successes, "attempted": successes,
                       "rejected": 0, "admitted": successes, "pending": 0, "peakPending": 1,
                       "completionsDuringMeasurement": successes, "successesDuringMeasurement": successes,
                       "mayHaveBeenSent": successes, "excessStatuses": 0, "outcomes": {"SUCCESS": successes},
                       "rejections": {}, "statuses": {"0": successes}, "invocationLatency": histogram,
                       "scheduledLatency": histogram, "schedulingLag": histogram}
            return {"rate": rate, "startOffsetNanos": offset * 10**9, "durationNanos": seconds * 10**9,
                    "metrics": metrics}
        intervals = [interval(1000, 0, 30, 30_000, 1000), interval(20_000, 30, 10, 100_000, 5000),
                     interval(1000, 40, 30, 30_000, 2000), interval(1000, 70, 50, 50_000, 1000)]
        report = {"configuration": {"operation": "submit", "model": "ARRIVAL_RATE",
                  "rates": [1000, 20_000, 1000, 1000], "rateHoldNanos": [30 * 10**9, 10 * 10**9, 30 * 10**9, 50 * 10**9]},
                  "traffic": {"intervals": intervals, "measurementStarted": True, "measurementNanos": 120 * 10**9}}
        result = load_runs.assess_intervals(report)
        self.assertEqual(1, result["firstUnmetLevel"]["index"])
        self.assertTrue(result["recovery"]["passed"])
        self.assertEqual(2, result["recovery"]["index"])
        intervals[2] = interval(1000, 40, 30, 29_999, 2001)
        result = load_runs.assess_intervals(report)
        self.assertFalse(result["recovery"]["passed"])
        self.assertIn("generator-skipped-arrivals", result["recovery"]["failures"])
        self.assertIn("scheduled-p99-above-twice-baseline", result["recovery"]["failures"])
        intervals[2]["startOffsetNanos"] += 1
        with self.assertRaisesRegex(ValueError, "contiguous"):
            load_runs.assess_intervals(report)

    def test_source_manifest_names_each_build_input_and_uses_a_reproducible_digest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name, value in (("src/main/java/a/A.java", "source"), ("build.gradle", "build"),
                                ("simulator/scripts/load_runs.py", "script"),
                                ("simulator/build/cache.class", "ignored"),
                                ("src/test/java/Test.java", "test")):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(value)
            manifest = load_runs.source_manifest(root)
            expected = ["build.gradle", "simulator/scripts/load_runs.py", "src/main/java/a/A.java"]
            self.assertEqual(expected, [entry["path"] for entry in manifest["files"]])
            digest = hashlib.sha256(b"smpp-simulator-inputs-v1\n")
            for entry in manifest["files"]:
                digest.update(entry["path"].encode() + b"\0" + entry["sha256"].encode() + b"\n")
            self.assertEqual(digest.hexdigest(), manifest["sha256"])
            (root / "src/main/java/a/A.java").write_text("changed")
            self.assertNotEqual(manifest["sha256"], load_runs.source_manifest(root)["sha256"])

    def test_histogram_overflow_is_a_separate_population_from_recorded_buckets(self):
        merged = load_runs.merge_histograms([
            {"count": 0, "overflow": 2, "maximumNanos": 3_600_000_000_001, "buckets": []}])
        self.assertEqual(0, merged["count"])
        self.assertEqual(2, merged["overflow"])
        self.assertEqual(0, merged["p99Micros"])

    def test_histogram_aggregation_weights_counts_instead_of_averaging_percentiles(self):
        merged = load_runs.merge_histograms([
            {"count": 1, "overflow": 1, "maximumNanos": 3_600_000_000_001,
             "buckets": [{"upperMicros": 1, "count": 1}]},
            {"count": 99, "overflow": 0, "maximumNanos": 100_000,
             "buckets": [{"upperMicros": 100, "count": 99}]},
        ])
        self.assertEqual(100, merged["count"])
        self.assertEqual(1, merged["overflow"])
        self.assertEqual(100, merged["p50Micros"])
        self.assertEqual(100, merged["p99Micros"])
        self.assertEqual(3_600_000_000_001, merged["maximumNanos"])
        self.assertEqual(2, len(merged["buckets"]))

    def test_real_child_timeout_stops_both_owners_and_reports_partial_execution(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            child = root / "waiting.py"
            child.write_text("import pathlib,sys,time\n"
                             "mode=sys.argv[1]\n"
                             "pathlib.Path(__file__).with_name(mode+'.ready').write_text('ready')\n"
                             "print('READY port=12345',flush=True)\n"
                             "time.sleep(30)\n")
            result = load_runs.run_pair([sys.executable, str(child)], {}, {}, root / "run", 1)
            self.assertTrue(result["timedOut"])
            self.assertTrue(result["cleanupComplete"])
            self.assertTrue((root / "server.ready").exists())
            self.assertTrue((root / "client.ready").exists())
            self.assertNotEqual(0, result["exitCodes"]["server"])
            self.assertNotEqual(0, result["exitCodes"]["client"])

    def test_bidirectional_soak_divides_true_originating_counts_between_two_jvms(self):
        server, client = load_runs.build_options(load_runs.profile("W-SOAK"), "5.0", "data",
                                               "0" * 40, Path("output"), 1)
        self.assertEqual("2500", client.get("rates"))
        self.assertEqual("2500", server["rates"])
        self.assertEqual("9000000", client["count"])
        self.assertEqual("9000000", server["count"])
        self.assertEqual("data", client["operation"])
        self.assertEqual("data", server["operation"])
        self.assertEqual("PT3600S", client["duration"])
        self.assertEqual("0.99", client["minimum-rate-ratio"])
        self.assertEqual("100", server["connections"])
        self.assertEqual("0", server["port"])
        self.assertNotIn("port", client)
        closed_server, closed_client = load_runs.build_options(load_runs.profile("W-WINDOW"), "3.4", "deliver",
                                                             "0" * 40, Path("output"), 2)
        self.assertEqual("concurrency", closed_server["model"])
        self.assertNotIn("rates", closed_server)
        self.assertNotIn("holds", closed_server)
        self.assertEqual("none", closed_client["operation"])

    def test_full_soak_and_three_unequal_bursts_keep_the_published_targets(self):
        soak = load_runs.profile("W-SOAK")
        self.assertEqual(100, soak.get("connections"))
        self.assertEqual(60, soak["warmupSeconds"])
        self.assertEqual([3600], soak["holdSeconds"])
        self.assertEqual([5000], soak["rates"])
        self.assertEqual(18_000_000, soak["count"])
        burst = load_runs.profile("W-BURST")
        self.assertEqual([30, 10, 20, 10, 20, 10, 30, 50], burst["holdSeconds"])
        self.assertEqual([1000, 20000, 1000, 20000, 1000, 20000, 1000, 1000], burst["rates"])
        self.assertEqual(750_000, burst["count"])
        ramp = load_runs.profile("W-RAMP")
        self.assertEqual([60, 60, 60, 60, 30, 30], ramp["holdSeconds"])
        self.assertEqual([1000, 5000, 10000, 20000, 1000, 1000], ramp["rates"])


if __name__ == "__main__":
    unittest.main()
