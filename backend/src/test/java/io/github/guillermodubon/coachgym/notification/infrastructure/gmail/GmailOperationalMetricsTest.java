package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GmailOperationalMetricsTest {

    @Test
    void recordsBoundedOAuthAndTransportOutcomesAndLatencyWithoutSensitiveTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GmailOperationalMetrics metrics = new GmailOperationalMetrics(registry);
        long started = System.nanoTime();

        metrics.recordOAuthRefresh(true, null, started);
        metrics.recordOAuthRefresh(
                false, GoogleOAuthFailureCode.AUTHENTICATION_FAILED, started);
        metrics.recordGmailSend(EmailSendResult.sent("provider-message-identifier"), started);
        metrics.recordGmailSend(EmailSendResult.ambiguous("Safe bounded failure."), started);

        assertThat(registry.get(GmailOperationalMetrics.OAUTH_REFRESHES)
                .tag("result", "SUCCESS")
                .tag("failure_code", "NONE")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(GmailOperationalMetrics.OAUTH_REFRESHES)
                .tag("result", "FAILED")
                .tag("failure_code", "AUTHENTICATION_FAILED")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(GmailOperationalMetrics.GMAIL_SENDS)
                .tag("result", EmailAttemptResult.AMBIGUOUS.name())
                .tag("failure_code", EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME.name())
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get(GmailOperationalMetrics.GMAIL_SEND_DURATION)
                .tag("result", EmailAttemptResult.SENT.name())
                .timer().count()).isEqualTo(1L);
        assertThat(registry.get(GmailOperationalMetrics.OAUTH_REFRESH_DURATION)
                .tag("result", "FAILED")
                .timer().count()).isEqualTo(1L);

        Set<String> allowedTagKeys = Set.of("result", "failure_code");
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags())
                    .extracting(io.micrometer.core.instrument.Tag::getKey)
                    .containsExactlyInAnyOrderElementsOf(allowedTagKeys);
            assertThat(meter.getId().getTags())
                    .noneMatch(tag -> tag.getValue().contains("provider-message-identifier")
                            || tag.getValue().contains("@"));
        });
    }
}
