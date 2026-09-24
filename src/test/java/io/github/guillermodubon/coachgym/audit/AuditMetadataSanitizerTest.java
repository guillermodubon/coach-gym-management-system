package io.github.guillermodubon.coachgym.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.audit.application.AuditEntryProjector;
import io.github.guillermodubon.coachgym.audit.application.AuditEntryRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditMetadataSanitizerTest {

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void coversEveryCurrentAuditActionFamilyWithExplicitPolicy() {
        Map<String, String> expectedKeys = Map.ofEntries(
                Map.entry("CLIENT_REGISTERED", "branchId"),
                Map.entry("PLAN_CREATED", "missing"),
                Map.entry("PROMOTION_ELIGIBLE_PLANS_CHANGED", "eligiblePlanIds"),
                Map.entry("MEMBERSHIP_CREATED", "listPrice"),
                Map.entry("MEMBERSHIP_PLAN_BRANCH_COVERAGE_CHANGED", "coverageScope"),
                Map.entry("PAYMENT_REGISTERED", "paymentMethod"),
                Map.entry("PAYMENT_ATTEMPT_FAILED", "failureCode"),
                Map.entry("PAYMENT_RECEIPT_GENERATED", "paymentCode"),
                Map.entry("ACCESS_CREDENTIAL_ISSUED", "tokenSchemeVersion"),
                Map.entry("ACCESS_DENIED", "reasonCode"),
                Map.entry("ACCESS_PAYMENT_POLICY_CHANGED", "previousValue"),
                Map.entry("BRANCH_ACCESS_PAYMENT_POLICY_CHANGED", "newMode"),
                Map.entry("EMAIL_DELIVERY_SENT", "deliveryType"),
                Map.entry("EQUIPMENT_REGISTERED", "categoryId"),
                Map.entry("INCIDENT_REPORTED", "equipmentId"),
                Map.entry("MAINTENANCE_SCHEDULED", "maintenanceType"),
                Map.entry("STAFF_PROFILE_UPDATED", "changedFields"),
                Map.entry("STAFF_PROFILE_PHOTO_UPDATED", "photoPresent"),
                Map.entry("STAFF_PROFILE_PHOTO_REMOVED", "photoPresent"),
                Map.entry("STAFF_PASSWORD_CHANGED", "reauthenticationRequired"),
                Map.entry("STAFF_SCOPE_CHANGED", "previousScope"),
                Map.entry("STAFF_BRANCH_ASSIGNED", "branchId"),
                Map.entry("STAFF_BRANCH_ASSIGNMENT_ENDED", "newStatus"),
                Map.entry("ORGANIZATION_UPDATED", "changedFields"),
                Map.entry("GYM_BRANCH_UPDATED", "changedFields"),
                Map.entry("GYM_BRANCH_DEACTIVATED", "previousStatus"));

        for (Map.Entry<String, String> entry : expectedKeys.entrySet()) {
            Set<String> allowlist = AuditMetadataPolicy.allowedKeysForAction(entry.getKey());
            if ("missing".equals(entry.getValue())) {
                assertThat(allowlist).isEmpty();
            } else {
                assertThat(allowlist).contains(entry.getValue());
            }
        }

        assertThat(AuditMetadataPolicy.allowedKeysForAction("STAFF_ACTIVE_BRANCH_CHANGED"))
                .isEmpty();
        assertThat(AuditMetadataPolicy.allowedKeysForAction("STAFF_ACTIVE_BRANCH_CLEARED"))
                .isEmpty();
    }

    @Test
    void allowlistsCurrentActionFamiliesAndDeniesUnknownKeys() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("paymentId", UUID.randomUUID());
        source.put("amount", "10.00");
        source.put("currency", "USD");
        source.put("unknownOperationalValue", "must disappear");
        source.put("providerSecret", "must disappear");

        AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                "PAYMENT_REGISTERED", source);

        assertThat(projection.values()).containsKeys("amount", "currency", "paymentId");
        assertThat(projection.values())
                .doesNotContainKeys("unknownOperationalValue", "providerSecret");
        assertThat(projection.metadataRedacted()).isTrue();
    }

    @Test
    void sanitizesBranchPolicyMetadataWithoutExposingUnapprovedValues() {
        AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                "BRANCH_ACCESS_PAYMENT_POLICY_CHANGED",
                Map.of(
                        "previousMode", "INHERIT",
                        "newMode", "REQUIRED",
                        "version", 4L,
                        "paymentReference", "must disappear"));

        assertThat(projection.values())
                .containsEntry("previousMode", "INHERIT")
                .containsEntry("newMode", "REQUIRED")
                .containsEntry("version", 4L)
                .doesNotContainKey("paymentReference");
        assertThat(projection.metadataRedacted()).isTrue();
    }

    @Test
    void sanitizesPlanCoverageMetadataWithoutExposingBranchIds() {
        AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                "MEMBERSHIP_PLAN_BRANCH_COVERAGE_CHANGED",
                Map.of(
                        "coverageScope", "SELECTED_BRANCHES",
                        "coveredBranchCount", 3,
                        "sourcePlanVersion", 5L,
                        "branchIds", List.of(UUID.randomUUID().toString()),
                        "internalPolicy", "must disappear"));

        assertThat(projection.values())
                .containsEntry("coverageScope", "SELECTED_BRANCHES")
                .containsEntry("coveredBranchCount", 3)
                .containsEntry("sourcePlanVersion", 5L)
                .doesNotContainKeys("branchIds", "internalPolicy");
        assertThat(projection.metadataRedacted()).isTrue();
    }

    @Test
    void preservesOnlyTheSafeBranchReferenceAcrossBranchOwnedActionFamilies() {
        String branchId = UUID.randomUUID().toString();
        List<String> actions = List.of(
                "CLIENT_REGISTERED", "MEMBERSHIP_CREATED", "PAYMENT_REGISTERED",
                "PAYMENT_ATTEMPT_CREATED", "PAYMENT_RECEIPT_GENERATED",
                "ACCESS_DENIED", "ACCESS_CREDENTIAL_ISSUED", "EMAIL_DELIVERY_SENT",
                "EQUIPMENT_REGISTERED", "INCIDENT_REPORTED", "MAINTENANCE_SCHEDULED");

        for (String action : actions) {
            AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                    action, Map.of("branchId", branchId, "branchAddress", "private"));
            assertThat(projection.values())
                    .as("safe branch snapshot for %s", action)
                    .containsEntry("branchId", branchId)
                    .doesNotContainKey("branchAddress");
        }
    }

    @Test
    void removesSuspiciousNestedKeysAndNeverMutatesSource() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("status", "FAILED");
        nested.put("password", "never-return");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("provider", nested);

        Map<String, Object> before = new LinkedHashMap<>(source);
        AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                "PAYMENT_ATTEMPT_FAILED", source);

        assertThat(source).isEqualTo(before);
        assertThat(projection.values()).containsKey("provider");
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedNested =
                (Map<String, Object>) projection.values().get("provider");
        assertThat(sanitizedNested).containsEntry("status", "FAILED");
        assertThat(sanitizedNested).doesNotContainKey("password");
        assertThat(projection.metadataRedacted()).isTrue();
        assertThatThrownBy(() -> sanitizedNested.put("status", "CHANGED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void boundsStringsCollectionsAndDepth() {
        String longValue = "safe-" + "x ".repeat(150);
        List<String> tooManyIds = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            tooManyIds.add("id-" + index);
        }
        Map<String, Object> deeplyNested = new LinkedHashMap<>();
        Map<String, Object> levelTwo = new LinkedHashMap<>();
        Map<String, Object> levelThree = new LinkedHashMap<>();
        levelThree.put("provider", "value");
        levelTwo.put("provider", levelThree);
        deeplyNested.put("provider", levelTwo);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("provider", longValue);
        source.put("attemptResult", tooManyIds);
        source.put("clientId", deeplyNested);

        AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                "PAYMENT_ATTEMPT_FAILED", source);

        assertThat(projection.metadataRedacted()).isTrue();
        assertThat((String) projection.values().get("provider")).hasSize(256);
        assertThat((List<?>) projection.values().get("attemptResult"))
                .hasSize(AuditMetadataSanitizer.MAX_ENTRIES);
        assertThat(projection.values()).containsKey("clientId");
    }

    @Test
    void rejectsBinaryLikeValuesAndMasksEmailRecipients() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("maskedRecipient", "ana.martinez@example.com");
        source.put("failureCode", "DELIVERY_FAILED");
        source.put("attemptNumber", 2);
        source.put("provider", "A".repeat(128));

        AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                "EMAIL_DELIVERY_FAILED", source);

        assertThat(projection.values().get("maskedRecipient"))
                .isEqualTo("a***@example.com");
        assertThat(projection.values()).doesNotContainKey("provider");
        assertThat(projection.metadataRedacted()).isTrue();
    }

    @Test
    void unknownActionAndSanitizerFailuresReturnOnlyRedactedEmptyOutput() {
        Map<String, Object> source = Map.of("status", "UNKNOWN", "payload", "secret");

        AuditMetadataProjection unknown = new AuditMetadataSanitizer().sanitize(
                "NEW_UNREVIEWED_ACTION", source);
        assertThat(unknown.values()).isEmpty();
        assertThat(unknown.metadataRedacted()).isTrue();

        AuditMetadataProjection nullSource = new AuditMetadataSanitizer().sanitize(
                "PAYMENT_REGISTERED", null);
        assertThat(nullSource).isEqualTo(AuditMetadataProjection.empty());
    }

    @Test
    void sanitizesStaffProfileMetadataByPositiveAllowlist() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("changedFields", List.of("firstName", "lastName"));
        source.put("passwordHash", "never-return");
        source.put("storageKey", "never-return");

        AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                "STAFF_PROFILE_UPDATED", source);

        assertThat(projection.values()).containsEntry(
                "changedFields", List.of("firstName", "lastName"));
        assertThat(projection.values()).doesNotContainKeys(
                "passwordHash", "storageKey");
        assertThat(projection.values().toString()).doesNotContain("never-return");
        assertThat(projection.metadataRedacted()).isTrue();
    }

    @Test
    void projectsStaffAssignmentMetadataAndOmitsSessionOrReasonDetails() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("assignmentId", UUID.randomUUID().toString());
        source.put("targetUserId", UUID.randomUUID().toString());
        source.put("branchId", UUID.randomUUID().toString());
        source.put("newStatus", "ACTIVE");
        source.put("reasonPresent", true);
        source.put("sessionId", "never-return");
        source.put("reasonText", "never-return");

        AuditMetadataProjection projection = new AuditMetadataSanitizer().sanitize(
                "STAFF_BRANCH_ASSIGNED", source);

        assertThat(projection.values())
                .containsKeys("assignmentId", "targetUserId", "branchId", "newStatus", "reasonPresent")
                .doesNotContainKeys("sessionId", "reasonText");
        assertThat(projection.values().toString()).doesNotContain("never-return");
        assertThat(projection.metadataRedacted()).isTrue();
    }

    @Test
    void summaryProjectionContainsLessDataThanDetailProjection() {
        UUID id = UUID.randomUUID();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("amount", "25.00");
        source.put("currency", "USD");
        source.put("password", "never-return");
        AuditEntryRow row = new AuditEntryRow(
                id,
                UUID.randomUUID(),
                "admin",
                "PAYMENT_REGISTERED",
                "PAYMENT",
                UUID.randomUUID(),
                "PAY-001",
                "Payment registered.",
                source,
                null,
                OCCURRED_AT);

        AuditEntryProjector projector = new AuditEntryProjector();
        AuditEntrySummary summary = projector.toSummary(row);
        AuditEntryDetails details = projector.toDetails(row);

        assertThat(summary).isNotNull();
        assertThat(details.metadata().values()).containsKeys("amount", "currency");
        assertThat(details.metadata().values()).doesNotContainKey("password");
        assertThat(details.metadata().metadataRedacted()).isTrue();
    }
}
