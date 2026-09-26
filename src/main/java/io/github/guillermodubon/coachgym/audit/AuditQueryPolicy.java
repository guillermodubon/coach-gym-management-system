package io.github.guillermodubon.coachgym.audit;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Executable policy for the audit-query boundary.
 *
 * <p>This class records the Block 1 ADR decisions in a source-level,
 * testable form. The audit module owns both the write model and these read
 * contracts. Query access is ADMIN-only; RECEPTIONIST and anonymous callers
 * are not granted an audit-history capability. List and detail projections
 * use persisted actor/resource snapshots and never enrich rows through live
 * joins. Lists contain summaries only; details may contain a separately
 * sanitized metadata projection.</p>
 *
 * <p>Filtering is exact and allowlisted. The only sort is occurred time with
 * an ID tie-breaker. Page indexes are zero-based, page sizes are bounded to
 * 100, and date bounds are inclusive. A non-null range may span at most 366
 * days. There is no arbitrary metadata filter and no result filter because
 * the current schema represents outcomes in action codes and metadata rather
 * than a result column. Audit reads are not self-audited by default to avoid
 * recursive writes and unbounded noise. Bounded CSV export uses the separate
 * {@link AuditExportPolicy} while reusing these filter and sort allowlists.</p>
 *
 * <p>Metadata follows a default-deny, action-aware allowlist and a global
 * sensitive-key denylist. Recursive sanitization and persistence projections
 * remain internal to the audit module. Query execution uses the existing
 * selective indexes plus the minimal composite index required for the
 * deterministic newest-first order. Provider, persistence, JSON-library, and
 * framework types are deliberately absent from this public policy.</p>
 */
public final class AuditQueryPolicy {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;
    public static final Duration MAX_DATE_RANGE = Duration.ofDays(366);
    public static final AuditSortField DEFAULT_SORT = AuditSortField.OCCURRED_AT;
    public static final AuditSortDirection DEFAULT_DIRECTION = AuditSortDirection.DESC;
    public static final boolean ADMIN_ONLY = true;
    public static final boolean SELF_AUDITING_ENABLED = false;

    private static final Set<String> ALLOWED_RESOURCE_TYPES = immutableSet(
            "ACCESS_CREDENTIAL",
            "ACCESS_RECORD",
            "AUDIT_EXPORT",
            "CLIENT",
            "EQUIPMENT",
            "EQUIPMENT_CATEGORY",
            "EMAIL_DELIVERY",
            "INCIDENT",
            "MAINTENANCE",
            "MEMBERSHIP",
            "MEMBERSHIP_PERIOD",
            "MEMBERSHIP_PLAN",
            "ORGANIZATION",
            "GYM_BRANCH",
            "PAYMENT",
            "PAYMENT_ATTEMPT",
            "PAYMENT_RECEIPT",
            "PROMOTION",
            "SETTINGS",
            "STAFF_PROFILE");

    private static final Set<String> ALLOWED_ACTION_CODES = immutableSet(
            "ACCESS_DENIED",
            "ACCESS_PAYMENT_POLICY_CHANGED",
            "BRANCH_ACCESS_PAYMENT_POLICY_CHANGED",
            "ACCESS_CREDENTIAL_ISSUED",
            "ACCESS_CREDENTIAL_REPLACED",
            "ACCESS_CREDENTIAL_REVOKED",
            "AUDIT_ENTRIES_EXPORTED",
            "CLIENT_REGISTERED",
            "EMAIL_DELIVERY_FAILED",
            "EMAIL_DELIVERY_RETRIED",
            "EMAIL_DELIVERY_SENT",
            "EQUIPMENT_CATEGORY_ACTIVATED",
            "EQUIPMENT_CATEGORY_CREATED",
            "EQUIPMENT_CATEGORY_DEACTIVATED",
            "EQUIPMENT_CATEGORY_UPDATED",
            "EQUIPMENT_REGISTERED",
            "EQUIPMENT_STATUS_CHANGED_TO_AVAILABLE",
            "EQUIPMENT_STATUS_CHANGED_TO_MAINTENANCE",
            "EQUIPMENT_STATUS_CHANGED_TO_OUT_OF_SERVICE",
            "EQUIPMENT_STATUS_CHANGED_TO_RETIRED",
            "EQUIPMENT_UPDATED",
            "INCIDENT_INVESTIGATION_STARTED",
            "INCIDENT_PRIORITY_CHANGED",
            "INCIDENT_REPORTED",
            "INCIDENT_RESOLVED",
            "ORGANIZATION_UPDATED",
            "GYM_BRANCH_CREATED",
            "GYM_BRANCH_UPDATED",
            "GYM_BRANCH_ACTIVATED",
            "GYM_BRANCH_DEACTIVATED",
            "MAINTENANCE_CANCELLED",
            "MAINTENANCE_COMPLETED",
            "MAINTENANCE_SCHEDULED",
            "MAINTENANCE_STARTED",
            "MAINTENANCE_UPDATED",
            "MEMBERSHIP_CANCELLED",
            "MEMBERSHIP_CREATED",
            "MEMBERSHIP_FROZEN",
            "MEMBERSHIP_PLAN_BRANCH_COVERAGE_CHANGED",
            "MEMBERSHIP_PERIOD_COVERAGE_CAPTURED",
            "MEMBERSHIP_REACTIVATED",
            "MEMBERSHIP_RENEWED",
            "PAYMENT_ATTEMPT_CANCELLED",
            "PAYMENT_ATTEMPT_CREATED",
            "PAYMENT_ATTEMPT_FAILED",
            "PAYMENT_ATTEMPT_PROCESSING",
            "PAYMENT_ATTEMPT_PROVIDER_FAILURE",
            "PAYMENT_ATTEMPT_STATUS_CHANGED",
            "PAYMENT_ATTEMPT_PROVIDER_SUCCESS",
            "PAYMENT_PROVIDER_EVENT_DUPLICATE_ACKNOWLEDGED",
            "PAYMENT_PROVIDER_PAYMENT_CONFIRMED",
            "PAYMENT_RECEIPT_GENERATED",
            "PAYMENT_REFUNDED",
            "PAYMENT_REGISTERED",
            "PAYMENT_VOIDED",
            "PLAN_CREATED",
            "PLAN_DEACTIVATED",
            "PLAN_REACTIVATED",
            "PLAN_UPDATED",
            "PROMOTION_CREATED",
            "PROMOTION_DEACTIVATED",
            "PROMOTION_ELIGIBLE_PLANS_CHANGED",
            "PROMOTION_REACTIVATED",
            "PROMOTION_UPDATED",
            "STAFF_PASSWORD_CHANGED",
            "STAFF_PROFILE_PHOTO_REMOVED",
            "STAFF_PROFILE_PHOTO_UPDATED",
            "STAFF_PROFILE_UPDATED");

    private AuditQueryPolicy() {}

    public static Set<String> allowedResourceTypes() {
        return ALLOWED_RESOURCE_TYPES;
    }

    public static Set<String> allowedActionCodes() {
        return ALLOWED_ACTION_CODES;
    }

    public static boolean isAllowedResourceType(String value) {
        return value != null
                && ALLOWED_RESOURCE_TYPES.contains(normalizeCode(value));
    }

    public static boolean isAllowedActionCode(String value) {
        return value != null
                && ALLOWED_ACTION_CODES.contains(normalizeCode(value));
    }

    static String normalizeCode(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(values)));
    }
}
