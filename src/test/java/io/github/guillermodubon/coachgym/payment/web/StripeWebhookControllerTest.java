package io.github.guillermodubon.coachgym.payment.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventProcessingResult;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookLimits;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookVerifier;
import io.github.guillermodubon.coachgym.payment.application.VerifiedPaymentProviderEvent;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StripeWebhookController.class)
@Import(StripeWebhookControllerTest.TestSecurityConfiguration.class)
class StripeWebhookControllerTest {

    private static final UUID ATTEMPT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentProviderWebhookVerifier verifier;

    @MockitoBean
    private PaymentProviderEventApplicationService eventService;

    @Test
    void verifiesBeforeProcessingAndAcceptsDuplicateAcknowledgementWithoutSession() throws Exception {
        when(verifier.verify(PaymentProvider.STRIPE, "{}".getBytes(), "v1=test"))
                .thenReturn(event());
        when(eventService.process(any())).thenReturn(PaymentProviderEventProcessingResult.REJECTED);

        mockMvc.perform(post("/api/v1/payment-provider/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "v1=test")
                        .content("{}"))
                .andExpect(status().isNoContent());

        verify(eventService).process(any(VerifiedPaymentProviderEvent.class));
    }

    @Test
    void mapsInvalidSignatureToSafeProblemDetail() throws Exception {
        when(verifier.verify(any(), any(), any()))
                .thenThrow(new PaymentProviderException(PaymentProviderFailureCode.INVALID_SIGNATURE));

        mockMvc.perform(post("/api/v1/payment-provider/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "bad")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"))
                .andExpect(jsonPath("$.detail").value(
                        "The provider signature is missing, malformed, invalid, or stale."));

        verifyNoInteractions(eventService);
    }

    @Test
    void rejectsOversizedBodyBeforeVerifier() throws Exception {
        mockMvc.perform(post("/api/v1/payment-provider/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "v1=test")
                        .content("12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_PAYLOAD_TOO_LARGE"));

        verifyNoInteractions(verifier, eventService);
    }

    @Test
    void rejectsOversizedSignatureHeaderBeforeVerifier() throws Exception {
        mockMvc.perform(post("/api/v1/payment-provider/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "12345678901234567")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_TOO_LARGE"));

        verifyNoInteractions(verifier, eventService);
    }

    private static VerifiedPaymentProviderEvent event() {
        return new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE,
                "evt_test_1",
                PaymentProviderEventType.PAYMENT_FAILED,
                ATTEMPT_ID,
                "checkout_test_1",
                null,
                null,
                null,
                Instant.parse("2026-09-11T12:00:00Z"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        PaymentProviderWebhookLimits paymentProviderWebhookLimits() {
            return new PaymentProviderWebhookLimits(4, 16);
        }

        @Bean
        SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.ignoringRequestMatchers(request ->
                                    "POST".equals(request.getMethod())
                                    && "/api/v1/payment-provider/stripe/webhook"
                                            .equals(request.getRequestURI())))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(HttpMethod.POST, "/api/v1/payment-provider/stripe/webhook")
                            .permitAll()
                            .anyRequest().denyAll())
                    .build();
        }
    }
}
