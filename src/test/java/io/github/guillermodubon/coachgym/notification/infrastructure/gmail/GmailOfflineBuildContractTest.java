package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GmailOfflineBuildContractTest {

    private static final Path APPLICATION_CONFIGURATION =
            Path.of("src/main/resources/application.yml");
    private static final Path CI_WORKFLOW =
            Path.of(".github/workflows/backend-ci.yml");
    private static final Path GRADLE_BUILD = Path.of("build.gradle.kts");
    private static final Path DEPLOYMENT_GUIDE =
            Path.of("docs/deployment/gmail-api-email-delivery.md");
    private static final Path README = Path.of("README.md");
    private static final Path OAUTH_STUB_TEST = Path.of(
            "src/test/java/io/github/guillermodubon/coachgym/notification/"
                    + "infrastructure/gmail/GoogleOAuthTokenClientTest.java");
    private static final Path GMAIL_STUB_TEST = Path.of(
            "src/test/java/io/github/guillermodubon/coachgym/notification/"
                    + "infrastructure/gmail/GmailApiEmailSenderAdapterTest.java");

    @Test
    void standardCiAndApplicationDefaultsRequireNoGmailSecrets() throws Exception {
        String application = Files.readString(APPLICATION_CONFIGURATION);
        String workflow = Files.readString(CI_WORKFLOW);
        String gradle = Files.readString(GRADLE_BUILD);

        assertThat(application)
                .contains("enabled: ${EMAIL_ENABLED:false}")
                .contains("client-id: ${GMAIL_CLIENT_ID:}")
                .contains("client-secret: ${GMAIL_CLIENT_SECRET:}")
                .contains("refresh-token: ${GMAIL_REFRESH_TOKEN:}");
        assertThat(workflow)
                .contains("./gradlew clean check")
                .doesNotContain("GMAIL_CLIENT_ID", "GMAIL_CLIENT_SECRET", "GMAIL_REFRESH_TOKEN");
        assertThat(gradle)
                .contains("sourceSets.create(\"gmailIntegrationTest\")",
                        "tasks.register<Test>(\"gmailIntegrationTest\")",
                        "GMAIL_LIVE_TESTS_ENABLED")
                .doesNotContain("dependsOn(\"gmailIntegrationTest\")",
                        "dependsOn(gmailIntegrationTestSourceSet");
        assertThat(application)
                .contains("show-details: never", "show-components: never",
                        "include: readinessState,db,storage,email,stripe");
    }

    @Test
    void gmailHttpTestsUseOnlyEphemeralLoopbackServers() throws Exception {
        String oauthTest = Files.readString(OAUTH_STUB_TEST);
        String gmailTest = Files.readString(GMAIL_STUB_TEST);

        assertThat(oauthTest).contains("new InetSocketAddress(\"127.0.0.1\", 0)");
        assertThat(gmailTest)
                .contains("new InetSocketAddress(\"127.0.0.1\", 0)")
                .contains("createContext(\"/token\"")
                .contains("createContext(\"/gmail/v1/users/me/messages/send\"");
    }

    @Test
    void optionalLiveTaskHasNoDefaultRecipientAndUsesOnlySyntheticContent() throws Exception {
        String gradle = Files.readString(GRADLE_BUILD);
        String liveTest = Files.readString(Path.of(
                "src/gmailIntegrationTest/java/io/github/guillermodubon/coachgym/"
                        + "notification/infrastructure/gmail/GmailApiLiveIntegrationTest.java"));

        assertThat(gradle)
                .contains("onlyIf(\"Set GMAIL_LIVE_TESTS_ENABLED=true")
                .doesNotContain("dependsOn(\"gmailIntegrationTest\")");
        assertThat(liveTest)
                .contains("GMAIL_LIVE_TESTS_ENABLED",
                        "GMAIL_LIVE_TEST_RECIPIENT",
                        "GMAIL_LIVE_TEST_RECIPIENT_ALLOWLIST",
                        "application/pdf",
                        "image/png",
                        "EmailAttemptResult.SENT")
                .doesNotContain("example.com", "example.test", "System.out", "printStackTrace");
    }

    @Test
    void operatorDocumentationUsesRuntimeSettingNamesWithoutCredentialValues() throws Exception {
        String guide = Files.readString(DEPLOYMENT_GUIDE).replaceAll("\\s+", " ");
        String readme = Files.readString(README).replaceAll("\\s+", " ");

        assertThat(guide)
                .contains("EMAIL_ENABLED", "EMAIL_FROM_ADDRESS", "EMAIL_FROM_NAME",
                        "GMAIL_SENDER_ADDRESS", "GMAIL_CLIENT_ID", "GMAIL_CLIENT_SECRET",
                        "GMAIL_REFRESH_TOKEN", "GMAIL_CONNECT_TIMEOUT", "GMAIL_READ_TIMEOUT",
                        "GMAIL_WRITE_TIMEOUT", "EMAIL_MAX_ATTACHMENT_BYTES",
                        "EMAIL_MAX_MESSAGE_BYTES", "GOOGLE_OAUTH_TOKEN_URL",
                        "GOOGLE_OAUTH_EXPIRY_SAFETY_MARGIN",
                        "GMAIL_LIVE_TESTS_ENABLED", "GMAIL_LIVE_TEST_RECIPIENT_ALLOWLIST",
                        "`SENT` means provider acceptance only")
                .doesNotContain("GMAIL_CLIENT_SECRET=", "GMAIL_REFRESH_TOKEN=", "ya29.",
                        "client_secret.json", "refresh_token=", "-----BEGIN");
        assertThat(readme)
                .contains("synthetic demo clients", "PDF/PNG", "allowlisted recipient",
                        "does not guarantee inbox placement");
    }
}
