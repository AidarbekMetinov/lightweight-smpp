package kg.aidarbek.simulator;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import kg.aidarbek.smpp.message.DeliveryReceipts;
import kg.aidarbek.smpp.message.MessageSegment;
import kg.aidarbek.smpp.message.MessageSegments;
import kg.aidarbek.smpp.message.ReassemblyKey;
import kg.aidarbek.smpp.message.ReceiptFormat;
import kg.aidarbek.smpp.message.ReceiptTlvs;
import kg.aidarbek.smpp.message.SegmentReassembler;
import kg.aidarbek.smpp.message.TextEncoding;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;

/**
 * Explicit deterministic message-helper traffic fixtures. Each receive stream owns a separate plan;
 * the simulator bounds their count for the run. Payload placement belongs to the operation adapter.
 * GSM fixtures explicitly agree on unpacked GSM at data_coding 0; that is a simulator convention,
 * not an interpretation imposed by SMPP or the wire codecs.
 */
final class HelperContentPlans {
    private static final OptionalParameters EMPTY_PARAMETERS = new OptionalParameters(List.of());

    private HelperContentPlans() {}

    /**
     * Returns the available exact CLI names in stable order.
     *
     * @return immutable variant names
     */
    public static List<String> names() {
        return List.of("gsm7", "ucs2", "sar", "receipt", "receipt-flexible", "receipt-tlv");
    }

    /**
     * Checks this simulator's content/operation pairing before starting endpoint resources. Role,
     * bind mode and version-specific payload limits remain operation-owned; a receive-only endpoint
     * skips this send check.
     * Replacement is excluded because it cannot declare the original message's encoding or flags.
     *
     * @param variant exact content name
     * @param operation CLI operation name
     * @return whether this content scenario supports the operation
     * @throws NullPointerException if either name is absent
     */
    public static boolean supports(String variant, String operation) {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(operation, "operation");
        if (!names().contains(variant)) return false;
        return switch (operation) {
            case "deliver", "data" -> true;
            case "submit", "multi" -> !variant.startsWith("receipt");
            default -> false;
        };
    }

    /**
     * Creates one bounded, thread-safe deterministic fixture. Text variants use an exact encoded
     * length (UCS-2 must be even). SAR uses 161..39015 total unpacked GSM octets that fit 255 parts,
     * and cycles the same fixed reference. Receipt variants use the final text-field length:
     * 0..20 for receipt and 0..2000 for receipt-flexible. Receipt-tlv requires zero payload.
     *
     * @param variant exact name from {@link #names()}
     * @param payloadBytes explicitly interpreted size for the selected variant
     * @param seed deterministic text offset or receipt/reference identity
     * @return new per-stream fixture plan
     * @throws IllegalArgumentException for an unknown variant or invalid size
     * @throws NullPointerException for a missing variant
     */
    public static ContentPlan create(String variant, int payloadBytes, long seed) {
        Objects.requireNonNull(variant, "variant");
        if (payloadBytes < 0 || payloadBytes > 65535)
            throw new IllegalArgumentException("Payload must contain 0..65535 encoded octets");
        return switch (variant) {
            case "gsm7" -> text(TextEncoding.GSM7_UNPACKED, payloadBytes, seed);
            case "ucs2" -> text(TextEncoding.UCS2, payloadBytes, seed);
            case "sar" -> new SarPlan(payloadBytes, seed);
            case "receipt" -> receipt(payloadBytes, seed, ReceiptFormat.EXAMPLE);
            case "receipt-flexible" -> receipt(payloadBytes, seed, ReceiptFormat.FLEXIBLE);
            case "receipt-tlv" -> receiptTlv(payloadBytes, seed);
            default -> throw new IllegalArgumentException("Unknown helper content variant");
        };
    }

    private static ContentPlan text(TextEncoding encoding, int bytes, long seed) {
        String sample = sample(encoding, bytes, seed);
        return new FixedPlan(
                new TrafficContent(0, encoding == TextEncoding.UCS2 ? 8 : 0, encoding.encode(sample), EMPTY_PARAMETERS),
                content -> encoding.decode(content.payload()));
    }

    private static ContentPlan receipt(int textBytes, long seed, ReceiptFormat format) {
        int limit = format == ReceiptFormat.EXAMPLE ? 20 : 2000;
        if (textBytes > limit)
            throw new IllegalArgumentException("Receipt payload option measures its text field: maximum " + limit);
        String id = HexFormat.of().toHexDigits(seed);
        Map<String, String> fields = format == ReceiptFormat.EXAMPLE
                ? Map.of(
                        "id",
                        id,
                        "sub",
                        "001",
                        "dlvrd",
                        "001",
                        "submit date",
                        "2609090000",
                        "done date",
                        "2609090001",
                        "stat",
                        "DELIVRD",
                        "err",
                        "000",
                        "text",
                        "x".repeat(textBytes))
                : Map.of(
                        "id",
                        id,
                        "submit date",
                        "202609090000",
                        "stat",
                        "PROVIDER_PENDING",
                        "provider_code",
                        "00A",
                        "text",
                        "x".repeat(textBytes));
        String raw = DeliveryReceipts.build(fields, format);
        return new FixedPlan(
                new TrafficContent(4, 1, new OctetString(raw.getBytes(StandardCharsets.US_ASCII)), EMPTY_PARAMETERS),
                content -> DeliveryReceipts.parse(
                        new String(content.payload().value(), StandardCharsets.US_ASCII), format));
    }

