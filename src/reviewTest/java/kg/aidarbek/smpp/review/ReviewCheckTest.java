package kg.aidarbek.smpp.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ReviewCheckTest {
    @TempDir
    Path root;

    @Test
    void writesADeterministicInventoryForAReviewedProject() throws Exception {
        createProject();
        Path output = root.resolve("build/reports/coverage.txt");

        ReviewCheck.run(root, output);

        assertEquals(
                "SOLID review coverage v1\n"
                        + "src/main/java/example/Value.java\texample.Value\t"
                        + "461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1\n",
                Files.readString(output));
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "report", "malformed"})
    void invalidInputRemovesAnEarlierSuccessArtifact(String change) throws Exception {
        createProject();
        Path output = root.resolve("build/reports/coverage.txt");
        ReviewCheck.run(root, output);
        if (change.equals("source")) {
            Files.writeString(
                    root.resolve("src/main/java/example/Value.java"),
                    "package example; record Value(long number) {}\n");
        } else if (change.equals("report")) {
            Path report = root.resolve("docs/reviews/current.md");
            Files.writeString(report, Files.readString(report).replace("S: pass |", "S: fail |"));
        } else {
            Files.writeString(root.resolve("docs/reviews/broken.md"), "```solid-review\nunknown: field\n```\n");
        }

        assertThrows(IllegalArgumentException.class, () -> ReviewCheck.run(root, output));
        assertFalse(Files.exists(output), "A failed check must not leave a previous success artifact");
    }

    @Test
    void discoversUnreviewedTypesInAPackageNamedBuild() throws Exception {
        createProject();
        Path source =
                Files.createDirectories(root.resolve("src/custom/java/build")).resolve("NewType.java");
        Files.writeString(source, "package build; class NewType {}\n");

        var failure = assertThrows(
                IllegalArgumentException.class, () -> ReviewCheck.run(root, root.resolve("build/coverage.txt")));
        assertTrue(failure.getMessage()
                .contains("Missing review for src/custom/java/build/NewType.java :: build.NewType"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"build/generated", "simulator/build/generated"})
    void excludesGeneratedSourcesFromEveryConfiguredBuildDirectory(String directory) throws Exception {
        createProject();
        Path generated = Files.createDirectories(root.resolve(directory)).resolve("Generated.java");
        Files.writeString(generated, "package generated; class Generated {}\n");
        Path output = root.resolve("build/coverage.txt");

        ReviewCheck.run(root, output);

        assertTrue(Files.readString(output).contains("\texample.Value\t"));
        assertFalse(Files.readString(output).contains("generated.Generated"));
    }

    @Test
    void onlyInventoriesUnreviewedSourcesWhenExplicitlyRequested() throws Exception {
        createProject();
        Files.delete(root.resolve("docs/reviews/current.md"));
        Path output = root.resolve("build/inventory.tsv");

        ReviewCheck.main(new String[] {"--inventory", root.toString(), output.toString()});

        assertTrue(Files.readString(output).startsWith("Java source inventory v1\n"));
        assertTrue(Files.readString(output).contains("\texample.Value\t"));
    }

    @Test
    void refusesVacuousCoverageWhenNoJavaTypeExists() throws Exception {
        Files.writeString(root.resolve("package-info.java"), "package example;\n");

        var failure = assertThrows(
                IllegalArgumentException.class, () -> ReviewCheck.run(root, root.resolve("build/coverage.txt")));
        assertTrue(failure.getMessage().contains("No project-owned Java types"));
    }

    @Test
    void explicitFileModeReadsExactlyTheFilesDeclaredByGradle() throws Exception {
        createProject();
        Path additional =
                Files.createDirectories(root.resolve("src/custom/java/build")).resolve("NewType.java");
        Files.writeString(additional, "package build; class NewType {}\n");
        Path output = root.resolve("build/coverage.txt");

        ReviewCheck.main(new String[] {
            "--check-files",
            root.toString(),
            output.toString(),
            "--source",
            "src/main/java/example/Value.java",
            "--report",
            "docs/reviews/current.md"
        });

        assertTrue(Files.readString(output).contains("\texample.Value\t"));
        assertFalse(Files.readString(output).contains("build.NewType"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "unpaired", "escaped", "absolute"})
    void rejectsInvalidExplicitFileArguments(String invalid) throws Exception {
        createProject();
        var arguments = new ArrayList<>(List.of(
                "--check-files",
                root.toString(),
                root.resolve("build/coverage.txt").toString()));
        switch (invalid) {
            case "unknown" -> arguments.addAll(List.of("--unknown", "src/main/java/example/Value.java"));
            case "unpaired" -> arguments.add("--source");
            case "escaped" -> arguments.addAll(List.of("--source", "../Outside.java"));
            case "absolute" ->
                arguments.addAll(List.of("--source", root.resolve("Value.java").toString()));
            default -> throw new AssertionError("Unknown test case");
        }

        assertThrows(IllegalArgumentException.class, () -> ReviewCheck.main(arguments.toArray(String[]::new)));
    }

    @Test
    void anEmptyExplicitFileListDoesNotTriggerImplicitDiscovery() throws Exception {
        createProject();
        Path output = root.resolve("build/coverage.txt");

        assertThrows(
                IllegalArgumentException.class,
                () -> ReviewCheck.main(new String[] {"--check-files", root.toString(), output.toString()}));
        ReviewCheck.main(new String[] {"--inventory-files", root.toString(), output.toString()});
        assertEquals("Java source inventory v1\n", Files.readString(output));
    }

    private void createProject() throws Exception {
        Path source =
                Files.createDirectories(root.resolve("src/main/java/example")).resolve("Value.java");
        Files.writeString(source, "package example; record Value(int number) {}\n");
        Path review = Files.createDirectories(root.resolve("docs/reviews")).resolve("current.md");
        Files.writeString(review, """
                ```solid-review
                source: src/main/java/example/Value.java
                type: example.Value
                sha256: 461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1
                responsibility: Stores the example caller's integer value.
                consumers: Example callers read the integer and compare values.
                S: pass | Only the integer invariant drives changes to this value.
                O: pass | This value has no supported extension requirement.
                L: pass | Record equality and hash code use the immutable primitive value.
                I: not applicable | No custom interface; callers need only its accessor.
                D: pass | The value has no variable infrastructure dependency.
                findings: none
                ```
                """);
    }
}
