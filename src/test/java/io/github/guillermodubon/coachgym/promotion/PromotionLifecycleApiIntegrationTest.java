package io.github.guillermodubon.coachgym.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class PromotionLifecycleApiIntegrationTest
        extends AbstractPromotionApiIntegrationTest {

    @Test
    void adminUpdatesPromotionAndCreatesAuditEntry()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        MvcResult created =
                createPercentagePromotion(
                        session,
                        uniqueName("Lifecycle Update"));

        UUID promotionId = promotionId(created);

        MvcResult updated =
                mockMvc.perform(
                                put(
                                        "/api/v1/promotions/{id}",
                                        promotionId)
                                        .with(csrf())
                                        .session(session)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "name": " Updated Fixed Promotion ",
                                                  "description": " Five dollars off selected plans. ",
                                                  "discountType": "FIXED_AMOUNT",
                                                  "discountValue": 5.00,
                                                  "currency": "usd",
                                                  "validFrom": "2026-09-01",
                                                  "validUntil": "2026-10-31",
                                                  "version": 0
                                                }
                                                """))
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.id")
                                        .value(
                                                promotionId.toString()))
                        .andExpect(
                                jsonPath("$.name")
                                        .value(
                                                "Updated Fixed Promotion"))
                        .andExpect(
                                jsonPath("$.description")
                                        .value(
                                                "Five dollars off selected plans."))
                        .andExpect(
                                jsonPath("$.discountType")
                                        .value("FIXED_AMOUNT"))
                        .andExpect(
                                jsonPath("$.discountValue")
                                        .value(5.00))
                        .andExpect(
                                jsonPath("$.currency")
                                        .value("USD"))
                        .andExpect(
                                jsonPath("$.validFrom")
                                        .value("2026-09-01"))
                        .andExpect(
                                jsonPath("$.validUntil")
                                        .value("2026-10-31"))
                        .andExpect(
                                jsonPath("$.active")
                                        .value(true))
                        .andExpect(
                                jsonPath("$.version")
                                        .value(1))
                        .andReturn();

        assertThat(promotionId(updated))
                .isEqualTo(promotionId);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select name
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isEqualTo("Updated Fixed Promotion");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select description
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isEqualTo(
                        "Five dollars off selected plans.");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select discount_type
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isEqualTo("FIXED_AMOUNT");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select discount_value
                        from gym.promotions
                        where id = ?
                        """,
                        BigDecimal.class,
                        promotionId))
                .isEqualByComparingTo("5.00");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select btrim(currency)
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isEqualTo("USD");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isEqualTo(1L);

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_UPDATED"))
                .isEqualTo(1);

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select summary
                        from gym.audit_entries
                        where resource_id = ?
                          and resource_type = 'PROMOTION'
                          and action_code = 'PROMOTION_UPDATED'
                        """,
                        String.class,
                        promotionId))
                .isEqualTo("Promotion updated.");
    }

    @Test
    void adminDeactivatesAndReactivatesPromotion()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        String promotionName =
                uniqueName("State Lifecycle");

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                promotionName));

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/deactivate",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(promotionId.toString()))
                .andExpect(
                        jsonPath("$.active")
                                .value(false))
                .andExpect(
                        jsonPath("$.version")
                                .value(1));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select is_active
                        from gym.promotions
                        where id = ?
                        """,
                        Boolean.class,
                        promotionId))
                .isFalse();

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isEqualTo(1L);

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_DEACTIVATED"))
                .isEqualTo(1);

        mockMvc.perform(
                        get("/api/v1/promotions")
                                .param("active", "true")
                                .param("name", promotionName)
                                .param("page", "0")
                                .param("size", "25")
                                .param("sort", "name")
                                .param("direction", "asc")
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(0))
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(0));

        mockMvc.perform(
                        get("/api/v1/promotions")
                                .param("active", "false")
                                .param("name", promotionName)
                                .param("page", "0")
                                .param("size", "25")
                                .param("sort", "name")
                                .param("direction", "asc")
                                .session(session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.items.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.items[0].id")
                                .value(promotionId.toString()))
                .andExpect(
                        jsonPath("$.items[0].active")
                                .value(false))
                .andExpect(
                        jsonPath("$.items[0].version")
                                .value(1));

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/activate",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 1
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(promotionId.toString()))
                .andExpect(
                        jsonPath("$.active")
                                .value(true))
                .andExpect(
                        jsonPath("$.version")
                                .value(2));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select is_active
                        from gym.promotions
                        where id = ?
                        """,
                        Boolean.class,
                        promotionId))
                .isTrue();

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isEqualTo(2L);

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_REACTIVATED"))
                .isEqualTo(1);
    }

    @Test
    void rejectsUpdateWithStaleVersion()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName("Stale Update")));

        updatePromotion(
                session,
                promotionId,
                0,
                "First Valid Update")
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.name")
                                .value("First Valid Update"))
                .andExpect(
                        jsonPath("$.version")
                                .value(1));

        mockMvc.perform(
                        put(
                                "/api/v1/promotions/{id}",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                "Stale Update Attempt",
                                                0)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_VERSION_CONFLICT"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Promotion "
                                                + promotionId
                                                + " was modified by another operation. "
                                                + "Expected version 0 but found 1."));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select name
                        from gym.promotions
                        where id = ?
                        """,
                        String.class,
                        promotionId))
                .isEqualTo("First Valid Update");

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isEqualTo(1L);

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_UPDATED"))
                .isEqualTo(1);
    }

    @Test
    void rejectsRepeatedStateTransitions()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Repeated Transition")));

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/activate",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_STATE_CONFLICT"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Promotion is already active."));

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_REACTIVATED"))
                .isZero();

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/deactivate",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.active")
                                .value(false))
                .andExpect(
                        jsonPath("$.version")
                                .value(1));

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/deactivate",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 1
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_STATE_CONFLICT"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Promotion is already inactive."));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isEqualTo(1L);

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_DEACTIVATED"))
                .isEqualTo(1);
    }

    @Test
    void rejectsStateChangeWithStaleVersion()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Stale State Change")));

        updatePromotion(
                session,
                promotionId,
                0,
                "Version Increment Update")
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.version")
                                .value(1));

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/deactivate",
                                promotionId)
                                .with(csrf())
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "PROMOTION_VERSION_CONFLICT"))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Promotion "
                                                + promotionId
                                                + " was modified by another operation. "
                                                + "Expected version 0 but found 1."));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select is_active
                        from gym.promotions
                        where id = ?
                        """,
                        Boolean.class,
                        promotionId))
                .isTrue();

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isEqualTo(1L);

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_DEACTIVATED"))
                .isZero();
    }

    @Test
    void receptionistCannotUpdateOrChangePromotionState()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                adminSession,
                                uniqueName(
                                        "Restricted Administration")));

        MockHttpSession receptionistSession =
                loginAsReceptionist();

        mockMvc.perform(
                        put(
                                "/api/v1/promotions/{id}",
                                promotionId)
                                .with(csrf())
                                .session(receptionistSession)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                "Forbidden Update",
                                                0)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED"));

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/deactivate",
                                promotionId)
                                .with(csrf())
                                .session(receptionistSession)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED"));

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/activate",
                                promotionId)
                                .with(csrf())
                                .session(receptionistSession)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED"));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select is_active
                        from gym.promotions
                        where id = ?
                        """,
                        Boolean.class,
                        promotionId))
                .isTrue();

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isZero();

        assertThat(
                administrativeAuditCount(
                        promotionId))
                .isZero();
    }

    @Test
    void authenticatedAdminCannotUpdateWithoutCsrf()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Missing Update CSRF")));

        mockMvc.perform(
                        put(
                                "/api/v1/promotions/{id}",
                                promotionId)
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        updateBody(
                                                "Unauthorized CSRF Update",
                                                0)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CSRF_TOKEN_INVALID"));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isZero();

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_UPDATED"))
                .isZero();
    }

    @Test
    void authenticatedAdminCannotChangeStateWithoutCsrf()
            throws Exception {

        MockHttpSession session = loginAsAdmin();

        UUID promotionId =
                promotionId(
                        createPercentagePromotion(
                                session,
                                uniqueName(
                                        "Missing State CSRF")));

        mockMvc.perform(
                        post(
                                "/api/v1/promotions/{id}/deactivate",
                                promotionId)
                                .session(session)
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "version": 0
                                        }
                                        """))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CSRF_TOKEN_INVALID"));

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select is_active
                        from gym.promotions
                        where id = ?
                        """,
                        Boolean.class,
                        promotionId))
                .isTrue();

        assertThat(
                jdbcTemplate.queryForObject(
                        """
                        select version
                        from gym.promotions
                        where id = ?
                        """,
                        Long.class,
                        promotionId))
                .isZero();

        assertThat(
                auditCount(
                        promotionId,
                        "PROMOTION_DEACTIVATED"))
                .isZero();
    }

    private ResultActions updatePromotion(
            MockHttpSession session,
            UUID promotionId,
            long version,
            String name)
            throws Exception {

        return mockMvc.perform(
                put(
                        "/api/v1/promotions/{id}",
                        promotionId)
                        .with(csrf())
                        .session(session)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(
                                updateBody(
                                        name,
                                        version)));
    }

    private static String updateBody(
            String name,
            long version) {

        return """
                {
                  "name": "%s",
                  "description": "Updated percentage promotion.",
                  "discountType": "PERCENTAGE",
                  "discountValue": 15.00,
                  "currency": null,
                  "validFrom": "2026-09-01",
                  "validUntil": "2026-10-31",
                  "version": %d
                }
                """
                .formatted(name, version);
    }

    private int auditCount(
            UUID promotionId,
            String actionCode) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.audit_entries
                        where resource_id = ?
                          and resource_type = 'PROMOTION'
                          and action_code = ?
                        """,
                        Integer.class,
                        promotionId,
                        actionCode);

        return count == null ? 0 : count;
    }

    private int administrativeAuditCount(
            UUID promotionId) {

        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from gym.audit_entries
                        where resource_id = ?
                          and resource_type = 'PROMOTION'
                          and action_code in (
                              'PROMOTION_UPDATED',
                              'PROMOTION_DEACTIVATED',
                              'PROMOTION_REACTIVATED'
                          )
                        """,
                        Integer.class,
                        promotionId);

        return count == null ? 0 : count;
    }

    private static String uniqueName(
            String prefix) {

        return prefix + " " + UUID.randomUUID();
    }
}