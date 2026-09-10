package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionApplicationService;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class PaymentCorrectionFinalSecurityContractTest {

    @Test
    void mutationsRemainAdministratorOnly() {
        assertAuthorization("voidPayment", "hasRole('ADMIN')");
        assertAuthorization("refundPayment", "hasRole('ADMIN')");
    }

    @Test
    void readsRemainAvailableToCurrentStaffRoles() {
        assertAuthorization(
                "findCorrection",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertAuthorization(
                "findStatusHistory",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }

    @Test
    void transactionBoundariesRemainExplicit() {
        assertTransaction("voidPayment", false);
        assertTransaction("refundPayment", false);
        assertTransaction("findCorrection", true);
        assertTransaction("findStatusHistory", true);
    }

    private static void assertAuthorization(String methodName, String expected) {
        Method method = method(methodName);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }

    private static void assertTransaction(String methodName, boolean readOnly) {
        Transactional annotation = method(methodName)
                .getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isEqualTo(readOnly);
    }

    private static Method method(String name) {
        return Arrays.stream(
                        PaymentCorrectionApplicationService.class
                                .getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
