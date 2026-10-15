package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyChanged;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPolicyOverrideChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessPaymentPolicyAuditEntryJpaEntityTest {

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");

    @Test
    void mapsPolicyChangeToAStableSafeSettingsAuditEntry() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new AccessPaymentPolicyChanged(
                        false, true, ACTOR_ID, "policy-admin", NOW));

        assertThat(entry.actionCode()).isEqualTo("ACCESS_PAYMENT_POLICY_CHANGED");
        assertThat(entry.resourceType()).isEqualTo("SETTINGS");
        assertThat(entry.resourceId())
                .isEqualTo(AccessPaymentPolicyChanged.SETTINGS_RESOURCE_ID);
        assertThat(entry.resourceCodeSnapshot()).isEqualTo("GYM_SETTINGS");
        assertThat(entry.actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(entry.actorIdentifierSnapshot()).isEqualTo("policy-admin");
        assertThat(entry.occurredAt()).isEqualTo(NOW);
        assertThat(entry.metadata())
                .containsEntry("previousValue", false)
                .containsEntry("newValue", true);
        assertThat(entry.metadata().keySet()).containsExactlyInAnyOrder(
                "previousValue", "newValue");
        assertThat(entry.metadata().toString())
                .doesNotContain("payment", "stripe", "token", "card", "secret");
    }

    @Test
    void mapsBranchOverrideChangeToAllowlistedPolicyMetadata() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new BranchAccessPolicyOverrideChanged(
                        ORGANIZATION_ID,
                        BRANCH_ID,
                        BranchAccessPaymentPolicyMode.INHERIT,
                        BranchAccessPaymentPolicyMode.REQUIRED,
                        3,
                        ACTOR_ID,
                        "policy-admin",
                        NOW));

        assertThat(entry.actionCode()).isEqualTo("BRANCH_ACCESS_PAYMENT_POLICY_CHANGED");
        assertThat(entry.resourceType()).isEqualTo("GYM_BRANCH");
        assertThat(entry.resourceId()).isEqualTo(BRANCH_ID);
        assertThat(entry.actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(entry.actorIdentifierSnapshot()).isEqualTo("policy-admin");
        assertThat(entry.occurredAt()).isEqualTo(NOW);
        assertThat(entry.metadata())
                .containsEntry("previousMode", "INHERIT")
                .containsEntry("newMode", "REQUIRED")
                .containsEntry("version", 3L);
        assertThat(entry.metadata().keySet())
                .containsExactlyInAnyOrder("previousMode", "newMode", "version");
        assertThat(entry.metadata().toString())
                .doesNotContain("payment details", "card", "token", "secret");
    }
}
