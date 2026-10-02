package io.github.guillermodubon.coachgym.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesBoundedCorrelationIdThroughMdcAndResponseThenRestoresPriorContext()
            throws Exception {
        String correlationId = "ui-contract-001";
        MockHttpServletRequest request = request();
        request.addHeader(CorrelationIdFilter.HEADER, correlationId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(CorrelationIdFilter.MDC_KEY, "outer-context");

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(correlationId));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(correlationId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("outer-context");
    }

    @Test
    void replacesInvalidOrOversizedCallerValueWithServerGeneratedIdentifier() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(CorrelationIdFilter.HEADER, "private.staff@example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY))
                        .matches("[0-9a-fA-F-]{36}"));

        String propagated = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(propagated).matches("[0-9a-fA-F-]{36}")
                .doesNotContain("private", "example.test");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/actuator/health/liveness");
        return request;
    }
}
