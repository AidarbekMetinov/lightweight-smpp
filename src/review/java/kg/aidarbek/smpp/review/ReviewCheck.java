package kg.aidarbek.smpp.review;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Development command that checks current source identities against recorded SOLID evidence. */
public final class ReviewCheck {
    private ReviewCheck() {}

    /**
     * Checks {@code ROOT OUTPUT}, or writes an unvalidated inventory for {@code --inventory ROOT OUTPUT}.
     * The caller owns the output file, which is replaced. Sources and reports are read as UTF-8.
     * Gradle uses {@code --check-files ROOT OUTPUT} or {@code --inventory-files ROOT OUTPUT}, followed by
     * repeated {@code --source PATH} and {@code --report PATH} pairs, to consume exactly its declared files.
     *
     * @param args command mode, repository and output paths, and any explicit input file pairs
     * @throws IOException if files cannot be read or written
     * @throws IllegalArgumentException if arguments, Java syntax or review evidence are invalid
     */
    public static void main(String[] args) throws IOException {
        if (args.length >= 3 && Set.of("--check-files", "--inventory-files").contains(args[0])) {
            Path root = Path.of(args[1]).toAbsolutePath().normalize();
            Path output = Path.of(args[2]);
            var sources = new ArrayList<Path>();
            var reports = new ArrayList<Path>();
            for (int index = 3; index < args.length; index += 2) {
                if (index + 1 == args.length) {
                    throw new IllegalArgumentException("Each file option requires a repository-relative path");
                }
                Path relative = Path.of(args[index + 1]);
                Path file = root.resolve(relative).normalize();
                if (relative.isAbsolute() || !file.startsWith(root)) {
                    throw new IllegalArgumentException("Input must be within the repository: " + relative);
                }
                switch (args[index]) {
                    case "--source" -> sources.add(file);
                    case "--report" -> reports.add(file);
                    default -> throw new IllegalArgumentException("Unknown file option: " + args[index]);
                }
            }
            Files.deleteIfExists(output);
            List<SourceType> types = new JavaSourceInventory().scan(root, sources);
            if (args[0].equals("--inventory-files")) {
                writeInventory(output, types);
            } else {
                validate(root, output, types, reports);
            }
        } else if (args.length == 3 && args[0].equals("--inventory")) {
            Path root = Path.of(args[1]).toAbsolutePath().normalize();
            writeInventory(Path.of(args[2]), inventory(root));
        } else if (args.length == 2) {
            run(Path.of(args[0]), Path.of(args[1]));
        } else {
            throw new IllegalArgumentException("Usage: ReviewCheck [--inventory] ROOT OUTPUT");
        }
    }

    static void run(Path root, Path output) throws IOException {
        Files.deleteIfExists(output);
        Path repository = root.toAbsolutePath().normalize();
        validate(repository, output, inventory(repository), reports(repository));
    }

    private static void validate(Path repository, Path output, List<SourceType> inventory, List<Path> reports)
            throws IOException {
        if (inventory.isEmpty()) {
            throw new IllegalArgumentException("No project-owned Java types found; coverage cannot be established");
        }
        var evidence = new ArrayList<ReviewEvidence>();
        for (Path report : reports) {
            evidence.addAll(new ReviewDocumentParser()
                    .parse(
                            Files.readString(report),
                            repository.relativize(report).toString()));
        }
        List<String> violations = new ReviewCoverage().violations(inventory, evidence);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", violations));
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, "SOLID review coverage v1\n" + render(inventory));
    }

    private static List<Path> reports(Path repository) throws IOException {
        Path directory = repository.resolve("docs/reviews");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .sorted()
                    .toList();
        }
    }

    private static void writeInventory(Path output, List<SourceType> inventory) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, "Java source inventory v1\n" + render(inventory));
    }

    private static List<SourceType> inventory(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            List<Path> sources = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> included(root.relativize(path)))
                    .sorted()
                    .toList();
            return new JavaSourceInventory().scan(root, sources);
        }
    }

    private static boolean included(Path relative) {
        if (relative.startsWith("build") || relative.startsWith(Path.of("simulator", "build"))) {
            return false;
        }
        for (Path component : relative) {
            if (Set.of(".git", ".gradle").contains(component.toString())) {
                return false;
            }
        }
        return true;
    }

    private static String render(List<SourceType> inventory) {
        var result = new StringBuilder();
        for (SourceType type : inventory) {
            result.append(type.source())
                    .append('\t')
                    .append(type.identity())
                    .append('\t')
                    .append(type.sha256())
                    .append('\n');
        }
        return result.toString();
    }
}
