package io.github.guillermodubon.coachgym.audit;

/** Safe failure raised when the completed export cannot be audited. */
public final class AuditExportAuditException extends RuntimeException {

    public static final String CODE = "AUDIT_EXPORT_AUDIT_FAILED";

    public AuditExportAuditException(Throwable cause) {
        super("Audit export could not be recorded.", cause);
    }

    public String code() {
        return CODE;
    }
}
