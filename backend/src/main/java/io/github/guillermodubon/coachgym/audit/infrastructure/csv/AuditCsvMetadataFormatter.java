package io.github.guillermodubon.coachgym.audit.infrastructure.csv;

import io.github.guillermodubon.coachgym.audit.AuditMetadataProjection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Formats only the existing sanitized metadata projection into a bounded value. */
public final class AuditCsvMetadataFormatter {

    public static final int MAX_METADATA_LENGTH = 4_096;
    private static final String TRUNCATION_MARKER = "...[truncated]";

    /**
     * Produces deterministic JSON-like metadata without accepting raw JSON or
     * creating dynamic CSV columns.
     */
    public String format(AuditMetadataProjection projection) {
        if (projection == null) {
            return "";
        }

        Map<String, Object> values = new java.util.TreeMap<>(projection.values());
        if (projection.metadataRedacted()) {
            values.put("_redacted", true);
        }
        return bound(formatMap(values));
    }

    private static String formatMap(Map<String, ?> values) {
        StringBuilder result = new StringBuilder("{");
        List<Map.Entry<String, ?>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            Map.Entry<String, ?> entry = entries.get(index);
            appendQuoted(result, entry.getKey());
            result.append(':');
            appendValue(result, entry.getValue());
        }
        return result.append('}').toString();
    }

    private static void appendValue(StringBuilder result, Object value) {
        if (value == null) {
            result.append("null");
        } else if (value instanceof String string) {
            appendQuoted(result, string);
        } else if (value instanceof UUID || value instanceof Instant) {
            appendQuoted(result, value.toString());
        } else if (value instanceof Number || value instanceof Boolean) {
            result.append(value);
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new java.util.TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    normalized.put(key, entry.getValue());
                }
            }
            result.append(formatMap(normalized));
        } else if (value instanceof List<?> list) {
            result.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    result.append(',');
                }
                appendValue(result, list.get(index));
            }
            result.append(']');
        } else {
            // AuditMetadataProjection rejects this type; fail closed if a
            // future implementation introduces an unsupported value.
            result.append("null");
        }
    }

    private static void appendQuoted(StringBuilder result, String value) {
        result.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\r' -> result.append("\\r");
                case '\n' -> result.append("\\n");
                case '\t' -> result.append("\\t");
                default -> {
                    if (Character.isISOControl(character)) {
                        appendUnicodeEscape(result, character);
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        result.append('"');
    }

    private static void appendUnicodeEscape(StringBuilder result, char character) {
        final char[] digits = "0123456789abcdef".toCharArray();
        result.append("\\u")
                .append(digits[(character >>> 12) & 0x0f])
                .append(digits[(character >>> 8) & 0x0f])
                .append(digits[(character >>> 4) & 0x0f])
                .append(digits[character & 0x0f]);
    }

    private static String bound(String value) {
        if (value.length() <= MAX_METADATA_LENGTH) {
            return value;
        }
        int end = MAX_METADATA_LENGTH - TRUNCATION_MARKER.length();
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + TRUNCATION_MARKER;
    }
}
