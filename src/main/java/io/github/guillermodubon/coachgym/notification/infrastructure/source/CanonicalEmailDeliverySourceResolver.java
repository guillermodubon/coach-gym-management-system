package io.github.guillermodubon.coachgym.notification.infrastructure.source;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialEmailSource;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialEmailSourceQuery;
import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryTemplateData;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryAttachmentException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryDataAccessException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryRecipientUnavailableException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySourceNotFoundException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySourceResolver;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptEmailSource;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptEmailSourceQuery;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves only server-owned source projections and canonical stored bytes.
 * No request value can override the source/client/recipient association.
 */
@Component
public class CanonicalEmailDeliverySourceResolver implements EmailDeliverySourceResolver {

    private final PaymentReceiptEmailSourceQuery receiptQuery;
    private final AccessCredentialEmailSourceQuery credentialQuery;
    private final ClientQuery clientQuery;

    public CanonicalEmailDeliverySourceResolver(
            PaymentReceiptEmailSourceQuery receiptQuery,
            AccessCredentialEmailSourceQuery credentialQuery,
            ClientQuery clientQuery) {
        this.receiptQuery = Objects.requireNonNull(receiptQuery);
        this.credentialQuery = Objects.requireNonNull(credentialQuery);
        this.clientQuery = Objects.requireNonNull(clientQuery);
    }

    @Override
    public EmailDeliverySource resolve(
            EmailDeliveryType deliveryType, UUID sourceResourceId) {
        if (deliveryType == null || sourceResourceId == null) {
            throw new IllegalArgumentException("Email delivery source is required.");
        }
        return switch (deliveryType) {
            case PAYMENT_RECEIPT -> resolveReceipt(sourceResourceId);
            case ACCESS_CREDENTIAL -> resolveCredential(sourceResourceId);
        };
    }

