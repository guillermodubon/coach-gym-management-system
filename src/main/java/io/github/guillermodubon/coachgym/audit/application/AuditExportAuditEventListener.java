package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.audit.AuditExportCompleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists completed export operations without publishing another export event. */
@Component
class AuditExportAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    AuditExportAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(AuditExportCompleted event) {
        auditEntryStore.recordAuditExportCompleted(event);
    }
}
