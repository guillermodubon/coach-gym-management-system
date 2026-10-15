package io.github.guillermodubon.coachgym.promotion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PromotionCatalogQueryApiIntegrationTest
        extends AbstractPromotionApiIntegrationTest {

    @Test
    void listsAndFiltersPromotions() throws Exception {
        MockHttpSession session = loginAsAdmin();

        createPercentagePromotion(
                session,
                "September Active Promotion");

        mockMvc.perform(
                        get("/api/v1/promotions")
                                .param("active", "true")
                                .param(
                                        "name",
                                        "September Active")
                                .param(
                                        "discountType",
                                        "PERCENTAGE")
                                .param(
                                        "validOn",
                                        "2026-09-15")
                                .param("page", "0")
                                .param("size", "25")
                                .param("sort", "name")
                                .param("direction", "asc")
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items").isArray())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.items[0].name")
                                .value(
                                        "September Active Promotion"))
                .andExpect(
                        jsonPath(
                                "$.items[0].discountType")
                                .value("PERCENTAGE"))
                .andExpect(
                        jsonPath("$.page")
                                .value(0))
                .andExpect(
                        jsonPath("$.size")
                                .value(25))
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(1))
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(1));
    }

    @Test
    void rejectsInvalidPromotionCatalogQueries()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(
                        get("/api/v1/promotions")
                                .param("page", "-1")
                                .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_VALIDATION_FAILED"));

        mockMvc.perform(
                        get("/api/v1/promotions")
                                .param("size", "101")
                                .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_VALIDATION_FAILED"));

        mockMvc.perform(
                        get("/api/v1/promotions")
                                .param(
                                        "sort",
                                        "discount_value")
                                .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_VALIDATION_FAILED"));

        mockMvc.perform(
                        get("/api/v1/promotions")
                                .param("sort", "name")
                                .param(
                                        "direction",
                                        "sideways")
                                .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_VALIDATION_FAILED"));
    }
}