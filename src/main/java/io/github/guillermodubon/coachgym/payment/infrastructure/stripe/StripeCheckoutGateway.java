package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.CardCheckoutGateway;
import io.github.guillermodubon.coachgym.payment.application.ProviderCheckout;
import io.github.guillermodubon.coachgym.payment.application.ProviderCheckoutCancellationRequest;
import io.github.guillermodubon.coachgym.payment.application.ProviderCheckoutRequest;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import java.math.RoundingMode;

class StripeCheckoutGateway implements CardCheckoutGateway {

    private final StripeSdkClient client;
    private final StripeProperties properties;

    StripeCheckoutGateway(StripeSdkClient client, StripeProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public ProviderCheckout createCheckout(ProviderCheckoutRequest request) {
        if (request == null || request.provider() != PaymentProvider.STRIPE
                || !request.successUrl().toString().equals(properties.successUrl())
                || !request.cancelUrl().toString().equals(properties.cancelUrl())) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
        }
        long amountMinor;
        try {
            amountMinor = request.amount().setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
        }
        try {
            StripeCheckoutResult session = client.createCheckout(new StripeCheckoutCreationRequest(
                    request.paymentAttemptId().toString(), amountMinor,
                    request.currency().toLowerCase(java.util.Locale.ROOT),
                    request.successUrl(), request.cancelUrl()));
            if (session == null || session.checkoutReference() == null
                    || session.checkoutReference().isBlank() || session.checkoutUrl() == null
                    || session.expiresAt() == null) {
                throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
            }
            return new ProviderCheckout(PaymentProvider.STRIPE, session.checkoutReference(),
                    session.checkoutUrl(), session.expiresAt());
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
        }
    }

    @Override
    public void cancelCheckout(ProviderCheckoutCancellationRequest request) {
        if (request == null || request.provider() != PaymentProvider.STRIPE) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
        }
        try {
            client.expireCheckout(request.checkoutReference());
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
        }
    }
}
