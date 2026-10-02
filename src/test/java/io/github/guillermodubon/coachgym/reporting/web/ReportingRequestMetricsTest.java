package io.github.guillermodubon.coachgym.reporting.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ReportingRequestMetricsTest {

    @Test
    void timesReportingWithFixedRouteAndBoundedOutcomeTagsOnly() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReportingRequestMetrics metrics = new ReportingRequestMetrics(provider(registry));
        String sensitivePath = "/api/v1/reporting/branches/" + UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", sensitivePath);
        request.setQueryString("email=private@example.test&token=private-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(metrics.preHandle(request, response, new Object())).isTrue();
        response.setStatus(200);
        metrics.afterCompletion(request, response, new Object(), null);

        assertThat(registry.get(ReportingRequestMetrics.REQUEST_METRIC)
                .tag("route", "reporting")
                .tag("outcome", "SUCCESS").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ReportingRequestMetrics.DURATION_METRIC)
                .tag("route", "reporting")
                .tag("outcome", "SUCCESS").timer().count()).isEqualTo(1L);
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags()).extracting(Tag::getKey)
                    .containsExactlyInAnyOrder("route", "outcome");
            assertThat(meter.getId().getTag("route")).isEqualTo("reporting");
            assertThat(meter.getId().getTags()).noneMatch(tag -> tag.getValue()
                    .contains("private@example.test")
                    || tag.getValue().contains("private-token")
                    || tag.getValue().contains(sensitivePath));
        });
    }

    @Test
    void mapsClientAndServerFailuresToFiniteOutcomes() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReportingRequestMetrics metrics = new ReportingRequestMetrics(provider(registry));

        recordStatus(metrics, 403);
        recordStatus(metrics, 503);

        assertThat(registry.get(ReportingRequestMetrics.REQUEST_METRIC)
                .tag("outcome", "CLIENT_ERROR").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ReportingRequestMetrics.REQUEST_METRIC)
                .tag("outcome", "SERVER_ERROR").counter().count()).isEqualTo(1.0);
    }

    @Test
    void isSafeWhenTheMvcSliceHasNoMeterRegistry() throws Exception {
        ReportingRequestMetrics metrics = new ReportingRequestMetrics(provider(null));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/reporting/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(metrics.preHandle(request, response, new Object())).isTrue();
        metrics.afterCompletion(request, response, new Object(), null);
    }

    private static void recordStatus(ReportingRequestMetrics metrics, int status)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reporting/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        metrics.preHandle(request, response, new Object());
        response.setStatus(status);
        metrics.afterCompletion(request, response, new Object(), null);
    }

    private static ObjectProvider<MeterRegistry> provider(MeterRegistry registry) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (registry != null) {
            beanFactory.registerSingleton("meterRegistry", registry);
        }
        return beanFactory.getBeanProvider(MeterRegistry.class);
    }
}
