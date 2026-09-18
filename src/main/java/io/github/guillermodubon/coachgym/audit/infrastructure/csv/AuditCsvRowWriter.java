package io.github.guillermodubon.coachgym.audit.infrastructure.csv;

import io.github.guillermodubon.coachgym.audit.AuditExportRow;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/** Writes one safe projected audit row incrementally. */
public final class AuditCsvRowWriter {

    private final AuditCsvEncoder encoder;
    private final AuditCsvMetadataFormatter metadataFormatter;

    public AuditCsvRowWriter() {
        this(new AuditCsvEncoder(), new AuditCsvMetadataFormatter());
    }

    public AuditCsvRowWriter(
            AuditCsvEncoder encoder,
            AuditCsvMetadataFormatter metadataFormatter) {
        this.encoder = Objects.requireNonNull(encoder);
        this.metadataFormatter = Objects.requireNonNull(metadataFormatter);
    }

    public void write(Writer writer, AuditExportRow row) throws IOException {
        Objects.requireNonNull(writer, "writer is required");
        Objects.requireNonNull(row, "row is required");
        Object[] values = {
            row.entryId(),
            row.occurredAt(),
            row.actorUserId(),
            row.actorIdentifier(),
            row.actionCode(),
            row.resourceType(),
            row.resourceId(),
            row.resourceCode(),
            row.summary(),
            row.correlationId(),
            metadataFormatter.format(row.metadata())
        };
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                writer.write(AuditCsvEncoder.FIELD_SEPARATOR);
            }
            encoder.writeCell(writer, values[index]);
        }
        encoder.writeRecordSeparator(writer);
    }
}
