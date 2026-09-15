package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Persists the privacy-safe audit entry for administrative policy changes. */
@Component
class AccessPaymentPolicyAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    AccessPaymentPolicyAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(AccessPaymentPolicyChanged event) {
        auditEntryStore.recordAccessPaymentPolicyChanged(event);
    }
}
