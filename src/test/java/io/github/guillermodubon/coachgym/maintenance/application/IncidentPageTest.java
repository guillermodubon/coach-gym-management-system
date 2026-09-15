package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class IncidentPageTest {

    @Test
    void defensivelyCopiesItems() {
        var mutableItems = new ArrayList<io.github.guillermodubon.coachgym.maintenance.IncidentDetails>();
        IncidentPage page = new IncidentPage(
                mutableItems,
                0,
                25,
                0,
                0);

        mutableItems.clear();
        assertThat(page.items()).isEmpty();
        assertThatThrownBy(() -> page.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNegativeTotals() {
        assertThatThrownBy(() ->
                new IncidentPage(java.util.List.of(), 0, 25, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Incident total elements cannot be negative.");
    }
}
