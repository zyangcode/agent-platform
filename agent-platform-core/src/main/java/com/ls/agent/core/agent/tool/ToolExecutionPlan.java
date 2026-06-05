package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record ToolExecutionPlan(List<Group> groups) {

    public ToolExecutionPlan {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public record Group(boolean parallel, List<Item> items) {

        public Group {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record Item(
            AgentToolSourceType sourceType,
            String toolName,
            JsonNode arguments,
            AgentToolDTO tool
    ) {
    }
}
