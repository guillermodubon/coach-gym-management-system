package io.github.guillermodubon.coachgym.payment.application;

/** Durable idempotency boundary for verified provider-event identities. */
public interface PaymentProviderEventStore {

    PaymentProviderEventReservation reserve(PersistPaymentProviderEventCommand command);

    PaymentProviderEventDetails findForProcessing(
            io.github.guillermodubon.coachgym.payment.PaymentProvider provider,
            String providerEventReference);

    void finalizeProcessing(FinalizePaymentProviderEventCommand command);
}
