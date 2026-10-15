package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;

/** Technology-neutral boundary for sending one fully composed email.
 *
 * <p>{@code SENT} means the configured provider confirmed acceptance for
 * sending; it does not guarantee inbox placement. A transport that cannot
 * determine whether the provider accepted the message must return an
 * ambiguous result rather than a definitive failure.</p>
 */
public interface EmailSender {

    /** Sends the message and maps transport details to a safe result. */
    EmailSendResult send(EmailMessage message);
}
