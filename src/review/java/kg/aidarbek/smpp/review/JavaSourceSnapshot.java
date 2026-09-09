package kg.aidarbek.smpp.review;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.tools.SimpleJavaFileObject;

/** An immutable source snapshot adapted to the compiler without rereading the file. */
final class JavaSourceSnapshot extends SimpleJavaFileObject {
    private final String source;
    private final String content;
    private final String sha256;

    private JavaSourceSnapshot(Path file, String source, byte[] bytes) throws IOException {
        super(file.toUri(), Kind.SOURCE);
        this.source = source;
        this.content = StandardCharsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        try {
            this.sha256 = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide mandatory SHA-256", exception);
        }
    }

    static JavaSourceSnapshot read(Path root, Path file) throws IOException {
        Path repository = root.toAbsolutePath().normalize();
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(repository)) {
            throw new IllegalArgumentException("Java source lies outside the repository: " + file);
        }
        return new JavaSourceSnapshot(
                absolute, repository.relativize(absolute).toString().replace('\\', '/'), Files.readAllBytes(absolute));
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }

    @Override
    public String getName() {
        return source;
    }

    String source() {
        return source;
    }

    String sha256() {
        return sha256;
    }
}
