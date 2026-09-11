package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;

/** Verifies a provider request before it can enter payment application logic. */
public interface PaymentProviderWebhookVerifier {

    VerifiedPaymentProviderEvent verify(
            PaymentProvider provider,
            byte[] rawPayload,
            String signatureHeader);
}
