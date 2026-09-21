package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationIdentityQuery;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryTemplateData;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.application.EmailCompositionException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailTemplateComposerTest {

    private static final UUID SOURCE_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");

    @Test
    void composesReceiptWithCorrectDetailsAndEscapesHtml() {
        EmailDeliveryTemplateData data = new EmailDeliveryTemplateData(
                "Coach <Gym>", "Ana <Martinez>", "CLI-001", "REC-001", "PAY-001",
                "CONFIRMED", "2026-09-15T12:00:00Z", "25.00", "USD", "MEM-001",
                "Premium", null, true);
        EmailDeliverySource source = source(EmailDeliveryType.PAYMENT_RECEIPT, data,
                "application/pdf", "receipt.pdf");

        var composed = new EmailTemplateComposer(properties()).compose(source);

        assertThat(composed.templateVersion()).isEqualTo("v1");
        assertThat(composed.message().subject()).isEqualTo("Coach <Gym> payment receipt REC-001");
        assertThat(composed.message().plainTextBody())
                .contains("Receipt number: REC-001", "Payment code: PAY-001",
                        "Amount: 25.00 USD", "Stripe Test Mode", "Ana <Martinez>");
        assertThat(composed.message().htmlBody())
                .contains("Coach &lt;Gym&gt;", "Ana &lt;Martinez&gt;", "REC-001", "&lt;Gym&gt;")
                .doesNotContain("<Gym>", "<Martinez>")
                .contains("does not constitute a tax invoice");
    }

    @Test
    void composesCredentialWithSecurityWarningAndNoRawToken() {
        EmailDeliveryTemplateData data = new EmailDeliveryTemplateData(
                "Coach Gym", "Ana Martinez", "CLI-001", null, null, "ACTIVE",
                "2026-09-15T12:00:00Z", null, null, null, null, "CRED-001", false);
        EmailDeliverySource source = source(EmailDeliveryType.ACCESS_CREDENTIAL, data,
                "image/png", "credential.png");

        var composed = new EmailTemplateComposer(properties()).compose(source);

        assertThat(composed.message().subject()).isEqualTo("Coach Gym access credential");
        assertThat(composed.message().plainTextBody())
                .contains("Credential code: CRED-001", "do not share or publish")
                .doesNotContain("QR-RAW-TOKEN");
        assertThat(composed.message().htmlBody()).contains("CRED-001", "<strong>Security:</strong>")
                .doesNotContain("{{");
    }

    @Test
    void composesUsingCanonicalOrganizationInsteadOfLegacyTemplateData() {
        EmailDeliveryTemplateData data = new EmailDeliveryTemplateData(
                "Legacy Gym", "Ana Martinez", "CLI-001", "REC-001", "PAY-001",
                "CONFIRMED", "2026-09-15T12:00:00Z", "25.00", "USD", "MEM-001",
                "Premium", null, true);
        EmailDeliverySource source = source(EmailDeliveryType.PAYMENT_RECEIPT, data,
                "application/pdf", "receipt.pdf");
        OrganizationIdentityQuery organizationQuery = () -> Optional.of(
                new OrganizationDetails(
                        UUID.fromString("30000000-0000-0000-0000-000000000001"),
                        "COACH_GYM", "Coach Gym Legal", "Canonical Gym", "support@example.test",
                        "+50370000000", "America/El_Salvador", "USD", OrganizationStatus.ACTIVE,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"), 0));

        var composed = new EmailTemplateComposer(properties(), organizationQuery).compose(source);

        assertThat(composed.message().subject()).isEqualTo(
                "Canonical Gym payment receipt REC-001");
        assertThat(composed.message().plainTextBody())
                .contains("Canonical Gym")
                .doesNotContain("Legacy Gym");
    }

    @Test
    void truncatesLongSubjectsAndRejectsUnsupportedTemplateVersions() {
        EmailProperties shortSubject = new EmailProperties(true, "C".repeat(200), "v1",
                "no-reply@coach-gym.local", "Coach Gym", null, "localhost", 1025, null, null,
                false, false, Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 40, 254);
        var source = source(EmailDeliveryType.PAYMENT_RECEIPT,
                new EmailDeliveryTemplateData(null, null, null, "REC-001", null, null,
                        null, null, null, null, null, null, false),
                "application/pdf", "receipt.pdf");
        assertThat(new EmailTemplateComposer(shortSubject).compose(source).message().subject())
                .hasSize(40);

        EmailProperties unsupported = new EmailProperties(true, "Coach Gym", "v2",
                "no-reply@coach-gym.local", "Coach Gym", null, "localhost", 1025, null, null,
                false, false, Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254);
        assertThatThrownBy(() -> new EmailTemplateComposer(unsupported).compose(source))
                .isInstanceOf(EmailCompositionException.class);
    }

    private static EmailDeliverySource source(
            EmailDeliveryType type, EmailDeliveryTemplateData data,
            String contentType, String filename) {
        return new EmailDeliverySource(type, SOURCE_ID, CLIENT_ID, "ana@example.com",
                new EmailAttachment(filename, contentType, new byte[]{1, 2, 3}), data);
    }

    private static EmailProperties properties() {
        return new EmailProperties(true, "Coach Gym", "v1", "no-reply@coach-gym.local",
                "Coach Gym", null, "localhost", 1025, null, null, false, false,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254);
    }
}
