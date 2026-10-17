package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.notification.infrastructure.identity.StaffAccountActivatedIdentityEmailAdapter;
import io.github.guillermodubon.coachgym.notification.infrastructure.identity.StaffIdentitySecurityEmailAdapter;
import io.github.guillermodubon.coachgym.notification.infrastructure.identity.StaffInvitationIdentityEmailAdapter;
import io.github.guillermodubon.coachgym.notification.infrastructure.identity.StaffPasswordRecoveryIdentityEmailAdapter;
import io.github.guillermodubon.coachgym.notification.infrastructure.email.EmailProperties;
import io.github.guillermodubon.coachgym.notification.infrastructure.email.IdentityEmailProperties;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentitySecurityNoticeType;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffIdentitySecurityEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmail;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GmailIdentityEmailIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:30:00Z");
    private static final String RECIPIENT = "staff@example.test";
    private static final String INVITATION_TOKEN = "A".repeat(43);
    private static final String RECOVERY_TOKEN = recoveryToken();

    private GmailHttpStub gmail;
    private StaffInvitationIdentityEmailAdapter invitationSender;
    private StaffAccountActivatedIdentityEmailAdapter activationSender;
    private StaffPasswordRecoveryIdentityEmailAdapter recoverySender;
    private StaffIdentitySecurityEmailAdapter securitySender;
    private int deliveriesBefore;
    private int attemptsBefore;

    @BeforeEach
    void prepareIdentitySendersAgainstGmailStub() throws Exception {
        deliveriesBefore = jdbcTemplate.queryForObject(
                "select count(*) from gym.email_deliveries", Integer.class);
        attemptsBefore = jdbcTemplate.queryForObject(
                "select count(*) from gym.email_delivery_attempts", Integer.class);
        gmail = GmailHttpStub.start();
        var sender = gmail.sender();
        EmailProperties properties = new EmailProperties(
                false, "Coach Gym", "v1", GmailHttpStub.SENDER, "Coach Gym",
                null, 10 * 1024 * 1024, 200, 3, Duration.ofMinutes(15));
        IdentityEmailProperties identityProperties = new IdentityEmailProperties(
                "https://staff.example.test/accept-invitation",
                "https://staff.example.test/recover-password");
        invitationSender = new StaffInvitationIdentityEmailAdapter(
                sender, properties, identityProperties);
        activationSender = new StaffAccountActivatedIdentityEmailAdapter(sender, properties);
        recoverySender = new StaffPasswordRecoveryIdentityEmailAdapter(
                sender, properties, identityProperties);
        securitySender = new StaffIdentitySecurityEmailAdapter(sender, properties);
    }

    @AfterEach
    void closeGmailStub() {
        if (gmail != null) {
            gmail.close();
        }
    }

    @Test
    void allEphemeralIdentityEmailTypesUseGmailWithoutCreatingDurableEmailRows()
            throws Exception {
        StaffInvitationEmail invitation = new StaffInvitationEmail(
                RECIPIENT, "RECEPTIONIST", "BRANCH", List.of("North branch"),
                NOW.plusSeconds(3600), INVITATION_TOKEN);
        assertThat(invitationSender.sendInvitation(invitation))
                .isEqualTo(IdentityEmailDeliveryStatus.SENT);

        StaffAccountActivatedEmail activation = new StaffAccountActivatedEmail(
                RECIPIENT, "Staff Member", "RECEPTIONIST", "BRANCH", NOW);
        assertThat(activationSender.sendAccountActivated(activation))
                .isEqualTo(IdentityEmailDeliveryStatus.SENT);

        StaffPasswordRecoveryEmail recovery = new StaffPasswordRecoveryEmail(
                RECIPIENT, "Staff Member", RECOVERY_TOKEN, NOW.plusSeconds(900));
        recoverySender.sendPasswordRecoveryLink(recovery);

        for (IdentitySecurityNoticeType type : IdentitySecurityNoticeType.values()) {
            securitySender.sendSecurityNotice(new StaffIdentitySecurityEmail(
                    RECIPIENT,
                    "Staff Member",
                    type,
                    type == IdentitySecurityNoticeType.AUTHORITY_CHANGED ? "ADMIN" : "",
                    type == IdentitySecurityNoticeType.AUTHORITY_CHANGED ? "BRANCH" : "",
                    NOW));
        }

        assertThat(gmail.sendCount()).isEqualTo(9);
        assertThat(gmail.requests()).hasSize(9);
        assertThat(gmail.requests()).allSatisfy(request -> {
            assertThat(request.path()).isEqualTo("/gmail/v1/users/me/messages/send");
            assertThat(request.toString()).doesNotContain(
                    INVITATION_TOKEN, RECOVERY_TOKEN, "https://staff.example.test");
            assertThat(request.bodyUtf8()).doesNotContain(
                    INVITATION_TOKEN, RECOVERY_TOKEN, "https://staff.example.test");
        });

        List<MimeMessage> messages = new java.util.ArrayList<>();
        for (int index = 0; index < gmail.requests().size(); index++) {
            messages.add(parse(gmail.decodeRawMessage(index)));
        }
        assertIdentityMessage(messages.get(0), "Staff invitation");
        assertIdentityMessage(messages.get(1), "Staff account activated");
        assertIdentityMessage(messages.get(2), "Password recovery");
        for (int index = 3; index < messages.size(); index++) {
            assertIdentityMessage(messages.get(index), "Staff account security update");
        }

        Multipart invitationBodies = (Multipart) messages.get(0).getContent();
        Multipart recoveryBodies = (Multipart) messages.get(2).getContent();
        assertThat(invitationBodies.getBodyPart(0).getContent().toString())
                .contains("#token=" + INVITATION_TOKEN);
        assertThat(invitationBodies.getBodyPart(1).getContent().toString())
                .contains("#token=" + INVITATION_TOKEN);
        assertThat(recoveryBodies.getBodyPart(0).getContent().toString())
                .contains(RECOVERY_TOKEN);
        assertThat(recoveryBodies.getBodyPart(1).getContent().toString())
                .contains(RECOVERY_TOKEN);
        assertThat(invitation.toString()).doesNotContain(INVITATION_TOKEN, RECIPIENT);
        assertThat(recovery.toString()).doesNotContain(RECOVERY_TOKEN, RECIPIENT);
        assertThat(String.join("\n", Collections.list(messages.get(0).getAllHeaderLines())))
                .doesNotContain(INVITATION_TOKEN, "accept-invitation");
        assertThat(String.join("\n", Collections.list(messages.get(2).getAllHeaderLines())))
                .doesNotContain(RECOVERY_TOKEN, "recover-password");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.email_deliveries", Integer.class))
                .isEqualTo(deliveriesBefore);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.email_delivery_attempts", Integer.class))
                .isEqualTo(attemptsBefore);
    }

    @Test
    void identityTransportFailureRemainsBestEffortAndDoesNotCreateOutboxRows() {
        gmail.respondToNextSend(400, "private provider response");
        StaffAccountActivatedEmail activation = new StaffAccountActivatedEmail(
                RECIPIENT, "Staff Member", "RECEPTIONIST", "BRANCH", NOW);

        assertThat(activationSender.sendAccountActivated(activation))
                .isEqualTo(IdentityEmailDeliveryStatus.FAILED);
        assertThat(gmail.sendCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.email_deliveries", Integer.class))
                .isEqualTo(deliveriesBefore);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.email_delivery_attempts", Integer.class))
                .isEqualTo(attemptsBefore);
    }

    private void assertIdentityMessage(MimeMessage message, String expectedSubject)
            throws Exception {
        assertThat(message.getSubject()).isEqualTo(expectedSubject);
        assertThat(message.getRecipients(Message.RecipientType.TO))
                .extracting(address -> ((jakarta.mail.internet.InternetAddress) address).getAddress())
                .containsExactly(RECIPIENT);
        assertThat(message.getContent()).isInstanceOf(Multipart.class);
        Multipart bodies = (Multipart) message.getContent();
        assertThat(bodies.getContentType().toLowerCase()).contains("multipart/alternative");
        assertThat(bodies.getCount()).isEqualTo(2);
        assertThat(bodies.getBodyPart(0).getContentType().toLowerCase()).contains("text/plain");
        assertThat(bodies.getBodyPart(1).getContentType().toLowerCase()).contains("text/html");
        assertThat(bodies.getBodyPart(0).getFileName()).isNull();
        assertThat(bodies.getBodyPart(1).getFileName()).isNull();
    }

    private static MimeMessage parse(byte[] mime) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(mime));
    }

    private static String recoveryToken() {
        byte[] token = new byte[32];
        token[0] = 1;
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
