package com.ls.agent.core.trace.api;

import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.trace.command.QueryTokenUsagePageCommand;
import com.ls.agent.core.trace.command.QueryTokenUsageSummaryCommand;
import com.ls.agent.core.trace.command.RecordTokenUsageCommand;
import com.ls.agent.core.trace.dto.TokenUsageDTO;
import com.ls.agent.core.trace.dto.TokenUsageSummaryDTO;

public interface TokenUsageService {

    void record(RecordTokenUsageCommand command);

    PageResult<TokenUsageDTO> pageTokenUsages(QueryTokenUsagePageCommand command);

    TokenUsageSummaryDTO summarizeTokenUsages(QueryTokenUsageSummaryCommand command);
}
