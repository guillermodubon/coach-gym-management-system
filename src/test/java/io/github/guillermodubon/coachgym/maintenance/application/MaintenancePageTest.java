package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaintenancePageTest {

    @Test
    void createsImmutableEmptyPage() {
        MaintenancePage page = new MaintenancePage(List.of(), 0, 25, 0, 0);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void defensivelyCopiesItems() {
        var source = new ArrayList<io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails>();
        MaintenancePage page = new MaintenancePage(source, 0, 25, 0, 0);
        source.clear();
        assertThat(page.items()).isEmpty();
        assertThatThrownBy(() -> page.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInconsistentTotals() {
        assertThatThrownBy(() -> new MaintenancePage(List.of(), 0, 25, 26, 1))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("totals are inconsistent");
    }
}
