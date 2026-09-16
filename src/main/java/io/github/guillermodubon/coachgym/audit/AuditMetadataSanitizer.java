package io.github.guillermodubon.coachgym.audit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fail-closed, deterministic sanitizer for audit JSONB metadata.
 *
 * <p>The sanitizer applies the positive action-family allowlist before the
 * global sensitive-key denylist. It copies only safe scalar values, bounded
 * lists, and bounded nested maps; source metadata is never mutated. Any
 * unsupported or over-limit value is omitted and marks the result redacted.
 * A sanitizer failure returns an empty redacted projection rather than raw
 * metadata.</p>
 */
public final class AuditMetadataSanitizer {

    public static final int MAX_DEPTH = 3;
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_TOTAL_ENTRIES = 256;
    public static final int MAX_STRING_LENGTH = 256;
    public static final int MAX_TOTAL_CHARACTERS = 4_096;

    /** Sanitizes metadata for one persisted action code. */
    public AuditMetadataProjection sanitize(
            String actionCode,
            Map<String, Object> sourceMetadata) {
        if (sourceMetadata == null || sourceMetadata.isEmpty()) {
            return AuditMetadataProjection.empty();
        }

        SanitizationContext context = new SanitizationContext();
        try {
            Set<String> allowlist = AuditMetadataPolicy.allowedKeysForAction(actionCode);
            Map<String, Object> sanitized = sanitizeMap(
                    sourceMetadata, allowlist, 0, context);
            return new AuditMetadataProjection(sanitized, context.redacted);
        } catch (RuntimeException failure) {
            return new AuditMetadataProjection(Map.of(), true);
        }
    }

    private static Map<String, Object> sanitizeMap(
            Map<?, ?> source,
            Set<String> allowlist,
            int depth,
            SanitizationContext context) {
        if (depth > MAX_DEPTH) {
            context.redacted = true;
            return Map.of();
        }

        List<String> keys = new ArrayList<>();
        Map<String, Object> normalizedSource = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String rawKey)) {
                context.redacted = true;
                continue;
            }
            String key = rawKey.strip();
            if (key.isEmpty() || AuditMetadataPolicy.isSensitiveKey(key)
                    || !allowlist.contains(key)) {
                context.redacted = true;
                continue;
            }
            if (normalizedSource.containsKey(key)) {
                context.redacted = true;
                continue;
            }
            if (context.entries >= MAX_TOTAL_ENTRIES) {
                context.redacted = true;
                break;
            }
            context.entries++;
            normalizedSource.put(key, entry.getValue());
            keys.add(key);
        }

        Collections.sort(keys);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) {
            Object sanitized = sanitizeValue(
                    key, normalizedSource.get(key), allowlist, depth, context);
            if (sanitized != Omitted.VALUE) {
                result.put(key, sanitized);
            }
        }
        return result;
    }

    private static Object sanitizeValue(
            String key,
            Object value,
            Set<String> allowlist,
            int depth,
            SanitizationContext context) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return sanitizeString(key, string, context);
        }
        if (value instanceof UUID
                || value instanceof Instant
                || value instanceof Boolean
                || value instanceof BigDecimal
                || value instanceof BigInteger
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            if (depth >= MAX_DEPTH) {
                context.redacted = true;
                return Omitted.VALUE;
            }
            return sanitizeMap(map, allowlist, depth + 1, context);
        }
        if (value instanceof List<?> list) {
            if (depth >= MAX_DEPTH) {
                context.redacted = true;
                return Omitted.VALUE;
            }
            int limit = Math.min(list.size(), MAX_ENTRIES);
            if (list.size() > limit) {
                context.redacted = true;
            }
            List<Object> values = new ArrayList<>(limit);
            for (int index = 0; index < limit; index++) {
                if (context.entries >= MAX_TOTAL_ENTRIES) {
                    context.redacted = true;
                    break;
                }
                context.entries++;
                Object item = sanitizeValue(
                        key, list.get(index), allowlist, depth + 1, context);
                if (item != Omitted.VALUE) {
                    values.add(item);
                }
            }
            return Collections.unmodifiableList(values);
        }

        // Arrays, byte buffers, JSON nodes, arbitrary objects, and binary
        // payloads are deliberately excluded from the public projection.
        context.redacted = true;
        return Omitted.VALUE;
    }

    private static Object sanitizeString(
            String key,
            String source,
            SanitizationContext context) {
        String value = source.strip();
        if (isBinaryLike(value)) {
            context.redacted = true;
            return Omitted.VALUE;
        }
        if ("maskedRecipient".equals(key)) {
            String masked = maskEmail(value);
            if (!masked.equals(value)) {
                context.redacted = true;
            }
            value = masked;
        }
        if (value.length() > MAX_STRING_LENGTH) {
            value = value.substring(0, MAX_STRING_LENGTH);
            context.redacted = true;
        }
        int remaining = MAX_TOTAL_CHARACTERS - context.characters;
        if (remaining <= 0) {
            context.redacted = true;
            return Omitted.VALUE;
        }
        if (value.length() > remaining) {
            value = value.substring(0, remaining);
            context.redacted = true;
        }
        context.characters += value.length();
        return value;
    }

    private static boolean isBinaryLike(String value) {
        if (value.startsWith("data:")) {
            return true;
        }
        if (value.length() < 64) {
            return false;
        }
        int encodedCharacters = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)
                    || character == '+' || character == '/' || character == '=') {
                encodedCharacters++;
            }
        }
        return encodedCharacters * 100 / value.length() >= 96;
    }

    private static String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1 || value.indexOf('@', at + 1) >= 0) {
            return "[redacted-email]";
        }
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        String maskedLocal = local.length() <= 1
                ? "*"
                : local.substring(0, 1) + "***";
        return maskedLocal + "@" + domain;
    }

    private enum Omitted {
        VALUE
    }

    private static final class SanitizationContext {
        private int entries;
        private int characters;
        private boolean redacted;
    }
}
