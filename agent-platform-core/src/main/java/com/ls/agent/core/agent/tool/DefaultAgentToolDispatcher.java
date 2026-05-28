package com.ls.agent.core.agent.tool;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.mcp.api.McpToolExecutor;
import com.ls.agent.core.mcp.command.McpToolExecuteCommand;
import com.ls.agent.core.mcp.dto.McpToolExecuteResult;
import com.ls.agent.core.skill.api.SkillExecutor;
import com.ls.agent.core.skill.command.SkillExecuteCommand;
import com.ls.agent.core.skill.dto.SkillExecuteResult;
import org.springframework.stereotype.Service;

@Service
public class DefaultAgentToolDispatcher implements AgentToolDispatcher {

    private final SkillExecutor skillExecutor;
    private final McpToolExecutor mcpToolExecutor;

    public DefaultAgentToolDispatcher(SkillExecutor skillExecutor, McpToolExecutor mcpToolExecutor) {
        this.skillExecutor = skillExecutor;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    @Override
    public AgentToolDispatchResult dispatch(AgentToolDispatchCommand command) {
        validate(command);
        if (AgentToolSourceType.SKILL.equals(command.sourceType())) {
            SkillExecuteResult result = skillExecutor.execute(new SkillExecuteCommand(
                    command.tenantId(),
                    command.userId(),
                    command.toolName(),
                    command.arguments()
            ));
            return new AgentToolDispatchResult(
                    result.success(),
                    result.skillCode(),
                    AgentToolSourceType.SKILL,
                    result.output(),
                    result.errorMessage()
            );
        }
        McpToolExecuteResult result = mcpToolExecutor.execute(new McpToolExecuteCommand(
                command.tenantId(),
                command.userId(),
                command.toolName(),
                command.arguments()
        ));
        return new AgentToolDispatchResult(
                result.success(),
                result.toolName(),
                AgentToolSourceType.MCP,
                result.output(),
                result.errorMessage()
        );
    }

    private void validate(AgentToolDispatchCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "agent tool dispatch command is required");
        }
        if (command.sourceType() == null) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "agent tool sourceType is required");
        }
        if (command.toolName() == null || command.toolName().isBlank()) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "agent tool name is required");
        }
    }
}
