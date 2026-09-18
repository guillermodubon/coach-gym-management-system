package io.github.guillermodubon.coachgym.audit.infrastructure.csv;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Dependency-light RFC-compatible encoder for individual CSV cells. */
public final class AuditCsvEncoder {

    public static final char FIELD_SEPARATOR = ',';
    public static final String RECORD_SEPARATOR = "\r\n";
    public static final java.nio.charset.Charset CHARSET = StandardCharsets.UTF_8;

    private final AuditCsvCellSafetyPolicy cellSafetyPolicy;

    public AuditCsvEncoder() {
        this(new AuditCsvCellSafetyPolicy());
    }

    public AuditCsvEncoder(AuditCsvCellSafetyPolicy cellSafetyPolicy) {
        this.cellSafetyPolicy = Objects.requireNonNull(cellSafetyPolicy);
    }

    /** Returns one encoded cell; it does not add a record separator. */
    public String encode(Object value) {
        return encodeCell(cellSafetyPolicy.protectValue(value));
    }

    /** Writes one encoded cell and never closes or flushes the caller's writer. */
    public void writeCell(Writer writer, Object value) throws IOException {
        Objects.requireNonNull(writer, "writer is required")
                .write(encode(value));
    }

    public void writeRecordSeparator(Writer writer) throws IOException {
        Objects.requireNonNull(writer, "writer is required")
                .write(RECORD_SEPARATOR);
    }

    private static String encodeCell(String value) {
        boolean requiresQuotes = value.indexOf(FIELD_SEPARATOR) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;
        if (!requiresQuotes) {
            return value;
        }

        StringBuilder encoded = new StringBuilder(value.length() + 2);
        encoded.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"') {
                encoded.append('"');
            }
            encoded.append(character);
        }
        encoded.append('"');
        return encoded.toString();
    }
}
