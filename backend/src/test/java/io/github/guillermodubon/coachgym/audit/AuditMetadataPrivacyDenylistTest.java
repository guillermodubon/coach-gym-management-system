package io.github.guillermodubon.coachgym.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regression coverage for metadata privacy denylist fragments. */
class AuditMetadataPrivacyDenylistTest {

    @ParameterizedTest(name = "sensitive metadata key is never projected: {0}")
    @ValueSource(strings = {
        "password",
        "passwordHash",
        "token",
        "qrPayload",
        "smtpPassword",
        "emailBody",
        "attachmentPath",
        "stripeSecretKey",
        "webhookPayload",
        "cardNumber",
        "cvc",
        "pin",
        "expiry",
        "authorization",
        "cookie",
        "rawSql",
        "stackTrace",
        "exceptionMessage",
        "unknownOperationalValue"
    })
    void sensitiveOrUnknownKeysAreDroppedEvenWhenMetadataContainsAnAttackerValue(
            String key) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("amount", "25.00");
        source.put(key, "LEAK-" + key);

        AuditMetadataProjection projection = new AuditMetadataSanitizer()
                .sanitize("PAYMENT_ATTEMPT_FAILED", source);

        assertThat(projection.values()).containsEntry("amount", "25.00");
        assertThat(projection.values()).doesNotContainKey(key);
        assertThat(projection.values().toString()).doesNotContain("LEAK-");
        assertThat(projection.metadataRedacted()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void binaryAndNestedProviderPayloadsFailClosedWithoutMutatingInput() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("payload", "provider-secret-payload");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("provider", nested);
        source.put("attemptResult", new byte[] {1, 2, 3});
        Map<String, Object> original = new LinkedHashMap<>(source);

        AuditMetadataProjection projection = new AuditMetadataSanitizer()
                .sanitize("PAYMENT_ATTEMPT_FAILED", source);

        assertThat(source).isEqualTo(original);
        assertThat(projection.values()).doesNotContainKey("attemptResult");
        assertThat(projection.values().toString())
                .doesNotContain("payload", "provider-secret-payload");
        assertThat(projection.metadataRedacted()).isTrue();
    }
}
