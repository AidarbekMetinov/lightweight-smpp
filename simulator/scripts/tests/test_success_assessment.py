"""Independent finite report fixtures for the additive healthy planned-success policy."""

import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("load_runs", Path(__file__).parents[1] / "load_runs.py")
load_runs = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(load_runs)


def cohort(planned, successes, in_phase=None):
    if in_phase is None:
        in_phase = successes
    histogram = {"count": successes, "overflow": 0, "maximumNanos": 1_000_000,
                 "buckets": [{"upperMicros": 1000, "count": successes}] if successes else []}
    return {"planned": planned, "skipped": planned - successes, "attempted": successes,
            "rejected": 0, "admitted": successes, "pending": 0, "peakPending": min(1, successes),
            "completionsDuringMeasurement": in_phase, "successesDuringMeasurement": in_phase,
            "mayHaveBeenSent": successes, "excessStatuses": 0, "outcomes": {"SUCCESS": successes},
            "rejections": {}, "statuses": {"0": successes} if successes else {},
            "invocationLatency": copy.deepcopy(histogram), "scheduledLatency": copy.deepcopy(histogram),
            "schedulingLag": copy.deepcopy(histogram)}


def healthy_report(successes=100, in_phase=None, elapsed=1_000_000_000):
    observation = {"physicalConnections": 0, "observedSessions": 1, "pendingRequests": 0,
                   "pendingRequestBytes": 0, "pendingReplies": 0, "retainedReplyBytes": 0,
                   "pendingDecisions": 0, "retainedStreams": 0}
    resource = {"heapBytes": 10, "committedHeapBytes": 20, "rssBytes": 30, "peakRssBytes": 40,
                "cpuNanos": 50, "gcMillis": 0, "fileDescriptors": 6, "platformThreads": 2}
    return {"schema": 2, "runId": "independent-test", "passed": successes == 100,
            "configuration": {"operation": "submit", "model": "ARRIVAL_RATE", "rates": [100],
                              "rateHoldNanos": [1_000_000_000], "count": 100,
                              "measurementNanos": 1_000_000_000, "warmupNanos": 0, "drainNanos": 10,
                              "connections": 1, "window": 32, "payloadBytes": 160,
                              "faults": {"rejectPercent": 0, "delayPercent": 0, "stallPercent": 0,
                                         "disconnectAfter": 0},
                              "loadSettings": {"scenario": "W-BASE", "maximumRssBytes": 1024,
                                               "maximumHeapBytes": 1024, "maximumDescriptorGrowth": 0,
                                               "maximumThreadGrowth": 0, "churnRate": 0,
                                               "churnCount": 0, "consumerDelayNanos": 0}},
            "criteria": {"expectFailures": False, "minimumSuccessfulRateRatio": .99,
                         "maximumScheduledP99Millis": 100},
            "traffic": {"measurementStarted": True, "measurement": cohort(100, successes, in_phase),
                        "warmup": cohort(0, 0), "measurementNanos": elapsed, "warmupNanos": 0,
                        "drainNanos": 10, "failures": []},
            "cleanupComplete": True, "samplingTerminated": True,
            "receiver": {"received": 0, "accepted": 0, "rejected": 0, "delayed": 0, "stalled": 0,
                         "disconnected": 0, "invalidContent": 0, "capacityRejected": 0,
                         "pendingDecisions": 0, "retainedStreams": 0, "incompleteAssemblies": 0,
                         "cancelledBeforeDecision": 0, "streamCapacityRejected": 0},
            "resources": {"baselineRecorded": True, "samples": 2, "baseline": copy.deepcopy(resource),
                          "latest": copy.deepcopy(resource), "sampledPeakHeapBytes": 10,
                          "sampledPeakRssBytes": 30, "sampledPeakFileDescriptors": 6,
                          "sampledPeakPlatformThreads": 2},
            "pressure": {"baselineRecorded": True, "samples": 2, "baseline": copy.deepcopy(observation),
                         "latest": copy.deepcopy(observation), "sampledPeaks": copy.deepcopy(observation)},
            "connections": {"initialBound": 1, "replacementStarted": 0, "replacementBound": 0,
                            "replacementFailures": 0, "replacementAborted": 0, "currentConnections": 0,
                            "peakConnections": 1},
            "churn": None, "reconnect": {"loops": 0, "attempts": 0, "publishedSessions": 0,
                                         "unfinishedLoops": 0, "terminalReasons": {}},
            "failures": [] if successes == 100 else ["unsuccessful-work"]}


