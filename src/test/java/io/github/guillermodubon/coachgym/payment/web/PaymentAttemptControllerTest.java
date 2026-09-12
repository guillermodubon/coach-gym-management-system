package io.github.guillermodubon.coachgym.payment.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptCheckoutDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PaymentAttemptController.class)
@Import(PaymentAttemptControllerTest.TestSecurityConfiguration.class)
class PaymentAttemptControllerTest {

    private static final UUID ATTEMPT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentAttemptApplicationService paymentAttemptService;

    @Test
    void createsCheckoutWithServerOwnedFinancialSnapshot() throws Exception {
        when(paymentAttemptService.createCheckout(any(), any()))
                .thenReturn(new PaymentAttemptCheckoutDetails(
                        details(PaymentAttemptStatus.PROCESSING),
                        URI.create("https://checkout.test/session"),
                        NOW.plusSeconds(900)));

        mockMvc.perform(post("/api/v1/payment-attempts/stripe-checkout")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "%s",
                                  "membershipId": "%s",
                                  "membershipPeriodId": "%s"
                                }
                                """.formatted(CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(
                        "/api/v1/payment-attempts/" + ATTEMPT_ID)))
                .andExpect(jsonPath("$.id").value(ATTEMPT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.sandbox").value(true))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.test/session"))
                .andExpect(jsonPath("$.expectedAmount").value(25.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void createRequiresCsrfAndAuthentication() throws Exception {
        String body = """
                {
                  "clientId": "%s",
                  "membershipId": "%s",
                  "membershipPeriodId": "%s"
                }
                """.formatted(CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID);

        mockMvc.perform(post("/api/v1/payment-attempts/stripe-checkout")
                        .with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payment-attempts/stripe-checkout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentAttemptService);
    }

    @Test
    void readsAndCancelsAttemptForReceptionist() throws Exception {
        when(paymentAttemptService.findById(ATTEMPT_ID)).thenReturn(details(PaymentAttemptStatus.PROCESSING));
        when(paymentAttemptService.cancel(any(), any())).thenReturn(details(PaymentAttemptStatus.CANCELLED));

        mockMvc.perform(get("/api/v1/payment-attempts/{id}", ATTEMPT_ID)
                        .with(authenticatedAs("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ATTEMPT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.checkoutReference").doesNotExist());

        mockMvc.perform(post("/api/v1/payment-attempts/{id}/cancel", ATTEMPT_ID)
                        .with(authenticatedAs("RECEPTIONIST"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void rejectsMalformedCheckoutSelectionBeforeApplicationService() throws Exception {
        mockMvc.perform(post("/api/v1/payment-attempts/stripe-checkout")
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(paymentAttemptService);
    }

    @Test
    void cancelRequiresAnExplicitVersion() throws Exception {
        mockMvc.perform(post("/api/v1/payment-attempts/{id}/cancel", ATTEMPT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(paymentAttemptService);
    }

    private static PaymentAttemptDetails details(PaymentAttemptStatus status) {
        boolean cancelled = status == PaymentAttemptStatus.CANCELLED;
        return new PaymentAttemptDetails(
                ATTEMPT_ID,
                CLIENT_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                PaymentProvider.STRIPE,
                status,
                BigDecimal.valueOf(25),
                "USD",
                cancelled ? PaymentAttemptFailureCode.PROVIDER_CANCELLED : null,
                null,
                USER_ID,
                NOW,
                NOW,
                cancelled ? NOW : null,
                status == PaymentAttemptStatus.PROCESSING ? 1 : 2);
    }

    private static RequestPostProcessor authenticatedAs(String role) {
        CoachGymUserPrincipal principal = org.mockito.Mockito.mock(CoachGymUserPrincipal.class);
        when(principal.authenticatedActor()).thenReturn(new AuthenticatedActor(USER_ID, "staff-user"));
        Authentication authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return authentication(authentication);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain paymentAttemptSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(HttpMethod.GET, "/api/v1/payment-attempts/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .requestMatchers("/api/v1/payment-attempts/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .anyRequest().denyAll())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    response.setStatus(401)))
                    .build();
        }
    }
}
