package io.github.guillermodubon.coachgym.audit;

/** Safe boundary exception for an audit-export data access failure. */
public final class AuditExportDataAccessException extends RuntimeException {

    public static final String CODE = "AUDIT_EXPORT_DATA_ACCESS_FAILED";

    public AuditExportDataAccessException(Throwable cause) {
        super("Audit export data could not be read.", cause);
    }

    public String code() {
        return CODE;
    }
}
