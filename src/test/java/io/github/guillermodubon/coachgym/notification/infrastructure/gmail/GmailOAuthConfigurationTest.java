package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GmailOAuthConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withUserConfiguration(GmailOAuthConfiguration.class);

    @Test
    void startsWithoutGmailSecretsAndDoesNotContactGoogle() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GmailApiProperties.class);
            assertThat(context).hasSingleBean(GoogleOAuthProperties.class);
            assertThat(context).hasSingleBean(GoogleOAuthTokenClient.class);
            assertThat(context).hasSingleBean(GmailConfigurationReadiness.class);
            assertThat(context).hasSingleBean(GmailApiEmailSenderFactory.class);
            assertThat(context.getBean(GmailApiEmailSenderFactory.class).createSender())
                    .isInstanceOf(GmailApiEmailSenderAdapter.class);
        });
    }

    @Test
    void oauthHttpClientHasBoundedConnectTimeoutAndNeverFollowsRedirects() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            HttpClient client = context.getBean("googleOAuthHttpClient", HttpClient.class);
            assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
            assertThat(client.connectTimeout()).isPresent();
        });
    }

    @Test
    void bindsEnvironmentBackedSecretsWithoutExposingThemInPropertiesOutput() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.email.gmail.api-base-url=https://gmail.googleapis.com",
                        "coach-gym.email.gmail.sender-address=coachgym.demo@gmail.com",
                        "coach-gym.email.gmail.oauth.client-id=synthetic-client-id",
                        "coach-gym.email.gmail.oauth.client-secret=synthetic-client-secret",
                        "coach-gym.email.gmail.oauth.refresh-token=synthetic-refresh-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GoogleOAuthProperties properties = context.getBean(GoogleOAuthProperties.class);
                    assertThat(properties.clientId()).isEqualTo("synthetic-client-id");
                    assertThat(properties.toString()).doesNotContain(
                            "synthetic-client-id", "synthetic-client-secret", "synthetic-refresh-token");
                });
    }
}
