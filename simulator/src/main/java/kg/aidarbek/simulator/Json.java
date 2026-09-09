package kg.aidarbek.simulator;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Small tool-only JSON encoder for bounded report values, never arbitrary object diagnostics. */
final class Json {
    private Json() {}

    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return string(text);
        if (value instanceof Enum<?> constant) return string(constant.name());
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number number) {
            if (!(number instanceof Byte
                            || number instanceof Short
                            || number instanceof Integer
                            || number instanceof Long
                            || number instanceof Float
                            || number instanceof Double)
                    || !Double.isFinite(number.doubleValue()))
                throw new IllegalArgumentException("Unsupported or nonfinite report number");
            return number.toString();
        }
        if (value instanceof Map<?, ?> map) {
            var joined = new StringJoiner(",", "{", "}");
            for (var entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String || key instanceof Enum<?> || key instanceof Long))
                    throw new IllegalArgumentException("Unsupported report key");
                joined.add(string(key.toString()) + ":" + encode(entry.getValue()));
            }
            return joined.toString();
        }
        if (value instanceof List<?> list) {
            var joined = new StringJoiner(",", "[", "]");
            for (Object element : list) joined.add(encode(element));
            return joined.toString();
        }
        if (value.getClass().isRecord()) {
            var joined = new StringJoiner(",", "{", "}");
            try {
                for (RecordComponent component : value.getClass().getRecordComponents())
                    joined.add(string(component.getName()) + ":"
                            + encode(component.getAccessor().invoke(value)));
                return joined.toString();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Report record access failed", failure);
            }
        }
        throw new IllegalArgumentException("Unsupported report value");
    }

    private static String string(String value) {
        var result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 32 || Character.isSurrogate(character))
                        result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }
}
