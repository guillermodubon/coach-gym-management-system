package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.util.UUID;

/** Resolves authoritative recipients and canonical attachments for one source. */
public interface EmailDeliverySourceResolver {

    EmailDeliverySource resolve(EmailDeliveryType deliveryType, UUID sourceResourceId);

    default EmailDeliverySource resolvePaymentReceipt(UUID receiptId) {
        return resolve(EmailDeliveryType.PAYMENT_RECEIPT, receiptId);
    }

    EmailDeliverySource resolvePaymentReceiptForPayment(UUID paymentId);

    default EmailDeliverySource resolveAccessCredential(UUID credentialId) {
        return resolve(EmailDeliveryType.ACCESS_CREDENTIAL, credentialId);
    }

    EmailDeliverySource resolveCurrentAccessCredentialForClient(UUID clientId);
}
