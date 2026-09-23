package io.github.guillermodubon.coachgym.audit;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Privacy policy for metadata that may eventually be projected to ADMINs.
 *
 * <p>Unknown keys are denied by default. The denylist is a second, global
 * defense against accidental exposure of credentials, provider payloads,
 * payment-card data, email content, local paths, SQL, and diagnostics. The
 * sanitizer in the next block will combine this policy with action-family
 * positive allowlists; this class intentionally does not inspect or rewrite
 * metadata values.</p>
 */
public final class AuditMetadataPolicy {

    private static final Set<String> SENSITIVE_KEY_FRAGMENTS =
            immutableSet(
                    "password",
                    "secret",
                    "token",
                    "signature",
                    "authorization",
                    "cookie",
                    "credential",
                    "digest",
                    "hash",
                    "payload",
                    "body",
                    "attachment",
                    "content",
                    "bytes",
                    "path",
                    "sql",
                    "stack",
                    "exception",
                    "card",
                    "cvc",
                    "pin",
                    "expiry",
                    "smtp",
                    "webhook");

    private static final Set<String> SAFE_REFERENCE_EXCEPTIONS =
            immutableSet("accesscredentialid", "tokenschemeversion");

    private static final Map<String, Set<String>> ALLOWED_KEYS_BY_ACTION_FAMILY =
            allowedKeysByActionFamily();

    private AuditMetadataPolicy() {}

    public static Set<String> sensitiveKeyFragments() {
        return SENSITIVE_KEY_FRAGMENTS;
    }

    /**
     * Returns the positive allowlist selected for an action family. Unknown
     * families return an empty set (default deny); values are never inspected
     * or rewritten by this policy class.
     */
    public static Set<String> allowedKeysForAction(String actionCode) {
        if (actionCode == null || actionCode.isBlank()) {
            return Set.of();
        }
        String normalized = actionCode.strip().toUpperCase(Locale.ROOT);
        return ALLOWED_KEYS_BY_ACTION_FAMILY.entrySet().stream()
                .filter(entry -> normalized.startsWith(entry.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(Set.of());
    }

    public static Map<String, Set<String>> allowedKeysByActionFamily() {
        Map<String, Set<String>> policy = new LinkedHashMap<>();
        policy.put("ACCESS_CREDENTIAL_", immutableSet(
                "accessCredentialId", "clientId", "replacementCredentialId",
                "tokenSchemeVersion", "previousStatus", "newStatus", "branchId",
                "reasonPresent"));
        policy.put("PAYMENT_ATTEMPT_", immutableSet(
                "amount", "attemptNumber", "attemptResult", "clientId",
                "confirmedPaymentPresent", "currency", "expectedAmount",
                "failureCode", "membershipId", "membershipPeriodId", "newStatus",
                "previousStatus", "processingResult", "provider",
                "providerEventReferencePresent", "status", "testMode",
                "paymentAttemptId", "branchId"));
        policy.put("EMAIL_DELIVERY_", immutableSet(
                "attemptNumber", "attemptResult", "clientId", "deliveryType",
                "failureCode", "maskedRecipient", "previousStatus", "sourceResourceId",
                "status", "branchId"));
        policy.put("MEMBERSHIP_", immutableSet(
                "cancelledOn", "clientId", "closedOpenFreeze", "currency",
                "discountAmount", "effectiveEndsOn", "finalPrice", "freezeStartsOn",
                "listPrice", "membershipFreezeId", "membershipPeriodId",
                "membershipPlanId", "periodNumber", "plannedEndsOn", "promotionId",
                "previousStatus", "reactivatedOn", "resultingStatus", "startsOn",
                "statusChanged", "branchId"));
        policy.put("PAYMENT_", immutableSet(
                "amount", "clientId", "currency", "hasExternalReference",
                "membershipId", "membershipPeriodId", "newStatus", "paidAt",
                "paymentMethod", "previousStatus", "provider", "providerEventReferencePresent",
                "resultingStatus", "status", "testMode", "paymentId",
                "paymentAttemptId", "paymentCode", "branchId"));
        policy.put("ACCESS_PAYMENT_", immutableSet("newValue", "previousValue"));
        policy.put("ACCESS_", immutableSet(
                "accessCredentialId", "checkedInAt", "duplicate", "identificationSource",
                "presentedIdentifierType", "reasonCode", "reasonPresent", "result",
                "clientId", "membershipId", "branchId"));
        policy.put("EQUIPMENT_", immutableSet(
                "categoryId", "equipmentCode", "equipmentId", "equipmentOutcome", "newStatus",
                "previousStatus", "priority", "statusChanged", "takenOutOfService",
                "branchId"));
        policy.put("INCIDENT_", immutableSet(
                "equipmentCode", "equipmentId", "newPriority", "newStatus", "previousPriority",
                "previousStatus", "priority", "statusChanged", "branchId"));
        policy.put("MAINTENANCE_", immutableSet(
                "actualCost", "currency", "equipmentCode", "equipmentId",
                "equipmentOutcome", "estimatedCost", "incidentId", "maintenanceType",
                "newStatus", "previousStatus", "scheduledOn", "statusChanged",
                "branchId"));
        policy.put("PROMOTION_", immutableSet(
                "eligiblePlanCount", "eligiblePlanIds", "promotionId"));
        policy.put("STAFF_PROFILE_", immutableSet(
                "changedFields", "photoPresent", "reauthenticationRequired"));
        policy.put("STAFF_PASSWORD_", immutableSet(
                "reauthenticationRequired"));
        policy.put("STAFF_SCOPE_", immutableSet(
                "targetUserId", "previousScope", "newScope", "reasonPresent"));
        policy.put("STAFF_BRANCH_", immutableSet(
                "assignmentId", "targetUserId", "branchId", "previousStatus",
                "newStatus", "reasonPresent"));
        policy.put("ORGANIZATION_", immutableSet(
                "changedFields"));
        policy.put("GYM_BRANCH_", immutableSet(
                "organizationId", "changedFields", "previousStatus", "newStatus"));
        policy.put("PLAN_", Set.of());
        policy.put("CLIENT_", immutableSet("branchId"));
        return Collections.unmodifiableMap(policy);
    }

    /**
     * Returns whether a key is denied by the global privacy defense.
     *
     * <p>Known safe references are exceptions because they identify a
     * persisted object or encoding version without carrying a secret.</p>
     */
    public static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalized = key.strip().toLowerCase(Locale.ROOT);
        if (SAFE_REFERENCE_EXCEPTIONS.contains(normalized)) {
            return false;
        }
        return SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(values)));
    }
}
