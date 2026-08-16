package io.github.guillermodubon.coachgym.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class PromotionSecurityApiIntegrationTest
        extends AbstractPromotionApiIntegrationTest {

    @Test
    void receptionistCanGetPromotionButCannotCreateOne()
            throws Exception {

        MockHttpSession adminSession = loginAsAdmin();

        MvcResult created = createPercentagePromotion(
                adminSession,
                "Receptionist Query Promotion");

        UUID promotionId = promotionId(created);

        MockHttpSession receptionistSession = loginAsReceptionist();

        mockMvc.perform(
                        get("/api/v1/promotions/{id}", promotionId)
                                .session(receptionistSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(promotionId.toString()))
                .andExpect(jsonPath("$.name").value("Receptionist Query Promotion"));

        mockMvc.perform(
                        post("/api/v1/promotions")
                                .with(csrf())
                                .session(receptionistSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPercentagePromotionBody("Forbidden Promotion")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.promotions
                        where name = ?
                        """,
                        Integer.class,
                        "Forbidden Promotion"))
                .isZero();
    }

    @Test
    void unauthenticatedUserCannotGetPromotion()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/promotions/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void unauthenticatedUserCannotListPromotions()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/promotions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void authenticatedAdminCannotCreatePromotionWithoutCsrf()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        String promotionName = "Missing CSRF " + UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/promotions")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPercentagePromotionBody(promotionName)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.promotions
                        where name = ?
                        """,
                        Integer.class,
                        promotionName))
                .isZero();
    }

    @Test
    void rejectsStructurallyInvalidPromotionRequest()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/promotions")
                                .with(csrf())
                                .session(loginAsAdmin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "",
                                          "discountType": "PERCENTAGE",
                                          "discountValue": 0.00,
                                          "currency": null,
                                          "validFrom": null,
                                          "validUntil": null
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.discountValue").exists())
                .andExpect(jsonPath("$.fieldErrors.validFrom").exists())
                .andExpect(jsonPath("$.fieldErrors.validUntil").exists());
    }

    @Test
    void rejectsInvalidPercentagePromotionDefinition()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/promotions")
                                .with(csrf())
                                .session(loginAsAdmin())
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "Invalid Percentage",
                                          "discountType": "PERCENTAGE",
                                          "discountValue": 101.00,
                                          "currency": null,
                                          "validFrom": "2026-09-01",
                                          "validUntil": "2026-09-30"
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Percentage discount must not exceed 100."));
    }
}
