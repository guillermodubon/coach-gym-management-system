package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
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

    @EventListener
    void record(PaymentAttemptCreated event) {
        auditEntryStore.recordPaymentAttemptCreated(event);
    }

    @EventListener
    void record(PaymentAttemptStatusChanged event) {
        auditEntryStore.recordPaymentAttemptStatusChanged(event);
    }

    @EventListener
    void record(PaymentAttemptProviderStatusChanged event) {
        auditEntryStore.recordPaymentAttemptProviderStatusChanged(event);
    }

    @EventListener
    void record(PaymentProviderEventAcknowledged event) {
        auditEntryStore.recordPaymentProviderEventAcknowledged(event);
    }

    @EventListener
    void record(PaymentProviderPaymentConfirmed event) {
        auditEntryStore.recordPaymentProviderPaymentConfirmed(event);
    }
}
