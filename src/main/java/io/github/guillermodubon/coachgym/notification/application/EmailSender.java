package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;

/** Technology-neutral boundary for sending one fully composed email. */
public interface EmailSender {

    /** Sends the message and maps transport details to a safe result. */
    EmailSendResult send(EmailMessage message);
}
