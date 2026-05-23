package com.ls.agent.core.quota;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaApiStructureTest {

    private static final List<String> QUOTA_API_TYPES = List.of(
            "com.ls.agent.core.quota.api.QuotaService",
            "com.ls.agent.core.quota.command.CommitQuotaReservationCommand",
            "com.ls.agent.core.quota.command.ReleaseQuotaReservationCommand",
            "com.ls.agent.core.quota.command.ReserveQuotaCommand",
            "com.ls.agent.core.quota.dto.QuotaReservationDTO",
            "com.ls.agent.core.quota.api.TokenUsageService",
            "com.ls.agent.core.quota.command.RecordTokenUsageCommand",
            "com.ls.agent.core.quota.command.QueryTokenUsagePageCommand",
            "com.ls.agent.core.quota.command.QueryTokenUsageSummaryCommand",
            "com.ls.agent.core.quota.dto.TokenUsageAggregateDTO",
            "com.ls.agent.core.quota.dto.TokenUsageDTO",
            "com.ls.agent.core.quota.dto.TokenUsageSummaryDTO",
            "com.ls.agent.core.quota.dto.TokenUsageTopModelDTO"
    );

    @Test
    void quotaApiCommandAndDtoTypesExist() throws ClassNotFoundException {
        for (String typeName : QUOTA_API_TYPES) {
            assertThat(Class.forName(typeName))
                    .as(typeName)
                    .isNotNull();
        }
    }
}
