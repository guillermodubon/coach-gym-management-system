package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ConfirmedPaymentForAccessQueryContractTest {

    @Test
    void queryIsAFunctionalPublicBoundaryWithOnlyTechnologyNeutralTypes()
            throws Exception {
        assertThat(ConfirmedPaymentForAccessQuery.class.isInterface()).isTrue();
        assertThat(Modifier.isPublic(
                ConfirmedPaymentForAccessQuery.class.getModifiers())).isTrue();
        assertThat(ConfirmedPaymentForAccessQuery.class.isAnnotationPresent(
                FunctionalInterface.class)).isTrue();

        Method method = ConfirmedPaymentForAccessQuery.class
                .getDeclaredMethod("hasConfirmedPaymentForPeriod",
                        java.util.UUID.class,
                        java.util.UUID.class,
                        java.util.UUID.class);
        assertThat(method.getReturnType()).isEqualTo(boolean.class);
        assertThat(Arrays.stream(method.getParameterTypes()))
                .allMatch(type -> type == java.util.UUID.class);
    }

    @Test
    void querySourceDoesNotLeakProviderPersistenceOrAttemptTypes() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/payment/"
                        + "ConfirmedPaymentForAccessQuery.java"))
                .toLowerCase(Locale.ROOT);

        assertThat(source)
                .doesNotContain("org.springframework", "jakarta.persistence",
                        "jdbc", "stripe", "paymentattempt", "cardnumber",
                        "cvc", "receipt");
    }
}
