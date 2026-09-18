package io.github.guillermodubon.coachgym.audit;

/** Raised when an audit export would exceed its server-side row limit. */
public final class AuditExportLimitExceededException extends RuntimeException {

    public static final String CODE = "AUDIT_EXPORT_LIMIT_EXCEEDED";

    private final int maximumRows;

    public AuditExportLimitExceededException(int maximumRows) {
        super("The audit export exceeds the configured row limit.");
        if (maximumRows < 1) {
            throw new IllegalArgumentException("Audit export maximum rows must be positive.");
        }
        this.maximumRows = maximumRows;
    }

    public String code() {
        return CODE;
    }

    public int maximumRows() {
        return maximumRows;
    }
}
