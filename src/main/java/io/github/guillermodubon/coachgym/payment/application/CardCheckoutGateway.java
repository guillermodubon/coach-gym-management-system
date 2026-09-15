package io.github.guillermodubon.coachgym.payment.application;

/** Provider-neutral boundary for hosted card checkout operations. */
public interface CardCheckoutGateway {

    ProviderCheckout createCheckout(ProviderCheckoutRequest request);

    void cancelCheckout(ProviderCheckoutCancellationRequest request);
}
