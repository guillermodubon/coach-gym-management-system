package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.user.StaffInvitationAccepted;
import io.github.guillermodubon.coachgym.user.StaffInvitationCreated;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryAttempted;
import io.github.guillermodubon.coachgym.user.StaffInvitationResent;
import io.github.guillermodubon.coachgym.user.StaffInvitationRevoked;
import io.github.guillermodubon.coachgym.user.StaffPasswordReset;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Counts identity lifecycle outcomes using event enums only, never identity or token values. */
@Component
public class IdentityOperationalMetricsListener {

    private static final String METRIC = "coachgym.identity.operations";

    private final MeterRegistry meterRegistry;

    public IdentityOperationalMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
    }

    @EventListener
    public void invitationCreated(StaffInvitationCreated event) {
        record("INVITATION_CREATED", "SUCCESS");
    }

    @EventListener
    public void invitationDeliveryAttempted(StaffInvitationDeliveryAttempted event) {
        record("INVITATION_DELIVERY", event.status() == null ? "UNKNOWN" : event.status().name());
    }

    @EventListener
    public void invitationAccepted(StaffInvitationAccepted event) {
        record("INVITATION_ACCEPTED", "SUCCESS");
    }

    @EventListener
    public void invitationResent(StaffInvitationResent event) {
        record("INVITATION_RESENT", "SUCCESS");
    }

    @EventListener
    public void invitationRevoked(StaffInvitationRevoked event) {
        record("INVITATION_REVOKED", "SUCCESS");
    }

    @EventListener
    public void passwordReset(StaffPasswordReset event) {
        record("PASSWORD_RECOVERY_COMPLETED", "SUCCESS");
    }

    private void record(String operation, String outcome) {
        meterRegistry.counter(METRIC, "operation", operation, "outcome", outcome)
                .increment();
    }
}
