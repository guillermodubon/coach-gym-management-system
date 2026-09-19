package io.github.guillermodubon.coachgym.audit.infrastructure.csv;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies the cell-level safety policy before a value is RFC-compatible CSV
 * encoded.
 *
 * <p>Typed identifiers, timestamps, numbers, booleans, and enums retain their
 * canonical representation. Textual values are protected against spreadsheet
 * formula execution when their first effective character is dangerous after
 * leading whitespace or control characters.</p>
 */
public final class AuditCsvCellSafetyPolicy {

    public static final int MAX_CELL_LENGTH = 8_192;
    private static final String FORMULA_PREFIX = "'";

    /** Protects a human/textual cell and returns an empty value for {@code null}. */
    public String protectText(String value) {
        if (value == null) {
            return "";
        }
        String bounded = bound(value);
        if (startsWithFormulaCharacter(bounded)) {
            bounded = FORMULA_PREFIX + bounded;
            bounded = bound(bounded);
        }
        return bounded;
    }

    /** Formats a typed value without treating safe numeric/identifier values as formulas. */
    public String protectValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number
                || value instanceof Boolean
                || value instanceof UUID
                || value instanceof Instant
                || value instanceof Enum<?>) {
            return bound(value.toString());
        }
        return protectText(value.toString());
    }

    private static boolean startsWithFormulaCharacter(String value) {
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            if (!Character.isWhitespace(codePoint)
                    && !Character.isSpaceChar(codePoint)
                    && !Character.isISOControl(codePoint)
                    && Character.getType(codePoint) != Character.FORMAT) {
                return codePoint == '='
                        || codePoint == '+'
                        || codePoint == '-'
                        || codePoint == '@';
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static String bound(String value) {
        if (value.length() <= MAX_CELL_LENGTH) {
            return value;
        }
        int end = MAX_CELL_LENGTH;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
