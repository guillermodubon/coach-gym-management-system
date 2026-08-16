package io.github.guillermodubon.coachgym.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class PromotionCreationApiIntegrationTest
        extends AbstractPromotionApiIntegrationTest {

    @Test
    void adminCreatesPercentagePromotionWithGeneratedCodeAndAuditEntry()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/promotions")
                                        .with(csrf())
                                        .session(session)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": " September Discount ",
                                                  "description": " Twenty percent off selected plans. ",
                                                  "discountType": "PERCENTAGE",
                                                  "discountValue": 20.00,
                                                  "currency": null,
                                                  "validFrom": "2026-09-01",
                                                  "validUntil": "2026-09-30"
                                                }
                                                """))
                        .andExpect(status().isCreated())
                        .andExpect(
                                header().string(
                                        "Location",
                                        matchesPattern(".*/api/v1/promotions/[0-9a-f-]+")))
                        .andExpect(jsonPath("$.id").isNotEmpty())
                        .andExpect(jsonPath("$.promotionCode").value(matchesPattern("PROMO-[0-9]{6}")))
                        .andExpect(jsonPath("$.name").value("September Discount"))
                        .andExpect(jsonPath("$.description").value("Twenty percent off selected plans."))
                        .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                        .andExpect(jsonPath("$.discountValue").value(20.00))
                        .andExpect(jsonPath("$.currency").value(nullValue()))
                        .andExpect(jsonPath("$.validFrom").value("2026-09-01"))
                        .andExpect(jsonPath("$.validUntil").value("2026-09-30"))
                        .andExpect(jsonPath("$.active").value(true))
                        .andExpect(jsonPath("$.createdAt").isNotEmpty())
                        .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                        .andExpect(jsonPath("$.version").value(0))
                        .andReturn();

        UUID promotionId = promotionId(result);

        // Verificaciones en Base de Datos usando jdbcTemplate
        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.promotions
                        where id = ?
                        """,
                        Integer.class,
                        promotionId))
                .isEqualTo(1);

        String promotionCode =
                jdbcTemplate.queryForObject(
                        """
                        select promotion_code
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId);

        assertThat(promotionCode)
                .matches("PROMO-[0-9]{6}");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select name
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isEqualTo("September Discount");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select description
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isEqualTo("Twenty percent off selected plans.");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select discount_type
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isEqualTo("PERCENTAGE");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select discount_value
                        from gym.promotions
                        where id = ?
                        """,
                        BigDecimal.class,
                        promotionId))
                .isEqualByComparingTo("20.00");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select currency
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isNull();
    }
}
