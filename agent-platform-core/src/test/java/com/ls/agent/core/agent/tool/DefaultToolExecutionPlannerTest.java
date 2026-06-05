package com.ls.agent.core.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolExecutionPlannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultToolExecutionPlanner planner = new DefaultToolExecutionPlanner();

    @Test
    void plansReadOnlyLowRiskDisjointToolsIntoParallelGroup() {
        ToolExecutionPlan plan = planner.plan(List.of(
                call("calculator", readOnlyTool("calculator", List.of("calc"))),
                call("read_file", readOnlyTool("read_file", List.of("file:/tmp/demo.txt")))
        ), 2);

        assertThat(plan.groups()).hasSize(1);
        assertThat(plan.groups().get(0).parallel()).isTrue();
        assertThat(plan.groups().get(0).items()).extracting(ToolExecutionPlan.Item::toolName)
                .containsExactly("calculator", "read_file");
    }

    @Test
    void keepsConflictingResourceKeysSerial() {
        ToolExecutionPlan plan = planner.plan(List.of(
                call("read_a", readOnlyTool("read_a", List.of("file:/tmp/demo.txt"))),
                call("read_b", readOnlyTool("read_b", List.of("file:/tmp/demo.txt")))
        ), 2);

        assertThat(plan.groups()).hasSize(2);
        assertThat(plan.groups()).extracting(ToolExecutionPlan.Group::parallel)
                .containsExactly(false, false);
    }

    @Test
    void keepsWritableOrHighRiskToolsSerial() {
        ToolExecutionPlan plan = planner.plan(List.of(
                call("search", new AgentToolDTO(
                        "search",
                        "search",
                        "Search",
                        AgentToolSourceType.SKILL,
                        objectMapper.createObjectNode(),
                        AgentToolRiskLevel.LOW,
                        false,
                        List.of("web")
                )),
                call("deploy", new AgentToolDTO(
                        "deploy",
                        "deploy",
                        "Deploy",
                        AgentToolSourceType.SKILL,
                        objectMapper.createObjectNode(),
                        AgentToolRiskLevel.HIGH,
                        true,
                        List.of("prod")
                ))
        ), 2);

        assertThat(plan.groups()).hasSize(2);
        assertThat(plan.groups()).extracting(ToolExecutionPlan.Group::parallel)
                .containsExactly(false, false);
    }

    @Test
    void splitsParallelGroupsByMaxParallelTools() {
        ToolExecutionPlan plan = planner.plan(List.of(
                call("a", readOnlyTool("a", List.of("a"))),
                call("b", readOnlyTool("b", List.of("b"))),
                call("c", readOnlyTool("c", List.of("c")))
        ), 2);

        assertThat(plan.groups()).hasSize(2);
        assertThat(plan.groups().get(0).parallel()).isTrue();
        assertThat(plan.groups().get(0).items()).extracting(ToolExecutionPlan.Item::toolName)
                .containsExactly("a", "b");
        assertThat(plan.groups().get(1).parallel()).isFalse();
        assertThat(plan.groups().get(1).items()).extracting(ToolExecutionPlan.Item::toolName)
                .containsExactly("c");
    }

    private ToolExecutionPlan.Item call(String toolName, AgentToolDTO tool) {
        return new ToolExecutionPlan.Item(
                AgentToolSourceType.SKILL,
                toolName,
                objectMapper.createObjectNode(),
                tool
        );
    }

    private AgentToolDTO readOnlyTool(String name, List<String> resourceKeys) {
        return new AgentToolDTO(
                name,
                name,
                name,
                AgentToolSourceType.SKILL,
                objectMapper.createObjectNode(),
                AgentToolRiskLevel.LOW,
                true,
                resourceKeys
        );
    }
}
