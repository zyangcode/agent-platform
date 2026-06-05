package com.ls.agent.core.agent.tool;

import java.util.List;

public interface AgentToolCallValidator {

    AgentToolValidationResult validate(AgentToolCall call, List<AgentToolDTO> availableTools);
}
