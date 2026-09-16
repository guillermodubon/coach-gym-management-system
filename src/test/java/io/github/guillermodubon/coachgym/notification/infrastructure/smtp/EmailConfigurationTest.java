package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.application.EmailComposer;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmailConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmailConfiguration.class);

    @Test
    void disabledEmailStartsWithSafeNoOpSender() {
        contextRunner
                .withPropertyValues("coach-gym.email.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmailProperties.class);
                    assertThat(context).hasSingleBean(EmailComposer.class);
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context).hasBean("emailSender");
                    assertThat(context.getBean(EmailSender.class))
                            .isInstanceOf(DisabledEmailSender.class);
                });
    }

    @Test
    void enabledEmailCreatesSmtpAdapterWithoutConnectingAtStartup() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.email.enabled=true",
                        "coach-gym.email.smtp-host=localhost",
                        "coach-gym.email.smtp-port=1025",
                        "coach-gym.email.from-address=no-reply@coach-gym.local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmailSender.class))
                            .isInstanceOf(SmtpEmailSender.class);
                });
    }

    @Test
    void enabledAuthenticationFailsFastWithoutCredentials() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.email.enabled=true",
                        "coach-gym.email.smtp-auth-enabled=true",
                        "coach-gym.email.from-address=no-reply@coach-gym.local")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidStalePendingThresholdFailsFastWithoutExposingConfiguration() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.email.enabled=true",
                        "coach-gym.email.stale-pending-threshold=PT0S",
                        "coach-gym.email.from-address=no-reply@coach-gym.local")
                .run(context -> assertThat(context).hasFailed());
    }
}
