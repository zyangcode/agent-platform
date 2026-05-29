package com.ls.agent.core.team.application;

import com.ls.agent.core.team.dto.ExecutionResultDTO;
import com.ls.agent.core.team.dto.TaskPlanDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TeamAnswerDraftBuilder {

    public String build(String userInput, TaskPlanDTO plan, List<ExecutionResultDTO> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("User request: ").append(safe(userInput)).append("\n");
        if (plan != null && plan.goal() != null && !plan.goal().isBlank()) {
            builder.append("Goal: ").append(plan.goal()).append("\n");
        }
        builder.append("Execution results:\n");
        builder.append(formatResults(results));
        return builder.toString();
    }

    private String formatResults(List<ExecutionResultDTO> results) {
        if (results == null || results.isEmpty()) {
            return "- No successful task result yet.";
        }
        return results.stream()
                .map(result -> "- " + safe(result.taskId()) + " [" + safe(result.status()) + "]: "
                        + safe(result.result()) + errorSuffix(result))
                .collect(Collectors.joining("\n"));
    }

    private String errorSuffix(ExecutionResultDTO result) {
        return result.errorMessage() == null || result.errorMessage().isBlank()
                ? ""
                : " error=" + result.errorMessage();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
