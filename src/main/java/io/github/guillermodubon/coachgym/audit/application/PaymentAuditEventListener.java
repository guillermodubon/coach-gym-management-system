package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class PaymentAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    PaymentAuditEventListener(
            AuditEntryStore auditEntryStore) {

        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(PaymentRegistered event) {
        auditEntryStore.recordPaymentRegistered(event);
    }

    @EventListener
    void record(PaymentVoided event) {
        auditEntryStore.recordPaymentVoided(event);
    }

    @EventListener
    void record(PaymentRefunded event) {
        auditEntryStore.recordPaymentRefunded(event);
    }
}
