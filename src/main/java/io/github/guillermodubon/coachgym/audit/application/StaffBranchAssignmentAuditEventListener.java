package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.user.StaffBranchAssigned;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentEnded;
import io.github.guillermodubon.coachgym.user.StaffScopeChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Forwards staff scope and assignment lifecycle events to the canonical audit
 * store. Active branch selection is intentionally not audited: it is a
 * high-volume, server-session preference rather than a durable authorization
 * change, and every resolution revalidates the selected branch.
 */
@Component
class StaffBranchAssignmentAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    StaffBranchAssignmentAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(StaffBranchAssigned event) {
        auditEntryStore.recordStaffBranchAssigned(event);
    }

    @EventListener
    void record(StaffBranchAssignmentEnded event) {
        auditEntryStore.recordStaffBranchAssignmentEnded(event);
    }

    @EventListener
    void record(StaffScopeChanged event) {
        auditEntryStore.recordStaffScopeChanged(event);
    }
}
