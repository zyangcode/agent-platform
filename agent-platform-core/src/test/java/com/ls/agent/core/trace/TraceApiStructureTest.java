package com.ls.agent.core.trace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceApiStructureTest {

    private static final List<String> TRACE_API_TYPES = List.of(
            "com.ls.agent.core.trace.api.TraceService",
            "com.ls.agent.core.trace.command.StartTraceRootCommand",
            "com.ls.agent.core.trace.command.FinishTraceRootCommand",
            "com.ls.agent.core.trace.command.StartTraceSpanCommand",
            "com.ls.agent.core.trace.command.FinishTraceSpanCommand",
            "com.ls.agent.core.trace.command.QueryTracePageCommand",
            "com.ls.agent.core.trace.dto.TraceRootDTO",
            "com.ls.agent.core.trace.dto.TraceSpanDTO",
            "com.ls.agent.core.trace.dto.TraceDetailDTO",
            "com.ls.agent.core.trace.dto.TraceSummaryDTO"
    );

    @Test
    void traceApiCommandAndDtoTypesExist() throws ClassNotFoundException {
        for (String typeName : TRACE_API_TYPES) {
            assertThat(Class.forName(typeName))
                    .as(typeName)
                    .isNotNull();
        }
    }
}
