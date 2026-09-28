package io.github.guillermodubon.coachgym.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.application.EmailComposer;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmailConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withUserConfiguration(EmailConfiguration.class, gmailOAuthConfiguration());

    @Test
    void disabledEmailStartsWithoutGmailSecretsOrNetworkAccess() {
        contextRunner
                .withPropertyValues("coach-gym.email.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmailProperties.class);
                    assertThat(context).hasSingleBean(EmailComposer.class);
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context.getBean(EmailSender.class))
                            .isInstanceOf(DisabledEmailSender.class);
                });
    }

    @Test
    void enabledEmailSelectsOnlyGmailWithoutContactingGoogleAtStartup() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.email.enabled=true",
                        "coach-gym.email.from-address=coach-gym@example.test",
                        "coach-gym.email.gmail.sender-address=coach-gym@example.test",
                        "coach-gym.email.gmail.oauth.client-id=synthetic-client-id",
                        "coach-gym.email.gmail.oauth.client-secret=synthetic-client-secret",
                        "coach-gym.email.gmail.oauth.refresh-token=synthetic-refresh-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmailSender.class).getClass().getSimpleName())
                            .isEqualTo("GmailApiEmailSenderAdapter");
                });
    }

    @Test
    void enabledEmailFailsClosedWhenGmailSettingsAreMissingOrMisaligned() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.email.enabled=true",
                        "coach-gym.email.from-address=coach-gym@example.test",
                        "coach-gym.email.gmail.sender-address=different@example.test",
                        "coach-gym.email.gmail.oauth.client-id=synthetic-client-id",
                        "coach-gym.email.gmail.oauth.client-secret=synthetic-client-secret",
                        "coach-gym.email.gmail.oauth.refresh-token=synthetic-refresh-token")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().toString())
                            .doesNotContain("synthetic-client-id", "synthetic-client-secret",
                                    "synthetic-refresh-token", "different@example.test");
                });
    }

    @Test
    void invalidLifecycleSettingsFailFast() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.email.enabled=true",
                        "coach-gym.email.stale-pending-threshold=PT0S")
                .run(context -> assertThat(context).hasFailed());
    }

    private static Class<?> gmailOAuthConfiguration() {
        try {
            return Class.forName(
                    "io.github.guillermodubon.coachgym.notification.infrastructure.gmail."
                            + "GmailOAuthConfiguration");
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
