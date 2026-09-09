package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {
    @TempDir
    Path directory;

    @Test
    void encodesEscapedTextBoundedNumericHistogramsAndExplicitMissingValues() {
        assertEquals("\"quote\\\"\\n\\t\\u0001\\\\\"", Json.encode("quote\"\n\t\u0001\\"));
        assertEquals("{\"count\":3}", Json.encode(Map.of("count", 3)));
        assertEquals("[0,true,null]", Json.encode(java.util.Arrays.asList(0, true, null)));
        assertEquals("{\"upperMicros\":100,\"count\":2}", Json.encode(new Latencies.Bucket(100, 2)));
        assertThrows(IllegalArgumentException.class, () -> Json.encode(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.encode(new Object()));
    }

    @Test
    void streamsSeriesAndRefusesToOverwriteAPreviousMeasurement() throws Exception {
        Path run = directory.resolve("run");
        try (var writer = new ReportWriter(run)) {
            writer.sample(10, 20, -1, 40, 50, 60);
            writer.sample(11, 21, 31, 41, 51, 61);
            writer.finish(Map.of("run", "fresh", "histogram", List.of(new Latencies.Bucket(1, 2))));
        }
        assertTrue(Files.exists(run.resolve("report.json")));
        assertTrue(Files.readString(run.resolve("report.json"))
                .contains("\"histogram\":[{\"upperMicros\":1,\"count\":2}]"));
        assertEquals(
                List.of(
                        "elapsed_nanos,heap_bytes,rss_bytes,cpu_nanos,gc_millis,file_descriptors",
                        "10,20,-1,40,50,60",
                        "11,21,31,41,51,61"),
                Files.readAllLines(run.resolve("resources.csv")));
        assertThrows(FileAlreadyExistsException.class, () -> new ReportWriter(run));
    }
}
