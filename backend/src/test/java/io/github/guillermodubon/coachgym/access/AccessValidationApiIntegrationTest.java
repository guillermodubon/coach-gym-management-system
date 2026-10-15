package io.github.guillermodubon.coachgym.access;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class AccessValidationApiIntegrationTest extends AbstractAccessApiIntegrationTest {

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsStructurallyInvalidBodies(String body, String code) throws Exception {
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(post("/api/v1/access/check-in").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
        org.assertj.core.api.Assertions.assertThat(countAccessRows()).isZero();
        org.assertj.core.api.Assertions.assertThat(countAccessAudits()).isZero();
    }

    @Test
    void rejectsMissingBody() throws Exception {
        mockMvc.perform(post("/api/v1/access/check-in").with(csrf())
                        .session(loginAsAdmin()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        org.assertj.core.api.Assertions.assertThat(countAccessRows()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void rejectsInvalidQueries(String name, String value) throws Exception {
        mockMvc.perform(get("/api/v1/access/records")
                        .session(loginAsAdmin()).param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCESS_VALIDATION_FAILED"));
        org.assertj.core.api.Assertions.assertThat(countAccessRows()).isZero();
        org.assertj.core.api.Assertions.assertThat(countAccessAudits()).isZero();
    }

    @Test
    void rejectsInvertedRange() throws Exception {
        mockMvc.perform(get("/api/v1/access/records").session(loginAsAdmin())
                        .param("checkedInFrom", "2026-09-16T00:00:00Z")
                        .param("checkedInUntil", "2026-09-15T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCESS_VALIDATION_FAILED"));
    }

    private static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("{}", "VALIDATION_FAILED"),
                Arguments.of("{\"identifier\":null}", "VALIDATION_FAILED"),
                Arguments.of("{\"identifier\":\"\"}", "VALIDATION_FAILED"),
                Arguments.of("{\"identifier\":\"   \"}", "VALIDATION_FAILED"),
                Arguments.of("{\"identifier\":\"" + "A".repeat(65) + "\"}", "VALIDATION_FAILED"),
                Arguments.of("{\"identifier\":", "MALFORMED_REQUEST"));
    }

    private static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of("page", "-1"),
                Arguments.of("size", "0"),
                Arguments.of("size", "101"),
                Arguments.of("result", "MAYBE"),
                Arguments.of("reasonCode", "WRONG_CODE"),
                Arguments.of("sort", "CREATED_AT"),
                Arguments.of("direction", "SIDEWAYS"));
    }
}
