package io.github.guillermodubon.coachgym.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditQueryPublicContractTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID RESOURCE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void policyRecordsTheAdministrativePrivacyDecisions() {
        assertThat(AuditQueryPolicy.ADMIN_ONLY).isTrue();
        assertThat(AuditQueryPolicy.SELF_AUDITING_ENABLED).isFalse();
        assertThat(AuditQueryPolicy.DEFAULT_PAGE).isZero();
        assertThat(AuditQueryPolicy.DEFAULT_SIZE).isEqualTo(25);
        assertThat(AuditQueryPolicy.MAX_SIZE).isEqualTo(100);
        assertThat(AuditQueryPolicy.MAX_DATE_RANGE).isEqualTo(
                java.time.Duration.ofDays(366));
        assertThat(AuditQueryPolicy.DEFAULT_SORT)
                .isEqualTo(AuditSortField.OCCURRED_AT);
        assertThat(AuditQueryPolicy.DEFAULT_DIRECTION)
                .isEqualTo(AuditSortDirection.DESC);
        assertThat(AuditQueryPolicy.allowedResourceTypes())
                .contains("PAYMENT", "ACCESS_RECORD", "EMAIL_DELIVERY");
        assertThat(AuditQueryPolicy.allowedActionCodes())
                .contains("PAYMENT_REFUNDED", "ACCESS_DENIED", "CLIENT_REGISTERED");
    }

    @Test
    void defaultsNormalizeOptionalFiltersAndUseDeterministicOrdering() {
        AuditSearchQuery query = AuditSearchQuery.from(
                ACTOR_ID,
                "  Admin.User  ",
                " plan_created ",
                " membership_plan ",
                RESOURCE_ID,
                "  PLAN-001  ",
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(10),
                0,
                25,
                null,
                null);

        assertThat(query.actorIdentifier()).isEqualTo("admin.user");
        assertThat(query.actionCode()).isEqualTo("PLAN_CREATED");
        assertThat(query.resourceType()).isEqualTo("MEMBERSHIP_PLAN");
        assertThat(query.resourceCode()).isEqualTo("PLAN-001");
        assertThat(query.sortField()).isEqualTo(AuditSortField.OCCURRED_AT);
        assertThat(query.direction()).isEqualTo(AuditSortDirection.DESC);
        assertThat(AuditSearchQuery.defaults()).satisfies(defaults -> {
            assertThat(defaults.page()).isZero();
            assertThat(defaults.size()).isEqualTo(25);
            assertThat(defaults.sort()).isEqualTo(AuditSortField.OCCURRED_AT);
            assertThat(defaults.sortDirection()).isEqualTo(AuditSortDirection.DESC);
        });
    }

    @Test
    void validatesPageSizeDateCodeAndSortAllowLists() {
        assertThatThrownBy(() -> query(-1, 25))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> query(0, 0))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> query(0, 101))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> AuditSearchQuery.from(
                null, null, "UNKNOWN_ACTION", null, null, null, null,
                null, null, 0, 25, null, null))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> AuditSearchQuery.from(
                null, null, null, "UNKNOWN_RESOURCE", null, null, null,
                null, null, 0, 25, null, null))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> AuditSearchQuery.from(
                null, null, null, null, null, null, null, NOW,
                NOW.minusSeconds(1), 0, 25, null, null))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> AuditSearchQuery.from(
                null, null, null, null, null, null, null, NOW,
                NOW.plus(java.time.Duration.ofDays(367)), 0, 25, null, null))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> AuditSearchQuery.from(
                null, null, null, null, null, null, null, null,
                null, 0, 25, "id", null))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> AuditSearchQuery.from(
                null, null, null, null, null, null, null, null,
                null, 0, 25, null, "sideways"))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> AuditSearchQuery.from(
                null, null, null, null, null, "%prefix", null, null,
                null, 0, 25, null, null))
                .isInstanceOf(AuditQueryValidationException.class);
    }

    @Test
    void rejectsResultAndArbitraryMetadataFiltersBecauseSchemaDoesNotPersistThem() {
        assertThatThrownBy(() -> AuditSearchQuery.from(
                null, null, null, null, "SUCCESS", null, null, null,
                null, null, 0, 25, null, null))
                .isInstanceOf(AuditQueryValidationException.class);

        Set<String> componentNames = Set.of(
                java.util.Arrays.stream(AuditSearchQuery.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toArray(String[]::new));
        assertThat(componentNames)
                .doesNotContain("result", "metadata", "metadataKey", "column");
    }

    @Test
    void pageIsImmutableAndItsTotalsAreConsistent() {
        AuditEntrySummary summary = summary();
        AuditEntryPage page = AuditEntryPage.of(List.of(summary), 0, 25, 1);

        assertThat(page.totalPages()).isEqualTo(1);
        assertThatThrownBy(() -> page.items().add(summary))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AuditEntryPage(
                List.of(summary), 0, 25, 1, 0))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> new AuditEntryPage(
                List.of(summary, summary), 0, 1, 2, 2))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThat(AuditEntryPage.of(List.of(), 0, 25, 0).totalPages()).isZero();
    }

    @Test
    void metadataProjectionIsDefensivelyImmutableAndFailsClosed() {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        Map<String, Object> nested = new java.util.LinkedHashMap<>();
        nested.put("status", "ACTIVE");
        source.put("clientId", ACTOR_ID);
        source.put("nested", nested);

        AuditMetadataProjection projection = new AuditMetadataProjection(source, true);
        source.put("status", "CHANGED");
        nested.put("status", "CHANGED");

        assertThat(projection.values()).containsEntry("clientId", ACTOR_ID);
        assertThat(projection.values()).doesNotContainEntry("status", "CHANGED");
        assertThat(projection.metadataRedacted()).isTrue();
        assertThatThrownBy(() -> projection.values().put("status", "ACTIVE"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AuditMetadataProjection(
                Map.of("password", "not-public"), false))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> new AuditMetadataProjection(
                Map.of("safe", new Object()), false))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThat(AuditMetadataPolicy.isSensitiveKey("stripe_secret_key")).isTrue();
        assertThat(AuditMetadataPolicy.isSensitiveKey("accessCredentialId")).isFalse();
        assertThat(AuditMetadataPolicy.allowedKeysForAction("PAYMENT_REGISTERED"))
                .contains("amount", "currency", "paymentMethod")
                .doesNotContain("reason", "payload", "providerSecret");
        assertThat(AuditMetadataPolicy.allowedKeysForAction("UNSUPPORTED_ACTION"))
                .isEmpty();
    }

    @Test
    void publicContractsDoNotLeakPersistenceOrFrameworkTypes() {
        List<Class<?>> contracts = List.of(
                AuditEntrySummary.class,
                AuditEntryDetails.class,
                AuditEntryPage.class,
                AuditSearchQuery.class,
                AuditMetadataProjection.class);
        for (Class<?> contract : contracts) {
            assertThat(contract.isRecord()).isTrue();
            for (RecordComponent component : contract.getRecordComponents()) {
                assertSafeType(component.getType());
            }
        }
        for (Method method : AuditEntryQuery.class.getDeclaredMethods()) {
            assertSafeType(method.getReturnType());
            for (Class<?> parameter : method.getParameterTypes()) {
                assertSafeType(parameter);
            }
        }
    }

    private static AuditSearchQuery query(int page, int size) {
        return new AuditSearchQuery(
                null, null, null, null, null, null, null, null, null,
                page, size, null, null);
    }

    private static AuditEntrySummary summary() {
        return new AuditEntrySummary(
                UUID.randomUUID(),
                "CLIENT_REGISTERED",
                "CLIENT",
                RESOURCE_ID,
                "CLI-001",
                ACTOR_ID,
                "admin",
                "Client registered.",
                NOW,
                null);
    }

    private static void assertSafeType(Class<?> type) {
        String name = type.getName();
        assertThat(name)
                .doesNotContain("jakarta.persistence", "org.springframework",
                        "org.hibernate", "java.sql", ".infrastructure",
                        "com.fasterxml.jackson");
    }
}
