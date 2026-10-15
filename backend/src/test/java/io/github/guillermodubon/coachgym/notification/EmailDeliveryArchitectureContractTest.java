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
import io.github.guillermodubon.coachgym.notification.infrastructure.gmail.GmailApiProperties;
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
    void providerTypesRemainConfinedToTransportInfrastructure()
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
                .matches(".*notification/infrastructure/gmail/.*"));
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
    void publicContractsDoNotExposeGmailSdkTypes() {
        List<Class<?>> contracts = List.of(
                EmailSender.class,
                EmailMessage.class,
                EmailAttachment.class,
                EmailSendResult.class,
                EmailDeliveryLifecycleEvent.class);
        contracts.forEach(type -> {
            assertThat(type.getName().toLowerCase(Locale.ROOT))
                    .doesNotContain("com.google.api", "com.google.auth", "gmail.v1");
            for (var field : type.getDeclaredFields()) {
                assertThat(field.getType().getName().toLowerCase(Locale.ROOT))
                        .doesNotContain("com.google.api", "com.google.auth", "gmail.v1");
            }
            for (var method : type.getDeclaredMethods()) {
                assertThat(method.getReturnType().getName().toLowerCase(Locale.ROOT))
                        .doesNotContain("com.google.api", "com.google.auth", "gmail.v1");
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertThat(parameter.getName().toLowerCase(Locale.ROOT))
                            .doesNotContain("com.google.api", "com.google.auth", "gmail.v1");
                }
            }
        });
    }

    @Test
    void oauthImplementationAndConfigurationRemainInsideGmailInfrastructure() {
        assertThat(GmailApiProperties.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.notification.infrastructure.gmail");
        List<Class<?>> applicationContracts = List.of(
                EmailSender.class,
                EmailMessage.class,
                EmailAttachment.class,
                EmailSendResult.class,
                EmailDeliveryLifecycleEvent.class);
        applicationContracts.forEach(contract -> {
            assertThat(contract.getPackageName()).doesNotContain("infrastructure");
            assertThat(contract.getName().toLowerCase(Locale.ROOT))
                    .doesNotContain("googleoauth", "oauthaccesstoken", "gmailapi");
        });
    }

    @Test
    void removedProviderServicesDependenciesAndConfigurationStayAbsent() throws Exception {
        String compose = Files.readString(Path.of("compose.yaml")).toLowerCase(Locale.ROOT);
        assertThat(compose)
                .doesNotContain("mailpit", "1025:1025", "8025:8025");

        String application = Files.readString(Path.of("src/main/resources/application.yml"))
                .toLowerCase(Locale.ROOT);
        assertThat(application)
                .contains("enabled: ${email_enabled:false}", "gmail:")
                .doesNotContain("email_provider", "smtp_", "smtp-host", "smtp-port",
                        "smtp-password", "resend_api_key", "resend_base_url",
                        "resend:", "mailpit");

        String local = Files.readString(Path.of("src/main/resources/application-local.yml"))
                .toLowerCase(Locale.ROOT);
        assertThat(local)
                .contains("on-profile: local")
                .contains("enabled: ${email_enabled:false}")
                .doesNotContain("smtp", "resend", "mailpit");

        String supabase = Files.readString(Path.of("src/main/resources/application-supabase.yml"))
                .toLowerCase(Locale.ROOT);
        assertThat(supabase).doesNotContain("email_provider", "smtp", "resend", "mailpit");

        String gradle = Files.readString(Path.of("build.gradle.kts")).toLowerCase(Locale.ROOT);
        assertThat(gradle)
                .contains("jakarta.mail:jakarta.mail-api", "org.eclipse.angus:angus-mail:2.0.5",
                        "org.eclipse.angus:angus-activation:2.0.3")
                .doesNotContain("spring-boot-starter-mail", "spring-boot-mail");

        Path infrastructure = Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/notification/infrastructure");
        try (var paths = Files.walk(infrastructure)) {
            assertThat(paths.map(path -> path.toString().replace('\\', '/')).toList())
                    .noneMatch(path -> path.contains("/smtp/") || path.contains("/resend/"));
        }
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
