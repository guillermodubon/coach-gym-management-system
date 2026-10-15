package io.github.guillermodubon.coachgym.payment.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.GeneratePaymentReceiptCommand;
import io.github.guillermodubon.coachgym.payment.application.PaymentNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptContent;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptDataAccessException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptDuplicateException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptRenderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStateConflictException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(PaymentReceiptController.class)
@Import({PaymentReceiptProblemHandler.class, PaymentReceiptControllerTest.TestSecurityConfiguration.class})
class PaymentReceiptControllerTest {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b01");
    private static final UUID RECEIPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b02");
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b03");
    private static final Instant PAID_AT = Instant.parse("2026-09-12T10:00:00Z");
    private static final Instant GENERATED_AT = Instant.parse("2026-09-12T10:01:00Z");
    private static final byte[] PDF = "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentReceiptApplicationService service;

    @Test
    void generatesReceiptWithServerOwnedFieldsAndLocation() throws Exception {
        PaymentReceiptDetails details = details();
        when(service.generate(eq(new GeneratePaymentReceiptCommand(PAYMENT_ID)), any()))
                .thenReturn(details);

        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(
                        "/api/v1/payments/" + PAYMENT_ID + "/receipt")))
                .andExpect(jsonPath("$.id").value(RECEIPT_ID.toString()))
                .andExpect(jsonPath("$.receiptNumber").value("REC-000B02"))
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.downloadUrl", containsString(
                        "/api/v1/payments/" + PAYMENT_ID + "/receipt.pdf")))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.bytes").doesNotExist());

        verify(service).generate(
                eq(new GeneratePaymentReceiptCommand(PAYMENT_ID)),
                eq(new AuthenticatedActor(USER_ID, "staff-user")));
    }

    @Test
    void returnsMetadataToReceptionist() throws Exception {
        when(service.findByPaymentId(eq(PAYMENT_ID), any())).thenReturn(details());

        mockMvc.perform(get("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value("REC-000B02"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.testMode").value(false))
                .andExpect(jsonPath("$.downloadUrl", containsString("/receipt.pdf")))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.document").doesNotExist());
    }

    @Test
    void downloadsValidatedPdfWithPrivateNoStoreHeaders() throws Exception {
        PaymentReceiptDetails details = details();
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(PDF);
        when(service.downloadByPaymentId(eq(PAYMENT_ID), any()))
                .thenReturn(new PaymentReceiptContent(details, document));

        mockMvc.perform(get("/api/v1/payments/{paymentId}/receipt.pdf", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Length", String.valueOf(PDF.length)))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"coach-gym-REC-000B02.pdf\""))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void repeatedGenerationReturnsTheSameCanonicalMetadata() throws Exception {
        when(service.generate(any(), any())).thenReturn(details());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                            .with(authenticatedAs("RECEPTIONIST"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(RECEIPT_ID.toString()))
                    .andExpect(jsonPath("$.receiptNumber").value("REC-000B02"));
        }

        verify(service, org.mockito.Mockito.times(2)).generate(
                eq(new GeneratePaymentReceiptCommand(PAYMENT_ID)), any());
    }

    @Test
    void enforcesAuthenticationAndCsrfForGeneration() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnsupportedRoleAndServerControlledRequestFields() throws Exception {
        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("MAINTENANCE"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":999,\"currency\":\"EUR\",\"receiptNumber\":\"fake\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsReceiptAndPaymentFailuresToStableProblemDetails() throws Exception {
        when(service.findByPaymentId(eq(PAYMENT_ID), any()))
                .thenThrow(new PaymentReceiptNotFoundException(RECEIPT_ID));
        mockMvc.perform(get("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIPT_NOT_FOUND"));

        when(service.downloadByPaymentId(eq(PAYMENT_ID), any()))
                .thenThrow(new PaymentReceiptStorageException("internal path must not leak"));
        mockMvc.perform(get("/api/v1/payments/{paymentId}/receipt.pdf", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIPT_STORAGE_FAILED"))
                .andExpect(jsonPath("$.detail").value(
                        "The payment receipt document is temporarily unavailable."));

        doThrow(new PaymentReceiptStateConflictException(PAYMENT_ID, PaymentStatus.VOIDED))
                .when(service)
                .generate(any(), any());
        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIPT_STATE_CONFLICT"));

        doThrow(new PaymentNotFoundException(PAYMENT_ID))
                .when(service)
                .generate(any(), any());
        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));

        reset(service);
        doThrow(new PaymentReceiptValidationException("internal validation detail"))
                .when(service)
                .generate(any(), any());
        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIPT_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(
                        "The payment receipt request is invalid."));

        reset(service);
        doThrow(new PaymentReceiptRenderException("internal render detail", null))
                .when(service)
                .generate(any(), any());
        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIPT_RENDER_FAILED"));

        reset(service);
        doThrow(new PaymentReceiptDuplicateException(PAYMENT_ID))
                .when(service)
                .generate(any(), any());
        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIPT_ALREADY_EXISTS"));

        reset(service);
        doThrow(new PaymentReceiptDataAccessException("internal data detail", null))
                .when(service)
                .downloadByPaymentId(eq(PAYMENT_ID), any());
        mockMvc.perform(get("/api/v1/payments/{paymentId}/receipt.pdf", PAYMENT_ID)
                        .with(authenticatedAs("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PAYMENT_RECEIPT_DATA_ACCESS_FAILED"));
    }

    private static PaymentReceiptDetails details() {
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(PDF);
        return new PaymentReceiptDetails(
                RECEIPT_ID,
                "REC-000B02",
                PAYMENT_ID,
                "PAY-000B01",
                PaymentStatus.PAID,
                "CLI-000B01",
                "Receipt Client",
                "MEM-000B01",
                "Premium",
                null,
                1,
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 10, 11),
                new BigDecimal("25.00"),
                BigDecimal.ZERO,
                new BigDecimal("25.00"),
                "USD",
                PaymentMethod.CASH,
                PAID_AT,
                GENERATED_AT,
                USER_ID,
                "Staff User",
                false,
                document.contentType(),
                document.sizeBytes(),
                document.checksumSha256(),
                "pdfbox-1",
                0);
    }

    private static RequestPostProcessor authenticatedAs(String role) {
        CoachGymUserPrincipal principal = org.mockito.Mockito.mock(CoachGymUserPrincipal.class);
        when(principal.authenticatedActor())
                .thenReturn(new AuthenticatedActor(USER_ID, "staff-user"));
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
        SecurityFilterChain paymentReceiptSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(HttpMethod.GET, "/api/v1/payments/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .requestMatchers("/api/v1/payments/**")
                            .hasAnyRole("ADMIN", "RECEPTIONIST")
                            .anyRequest().denyAll())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    response.setStatus(401)))
                    .build();
        }
    }
}
