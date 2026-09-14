package io.github.guillermodubon.coachgym.accesscredential;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessCredentialPublicContractTest {

    private static final List<Class<?>> CONTRACTS = List.of(
            AccessCredentialDetails.class,
            AccessCredentialDocument.class,
            AccessCredentialHistoryDetails.class,
            AccessCredentialQrPayload.class,
            AccessCredentialStatus.class);

    @Test
    void publicContractsDoNotLeakFrameworkOrProviderTypes() {
        for (Class<?> contract : CONTRACTS) {
            assertThat(contract.getPackageName())
                    .isEqualTo("io.github.guillermodubon.coachgym.accesscredential");
            Arrays.stream(contract.getDeclaredFields())
                    .forEach(field -> assertThat(field.getType().getName())
                            .doesNotStartWith("org.springframework.")
                            .doesNotStartWith("jakarta.persistence.")
                            .doesNotStartWith("com.google.zxing.")
                            .doesNotStartWith("com.stripe."));
        }
    }

    @Test
    void metadataContractsContainNoRawTokenOrDigestComponent() {
        for (Class<?> contract : List.of(
                AccessCredentialDetails.class,
                AccessCredentialHistoryDetails.class)) {
            for (RecordComponent component : contract.getRecordComponents()) {
                assertThat(component.getName())
                        .doesNotMatch("(?i).*(raw|token|digest|hmac|secret|storagepath).*");
            }
        }
    }
}
