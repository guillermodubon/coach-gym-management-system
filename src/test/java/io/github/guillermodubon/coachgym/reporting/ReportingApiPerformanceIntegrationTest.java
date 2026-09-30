package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.mock.web.MockHttpSession;

/** Bounded local latency sample; measurements are not a production SLA. */
class ReportingApiPerformanceIntegrationTest extends AbstractDashboardApiIntegrationTest {

    private static final ZoneId BUSINESS_TIMEZONE = ZoneId.of("America/El_Salvador");
    private static final int WARMUP_REQUESTS = 3;
    private static final int MEASURED_REQUESTS = 20;
    private static final int SYNTHETIC_AUDIT_ROWS = 1_000;
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
    void publishesLocalP95ForDashboardTrendsAuditQueryAndCsvResponse(TestReporter reporter)
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
        reporter.publishEntry(
                "reportingPerformanceDataset",
                "PostgreSQL 17 Testcontainer; 1,000 synthetic audit rows; "
                        + "20 measured requests per route after 3 warmups; MockMvc, local run.");

        MockHttpSession session = loginAsAdmin();
        LocalDate today = LocalDate.now(BUSINESS_TIMEZONE);
        LocalDate from = today.minusDays(7);
        LocalDate until = today.plusDays(1);
        long summaryP95 = measureP95(reporter, "dashboardSummaryP95Ms", () ->
                mockMvc.perform(get("/api/v1/reporting/summary")
                                .session(session)
                                .param("fromInclusive", from.toString())
                                .param("toExclusive", until.toString()))
                        .andExpect(status().isOk()));
        long financialTrendP95 = measureP95(reporter, "financialTrendP95Ms", () ->
                mockMvc.perform(get("/api/v1/reporting/financial-trend")
                                .session(session)
                                .param("scope", "ORGANIZATION")
                                .param("fromInclusive", from.toString())
                                .param("toExclusive", until.toString())
                                .param("granularity", "DAILY"))
                        .andExpect(status().isOk()));
        long accessSummaryP95 = measureP95(reporter, "accessSummaryP95Ms", () ->
                mockMvc.perform(get("/api/v1/reporting/summary")
                                .session(session)
                                .param("fromInclusive", from.toString())
                                .param("toExclusive", until.toString()))
                        .andExpect(status().isOk()));
        long auditQueryP95 = measureP95(reporter, "auditQueryP95Ms", () ->
                mockMvc.perform(get("/api/v1/audit-entries")
                                .session(session)
                                .param("page", "0")
                                .param("size", "25"))
                        .andExpect(status().isOk()));
        Instant exportUntil = Instant.now();
        Instant exportFrom = exportUntil.minusSeconds(7_200);
        long auditCsvResponseP95 = measureP95(reporter, "auditCsvMockMvcResponseP95Ms", () ->
                mockMvc.perform(get("/api/v1/audit-entries/export.csv")
                                .session(session)
                                .param("occurredFrom", exportFrom.toString())
                                .param("occurredUntil", exportUntil.toString()))
                        .andExpect(status().isOk()));

        assertThat(summaryP95).isLessThan(5_000);
        assertThat(financialTrendP95).isLessThan(5_000);
        assertThat(accessSummaryP95).isLessThan(5_000);
        assertThat(auditQueryP95).isLessThan(5_000);
        assertThat(auditCsvResponseP95).isLessThan(5_000);
    }

    private static long measureP95(
            TestReporter reporter,
            String metricName,
            MeasuredRequest request) throws Exception {
        for (int index = 0; index < WARMUP_REQUESTS; index++) {
            request.run();
        }
        long[] samples = new long[MEASURED_REQUESTS];
        for (int index = 0; index < samples.length; index++) {
            long startedAt = System.nanoTime();
            request.run();
            samples[index] = (System.nanoTime() - startedAt) / 1_000_000;
        }
        Arrays.sort(samples);
        long p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1];
        reporter.publishEntry(metricName, p95 + " ms");
        return p95;
    }

    @FunctionalInterface
    private interface MeasuredRequest {
        void run() throws Exception;
    }
}
