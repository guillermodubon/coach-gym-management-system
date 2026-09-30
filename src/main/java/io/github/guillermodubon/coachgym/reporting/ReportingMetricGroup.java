package io.github.guillermodubon.coachgym.reporting;

/**
 * Aggregate metric groups used for authorization before query composition.
 *
 * <p>Groups are intentionally fine-grained enough to keep the receptionist
 * allowlist limited to the two aggregate projections already visible in the
 * current dashboard.</p>
 */
public enum ReportingMetricGroup {
    FINANCIAL_SUMMARY,
    PAYMENT_DISTRIBUTION,
    FINANCIAL_TREND,
    CLIENT_SNAPSHOT,
    CLIENT_REGISTRATIONS,
    MEMBERSHIP_SUMMARY,
    MEMBERSHIP_DISTRIBUTION,
    MEMBERSHIP_REGISTRATIONS,
    ACCESS_SUMMARY,
    ACCESS_DETAIL,
    ACCESS_TREND,
    CREDENTIAL_USAGE,
    OPERATIONS,
    DURABLE_EMAIL_DELIVERY
}
