package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationAccepted;
import io.github.guillermodubon.coachgym.user.StaffInvitationCreated;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryAttempted;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationResent;
import io.github.guillermodubon.coachgym.user.StaffInvitationRevoked;
import io.github.guillermodubon.coachgym.user.StaffPasswordReset;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityOperationalMetricsListenerTest {

    private static final UUID ID = UUID.fromString(
            "00000000-0000-0000-0000-000000009811");
    private static final UUID ORG_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000009812");
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void recordsInvitationAndRecoveryOutcomesUsingOnlyFiniteTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdentityOperationalMetricsListener listener =
                new IdentityOperationalMetricsListener(registry);
        listener.invitationCreated(new StaffInvitationCreated(
                ID, ORG_ID, UUID.randomUUID(), "s***@example.test", RoleCode.ADMIN,
                StaffScopeType.ORGANIZATION, Set.of(), NOW));
        listener.invitationDeliveryAttempted(new StaffInvitationDeliveryAttempted(
                ID, 1, StaffInvitationDeliveryStatus.AMBIGUOUS, NOW));
        listener.invitationAccepted(new StaffInvitationAccepted(
                ID, ORG_ID, UUID.randomUUID(), UUID.randomUUID(), RoleCode.ADMIN,
                StaffScopeType.ORGANIZATION, Set.of(), NOW));
        listener.invitationResent(new StaffInvitationResent(ID, ORG_ID, UUID.randomUUID(), 2, NOW));
        listener.invitationRevoked(new StaffInvitationRevoked(ID, ORG_ID, UUID.randomUUID(), 3, NOW));
        listener.passwordReset(new StaffPasswordReset(UUID.randomUUID(), NOW));

        assertThat(registry.get("coachgym.identity.operations")
                .tag("operation", "INVITATION_DELIVERY")
                .tag("outcome", "AMBIGUOUS").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("coachgym.identity.operations")
                .tag("operation", "PASSWORD_RECOVERY_COMPLETED")
                .tag("outcome", "SUCCESS").counter().count()).isEqualTo(1.0);
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags()).extracting(Tag::getKey)
                    .containsExactlyInAnyOrder("operation", "outcome");
            assertThat(meter.getId().getTag("operation")).isIn(
                    "INVITATION_CREATED", "INVITATION_DELIVERY", "INVITATION_ACCEPTED",
                    "INVITATION_RESENT", "INVITATION_REVOKED", "PASSWORD_RECOVERY_COMPLETED");
            assertThat(meter.getId().getTags()).allSatisfy(tag ->
                    assertThat(tag.getValue())
                            .doesNotContain(ID.toString(), ORG_ID.toString(), "example.test"));
        });
    }
}
