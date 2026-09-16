package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;

/** Safe result returned by the provider-neutral sender port. */
public record EmailSendResult(
        EmailAttemptResult result,
        EmailDeliveryFailureCode failureCode,
        String failureMessage,
        String providerMessageId) {

    public static EmailSendResult sent() {
        return new EmailSendResult(EmailAttemptResult.SENT, null, null, null);
    }

    public static EmailSendResult sent(String providerMessageId) {
        return new EmailSendResult(EmailAttemptResult.SENT, null, null, providerMessageId);
    }

    public static EmailSendResult failed(
            EmailDeliveryFailureCode failureCode, String failureMessage) {
        return new EmailSendResult(
                EmailAttemptResult.FAILED, failureCode, failureMessage, null);
    }

    public static EmailSendResult ambiguous(
            String failureMessage) {
        return new EmailSendResult(
                EmailAttemptResult.AMBIGUOUS,
                EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME,
                failureMessage,
                null);
    }

    public EmailSendResult {
        if (result == null) {
            throw new IllegalArgumentException("Email send result is required.");
        }
        if (result == EmailAttemptResult.SENT) {
            if (failureCode != null || failureMessage != null) {
                throw new IllegalArgumentException("A successful email send cannot contain failure metadata.");
            }
        } else {
            if (failureCode == null || failureMessage == null) {
                throw new IllegalArgumentException("An unsuccessful email send requires safe failure metadata.");
            }
            if (result == EmailAttemptResult.AMBIGUOUS
                    && failureCode != EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME) {
                throw new IllegalArgumentException(
                        "An ambiguous email send requires the ambiguous transport failure code.");
            }
            failureMessage = EmailDeliveryValuePolicy.normalizeFailureMessage(failureMessage);
        }
        providerMessageId = EmailDeliveryValuePolicy.normalizeOptionalText(
                providerMessageId, "Email provider message id", 200);
    }

    @Override
    public String toString() {
        return "EmailSendResult[result=" + result
                + ", failureCode=" + failureCode
                + ", failureMessagePresent=" + (failureMessage != null)
                + ", providerMessageIdPresent=" + (providerMessageId != null)
                + ']';
    }
}
