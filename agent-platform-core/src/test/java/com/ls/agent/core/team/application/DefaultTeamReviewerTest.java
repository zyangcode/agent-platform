package com.ls.agent.core.team.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.core.context.dto.AgentContextDTO;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelToolCallDTO;
import com.ls.agent.core.model.dto.ModelToolSpecDTO;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.profile.dto.ProfileDTO;
import com.ls.agent.core.team.command.ReviewTeamCommand;
import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.ReviewResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import com.ls.agent.core.team.dto.TeamReviewResultDTO;
import com.ls.agent.core.team.dto.TeamTaskDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTeamReviewerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelInvokeService modelInvokeService = mock(ModelInvokeService.class);
    private final DefaultTeamReviewer reviewer = new DefaultTeamReviewer(
            modelInvokeService,
            new ReviewResultValidator(),
            objectMapper
    );

    @Test
    void returnsValidatedReviewFromFunctionCallArguments() {
        com.fasterxml.jackson.databind.node.ObjectNode arguments = objectMapper.createObjectNode()
                .put("passed", true)
                .put("summary", "Function review passed.")
                .put("replanRequired", false)
                .put("replanInstruction", "");
        arguments.set("issues", objectMapper.createArrayNode());
        arguments.set("retryTasks", objectMapper.createArrayNode());

        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult(
                "",
                new ModelToolCallDTO("TEAM", "team_review", arguments)
        ));

        TeamReviewResultDTO result = reviewer.review(command("Draft answer"));

        assertThat(result.reviewResult().passed()).isTrue();
        assertThat(result.reviewResult().summary()).isEqualTo("Function review passed.");

        ArgumentCaptor<ModelInvokeCommand> captor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(captor.capture());
        assertThat(captor.getValue().tools()).extracting(ModelToolSpecDTO::name)
                .containsExactly("team_review");
    }

    @Test
    void returnsValidatedReviewFromModelJson() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("""
                {
                  "passed": true,
                  "issues": [],
                  "retryTasks": [],
                  "summary": "Plan covers the request."
                }
                """));

        TeamReviewResultDTO result = reviewer.review(command("Draft answer"));

        assertThat(result.reviewResult().passed()).isTrue();
        assertThat(result.reviewResult().summary()).isEqualTo("Plan covers the request.");
        assertThat(result.modelInvocations()).hasSize(1);
        assertThat(result.modelInvocations().get(0).usage().totalTokens()).isEqualTo(7);

        ArgumentCaptor<ModelInvokeCommand> captor = ArgumentCaptor.forClass(ModelInvokeCommand.class);
        verify(modelInvokeService).invoke(captor.capture());
        assertThat(captor.getValue().modelConfigId()).isEqualTo(30001L);
        assertThat(captor.getValue().stream()).isFalse();
        assertThat(captor.getValue().messages()).extracting(ModelMessage::role).containsExactly("system", "user");
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("Original user request");
        assertThat(prompt).contains("Task plan");
        assertThat(prompt).contains("Execution results");
        assertThat(prompt).contains("Answer draft");
    }

    @Test
    void acceptsFailedReviewWithRetryTasks() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class))).thenReturn(modelResult("""
                {
                  "passed": false,
                  "issues": [{"taskId": "task-1", "level": "WARN", "message": "Need more detail"}],
                  "retryTasks": ["task-1"],
                  "summary": "Retry task-1."
                }
                """));

        TeamReviewResultDTO result = reviewer.review(command("Draft answer"));

        assertThat(result.reviewResult().passed()).isFalse();
        assertThat(result.reviewResult().retryTasks()).containsExactly("task-1");
        assertThat(result.reviewResult().issues()).hasSize(1);
    }

    @Test
    void retriesOnceWhenModelOutputIsInvalid() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("not json"))
                .thenReturn(modelResult("""
                        {
                          "passed": true,
                          "issues": [],
                          "retryTasks": [],
                          "summary": "Corrected review."
                        }
                        """));

        TeamReviewResultDTO result = reviewer.review(command("Draft answer"));

        assertThat(result.reviewResult().passed()).isTrue();
        assertThat(result.reviewResult().summary()).isEqualTo("Corrected review.");
        assertThat(result.modelInvocations()).hasSize(2);
        verify(modelInvokeService, times(2)).invoke(any(ModelInvokeCommand.class));
    }

    @Test
    void fallsBackToWarningReviewAfterTwoInvalidOutputs() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenReturn(modelResult("not json"))
                .thenReturn(modelResult("{\"passed\":false,\"retryTasks\":[\"missing\"],\"issues\":[],\"summary\":\"bad\"}"));

        TeamReviewResultDTO result = reviewer.review(command("Draft answer"));

        assertThat(result.reviewResult().passed()).isTrue();
        assertThat(result.reviewResult().issues()).hasSize(1);
        assertThat(result.reviewResult().issues().get(0).level()).isEqualTo("WARN");
        assertThat(result.reviewResult().summary()).contains("fallback");
        assertThat(result.reviewResult().retryTasks()).isEmpty();
        assertThat(result.modelInvocations()).hasSize(2);
    }

    @Test
    void fallbackWarnsWhenAnswerDraftIsBlank() {
        when(modelInvokeService.invoke(any(ModelInvokeCommand.class)))
                .thenThrow(new IllegalStateException("model down"))
                .thenThrow(new IllegalStateException("model still down"));

        TeamReviewResultDTO result = reviewer.review(command(" "));

        assertThat(result.reviewResult().passed()).isTrue();
        assertThat(result.reviewResult().issues()).extracting(ReviewResultDTO.ReviewIssueDTO::message)
                .anyMatch(message -> message.contains("answerDraft is blank"));
        assertThat(result.modelInvocations()).isEmpty();
    }

    private ReviewTeamCommand command(String answerDraft) {
        return new ReviewTeamCommand(
                "Plan a light team activity",
                taskPlan(),
                executionResults(),
                answerDraft,
                context()
        );
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
                        "Keep review practical.",
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

    private TaskPlanDTO taskPlan() {
        return new TaskPlanDTO(
                "Plan activity",
                List.of(new TeamTaskDTO(
                        "task-1",
                        "Summarize",
                        "Summarize options.",
                        "MODEL_TASK",
                        null,
                        objectMapper.createObjectNode(),
                        List.of()
                ))
        );
    }

    private List<ExecutionResultDTO> executionResults() {
        return List.of(new ExecutionResultDTO("task-1", "MODEL_TASK", "SUCCESS", "Activity options", List.of(), null));
    }

    private ModelInvokeResult modelResult(String content) {
        return new ModelInvokeResult(30001L, 1L, "mock", "mock-chat", content, new ModelUsageDTO(3, 4, 7, true));
    }

    private ModelInvokeResult modelResult(String content, ModelToolCallDTO toolCall) {
        return new ModelInvokeResult(
                30001L,
                1L,
                "mock",
                "mock-chat",
                content,
                new ModelUsageDTO(3, 4, 7, true),
                List.of(toolCall)
        );
    }
}
