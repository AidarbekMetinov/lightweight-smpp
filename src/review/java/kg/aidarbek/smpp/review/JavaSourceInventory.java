package kg.aidarbek.smpp.review;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

final class JavaSourceInventory {
    List<SourceType> scan(Path root, List<Path> sources) throws IOException {
        var snapshots = new ArrayList<JavaSourceSnapshot>();
        for (Path source : sources) {
            snapshots.add(JavaSourceSnapshot.read(root, source));
        }
        return scanSnapshots(snapshots);
    }

    List<SourceType> scanSnapshots(List<JavaSourceSnapshot> snapshots) throws IOException {
        if (snapshots.isEmpty()) {
            return List.of();
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Review inventory requires a JDK with the Java compiler");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var result = new ArrayList<SourceType>();
        var byUri = new HashMap<URI, JavaSourceSnapshot>();
        for (JavaSourceSnapshot snapshot : snapshots) {
            if (byUri.putIfAbsent(snapshot.toUri(), snapshot) != null) {
                throw new IllegalArgumentException("Duplicate source path: " + snapshot.source());
            }
        }
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(
                    null, manager, diagnostics, List.of("-proc:none", "--release", "21"), null, snapshots);
            for (CompilationUnitTree unit : task.parse()) {
                JavaSourceSnapshot source = byUri.get(unit.getSourceFile().toUri());
                new TypeScanner(
                                source.source(),
                                source.sha256(),
                                unit,
                                Trees.instance(task).getSourcePositions(),
                                result)
                        .scan(unit, null);
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new IllegalArgumentException(
                        "Cannot parse Java source " + diagnostic.getSource().getName()
                                + ":" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber()
                                + ": " + diagnostic.getMessage(Locale.ROOT));
            }
        }
        result.sort(Comparator.comparing(SourceType::source).thenComparing(SourceType::identity));
        var unique = new HashSet<SourceType>();
        for (SourceType type : result) {
            if (!unique.add(type)) {
                throw new IllegalArgumentException("Duplicate type identity: " + type.source() + " " + type.identity());
            }
        }
        return List.copyOf(result);
    }

    private static final class TypeScanner extends TreePathScanner<Void, Void> {
        private final String source;
        private final String hash;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final List<SourceType> result;
        private final Map<ClassTree, String> identities = new IdentityHashMap<>();

        private TypeScanner(
                String source,
                String hash,
                CompilationUnitTree unit,
                SourcePositions positions,
                List<SourceType> result) {
            this.source = source;
            this.hash = hash;
            this.unit = unit;
            this.positions = positions;
            this.result = result;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            TreePath parent = getCurrentPath().getParentPath();
            TreePath enclosing = parent;
            while (enclosing != null && !(enclosing.getLeaf() instanceof ClassTree)) {
                enclosing = enclosing.getParentPath();
            }
            String name = node.getSimpleName().toString();
            String identity;
            if (enclosing == null) {
                identity = (unit.getPackageName() == null ? "" : unit.getPackageName() + ".") + name;
            } else if (parent.getLeaf() instanceof ClassTree) {
                identity = identities.get(enclosing.getLeaf()) + "." + name;
            } else {
                long start = positions.getStartPosition(unit, node);
                identity = identities.get(enclosing.getLeaf())
                        + "#" + enclosingMember(parent)
                        + "/" + (name.isEmpty() ? "<anonymous>" : name)
                        + "@" + unit.getLineMap().getLineNumber(start)
                        + ":" + unit.getLineMap().getColumnNumber(start);
            }
            identities.put(node, identity);
            result.add(new SourceType(source, identity, hash));
            return super.visitClass(node, unused);
        }

        private static String enclosingMember(TreePath path) {
            for (TreePath cursor = path; cursor != null; cursor = cursor.getParentPath()) {
                if (cursor.getLeaf() instanceof MethodTree method) {
                    return method.getName().toString();
                }
                if (cursor.getParentPath() != null && cursor.getParentPath().getLeaf() instanceof ClassTree) {
                    if (cursor.getLeaf() instanceof VariableTree field) {
                        return "field:" + field.getName();
                    }
                    if (cursor.getLeaf() instanceof BlockTree block && block.isStatic()) {
                        return "<static-initializer>";
                    }
                    return "<initializer>";
                }
            }
            throw new IllegalArgumentException("Local type has no enclosing member");
        }
    }
}