    @Override
    public EmailDeliverySource resolvePaymentReceiptForPayment(UUID paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment id is required.");
        }
        PaymentReceiptEmailSource source;
        try {
            source = receiptQuery.findByPaymentId(paymentId)
                    .orElseThrow(() -> new EmailDeliverySourceNotFoundException(
                            EmailDeliveryType.PAYMENT_RECEIPT, paymentId));
        } catch (EmailDeliverySourceNotFoundException exception) {
            throw exception;
        } catch (EmailDeliveryDataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EmailDeliveryAttachmentException(
                    EmailDeliveryType.PAYMENT_RECEIPT, paymentId,
                    "Payment receipt attachment is unavailable.");
        }
        if (source.clientId() == null || source.receipt().paymentId() == null
                || !paymentId.equals(source.receipt().paymentId())) {
            throw new EmailDeliverySourceNotFoundException(
                    EmailDeliveryType.PAYMENT_RECEIPT, paymentId);
        }
        return resolveReceiptSource(source);
    }

    @Override
    public EmailDeliverySource resolveCurrentAccessCredentialForClient(UUID clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("Client id is required.");
        }
        AccessCredentialEmailSource source;
        try {
            source = credentialQuery.findActiveByClientId(clientId)
                    .orElseThrow(() -> new EmailDeliverySourceNotFoundException(
                            EmailDeliveryType.ACCESS_CREDENTIAL, clientId));
        } catch (EmailDeliverySourceNotFoundException exception) {
            throw exception;
        } catch (EmailDeliveryDataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EmailDeliveryAttachmentException(
                    EmailDeliveryType.ACCESS_CREDENTIAL, clientId,
                    "Access credential attachment is unavailable.");
        }
        if (!clientId.equals(source.clientId())) {
            throw new EmailDeliverySourceNotFoundException(
                    EmailDeliveryType.ACCESS_CREDENTIAL, clientId);
        }
        return resolveCredentialSource(source);
    }

    private EmailDeliverySource resolveReceipt(UUID receiptId) {
        PaymentReceiptEmailSource source = safelyFindReceipt(receiptId)
                .orElseThrow(() -> new EmailDeliverySourceNotFoundException(
                        EmailDeliveryType.PAYMENT_RECEIPT, receiptId));
        if (!receiptId.equals(source.receiptId()) || source.clientId() == null) {
            throw new EmailDeliveryDataAccessException(
                    "Payment receipt source association is invalid.", null);
        }
        return resolveReceiptSource(source);
    }

    private EmailDeliverySource resolveReceiptSource(PaymentReceiptEmailSource source) {
        UUID receiptId = source.receiptId();
        if (source.clientId() == null) {
            throw new EmailDeliveryDataAccessException(
                    "Payment receipt source association is invalid.", null);
        }
        ResolvedRecipient resolvedRecipient = recipientFor(
                source.clientId(), EmailDeliveryType.PAYMENT_RECEIPT, receiptId);
        EmailAttachment attachment;
        try {
            attachment = new EmailAttachment(
                    "payment-receipt-" + receiptId + ".pdf",
                    source.document().contentType(),
                    source.document().bytes(),
                    source.document().checksumSha256());
        } catch (IllegalArgumentException exception) {
            throw new EmailDeliveryAttachmentException(
                    EmailDeliveryType.PAYMENT_RECEIPT, receiptId,
                    "Payment receipt attachment is invalid.");
        }
        return new EmailDeliverySource(
                EmailDeliveryType.PAYMENT_RECEIPT,
                receiptId,
                source.clientId(),
                resolvedRecipient.email(),
                attachment,
                new EmailDeliveryTemplateData(
                        null,
                        displayName(resolvedRecipient.client()),
                        resolvedRecipient.client().clientCode(),
                        source.receipt().receiptNumber(),
                        source.receipt().paymentCode(),
                        source.receipt().paymentStatus().name(),
                        source.receipt().paidAt().toString(),
                        source.receipt().amount().toPlainString(),
                        source.receipt().currency(),
                        source.receipt().membershipCode(),
                        source.receipt().planName(),
                        null,
                        source.receipt().testMode()));
    }

    private EmailDeliverySource resolveCredential(UUID credentialId) {
        AccessCredentialEmailSource source = safelyFindCredential(credentialId)
                .orElseThrow(() -> new EmailDeliverySourceNotFoundException(
                        EmailDeliveryType.ACCESS_CREDENTIAL, credentialId));
        if (!credentialId.equals(source.credentialId())) {
            throw new EmailDeliveryDataAccessException(
                    "Access credential source association is invalid.", null);
        }
        return resolveCredentialSource(source);
    }

    private EmailDeliverySource resolveCredentialSource(AccessCredentialEmailSource source) {
        UUID credentialId = source.credentialId();
        ResolvedRecipient resolvedRecipient = recipientFor(
                source.clientId(), EmailDeliveryType.ACCESS_CREDENTIAL, credentialId);
        EmailAttachment attachment;
        try {
            attachment = new EmailAttachment(
                    "access-credential-" + credentialId + ".png",
                    source.contentType(),
                    source.document().bytes(),
                    source.checksumSha256());
        } catch (IllegalArgumentException exception) {
            throw new EmailDeliveryAttachmentException(
                    EmailDeliveryType.ACCESS_CREDENTIAL, credentialId,
                    "Access credential attachment is invalid.");
        }
        return new EmailDeliverySource(
                EmailDeliveryType.ACCESS_CREDENTIAL,
                credentialId,
                source.clientId(),
                resolvedRecipient.email(),
                attachment,
                new EmailDeliveryTemplateData(
                        null,
                        displayName(resolvedRecipient.client()),
                        resolvedRecipient.client().clientCode(),
                        null,
                        null,
                        source.credential().status().name(),
                        source.credential().issuedAt().toString(),
                        null,
                        null,
                        null,
                        null,
                        source.credential().credentialCode(),
                        false));
    }

    private ResolvedRecipient recipientFor(
            UUID clientId, EmailDeliveryType deliveryType, UUID sourceResourceId) {
        Optional<ClientDetails> client;
        try {
            client = clientQuery.findClientById(clientId);
        } catch (RuntimeException exception) {
            throw new EmailDeliveryDataAccessException(
                    "Authoritative client could not be read.", exception);
        }
        if (client.isEmpty() || !clientId.equals(client.get().id())) {
            throw new EmailDeliverySourceNotFoundException(deliveryType, sourceResourceId);
        }
        String email = client.get().email();
        try {
            return new ResolvedRecipient(
                    client.get(), EmailDeliveryValuePolicy.normalizeRecipient(email));
        } catch (RuntimeException exception) {
            throw new EmailDeliveryRecipientUnavailableException(clientId);
        }
    }

    private record ResolvedRecipient(ClientDetails client, String email) {
    }

    private static String displayName(ClientDetails client) {
        String firstName = client.firstName() == null ? "" : client.firstName().strip();
        String lastName = client.lastName() == null ? "" : client.lastName().strip();
        String displayName = (firstName + " " + lastName).strip();
        return displayName.isBlank() ? null : displayName;
    }

    private Optional<PaymentReceiptEmailSource> safelyFindReceipt(UUID receiptId) {
        try {
            return receiptQuery.findByReceiptId(receiptId);
        } catch (EmailDeliveryDataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EmailDeliveryAttachmentException(
                    EmailDeliveryType.PAYMENT_RECEIPT, receiptId,
                    "Payment receipt attachment is unavailable.");
        }
    }

    private Optional<AccessCredentialEmailSource> safelyFindCredential(UUID credentialId) {
        try {
            return credentialQuery.findByCredentialId(credentialId);
        } catch (EmailDeliveryDataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EmailDeliveryAttachmentException(
                    EmailDeliveryType.ACCESS_CREDENTIAL, credentialId,
                    "Access credential attachment is unavailable.");
        }
    }
}
