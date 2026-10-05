package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DeploymentReadinessContractTest {

    private static final Path COMPOSE = Path.of("compose.yaml");
    private static final Path DOCKERFILE = Path.of("Dockerfile");
    private static final Path DOCKERIGNORE = Path.of(".dockerignore");
    private static final Path APPLICATION = Path.of("src/main/resources/application.yml");
    private static final Path LOCAL_PROFILE = Path.of("src/main/resources/application-local.yml");
    private static final Path DEPLOYED_PROFILE = Path.of("src/main/resources/application-supabase.yml");
    private static final Path ENV_EXAMPLE = Path.of(".env.example");
    private static final Pattern SECRET_LIKE_VALUE = Pattern.compile(
            "(?i)(?:sb_secret_[a-z0-9_-]{12,}|sk_(?:live|test)_[a-z0-9]{12,}|"
                    + "whsec_[a-z0-9]{12,}|"
                    + "ya29\\.[a-z0-9._-]+|gocspx-[a-z0-9_-]{12,}|"
                    + "-----begin (?:rsa|ec|openssh) private key-----)");
    private static final Pattern SENSITIVE_ENV_ASSIGNMENT = Pattern.compile(
            "(?im)^(?:POSTGRES_PASSWORD|DATABASE_PASSWORD|SUPABASE_DB_PASSWORD|"
                    + "SUPABASE_SECRET_KEY|GMAIL_CLIENT_ID|GMAIL_CLIENT_SECRET|GMAIL_REFRESH_TOKEN|"
                    + "STRIPE_SECRET_KEY|STRIPE_WEBHOOK_SIGNING_SECRET|BOOTSTRAP_ADMIN_PASSWORD)"
                    + "\\s*=\\s*(?!REPLACE_WITH_[A-Z0-9_]+\\s*$)[^\\r\\n]+$");
    private static final Pattern REAL_LIVE_TEST_RECIPIENT = Pattern.compile(
            "(?im)^GMAIL_LIVE_TEST_RECIPIENT(?:_ALLOWLIST)?\\s*=.*@"
                    + "(?!example\\.test(?:\\s|,|$))[^\\s,]+.*$");

    @Test
    void localComposeContainsOnlyHealthyPersistentPostgres() throws Exception {
        String compose = normalized(COMPOSE);
        List<String> serviceNames = Files.readAllLines(COMPOSE).stream()
                .takeWhile(line -> !line.equals("volumes:"))
                .filter(line -> line.matches("^  [a-z][a-z0-9_-]*:$"))
                .toList();

        assertThat(serviceNames).containsExactly("  postgres:");
        assertThat(compose)
                .contains("image: postgres:17-alpine")
                .contains("${postgres_db:?set postgres_db in .env}")
                .contains("${postgres_user:?set postgres_user in .env}")
                .contains("${postgres_password:?set postgres_password in .env}")
                .contains("${postgres_host_port:-5433}:5432")
                .contains("postgres-data:/var/lib/postgresql/data")
                .contains("name: coach-gym-postgres-data")
                .contains("pg_isready -u $$postgres_user -d $$postgres_db")
                .doesNotContain("mailpit", "smtp", "resend", "redis", "mysql");
    }

    @Test
    void backendImageIsMinimalNonRootAndExcludesSecretsAndLocalDataFromContext()
            throws Exception {
        String dockerfile = normalized(DOCKERFILE);
        String dockerignore = normalized(DOCKERIGNORE);

        assertThat(dockerfile)
                .contains("from eclipse-temurin:21-jdk as build")
                .contains("from eclipse-temurin:21-jre")
                .contains("copy src/main ./src/main")
                .contains("user 10001:10001")
                .contains("-dserver.port=${port:-8080}")
                .doesNotContain("password=", "secret=", ".env");
        assertThat(dockerignore)
                .contains(".env", ".env.*", ".codex", "docs", "data", "build", "src/test");
    }

    @Test
    void localAndSupabaseProfilesKeepSafeDefaultsAndForwardFlywayValidation()
            throws Exception {
        String application = normalized(APPLICATION);
        String local = normalized(LOCAL_PROFILE);
        String deployed = normalized(DEPLOYED_PROFILE);

        assertThat(application)
                .contains("enabled: ${email_enabled:false}")
                .contains("enabled: ${stripe_enabled:false}")
                .contains("enabled: ${bootstrap_admin_enabled:false}")
                .contains("validate-on-migrate: true")
                .contains("clean-disabled: true")
                .contains("csrf-cookie: same-site: ${csrf_cookie_same_site:${session_cookie_same_site:lax}}")
                .contains("csrf-cookie: same-site: ${csrf_cookie_same_site:${session_cookie_same_site:lax}} secure: ${csrf_cookie_secure:${session_cookie_secure:false}}")
                .doesNotContain("spring.mail", "smtp:", "resend:", "mailpit");
        assertThat(local).contains("enabled: ${email_enabled:false}");
        assertThat(deployed)
                .contains("compose: enabled: false")
                .contains("allowed-origins: ${cors_allowed_origins}")
                .contains("csrf-cookie: same-site: ${csrf_cookie_same_site:none} secure: ${csrf_cookie_secure:true}")
                .contains("hsts-enabled: true")
                .contains("same-site: none")
                .contains("secure: true");
    }

    @Test
    void runtimeProfilesUseDocumentedConfigurationNamesWithoutCredentialValues()
            throws Exception {
        String applicationConfig = (Files.readString(APPLICATION)
                        + Files.readString(DEPLOYED_PROFILE))
                .toLowerCase(Locale.ROOT);

        for (String environmentVariable : List.of(
                "SUPABASE_JDBC_URL",
                "SUPABASE_DB_USERNAME",
                "SUPABASE_DB_PASSWORD",
                "SUPABASE_FLYWAY_JDBC_URL",
                "SUPABASE_FLYWAY_USERNAME",
                "SUPABASE_FLYWAY_PASSWORD",
                "SUPABASE_STORAGE_URL",
                "SUPABASE_SECRET_KEY",
                "SUPABASE_STORAGE_BUCKET",
                "CORS_ALLOWED_ORIGINS",
                "CSRF_COOKIE_SAME_SITE",
                "CSRF_COOKIE_SECURE",
                "GMAIL_CLIENT_ID",
                "GMAIL_CLIENT_SECRET",
                "GMAIL_REFRESH_TOKEN",
                "EMAIL_ENABLED",
                "STRIPE_ENABLED",
                "STRIPE_SANDBOX",
                "STRIPE_SECRET_KEY",
                "STRIPE_WEBHOOK_SIGNING_SECRET",
                "BOOTSTRAP_ADMIN_ENABLED",
                "BOOTSTRAP_ADMIN_PASSWORD",
                "STAFF_INVITATION_ACCEPTANCE_URL",
                "STAFF_PASSWORD_RECOVERY_URL")) {
            assertThat(applicationConfig)
                    .contains(environmentVariable.toLowerCase(Locale.ROOT));
        }

        assertThat(applicationConfig).doesNotMatch(SECRET_LIKE_VALUE);
    }

    @Test
    void environmentExampleUsesCurrentNamesAndContainsOnlySafeCredentialPlaceholders()
            throws Exception {
        String example = Files.readString(ENV_EXAMPLE);

        assertThat(example).contains(
                "SPRING_PROFILES_ACTIVE=",
                "CORS_ALLOWED_ORIGINS=",
                "DATABASE_MAX_POOL_SIZE=",
                "SUPABASE_JDBC_URL=",
                "SUPABASE_DB_USERNAME=",
                "SUPABASE_DB_PASSWORD=",
                "SUPABASE_STORAGE_URL=https://REPLACE_WITH_PROJECT_REF.supabase.co",
                "SUPABASE_SECRET_KEY=",
                "SUPABASE_STORAGE_BUCKET=",
                "SUPABASE_STORAGE_CONNECTION_TIMEOUT=",
                "SUPABASE_STORAGE_REQUEST_TIMEOUT=",
                "EMAIL_ENABLED=",
                "EMAIL_FROM_ADDRESS=",
                "EMAIL_FROM_NAME=",
                "GMAIL_API_BASE_URL=",
                "GOOGLE_OAUTH_TOKEN_URL=",
                "GMAIL_SENDER_ADDRESS=",
                "GMAIL_CLIENT_ID=",
                "GMAIL_CLIENT_SECRET=",
                "GMAIL_REFRESH_TOKEN=",
                "GMAIL_CONNECT_TIMEOUT=",
                "GMAIL_READ_TIMEOUT=",
                "GMAIL_WRITE_TIMEOUT=",
                "EMAIL_MAX_ATTACHMENT_BYTES=",
                "EMAIL_MAX_MESSAGE_BYTES=",
                "GMAIL_LIVE_TESTS_ENABLED=",
                "GMAIL_LIVE_TEST_RECIPIENT=",
                "GMAIL_LIVE_TEST_RECIPIENT_ALLOWLIST=",
                "STRIPE_ENABLED=",
                "STRIPE_SANDBOX=",
                "STRIPE_SECRET_KEY=",
                "STRIPE_WEBHOOK_SIGNING_SECRET=",
                "STRIPE_SUCCESS_URL=",
                "STRIPE_CANCEL_URL=",
                "STRIPE_CONNECT_TIMEOUT=",
                "STRIPE_READ_TIMEOUT=",
                "STRIPE_WEBHOOK_TOLERANCE=",
                "STRIPE_MAX_WEBHOOK_PAYLOAD_BYTES=",
                "STRIPE_MAX_SIGNATURE_HEADER_LENGTH=")
                .doesNotContain("COACH_GYM_PAYMENT_STRIPE_", "SUPABASE_SERVICE_ROLE_KEY");
        assertThat(example)
                .doesNotContain("SUPABASE_STORAGE_URL=https://REPLACE_WITH_PROJECT_REF.supabase.co/storage/v1");
        assertThat(example)
                .doesNotMatch(SECRET_LIKE_VALUE)
                .doesNotMatch(SENSITIVE_ENV_ASSIGNMENT)
                .doesNotMatch(REAL_LIVE_TEST_RECIPIENT);
    }

    private static String normalized(Path path) throws Exception {
        return Files.readString(path)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
