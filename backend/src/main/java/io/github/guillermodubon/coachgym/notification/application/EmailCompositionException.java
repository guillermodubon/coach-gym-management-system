package io.github.guillermodubon.coachgym.notification.application;

/** Safe failure raised when a packaged email template cannot be composed. */
public class EmailCompositionException extends RuntimeException {

    public EmailCompositionException(String message) {
        super(message);
    }

    public EmailCompositionException(String message, Throwable cause) {
        super(message, cause);
    }
}
