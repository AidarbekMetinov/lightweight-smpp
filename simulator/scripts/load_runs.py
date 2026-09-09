#!/usr/bin/env python3
"""Fresh finite simulator campaigns and compatible, count-weighted report aggregation."""

import copy
from decimal import Decimal
from pathlib import Path
import subprocess
import time
import math
import hashlib
import json
import argparse
import re
import sys


def source_manifest(root):
    root = Path(root).resolve(strict=True)
    paths = set()
    for source in ("src/main/java", "simulator/src/main/java"):
        paths.update(path for path in (root / source).rglob("*.java") if path.is_file())
    paths.update(path for path in (root / "simulator/scripts").glob("*.py") if path.is_file())
    for name in ("build.gradle", "settings.gradle", "gradle.properties", "simulator/build.gradle",
                 "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
                 "gradle/wrapper/gradle-wrapper.properties"):
        if (root / name).is_file():
            paths.add(root / name)
    if not paths or len(paths) > 10000:
        raise ValueError("Source fingerprint requires 1..10000 declared inputs")
    digest, entries = hashlib.sha256(b"smpp-simulator-inputs-v1\n"), []
    for path in sorted(paths, key=lambda path: path.relative_to(root).as_posix()):
        if path.is_symlink() or path.stat().st_size > 32 * 1024 * 1024:
            raise ValueError("Source inputs must be regular, bounded files")
        name = path.relative_to(root).as_posix()
        value = hashlib.sha256(path.read_bytes()).hexdigest()
        entries.append({"path": name, "sha256": value, "bytes": path.stat().st_size})
        digest.update(name.encode("utf-8") + b"\0" + value.encode("ascii") + b"\n")
    return {"sha256": digest.hexdigest(), "files": entries,
            "algorithm": "SHA-256 per file; aggregate SHA-256 of UTF-8 smpp-simulator-inputs-v1 plus newline, followed by sorted UTF-8 relative path, NUL, lowercase ASCII file SHA-256, newline",
            "scope": "library/simulator production Java, root/simulator Gradle build and settings, wrapper scripts/config/JAR, direct simulator Python tools; tests, reports, caches and other outputs excluded"}


_PROFILES = {
    "W-SMOKE": (1, 0, [10], [10]),
    "W-BASE": (10, 30, [1000], [120]),
    "W-TARGET": (100, 30, [10000], [300]),
    "W-RAMP": (100, 30, [1000, 5000, 10000, 20000, 1000, 1000], [60, 60, 60, 60, 30, 30]),
    "W-WINDOW": (100, 30, [], [120]),
    "W-BURST": (100, 30, [1000, 20000, 1000, 20000, 1000, 20000, 1000, 1000], [30, 10, 20, 10, 20, 10, 30, 50]),
    "W-CONNECTIONS": (1000, 0, [1000], [300]),
    "W-CHURN": (100, 30, [1000], [300]),
    "W-FAULT": (100, 30, [1000], [300]),
    "W-SLOW-CONSUMER": (100, 30, [1000], [300]),
    "W-SOAK": (100, 60, [5000], [3600]),
}


def profile(name):
    if name not in _PROFILES:
        raise ValueError("Unknown workload profile")
    connections, warmup, rates, holds = copy.deepcopy(_PROFILES[name])
    healthy = name in ("W-BASE", "W-TARGET", "W-SOAK")
    return {
        "name": name,
        "connections": connections,
        "warmupSeconds": warmup,
        "rates": rates,
        "holdSeconds": holds,
        "count": sum(rate * hold for rate, hold in zip(rates, holds)) if rates else 1_000_000_000,
        "window": 32,
        "drainSeconds": 30,
        "timeoutSeconds": 2,
        "payloadBytes": 160,
        "seed": 1,
        "minimumRateRatio": 0.99 if healthy else 0,
        "maximumP99Millis": 100 if healthy else 0,
        "maximumRssMib": 1024,
        "expectFailures": name in ("W-RAMP", "W-BURST", "W-CHURN", "W-FAULT", "W-SLOW-CONSUMER"),
        "connectIntervalSeconds": 0.04 if name == "W-CONNECTIONS" else 0,
        "churnRate": 10 if name == "W-CHURN" else 0,
        "churnCount": 3000 if name == "W-CHURN" else 0,
        "faults": {"reject": 5, "delay": 10, "stall": 1} if name == "W-FAULT" else {},
        "consumerDelaySeconds": 0.2 if name == "W-SLOW-CONSUMER" else 0,
    }


