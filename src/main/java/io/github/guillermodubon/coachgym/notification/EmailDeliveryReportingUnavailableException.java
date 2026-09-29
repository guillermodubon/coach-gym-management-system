package io.github.guillermodubon.coachgym.notification;

/** Safe failure for an unavailable durable-email aggregate read. */
public final class EmailDeliveryReportingUnavailableException extends RuntimeException {

    public EmailDeliveryReportingUnavailableException(Throwable cause) {
        super("Email delivery reporting data could not be read.", cause);
    }
}
