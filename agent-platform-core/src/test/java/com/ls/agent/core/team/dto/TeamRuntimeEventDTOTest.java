package com.ls.agent.core.team.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRuntimeEventDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsPlanEventWithImmutablePayloadAndTimestamp() {
        ObjectNode payload = objectMapper.createObjectNode().put("goal", "Plan activity");

        TeamRuntimeEventDTO event = TeamRuntimeEventDTO.plan(
                "trace-1",
                2,
                "planner created plan",
                payload
        );

        payload.put("goal", "mutated");

        assertThat(event.type()).isEqualTo("team_plan");
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.step()).isEqualTo(2);
        assertThat(event.message()).isEqualTo("planner created plan");
        assertThat(event.payload().get("goal").asText()).isEqualTo("Plan activity");
        assertThat(event.createdAt()).isNotNull();
        assertThat(event.createdAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void createsTaskAndToolEventsWithStableFields() {
        TeamRuntimeEventDTO taskStart = TeamRuntimeEventDTO.taskStart("trace-1", 3, "task-1", "Check weather");
        TeamRuntimeEventDTO toolCall = TeamRuntimeEventDTO.toolCall("trace-1", 4, "task-1", "weather");
        TeamRuntimeEventDTO toolResult = TeamRuntimeEventDTO.toolResult("trace-1", 5, "task-1", "weather", "SUCCESS", null);
        TeamRuntimeEventDTO taskResult = TeamRuntimeEventDTO.taskResult("trace-1", 6, "task-1", "SUCCESS", "done", null);

        assertThat(taskStart.type()).isEqualTo("team_task_start");
        assertThat(taskStart.taskId()).isEqualTo("task-1");
        assertThat(toolCall.type()).isEqualTo("team_tool_call");
        assertThat(toolCall.toolName()).isEqualTo("weather");
        assertThat(toolResult.type()).isEqualTo("team_tool_result");
        assertThat(toolResult.status()).isEqualTo("SUCCESS");
        assertThat(taskResult.type()).isEqualTo("team_task_result");
        assertThat(taskResult.message()).isEqualTo("done");
    }

    @Test
    void createsReviewAndFinalEvents() {
        TeamRuntimeEventDTO review = TeamRuntimeEventDTO.review("trace-1", 7, "SUCCESS", "review passed", null);
        TeamRuntimeEventDTO finalEvent = TeamRuntimeEventDTO.finalAnswer("trace-1", 8, "final answer", null);

        assertThat(review.type()).isEqualTo("team_review");
        assertThat(review.status()).isEqualTo("SUCCESS");
        assertThat(finalEvent.type()).isEqualTo("team_final");
        assertThat(finalEvent.message()).isEqualTo("final answer");
    }
}
