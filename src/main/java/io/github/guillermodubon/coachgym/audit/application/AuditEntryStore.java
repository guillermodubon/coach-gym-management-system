package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.plan.PlanChanged;

public interface AuditEntryStore {

    void recordClientRegistered(ClientRegistered event);

    void recordPlanChanged(PlanChanged event);
}
