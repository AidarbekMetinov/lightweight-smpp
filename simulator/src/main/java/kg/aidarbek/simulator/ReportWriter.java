package kg.aidarbek.simulator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Fresh machine-readable report files and a streamed numeric resource series. */
final class ReportWriter implements AutoCloseable {
    private final Path directory;
    private final BufferedWriter series;

    public ReportWriter(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        if (Files.exists(directory.resolve("report.json")))
            throw new FileAlreadyExistsException(
                    directory.resolve("report.json").toString());
        series = Files.newBufferedWriter(
                directory.resolve("resources.csv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        try {
            series.write("elapsed_nanos,heap_bytes,rss_bytes,cpu_nanos,gc_millis,file_descriptors\n");
        } catch (IOException failure) {
            try {
                series.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public void sample(
            long elapsedNanos, long heapBytes, long rssBytes, long cpuNanos, long gcMillis, long fileDescriptors)
            throws IOException {
        series.write(elapsedNanos + "," + heapBytes + "," + rssBytes + "," + cpuNanos + "," + gcMillis + ","
                + fileDescriptors + "\n");
    }

    public void finish(Map<String, Object> report) throws IOException {
        series.flush();
        Files.writeString(
                directory.resolve("report.json"),
                Json.encode(report) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    @Override
    public void close() throws IOException {
        series.close();
    }
}
