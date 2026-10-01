package io.github.guillermodubon.coachgym.reporting.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Captures bounded reporting route latency and result without query or resource identifiers. */
@Component
final class ReportingRequestMetrics implements HandlerInterceptor, WebMvcConfigurer {

    static final String REQUEST_METRIC = "coachgym.reporting.requests";
    static final String DURATION_METRIC = "coachgym.reporting.request.duration";
    private static final String STARTED_ATTRIBUTE = ReportingRequestMetrics.class.getName() + ".started";
    private static final String ROUTE = "reporting";

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    ReportingRequestMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = Objects.requireNonNull(meterRegistryProvider);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/reporting/**");
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        request.setAttribute(STARTED_ATTRIBUTE, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        Object startedAttribute = request.getAttribute(STARTED_ATTRIBUTE);
        if (!(startedAttribute instanceof Long startedAt)) {
            return;
        }
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }
        String outcome = outcome(response.getStatus(), exception);
        meterRegistry.counter(REQUEST_METRIC, "route", ROUTE, "outcome", outcome).increment();
        long elapsed = Math.max(0L, System.nanoTime() - startedAt);
        Timer.builder(DURATION_METRIC)
                .tags("route", ROUTE, "outcome", outcome)
                .register(meterRegistry)
                .record(elapsed, TimeUnit.NANOSECONDS);
    }

    private static String outcome(int status, Exception exception) {
        if (exception != null || status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            return "SERVER_ERROR";
        }
        if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            return "CLIENT_ERROR";
        }
        return "SUCCESS";
    }
}
