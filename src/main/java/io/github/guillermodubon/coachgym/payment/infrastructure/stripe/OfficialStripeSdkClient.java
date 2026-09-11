package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** The only class allowed to import and use the official Stripe SDK. */
final class OfficialStripeSdkClient implements StripeSdkClient {

    private final StripeProperties properties;
    private final RequestOptions requestOptions;

    OfficialStripeSdkClient(StripeProperties properties) {
        this.properties = properties;
        this.requestOptions = RequestOptions.builder()
                .setApiKey(properties.secretKey())
                .setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
                .setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
                .setMaxNetworkRetries(0)
                .build();
    }

    @Override
    public StripeCheckoutResult createCheckout(StripeCheckoutCreationRequest request) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(request.successUrl().toString())
                    .setCancelUrl(request.cancelUrl().toString())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(request.currency())
                                    .setUnitAmount(request.amountMinor())
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Coach Gym membership")
                                            .build())
                                    .build())
                            .build())
                    .putMetadata("payment_attempt_id", request.attemptId())
                    .build();
            Session session = Session.create(params, requestOptionsFor(request.attemptId()));
            if (session == null || session.getId() == null || session.getId().isBlank()
                    || session.getUrl() == null || session.getUrl().isBlank()
                    || session.getExpiresAt() == null) {
                throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_RESPONSE);
            }
            return new StripeCheckoutResult(session.getId(), java.net.URI.create(session.getUrl()),
                    Instant.ofEpochSecond(session.getExpiresAt()));
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (StripeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
        }
    }

    private RequestOptions requestOptionsFor(String attemptId) {
        return RequestOptions.builder()
                .setApiKey(properties.secretKey())
                .setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()))
                .setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()))
                .setMaxNetworkRetries(0)
                .setIdempotencyKey("payment-attempt-" + attemptId)
                .build();
    }

    @Override
    public void expireCheckout(String checkoutReference) {
        try {
            Session session = Session.retrieve(checkoutReference, requestOptions);
            session.expire(requestOptions);
        } catch (StripeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
        }
    }

    @Override
    public StripeVerifiedEvent verifyWebhook(StripeWebhookRequest request) {
        try {
            Event event = Webhook.constructEvent(
                    new String(request.payload(), java.nio.charset.StandardCharsets.UTF_8),
                    request.signatureHeader(), properties.webhookSigningSecret(),
                    properties.webhookTolerance().toSeconds(),
                    Clock.fixed(request.verificationTime(), ZoneOffset.UTC));
            StripeObject data = event.getDataObjectDeserializer().getObject().orElse(null);
            if (data == null) {
                data = event.getDataObjectDeserializer().deserializeUnsafe();
            }
            String type = event.getType();
            String checkoutReference = null;
            String paymentReference = null;
            java.math.BigDecimal amount = null;
            String currency = null;
            UUID attemptId = null;
            if (data instanceof Session session) {
                checkoutReference = session.getId();
                attemptId = metadataAttemptId(session.getMetadata());
                if ("checkout.session.completed".equals(type)) {
                    paymentReference = session.getPaymentIntent();
                    if (session.getAmountTotal() != null) {
                        amount = java.math.BigDecimal.valueOf(session.getAmountTotal(), 2);
                    }
                    currency = session.getCurrency();
                }
            } else if (data != null) {
                attemptId = metadataAttemptId(readMetadata(data));
            }
            return new StripeVerifiedEvent(event.getId(), type, attemptId, checkoutReference,
                    paymentReference, amount, currency, Instant.ofEpochSecond(event.getCreated()));
        } catch (StripeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_SIGNATURE);
        } catch (RuntimeException exception) {
            throw new PaymentProviderException(PaymentProviderFailureCode.INVALID_SIGNATURE);
        }
    }

    private static UUID metadataAttemptId(java.util.Map<String, String> metadata) {
        if (metadata == null) {
            return null;
        }
        return Optional.ofNullable(metadata.get("payment_attempt_id"))
                .map(value -> {
                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException exception) {
                        return null;
                    }
                }).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, String> readMetadata(Object value) {
        try {
            return (java.util.Map<String, String>) value.getClass()
                    .getMethod("getMetadata").invoke(value);
        } catch (ReflectiveOperationException exception) {
            return java.util.Map.of();
        }
    }
}
