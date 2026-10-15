package io.github.guillermodubon.coachgym.audit;

/** Raised when an audit-export request violates its public policy. */
public final class AuditExportValidationException extends RuntimeException {

    public static final String DEFAULT_CODE = "AUDIT_EXPORT_VALIDATION_FAILED";
    public static final String RANGE_REQUIRED_CODE = "AUDIT_EXPORT_RANGE_REQUIRED";
    public static final String RANGE_TOO_LARGE_CODE = "AUDIT_EXPORT_RANGE_TOO_LARGE";

    private final String code;

    public AuditExportValidationException(String message) {
        this(DEFAULT_CODE, message);
    }

    public AuditExportValidationException(String code, String message) {
        super(message);
        this.code = requireCode(code);
    }

    public String code() {
        return code;
    }

    static AuditExportValidationException rangeRequired() {
        return new AuditExportValidationException(
                RANGE_REQUIRED_CODE,
                "Both occurredFrom and occurredUntil are required for an audit export.");
    }

    static AuditExportValidationException rangeTooLarge() {
        return new AuditExportValidationException(
                RANGE_TOO_LARGE_CODE,
                "The audit export date range exceeds the configured maximum.");
    }

    private static String requireCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Audit export error code is required.");
        }
        return value;
    }
}
