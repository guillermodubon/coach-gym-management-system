package io.github.guillermodubon.coachgym.payment.application;

/** Request-size limits applied before provider verification and processing. */
public record PaymentProviderWebhookLimits(
        int maxPayloadBytes,
        int maxSignatureHeaderLength) {

    private static final int DEFAULT_MAX_PAYLOAD_BYTES = 262_144;
    private static final int DEFAULT_MAX_SIGNATURE_HEADER_LENGTH = 1_024;

    public PaymentProviderWebhookLimits {
        if (maxPayloadBytes < 1 || maxSignatureHeaderLength < 1) {
            throw new IllegalArgumentException("Webhook request limits must be positive.");
        }
    }

    public static PaymentProviderWebhookLimits defaults() {
        return new PaymentProviderWebhookLimits(
                DEFAULT_MAX_PAYLOAD_BYTES,
                DEFAULT_MAX_SIGNATURE_HEADER_LENGTH);
    }
}
