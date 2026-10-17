package io.github.guillermodubon.coachgym.audit.infrastructure.csv;

import io.github.guillermodubon.coachgym.audit.AuditExportColumn;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/** Writes the fixed export header without taking ownership of the writer. */
public final class AuditCsvHeaderWriter {

    private final AuditCsvEncoder encoder;

    public AuditCsvHeaderWriter() {
        this(new AuditCsvEncoder());
    }

    public AuditCsvHeaderWriter(AuditCsvEncoder encoder) {
        this.encoder = Objects.requireNonNull(encoder);
    }

    public void write(Writer writer) throws IOException {
        Objects.requireNonNull(writer, "writer is required");
        AuditExportColumn[] columns = AuditExportColumn.values();
        for (int index = 0; index < columns.length; index++) {
            if (index > 0) {
                writer.write(AuditCsvEncoder.FIELD_SEPARATOR);
            }
            encoder.writeCell(writer, columns[index].header());
        }
        encoder.writeRecordSeparator(writer);
    }
}
