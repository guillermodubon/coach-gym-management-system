package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.user.StaffPasswordChanged;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoChanged;
import io.github.guillermodubon.coachgym.user.StaffProfileUpdated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records the privacy-safe self-profile events through the existing audit port. */
@Component
class StaffSelfProfileAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    StaffSelfProfileAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(StaffProfileUpdated event) {
        auditEntryStore.recordStaffProfileUpdated(event);
    }

    @EventListener
    void record(StaffProfilePhotoChanged event) {
        auditEntryStore.recordStaffProfilePhotoChanged(event);
    }

    @EventListener
    void record(StaffPasswordChanged event) {
        auditEntryStore.recordStaffPasswordChanged(event);
    }
}
