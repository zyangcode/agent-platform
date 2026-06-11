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
import com.ls.agent.core.model.dto.ModelToolCallDTO;
import com.ls.agent.core.model.dto.ModelToolSpecDTO;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.team.command.PlanTeamCommand;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamPlanResultDTO;
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
    void returnsValidatedPlanFromFunctionCallArguments() {
        com.fasterxml.jackson.databind.node.ObjectNode task = objectMapper.createObjectNode()
                .put("id", "task-1")
                .put("name", "Answer")
                .put("description", "Answer directly with gathered context.")
                .put("taskType", "MODEL_TASK")
                .putNull("suggestedTool");
        task.set("arguments", objectMapper.createObjectNode());
        task.set("dependsOn", objectMapper.createArrayNode());
        com.fasterxml.jackson.databind.node.ObjectNode arguments = objectMapper.createObjectNode()
                .put("goal", "Plan with function calling");
        arguments.set("tasks", objectMapper.createArrayNode().add(task));
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult(
                "",
                new ModelToolCallDTO("TEAM", "team_plan", arguments)
        ));

        TeamPlanResultDTO result = planner.plan(command("Use function calling", tools()));

        assertThat(result.plan().goal()).isEqualTo("Plan with function calling");
        assertThat(result.plan().tasks()).hasSize(1);
        assertThat(result.plan().tasks().get(0).taskType()).isEqualTo("MODEL_TASK");

        ArgumentCaptor<ModelInvokeCommand> captor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(captor.capture());
        assertThat(captor.getValue().tools()).extracting(ModelToolSpecDTO::name)
                .containsExactly("team_plan");
    }

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

        TeamPlanResultDTO result = planner.plan(command("Plan an easy team activity", tools()));

        assertThat(result.plan().goal()).isEqualTo("Plan a team activity");
        assertThat(result.plan().tasks()).hasSize(2);
        assertThat(result.plan().tasks().get(0).suggestedTool()).isEqualTo("weather");
        assertThat(result.modelInvocations()).hasSize(1);
        assertThat(result.modelInvocations().get(0).usage().totalTokens()).isEqualTo(4);

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

        TeamPlanResultDTO result = planner.plan(command("Explain Team mode", tools()));

        assertThat(result.plan().tasks()).hasSize(1);
        assertThat(result.plan().tasks().get(0).taskType()).isEqualTo("MODEL_TASK");
        assertThat(result.modelInvocations()).hasSize(2);
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void fallsBackToGenericModelTaskAfterTwoInvalidOutputs() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("not json"))
                .thenReturn(modelResult("{\"goal\":\"bad\",\"tasks\":[]}"));

        TeamPlanResultDTO result = planner.plan(command("Any user request", List.of()));

        assertThat(result.plan().goal()).isEqualTo("Any user request");
        assertThat(result.plan().tasks()).hasSize(1);
        assertThat(result.plan().tasks().get(0).id()).isEqualTo("task-1");
        assertThat(result.plan().tasks().get(0).taskType()).isEqualTo("MODEL_TASK");
        assertThat(result.plan().tasks().get(0).suggestedTool()).isNull();
        assertThat(result.plan().tasks().get(0).description()).contains("Any user request");
        assertThat(result.modelInvocations()).hasSize(2);
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void fallsBackWhenModelReturnsEmptyResponse() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult(" "))
                .thenReturn(modelResult(""));

        TeamPlanResultDTO result = planner.plan(command("Summarize project status", tools()));

        assertThat(result.plan().tasks()).hasSize(1);
        assertThat(result.plan().tasks().get(0).taskType()).isEqualTo("MODEL_TASK");
        assertThat(result.plan().tasks().get(0).suggestedTool()).isNull();
        assertThat(result.modelInvocations()).hasSize(2);
    }

    @Test
    void fallsBackWhenModelInvocationFails() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenThrow(new IllegalStateException("model down"))
                .thenThrow(new IllegalStateException("model still down"));

        TeamPlanResultDTO result = planner.plan(command("Prepare a fallback plan", tools()));

        assertThat(result.plan().goal()).isEqualTo("Prepare a fallback plan");
        assertThat(result.plan().tasks()).hasSize(1);
        assertThat(result.plan().tasks().get(0).taskType()).isEqualTo("MODEL_TASK");
        assertThat(result.plan().tasks().get(0).suggestedTool()).isNull();
        assertThat(result.modelInvocations()).isEmpty();
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

        TeamPlanResultDTO result = planner.plan(command("Create a compact plan", tools()));

        assertThat(result.plan().goal()).isEqualTo("Compact plan");
        assertThat(result.plan().tasks()).hasSize(1);
        assertThat(result.modelInvocations()).hasSize(2);
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void truncatesPreviousFailureInRetryPrompt() {
        String longFailure = "x".repeat(500);
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenThrow(new IllegalStateException(longFailure))
                .thenReturn(modelResult("""
                        {
                          "goal": "Retry plan",
                          "tasks": [
                            {
                              "id": "task-1",
                              "name": "Answer",
                              "description": "Answer after retry.",
                              "taskType": "MODEL_TASK",
                              "suggestedTool": null,
                              "arguments": {},
                              "dependsOn": []
                            }
                          ]
                        }
                        """));

        planner.plan(command("Retry with compact failure", tools()));

        ArgumentCaptor<ModelInvokeCommand> captor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService, times(2)).invoke(captor.capture());
        String retryPrompt = captor.getAllValues().get(1).messages().get(1).content();
        assertThat(retryPrompt).contains("Previous plan was invalid:");
        assertThat(retryPrompt).contains("...");
        assertThat(retryPrompt).doesNotContain("x".repeat(250));
    }

    @Test
    void includesPreviousPlanResultsAndReviewWhenReplanning() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("""
                {
                  "goal": "Plan with missing weather",
                  "tasks": [
                    {
                      "id": "task-1",
                      "name": "Summarize",
                      "description": "Summarize existing options.",
                      "taskType": "MODEL_TASK",
                      "suggestedTool": null,
                      "arguments": {},
                      "dependsOn": []
                    },
                    {
                      "id": "task-weather",
                      "name": "Check weather",
                      "description": "Check weather before final recommendation.",
                      "taskType": "TOOL_TASK",
                      "suggestedTool": "weather",
                      "arguments": {"city": "Chongqing"},
                      "dependsOn": ["task-1"]
                    }
                  ]
                }
                """));
        PlanTeamCommand replanCommand = new PlanTeamCommand(
                "Plan an easy team activity",
                context(),
                tools(),
                new TaskPlanDTO(
                        "Plan activity",
                        List.of(new com.ls.agent.core.team.dto.TeamTaskDTO(
                                "task-1",
                                "Summarize",
                                "Summarize existing options.",
                                "MODEL_TASK",
                                null,
                                objectMapper.createObjectNode(),
                                List.of()
                        ))
                ),
                List.of(new com.ls.agent.core.team.dto.ExecutionResultDTO(
                        "task-1",
                        "MODEL_TASK",
                        "SUCCESS",
                        "Indoor options only",
                        List.of(),
                        null
                )),
                new com.ls.agent.core.team.dto.ReviewResultDTO(
                        false,
                        List.of(new com.ls.agent.core.team.dto.ReviewResultDTO.ReviewIssueDTO(null, "WARN", "Need weather")),
                        List.of(),
                        "Need weather before final answer",
                        true,
                        "Add a weather tool task and keep task-1 unchanged"
                )
        );

        TeamPlanResultDTO result = planner.plan(replanCommand);

        assertThat(result.plan().tasks()).extracting("id").containsExactly("task-1", "task-weather");
        ArgumentCaptor<ModelInvokeCommand> captor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("Re-plan mode");
        assertThat(prompt).contains("Keep existing completed task ids unchanged");
        assertThat(prompt).contains("task-1");
        assertThat(prompt).contains("Indoor options only");
        assertThat(prompt).contains("Need weather before final answer");
        assertThat(prompt).contains("Add a weather tool task");
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
        return new ModelInvokeResult(30001L, 1L, "mock", "mock-chat", content, new ModelUsageDTO(2, 2, 4, true));
    }

    private ModelInvokeResult modelResult(String content, ModelToolCallDTO toolCall) {
        return new ModelInvokeResult(
                30001L,
                1L,
                "mock",
                "mock-chat",
                content,
                new ModelUsageDTO(2, 2, 4, true),
                List.of(toolCall)
        );
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
