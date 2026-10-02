package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

/** Bounded local latency sample; measurements are not a production SLA. */
class ReportingApiPerformanceIntegrationTest extends AbstractDashboardApiIntegrationTest {

    private static final ZoneId BUSINESS_TIMEZONE = ZoneId.of("America/El_Salvador");
    private static final int WARMUP_REQUESTS = 3;
    private static final int MEASURED_REQUESTS = 20;
    private static final int SYNTHETIC_AUDIT_ROWS = 1_000;
    @Autowired
    private DataSource dataSource;

    private Instant performanceStartedAt;

    @BeforeEach
    void startPerformanceFixtureWindow() {
        performanceStartedAt = Instant.now();
    }

    @AfterEach
    void removePerformanceFixtureRows() {
        jdbcTemplate.update("""
                delete from gym.audit_entries
                where actor_identifier_snapshot = 'reporting-performance-fixture'
                """);
        if (adminId != null && performanceStartedAt != null) {
            jdbcTemplate.update("""
                    delete from gym.audit_entries
                    where actor_user_id = ?
                      and action_code = 'AUDIT_ENTRIES_EXPORTED'
                      and occurred_at >= ?
                    """, adminId, OffsetDateTime.ofInstant(performanceStartedAt, ZoneOffset.UTC));
        }
    }

    @Test
    void publishesLocalP50AndP95ForDashboardTrendsAuditQueryAndCsvResponse(
            TestReporter reporter)
            throws Exception {
        jdbcTemplate.update("""
                insert into gym.audit_entries
                    (id, actor_user_id, actor_identifier_snapshot, action_code,
                     resource_type, resource_id, resource_code_snapshot, summary,
                     metadata, correlation_id, occurred_at)
                select gen_random_uuid(), ?, 'reporting-performance-fixture',
                       'CLIENT_REGISTERED', 'CLIENT',
                       gen_random_uuid(), 'PERF-' || item,
                       'Non-sensitive reporting performance fixture',
                       jsonb_build_object('branchId', ?), null,
                       current_timestamp - make_interval(secs => item)
                from generate_series(1, ?) item
                """, adminId,
                "7b0bf7d5-5184-43d2-8f9a-200000000002", SYNTHETIC_AUDIT_ROWS);
        jdbcTemplate.execute("analyze gym.audit_entries");
        HikariDataSource hikari = (HikariDataSource) dataSource;
        reporter.publishEntry(
                "reportingPerformanceDataset",
                "PostgreSQL " + jdbcTemplate.queryForObject(
                        "select current_setting('server_version')", String.class)
                        + " Testcontainer; 1,000 synthetic audit rows; "
                        + MEASURED_REQUESTS + " sequential MockMvc requests per route after "
                        + WARMUP_REQUESTS + " warmups; request concurrency=1; Hikari maximum="
                        + hikari.getMaximumPoolSize() + "; OS=" + System.getProperty("os.name")
                        + "; arch=" + System.getProperty("os.arch")
                        + "; processors=" + Runtime.getRuntime().availableProcessors()
                        + "; maxHeapBytes=" + Runtime.getRuntime().maxMemory()
                        + "; Java=" + System.getProperty("java.version")
                        + "; separate pool-capacity regression uses 12 workers and verifies "
                        + "active connections never exceed the pool size."
                        + "; CSV uses 256-row JDBC fetches with a 10,000-row default cap; "
                        + "heap was not directly instrumented."
                        + "; local Testcontainers measurement, not a production SLA.");

        MockHttpSession session = loginAsAdmin();
        LocalDate today = LocalDate.now(BUSINESS_TIMEZONE);
        LocalDate from = today.minusDays(7);
        LocalDate until = today.plusDays(1);
        LatencyPercentiles summary = measureLatency(reporter, "dashboardSummary", () ->
                mockMvc.perform(get("/api/v1/reporting/summary")
                                .session(session)
                                .param("fromInclusive", from.toString())
                                .param("toExclusive", until.toString()))
                        .andExpect(status().isOk()));
        LatencyPercentiles financialTrend = measureLatency(reporter, "financialTrend", () ->
                mockMvc.perform(get("/api/v1/reporting/financial-trend")
                                .session(session)
                                .param("scope", "ORGANIZATION")
                                .param("fromInclusive", from.toString())
                                .param("toExclusive", until.toString())
                                .param("granularity", "DAILY"))
                        .andExpect(status().isOk()));
        LatencyPercentiles accessTrend = measureLatency(reporter, "accessTrend", () ->
                mockMvc.perform(get("/api/v1/reporting/access-trend")
                                .session(session)
                                .param("scope", "ORGANIZATION")
                                .param("fromInclusive", from.toString())
                                .param("toExclusive", until.toString())
                                .param("granularity", "DAILY"))
                        .andExpect(status().isOk()));
        LatencyPercentiles auditQuery = measureLatency(reporter, "auditQuery", () ->
                mockMvc.perform(get("/api/v1/audit-entries")
                                .session(session)
                                .param("page", "0")
                                .param("size", "25"))
                        .andExpect(status().isOk()));
        Instant exportUntil = Instant.now();
        Instant exportFrom = exportUntil.minusSeconds(7_200);
        LatencyPercentiles auditCsvResponse = measureLatency(
                reporter, "auditCsvMockMvcResponse", () ->
                mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                                .session(session)
                                .param("occurredFrom", exportFrom.toString())
                                .param("occurredUntil", exportUntil.toString()))
                        .andExpect(status().isOk()));

        assertThat(summary.p95Millis()).isLessThan(5_000);
        assertThat(financialTrend.p95Millis()).isLessThan(5_000);
        assertThat(accessTrend.p95Millis()).isLessThan(5_000);
        assertThat(auditQuery.p95Millis()).isLessThan(5_000);
        assertThat(auditCsvResponse.p95Millis()).isLessThan(5_000);
    }

    private static LatencyPercentiles measureLatency(
            TestReporter reporter,
            String metricName,
            MeasuredRequest request) throws Exception {
        for (int index = 0; index < WARMUP_REQUESTS; index++) {
            request.run();
        }
        long[] samplesNanos = new long[MEASURED_REQUESTS];
        for (int index = 0; index < samplesNanos.length; index++) {
            long startedAt = System.nanoTime();
            request.run();
            samplesNanos[index] = System.nanoTime() - startedAt;
        }
        Arrays.sort(samplesNanos);
        double p50 = percentileMillis(samplesNanos, 0.50);
        double p95 = percentileMillis(samplesNanos, 0.95);
        reporter.publishEntry(
                metricName + "P50Ms", String.format(Locale.ROOT, "%.3f ms", p50));
        reporter.publishEntry(
                metricName + "P95Ms", String.format(Locale.ROOT, "%.3f ms", p95));
        return new LatencyPercentiles(p50, p95);
    }

    private static double percentileMillis(long[] sortedNanos, double percentile) {
        int index = (int) Math.ceil(sortedNanos.length * percentile) - 1;
        return sortedNanos[index] / 1_000_000.0;
    }

    private record LatencyPercentiles(double p50Millis, double p95Millis) {}

    @FunctionalInterface
    private interface MeasuredRequest {
        void run() throws Exception;
    }
}
