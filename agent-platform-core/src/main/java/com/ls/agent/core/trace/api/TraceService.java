package com.ls.agent.core.trace.api;

import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.trace.command.FinishTraceRootCommand;
import com.ls.agent.core.trace.command.FinishTraceSpanCommand;
import com.ls.agent.core.trace.command.QueryTracePageCommand;
import com.ls.agent.core.trace.command.StartTraceRootCommand;
import com.ls.agent.core.trace.command.StartTraceSpanCommand;
import com.ls.agent.core.trace.dto.TraceDetailDTO;
import com.ls.agent.core.trace.dto.TraceRootDTO;
import com.ls.agent.core.trace.dto.TraceSpanDTO;
import com.ls.agent.core.trace.dto.TraceSummaryDTO;

public interface TraceService {

    TraceRootDTO startRoot(StartTraceRootCommand command);

    void finishRoot(FinishTraceRootCommand command);

    TraceSpanDTO startSpan(StartTraceSpanCommand command);

    void finishSpan(FinishTraceSpanCommand command);

    TraceDetailDTO getTrace(Long tenantId, Long userId, String traceId);

    PageResult<TraceSummaryDTO> pageTraces(QueryTracePageCommand command);
}
