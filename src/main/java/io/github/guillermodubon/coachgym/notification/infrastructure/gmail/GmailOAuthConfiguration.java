package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Gmail transport and OAuth wiring; construction never contacts Google or requires secrets. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GmailApiProperties.class, GoogleOAuthProperties.class})
class GmailOAuthConfiguration {

    @Bean("googleOAuthHttpClient")
    HttpClient googleOAuthHttpClient(GoogleOAuthProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectionTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    GoogleOAuthTokenClient googleOAuthTokenClient(
            HttpClient googleOAuthHttpClient,
            GoogleOAuthProperties properties,
            GmailOperationalMetrics metrics) {
        ObjectMapper safeObjectMapper = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
        return new GoogleOAuthTokenClient(
                googleOAuthHttpClient, safeObjectMapper, properties, Clock.systemUTC(), metrics);
    }

    @Bean
    GmailOperationalMetrics gmailOperationalMetrics(MeterRegistry meterRegistry) {
        return new GmailOperationalMetrics(meterRegistry);
    }

    @Bean
    GmailApiEmailSenderFactory gmailApiEmailSenderFactory(
            GmailApiProperties apiProperties,
            GoogleOAuthTokenClient tokenClient,
            GmailOperationalMetrics metrics) {
        return new GmailApiEmailSenderFactory(apiProperties, tokenClient, metrics);
    }

    @Bean
    GmailConfigurationReadiness gmailConfigurationReadiness(
            GmailApiProperties apiProperties,
            GoogleOAuthProperties oauthProperties) {
        return new GmailConfigurationReadiness(apiProperties, oauthProperties);
    }
}
