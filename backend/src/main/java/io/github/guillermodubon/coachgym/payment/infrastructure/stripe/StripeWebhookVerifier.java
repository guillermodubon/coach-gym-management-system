package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventType;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookVerifier;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import io.github.guillermodubon.coachgym.payment.application.VerifiedPaymentProviderEvent;
import java.time.Clock;

final class StripeWebhookVerifier implements PaymentProviderWebhookVerifier {

    private final StripeSdkClient client;
    private final StripeProperties properties;
    private final Clock clock;

    StripeWebhookVerifier(StripeSdkClient client, StripeProperties properties) {
        this(client, properties, Clock.systemUTC());
    }

    StripeWebhookVerifier(StripeSdkClient client, StripeProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public VerifiedPaymentProviderEvent verify(
            PaymentProvider provider, byte[] rawPayload, String signatureHeader) {
        if (provider != PaymentProvider.STRIPE || rawPayload == null
                || rawPayload.length > properties.maxWebhookPayloadBytes()
                || signatureHeader == null || signatureHeader.isBlank()
                || signatureHeader.length() > properties.maxSignatureHeaderLength()) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_SIGNATURE);
        }
        try {
            StripeVerifiedEvent event = client.verifyWebhook(
                    new StripeWebhookRequest(rawPayload, signatureHeader, clock.instant()));
            if (event == null) {
                throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
            }
            PaymentProviderEventType eventType = mapType(event.eventType());
            if (event.paymentAttemptId() == null) {
                throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
            }
            return new VerifiedPaymentProviderEvent(
                    PaymentProvider.STRIPE, event.eventReference(), eventType,
                    event.paymentAttemptId(), event.checkoutReference(),
                    event.providerPaymentReference(), event.amount(), event.currency(), event.occurredAt());
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_SIGNATURE);
        }
    }

    private static PaymentProviderEventType mapType(String type) {
        if (type == null || type.isBlank()) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
        }
        return switch (type) {
            case "checkout.session.completed" -> PaymentProviderEventType.CHECKOUT_COMPLETED;
            case "payment_intent.payment_failed" -> PaymentProviderEventType.PAYMENT_FAILED;
            case "checkout.session.expired" -> PaymentProviderEventType.CHECKOUT_EXPIRED;
            default -> throw new PaymentProviderException(PaymentProviderFailureCode.UNSUPPORTED_EVENT);
        };
    }
}
