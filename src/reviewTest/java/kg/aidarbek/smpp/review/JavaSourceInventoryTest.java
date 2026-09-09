package kg.aidarbek.smpp.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaSourceInventoryTest {
    @TempDir
    Path root;

    @Test
    void inventoriesNamedTypesWithTheirExactSourceHash() throws Exception {
        Path source = root.resolve("Value.java");
        Files.writeString(source, "package example; record Value(int number) {}\n");

        assertEquals(
                List.of(new SourceType(
                        "Value.java",
                        "example.Value",
                        "461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1")),
                new JavaSourceInventory().scan(root, List.of(source)));
    }

    @Test
    void inventoriesMembersLocalsAnonymousClassesAndEnumConstantBodies() throws Exception {
        Path source = root.resolve("Outer.java");
        Files.writeString(source, """
                package example;
                class Outer {
                    interface Member {}
                    @interface Mark {}
                    enum Choice { FIRST { void action() {} } }
                    record Value(int number) {}
                    void run() {
                        class Local { class Child {} }
                        new Runnable() { public void run() {} };
                    }
                    Object field = new Object() {};
                    { class InitializerLocal {} }
                }
                """);

        assertEquals(
                List.of(
                                "example.Outer",
                                "example.Outer#<initializer>/InitializerLocal@12:7",
                                "example.Outer#field:field/<anonymous>@11:33",
                                "example.Outer#run/Local@8:9",
                                "example.Outer#run/Local@8:9.Child",
                                "example.Outer#run/<anonymous>@9:24",
                                "example.Outer.Choice",
                                "example.Outer.Choice#field:FIRST/<anonymous>@5:19",
                                "example.Outer.Mark",
                                "example.Outer.Member",
                                "example.Outer.Value")
                        .stream()
                        .sorted()
                        .toList(),
                new JavaSourceInventory()
                        .scan(root, List.of(source)).stream()
                                .map(SourceType::identity)
                                .toList());
    }

    @Test
    void rejectsCollidingTypeIdentitiesWithinOneSource() throws Exception {
        Path source = root.resolve("Duplicate.java");
        Files.writeString(source, "class Duplicate {} class Duplicate {}\n");

        assertThrows(IllegalArgumentException.class, () -> new JavaSourceInventory().scan(root, List.of(source)));
    }

    @Test
    void distinguishesIdenticalQualifiedTypesInDifferentSourceRoots() throws Exception {
        Path first = Files.createDirectories(root.resolve("src/main/java")).resolve("Value.java");
        Path second = Files.createDirectories(root.resolve("src/test/java")).resolve("Value.java");
        Files.writeString(first, "package example; class Value {}\n");
        Files.copy(first, second);

        assertEquals(
                List.of("src/main/java/Value.java", "src/test/java/Value.java"),
                new JavaSourceInventory()
                        .scan(root, List.of(second, first)).stream()
                                .map(SourceType::source)
                                .toList());
    }

    @Test
    void ignoresCommentsAndStringLiteralsButRejectsBrokenJava() throws Exception {
        Path source = root.resolve("Real.java");
        Files.writeString(source, "// class Comment {}\nclass Real { String text = \"class Literal {}\"; }\n");
        assertEquals(
                List.of("Real"),
                new JavaSourceInventory()
                        .scan(root, List.of(source)).stream()
                                .map(SourceType::identity)
                                .toList());

        Files.writeString(source, "class Broken {");
        assertThrows(IllegalArgumentException.class, () -> new JavaSourceInventory().scan(root, List.of(source)));
    }

    @Test
    void usesTheSameImmutableSnapshotForSyntaxAndHashAfterADiskEdit() throws Exception {
        Path source = root.resolve("Value.java");
        Files.writeString(source, "package example; record Value(int number) {}\n");
        JavaSourceSnapshot snapshot = JavaSourceSnapshot.read(root, source);
        Files.writeString(source, "package example; class Renamed {}\n");

        assertEquals(
                List.of(new SourceType(
                        "Value.java",
                        "example.Value",
                        "461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1")),
                new JavaSourceInventory().scanSnapshots(List.of(snapshot)));
    }

    @Test
    void syntaxFailuresIdentifyTheSourceAndLine() throws Exception {
        Path source = root.resolve("Broken.java");
        Files.writeString(source, "class Broken {");

        var failure = assertThrows(
                IllegalArgumentException.class, () -> new JavaSourceInventory().scan(root, List.of(source)));
        assertTrue(failure.getMessage().contains("Broken.java:1:"));
    }

    @Test
    void rejectsMalformedUtf8BeforeParsing() throws Exception {
        Path source = root.resolve("Invalid.java");
        Files.write(source, new byte[] {(byte) 0xc3, (byte) 0x28});

        assertThrows(CharacterCodingException.class, () -> JavaSourceSnapshot.read(root, source));
    }

    @Test
    void rejectsDuplicateSourceSnapshots() throws Exception {
        Path source = root.resolve("Value.java");
        Files.writeString(source, "package example; record Value(int number) {}\n");
        JavaSourceSnapshot snapshot = JavaSourceSnapshot.read(root, source);

        assertThrows(
                IllegalArgumentException.class,
                () -> new JavaSourceInventory().scanSnapshots(List.of(snapshot, snapshot)));
    }

    @Test
    void anEmptyInputHasNoInventedTypes() throws Exception {
        assertEquals(List.of(), new JavaSourceInventory().scan(root, List.of()));
    }
}
