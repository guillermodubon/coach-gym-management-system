package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

/** Safe internal signal that a message could not be encoded as valid MIME. */
final class GmailMimeEncodingException extends RuntimeException {

    GmailMimeEncodingException() {
        super("The email message could not be encoded for Gmail delivery.");
    }
}
