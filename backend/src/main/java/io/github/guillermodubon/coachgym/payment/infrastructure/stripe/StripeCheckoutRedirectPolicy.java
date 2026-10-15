package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.CheckoutRedirectPolicy;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import java.net.URI;

/** Supplies server-owned redirect targets to the application checkout orchestration. */
final class StripeCheckoutRedirectPolicy implements CheckoutRedirectPolicy {

    private final StripeProperties properties;

    StripeCheckoutRedirectPolicy(StripeProperties properties) {
        this.properties = properties;
    }

    @Override
    public URI successUrl(PaymentProvider provider) {
        requireStripe(provider);
        return URI.create(properties.successUrl());
    }

    @Override
    public URI cancelUrl(PaymentProvider provider) {
        requireStripe(provider);
        return URI.create(properties.cancelUrl());
    }

    private static void requireStripe(PaymentProvider provider) {
        if (provider != PaymentProvider.STRIPE) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
        }
    }
}
