package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

/** Builds a complete provider-ready UTF-8 MIME message without retaining its contents. */
final class GmailMimeEncoder {

    byte[] encode(EmailMessage source, String configuredSenderAddress) {
        if (source == null || configuredSenderAddress == null
                || configuredSenderAddress.isBlank()) {
            throw new GmailMimeEncodingException();
        }
        if (!source.fromAddress().equalsIgnoreCase(configuredSenderAddress.strip())) {
            throw new GmailMimeEncodingException();
        }

        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
            InternetAddress from = address(source.fromAddress());
            if (source.fromName() == null) {
                message.setFrom(from);
            } else {
                message.setFrom(new InternetAddress(
                        from.getAddress(), source.fromName(), StandardCharsets.UTF_8.name()));
            }
            message.setRecipients(
                    jakarta.mail.Message.RecipientType.TO,
                    new Address[]{address(source.recipient())});
            if (source.replyTo() != null) {
                message.setReplyTo(new Address[]{address(source.replyTo())});
            }
            message.setSubject(source.subject(), StandardCharsets.UTF_8.name());

            MimeMultipart alternative = new MimeMultipart("alternative");
            MimeBodyPart plainText = new MimeBodyPart();
            plainText.setText(source.plainTextBody(), StandardCharsets.UTF_8.name(), "plain");
            alternative.addBodyPart(plainText);

            MimeBodyPart html = new MimeBodyPart();
            html.setText(source.htmlBody(), StandardCharsets.UTF_8.name(), "html");
            alternative.addBodyPart(html);

            if (source.attachment() == null) {
                message.setContent(alternative);
            } else {
                MimeMultipart mixed = new MimeMultipart("mixed");
                MimeBodyPart alternatives = new MimeBodyPart();
                alternatives.setContent(alternative);
                mixed.addBodyPart(alternatives);

                MimeBodyPart attachment = new MimeBodyPart();
                attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(
                        source.attachment().bytes(), source.attachment().contentType())));
                attachment.setFileName(source.attachment().filename());
                attachment.setDisposition(Part.ATTACHMENT);
                mixed.addBodyPart(attachment);
                message.setContent(mixed);
            }

            message.saveChanges();
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                message.writeTo(output);
                return output.toByteArray();
            }
        } catch (MessagingException | IOException | IllegalArgumentException exception) {
            throw new GmailMimeEncodingException();
        }
    }

    private static InternetAddress address(String value) throws AddressException {
        InternetAddress address = new InternetAddress(Objects.requireNonNull(value), true);
        address.validate();
        return address;
    }
}
