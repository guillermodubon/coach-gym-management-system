package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class PaymentReceiptApplicationServiceContractTest {

    @Test
    void generationKeepsStorageIouOutsideTheDatabaseTransactionAndUsesCurrentStaffRoles() {
        Method method = method("generate");
        Transactional transaction = method.getAnnotation(Transactional.class);
        PreAuthorize security = method.getAnnotation(PreAuthorize.class);

        assertThat(transaction).isNull();
        assertThat(security).isNotNull();
        assertThat(security.value()).isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }

    @Test
    void readsUseReadOnlyTransactionsAndCurrentStaffRoles() {
        for (String name : new String[] {"findById", "findByPaymentId", "downloadByPaymentId"}) {
            Method method = method(name);
            Transactional transaction = method.getAnnotation(Transactional.class);
            PreAuthorize security = method.getAnnotation(PreAuthorize.class);

            assertThat(transaction).isNotNull();
            assertThat(transaction.readOnly()).isTrue();
            assertThat(security).isNotNull();
            assertThat(security.value()).isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        }
    }

    @Test
    void noMethodSecurityExpressionMentionsRemovedRoles() {
        String expressions = Arrays.stream(PaymentReceiptApplicationService.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .map(PreAuthorize::value)
                .reduce("", (left, right) -> left + " " + right);

        assertThat(expressions).doesNotContain("MAINTENANCE");
    }

    private static Method method(String name) {
        return Arrays.stream(PaymentReceiptApplicationService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
