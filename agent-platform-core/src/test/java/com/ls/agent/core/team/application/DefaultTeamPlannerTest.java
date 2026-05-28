package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.agent.tool.AgentToolDTO;
import com.ls.agent.core.agent.tool.AgentToolRiskLevel;
import com.ls.agent.core.agent.tool.AgentToolSourceType;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.team.command.PlanTeamCommand;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTeamPlannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelInvokeService modelInvokeService = mock(ModelInvokeService.class);
    private final DefaultTeamPlanner planner = new DefaultTeamPlanner(
            modelInvokeService,
            new TaskPlanValidator(),
            objectMapper
    );

    @Test
    void returnsValidatedPlanFromModelJson() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("""
                {
                  "goal": "Plan a team activity",
                  "tasks": [
                    {
                      "id": "task-1",
                      "name": "Check weather",
                      "description": "Check the weather for the target time.",
                      "taskType": "TOOL_TASK",
                      "suggestedTool": "weather",
                      "arguments": {"city": "Chongqing"},
                      "dependsOn": []
                    },
                    {
                      "id": "task-2",
                      "name": "Summarize",
                      "description": "Summarize options with budget constraints.",
                      "taskType": "MODEL_TASK",
                      "suggestedTool": null,
                      "arguments": {},
                      "dependsOn": ["task-1"]
                    }
                  ]
                }
                """));

        TaskPlanDTO result = planner.plan(command("Plan an easy team activity", tools()));

        assertThat(result.goal()).isEqualTo("Plan a team activity");
        assertThat(result.tasks()).hasSize(2);
        assertThat(result.tasks().get(0).suggestedTool()).isEqualTo("weather");

        ArgumentCaptor<ModelInvokeCommand> captor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(captor.capture());
        assertThat(captor.getValue().modelConfigId()).isEqualTo(30001L);
        assertThat(captor.getValue().stream()).isFalse();
        assertThat(captor.getValue().messages()).extracting(ModelMessage::role).containsExactly("system", "user");
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("[SKILL] weather");
        assertThat(prompt).contains("[MCP] read_file");
    }

    @Test
    void retriesOnceWhenFirstModelOutputIsInvalid() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("not json"))
                .thenReturn(modelResult("""
                        {
                          "goal": "Answer directly",
                          "tasks": [
                            {
                              "id": "task-1",
                              "name": "Answer",
                              "description": "Answer the original user request.",
                              "taskType": "MODEL_TASK",
                              "suggestedTool": null,
                              "arguments": {},
                              "dependsOn": []
                            }
                          ]
                        }
                        """));

        TaskPlanDTO result = planner.plan(command("Explain Team mode", tools()));

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().get(0).taskType()).isEqualTo("MODEL_TASK");
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void fallsBackToGenericModelTaskAfterTwoInvalidOutputs() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("not json"))
                .thenReturn(modelResult("{\"goal\":\"bad\",\"tasks\":[]}"));

        TaskPlanDTO result = planner.plan(command("Any user request", List.of()));

        assertThat(result.goal()).isEqualTo("Any user request");
        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().get(0).id()).isEqualTo("task-1");
        assertThat(result.tasks().get(0).taskType()).isEqualTo("MODEL_TASK");
        assertThat(result.tasks().get(0).suggestedTool()).isNull();
        assertThat(result.tasks().get(0).description()).contains("Any user request");
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void fallsBackWhenModelReturnsEmptyResponse() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult(" "))
                .thenReturn(modelResult(""));

        TaskPlanDTO result = planner.plan(command("Summarize project status", tools()));

        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().get(0).taskType()).isEqualTo("MODEL_TASK");
        assertThat(result.tasks().get(0).suggestedTool()).isNull();
    }

    @Test
    void fallsBackWhenModelInvocationFails() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenThrow(new IllegalStateException("model down"))
                .thenThrow(new IllegalStateException("model still down"));

        TaskPlanDTO result = planner.plan(command("Prepare a fallback plan", tools()));

        assertThat(result.goal()).isEqualTo("Prepare a fallback plan");
        assertThat(result.tasks()).hasSize(1);
        assertThat(result.tasks().get(0).taskType()).isEqualTo("MODEL_TASK");
        assertThat(result.tasks().get(0).suggestedTool()).isNull();
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void retriesWhenModelPlanExceedsMaxTasks() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult(planWithTooManyTasks()))
                .thenReturn(modelResult("""
                        {
                          "goal": "Compact plan",
                          "tasks": [
                            {
                              "id": "task-1",
                              "name": "Answer",
                              "description": "Answer compactly.",
                              "taskType": "MODEL_TASK",
                              "suggestedTool": null,
                              "arguments": {},
                              "dependsOn": []
                            }
                          ]
                        }
                        """));

        TaskPlanDTO result = planner.plan(command("Create a compact plan", tools()));

        assertThat(result.goal()).isEqualTo("Compact plan");
        assertThat(result.tasks()).hasSize(1);
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    private PlanTeamCommand command(String userInput, List<AgentToolDTO> tools) {
        return new PlanTeamCommand(userInput, context(), tools);
    }

    private AgentContextDTO context() {
        return new AgentContextDTO(
                30001L,
                new ProfileDTO(
                        50001L,
                        20001L,
                        "Team Profile",
                        "GENERAL",
                        "Demo team profile",
                        30001L,
                        "Keep the plan practical.",
                        objectMapper.createObjectNode(),
                        5,
                        "TEAM",
                        "PRIVATE",
                        "DRAFT",
                        List.of(),
                        List.of()
                ),
                List.of(),
                List.of(),
                List.of(),
                120,
                false
        );
    }

    private List<AgentToolDTO> tools() {
        return List.of(
                new AgentToolDTO(
                        "weather",
                        "Weather",
                        "Check weather.",
                        AgentToolSourceType.SKILL,
                        objectMapper.createObjectNode(),
                        AgentToolRiskLevel.LOW
                ),
                new AgentToolDTO(
                        "read_file",
                        "read_file",
                        "Read a demo file.",
                        AgentToolSourceType.MCP,
                        objectMapper.createObjectNode(),
                        AgentToolRiskLevel.LOW
                )
        );
    }

    private ModelInvokeResult modelResult(String content) {
        return new ModelInvokeResult(30001L, 1L, "mock", "mock-chat", content, null);
    }

    private String planWithTooManyTasks() {
        StringBuilder builder = new StringBuilder("{\"goal\":\"Too many\",\"tasks\":[");
        for (int index = 1; index <= TeamLimits.DEFAULT_MAX_TASKS + 1; index++) {
            if (index > 1) {
                builder.append(",");
            }
            builder.append("""
                    {
                      "id": "task-%d",
                      "name": "Task %d",
                      "description": "Task description",
                      "taskType": "MODEL_TASK",
                      "suggestedTool": null,
                      "arguments": {},
                      "dependsOn": []
                    }
                    """.formatted(index, index));
        }
        builder.append("]}");
        return builder.toString();
    }
}