    private static ContentPlan receiptTlv(int bytes, long seed) {
        if (bytes != 0) throw new IllegalArgumentException("TLV-only receipt requires payload option 0");
        OptionalParameters parameters = ReceiptTlvs.build(
                Optional.of(HexFormat.of().toHexDigits(seed)),
                OptionalInt.of(2),
                Optional.of(new OctetString(new byte[] {3, 0, 0})));
        return new FixedPlan(
                new TrafficContent(4, 1, new OctetString(new byte[0]), parameters),
                content -> ReceiptTlvs.read(content.parameters()));
    }

    private static String sample(TextEncoding encoding, int bytes, long seed) {
        if (encoding == TextEncoding.UCS2 && (bytes & 1) != 0)
            throw new IllegalArgumentException("UCS-2 payload must have an even encoded length");
        String alphabet = encoding == TextEncoding.UCS2 ? "AΩЖ漢" : "A£Δä^{}\\[~]|€";
        int position = Math.floorMod(seed, alphabet.length());
        StringBuilder result = new StringBuilder(bytes);
        while (bytes > 0) {
            String unit = alphabet.substring(position, position + 1);
            int size = encoding.encodedLength(unit);
            if (size > bytes) {
                unit = "A";
                size = 1;
            }
            result.append(unit);
            bytes -= size;
            position = (position + 1) % alphabet.length();
        }
        return result.toString();
    }

    private record FixedPlan(TrafficContent expected, Consumer<TrafficContent> inspect) implements ContentPlan {
        @Override
        public TrafficContent next(long index) {
            return expected;
        }

        @Override
        public void validate(TrafficContent content) {
            if (!expected.equals(content)) throw new IllegalArgumentException("Unexpected helper fixture content");
            inspect.accept(content);
        }
    }

    /** One fixed multipart message; every later cycle deliberately repeats the same fragments. */
    private static final class SarPlan implements ContentPlan {
        private final List<MessageSegment> parts;
        private final OctetString expected;
        private final ReassemblyKey key;
        private final SegmentReassembler reassembler;
        private boolean seen;
        private boolean complete;

        SarPlan(int bytes, long seed) {
            if (bytes < 161 || bytes > 39015)
                throw new IllegalArgumentException("SAR fixture needs 161..39015 encoded octets and at most 255 parts");
            String message = sample(TextEncoding.GSM7_UNPACKED, bytes, seed);
            int reference = (int) (seed & 65535);
            parts = MessageSegments.split(message, TextEncoding.GSM7_UNPACKED, reference, 160, 153);
            expected = TextEncoding.GSM7_UNPACKED.encode(message);
            key = new ReassemblyKey("fixed-simulator-fixture", reference);
            // A run owns one fixed reference; the paused fixture clock deliberately retains deduplication.
            reassembler = new SegmentReassembler(1, 255, bytes, bytes, Duration.ofNanos(1), () -> 0L);
        }

        @Override
        public TrafficContent next(long index) {
            MessageSegment part = parts.get(Math.floorMod(index, parts.size()));
            return new TrafficContent(0, 0, part.payload(), part.sarParameters());
        }

        @Override
        public synchronized void validate(TrafficContent content) {
            MessageSegment part = MessageSegments.fromSar(content.payload(), content.parameters())
                    .orElseThrow(() -> new IllegalArgumentException("Expected SAR parameters"));
            if (content.esmClass() != 0 || content.dataCoding() != 0 || part.number() > parts.size())
                throw new IllegalArgumentException("Unexpected SAR fixture metadata");
            MessageSegment expectedPart = parts.get(part.number() - 1);
            if (!expectedPart.equals(part) || !expectedPart.sarParameters().equals(content.parameters()))
                throw new IllegalArgumentException("Unexpected SAR fixture content");
            var assembled = reassembler.accept(key, part);
            if (assembled.isPresent()) {
                if (!expected.equals(assembled.orElseThrow()))
                    throw new IllegalArgumentException("Unexpected reassembled fixture content");
                complete = true;
            }
            seen = true;
        }

        @Override
        public synchronized int incompleteAssemblies() {
            return seen && !complete ? 1 : 0;
        }
    }
}
