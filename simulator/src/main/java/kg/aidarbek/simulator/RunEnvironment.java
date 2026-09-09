package kg.aidarbek.simulator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kg.aidarbek.smpp.endpoint.SmppClient;
import org.HdrHistogram.Histogram;

/** Portable JVM observations plus explicitly optional Linux resource readings and executable hashes. */
final class RunEnvironment {
    private RunEnvironment() {}

    public static Map<String, Object> describe() throws IOException {
        var values = new LinkedHashMap<String, Object>();
        values.put("java", System.getProperty("java.runtime.version"));
        values.put("vm", System.getProperty("java.vm.name"));
        values.put("vendor", System.getProperty("java.vendor"));
        values.put("os", System.getProperty("os.name"));
        values.put("osVersion", System.getProperty("os.version"));
        values.put("architecture", System.getProperty("os.arch"));
        values.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
        values.put("maximumHeapBytes", Runtime.getRuntime().maxMemory());
        values.put(
                "heapAndCollectorArguments",
                ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                        .filter(value -> value.matches("-Xm[sx][0-9]+[kKmMgG]?|-XX:[+-]Use[A-Za-z0-9]+GC"))
                        .toList());
        var binaries = new LinkedHashMap<String, Object>();
        for (Class<?> type : List.of(SimulatorMain.class, SmppClient.class, Histogram.class))
            binaries.put(type.getName(), fingerprint(type));
        values.put("executableSha256", binaries);
        return values;
    }

    private static String fingerprint(Class<?> type) throws IOException {
        try {
            Path location = Path.of(
                    type.getProtectionDomain().getCodeSource().getLocation().toURI());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (Files.isDirectory(location)) {
                try (var entries = Files.walk(location)) {
                    for (Path entry :
                            entries.filter(Files::isRegularFile).sorted().toList()) {
                        digest.update(location.relativize(entry)
                                .toString()
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        update(digest, entry);
                    }
                }
            } else update(digest, location);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | URISyntaxException failure) {
            throw new IOException("Cannot fingerprint executable", failure);
        }
    }

    private static void update(MessageDigest digest, Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
    }

    public static Snapshot sample() {
        long gc = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> Math.max(0, bean.getCollectionTime()))
                .sum();
        long descriptors = -1;
        try (var entries = Files.list(Path.of("/proc/self/fd"))) {
            descriptors = entries.count();
        } catch (IOException unavailable) {
            /* A missing platform observation is -1, never zero usage. */
        }
        return new Snapshot(
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted(),
                linuxBytes("VmRSS:"),
                linuxBytes("VmHWM:"),
                ProcessHandle.current()
                        .info()
                        .totalCpuDuration()
                        .map(java.time.Duration::toNanos)
                        .orElse(-1L),
                gc,
                descriptors,
                ManagementFactory.getThreadMXBean().getThreadCount());
    }

    private static long linuxBytes(String key) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status")))
                if (line.startsWith(key))
                    return Long.parseLong(line.substring(key.length()).trim().split("\\s+")[0]) * 1024;
        } catch (IOException | NumberFormatException unavailable) {
            return -1;
        }
        return -1;
    }

    public record Snapshot(
            long heapBytes,
            long committedHeapBytes,
            long rssBytes,
            long peakRssBytes,
            long cpuNanos,
            long gcMillis,
            long fileDescriptors,
            int platformThreads) {}
}
