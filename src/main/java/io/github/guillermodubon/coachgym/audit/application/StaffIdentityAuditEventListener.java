package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.user.StaffIdentityLifecycleChanged;
import io.github.guillermodubon.coachgym.user.StaffInitialAdministratorProvisioned;
import io.github.guillermodubon.coachgym.user.StaffInvitationAccepted;
import io.github.guillermodubon.coachgym.user.StaffInvitationCreated;
import io.github.guillermodubon.coachgym.user.StaffInvitationResent;
import io.github.guillermodubon.coachgym.user.StaffInvitationRevoked;
import io.github.guillermodubon.coachgym.user.StaffPasswordReset;
import io.github.guillermodubon.coachgym.user.StaffRoleScopeChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Persists minimized staff identity lifecycle facts through the existing audit port. */
@Component
class StaffIdentityAuditEventListener {

    private final AuditEntryStore auditEntries;

    StaffIdentityAuditEventListener(AuditEntryStore auditEntries) {
        this.auditEntries = auditEntries;
    }

    @EventListener
    void record(StaffInvitationCreated event) {
        auditEntries.recordStaffInvitationCreated(event);
    }

    @EventListener
    void record(StaffInvitationResent event) {
        auditEntries.recordStaffInvitationResent(event);
    }

    @EventListener
    void record(StaffInvitationRevoked event) {
        auditEntries.recordStaffInvitationRevoked(event);
    }

    @EventListener
    void record(StaffInvitationAccepted event) {
        auditEntries.recordStaffInvitationAccepted(event);
    }

    @EventListener
    void record(StaffIdentityLifecycleChanged event) {
        auditEntries.recordStaffIdentityLifecycleChanged(event);
    }

    @EventListener
    void record(StaffRoleScopeChanged event) {
        auditEntries.recordStaffRoleScopeChanged(event);
    }

    @EventListener
    void record(StaffPasswordReset event) {
        auditEntries.recordStaffPasswordReset(event);
    }

    @EventListener
    void record(StaffInitialAdministratorProvisioned event) {
        auditEntries.recordInitialAdministratorProvisioned(event);
    }
}
