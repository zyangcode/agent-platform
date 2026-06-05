package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultAgentToolCallValidator implements AgentToolCallValidator {

    private final ObjectMapper objectMapper;

    public DefaultAgentToolCallValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentToolValidationResult validate(AgentToolCall call, List<AgentToolDTO> availableTools) {
        if (call == null || call.sourceType() == null || call.toolName() == null || call.toolName().isBlank()) {
            return AgentToolValidationResult.invalid(call, "[tool unavailable] tool name is required");
        }
        JsonNode normalizedArguments = call.arguments() == null || call.arguments().isNull()
                ? objectMapper.createObjectNode()
                : call.arguments();
        AgentToolCall normalizedCall = new AgentToolCall(call.sourceType(), call.toolName(), normalizedArguments);
        AgentToolDTO tool = findTool(normalizedCall, availableTools);
        if (tool == null) {
            return AgentToolValidationResult.invalid(
                    normalizedCall,
                    "[tool unavailable] " + normalizedCall.sourceType().name().toLowerCase() + ":" + normalizedCall.toolName()
            );
        }
        if (!normalizedArguments.isObject()) {
            return AgentToolValidationResult.invalid(
                    normalizedCall,
                    "[tool invalid arguments] " + normalizedCall.sourceType().name().toLowerCase() + ":" + normalizedCall.toolName()
                            + " arguments must be a JSON object"
            );
        }
        return AgentToolValidationResult.valid(tool, normalizedCall);
    }

    private AgentToolDTO findTool(AgentToolCall call, List<AgentToolDTO> availableTools) {
        if (availableTools == null || availableTools.isEmpty()) {
            return null;
        }
        return availableTools.stream()
                .filter(tool -> tool != null)
                .filter(tool -> call.sourceType().equals(tool.sourceType()))
                .filter(tool -> call.toolName().equals(tool.name()))
                .findFirst()
                .orElse(null);
    }
}
