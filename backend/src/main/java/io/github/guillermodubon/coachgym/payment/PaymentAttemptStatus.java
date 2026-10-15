package io.github.guillermodubon.coachgym.payment;

/** Lifecycle state of a provider payment interaction before it becomes a payment. */
public enum PaymentAttemptStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return switch (this) {
            case SUCCEEDED, FAILED, CANCELLED, EXPIRED -> true;
            case CREATED, PROCESSING -> false;
        };
    }
}