def assess_intervals(report):
    """Characterize original scheduled cohorts and an exact first 30-second recovery cohort."""
    configuration, traffic = report["configuration"], report["traffic"]
    intervals = traffic.get("intervals", [])
    if (configuration.get("operation") == "none" or configuration.get("model") != "ARRIVAL_RATE"
            or not intervals):
        return {"applicable": False, "levels": [], "firstUnmetLevel": None,
                "recovery": {"applicable": False, "passed": None}}
    rates, holds = configuration["rates"], configuration["rateHoldNanos"]
    if not isinstance(intervals, list) or not 1 <= len(intervals) <= 64 or len(intervals) != len(rates) or len(rates) != len(holds):
        raise ValueError("Intervals require 1..64 matching configured rates and holds")
    elapsed = bounded_int(traffic["measurementNanos"], 86_400_000_000_000, "measurement duration")
    offset, levels = 0, []
    for index, interval in enumerate(intervals):
        rate = bounded_int(interval["rate"], 1_000_000, "interval rate")
        duration = bounded_int(interval["durationNanos"], 86_400_000_000_000, "interval duration")
        if (not rate or duration < 1_000_000 or interval["startOffsetNanos"] != offset
                or rate != rates[index] or duration != holds[index]):
            raise ValueError("Intervals must be contiguous and match their configured rates and holds")
        metrics = interval["metrics"]
        validate_cohort(metrics)
        histogram = merge_histograms([metrics["scheduledLatency"]])
        achieved = metrics["successesDuringMeasurement"] * 1_000_000_000 / duration
        failures = []
        if traffic["measurementStarted"] is not True or elapsed < offset + duration:
            failures.append("interval-not-fully-observed")
        if metrics["planned"] != (rate * duration + 999_999_999) // 1_000_000_000:
            failures.append("offered-interval-not-established")
        if metrics["skipped"]:
            failures.append("generator-skipped-arrivals")
        if metrics["rejected"]:
            failures.append("local-rejections")
        if metrics["pending"] or any(count for outcome, count in metrics["outcomes"].items() if outcome != "SUCCESS"):
            failures.append("non-success-outcomes")
        if achieved < 0.99 * rate:
            failures.append("successful-rate-below-99-percent")
        if not histogram["count"] or histogram["overflow"]:
            failures.append("latency-population-unavailable")
        if histogram["p99Micros"] > 100_000:
            failures.append("scheduled-p99-above-100ms")
        levels.append({"index": index, "offeredRequestsPerSecond": rate, "durationNanos": duration,
                       "startOffsetNanos": offset, "successfulCompletionsPerSecond": achieved,
                       "scheduledP99Micros": histogram["p99Micros"], "latencySamples": histogram["count"],
                       "generatorSkippedArrivals": metrics["skipped"], "failures": failures, "passed": not failures})
        offset += duration
    if offset > 86_400_000_000_000:
        raise ValueError("Intervals exceed the finite phase duration")
    baseline = levels[0]
    overload = [level["index"] for level in levels if level["offeredRequestsPerSecond"] > baseline["offeredRequestsPerSecond"]]
    recovery = {"applicable": bool(overload), "passed": None}
    if overload:
        index = overload[-1] + 1
        failures = []
        if index >= len(levels) or levels[index]["offeredRequestsPerSecond"] != baseline["offeredRequestsPerSecond"] or levels[index]["durationNanos"] != 30_000_000_000:
            failures.append("recovery-requires-first-exact-30-second-baseline-cohort")
        else:
            recovered = levels[index]
            failures.extend(recovered["failures"])
            if not baseline["passed"]:
                failures.append("pre-fault-baseline-not-established")
            if recovered["successfulCompletionsPerSecond"] < 0.99 * baseline["successfulCompletionsPerSecond"]:
                failures.append("successful-rate-below-99-percent-of-baseline")
            if recovered["scheduledP99Micros"] > 2 * baseline["scheduledP99Micros"]:
                failures.append("scheduled-p99-above-twice-baseline")
        recovery.update({"index": index, "passed": not failures, "failures": failures,
                         "policy": "First exact 30-second cohort after the last overload; established initial baseline, zero skips/local rejections/non-successes, at least 99% of offered and pre-fault achieved rate, p99 at most 100ms and twice baseline"})
    return {"applicable": True, "levels": levels,
            "firstUnmetLevel": next((level for level in levels if not level["passed"]), None),
            "recovery": recovery,
            "semantics": "Cohorts retain original schedule attribution and late outcomes; in-cohort successful throughput uses the declared interval length; healthy criteria are provisional, first failure does not identify a target-only bottleneck"}


