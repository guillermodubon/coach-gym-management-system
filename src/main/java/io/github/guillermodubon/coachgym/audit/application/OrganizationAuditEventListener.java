package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.organization.GymBranchCreated;
import io.github.guillermodubon.coachgym.organization.GymBranchStatusChanged;
import io.github.guillermodubon.coachgym.organization.GymBranchUpdated;
import io.github.guillermodubon.coachgym.organization.OrganizationUpdated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Forwards privacy-safe organization lifecycle events to the audit store. */
@Component
class OrganizationAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    OrganizationAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(OrganizationUpdated event) {
        auditEntryStore.recordOrganizationUpdated(event);
    }

    @EventListener
    void record(GymBranchCreated event) {
        auditEntryStore.recordGymBranchCreated(event);
    }

    @EventListener
    void record(GymBranchUpdated event) {
        auditEntryStore.recordGymBranchUpdated(event);
    }

    @EventListener
    void record(GymBranchStatusChanged event) {
        auditEntryStore.recordGymBranchStatusChanged(event);
    }
}