class PlannedSuccessTests(unittest.TestCase):
    def test_one_percent_skips_meet_the_new_floor_without_changing_legacy_failure(self):
        report = healthy_report(99)
        original = copy.deepcopy(report)
        result = load_runs.assess_planned_success(report)
        self.assertTrue(result["passed"])
        self.assertEqual(.99, result["eventualSuccessPerPlanned"])
        self.assertEqual(.99, result["inMeasurementSuccessPerPlanned"])
        self.assertEqual(.99, result["inMeasurementConfiguredRateRatio"])
        self.assertEqual(100, result["planned"])
        self.assertFalse(report["passed"])
        self.assertEqual(original, report)

    def test_elapsed_phase_and_drain_successes_cannot_mask_the_in_phase_rate_floor(self):
        for report in (healthy_report(98), healthy_report(99, elapsed=1_020_000_000),
                       healthy_report(100, elapsed=1_020_000_000),
                       healthy_report(99, elapsed=1_000_000_001),
                       healthy_report(100, in_phase=98), healthy_report(100, elapsed=500_000_000)):
            with self.subTest(report=report["traffic"]["measurement"]):
                self.assertFalse(load_runs.assess_planned_success(report)["passed"])
        result = load_runs.assess_planned_success(healthy_report(100, in_phase=99))
        self.assertTrue(result["passed"])
        self.assertEqual(1, result["eventualSuccessPerPlanned"])
        self.assertEqual(.99, result["inMeasurementSuccessPerPlanned"])

    def test_receiving_expected_fault_and_concurrency_roles_are_explicitly_not_applicable(self):
        for path, value, reason in (("operation", "none", "receive-only"),
                                    ("model", "FIXED_CONCURRENCY", "no-independent-arrival-plan")):
            report = healthy_report()
            report["configuration"][path] = value
            result = load_runs.assess_planned_success(report)
            self.assertFalse(result["applicable"])
            self.assertIsNone(result["passed"])
            self.assertEqual(reason, result["reason"])
        report = healthy_report()
        report["criteria"]["expectFailures"] = True
        self.assertEqual("expected-fault-role", load_runs.assess_planned_success(report)["reason"])
        report = healthy_report()
        report["configuration"]["loadSettings"]["scenario"] = "W-FAULT"
        self.assertEqual("outside-healthy-profiles", load_runs.assess_planned_success(report)["reason"])
        report["configuration"]["loadSettings"]["scenario"] = "W-SOAK-diagnostic"
        self.assertTrue(load_runs.assess_planned_success(report)["diagnostic"])

    def test_a_hidden_admitted_failure_or_local_rejection_cannot_spend_the_skip_budget(self):
        for outcome in ("LOCAL_FAILURE", "PEER_NEGATIVE", "PEER_NACK"):
            report = healthy_report(100, in_phase=99)
            measured = report["traffic"]["measurement"]
            measured["outcomes"] = {"SUCCESS": 99, outcome: 1}
            measured["statuses"] = {"0": 99}
            with self.subTest(outcome=outcome):
                load_runs.validate_cohort(measured)
                result = load_runs.assess_planned_success(report)
                self.assertFalse(result["passed"])
                self.assertIn("measurement-non-success-outcomes", result["failures"])
        report = healthy_report(99)
        measured = report["traffic"]["measurement"]
        measured.update({"skipped": 0, "attempted": 100, "rejected": 1,
                         "rejections": {"WINDOW_FULL": 1},
                         "schedulingLag": cohort(100, 100)["schedulingLag"]})
        load_runs.validate_cohort(measured)
        result = load_runs.assess_planned_success(report)
        self.assertFalse(result["passed"])
        self.assertIn("measurement-local-rejections", result["failures"])

    def test_cleanup_receiver_and_execution_guards_require_explicit_valid_evidence(self):
        corruptions = [
            (("cleanupComplete",), False), (("samplingTerminated",), False),
            (("traffic", "failures"), ["maintenance-failed"]),
            (("receiver", "invalidContent"), 1), (("receiver", "received"), 1),
            (("receiver", "pendingDecisions"), 1), (("receiver", "retainedStreams"), 1),
            (("receiver", "incompleteAssemblies"), 1),
            (("connections", "initialBound"), 0), (("connections", "currentConnections"), 1),
            (("reconnect", "unfinishedLoops"), 1),
            (("configuration", "faults", "stallPercent"), 1),
            (("configuration", "loadSettings", "consumerDelayNanos"), 1),
            (("configuration", "loadSettings", "churnCount"), 1),
            (("passed",), None), (("criteria", "expectFailures"), 0),
            (("failures",), ["hidden-timeout"]), (("traffic", "measurementStarted"), False),
        ]
        for path, value in corruptions:
            report = healthy_report()
            target = report
            for key in path[:-1]:
                target = target[key]
            target[path[-1]] = value
            with self.subTest(path=path):
                result = load_runs.assess_planned_success(report)
                self.assertFalse(result["passed"])
                self.assertTrue(result["failures"])
        for path in (("passed",), ("failures",), ("cleanupComplete",), ("samplingTerminated",),
                     ("criteria", "expectFailures"), ("traffic", "failures"),
                     ("receiver", "invalidContent"), ("connections", "initialBound"),
                     ("reconnect", "unfinishedLoops")):
            report = healthy_report()
            target = report
            for key in path[:-1]:
                target = target[key]
            del target[path[-1]]
            with self.subTest(missing=path):
                self.assertFalse(load_runs.assess_planned_success(report)["passed"])

    def test_warmup_failure_and_unfinished_measurement_are_hard_guards(self):
        report = healthy_report()
        warmup = cohort(100, 100, 99)
        warmup["outcomes"] = {"SUCCESS": 99, "LOCAL_FAILURE": 1}
        warmup["statuses"] = {"0": 99}
        report["traffic"]["warmup"] = warmup
        report["configuration"]["warmupNanos"] = 1_000_000_000
        report["traffic"]["warmupNanos"] = 1_000_000_000
        self.assertFalse(load_runs.assess_planned_success(report)["passed"])
        report = healthy_report(99)
        measured = report["traffic"]["measurement"]
        measured.update({"skipped": 0, "attempted": 100, "admitted": 100,
                         "outcomes": {"SUCCESS": 99, "UNFINISHED": 1},
                         "schedulingLag": cohort(100, 100)["schedulingLag"]})
        load_runs.validate_cohort(measured)
        self.assertFalse(load_runs.assess_planned_success(report)["passed"])

    def test_balanced_counts_must_still_match_the_declared_arrival_schedule(self):
        report = healthy_report()
        report["traffic"]["measurement"] = cohort(1, 1)
        load_runs.validate_cohort(report["traffic"]["measurement"])
        result = load_runs.assess_planned_success(report)
        self.assertFalse(result["passed"])
        self.assertIn("measurement-planned-does-not-match-declared-arrivals", result["failures"])
        for key, value in (("rates", [0]), ("rates", [100, 100]), ("rates", [True]),
                           ("rates", [1_000_001]), ("rateHoldNanos", [500_000_000]),
                           ("count", 0), ("measurementNanos", 0)):
            report = healthy_report()
            report["configuration"][key] = value
            with self.subTest(key=key, value=value):
                self.assertFalse(load_runs.assess_planned_success(report)["passed"])
        for planned in (0, -1, True, "100"):
            report = healthy_report()
            report["traffic"]["measurement"] = cohort(0, 0)
            report["traffic"]["measurement"]["planned"] = planned
            with self.subTest(planned=planned):
                self.assertFalse(load_runs.assess_planned_success(report)["passed"])

    def test_count_caps_and_fractional_step_arrivals_have_exact_independent_populations(self):
        report = healthy_report()
        report["configuration"]["count"] = 50
        report["traffic"]["measurement"] = cohort(50, 50)
        self.assertTrue(load_runs.assess_planned_success(report)["passed"])
        # Each 1.001ms step schedules an arrival at its own start; the total is two, not one.
        report["configuration"].update({"rates": [100, 100], "rateHoldNanos": [1_001_000, 1_001_000],
                                         "measurementNanos": 2_002_000})
        report["traffic"]["measurementNanos"] = 2_002_000
        report["traffic"]["measurement"] = cohort(2, 2)
        self.assertTrue(load_runs.assess_planned_success(report)["passed"])

    def test_original_warmup_and_receiver_drain_must_complete_without_refreshing_the_plan(self):
        report = healthy_report()
        report["configuration"]["warmupNanos"] = 1_000_000_000
        report["traffic"]["warmup"] = cohort(100, 100)
        report["traffic"]["warmupNanos"] = 1_000_000_000
        self.assertTrue(load_runs.assess_planned_success(report)["passed"])
        for field, value in (("warmupNanos", 999_999_999), ("drainNanos", 9)):
            incomplete = copy.deepcopy(report)
            incomplete["traffic"][field] = value
            with self.subTest(field=field):
                self.assertFalse(load_runs.assess_planned_success(incomplete)["passed"])
        report["traffic"]["warmup"] = cohort(1, 1)
        self.assertFalse(load_runs.assess_planned_success(report)["passed"])

    def test_enabled_latency_and_resource_guards_are_recomputed_from_observations(self):
        corruptions = [
            (("traffic", "measurement", "scheduledLatency", "buckets"),
             [{"upperMicros": 100_001, "count": 100}]),
            (("resources", "sampledPeakHeapBytes"), 1025),
            (("resources", "sampledPeakRssBytes"), 1025),
            (("resources", "latest", "peakRssBytes"), 1025),
            (("resources", "latest", "fileDescriptors"), 7),
            (("resources", "latest", "platformThreads"), 3),
            (("resources", "baseline", "fileDescriptors"), -1),
            (("resources", "sampledPeakHeapBytes"), -1),
            (("resources", "samples"), 0), (("resources", "baselineRecorded"), False),
            (("pressure", "sampledPeaks", "pendingRequests"), 33),
            (("pressure", "sampledPeaks", "pendingReplies"), 41),
            (("pressure", "sampledPeaks", "pendingRequestBytes"), 1_048_577),
            (("pressure", "sampledPeaks", "retainedReplyBytes"), 1_048_577),
            (("pressure", "latest", "pendingRequests"), 1),
            (("pressure", "samples"), 0), (("pressure", "baselineRecorded"), False),
            (("criteria", "maximumScheduledP99Millis"), float("nan")),
            (("criteria", "minimumSuccessfulRateRatio"), True),
        ]
        for path, value in corruptions:
            report = healthy_report()
            target = report
            for key in path[:-1]:
                target = target[key]
            target[path[-1]] = value
            with self.subTest(path=path):
                result = load_runs.assess_planned_success(report)
                self.assertFalse(result["passed"])
                self.assertTrue(result["failures"])
        for path in (("criteria", "maximumScheduledP99Millis"),
                     ("configuration", "loadSettings", "maximumRssBytes"),
                     ("resources", "sampledPeakRssBytes"), ("resources", "latest", "fileDescriptors"),
                     ("pressure", "latest", "pendingRequestBytes")):
            report = healthy_report()
            target = report
            for key in path[:-1]:
                target = target[key]
            del target[path[-1]]
            with self.subTest(missing=path):
                self.assertFalse(load_runs.assess_planned_success(report)["passed"])

    def test_guard_boundaries_and_explicitly_disabled_thresholds_are_reported(self):
        report = healthy_report()
        report["traffic"]["measurement"]["scheduledLatency"]["buckets"] = [
            {"upperMicros": 100_000, "count": 100}]
        report["resources"]["sampledPeakRssBytes"] = 1024
        report["resources"]["sampledPeakHeapBytes"] = 1024
        result = load_runs.assess_planned_success(report)
        self.assertTrue(result["passed"])
        self.assertEqual(1024, result["guardThresholds"]["maximumRssBytes"])
        report["criteria"]["maximumScheduledP99Millis"] = 0
        report["configuration"]["loadSettings"].update({"maximumRssBytes": 0, "maximumHeapBytes": 0,
                                                         "maximumDescriptorGrowth": -1,
                                                         "maximumThreadGrowth": -1})
        report["resources"]["latest"]["fileDescriptors"] = -1
        report["resources"]["baseline"]["fileDescriptors"] = -1
        result = load_runs.assess_planned_success(report)
        self.assertTrue(result["passed"])
        self.assertEqual(0, result["guardThresholds"]["maximumScheduledP99Millis"])
        self.assertEqual(-1, result["guardThresholds"]["maximumDescriptorGrowth"])

    def test_measurement_floor_discloses_warmup_skips_without_imposing_a_warmup_rate_floor(self):
        report = healthy_report()
        report["configuration"]["warmupNanos"] = 1_000_000_000
        report["traffic"]["warmup"] = cohort(100, 1)
        report["traffic"]["warmupNanos"] = 1_000_000_000
        report["passed"], report["failures"] = False, ["unsuccessful-work"]
        result = load_runs.assess_planned_success(report)
        self.assertTrue(result["passed"])
        self.assertEqual(99, result.get("warmupSkippedArrivals"))
        self.assertEqual(0, result["measurementSkippedArrivals"])

    def test_invalid_guard_values_never_escape_as_non_json_numbers_or_unchecked_categories(self):
        report = healthy_report()
        report["criteria"]["maximumScheduledP99Millis"] = float("nan")
        result = load_runs.assess_planned_success(report)
        self.assertFalse(result["passed"])
        self.assertNotIn("guardThresholds", result)
        json.dumps(result, allow_nan=False)
        report = healthy_report()
        report["reconnect"]["terminalReasons"] = {"CANCELLED": "1"}
        self.assertFalse(load_runs.assess_planned_success(report)["passed"])

    def test_an_out_of_range_json_integer_fails_closed_before_float_conversion(self):
        report = healthy_report()
        report["criteria"]["minimumSuccessfulRateRatio"] = 10**400
        result = load_runs.assess_planned_success(report)
        self.assertFalse(result["passed"])
        self.assertTrue(result["failures"])
        json.dumps(result, allow_nan=False)

    def test_invalid_operation_evidence_cannot_be_assessed_as_an_originating_role(self):
        for operation in (None, "", 1):
            report = healthy_report()
            report["configuration"]["operation"] = operation
            with self.subTest(operation=operation):
                self.assertFalse(load_runs.assess_planned_success(report)["passed"])

    def test_legacy_skip_and_rate_labels_must_be_explained_by_direct_counts(self):
        for report in (healthy_report(99), healthy_report()):
            report["passed"] = not report["passed"]
            report["failures"] = [] if report["passed"] else ["unsuccessful-work"]
            with self.subTest(skipped=report["traffic"]["measurement"]["skipped"]):
                self.assertFalse(load_runs.assess_planned_success(report)["passed"])
        report = healthy_report()
        report["passed"], report["failures"] = False, ["successful-rate-below-threshold"]
        self.assertFalse(load_runs.assess_planned_success(report)["passed"])
        report = healthy_report(99)
        report["criteria"]["minimumSuccessfulRateRatio"] = 1
        report["failures"].append("successful-rate-below-threshold")
        self.assertTrue(load_runs.assess_planned_success(report)["passed"])

    def test_an_inconsistent_resource_peak_cannot_hide_an_observed_budget_violation(self):
        for section, field in (("baseline", "heapBytes"), ("latest", "heapBytes"),
                               ("baseline", "rssBytes"), ("latest", "rssBytes")):
            report = healthy_report()
            report["resources"][section][field] = 1025
            with self.subTest(section=section, field=field):
                self.assertFalse(load_runs.assess_planned_success(report)["passed"])
        report = healthy_report()
        report["connections"]["peakConnections"] = 2
        self.assertFalse(load_runs.assess_planned_success(report)["passed"])

    def test_initial_samples_and_every_recorded_rss_high_water_contribute_to_budgets(self):
        for section, field, limit in (("initial", "heapBytes", "maximumHeapBytes"),
                                       ("initial", "rssBytes", "maximumRssBytes"),
                                       ("initial", "peakRssBytes", "maximumRssBytes"),
                                       ("baseline", "peakRssBytes", "maximumRssBytes"),
                                       ("latest", "peakRssBytes", "maximumRssBytes")):
            report = healthy_report()
            resources = report["resources"]
            resources["initial"] = copy.deepcopy(resources["baseline"])
            resources[section][field] = 1025
            if field == "peakRssBytes" and section != "latest":
                # Later Linux reads may be unavailable after a known high-water observation.
                resources["latest"][field] = -1
                if section == "initial":
                    resources["baseline"][field] = -1
            with self.subTest(section=section, field=field):
                result = load_runs.assess_planned_success(report)
                self.assertFalse(result["passed"])
                self.assertIn(limit + "-unavailable-or-exceeded", result["failures"])

    def test_initial_samples_require_peak_coverage_but_high_water_is_not_a_sampled_peak(self):
        for field, peak in (("heapBytes", "sampledPeakHeapBytes"), ("rssBytes", "sampledPeakRssBytes")):
            report = healthy_report()
            resources = report["resources"]
            resources["initial"] = copy.deepcopy(resources["baseline"])
            resources["initial"][field] = 1024
            with self.subTest(field=field):
                result = load_runs.assess_planned_success(report)
                self.assertFalse(result["passed"])
                self.assertIn(peak + "-does-not-cover-observations", result["failures"])
                resources[peak] = 1024
                self.assertTrue(load_runs.assess_planned_success(report)["passed"])
        report = healthy_report()
        resources = report["resources"]
        resources["initial"] = copy.deepcopy(resources["baseline"])
        resources["initial"]["peakRssBytes"] = 1024
        resources["baseline"]["peakRssBytes"] = resources["latest"]["peakRssBytes"] = -1
        self.assertEqual(30, resources["sampledPeakRssBytes"])
        self.assertTrue(load_runs.assess_planned_success(report)["passed"])

    def test_sequential_aggregation_adds_per_run_assessments_without_relaxing_legacy_passed(self):
        report = healthy_report(99)
        report.update({"suppliedSourceRevision": "0" * 40, "histogramSettings": {"unit": "microseconds"},
                       "environment": {"executableSha256": {"fixture": "0" * 64}}})
        with tempfile.TemporaryDirectory() as temporary:
            paths = [Path(temporary) / name for name in ("first.json", "second.json")]
            paths[0].write_text(json.dumps(report))
            report["runId"] = "independent-repeat"
            paths[1].write_text(json.dumps(report))
            result = load_runs.aggregate_reports(paths)
            self.assertFalse(result["passed"])
            self.assertEqual(200, result["measurement"]["planned"])
            self.assertEqual(198, result["measurement"]["outcomes"]["SUCCESS"])
            for run in result["individualRuns"]:
                self.assertFalse(run["passed"])
                self.assertTrue(run.get("healthyPlannedSuccess", {}).get("passed"))

    def test_campaign_persists_additive_assessments_for_originating_and_receive_only_roles(self):
        report = healthy_report(99)
        report.update({"suppliedSourceRevision": "0" * 40, "histogramSettings": {"unit": "microseconds"},
                       "environment": {"executableSha256": {"fixture": "0" * 64}}})
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "fixture.json").write_text(json.dumps(report))
            (root / "build.gradle").write_text("// bounded Python report fixture, no JVM")
            launcher = root / "peer"
            launcher.write_text(
                "#!/usr/bin/env python3\nimport json,pathlib,sys\n"
                "options=dict(arg[2:].split('=',1) for arg in sys.argv[2:])\n"
                "report=json.loads(pathlib.Path(__file__).with_name('fixture.json').read_text())\n"
                "report['runId']=options['run-id']\n"
                "report['configuration']['operation']=options['operation']\n"
                "report['configuration']['loadSettings']['scenario']=options['scenario']\n"
                "if options['operation']=='none':\n"
                " report['traffic']['measurement']=report['traffic']['warmup']\n"
                " report['passed']=True;report['failures']=[]\n"
                "path=pathlib.Path(options['report']);path.mkdir(parents=True)\n"
                "path.joinpath('report.json').write_text(json.dumps(report))\n"
                "print('READY port=12345',flush=True)\n"
                "sys.exit(0 if report['passed'] else 1)\n")
            launcher.chmod(0o700)
            output = root / "campaign"
            result = load_runs.main(["run", "--profile", "W-BASE", "--version", "3.4",
                                    "--direction", "submit", "--revision", "0" * 40,
                                    "--source-root", str(root), "--launcher", str(launcher),
                                    "--output", str(output), "--diagnostic-seconds", "1",
                                    "--warmup-seconds", "0", "--drain-seconds", "0.000000010",
                                    "--rate", "100", "--connections", "1"])
            self.assertEqual(1, result)
            campaign = json.loads((output / "campaign.json").read_text())
            self.assertFalse(campaign["passed"])
            run = campaign["runs"][0]
            self.assertFalse(run["passed"])
            self.assertTrue(run["cleanupComplete"])
            client = run["reports"]["client"]
            self.assertFalse(client["passed"])
            self.assertTrue(client.get("healthyPlannedSuccess", {}).get("passed"))
            self.assertEqual("receive-only", run["reports"]["server"]["healthyPlannedSuccess"]["reason"])


if __name__ == "__main__":
    unittest.main()
