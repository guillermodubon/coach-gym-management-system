package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one privacy-safe audit entry for each finalized email attempt.
 *
 * <p>The notification module owns the delivery and attempt history. This
 * listener only records the bounded lifecycle projection in the audit module;
 * it never reads or stores message content, attachment bytes, or transport
 * details.</p>
 */
@Component
class EmailDeliveryAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    EmailDeliveryAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(EmailDeliveryLifecycleEvent event) {
        auditEntryStore.recordEmailDeliveryLifecycle(event);
    }
}
