package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class PaymentCorrectionTransactionContractTest {

    @Test
    void mutationsUseReadWriteTransactions() {
        assertTransaction("voidPayment", false);
        assertTransaction("refundPayment", false);
    }

    @Test
    void queriesUseReadOnlyTransactions() {
        assertTransaction("findCorrection", true);
        assertTransaction("findStatusHistory", true);
    }

    private static void assertTransaction(
            String methodName,
            boolean readOnly) {
        Method method = Arrays.stream(
                        PaymentCorrectionApplicationService.class
                                .getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isEqualTo(readOnly);
    }
}
