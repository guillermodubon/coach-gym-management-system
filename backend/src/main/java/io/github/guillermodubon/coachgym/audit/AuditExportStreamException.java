package io.github.guillermodubon.coachgym.audit;

/** Safe failure raised when the caller-owned export stream cannot be written. */
public final class AuditExportStreamException extends RuntimeException {

    public static final String CODE = "AUDIT_EXPORT_STREAM_FAILED";

    public AuditExportStreamException() {
        super("Audit export could not be streamed.");
    }

    public AuditExportStreamException(Throwable cause) {
        super("Audit export could not be streamed.", cause);
    }

    public String code() {
        return CODE;
    }
}
