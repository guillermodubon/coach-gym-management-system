package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClientSearchContractTest {

    @Test
    void searchContractsRemainPublicImmutableRecords() {
        assertThat(List.of(
                ClientSearchQuery.class,
                ClientSummary.class,
                ClientPage.class,
                ClientEmergencyContactDetails.class,
                ClientMembershipSummary.class,
                ClientPaymentSummary.class,
                ClientAccessSummary.class,
                ClientPhotoDetails.class,
                ClientOperationalProfile.class,
                ClientStatusHistoryDetails.class))
                .allSatisfy(type -> assertThat(type.isRecord()).isTrue());
    }

    @Test
    void sortContractContainsOnlyAllowlistedFields() {
        assertThat(ClientSortField.values()).containsExactly(
                ClientSortField.CLIENT_CODE,
                ClientSortField.FIRST_NAME,
                ClientSortField.LAST_NAME,
                ClientSortField.CREATED_AT,
                ClientSortField.UPDATED_AT);
        assertThat(ClientSortDirection.values()).containsExactly(
                ClientSortDirection.ASC,
                ClientSortDirection.DESC);
    }
}
