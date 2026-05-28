package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.api.McpToolExecutor;
import com.ls.agent.core.mcp.command.McpToolExecuteCommand;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import com.ls.agent.core.skill.api.SkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultAgentToolDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillExecutor skillExecutor = mock(SkillExecutor.class);
    private final McpToolExecutor mcpToolExecutor = mock(McpToolExecutor.class);
    private final DefaultAgentToolDispatcher dispatcher = new DefaultAgentToolDispatcher(skillExecutor, mcpToolExecutor);

    @Test
    void dispatchesSkillToolToSkillExecutor() {
        when(skillExecutor.execute(any(SkillExecuteCommand.class))).thenReturn(new SkillExecuteResult(
                true,
                "calculator",
                objectMapper.createObjectNode().put("result", "2"),
                null
        ));

        AgentToolDispatchResult result = dispatcher.dispatch(new AgentToolDispatchCommand(
                1L,
                10001L,
                "calculator",
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode().put("expression", "1+1")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("calculator");
        assertThat(result.sourceType()).isEqualTo(AgentToolSourceType.SKILL);
        assertThat(result.output().get("result").asText()).isEqualTo("2");
        verify(skillExecutor).execute(any(SkillExecuteCommand.class));
        verifyNoInteractions(mcpToolExecutor);
    }

    @Test
    void dispatchesMcpToolToMcpExecutor() {
        when(mcpToolExecutor.execute(any(McpToolExecuteCommand.class))).thenReturn(new McpToolExecuteResult(
                true,
                "read_file",
                objectMapper.createObjectNode().put("content", "demo"),
                null
        ));

        AgentToolDispatchResult result = dispatcher.dispatch(new AgentToolDispatchCommand(
                1L,
                10001L,
                "read_file",
                AgentToolSourceType.MCP,
                objectMapper.createObjectNode().put("path", "/tmp/demo.txt")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("read_file");
        assertThat(result.sourceType()).isEqualTo(AgentToolSourceType.MCP);
        assertThat(result.output().get("content").asText()).isEqualTo("demo");
        verify(mcpToolExecutor).execute(any(McpToolExecuteCommand.class));
        verifyNoInteractions(skillExecutor);
    }

    @Test
    void rejectsMissingSourceType() {
        AgentToolDispatchCommand command = new AgentToolDispatchCommand(
                1L,
                10001L,
                "calculator",
                null,
                objectMapper.createObjectNode()
        );

        assertThatThrownBy(() -> dispatcher.dispatch(command))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getCode())
                        .isEqualTo(ErrorCode.REQUEST_INVALID.getCode()));
    }
}
