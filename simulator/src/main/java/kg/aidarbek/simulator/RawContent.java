package kg.aidarbek.simulator;

import java.util.List;
import java.util.SplittableRandom;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;

/** Deterministic uninterpreted octets for the initial simulator workload. */
final class RawContent implements ContentPlan {
    private final int bytes;
    private final long seed;

    public RawContent(int bytes, long seed) {
        if (bytes < 0 || bytes > 65535) throw new IllegalArgumentException("Payload must contain 0..65535 octets");
        this.bytes = bytes;
        this.seed = seed;
    }

    @Override
    public TrafficContent next(long index) {
        byte[] value = new byte[bytes];
        var random = new SplittableRandom(seed + index * 0x9e3779b97f4a7c15L);
        for (int i = 0; i < value.length; i++) value[i] = (byte) random.nextInt(256);
        return new TrafficContent(0, 4, new OctetString(value), new OptionalParameters(List.of()));
    }

    @Override
    public void validate(TrafficContent content) {
        if (content.payload().length() != bytes
                || content.esmClass() != 0
                || content.dataCoding() != 4
                || !content.parameters().entries().isEmpty())
            throw new IllegalArgumentException("Unexpected raw content shape");
    }
}
