package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryQuery;
import io.github.guillermodubon.coachgym.notification.application.EmailComposer;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySourceResolver;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStore;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.application.RequestAccessCredentialEmailCommand;
import io.github.guillermodubon.coachgym.notification.application.RequestPaymentReceiptEmailCommand;
import io.github.guillermodubon.coachgym.notification.application.RetryEmailDeliveryCommand;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class EmailDeliveryArchitectureContractTest {

    @Test
    void deliveryPortsRemainPublicTechnologyNeutralInterfaces() {
        assertThat(List.of(
                EmailSender.class,
                EmailComposer.class,
                EmailDeliveryStore.class,
                EmailDeliveryQuery.class,
                EmailDeliverySourceResolver.class))
                .allSatisfy(type -> {
                    assertThat(type.isInterface()).isTrue();
                    assertThat(type.getPackageName()).isEqualTo(
                            "io.github.guillermodubon.coachgym.notification.application");
                });
    }

    @Test
    void sourceResolverIsAProviderNeutralApplicationPort() {
        assertThat(EmailDeliverySourceResolver.class.isInterface()).isTrue();
        assertThat(EmailDeliverySourceResolver.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.notification.application");
        assertThat(EmailDeliverySource.class.getRecordComponents())
                .allSatisfy(component -> assertThat(component.getType().getName().toLowerCase(Locale.ROOT))
                        .doesNotContain("jakarta.mail", "javax.mail", "smtp", "path", "token"));
    }

    @Test
    void providerTypesRemainConfinedToSmtpInfrastructure()
            throws Exception {
        Path moduleRoot = Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/notification");
        List<Path> providerFiles;
        try (var paths = Files.walk(moduleRoot)) {
            providerFiles = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).toLowerCase(Locale.ROOT)
                            .matches("(?s).*\\b(jakarta\\.mail|javax\\.mail|springframework\\.mail).*"))
                    .toList();
        }
        assertThat(providerFiles).allSatisfy(path -> assertThat(path.toString().replace('\\', '/'))
                .contains("notification/infrastructure/smtp/"));
    }

    @Test
    void publicMessageContractsRemainFreeOfProviderTypesAndPaths() {
        List<Class<?>> contracts = List.of(
                EmailMessage.class,
                EmailAttachment.class,
                ComposedEmail.class,
                EmailDeliverySource.class);
        assertThat(contracts).allSatisfy(type -> {
            assertThat(type.getPackageName()).isEqualTo(
                    "io.github.guillermodubon.coachgym.notification");
            assertThat(type.getRecordComponents())
                    .allSatisfy(component -> assertThat(component.getType().getName().toLowerCase(Locale.ROOT))
                            .doesNotContain("jakarta.mail", "javax.mail", "path", "smtp"));
        });
    }

    @Test
    void composeDefinesStableMailpitServiceAndLocalPorts() throws Exception {
        String compose = Files.readString(Path.of("compose.yaml")).toLowerCase(Locale.ROOT);
        assertThat(compose)
                .contains("mailpit:")
                .contains("axllent/mailpit:v1.21.8")
                .contains("1025:1025")
                .contains("8025:8025")
                .contains("healthcheck:");
        String application = Files.readString(Path.of("src/main/resources/application.yml"))
                .toLowerCase(Locale.ROOT);
        assertThat(application)
                .contains("smtp-host: ${smtp_host:localhost}")
                .contains("smtp-port: ${smtp_port:1025}")
                .contains("enabled: ${email_enabled:false}");
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"))
                .toLowerCase(Locale.ROOT);
        assertThat(local)
                .contains("on-profile: local")
                .contains("smtp-host: ${smtp_host:localhost}")
                .contains("smtp-port: ${smtp_port:1025}")
                .contains("enabled: ${email_enabled:true}");
    }

    @Test
    void publicDeliveryPolicyRemainsExplicitAndSourceControlled() {
        assertThat(EmailDeliveryType.values())
                .containsExactly(EmailDeliveryType.PAYMENT_RECEIPT,
                        EmailDeliveryType.ACCESS_CREDENTIAL);
        assertThat(EmailDeliveryStatus.values())
                .containsExactly(EmailDeliveryStatus.PENDING,
                        EmailDeliveryStatus.SENT,
                        EmailDeliveryStatus.FAILED);
        assertThat(EmailAttemptResult.values())
                .containsExactly(EmailAttemptResult.SENT,
                        EmailAttemptResult.FAILED,
                        EmailAttemptResult.AMBIGUOUS);
        assertThat(EmailDeliveryFailureCode.values())
                .contains(EmailDeliveryFailureCode.RECIPIENT_UNAVAILABLE,
                        EmailDeliveryFailureCode.ATTACHMENT_INTEGRITY_FAILED,
                        EmailDeliveryFailureCode.RETRY_LIMIT_REACHED,
                        EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME);

        assertThat(RequestPaymentReceiptEmailCommand.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("paymentId");
        assertThat(RequestAccessCredentialEmailCommand.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("clientId");
        assertThat(RetryEmailDeliveryCommand.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("deliveryId", "expectedVersion");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
