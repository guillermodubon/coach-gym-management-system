package io.github.guillermodubon.coachgym.audit;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable safety policy for the administrative audit export boundary.
 *
 * <p>The defaults are deliberately conservative. A later configuration
 * adapter may provide a smaller or bounded value, but cannot exceed the hard
 * limits declared here.</p>
 */
public record AuditExportPolicy(Duration maxDateRange, int maxRows) {

    public static final Duration DEFAULT_MAX_DATE_RANGE = Duration.ofDays(31);
    public static final Duration HARD_MAX_DATE_RANGE = Duration.ofDays(366);
    public static final int DEFAULT_MAX_ROWS = 10_000;
    public static final int HARD_MAX_ROWS = 100_000;
    public static final boolean ADMIN_ONLY = true;
    public static final boolean UTF_8_BOM = false;
    public static final boolean OVERFLOW_REJECTED = true;

    public AuditExportPolicy {
        if (maxDateRange == null
                || maxDateRange.isZero()
                || maxDateRange.isNegative()
                || maxDateRange.compareTo(HARD_MAX_DATE_RANGE) > 0) {
            throw new AuditExportValidationException(
                    "Audit export maximum date range must be positive and no greater than 366 days.");
        }
        if (maxRows < 1 || maxRows > HARD_MAX_ROWS) {
            throw new AuditExportValidationException(
                    "Audit export maximum rows must be between 1 and 100000.");
        }
    }

    public static AuditExportPolicy defaults() {
        return new AuditExportPolicy(DEFAULT_MAX_DATE_RANGE, DEFAULT_MAX_ROWS);
    }

    /** Validates the required inclusive range without disclosing query data. */
    public void validateRange(Instant occurredFrom, Instant occurredUntil) {
        if (occurredFrom == null || occurredUntil == null) {
            throw AuditExportValidationException.rangeRequired();
        }
        if (occurredFrom.isAfter(occurredUntil)) {
            throw new AuditExportValidationException(
                    "occurredFrom must not be after occurredUntil.");
        }
        if (maxDateRange.compareTo(Duration.between(occurredFrom, occurredUntil)) < 0) {
            throw AuditExportValidationException.rangeTooLarge();
        }
    }

    public void validateRowCount(long rows) {
        if (rows < 0) {
            throw new AuditExportValidationException(
                    "Audit export row count must not be negative.");
        }
        if (rows > maxRows) {
            throw new AuditExportLimitExceededException(maxRows);
        }
    }
}
