package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class GmailMimeEncoderTest {

    private static final String SENDER = "coach-gym@example.test";
    private final GmailMimeEncoder encoder = new GmailMimeEncoder();

    @Test
    void encodesPlainAndHtmlAlternativesWithUtf8HeadersAndCrLf() throws Exception {
        EmailMessage source = new EmailMessage(
                "ana@example.test", SENDER, "Gimnasio Ágil", "reply@example.test",
                "Recibo de José — mes de abril", "Pago recibido: café y piñata.",
                "<p>Pago recibido: café y piñata.</p>", null);

        byte[] bytes = encoder.encode(source, SENDER);
        MimeMessage parsed = parse(bytes);
        Multipart alternative = (Multipart) parsed.getContent();
        List<String> bodies = new ArrayList<>();
        for (int index = 0; index < alternative.getCount(); index++) {
            bodies.add(alternative.getBodyPart(index).getContentType().toLowerCase());
        }

        assertThat(parsed.getSubject()).isEqualTo(source.subject());
        assertThat(((InternetAddress) parsed.getFrom()[0]).getPersonal()).isEqualTo("Gimnasio Ágil");
        assertThat(parsed.getReplyTo()[0].toString()).contains("reply@example.test");
        assertThat(bodies).hasSize(2)
                .anyMatch(type -> type.startsWith("text/plain; charset=utf-8"))
                .anyMatch(type -> type.startsWith("text/html; charset=utf-8"));
        assertThat(bareLineFeedExists(bytes)).isFalse();
        assertThat(new String(bytes, StandardCharsets.ISO_8859_1))
                .contains("=?UTF-8?");
    }

    @Test
    void preservesReceiptPdfAndCredentialPngBytesAndUsesSafeEncodedFilenames() throws Exception {
        for (EmailAttachment attachment : List.of(
                new EmailAttachment("recibo-ábril.pdf", "application/pdf",
                        new byte[]{0x25, 0x50, 0x44, 0x46, 0x2d, 0x31}),
                new EmailAttachment("credencial-ñ.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a}))) {
            MimeMessage parsed = parse(encoder.encode(message(attachment), SENDER));
            Multipart mixed = (Multipart) parsed.getContent();
            BodyPart file = mixed.getBodyPart(1);

            assertThat(file.getFileName()).isEqualTo(attachment.filename());
            assertThat(file.getContentType().toLowerCase())
                    .startsWith(attachment.contentType());
            assertThat(file.getInputStream().readAllBytes()).containsExactly(attachment.bytes());
            assertThat(mixed.getBodyPart(0).getContentType().toLowerCase())
                    .startsWith("multipart/alternative");
        }
    }

    @Test
    void encodesIdentityLinksAsBodyContentAndRejectsHeaderInjectionOrSenderMismatch()
            throws Exception {
        String syntheticLink = "https://app.example.test/accept?token=synthetic-token";
        EmailMessage identity = new EmailMessage(
                "invitee@example.test", SENDER, "Coach Gym", null,
                "Invitación de personal", "Abra este enlace: " + syntheticLink,
                "<a href=\"" + syntheticLink + "\">Aceptar invitación</a>", null);
        MimeMessage parsed = parse(encoder.encode(identity, SENDER));

        assertThat(((Multipart) parsed.getContent()).getBodyPart(0).getContent().toString())
                .contains(syntheticLink);
        assertThatThrownBy(() -> new EmailMessage(
                "ana@example.test", SENDER, "Coach Gym", null,
                "subject\r\nBcc: attacker@example.test", "plain", "<p>html</p>", null))
                .isInstanceOf(EmailDeliveryValidationException.class);
        assertThatThrownBy(() -> new EmailAttachment("../receipt.pdf", "application/pdf", new byte[]{1}))
                .isInstanceOf(EmailDeliveryValidationException.class);
        assertThatThrownBy(() -> encoder.encode(identity, "other@example.test"))
                .isInstanceOf(GmailMimeEncodingException.class);
    }

    private static EmailMessage message(EmailAttachment attachment) {
        return new EmailMessage(
                "ana@example.test", SENDER, "Coach Gym", null,
                "Payment receipt", "Plain receipt text", "<p>Receipt</p>", attachment);
    }

    private static MimeMessage parse(byte[] bytes) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes));
    }

    private static boolean bareLineFeedExists(byte[] bytes) {
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == '\n' && (index == 0 || bytes[index - 1] != '\r')) {
                return true;
            }
        }
        return false;
    }
}
