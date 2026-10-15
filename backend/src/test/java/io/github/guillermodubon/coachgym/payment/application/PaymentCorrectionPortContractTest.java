package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentCorrectionPortContractTest {

    @Test
    void correctionPortsArePublicInterfaces() {
        assertThat(List.of(
                PaymentCorrectionStore.class,
                PaymentCorrectionQuery.class,
                PaymentStatusHistoryQuery.class))
                .allSatisfy(type -> {
                    assertThat(type.isInterface()).isTrue();
                    assertThat(Modifier.isPublic(type.getModifiers())).isTrue();
                });
    }

    @Test
    void writePortExposesExactlyVoidAndFullRefundOperations() {
        assertThat(PaymentCorrectionStore.class.getDeclaredMethods())
                .extracting(Method::getName)
                .containsOnly("voidPayment", "refundPayment")
                .hasSize(4);
    }

    @Test
    void portsDoNotExposeFrameworkProviderOrDocumentTypes() {
        for (Class<?> type : List.of(
                PaymentCorrectionStore.class,
                PaymentCorrectionQuery.class,
                PaymentStatusHistoryQuery.class)) {
            for (Method method : type.getDeclaredMethods()) {
                assertAllowed(method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertAllowed(parameter);
                }
            }
        }
    }

    private static void assertAllowed(Class<?> type) {
        if (type.isPrimitive()) {
            return;
        }
        String name = type.getName();
        assertThat(name)
                .doesNotContain("jakarta.persistence")
                .doesNotContain("org.springframework.data")
                .doesNotContain("org.springframework.web")
                .doesNotContain("org.springframework.security")
                .doesNotContain("com.stripe")
                .doesNotContain("pdf")
                .doesNotContain("mail");
    }
}
