package io.github.guillermodubon.coachgym.audit;

/** Safe result metadata returned after a bounded audit export completes. */
public record AuditExportResult(
        long rowsExported,
        AuditExportQuery query,
        String filename,
        String mediaType) {

    /** Compatibility constructor for the technology-neutral Block 1 contract. */
    public AuditExportResult(long rowsExported, AuditExportQuery query) {
        this(rowsExported, query, null, null);
    }

    public AuditExportResult {
        if (rowsExported < 0) {
            throw new AuditExportValidationException(
                    "Audit export row count must not be negative.");
        }
        if (query == null) {
            throw new AuditExportValidationException(
                    "Audit export query is required.");
        }
        if (filename != null && !filename.matches("audit-export-[0-9]{8}-[0-9]{6}Z\\.csv")) {
            throw new AuditExportValidationException(
                    "Audit export filename is invalid.");
        }
        if (mediaType != null && !"text/csv;charset=UTF-8".equals(mediaType)) {
            throw new AuditExportValidationException(
                    "Audit export media type is invalid.");
        }
    }
}