def merge_histograms(histograms):
    merged, count, overflow, maximum = {}, 0, 0, 0
    for histogram in histograms:
        source_count = bounded_int(histogram["count"], 128_000_000_000, "histogram count")
        source_overflow = bounded_int(histogram["overflow"], 128_000_000_000, "histogram overflow")
        buckets = histogram["buckets"]
        if not isinstance(buckets, list) or len(buckets) > 65536:
            raise ValueError("A histogram has too many buckets")
        total, previous = 0, -1
        for bucket in buckets:
            upper = bounded_int(bucket["upperMicros"], 3_604_000_000, "bucket upper bound")
            samples = bounded_int(bucket["count"], 128_000_000_000, "bucket count")
            if upper <= previous or samples == 0:
                raise ValueError("Histogram buckets must be ordered, unique, and nonempty")
            previous, total = upper, total + samples
            merged[upper] = merged.get(upper, 0) + samples
        if total != source_count:
            raise ValueError("Histogram bucket counts do not reconcile")
        count += source_count
        overflow += source_overflow
        maximum = max(maximum, bounded_int(histogram["maximumNanos"], 2**63 - 1, "maximum latency"))
        if count + overflow > 128_000_000_000 or len(merged) > 65536:
            raise ValueError("Aggregate histogram exceeds its finite bounds")
    buckets = [{"upperMicros": upper, "count": samples} for upper, samples in sorted(merged.items())]
    result = {"count": count, "overflow": overflow, "maximumNanos": maximum, "buckets": buckets}
    for name, numerator, denominator in (("p50Micros", 50, 100), ("p95Micros", 95, 100),
                                         ("p99Micros", 99, 100), ("p999Micros", 999, 1000)):
        rank = max(1, (count * numerator + denominator - 1) // denominator)
        cumulative, percentile = 0, 0
        for bucket in buckets:
            cumulative += bucket["count"]
            if cumulative >= rank:
                percentile = bucket["upperMicros"]
                break
        result[name] = percentile
    return result


def bounded_int(value, maximum, name):
    if type(value) is not int or not 0 <= value <= maximum:
        raise ValueError(name + " is outside its finite integer bound")
    return value


def aggregate_reports(paths):
    if not 1 <= len(paths) <= 128:
        raise ValueError("Aggregation accepts 1..128 report files")
    identities, run_ids, expected = set(), set(), None
    merged, runs, elapsed = {}, [], 0
    for supplied in paths:
        path = Path(supplied).resolve(strict=True)
        if path in identities:
            raise ValueError("Duplicate report path")
        identities.add(path)
        report = read_json(path)
        configuration = dict(report["configuration"])
        configuration.pop("port", None)
        signature = {"schema": report["schema"], "configuration": configuration,
                     "histogramSettings": report["histogramSettings"],
                     "suppliedSourceRevision": report["suppliedSourceRevision"],
                     "executableSha256": report["environment"]["executableSha256"]}
        if signature["schema"] != 2 or expected is not None and signature != expected:
            raise ValueError("Reports require compatible schema, inputs, revision, histograms and executables")
        expected = signature
        run_id = report["runId"]
        if not isinstance(run_id, str) or not 1 <= len(run_id) <= 128 or run_id in run_ids:
            raise ValueError("Reports require distinct bounded run identities")
        run_ids.add(run_id)
        traffic = report["traffic"]
        for phase in ("warmup", "measurement"):
            cohort = traffic[phase]
            validate_cohort(cohort)
            merged[phase] = merge_cohort(merged.get(phase), cohort)
        duration = bounded_int(traffic["measurementNanos"], 86_400_000_000_000, "measurement duration")
        elapsed += duration
        successes = traffic["measurement"]["successesDuringMeasurement"]
        histogram = merge_histograms([traffic["measurement"]["scheduledLatency"]])
        assessment = assess_intervals(report)
        runs.append({"path": str(path), "runId": run_id,
                     "passed": report["passed"] is True and assessment["recovery"]["passed"] is not False,
                     "intervalAssessment": assessment,
                     "cleanupComplete": report["cleanupComplete"] is True,
                     "measurementStarted": traffic["measurementStarted"] is True,
                     "measurementNanos": duration,
                     "successfulCompletionsPerSecond": successes * 1_000_000_000 / duration if duration else 0,
                     "scheduledP99Micros": histogram["p99Micros"],
                     "generatorSkippedArrivals": traffic["measurement"]["skipped"],
                     "resources": resource_summary(report.get("resources", {}))})
    rates = [run["successfulCompletionsPerSecond"] for run in runs]
    p99s = [run["scheduledP99Micros"] for run in runs]
    return {"schema": 1, "runs": len(runs), "compatibility": expected, **merged,
            "successfulCompletionsPerSecond": merged["measurement"]["successesDuringMeasurement"] * 1_000_000_000 / elapsed if elapsed else 0,
            "rateSpread": {"minimum": min(rates), "maximum": max(rates), "range": max(rates) - min(rates)},
            "scheduledP99SpreadMicros": {"minimum": min(p99s), "maximum": max(p99s)},
            "measurementNanosSum": elapsed, "individualRuns": runs,
            "passed": all(run["passed"] and run["cleanupComplete"] for run in runs),
            "semantics": "Sequential compatible repeats; histogram buckets are count-weighted, throughput is total in-phase successes divided by summed observed phase time, never an average of percentiles or a distributed wall-clock rate"}


def resource_summary(resources):
    """Retain only a fixed numeric resource schema for each of at most 128 reports."""
    if not isinstance(resources, dict):
        raise ValueError("Resource observations require an object")
    result = {}
    names = ("sampledPeakHeapBytes", "sampledPeakFileDescriptors", "sampledPeakRssBytes",
             "sampledPeakPlatformThreads", "samples")
    snapshots = ("heapBytes", "committedHeapBytes", "rssBytes", "peakRssBytes", "cpuNanos",
                 "gcMillis", "fileDescriptors", "platformThreads")
    def selected(values, keys):
        if not isinstance(values, dict):
            raise ValueError("A resource snapshot requires an object")
        kept = {}
        for key in keys:
            if key in values:
                value = values[key]
                if type(value) is not int or not -1 <= value <= 2**63 - 1:
                    raise ValueError("Resource observation is outside its signed finite bound")
                kept[key] = value
        return kept
    result.update(selected(resources, names))
    for name in ("initial", "baseline", "latest"):
        if name in resources:
            result[name] = selected(resources[name], snapshots)
    if "baselineRecorded" in resources:
        if type(resources["baselineRecorded"]) is not bool:
            raise ValueError("Baseline presence must be boolean")
        result["baselineRecorded"] = resources["baselineRecorded"]
    return result


_COUNTERS = ("planned", "skipped", "attempted", "rejected", "admitted", "pending",
             "completionsDuringMeasurement", "successesDuringMeasurement", "mayHaveBeenSent", "excessStatuses")
_HISTOGRAMS = ("invocationLatency", "scheduledLatency", "schedulingLag")


def validate_cohort(cohort):
    for key in (*_COUNTERS, "peakPending"):
        bounded_int(cohort[key], 1_000_000_000, key)
    for key, maximum in (("outcomes", 7), ("rejections", 33), ("statuses", 256)):
        if not isinstance(cohort[key], dict) or len(cohort[key]) > maximum:
            raise ValueError("Cohort categories exceed bounds")
        for name, value in cohort[key].items():
            if not isinstance(name, str) or not 1 <= len(name) <= 128:
                raise ValueError("Invalid cohort category")
            bounded_int(value, 1_000_000_000, key)
    if (cohort["planned"] != cohort["skipped"] + cohort["attempted"]
            or cohort["attempted"] != cohort["rejected"] + cohort["admitted"]
            or cohort["admitted"] != cohort["pending"] + sum(cohort["outcomes"].values())):
        raise ValueError("Cohort accounting does not reconcile")
    terminal = sum(cohort["outcomes"].values()) - cohort["outcomes"].get("UNFINISHED", 0)
    if (cohort["successesDuringMeasurement"] > cohort["outcomes"].get("SUCCESS", 0)
            or cohort["successesDuringMeasurement"] > cohort["completionsDuringMeasurement"]
            or cohort["completionsDuringMeasurement"] > terminal):
        raise ValueError("In-phase observations exceed their terminal populations")
    for key in _HISTOGRAMS:
        histogram = merge_histograms([cohort[key]])
        population = cohort["attempted"] if key == "schedulingLag" else terminal
        if histogram["count"] + histogram["overflow"] != population:
            raise ValueError("Histogram population does not reconcile with its cohort")


def merge_cohort(existing, cohort):
    if existing is None:
        existing = {key: 0 for key in _COUNTERS}
        existing.update({"peakPending": 0, "outcomes": {}, "rejections": {}, "statuses": {}})
        existing.update({key: {"count": 0, "overflow": 0, "maximumNanos": 0, "buckets": []}
                         for key in _HISTOGRAMS})
    for key in _COUNTERS:
        existing[key] += cohort[key]
    existing["peakPending"] = max(existing["peakPending"], cohort["peakPending"])
    for key in ("outcomes", "rejections", "statuses"):
        for category, count in cohort[key].items():
            existing[key][category] = existing[key].get(category, 0) + count
    for key in _HISTOGRAMS:
        existing[key] = merge_histograms([existing[key], cohort[key]])
    return existing


def read_json(path):
    with Path(path).open("rb") as stream:
        data = stream.read(32 * 1024 * 1024 + 1)
    if len(data) > 32 * 1024 * 1024:
        raise ValueError("Report exceeds the 32 MiB input bound")
    return json.loads(data, parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Non-finite JSON number")))


def build_options(plan, version, direction, revision, output, repeat):
    if version not in ("3.4", "5.0") or direction not in ("submit", "deliver", "data"):
        raise ValueError("Choose version 3.4/5.0 and direction submit/deliver/data")
    holds = [nanos(value) for value in plan["holdSeconds"]]
    rates = plan["rates"]
    if direction == "data":
        if any(rate % 2 for rate in rates):
            raise ValueError("An exact 50/50 bidirectional profile requires even rates")
        rates = [rate // 2 for rate in rates]
    count = sum((rate * hold + 999_999_999) // 1_000_000_000 for rate, hold in zip(rates, holds)) if rates else plan["count"]
    shared = {
        "revision": revision, "version": version, "host": "127.0.0.1", "bind": "trx",
        "connections": str(plan["connections"]), "window": str(plan["window"]),
        "warmup": iso_duration(nanos(plan["warmupSeconds"])), "duration": iso_duration(sum(holds)),
        "drain": iso_duration(nanos(plan["drainSeconds"])), "timeout": iso_duration(nanos(plan["timeoutSeconds"])),
        "payload": str(plan["payloadBytes"]), "seed": str(plan["seed"]), "count": str(count),
        "model": "arrival" if rates else "concurrency", "scenario": plan["name"],
        "minimum-rate-ratio": str(plan["minimumRateRatio"]), "p99-ms": str(plan["maximumP99Millis"]),
        "max-rss-mib": str(plan["maximumRssMib"]),
        "expect-failures": str(plan["expectFailures"]).lower(),
        "connect-interval": iso_duration(nanos(plan["connectIntervalSeconds"])),
    }
    if rates:
        shared["rates"] = ",".join(str(value) for value in rates)
        shared["holds"] = ",".join(iso_duration(value) for value in holds)
    server, client = dict(shared), dict(shared)
    server["port"] = "0"
    server["operation"] = "none" if direction == "submit" else direction
    client["operation"] = "none" if direction == "deliver" else direction
    for role, options, receives in (("server", server, direction != "deliver"), ("client", client, direction != "submit")):
        options["report"] = str(Path(output) / role)
        options["run-id"] = f"{plan['name']}-{version}-{direction}-{repeat}-{role}"
        if receives:
            options.update({key: str(value) for key, value in plan["faults"].items()})
            options["consumer-delay"] = iso_duration(nanos(plan["consumerDelaySeconds"]))
            if plan["faults"]:
                options["verify-fault-mix"] = "true"
    if plan["churnRate"]:
        client["churn-rate"] = str(plan["churnRate"])
        client["churn-count"] = str(plan["churnCount"])
    return server, client


def nanos(seconds):
    value = Decimal(str(seconds)) * 1_000_000_000
    if not value.is_finite() or value < 0 or value != value.to_integral_value():
        raise ValueError("Durations require finite nonnegative integral nanoseconds")
    return int(value)


def iso_duration(value):
    seconds, fraction = divmod(value, 1_000_000_000)
    tail = f".{fraction:09d}".rstrip("0") if fraction else ""
    return f"PT{seconds}{tail}S"


def run_pair(launcher, server_options, client_options, output, timeout_seconds):
    if not math.isfinite(timeout_seconds) or not 0 < timeout_seconds <= 200_000:
        raise ValueError("The shared process deadline must be finite and at most 200000 seconds")
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    processes, logs, commands, errors = {}, [], {}, []
    started = time.monotonic()
    deadline = started + timeout_seconds
    timed_out = interrupted = False
    try:
        server_log = (output / "server.log").open("xb")
        logs.append(server_log)
        commands["server"] = command(launcher, "server", server_options)
        processes["server"] = subprocess.Popen(commands["server"], stdin=subprocess.DEVNULL,
                                                stdout=server_log, stderr=subprocess.STDOUT)
        port = None
        while time.monotonic() < deadline:
            with (output / "server.log").open("rb") as stream:
                text = stream.read(65536).decode("utf-8", errors="replace")
            for line in text.splitlines():
                if line.startswith("READY port="):
                    port = int(line[11:])
                    if not 1 <= port <= 65535:
                        raise ValueError("The server emitted an invalid listening port")
                    break
            if port is not None or processes["server"].poll() is not None:
                break
            time.sleep(0.01)
        if port is None:
            timed_out = time.monotonic() >= deadline
            errors.append("server-did-not-become-ready")
        else:
            client_log = (output / "client.log").open("xb")
            logs.append(client_log)
            options = dict(client_options, port=str(port))
            commands["client"] = command(launcher, "client", options)
            processes["client"] = subprocess.Popen(commands["client"], stdin=subprocess.DEVNULL,
                                                    stdout=client_log, stderr=subprocess.STDOUT)
            while any(process.poll() is None for process in processes.values()):
                if time.monotonic() >= deadline:
                    timed_out = True
                    break
                if any(process.poll() == 2 for process in processes.values()):
                    errors.append("invalid-child-configuration")
                    break
                time.sleep(0.01)
    except KeyboardInterrupt:
        interrupted = True
        errors.append("campaign-interrupted")
    except (OSError, ValueError) as failure:
        errors.append("launch-failed:" + type(failure).__name__)
    finally:
        # Attempt every owned process before any bounded reap can fail.
        for role, process in processes.items():
            try:
                if process.poll() is None:
                    process.kill()
            except OSError as failure:
                errors.append(role + "-kill-failed:" + type(failure).__name__)
        for role, process in processes.items():
            try:
                process.wait(timeout=5)
            except (OSError, subprocess.TimeoutExpired) as failure:
                errors.append(role + "-reap-failed:" + type(failure).__name__)
        for log in logs:
            log.close()
    return {
        "commands": commands, "exitCodes": {role: process.poll() for role, process in processes.items()},
        "elapsedSeconds": time.monotonic() - started, "timedOut": timed_out, "interrupted": interrupted,
        "cleanupComplete": all(process.poll() is not None for process in processes.values()),
        "errors": errors,
    }


def command(launcher, mode, options):
    return list(launcher) + [mode] + [f"--{key}={value}" for key, value in sorted(options.items())]


def main(arguments=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    commands.add_parser("profiles", help="Print full provisional workload definitions")
    aggregate = commands.add_parser("aggregate", help="Merge compatible sequential reports")
    aggregate.add_argument("reports", nargs="+")
    aggregate.add_argument("--output", required=True)
    run = commands.add_parser("run", help="Run fresh server/client JVM pairs with finite cleanup")
    run.add_argument("--profile", required=True, choices=sorted(_PROFILES))
    run.add_argument("--version", required=True, choices=("3.4", "5.0"))
    run.add_argument("--direction", default="submit", choices=("submit", "deliver", "data"))
    run.add_argument("--revision", required=True)
    run.add_argument("--source-root", required=True)
    run.add_argument("--launcher", required=True)
    run.add_argument("--output", required=True)
    run.add_argument("--repeats", type=int, default=1)
    run.add_argument("--diagnostic-seconds", type=Decimal)
    run.add_argument("--warmup-seconds", type=Decimal)
    run.add_argument("--drain-seconds", type=Decimal)
    run.add_argument("--rate", type=int)
    run.add_argument("--connections", type=int)
    run.add_argument("--window", type=int)
    run.add_argument("--payload", type=int)
    run.add_argument("--client-option", action="append", default=[])
    run.add_argument("--server-option", action="append", default=[])
    args = parser.parse_args(arguments)
    try:
        if args.action == "profiles":
            print(json.dumps({name: profile(name) for name in _PROFILES}, indent=2))
            return 0
        if args.action == "aggregate":
            result = aggregate_reports(args.reports)
            write_json(Path(args.output), result, fresh=True)
            return 0 if result["passed"] else 1
        return run_campaign(args)
    except (OSError, ValueError, KeyError, TypeError) as failure:
        print("Campaign failed: " + type(failure).__name__ + ": " + str(failure), file=sys.stderr)
        return 2


def run_campaign(args):
    if not 1 <= args.repeats <= 10 or not re.fullmatch(r"[0-9a-f]{40}(\+[0-9a-f]{64})?", args.revision):
        raise ValueError("Choose 1..10 fresh repeats and a full revision, optionally plus a source SHA-256")
    launcher = Path(args.launcher).resolve(strict=True)
    plan = profile(args.profile)
    overrides = {}
    for option, key, maximum in (("connections", "connections", 4096), ("window", "window", 65536),
                                  ("payload", "payloadBytes", 65535), ("rate", "rates", 1_000_000)):
        value = getattr(args, option)
        if value is not None:
            if type(value) is not int or not (0 if option == "payload" else 1) <= value <= maximum:
                raise ValueError("Invalid bounded " + option + " override")
            if option == "rate":
                if len(plan["rates"]) != 1:
                    raise ValueError("Rate overrides require a single arrival-rate step")
                value = [value]
            plan[key], overrides[option] = value, value
    for option, key, maximum in (("diagnostic_seconds", "holdSeconds", 86400),
                                  ("warmup_seconds", "warmupSeconds", 86400),
                                  ("drain_seconds", "drainSeconds", 3600)):
        value = getattr(args, option)
        if value is not None:
            duration = nanos(value)
            if duration > maximum * 1_000_000_000 or option == "diagnostic_seconds" and duration < 1_000_000:
                raise ValueError("Invalid finite duration override")
            if option == "diagnostic_seconds":
                if len(plan["holdSeconds"]) != 1:
                    raise ValueError("Diagnostic duration requires a single step; burst/ramp holds stay explicit")
                plan[key] = [str(value)]
            else:
                plan[key] = str(value)
            overrides[option] = str(value)
    if plan["rates"]:
        streams = 2 if args.direction == "data" else 1
        plan["count"] = streams * sum(((rate // streams) * nanos(hold) + 999_999_999) // 1_000_000_000
                                      for rate, hold in zip(plan["rates"], plan["holdSeconds"]))
    if overrides:
        plan["name"] += "-diagnostic"
    if plan["connections"] * plan["window"] > 65536:
        raise ValueError("Combined tool window exceeds 65536")
    source = source_manifest(args.source_root)
    supplied = args.revision
    if "+" in supplied and supplied.split("+", 1)[1] != source["sha256"]:
        raise ValueError("Supplied source fingerprint does not match declared inputs")
    revision = supplied.split("+", 1)[0] + "+" + source["sha256"]
    output = Path(args.output).resolve()
    server_extra, client_extra = extra_options(args.server_option), extra_options(args.client_option)
    # Complete preflight before creating reports or starting either process.
    build_options(plan, args.version, args.direction, revision, output / "repeat-001", 1)
    timeout = (float(sum(nanos(value) for value in plan["holdSeconds"])) / 1_000_000_000
               + float(plan["warmupSeconds"]) + 2 * float(plan["drainSeconds"])
               + plan["connections"] * float(plan["connectIntervalSeconds"]) + 180)
    output.mkdir(parents=True, exist_ok=False)
    manifest = {"schema": 1, "profile": plan, "overrides": overrides, "version": args.version,
                "direction": args.direction, "repeatsRequested": args.repeats,
                "suppliedRevision": supplied, "reportedRevision": revision,
                "sourceRoot": str(Path(args.source_root).resolve()), "sourceInputs": source,
                "launcher": {"path": str(launcher), "sha256": hashlib.sha256(launcher.read_bytes()).hexdigest()},
                "childDeadlineSeconds": timeout, "runs": [], "passed": False,
                "sourceBinaryRelationship": "Caller supplies source checkout and compiled launcher; input and executable hashes identify both independently and do not prove a build relationship",
                "measurement": "Fresh separate JVMs, no build/test cache measurements, every failed attempt retained"}
    write_json(output / "campaign.json", manifest)
    paths = {"server": [], "client": []}
    for repeat in range(1, args.repeats + 1):
        directory = output / f"repeat-{repeat:03d}"
        server, client = build_options(plan, args.version, args.direction, revision, directory, repeat)
        server.update(server_extra)
        client.update(client_extra)
        print(f"START {plan['name']} {args.version} {args.direction} repeat={repeat}", flush=True)
        result = run_pair([str(launcher)], server, client, directory, timeout)
        result["reports"] = {}
        for role in ("server", "client"):
            path = directory / role / "report.json"
            try:
                report = read_json(path)
                assessment = assess_intervals(report)
                result["reports"][role] = {"path": str(path),
                                          "passed": report["passed"] is True and assessment["recovery"]["passed"] is not False,
                                          "intervalAssessment": assessment,
                                          "cleanupComplete": report["cleanupComplete"] is True,
                                          "executableSha256": report.get("environment", {}).get("executableSha256", {})}
                paths[role].append(path)
            except (OSError, ValueError, KeyError, TypeError) as failure:
                result["errors"].append(role + "-report-invalid:" + type(failure).__name__)
        result["passed"] = (not result["errors"] and not result["timedOut"] and result["cleanupComplete"]
                            and len(result["exitCodes"]) == 2 and all(value == 0 for value in result["exitCodes"].values())
                            and len(result["reports"]) == 2
                            and all(value["passed"] and value["cleanupComplete"] for value in result["reports"].values()))
        manifest["runs"].append(result)
        write_json(output / "campaign.json", manifest)
        print(f"FINISH repeat={repeat} passed={str(result['passed']).lower()} cleanup={str(result['cleanupComplete']).lower()}", flush=True)
        if result["interrupted"] or not result["cleanupComplete"]:
            break
    manifest["aggregationErrors"] = []
    for role, reports in paths.items():
        if reports:
            try:
                write_json(output / (role + "-aggregate.json"), aggregate_reports(reports), fresh=True)
            except (OSError, ValueError, KeyError, TypeError) as failure:
                manifest["aggregationErrors"].append(role + ":" + type(failure).__name__)
    manifest["passed"] = (len(manifest["runs"]) == args.repeats
                          and all(result["passed"] for result in manifest["runs"])
                          and not manifest["aggregationErrors"])
    write_json(output / "campaign.json", manifest)
    return 0 if manifest["passed"] else 1


def extra_options(values):
    allowed = {"tls", "tls-key-store", "tls-trust-store", "tls-key-password-env", "tls-trust-password-env",
               "tls-peer-name", "tls-handshake-timeout", "tls-client-certificate", "keepalive-idle",
               "keepalive-timeout", "reconnect-attempts", "reconnect-delay", "spin", "sample",
               "max-rss-mib", "max-heap-mib", "max-fd-growth", "max-thread-growth", "content"}
    if len(values) > len(allowed):
        raise ValueError("Too many endpoint overrides")
    result = {}
    for value in values:
        if "=" not in value or len(value) > 4096:
            raise ValueError("Endpoint overrides require bounded name=value pairs")
        key, content = value.removeprefix("--").split("=", 1)
        if key not in allowed or key in result or not content:
            raise ValueError("Unknown or duplicate endpoint override")
        result[key] = content
    return result


def write_json(path, value, fresh=False):
    if fresh:
        with Path(path).open("x", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, allow_nan=False)
            stream.write("\n")
    else:
        pending = Path(path).with_suffix(".pending")
        with pending.open("w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, allow_nan=False)
            stream.write("\n")
        pending.replace(path)


if __name__ == "__main__":
    sys.exit(main())
