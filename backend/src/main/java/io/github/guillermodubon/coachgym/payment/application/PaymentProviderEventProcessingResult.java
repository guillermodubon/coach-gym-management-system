package io.github.guillermodubon.coachgym.payment.application;

/** Durable outcome of applying a verified provider event. */
public enum PaymentProviderEventProcessingResult {
    PENDING,
    PROCESSED,
    REJECTED
}
