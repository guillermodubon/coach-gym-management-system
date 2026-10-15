package io.github.guillermodubon.coachgym.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportingQueryScopeTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void distinguishesOrganizationScopeFromNormalizedBranchFilters() {
        assertThat(ReportingQueryScope.organization())
                .satisfies(scope -> {
                    assertThat(scope.organizationWide()).isTrue();
                    assertThat(scope.branchIds()).isEmpty();
                });

        assertThat(ReportingQueryScope.branches(List.of(SECOND, FIRST, SECOND)))
                .satisfies(scope -> {
                    assertThat(scope.organizationWide()).isFalse();
                    assertThat(scope.branchIds()).containsExactly(FIRST, SECOND);
                });
    }

    @Test
    void rejectsAmbiguousOrNullBranchScope() {
        assertThatThrownBy(() -> new ReportingQueryScope(true, List.of(FIRST)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportingQueryScope(false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportingQueryScope.branches(List.of(FIRST, null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReportingQueryScope(true, null))
                .isInstanceOf(NullPointerException.class);
    }
}
