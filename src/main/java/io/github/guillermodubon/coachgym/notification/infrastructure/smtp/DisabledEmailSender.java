package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;

/** Safe no-op sender used when transactional email is not explicitly enabled. */
final class DisabledEmailSender implements EmailSender {

    @Override
    public EmailSendResult send(EmailMessage message) {
        return EmailSendResult.failed(
                EmailDeliveryFailureCode.DELIVERY_DISABLED,
                "Transactional email delivery is disabled.");
    }
}
