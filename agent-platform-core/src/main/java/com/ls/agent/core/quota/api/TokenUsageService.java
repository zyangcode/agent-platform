package com.ls.agent.core.quota.api;

import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.quota.command.QueryTokenUsagePageCommand;
import com.ls.agent.core.quota.command.QueryTokenUsageSummaryCommand;
import com.ls.agent.core.quota.command.RecordTokenUsageCommand;
import com.ls.agent.core.quota.dto.TokenUsageAggregateDTO;
import com.ls.agent.core.quota.dto.TokenUsageDTO;
import com.ls.agent.core.quota.dto.TokenUsageSummaryDTO;

import java.util.List;
import java.util.Map;

public interface TokenUsageService {

    void record(RecordTokenUsageCommand command);

    PageResult<TokenUsageDTO> pageTokenUsages(QueryTokenUsagePageCommand command);

    TokenUsageSummaryDTO summarizeTokenUsages(QueryTokenUsageSummaryCommand command);

    List<TokenUsageDTO> listByTrace(Long tenantId, Long userId, String traceId);

    Map<String, TokenUsageAggregateDTO> aggregateByTraceIds(
            Long tenantId,
            Long userId,
            List<String> traceIds,
            Long modelConfigId
    );
}
