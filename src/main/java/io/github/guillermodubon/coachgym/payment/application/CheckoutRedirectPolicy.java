package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.net.URI;

/** Server-owned redirect targets for hosted provider checkout flows. */
public interface CheckoutRedirectPolicy {

    URI successUrl(PaymentProvider provider);

    URI cancelUrl(PaymentProvider provider);
}
