package io.github.guillermodubon.coachgym.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, JSON-library-neutral metadata projection.
 *
 * <p>Only deterministic scalar values, nested maps with string keys, and
 * lists are accepted. Arrays, arbitrary objects, and keys denied by the
 * global privacy policy fail closed. The action-aware sanitizer constructs
 * this value before it crosses an application or HTTP boundary.</p>
 */
public record AuditMetadataProjection(Map<String, Object> values, boolean redacted) {

    public AuditMetadataProjection {
        values = freezeMap(values == null ? Map.of() : values);
    }

    /** An empty projection with no redacted fields. */
    public static AuditMetadataProjection empty() {
        return new AuditMetadataProjection(Map.of(), false);
    }

    /** Indicates whether a later privacy projection removed any fields. */
    public boolean metadataRedacted() {
        return redacted;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    private static Map<String, Object> freezeMap(Map<?, ?> source) {
        Map<String, Object> normalizedSource = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object rawKey = entry.getKey();
            if (!(rawKey instanceof String key) || key.strip().isEmpty()) {
                throw new AuditQueryValidationException(
                        "Audit metadata keys must be non-blank strings.");
            }
            String normalizedKey = key.strip();
            if (AuditMetadataPolicy.isSensitiveKey(normalizedKey)) {
                throw new AuditQueryValidationException(
                        "Sensitive audit metadata keys are not public.");
            }
            if (normalizedSource.containsKey(normalizedKey)) {
                throw new AuditQueryValidationException(
                        "Audit metadata keys must be unique after normalization.");
            }
            normalizedSource.put(normalizedKey, entry.getValue());
        }
        List<String> keys = new ArrayList<>(normalizedSource.keySet());
        Collections.sort(keys);

        Map<String, Object> frozen = new LinkedHashMap<>();
        for (String key : keys) {
            Object value = normalizedSource.get(key);
            frozen.put(key, freezeValue(value));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static Object freezeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Number
                || value instanceof UUID
                || value instanceof Instant) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return freezeMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(freezeValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        throw new AuditQueryValidationException(
                "Unsupported audit metadata value type.");
    }
}
