package com.ls.agent.core.agent.tool;

public record AgentToolValidationResult(
        boolean valid,
        AgentToolDTO tool,
        AgentToolCall call,
        String observation
) {

    public static AgentToolValidationResult valid(AgentToolDTO tool, AgentToolCall call) {
        return new AgentToolValidationResult(true, tool, call, null);
    }

    public static AgentToolValidationResult invalid(AgentToolCall call, String observation) {
        return new AgentToolValidationResult(false, null, call, observation);
    }
}
